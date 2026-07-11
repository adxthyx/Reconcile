package com.abc.expensetracker.sms.parser

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * Turns a raw SMS into a [ParsedTransaction], or null when the message is not
 * a completed money movement (OTP, promo, reminder, request, statement...).
 *
 * Pure Kotlin, no Android dependencies — fully unit-testable on the JVM.
 */
object SmsParser {

    fun parse(sender: String?, body: String, smsEpochMillis: Long): ParsedTransaction? {
        val text = body.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        if (text.length < 12) return null

        // 1. Hard rejects: OTPs, promos, future/failed/requested payments,
        //    and credit-card bill payment confirmations (transfers, not income).
        if (ParserRules.rejectPatterns.any { it.containsMatchIn(text) }) return null
        if (ParserRules.ccBillPaymentReceived.any { it.containsMatchIn(text) }) return null

        // 2. Direction — earliest keyword in the message wins (an ICICI debit
        //    says "debited ... ; PAYEE credited", so position matters).
        val debitIdx = ParserRules.debitPatterns.earliestIndex(text)
        val creditIdx = ParserRules.creditPatterns.earliestIndex(text)
        val direction = when {
            debitIdx == null && creditIdx == null -> return null
            debitIdx == null -> Direction.CREDIT
            creditIdx == null -> Direction.DEBIT
            debitIdx <= creditIdx -> Direction.DEBIT
            else -> Direction.CREDIT
        }

        // 3. Amount — verb-adjacent first, then first currency-tagged amount.
        val amountPaise = extractAmount(text) ?: return null
        if (amountPaise <= 0) return null

        // 4. Everything else is best-effort.
        val merchant = extractMerchant(text)
        val (accountTail, instrument) = extractAccount(text)
        val refId = ParserRules.refPatterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
        val balancePaise = ParserRules.balancePatterns
            .firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
            ?.let { toPaise(it) }
        val bank = detectBank(sender, text)
        val finalInstrument = when {
            instrument != Instrument.UNKNOWN -> instrument
            text.contains("upi", ignoreCase = true) -> Instrument.UPI
            else -> Instrument.UNKNOWN
        }

        return ParsedTransaction(
            amountPaise = amountPaise,
            direction = direction,
            merchant = merchant,
            accountTail = accountTail,
            bank = bank,
            instrument = finalInstrument,
            refId = refId,
            balancePaise = balancePaise,
            timestamp = extractTimestamp(text, smsEpochMillis),
        )
    }

    /**
     * Detects "we received your credit card bill payment" confirmations.
     * Returns amount + card tail (tail may be null, e.g. SBI's BBPS message)
     * so the pipeline can auto-mark the matching card's bill as paid.
     */
    fun parseCcBillPayment(body: String): CcBillPayment? {
        val text = body.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        for (p in ParserRules.ccBillPaymentReceived) {
            val m = p.find(text) ?: continue
            val amount = toPaise(m.groupValues[1]) ?: return null
            val tail = ParserRules.accountPatterns
                .firstOrNull { it.second == Instrument.CARD }
                ?.first?.find(text)?.groupValues?.get(1)
            val bank = detectBank(null, text)
            return CcBillPayment(amountPaise = amount, cardTail = tail, bank = bank)
        }
        return null
    }

    // ---------------------------------------------------------------- amount

    private fun extractAmount(text: String): Long? {
        for (p in ParserRules.amountNearVerb) {
            p.find(text)?.let { return toPaise(it.groupValues[1]) }
        }
        return ParserRules.anyAmount.find(text)?.let { toPaise(it.groupValues[1]) }
    }

    private fun toPaise(raw: String): Long? = try {
        BigDecimal(raw.replace(",", "")).movePointRight(2).toLong()
    } catch (_: NumberFormatException) {
        null
    }

    // -------------------------------------------------------------- merchant

    private fun extractMerchant(text: String): String? {
        for (p in ParserRules.merchantPatterns) {
            val m = p.find(text) ?: continue
            val cleaned = cleanMerchant(m.groupValues[1])
            if (cleaned != null) return cleaned
        }
        return null
    }

