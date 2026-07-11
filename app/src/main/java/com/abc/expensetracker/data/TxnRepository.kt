package com.abc.expensetracker.data

import com.abc.expensetracker.sms.parser.Categorizer
import com.abc.expensetracker.sms.parser.CcBillPayment
import com.abc.expensetracker.sms.parser.Direction
import com.abc.expensetracker.sms.parser.Instrument
import com.abc.expensetracker.sms.parser.ParsedTransaction
import com.abc.expensetracker.util.Dates
import java.security.MessageDigest
import java.time.YearMonth

sealed class InsertResult {
    data class Inserted(val id: Long) : InsertResult()
    data object Duplicate : InsertResult()
}

/**
 * All writes funnel through here so dedup, auto-categorization, the learning
 * layer, and account auto-creation behave identically for the realtime
 * receiver and the historical importer.
 */
class TxnRepository(private val db: KharchaDb) {

    private val txnDao = db.txnDao()
    private val categoryDao = db.categoryDao()
    private val accountDao = db.accountDao()
    private val mappingDao = db.merchantMappingDao()
    private val cardBillDao = db.cardBillDao()

    /** Same-transaction window for cross-source dedup (bank SMS + UPI app SMS). */
    private val dedupWindowMillis = 3 * 60 * 1000L

    suspend fun ensureSeeded() {
        if (categoryDao.count() == 0) {
            categoryDao.insertAll(Seed.categories)
            accountDao.insert(Seed.defaultAccount)
        } else {
            // v2 upgrade path: add any categories that didn't exist in v1.
            for (cat in Seed.categories) {
                if (categoryDao.byKey(cat.key) == null) categoryDao.insert(cat)
            }
        }
        if (cardBillDao.count() == 0) {
            Seed.cardBills.forEach { cardBillDao.insert(it) }
        }
    }

    /**
     * Insert a parsed SMS transaction. Dedup layers:
     *  1. smsHash unique index — identical SMS never re-imported.
     *  2. Same UPI ref + amount already stored — bank copy vs UPI app copy.
     *  3. Same amount+direction within ±3 min — same purchase, two SMSes
     *     with no shared ref.
     */
    suspend fun insertFromSms(
        parsed: ParsedTransaction,
        sender: String?,
        body: String,
        smsEpochMillis: Long,
    ): InsertResult {
        ensureSeeded()

        parsed.refId?.let { ref ->
            if (txnDao.countByRef(ref, parsed.amountPaise) > 0) return InsertResult.Duplicate
        }
        if (txnDao.countNearDuplicates(
                parsed.amountPaise,
                parsed.direction.name,
                parsed.timestamp - dedupWindowMillis,
                parsed.timestamp + dedupWindowMillis,
            ) > 0
        ) return InsertResult.Duplicate

        val merchantNorm = parsed.merchant?.let(::normalizeMerchant)
        val learnKey = learningKey(merchantNorm, sender, parsed.direction)
        val learned = learnKey?.let { mappingDao.byMerchant(it) }

        val category = when {
            learned != null -> categoryDao.all().find { it.id == learned.categoryId }
            else -> {
                val key = Categorizer.categorize(parsed.merchant, body, parsed.direction)
                categoryDao.byKey(key)
            }
        } ?: categoryDao.byKey(Categorizer.OTHER)!!

        // excluded if: user taught us to exclude, or category defaults to excluded
        val excluded = learned?.excluded ?: category.excludeFromTotals

        val accountId = resolveAccount(parsed)

        val txn = Txn(
            amountPaise = parsed.amountPaise,
            direction = parsed.direction,
            merchant = parsed.merchant,
            merchantNorm = merchantNorm,
            categoryId = category.id,
            accountId = accountId,
            timestamp = parsed.timestamp,
            source = TxnSource.SMS,
            instrument = parsed.instrument,
            smsHash = smsHash(sender, body, smsEpochMillis),
            refId = parsed.refId,
            balancePaise = parsed.balancePaise,
            smsBody = body,
            smsSender = sender,
            excluded = excluded,
        )
        val id = txnDao.insert(txn) // OnConflict IGNORE on smsHash
        return if (id == -1L) InsertResult.Duplicate else InsertResult.Inserted(id)
    }

