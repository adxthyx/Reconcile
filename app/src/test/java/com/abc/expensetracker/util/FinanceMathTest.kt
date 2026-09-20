package com.abc.expensetracker.util

import com.abc.expensetracker.data.Txn
import com.abc.expensetracker.sms.parser.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinanceMathTest {
    @Test
    fun excludedTransactionsDoNotAffectSpendOrNet() {
        val excludedDebit = txn(89_599_00, Direction.DEBIT, excluded = true)
        val excludedCredit = txn(10_000_00, Direction.CREDIT, excluded = true)

        assertEquals(0L, FinanceMath.spendContribution(excludedDebit))
        assertEquals(0L, FinanceMath.dayNet(listOf(excludedDebit, excludedCredit)))
    }

    @Test
    fun debitContributionAndDailyNetAreSplitAware() {
        val dinner = txn(2_400_00, Direction.DEBIT, splitOwedPaise = 1_600_00)
        val salary = txn(5_000_00, Direction.CREDIT)

        assertEquals(800_00L, FinanceMath.spendContribution(dinner))
        assertEquals(4_200_00L, FinanceMath.dayNet(listOf(dinner, salary)))
    }

    @Test
    fun currentMonthProjectionUsesElapsedCalendarDays() {
        val result = FinanceMath.projectedMonthEnd(
            currentSpendPaise = 10_000_00,
            month = YearMonth.of(2026, 8),
            asOf = LocalDate.of(2026, 8, 10),
        )

        assertEquals(31_000_00L, result)
    }

    @Test
    fun completedMonthProjectionIsItsActualSpend() {
        val result = FinanceMath.projectedMonthEnd(
            currentSpendPaise = 18_750_00,
            month = YearMonth.of(2026, 7),
            asOf = LocalDate.of(2026, 8, 10),
        )

        assertEquals(18_750_00L, result)
    }

    @Test
    fun percentChangeNeedsAPreviousBaseline() {
        assertEquals(25, FinanceMath.percentChange(12_500, 10_000))
        assertEquals(-25, FinanceMath.percentChange(7_500, 10_000))
        assertNull(FinanceMath.percentChange(10_000, 0))
    }

    private fun txn(
        amount: Long,
        direction: Direction,
        excluded: Boolean = false,
        splitOwedPaise: Long? = null,
    ) = Txn(
        amountPaise = amount,
        direction = direction,
        merchant = "Example",
        merchantNorm = "example",
        categoryId = 1,
        accountId = null,
        timestamp = 0,
        excluded = excluded,
        splitOwedPaise = splitOwedPaise,
    )
}
