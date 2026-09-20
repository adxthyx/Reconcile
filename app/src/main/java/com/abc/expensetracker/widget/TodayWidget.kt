package com.abc.expensetracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.abc.expensetracker.KharchaApp
import com.abc.expensetracker.MainActivity
import com.abc.expensetracker.R
import com.abc.expensetracker.bills.BillReminders
import com.abc.expensetracker.data.CardBill
import com.abc.expensetracker.data.Category
import com.abc.expensetracker.data.Txn
import com.abc.expensetracker.util.Dates
import com.abc.expensetracker.util.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Responsive dashboard widget.
 *
 * The launcher inflates only a FrameLayout + ImageView. Rendering the visual
 * hierarchy into a bitmap avoids OEM RemoteViews inflation differences while
 * preserving the compact/expanded dashboard design.
 */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        widgetScope.launch {
            try {
                update(context.applicationContext, manager, ids)
            } catch (t: Throwable) {
                Log.e(TAG, "Widget update failed", t)
                ids.forEach { id ->
                    manager.updateAppWidget(id, fallbackViews(context, manager.getAppWidgetOptions(id)))
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        val pending = goAsync()
        widgetScope.launch {
            try {
                val data = loadData(context.applicationContext)
                manager.updateAppWidget(appWidgetId, buildViews(context, data, newOptions))
            } catch (t: Throwable) {
                Log.e(TAG, "Widget resize failed", t)
                manager.updateAppWidget(appWidgetId, fallbackViews(context, newOptions))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ReconcileWidget"
        private const val SHOW_BILL_AT_DP = 164
        private const val SHOW_RECENT_AT_DP = 244

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val refreshQueue = Channel<Context>(Channel.CONFLATED)

        init {
            widgetScope.launch {
                for (first in refreshQueue) {
                    var context = first
                    // Wait for a quiet period. Sustained imports/edits produce
                    // one final render instead of repainting every few hundred ms.
                    while (true) {
                        val newer = withTimeoutOrNull(800) { refreshQueue.receive() } ?: break
                        context = newer
                    }
                    runCatching { refreshNow(context) }
                        .onFailure { Log.e(TAG, "Queued widget refresh failed", it) }
                }
            }
        }

        private data class CategorySlice(
            val name: String,
            val valuePaise: Long,
            val color: Int,
        )

        private data class UpcomingBill(
            val card: CardBill,
            val dueDate: LocalDate,
            val daysLeft: Long,
            val paid: Boolean,
        )

        private data class WidgetData(
            val spentToday: Long,
            val spentMonth: Long,
            val budgetLimit: Long?,
            val categories: List<CategorySlice>,
            val upcomingBill: UpcomingBill?,
            val recent: List<Txn>,
            val categoryById: Map<Long, Category>,
        )

        private data class Palette(
            val background: Int,
            val panel: Int,
            val panelStroke: Int,
            val text: Int,
            val secondary: Int,
            val divider: Int,
            val green: Int,
            val purple: Int,
            val coral: Int,
            val track: Int,
        )

        /** Coalesced refresh entry point used by database invalidations and SMS. */
        fun refresh(context: Context) {
            refreshQueue.trySend(context.applicationContext)
        }

        private suspend fun refreshNow(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            update(context, manager, ids)
        }

        private suspend fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val data = loadData(context)
            ids.forEach { id ->
                manager.updateAppWidget(
                    id,
                    buildViews(context, data, manager.getAppWidgetOptions(id)),
                )
            }
        }

        private suspend fun loadData(context: Context): WidgetData = coroutineScope {
            val container = KharchaApp.from(context)
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            val monthRange = Dates.monthRange(Dates.currentMonth())

            val todayDeferred = async { container.db.txnDao().expenseTotal(dayStart, dayEnd) }
            val monthDeferred = async {
                container.db.txnDao().expenseTotal(monthRange.first, monthRange.last)
            }
            val categoryRowsDeferred = async {
                container.db.txnDao().spendByCategory(monthRange.first, monthRange.last)
            }
            val categoriesDeferred = async { container.db.categoryDao().all() }
            val cardsDeferred = async { container.db.cardBillDao().all() }
            val budgetDeferred = async { container.db.budgetDao().overall() }
            val recentDeferred = async { container.db.txnDao().recentExpenses(6) }
            val categories = categoriesDeferred.await()
            val categoryById = categories.associateBy { it.id }
            val slices = categoryRowsDeferred.await().mapNotNull { row ->
                categoryById[row.categoryId]
                    ?.takeIf { !it.isIncome && !it.excludeFromTotals }
                    ?.let { category ->
                        CategorySlice(
                            name = category.name.substringBefore(" &"),
                            valuePaise = row.totalPaise,
                            color = category.color.toInt(),
                        )
                    }
            }.filter { it.valuePaise > 0 }

            val paidCycle = BillReminders.paidStateCycleKey(today)
            val billRows = cardsDeferred.await()
                .asSequence()
                .filter { it.enabled }
                .map { card ->
                    val due = BillReminders.dueDateFor(card, today)
                    UpcomingBill(
                        card = card,
                        dueDate = due,
                        daysLeft = due.toEpochDay() - today.toEpochDay(),
                        paid = card.lastPaidCycle == paidCycle,
                    )
                }
                .sortedBy { it.dueDate }
                .toList()
            val upcoming = billRows.firstOrNull { !it.paid } ?: billRows.firstOrNull()

            WidgetData(
                spentToday = todayDeferred.await(),
                spentMonth = monthDeferred.await(),
                budgetLimit = budgetDeferred.await()?.limitPaise,
                categories = slices,
                upcomingBill = upcoming,
                recent = recentDeferred.await(),
                categoryById = categoryById,
            )
        }

        private fun buildViews(context: Context, data: WidgetData, options: Bundle): RemoteViews {
            val description = dashboardDescription(data)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                responsiveSizes(options).takeIf { it.isNotEmpty() }?.let { sizes ->
                    return RemoteViews(
                        sizes.associateWith { size ->
                            val (width, height) = sanitizeSize(size.width, size.height)
                            bitmapViews(
                                context,
                                renderDashboard(context, data, width, height),
                                description,
                            )
                        },
                    )
                }
            }
            val (width, height) = widgetSize(context, options)
            return bitmapViews(
                context,
                renderDashboard(context, data, width, height),
                description,
            )
        }

        private fun fallbackViews(context: Context, options: Bundle): RemoteViews {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                responsiveSizes(options).takeIf { it.isNotEmpty() }?.let { sizes ->
                    return RemoteViews(
                        sizes.associateWith { size ->
                            val (width, height) = sanitizeSize(size.width, size.height)
                            bitmapViews(
                                context,
                                renderFallback(context, width, height),
                                "Reconcile refresh pending",
                            )
                        },
                    )
                }
            }
            val (width, height) = widgetSize(context, options)
            return bitmapViews(
                context,
                renderFallback(context, width, height),
                "Reconcile refresh pending",
            )
        }

        private fun bitmapViews(
            context: Context,
            bitmap: Bitmap,
            description: String,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setImageViewBitmap(R.id.widget_dashboard, bitmap)
            views.setContentDescription(R.id.widget_dashboard, description)
            views.setOnClickPendingIntent(R.id.widget_root, launchApp(context))
            return views
        }

        private fun launchApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            8100,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun renderDashboard(
            context: Context,
            data: WidgetData,
            widthDp: Int,
            heightDp: Int,
        ): Bitmap {
            // Two bitmap pixels per dp remain sharp on high-density displays
            // while cutting refresh allocation substantially on 3x/4x phones.
            val renderScale = context.resources.displayMetrics.density.coerceAtMost(2f)
            val bitmap = Bitmap.createBitmap(
                (widthDp * renderScale).roundToInt().coerceAtLeast(1),
                (heightDp * renderScale).roundToInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bitmap)
            canvas.scale(renderScale, renderScale)
            val colors = palette()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val width = widthDp.toFloat()
            val height = heightDp.toFloat()
            val pad = 6f
            val gap = 4f

            paint.color = colors.background
            canvas.drawRoundRect(RectF(0f, 0f, width, height), 22f, 22f, paint)

            var y = pad
            val rowOneHeight = 38f
            drawPanel(canvas, paint, pad, y, width - pad * 2, rowOneHeight, colors)
            drawSpendRow(canvas, paint, data, pad, y, width - pad * 2, rowOneHeight, colors)
            y += rowOneHeight + gap

            val rowTwoHeight = 56f
            drawBudgetAndCategories(
                canvas,
                paint,
                data,
                pad,
                y,
                width - pad * 2,
                rowTwoHeight,
                colors,
            )
            y += rowTwoHeight + gap

            val bottom = height - pad
            if (heightDp >= SHOW_BILL_AT_DP && y < bottom) {
                val billHeight = if (heightDp < SHOW_RECENT_AT_DP) {
                    (bottom - y).coerceAtLeast(50f)
                } else {
                    50f
                }
                drawPanel(canvas, paint, pad, y, width - pad * 2, billHeight, colors)
                drawBill(
                    canvas, paint, data.upcomingBill, pad, y, width - pad * 2,
                    billHeight, colors,
                )
                y += billHeight + gap
            }
            if (heightDp >= SHOW_RECENT_AT_DP && y < bottom) {
                // With Insights removed, Recent owns every remaining pixel so
                // an expanded widget never ends in an empty strip.
                val recentHeight = (bottom - y).coerceAtLeast(76f)
                drawPanel(canvas, paint, pad, y, width - pad * 2, recentHeight, colors)
                drawRecent(canvas, paint, data, pad, y, width - pad * 2, recentHeight, colors)
            }
            return bitmap
        }

        private fun drawSpendRow(
            canvas: Canvas,
            paint: Paint,
            data: WidgetData,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            colors: Palette,
        ) {
            val half = width / 2f
            paint.color = colors.green
            canvas.drawCircle(x + 16f, y + 20f, 5f, paint)
            drawText(canvas, paint, "Today", x + 30f, y + 13f, 8.5f, colors.secondary)
            drawFittedText(
                canvas, paint, Money.format(data.spentToday), x + 30f, y + 31f,
                16f, colors.text, half - 38f, bold = true,
            )
            paint.color = colors.divider
            canvas.drawRect(x + half, y + 8f, x + half + 0.7f, y + height - 8f, paint)
            paint.color = colors.purple
            canvas.drawCircle(x + half + 15f, y + 20f, 5f, paint)
            drawText(canvas, paint, "This month", x + half + 29f, y + 13f, 8.5f, colors.secondary)
            drawFittedText(
                canvas, paint, Money.format(data.spentMonth), x + half + 29f, y + 31f,
                16f, colors.text, half - 36f, bold = true,
            )
        }

        private fun drawBudgetAndCategories(
            canvas: Canvas,
            paint: Paint,
            data: WidgetData,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            colors: Palette,
        ) {
            val budgetWidth = width * 0.42f
            val categoryX = x + budgetWidth + 3f
            val categoryWidth = width - budgetWidth - 3f
            drawPanel(canvas, paint, x, y, budgetWidth - 3f, height, colors)
            drawPanel(canvas, paint, categoryX, y, categoryWidth, height, colors)

            drawText(canvas, paint, "Monthly budget", x + 8f, y + 12f, 8f, colors.secondary)
            val limit = data.budgetLimit
            if (limit == null || limit <= 0) {
                drawFittedText(
                    canvas, paint, "Set budget", x + 8f, y + 31f,
                    15f, colors.green, budgetWidth - 18f, bold = true,
                )
                drawText(canvas, paint, "Set in Plan", x + 8f, y + 43f, 6.8f, colors.secondary)
                drawProgress(canvas, paint, x + 8f, y + 48f, budgetWidth - 19f, 0f, colors)
            } else {
                val percent = (data.spentMonth.toDouble() / limit * 100).roundToInt()
                val valueColor = if (percent > 100) colors.coral else colors.green
                drawText(canvas, paint, "$percent%", x + 8f, y + 33f, 18f, valueColor, bold = true)
                drawFittedText(
                    canvas, paint, "${Money.compact(data.spentMonth)} of ${Money.compact(limit)}",
                    x + 8f, y + 43f, 6.8f, colors.secondary, budgetWidth - 18f,
                )
                drawProgress(
                    canvas, paint, x + 8f, y + 48f, budgetWidth - 19f,
                    (percent.coerceIn(0, 100) / 100f), colors, valueColor,
                )
            }

            val chartSlices = collapseSlices(data.categories)
            drawDonut(canvas, paint, categoryX + 25f, y + 28f, 17f, chartSlices, colors)
            val legendX = categoryX + 48f
            drawText(canvas, paint, "Categories", legendX, y + 12f, 7.5f, colors.secondary)
            if (data.categories.isEmpty()) {
                drawFittedText(
                    canvas, paint, "No spending yet", legendX, y + 31f,
                    8.5f, colors.text, categoryWidth - 53f, bold = true,
                )
            } else {
                val total = data.categories.sumOf { it.valuePaise }.coerceAtLeast(1L)
                data.categories.sortedByDescending { it.valuePaise }.take(2).forEachIndexed { index, slice ->
                    val baseline = y + 28f + index * 15f
                    val percent = (slice.valuePaise.toDouble() / total * 100).roundToInt()
                    paint.color = slice.color
                    canvas.drawCircle(legendX + 3f, baseline - 3f, 3f, paint)
                    drawFittedText(
                        canvas, paint, slice.name, legendX + 10f, baseline,
                        7.7f, colors.text, categoryWidth - 87f, bold = true,
                    )
                    drawText(
                        canvas, paint, "$percent%", categoryX + categoryWidth - 6f,
                        baseline, 7.4f, colors.secondary, align = Paint.Align.RIGHT,
                    )
                }
            }
        }

        private fun drawBill(
            canvas: Canvas,
            paint: Paint,
            bill: UpcomingBill?,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            colors: Palette,
        ) {
            val contentY = y + (height - 50f) / 2f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = colors.purple
            canvas.drawRoundRect(RectF(x + 10f, contentY + 18f, x + 28f, contentY + 31f), 3f, 3f, paint)
            canvas.drawLine(x + 11f, contentY + 23f, x + 27f, contentY + 23f, paint)
            paint.style = Paint.Style.FILL
            if (bill == null) {
                drawText(canvas, paint, "Add a credit card", x + 37f, contentY + 21f, 11f, colors.text, bold = true)
                drawText(canvas, paint, "Get a reminder every month", x + 37f, contentY + 36f, 8f, colors.secondary)
                drawText(canvas, paint, "Add", x + width - 10f, contentY + 29f, 13f, colors.purple, true, Paint.Align.RIGHT)
                return
            }
            val title = if (bill.paid) "${bill.card.name} · Paid ✓" else "Upcoming · ${bill.card.name}"
            drawFittedText(canvas, paint, title, x + 37f, contentY + 20f, 10.5f, colors.text, width - 105f, true)
            drawText(
                canvas, paint, "Due ${bill.dueDate.dayOfMonth}/${bill.dueDate.monthValue} · resets on 15th",
                x + 37f, contentY + 36f, 7.8f, colors.secondary,
            )
            val due = when {
                bill.paid -> "Paid"
                bill.daysLeft == 0L -> "Today"
                bill.daysLeft == 1L -> "1d"
                else -> "${bill.daysLeft}d"
            }
            drawText(canvas, paint, due, x + width - 10f, contentY + 29f, 13f, colors.purple, true, Paint.Align.RIGHT)
        }

        private fun drawRecent(
            canvas: Canvas,
            paint: Paint,
            data: WidgetData,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            colors: Palette,
        ) {
            drawText(canvas, paint, "Recent", x + 10f, y + 14f, 9f, colors.text, bold = true)
            if (data.recent.isEmpty()) {
                val center = y + height / 2f
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                paint.color = colors.secondary
                canvas.drawCircle(x + 18f, center, 7f, paint)
                paint.style = Paint.Style.FILL
                drawText(canvas, paint, "No recent expenses", x + 36f, center + 5f, 10f, colors.secondary)
                return
            }
            val contentTop = y + 18f
            val rowCount = ((height - 18f) / 28f).toInt()
                .coerceIn(1, data.recent.size.coerceAtLeast(1))
            val visible = data.recent.take(rowCount)
            val rowHeight = (height - 18f) / visible.size
            visible.forEachIndexed { index, txn ->
                val rowTop = contentTop + index * rowHeight
                val baseline = rowTop + rowHeight * 0.62f
                val category = data.categoryById[txn.categoryId]
                val name = txn.merchant?.takeIf { it.isNotBlank() }
                    ?: txn.note?.takeIf { it.isNotBlank() }
                    ?: category?.name
                    ?: "Expense"
                paint.color = category?.color?.toInt() ?: colors.secondary
                canvas.drawCircle(x + 18f, baseline - 3f, 5f, paint)
                drawFittedText(canvas, paint, name, x + 36f, baseline, 9.8f, colors.text, width - 112f)
                drawText(
                    canvas, paint, Money.format(txn.amountPaise), x + width - 10f,
                    baseline, 9.8f, colors.text, true, Paint.Align.RIGHT,
                )
                if (index < visible.lastIndex) {
                    paint.color = colors.divider
                    val dividerY = rowTop + rowHeight
                    canvas.drawRect(x + 36f, dividerY, x + width - 10f, dividerY + 0.6f, paint)
                }
            }
        }

        private fun drawPanel(
            canvas: Canvas,
            paint: Paint,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            colors: Palette,
        ) {
            paint.style = Paint.Style.FILL
            paint.color = colors.panel
            canvas.drawRoundRect(RectF(x, y, x + width, y + height), 14f, 14f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.7f
            paint.color = colors.panelStroke
            canvas.drawRoundRect(RectF(x + 0.35f, y + 0.35f, x + width - 0.35f, y + height - 0.35f), 14f, 14f, paint)
            paint.style = Paint.Style.FILL
        }

        private fun drawProgress(
            canvas: Canvas,
            paint: Paint,
            x: Float,
            y: Float,
            width: Float,
            progress: Float,
            colors: Palette,
            progressColor: Int = colors.green,
        ) {
            paint.color = colors.track
            canvas.drawRoundRect(RectF(x, y, x + width, y + 3f), 2f, 2f, paint)
            if (progress > 0f) {
                paint.color = progressColor
                canvas.drawRoundRect(RectF(x, y, x + width * progress, y + 3f), 2f, 2f, paint)
            }
        }

        private fun drawDonut(
            canvas: Canvas,
            paint: Paint,
            cx: Float,
            cy: Float,
            radius: Float,
            slices: List<CategorySlice>,
            colors: Palette,
        ) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f
            paint.strokeCap = Paint.Cap.BUTT
            paint.color = colors.track
            val bounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(bounds, 0f, 360f, false, paint)
            val total = slices.sumOf { it.valuePaise }.toFloat()
            if (total > 0f) {
                var start = -90f
                slices.forEach { slice ->
                    val sweep = slice.valuePaise / total * 360f
                    paint.color = slice.color
                    canvas.drawArc(bounds, start + 2f, (sweep - 4f).coerceAtLeast(1f), false, paint)
                    start += sweep
                }
            }
            paint.style = Paint.Style.FILL
        }

        private fun drawText(
            canvas: Canvas,
            paint: Paint,
            text: String,
            x: Float,
            baseline: Float,
            sizeSp: Float,
            color: Int,
            bold: Boolean = false,
            align: Paint.Align = Paint.Align.LEFT,
        ) {
            paint.textSize = sizeSp
            paint.color = color
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            paint.textAlign = align
            canvas.drawText(text, x, baseline, paint)
        }

        private fun drawFittedText(
            canvas: Canvas,
            paint: Paint,
            text: String,
            x: Float,
            baseline: Float,
            sizeSp: Float,
            color: Int,
            maxWidth: Float,
            bold: Boolean = false,
        ) {
            paint.textSize = sizeSp
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            paint.textAlign = Paint.Align.LEFT
            var output = text
            if (paint.measureText(output) > maxWidth) {
                val ellipsis = "…"
                while (output.isNotEmpty() && paint.measureText(output + ellipsis) > maxWidth) {
                    output = output.dropLast(1)
                }
                output += ellipsis
            }
            paint.color = color
            canvas.drawText(output, x, baseline, paint)
        }

        private fun collapseSlices(slices: List<CategorySlice>): List<CategorySlice> {
            val sorted = slices.sortedByDescending { it.valuePaise }
            if (sorted.size <= 5) return sorted
            return sorted.take(4) + CategorySlice(
                name = "Other",
                valuePaise = sorted.drop(4).sumOf { it.valuePaise },
                color = Color.rgb(140, 152, 165),
            )
        }

        private fun dashboardDescription(data: WidgetData): String = buildString {
            append("Spent ${Money.format(data.spentToday)} today and ")
            append("${Money.format(data.spentMonth)} this month. ")
            data.budgetLimit?.takeIf { it > 0 }?.let { limit ->
                append("Monthly budget ")
                append((data.spentMonth.toDouble() / limit * 100).roundToInt())
                append(" percent used. ")
            }
        }

        private fun renderFallback(context: Context, widthDp: Int, heightDp: Int): Bitmap {
            val renderScale = context.resources.displayMetrics.density.coerceAtMost(2f)
            val bitmap = Bitmap.createBitmap(
                (widthDp * renderScale).roundToInt(),
                (heightDp * renderScale).roundToInt(),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bitmap)
            canvas.scale(renderScale, renderScale)
            val colors = palette()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.background }
            canvas.drawRoundRect(RectF(0f, 0f, widthDp.toFloat(), heightDp.toFloat()), 22f, 22f, paint)
            drawText(canvas, paint, "Reconcile", 16f, 34f, 16f, colors.text, true)
            drawText(canvas, paint, "Tap to open · refresh pending", 16f, 53f, 9f, colors.secondary)
            return bitmap
        }

        /** Exact launcher-supported dimensions on Android 12+. */
        @RequiresApi(Build.VERSION_CODES.S)
        private fun responsiveSizes(options: Bundle): List<SizeF> {
            val sizes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                options.getParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    SizeF::class.java,
                )
            } else {
                @Suppress("DEPRECATION")
                options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            }.orEmpty()
            return sizes
                .filter { it.width > 0f && it.height > 0f }
                .distinctBy { it.width.roundToInt() to it.height.roundToInt() }
        }

        /**
         * Widget min/max options describe portrait and landscape bounds. On a
         * portrait home screen the actual pair is minWidth + maxHeight; using
         * minHeight here vertically stretched every expanded bitmap.
         */
        private fun widgetSize(context: Context, options: Bundle): Pair<Int, Int> {
            val portrait = context.resources.configuration.orientation !=
                Configuration.ORIENTATION_LANDSCAPE
            val widthKey = if (portrait) {
                AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
            } else {
                AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
            }
            val heightKey = if (portrait) {
                AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
            } else {
                AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
            }
            val width = options.getInt(widthKey, 250).takeIf { it > 0 } ?: 250
            val height = options.getInt(heightKey, 110).takeIf { it > 0 } ?: 110
            return sanitizeSize(width.toFloat(), height.toFloat())
        }

        private fun sanitizeSize(width: Float, height: Float): Pair<Int, Int> =
            width.roundToInt().coerceIn(220, 460) to
                height.roundToInt().coerceIn(100, 520)

        private fun palette() = Palette(
            background = Color.rgb(9, 12, 16),
            panel = Color.rgb(17, 22, 29),
            panelStroke = Color.rgb(39, 48, 58),
            text = Color.rgb(244, 247, 245),
            secondary = Color.rgb(140, 152, 165),
            divider = Color.rgb(39, 48, 58),
            green = Color.rgb(101, 242, 195),
            purple = Color.rgb(124, 140, 255),
            coral = Color.rgb(255, 122, 134),
            track = Color.rgb(39, 48, 58),
        )
    }
}
