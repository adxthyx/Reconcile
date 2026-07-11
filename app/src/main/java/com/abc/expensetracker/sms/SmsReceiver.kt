package com.abc.expensetracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.abc.expensetracker.KharchaApp
import com.abc.expensetracker.data.InsertResult
import com.abc.expensetracker.sms.parser.SmsParser
import com.abc.expensetracker.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Realtime capture: every incoming SMS is parsed and, if transactional, stored. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // Multipart SMS arrives as several PDUs of one logical message.
        val sender = messages.first().originatingAddress
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        val timestamp = messages.first().timestampMillis

        val ccPayment = SmsParser.parseCcBillPayment(body)
        val parsed = if (ccPayment == null) SmsParser.parse(sender, body, timestamp) else null
        if (ccPayment == null && parsed == null) return

        val container = KharchaApp.from(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (ccPayment != null) {
                    container.repo.recordCardPayment(ccPayment, timestamp)
                } else if (parsed != null) {
                    val result = container.repo.insertFromSms(parsed, sender, body, timestamp)
                    if (result is InsertResult.Inserted) {
                        TodayWidget.refresh(context)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
