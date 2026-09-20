package com.abc.expensetracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedTest {
    @Test
    fun friendsCategoryIsAnExcludedExpenseCategory() {
        val friends = Seed.categories.single { it.key == "friends" }

        assertEquals("Friends", friends.name)
        assertFalse(friends.isIncome)
        assertTrue(friends.excludeFromTotals)
    }

    @Test
    fun seededCategoryKeysRemainUnique() {
        val keys = Seed.categories.map { it.key }

        assertEquals(keys.size, keys.distinct().size)
    }
}
