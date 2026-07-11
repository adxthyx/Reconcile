package com.abc.expensetracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.abc.expensetracker.sms.parser.Direction
import com.abc.expensetracker.sms.parser.Instrument

enum class TxnSource { SMS, MANUAL }

enum class AccountType { BANK, CARD, WALLET, CASH }

@Entity(
    tableName = "transactions",
    indices = [
        Index("smsHash", unique = true),
        Index("timestamp"),
        Index("categoryId"),
        Index("accountId"),
        Index("merchantNorm"),
    ],
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val direction: Direction,
    val merchant: String?,
    /** lowercase merchant used for grouping + the category learning layer */
    val merchantNorm: String?,
    val categoryId: Long,
    val accountId: Long?,
    val timestamp: Long,
    val note: String? = null,
    val source: TxnSource = TxnSource.SMS,
    val instrument: Instrument = Instrument.UNKNOWN,
    /** SHA-256 of sender+body+smsDate: blocks re-importing the same SMS */
    val smsHash: String? = null,
    val refId: String? = null,
    val balancePaise: Long? = null,
    val smsBody: String? = null,
    val smsSender: String? = null,
    /** excluded from every total/chart/budget (self transfers, CC bill payments) */
    val excluded: Boolean = false,
    /** comma-separated free-form tags, e.g. "trip:hampta,reimbursable" */
    val tags: String? = null,
    /** split: part of this spend owed to you by someone else */
    val splitOwedPaise: Long? = null,
    val splitWith: String? = null,
    val splitSettled: Boolean = false,
)

@Entity(tableName = "categories", indices = [Index("key", unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** stable key matching Categorizer rule keys */
    val key: String,
    val name: String,
    val emoji: String,
    /** ARGB color */
    val color: Long,
    val isIncome: Boolean = false,
    val sortOrder: Int = 0,
    /** transactions in this category default to excluded (transfers, CC payments) */
    val excludeFromTotals: Boolean = false,
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bank: String? = null,
    /** last digits from SMS, used to route parsed transactions */
    val tail: String? = null,
    val type: AccountType = AccountType.BANK,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "budgets", indices = [Index("categoryId", unique = true)])
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** null = overall monthly budget */
    val categoryId: Long?,
    val limitPaise: Long,
    /** envelope mode: unspent budget rolls over to next month */
    val rollover: Boolean = false,
    /** 0 = unknown (pre-v2 rows) — rollover math starts from first tracked month */
    val createdAt: Long = 0,
)

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "🎯",
    val targetPaise: Long,
    val savedPaise: Long = 0,
    val targetDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Learning layer: user's manual corrections. Keyed by normalized merchant, or
 * by "sender:<address>:<direction>" when the SMS had no parseable merchant
 * (e.g. bank "payment received" notices) so those learn too.
 */
@Entity(tableName = "merchant_mappings")
data class MerchantMapping(
    @PrimaryKey val merchantNorm: String,
    val categoryId: Long,
    /** future transactions from this merchant are excluded from totals */
    val excluded: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A credit card whose bill due date we remind about. User-editable. */
@Entity(tableName = "card_bills")
data class CardBill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** card last-4 — links payment SMSes and the statement view; may be null */
    val tail: String? = null,
    /** day of month the bill is due, 1..31 (clamped to month length) */
    val dueDay: Int,
    /** day of month the statement generates — cycle boundary for reconciliation */
    val statementDay: Int,
    val enabled: Boolean = true,
    /** "yyyy-MM" of the due date whose bill was last marked paid */
    val lastPaidCycle: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Near-duplicate pairs the user reviewed and said "keep both". */
@Entity(tableName = "dup_dismissals", primaryKeys = ["loId", "hiId"])
data class DupDismissal(
    val loId: Long,
    val hiId: Long,
)
