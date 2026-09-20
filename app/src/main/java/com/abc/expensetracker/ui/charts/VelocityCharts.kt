package com.abc.expensetracker.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.abc.expensetracker.data.DaySum
import com.abc.expensetracker.ui.common.ChartContainer
import com.abc.expensetracker.ui.theme.ReconcileColors
import com.abc.expensetracker.util.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

data class PacePoint(val day: Int, val cumulativePaise: Long)

@Composable
fun MoneyPulseChart(
    dailySpend: List<DaySum>,
    modifier: Modifier = Modifier,
    comparisonPercent: Int? = null,
) {
    val bars = dailySpend.takeLast(12)
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var chartSize by remember { mutableIntStateOf(0) }
    LaunchedEffect(bars) {
        selectedIndex = bars.lastIndex
    }

    val selected = bars.getOrNull(selectedIndex)
    val density = LocalDensity.current
    val gapPx = with(density) { 8.dp.toPx() }
    val minBarPx = with(density) { 5.dp.toPx() }
    val description = buildString {
        append("Money pulse for recent spending days")
        comparisonPercent?.let { append(", ${kotlin.math.abs(it)} percent ${if (it >= 0) "higher" else "lower"}") }
        selected?.let { append(", selected ${Money.format(it.totalPaise)} on ${pulseDateLabel(it.day)}") }
    }
    ChartContainer(modifier = modifier.semantics { contentDescription = description }) {
        Column {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    "MONEY PULSE",
                    style = MaterialTheme.typography.labelLarge,
                    color = ReconcileColors.TextSecondary,
                )
                Spacer(Modifier.weight(1f))
                comparisonPercent?.let {
                    Text(
                        "${if (it >= 0) "+" else ""}$it% ${if (it >= 0) "↑" else "↓"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (it > 0) ReconcileColors.Coral else ReconcileColors.Mint,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (selected != null) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                    Text(
                        Money.format(selected.totalPaise),
                        style = MaterialTheme.typography.titleLarge,
                        color = ReconcileColors.Text,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        pulseDateLabel(selected.day),
                        style = MaterialTheme.typography.bodySmall,
                        color = ReconcileColors.TextSecondary,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            if (bars.isEmpty()) {
                Text(
                    "No spending recorded yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReconcileColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            } else {
                val maxValue = max(bars.maxOf { it.totalPaise }, 1L).toFloat()
                fun indexForX(x: Float, width: Float): Int {
                    if (width <= 0f) return selectedIndex
                    val barWidth = ((width - gapPx * (bars.size - 1)) / bars.size).coerceAtLeast(minBarPx)
                    return (x / (barWidth + gapPx)).toInt().coerceIn(0, bars.lastIndex)
                }

                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .onSizeChanged { chartSize = it.width }
                        .pointerInput(bars, chartSize) {
                            detectTapGestures { offset ->
                                selectedIndex = indexForX(offset.x, chartSize.toFloat())
                            }
                        }
                ) {
                    val gap = 8.dp.toPx()
                    val barWidth = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(5.dp.toPx())
                    bars.forEachIndexed { index, point ->
                        val ratio = point.totalPaise / maxValue
                        val height = (size.height * (0.22f + 0.78f * ratio)).coerceAtLeast(12.dp.toPx())
                        val x = index * (barWidth + gap)
                        val isSelected = index == selectedIndex
                        drawRoundRect(
                            color = when {
                                isSelected -> ReconcileColors.Mint
                                point.totalPaise == 0L -> ReconcileColors.Border.copy(alpha = 0.5f)
                                else -> ReconcileColors.Border
                            },
                            topLeft = Offset(x, size.height - height),
                            size = Size(barWidth, height),
                            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                        )
                        if (isSelected) {
                            drawRoundRect(
                                color = ReconcileColors.Mint.copy(alpha = 0.22f),
                                topLeft = Offset(x - 3.dp.toPx(), size.height - height - 3.dp.toPx()),
                                size = Size(barWidth + 6.dp.toPx(), height + 6.dp.toPx()),
                                cornerRadius = CornerRadius((barWidth + 6.dp.toPx()) / 2, (barWidth + 6.dp.toPx()) / 2),
                                style = Stroke(width = 1.5.dp.toPx()),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun pulseDateLabel(day: String): String = runCatching {
    LocalDate.parse(day).format(DateTimeFormatter.ofPattern("d MMM"))
}.getOrDefault(day)

@Composable
fun SpendingPaceChart(
    points: List<PacePoint>,
    projectedPaise: Long,
    daysInMonth: Int,
    planPaise: Long?,
    modifier: Modifier = Modifier,
) {
    val current = points.lastOrNull()?.cumulativePaise ?: 0L
    val description = "Spending pace ${Money.format(current)}, projected ${Money.format(projectedPaise)}"
    ChartContainer(modifier = modifier.semantics { contentDescription = description }) {
        SpendingPacePlot(points, projectedPaise, daysInMonth, planPaise)
    }
}

@Composable
fun SpendingPacePlot(
    points: List<PacePoint>,
    projectedPaise: Long,
    daysInMonth: Int,
    planPaise: Long?,
    modifier: Modifier = Modifier,
) {
    val current = points.lastOrNull()?.cumulativePaise ?: 0L
    Box(
        modifier
            .fillMaxWidth()
            .height(176.dp)
            .padding(top = 8.dp)
    ) {
        val axis = MaterialTheme.colorScheme.onSurfaceVariant
        Canvas(Modifier.matchParentSize()) {
            val topPadding = 8.dp.toPx()
            val bottomPadding = 18.dp.toPx()
            val chartHeight = size.height - topPadding - bottomPadding
            val maximum = max(max(projectedPaise, planPaise ?: 0L), current).coerceAtLeast(1L).toFloat()
            fun x(day: Int): Float = ((day.coerceIn(1, daysInMonth) - 1f) / (daysInMonth - 1f).coerceAtLeast(1f)) * size.width
            fun y(value: Long): Float = topPadding + chartHeight * (1f - value / maximum)

            planPaise?.takeIf { it > 0 }?.let { plan ->
                drawLine(
                    color = ReconcileColors.Amber.copy(alpha = 0.75f),
                    start = Offset(0f, y(plan)),
                    end = Offset(size.width, y(plan)),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx())),
                )
            }

            if (points.isNotEmpty()) {
                val actual = Path().apply {
                    moveTo(x(points.first().day), y(points.first().cumulativePaise))
                    points.drop(1).forEach { lineTo(x(it.day), y(it.cumulativePaise)) }
                }
                drawPath(
                    actual,
                    color = ReconcileColors.Mint,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
                val last = points.last()
                drawCircle(ReconcileColors.Mint, radius = 6.dp.toPx(), center = Offset(x(last.day), y(last.cumulativePaise)))
                if (last.day < daysInMonth) {
                    drawLine(
                        color = ReconcileColors.Mint.copy(alpha = 0.72f),
                        start = Offset(x(last.day), y(last.cumulativePaise)),
                        end = Offset(size.width, y(projectedPaise)),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx())),
                    )
                }
            }
            drawLine(
                color = axis.copy(alpha = 0.18f),
                start = Offset(0f, size.height - bottomPadding),
                end = Offset(size.width, size.height - bottomPadding),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}
