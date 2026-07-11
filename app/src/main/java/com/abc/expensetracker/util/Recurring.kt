package com.abc.expensetracker.util

import com.abc.expensetracker.data.Txn
import kotlin.math.abs

enum class Cadence(val display: String, val days: Int) {
    WEEKLY("Weekly", 7),
    MONTHLY("Monthly", 30),
    YEARLY("Yearly", 365),
}

data class RecurringItem(
    val merchant: String,
    val merchantNorm: String,
    val cadence: Cadence,
    val typicalAmountPaise: Long,
    val lastPaid: Long,
    val nextDueEstimate: Long,
    val occurrences: Int,
)

/**
 * Heuristic recurring-payment detection: a merchant paid >= 2 times at a
 * stable-ish amount (median +/- 25%) whose payment gaps cluster near a weekly,
 * monthly, or yearly cadence.
 */
object Recurring {
    fun detect(txns: List<Txn>): List<RecurringItem> {
        return txns
            .filter { it.merchantNorm != null }
            .groupBy { it.merchantNorm!! }
            .mapNotNull { (norm, group) -> analyze(norm, group.sortedBy { it.timestamp }) }
            .sortedBy { it.nextDueEstimate }
    }

    private fun analyze(norm: String, txns: List<Txn>): RecurringItem? {
        if (txns.size < 2) return null
        val gapsDays = txns.zipWithNext { a, b -> (b.timestamp - a.timestamp) / 86_400_000.0 }
        val medianGap = gapsDays.sorted()[gapsDays.size / 2]
        val cadence = Cadence.entries.firstOrNull { c ->
            abs(medianGap - c.days) <= c.days * 0.2
        } ?: return null

        // gaps must be consistent with the cadence, not just the median
        val consistent = gapsDays.count { abs(it - cadence.days) <= cadence.days * 0.3 }
        if (consistent < gapsDays.size / 2.0) return null

        val amounts = txns.map { it.amountPaise }.sorted()
        val medianAmount = amounts[amounts.size / 2]
        val stable = txns.count { abs(it.amountPaise - medianAmount) <= medianAmount * 0.25 }
        if (stable < txns.size / 2.0) return null

        val last = txns.last()
        return RecurringItem(
            merchant = last.merchant ?: norm,
            merchantNorm = norm,
            cadence = cadence,
            typicalAmountPaise = medianAmount,
            lastPaid = last.timestamp,
            nextDueEstimate = last.timestamp + cadence.days * 86_400_000L,
            occurrences = txns.size,
        )
    }
}
