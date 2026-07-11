package com.abc.expensetracker.sms.parser

enum class Direction { DEBIT, CREDIT }

enum class Instrument { CARD, ACCOUNT, UPI, WALLET, UNKNOWN }

/**
 * Result of parsing one transactional SMS. All money values are in paise
 * to avoid floating-point drift.
 */
data class ParsedTransaction(
    val amountPaise: Long,
    val direction: Direction,
    val merchant: String?,
    val accountTail: String?,
    val bank: String?,
    val instrument: Instrument,
    val refId: String?,
    val balancePaise: Long?,
    val timestamp: Long,
)

/** A confirmation that a credit-card bill was paid (bank's "payment received" SMS). */
data class CcBillPayment(
    val amountPaise: Long,
    val cardTail: String?,
    val bank: String?,
)