    /**
     * "Payment received towards your credit card" SMS → mark the matching card's
     * current bill cycle paid. Match by tail first, else by bank name.
     */
    suspend fun recordCardPayment(payment: CcBillPayment, epochMillis: Long) {
        ensureSeeded()
        val cards = cardBillDao.all()
        val card = payment.cardTail?.let { tail -> cards.find { it.tail == tail } }
            ?: payment.bank?.let { bank ->
                cards.filter { it.name.contains(bank, ignoreCase = true) || bank.contains(it.name, ignoreCase = true) }
                    .takeIf { it.size == 1 }?.first()
            }
            ?: return
        val cycle = currentCycleKey(card, epochMillis)
        if (card.lastPaidCycle != cycle) {
            cardBillDao.update(card.copy(lastPaidCycle = cycle))
        }
        // learn the tail if we didn't know it
        if (card.tail == null && payment.cardTail != null) {
            cardBillDao.update(card.copy(tail = payment.cardTail, lastPaidCycle = cycle))
        }
    }

    /** "yyyy-MM" of the due date that epochMillis falls in the window of. */
    fun currentCycleKey(card: CardBill, epochMillis: Long): String {
        val date = Dates.toLocalDate(epochMillis)
        val ym = YearMonth.from(date)
        val due = ym.atDay(card.dueDay.coerceAtMost(ym.lengthOfMonth()))
        // Payments after this month's due date belong to next month's bill.
        return if (date.isAfter(due)) ym.plusMonths(1).toString() else ym.toString()
    }

    /**
     * User corrected a category: persist the mapping forever and recategorize
     * this merchant's (or, for merchant-less bank notices, this sender's)
     * whole history. Exclusion travels with the category default unless
     * explicitly overridden via [setExcludedLearning].
     */
    suspend fun setCategoryLearning(txn: Txn, categoryId: Long) {
        val category = categoryDao.all().find { it.id == categoryId }
        val excluded = category?.excludeFromTotals ?: false
        txnDao.update(txn.copy(categoryId = categoryId, excluded = excluded))
        val key = learningKey(txn.merchantNorm, txn.smsSender, txn.direction) ?: return
        mappingDao.upsert(MerchantMapping(merchantNorm = key, categoryId = categoryId, excluded = excluded))
        if (txn.merchantNorm != null) {
            txnDao.recategorizeMerchant(txn.merchantNorm, categoryId)
            txnDao.setExcludedForMerchant(txn.merchantNorm, excluded)
        } else if (txn.smsSender != null) {
            txnDao.recategorizeBySender(txn.smsSender, txn.direction.name, categoryId, excluded)
        }
    }

    /**
     * Exclude/include one transaction; optionally teach it for the merchant
     * (or sender) so future SMSes are excluded automatically.
     */
    suspend fun setExcludedLearning(txn: Txn, excluded: Boolean, always: Boolean) {
        txnDao.update(txn.copy(excluded = excluded))
        if (!always) return
        val key = learningKey(txn.merchantNorm, txn.smsSender, txn.direction) ?: return
        val existing = mappingDao.byMerchant(key)
        mappingDao.upsert(
            MerchantMapping(
                merchantNorm = key,
                categoryId = existing?.categoryId ?: txn.categoryId,
                excluded = excluded,
            )
        )
        if (txn.merchantNorm != null) {
            txnDao.setExcludedForMerchant(txn.merchantNorm, excluded)
        } else if (txn.smsSender != null) {
            txnDao.recategorizeBySender(txn.smsSender, txn.direction.name, txn.categoryId, excluded)
        }
    }

    /**
     * Learning key: merchant when we have one; otherwise sender+direction so
     * merchant-less bank notices ("payment received...") can be taught too.
     */
    private fun learningKey(merchantNorm: String?, sender: String?, direction: Direction): String? {
        if (!merchantNorm.isNullOrBlank()) return merchantNorm
        val s = sender?.lowercase()?.replace(Regex("[^a-z0-9]"), "")?.takeIf { it.isNotBlank() } ?: return null
        return "sender:$s:${direction.name}"
    }

    /** Auto-create one account per (bank, last-4) seen in SMS. */
    private suspend fun resolveAccount(parsed: ParsedTransaction): Long? {
        val tail = parsed.accountTail ?: return null
        accountDao.byTail(tail)?.let { return it.id }
        val type = when (parsed.instrument) {
            Instrument.CARD -> AccountType.CARD
            Instrument.WALLET -> AccountType.WALLET
            else -> AccountType.BANK
        }
        val name = listOfNotNull(parsed.bank, "••$tail").joinToString(" ")
        return accountDao.insert(Account(name = name, bank = parsed.bank, tail = tail, type = type))
    }

    fun normalizeMerchant(merchant: String): String =
        merchant.lowercase().replace(Regex("[^a-z0-9@ ]"), "").replace(Regex("\\s+"), " ").trim()

    companion object {
        fun smsHash(sender: String?, body: String, epochMillis: Long): String {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest("${sender.orEmpty()}|$body|$epochMillis".toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
