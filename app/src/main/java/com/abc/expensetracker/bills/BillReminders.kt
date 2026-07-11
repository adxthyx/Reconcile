package com.abc.expensetracker.bills

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.abc.expensetracker.KharchaApp
import com.abc.expensetracker.MainActivity
import com.abc.expensetracker.R
import com.abc.expensetracker.data.CardBill
import com.abc.expensetracker.util.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Credit-card bill reminders: from 3 days before the due date, a notification
 * fires daily at 10:00 until the bill is marked paid (in-app button, or
 * auto-detected from the bank's "payment received" SMS).
 */
object BillReminders {

    private const val CHANNEL_ID = "bill_reminders"
    private const val REQUEST_CODE = 4110

    const val REMIND_DAYS_BEFORE = 3L
    const val ACTION_CHECK = "com.abc.expensetracker.BILL_CHECK"

    /** Due date of the cycle epochDate falls in (due day clamped to month length). */
    fun dueDateFor(card: CardBill, today: LocalDate): LocalDate {
        val ym = YearMonth.from(today)
        val thisMonthDue = ym.atDay(card.dueDay.coerceAtMost(ym.lengthOfMonth()))
        return if (today.isAfter(thisMonthDue)) {
            val next = ym.plusMonths(1)
            next.atDay(card.dueDay.coerceAtMost(next.lengthOfMonth()))
        } else thisMonthDue
    }

    fun cycleKey(card: CardBill, today: LocalDate): String =
        YearMonth.from(dueDateFor(card, today)).toString()

    /** Cards needing a reminder today: within window and not marked paid. */
    fun dueCards(cards: List<CardBill>, today: LocalDate): List<Pair<CardBill, LocalDate>> =
        cards.filter { it.enabled }.mapNotNull { card ->
            val due = dueDateFor(card, today)
            val windowStart = due.minusDays(REMIND_DAYS_BEFORE)
            val inWindow = !today.isBefore(windowStart) && !today.isAfter(due)
            val unpaid = card.lastPaidCycle != YearMonth.from(due).toString()
            if (inWindow && unpaid) card to due else null
        }

    /** Schedule the next 10:00 check (today if before 10:00, else tomorrow). */
    @SuppressLint("ScheduleExactAlarm")
    fun scheduleNextCheck(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = LocalDateTime.now()
        val next = if (now.toLocalTime().isBefore(LocalTime.of(10, 0))) {
            now.toLocalDate().atTime(10, 0)
        } else {
            now.toLocalDate().plusDays(1).atTime(10, 0)
        }
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, BillReminderReceiver::class.java).setAction(ACTION_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val canExact = android.os.Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
        if (canExact) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            // no exact-alarm permission: fire within a ±15 min window of 10:00
            alarm.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 30 * 60 * 1000L, pi)
        }
    }

    fun notifyDueCards(context: Context, due: List<Pair<CardBill, LocalDate>>, amounts: Map<Long, Long>) {
        if (due.isEmpty()) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
        due.forEach { (card, dueDate) ->
            val open = PendingIntent.getActivity(
                context, card.id.toInt(),
                Intent(context, MainActivity::class.java)
                    .putExtra("openCards", true)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val today = LocalDate.now()
            val daysLeft = dueDate.toEpochDay() - today.toEpochDay()
            val whenText = when (daysLeft) {
                0L -> "due TODAY"
                1L -> "due tomorrow"
                else -> "due in $daysLeft days"
            }
            val cycleSpend = amounts[card.id]
            val text = buildString {
                append("${card.name} bill $whenText (${dueDate.dayOfMonth}/${dueDate.monthValue})")
                if (cycleSpend != null && cycleSpend > 0) append(" · cycle spend ${Money.format(cycleSpend)}")
                append(". Open to mark paid.")
            }
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("💳 ${card.name} bill $whenText")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(2000 + card.id.toInt(), notif)
        }
    }
}

/** Fires at ~10:00 daily; posts reminders and reschedules itself. */
class BillReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BillReminders.ACTION_CHECK) return
        val container = KharchaApp.from(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cards = container.db.cardBillDao().all()
                val due = BillReminders.dueCards(cards, LocalDate.now())
                BillReminders.notifyDueCards(context, due, emptyMap())
            } finally {
                BillReminders.scheduleNextCheck(context)
                pending.finish()
            }
        }
    }
}

/** Re-arm the daily check after reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            BillReminders.scheduleNextCheck(context)
        }
    }
}
