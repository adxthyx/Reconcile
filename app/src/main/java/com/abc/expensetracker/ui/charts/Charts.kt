package com.abc.expensetracker.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abc.expensetracker.util.Money
import kotlin.math.max

data class Slice(val label: String, val emoji: String, val value: Long, val color: Color)

/**
 * Donut of spend by category, top slices + "Other" fold handled by caller.
 * 2dp surface gaps between slices; center holds the headline total; a legend
 * with direct labels (emoji, name, value) always accompanies the donut, so
 * identity is never color-alone.
 */
@Composable
fun DonutChart(
    slices: List<Slice>,
    total: Long,
    modifier: Modifier = Modifier,
) {
    if (slices.isEmpty() || total <= 0) return
    val surface = MaterialTheme.colorScheme.surface
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(150.dp)) {
                val stroke = 22.dp.toPx()
                val gapDeg = 2.5f
                val diameter = size.minDimension - stroke
                val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                var start = -90f
                val sum = slices.sumOf { it.value }.toFloat()
                slices.forEach { s ->
                    val sweep = s.value / sum * 360f
                    drawArc(
                        color = s.color,
                        startAngle = start + gapDeg / 2,
                        sweepAngle = max(sweep - gapDeg, 0.5f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke),
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Spent", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    Money.compact(total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(10.dp)) { drawCircle(s.color) }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${s.emoji} ${s.label}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Money.compact(s.value),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

data class BarGroup(val label: String, val income: Long, val expense: Long)

/**
 * Month-over-month grouped bars (income vs expense). Thin marks, 4dp rounded
 * data-ends anchored to the baseline, 2dp gap between the pair, every bar
 * direct-labeled via the value row under the selected group.
 */
@Composable
fun MonthBars(
    groups: List<BarGroup>,
    incomeColor: Color,
    expenseColor: Color,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) return
    var selected by remember(groups) { mutableIntStateOf(groups.lastIndex) }
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier) {
        val sel = groups[selected]
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sel.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            LegendDot(incomeColor); Spacer(Modifier.width(4.dp))
            Text("In ${Money.compact(sel.income)}", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(12.dp))
            LegendDot(expenseColor); Spacer(Modifier.width(4.dp))
            Text("Out ${Money.compact(sel.expense)}", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(8.dp))
        val density = LocalDensity.current
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .pointerInput(groups) {
                    detectTapGestures { offset ->
                        val groupWidth = size.width / groups.size.toFloat()
                        selected = (offset.x / groupWidth).toInt().coerceIn(0, groups.lastIndex)
                    }
                }
        ) {
            val maxVal = max(groups.maxOf { max(it.income, it.expense) }, 1L).toFloat()
            val labelSpace = with(density) { 18.dp.toPx() }
            val chartH = size.height - labelSpace
            val groupW = size.width / groups.size
            val barW = with(density) { 14.dp.toPx() }.coerceAtMost(groupW / 3f)
            val gap = with(density) { 2.dp.toPx() }
            val corner = with(density) { 4.dp.toPx() }

            // baseline
            drawLine(axisColor, Offset(0f, chartH), Offset(size.width, chartH), strokeWidth = 1.5f)

            groups.forEachIndexed { i, g ->
                val cx = groupW * i + groupW / 2
                fun bar(value: Long, color: Color, xOffset: Float) {
                    if (value <= 0) return
                    val h = (value / maxVal) * (chartH * 0.92f)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(cx + xOffset, chartH - h),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(corner, corner),
                    )
                    // square off the baseline end so rounding is data-end only
                    drawRect(
                        color = color,
                        topLeft = Offset(cx + xOffset, chartH - corner),
                        size = Size(barW, corner),
                    )
                }
                bar(g.income, if (i == selected) incomeColor else incomeColor.copy(alpha = 0.45f), -barW - gap / 2)
                bar(g.expense, if (i == selected) expenseColor else expenseColor.copy(alpha = 0.45f), gap / 2)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            groups.forEach { g ->
                Text(
                    g.label.take(3),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Canvas(Modifier.size(8.dp)) { drawCircle(color) }
}

data class LinePoint(val label: String, val value: Long)

/**
 * Daily spend line: 2dp stroke, dotted grid, tap for crosshair + direct label
 * of the selected day. Markers appear on the selected point only (≥8dp target
 * via full-height tap columns).
 */
@Composable
fun SpendLine(
    points: List<LinePoint>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier) {
        val sel = points[selected]
        Text(
            "${sel.label} · ${Money.format(sel.value)}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val step = size.width / (points.size - 1).toFloat()
                        selected = (offset.x / step + 0.5f).toInt().coerceIn(0, points.lastIndex)
                    }
                }
        ) {
            val maxVal = max(points.maxOf { it.value }, 1L).toFloat()
            val stepX = size.width / (points.size - 1)
            fun y(v: Long) = size.height - (v / maxVal) * (size.height * 0.9f)

            // dotted midline grid
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
            drawLine(gridColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                strokeWidth = 1f, pathEffect = dash)
            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.5f)

            val path = Path()
            points.forEachIndexed { i, p ->
                val pt = Offset(stepX * i, y(p.value))
                if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

            // crosshair + marker on selection
            val sx = stepX * selected
            val sy = y(points[selected].value)
            drawLine(gridColor, Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 1f, pathEffect = dash)
            drawCircle(color, radius = 5.dp.toPx(), center = Offset(sx, sy), alpha = 0.25f)
            drawCircle(color, radius = 3.dp.toPx(), center = Offset(sx, sy))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(points.first().label, style = MaterialTheme.typography.labelSmall, color = labelColor)
            Spacer(Modifier.weight(1f))
            Text(points.last().label, style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
    }
}