    /** Strip decoration ("..ARHAM ENTERPRISE_" → "ARHAM ENTERPRISE"), drop junk captures. */
    private fun cleanMerchant(raw: String): String? {
        var s = raw.trim().trim('.', '_', '*', '-', ',', ':', ';', '\'', '"').trim()
        s = s.replace(Regex("\\s+"), " ")
        if (s.length < 2) return null
        val lower = s.lowercase()
        // Captures that are clearly not a payee name.
        val junk = listOf(
            "your", "you", "the rate", "a/c", "acct", "account", "card", "credit card",
            "debit card", "atm", "pos", "www", "http", "call", "sms", "bank",
        )
        if (junk.any { lower == it || lower.startsWith("$it ") }) return null
        if (Regex("^[\\d,.]+$").matches(s)) return null // pure number = amount/date fragment
        return s
    }

    // --------------------------------------------------------------- account

    private fun extractAccount(text: String): Pair<String?, Instrument> {
        for ((p, instrument) in ParserRules.accountPatterns) {
            p.find(text)?.let { return it.groupValues[1] to instrument }
        }
        return null to Instrument.UNKNOWN
    }

    // ------------------------------------------------------------------ bank

    private fun detectBank(sender: String?, text: String): String? {
        val senderUp = sender?.uppercase() ?: ""
        val bodyUp = text.uppercase()
        for ((kw, name) in ParserRules.bankKeywords) {
            if (senderUp.contains(kw)) return name
        }
        for ((kw, name) in ParserRules.bankKeywords) {
            if (bodyUp.contains(kw)) return name
        }
        return null
    }

    // ------------------------------------------------------------------ date

    private val isoDateTime =
        Regex("""(\d{4})-(\d{2})-(\d{2})[:\sT]*(\d{2}):(\d{2})(?::(\d{2}))?""")
    private val isoDate = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    private val dMmmY = Regex("""(\d{1,2})[-\s]?([A-Za-z]{3})[-\s]?(\d{2,4})""")
    private val dmy = Regex("""(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2,4})""")

    private val months: Map<String, Int> = java.time.Month.entries.associate {
        it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase() to it.value
    }

    /**
     * Prefer the date embedded in the body; keep the SMS receive time when the
     * body date is the same day (the SMS timestamp carries the real clock time).
     */
    private fun extractTimestamp(text: String, smsEpochMillis: Long): Long {
        val zone = ZoneId.systemDefault()
        val smsDate = java.time.Instant.ofEpochMilli(smsEpochMillis).atZone(zone).toLocalDate()

        isoDateTime.find(text)?.let { m ->
            val (y, mo, d, h, mi) = m.destructured
            val sec = m.groupValues[6].ifEmpty { "0" }
            runCatching {
                return LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), sec.toInt())
                    .atZone(zone).toInstant().toEpochMilli()
            }
        }

        val bodyDate: LocalDate? = run {
            isoDate.find(text)?.let { m ->
                val (y, mo, d) = m.destructured
                runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
            } ?: dMmmY.find(text)?.let { m ->
                val (d, mon, y) = m.destructured
                val month = months[mon.lowercase()] ?: return@let null
                runCatching { LocalDate.of(fixYear(y.toInt()), month, d.toInt()) }.getOrNull()
            } ?: dmy.find(text)?.let { m ->
                val (d, mo, y) = m.destructured
                runCatching { LocalDate.of(fixYear(y.toInt()), mo.toInt(), d.toInt()) }.getOrNull()
            }
        }

        return when {
            bodyDate == null -> smsEpochMillis
            abs(bodyDate.toEpochDay() - smsDate.toEpochDay()) <= 1 -> smsEpochMillis
            else -> bodyDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        }
    }

    private fun fixYear(y: Int): Int = if (y < 100) 2000 + y else y

    private fun List<Regex>.earliestIndex(text: String): Int? =
        mapNotNull { it.find(text)?.range?.first }.minOrNull()
}
