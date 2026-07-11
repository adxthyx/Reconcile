package com.abc.expensetracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.abc.expensetracker.KharchaApp
import com.abc.expensetracker.MainActivity
import com.abc.expensetracker.R
import com.abc.expensetracker.util.Dates
import com.abc.expensetracker.util.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Home-screen widget: today's spend vs the daily slice of the overall budget. */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        CoroutineScope(Dispatchers.IO).launch { update(context, manager, ids) }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { update(context, manager, ids) }
        }

        private suspend fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val container = KharchaApp.from(context)
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            val spentToday = container.db.txnDao().observeExpense(dayStart, dayEnd).first()

            val monthRange = Dates.monthRange(Dates.currentMonth())
            val spentMonth = container.db.txnDao().observeExpense(monthRange.first, monthRange.last).first()
            val overall = container.db.budgetDao().overall()

            val subtitle = if (overall != null && overall.limitPaise > 0) {
                val daysInMonth = today.lengthOfMonth()
                val dailyBudget = overall.limitPaise / daysInMonth
                val left = dailyBudget - spentToday
                if (left >= 0) "${Money.format(left)} left today" else "${Money.format(-left)} over today"
            } else {
                "${Money.format(spentMonth)} this month"
            }

            val views = RemoteViews(context.packageName, R.layout.widget_today).apply {
                setTextViewText(R.id.widget_amount, Money.format(spentToday))
                setTextViewText(R.id.widget_subtitle, subtitle)
                setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        context, 0,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}
