package com.abc.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abc.expensetracker.ThemeMode
import com.abc.expensetracker.data.Category
import com.abc.expensetracker.data.DaySum
import com.abc.expensetracker.data.MerchantStat
import com.abc.expensetracker.ui.charts.PacePoint
import com.abc.expensetracker.ui.charts.SpendingPacePlot
import com.abc.expensetracker.ui.common.CategoryIcon
import com.abc.expensetracker.ui.common.ChartContainer
import com.abc.expensetracker.ui.common.EmptyState
import com.abc.expensetracker.ui.common.FilterControl
import com.abc.expensetracker.ui.common.MoneyText
import com.abc.expensetracker.ui.common.MoneyTone
import com.abc.expensetracker.ui.common.ProgressBar
import com.abc.expensetracker.ui.common.SectionHeader
import com.abc.expensetracker.ui.theme.KharchaTheme
import com.abc.expensetracker.ui.theme.ReconcileColors
import com.abc.expensetracker.ui.theme.ReconcileSpacing
import com.abc.expensetracker.ui.vm.CategoryShift
import com.abc.expensetracker.ui.vm.StatsVm
import com.abc.expensetracker.util.Dates
import com.abc.expensetracker.util.FinanceMath
import com.abc.expensetracker.util.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(vm: StatsVm) {
    val month by vm.month.collectAsStateWithLifecycle()
    val currentSpend by vm.currentSpend.collectAsStateWithLifecycle()
    val previousSpend by vm.previousSpend.collectAsStateWithLifecycle()
    val daily by vm.dailySpend.collectAsStateWithLifecycle()
    val shifts by vm.categoryShifts.collectAsStateWithLifecycle()
    val merchants by vm.topMerchants.collectAsStateWithLifecycle()
    val overallBudget by vm.overallBudget.collectAsStateWithLifecycle()
    var showMonthPicker by remember { mutableStateOf(false) }

    val projection = FinanceMath.projectedMonthEnd(currentSpend, month, LocalDate.now())
    InsightsContent(
        month = month,
        currentSpend = currentSpend,
        previousSpend = previousSpend,
        projectedSpend = projection,
        planPaise = overallBudget?.limitPaise,
        daily = daily,
        shifts = shifts,
        merchants = merchants,
        onMonthClick = { showMonthPicker = true },
    )

    if (showMonthPicker) {
        ModalBottomSheet(
            onDismissRequest = { showMonthPicker = false },
            containerColor = ReconcileColors.Surface,
        ) {
            Column(Modifier.padding(horizontal = ReconcileSpacing.Screen)) {
                Text("Choose month", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                repeat(12) { offset ->
                    val option = Dates.currentMonth().minusMonths(offset.toLong())
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                while (vm.month.value > option) vm.prevMonth()
                                while (vm.month.value < option) vm.nextMonth()
                                showMonthPicker = false
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(Dates.monthFull(option), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (option == month) Text("Selected", color = ReconcileColors.Mint, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
internal fun InsightsContent(
    month: YearMonth,
    currentSpend: Long,
    previousSpend: Long,
    projectedSpend: Long,
    planPaise: Long?,
    daily: List<DaySum>,
    shifts: List<CategoryShift>,
    merchants: List<MerchantStat>,
    onMonthClick: () -> Unit,
) {
    val comparison = FinanceMath.percentChange(currentSpend, previousSpend)
    val points = remember(daily) {
        var running = 0L
        daily.map { sum ->
            running += sum.totalPaise
            PacePoint(LocalDate.parse(sum.day).dayOfMonth, running)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(ReconcileColors.Ink),
        contentPadding = PaddingValues(
            start = ReconcileSpacing.Screen,
            end = ReconcileSpacing.Screen,
            bottom = 28.dp,
        ),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Insights", style = MaterialTheme.typography.headlineLarge, color = ReconcileColors.Text)
                    Text("What changed, and why", style = MaterialTheme.typography.bodyMedium, color = ReconcileColors.TextSecondary)
                }
                FilterControl(
                    label = month.format(DateTimeFormatter.ofPattern("MMMM")),
                    selected = true,
                    onClick = onMonthClick,
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        if (currentSpend == 0L && shifts.isEmpty() && merchants.isEmpty()) {
            item { EmptyState("No data for this month", "Insights appear as transactions arrive.") }
        } else {
            item {
                ChartContainer(Modifier.fillMaxWidth()) {
                    Column {
                        Text("SPENDING PACE", style = MaterialTheme.typography.labelLarge, color = ReconcileColors.TextSecondary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MoneyText(currentSpend, modifier = Modifier.weight(1f), style = MaterialTheme.typography.displayMedium)
                            comparison?.let {
                                Text(
                                    "${if (it >= 0) "+" else ""}$it% vs ${month.minusMonths(1).format(DateTimeFormatter.ofPattern("MMM"))}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (it > 0) ReconcileColors.Coral else ReconcileColors.Mint,
                                )
                            }
                        }
                        SpendingPacePlot(
                            points = points,
                            projectedPaise = projectedSpend,
                            daysInMonth = month.lengthOfMonth(),
                            planPaise = planPaise,
                            modifier = Modifier.height(152.dp),
                        )
                        Row {
                            Text(
                                "Projected ${Money.format(projectedSpend)}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                color = ReconcileColors.Mint,
                            )
                            Text(
                                "${month.lengthOfMonth()} ${month.format(DateTimeFormatter.ofPattern("MMM"))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ReconcileColors.TextSecondary,
                            )
                        }
                    }
                }
            }

            if (shifts.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionHeader("Biggest shifts") {
                        Text("vs ${month.minusMonths(1).format(DateTimeFormatter.ofPattern("MMMM"))}", style = MaterialTheme.typography.bodySmall, color = ReconcileColors.TextSecondary)
                    }
                }
                shifts.take(4).forEach { shift ->
                    item(key = "shift-${shift.category.id}") {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIcon(shift.category)
                            Spacer(Modifier.padding(start = 12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(shift.category.name, style = MaterialTheme.typography.bodyLarge, color = ReconcileColors.Text)
                                Text(
                                    shiftDescription(shift),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ReconcileColors.TextSecondary,
                                )
                            }
                            MoneyText(
                                amountPaise = shift.deltaPaise,
                                prefix = if (shift.deltaPaise >= 0) "+" else "−",
                                tone = if (shift.deltaPaise > 0) MoneyTone.Risk else MoneyTone.Income,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            if (merchants.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader("Top merchants")
                }
                val maximum = merchants.maxOf { it.totalPaise }.coerceAtLeast(1L)
                merchants.take(5).forEachIndexed { index, merchant ->
                    item(key = merchant.merchantNorm) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                merchant.merchant,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ReconcileColors.Text,
                                maxLines = 1,
                            )
                            MoneyText(merchant.totalPaise, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(6.dp))
                        val colors = listOf(Color(0xFFFF9A75), ReconcileColors.Indigo, Color(0xFFFF72AF), ReconcileColors.Mint)
                        ProgressBar(
                            progress = merchant.totalPaise.toFloat() / maximum,
                            color = colors[index % colors.size],
                        )
                        Text(
                            "${merchant.count} transaction${if (merchant.count == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ReconcileColors.TextSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

private fun shiftDescription(shift: CategoryShift): String = when {
    shift.previousPaise == 0L -> "New spending this month"
    shift.deltaPaise > 0 -> "${Money.format(shift.deltaPaise)} more than last month"
    else -> "${Money.format(abs(shift.deltaPaise))} less than last month"
}

@Preview(showBackground = true, backgroundColor = 0xFF090C10, widthDp = 360, heightDp = 800)
@Composable
private fun InsightsPreview() {
    val food = Category(1, "food", "Food & Dining", "", 0xFFFF8A65, sortOrder = 0)
    val transport = Category(2, "transport", "Transport", "", 0xFF53D7E8, sortOrder = 1)
    KharchaTheme(ThemeMode.DARK) {
        InsightsContent(
            month = YearMonth.of(2026, 8),
            currentSpend = 37_940_00,
            previousSpend = 32_150_00,
            projectedSpend = 42_100_00,
            planPaise = 30_000_00,
            daily = listOf(
                DaySum("2026-08-02", 4_000_00),
                DaySum("2026-08-10", 8_000_00),
                DaySum("2026-08-20", 12_000_00),
                DaySum("2026-08-27", 13_940_00),
            ),
            shifts = listOf(
                CategoryShift(food, 8_320_00, 6_210_00),
                CategoryShift(transport, 4_000_00, 2_580_00),
            ),
            merchants = listOf(
                MerchantStat("swiggy", "Swiggy", 7, 4_830_00),
                MerchantStat("amazon", "Amazon", 3, 2_410_00),
            ),
            onMonthClick = {},
        )
    }
}
