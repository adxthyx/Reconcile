package com.abc.expensetracker.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Indian-style formatting: ₹1,23,456.78 */
object Money {
    private val indianFormat = DecimalFormat("##,##,##0.##", DecimalFormatSymbols(Locale("en", "IN")))
    private val indianFormatNoDecimals = DecimalFormat("##,##,##0", DecimalFormatSymbols(Locale("en", "IN")))

    @Synchronized
    fun format(paise: Long, withSymbol: Boolean = true): String {
        val rupees = paise / 100.0
        val s = if (paise % 100 == 0L) {
            indianFormatNoDecimals.format(rupees)
        } else {
            indianFormat.format(rupees)
        }
        return if (withSymbol) "₹$s" else s
    }

    /** Compact for chart labels: ₹1.2K, ₹3.4L, ₹1.1Cr */
    fun compact(paise: Long): String {
        val r = paise / 100.0
        return when {
            r >= 1_00_00_000 -> "₹%.1fCr".format(r / 1_00_00_000)
            r >= 1_00_000 -> "₹%.1fL".format(r / 1_00_000)
            r >= 1_000 -> "₹%.1fK".format(r / 1_000)
            else -> "₹%.0f".format(r)
        }
    }

    /** Parse user input like "1,234.50" to paise; null when invalid. */
    fun parse(input: String): Long? {
        val cleaned = input.replace(",", "").replace("₹", "").trim()
        if (cleaned.isEmpty()) return null
        return cleaned.toBigDecimalOrNull()?.movePointRight(2)?.toLong()?.takeIf { it > 0 }
    }
}
