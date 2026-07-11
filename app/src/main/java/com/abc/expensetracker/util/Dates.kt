package com.abc.expensetracker.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dates {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun monthRange(month: YearMonth): LongRange {
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start..end
    }

    fun currentMonth(): YearMonth = YearMonth.now(zone)

    fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val dayYearFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val timeFmt = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)
    private val monthFmt = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH)

    fun day(epochMillis: Long): String = dayFmt.format(toLocalDate(epochMillis))
    fun dayYear(epochMillis: Long): String = dayYearFmt.format(toLocalDate(epochMillis))
    fun dateTime(epochMillis: Long): String =
        timeFmt.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    fun month(ym: YearMonth): String = monthFmt.format(ym)
    fun monthFull(ym: YearMonth): String =
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH).format(ym)
}
