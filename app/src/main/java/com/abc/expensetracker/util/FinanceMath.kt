package com.abc.expensetracker.util

import com.abc.expensetracker.data.Txn
import com.abc.expensetracker.sms.parser.Direction
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

/** Pure financial presentation calculations shared by UI and JVM tests. */
object FinanceMath {
    fun spendContribution(txn: Txn): Long = when {
        txn.excluded || txn.direction != Direction.DEBIT -> 0L
        else -> txn.amountPaise - (txn.splitOwedPaise ?: 0L)
    }

    fun dayNet(txns: List<Txn>): Long = txns.sumOf { txn ->
        when {
            txn.excluded -> 0L
            txn.direction == Direction.CREDIT -> txn.amountPaise
            else -> -spendContribution(txn)
        }
    }

    /** Straight-line projection: current spend / elapsed days * days in month. */
    fun projectedMonthEnd(
        currentSpendPaise: Long,
        month: YearMonth,
        asOf: LocalDate,
    ): Long {
        val currentMonth = YearMonth.from(asOf)
        return when {
            month < currentMonth -> currentSpendPaise
            month > currentMonth -> 0L
            else -> (currentSpendPaise.toDouble() * month.lengthOfMonth() / asOf.dayOfMonth)
                .roundToLong()
        }
    }

    fun percentChange(currentPaise: Long, previousPaise: Long): Int? {
        if (previousPaise <= 0L) return null
        return (((currentPaise - previousPaise).toDouble() / previousPaise) * 100).roundToLong().toInt()
    }
}
