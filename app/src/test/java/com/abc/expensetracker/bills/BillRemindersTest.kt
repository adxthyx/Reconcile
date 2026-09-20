package com.abc.expensetracker.bills

import com.abc.expensetracker.data.CardBill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BillRemindersTest {

    private val card = CardBill(name = "Test card", dueDay = 20, statementDay = 3)

    @Test
    fun paidStateCycle_resetsOnTheFifteenth() {
        assertEquals("2026-06", BillReminders.paidStateCycleKey(LocalDate.of(2026, 7, 14)))
        assertEquals("2026-07", BillReminders.paidStateCycleKey(LocalDate.of(2026, 7, 15)))
        assertEquals("2026-07", BillReminders.paidStateCycleKey(LocalDate.of(2026, 7, 31)))
        assertEquals("2026-07", BillReminders.paidStateCycleKey(LocalDate.of(2026, 8, 1)))
        assertEquals("2026-08", BillReminders.paidStateCycleKey(LocalDate.of(2026, 8, 15)))
    }

    @Test
    fun markedPaid_doesNotRemindAgainUntilNextReset() {
        val paid = card.copy(lastPaidCycle = "2026-07")

        assertTrue(BillReminders.dueCards(listOf(paid), LocalDate.of(2026, 7, 18)).isEmpty())
        assertTrue(BillReminders.dueCards(listOf(paid), LocalDate.of(2026, 8, 18)).isNotEmpty())
    }

    @Test
    fun resetWorksForDueDatesOnEitherSideOfTheFifteenth() {
        val earlyCard = card.copy(dueDay = 4, lastPaidCycle = "2026-06")
        val lateCard = card.copy(dueDay = 30, lastPaidCycle = "2026-07")

        // The early card was paid before July 15; it reminds again for August.
        assertTrue(BillReminders.dueCards(listOf(earlyCard), LocalDate.of(2026, 8, 1)).isNotEmpty())
        // The late card is quiet for July's bill, then eligible after August 15.
        assertTrue(BillReminders.dueCards(listOf(lateCard), LocalDate.of(2026, 7, 27)).isEmpty())
        assertTrue(BillReminders.dueCards(listOf(lateCard), LocalDate.of(2026, 8, 27)).isNotEmpty())
    }
}
