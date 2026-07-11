package com.abc.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abc.expensetracker.ui.charts.BarGroup
import com.abc.expensetracker.ui.charts.DonutChart
import com.abc.expensetracker.ui.charts.LinePoint
import com.abc.expensetracker.ui.charts.MonthBars
import com.abc.expensetracker.ui.charts.Slice
import com.abc.expensetracker.ui.charts.SpendLine
import com.abc.expensetracker.ui.common.EmptyState
import com.abc.expensetracker.ui.common.SectionHeader
import com.abc.expensetracker.ui.theme.expenseColor
import com.abc.expensetracker.ui.theme.incomeColor
import com.abc.expensetracker.ui.vm.StatsVm
import com.abc.expensetracker.util.Dates
import com.abc.expensetracker.util.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(vm: StatsVm) {
    val month by vm.month.collectAsStateWithLifecycle()
    val spendByCategory by vm.spendByCategory.collectAsStateWithLifecycle()
    val daily by vm.dailySpend.collectAsStateWithLifecycle()
    val monthly by vm.monthlyTotals.collectAsStateWithLifecycle()
    val topMerchants by vm.topMerchants.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = vm::prevMonth) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    Dates.monthFull(month),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = vm::nextMonth) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
        }

        if (spendByCategory.isEmpty() && daily.isEmpty()) {
            item { EmptyState("📊", "No data for this month", "Spending will show up here as SMSes arrive.") }
        }

        if (spendByCategory.isNotEmpty()) {
            item { SectionHeader("Spend by category") }
            item {
                val top = spendByCategory.take(6)
                val rest = spendByCategory.drop(6).sumOf { it.totalPaise }
                val total = spendByCategory.sumOf { it.totalPaise }
                val slices = top.map {
                    Slice(it.category.name, it.category.emoji, it.totalPaise, Color(it.category.color))
                } + if (rest > 0) listOf(Slice("More", "📦", rest, Color(0xFFA9711C))) else emptyList()
                DonutChart(
                    slices, total,
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (daily.size >= 2) {
            item { SectionHeader("Daily spending") }
            item {
                val fmt = DateTimeFormatter.ofPattern("d MMM")
                val points = daily.map {
                    LinePoint(LocalDate.parse(it.day).format(fmt), it.totalPaise)
                }
                SpendLine(
                    points, expenseColor(),
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (monthly.size >= 2) {
            item { SectionHeader("Month over month") }
            item {
                val groups = monthly.map {
                    BarGroup(monthLabel(it.month), it.incomePaise, it.expensePaise)
                }
                MonthBars(
                    groups,
                    incomeColor = incomeColor(),
                    expenseColor = expenseColor(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (topMerchants.isNotEmpty()) {
            item { SectionHeader("Top merchants") }
            items(topMerchants, key = { it.merchantNorm }) { m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.merchant, style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(
                            "${m.count} transaction${if (m.count > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        Money.format(m.totalPaise),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

private fun monthLabel(yyyyMm: String): String = runCatching {
    val (y, m) = yyyyMm.split("-")
    java.time.YearMonth.of(y.toInt(), m.toInt()).format(DateTimeFormatter.ofPattern("MMM"))
}.getOrDefault(yyyyMm)
