package com.abc.expensetracker.sms

import android.content.Context
import android.provider.Telephony
import com.abc.expensetracker.data.InsertResult
import com.abc.expensetracker.data.TxnRepository
import com.abc.expensetracker.sms.parser.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

sealed class ImportState {
    data object Idle : ImportState()
    data class Running(val scanned: Int, val total: Int, val imported: Int) : ImportState()
    data class Done(val scanned: Int, val imported: Int, val duplicates: Int) : ImportState()
    data class Failed(val message: String) : ImportState()
}

/** One-time historical import: scans the whole SMS inbox and logs every transaction found. */
class SmsImporter(private val context: Context, private val repo: TxnRepository) {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state

    suspend fun importInbox() = withContext(Dispatchers.IO) {
        if (_state.value is ImportState.Running) return@withContext
        try {
            val uri = Telephony.Sms.Inbox.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
            )
            val cursor = context.contentResolver.query(uri, projection, null, null, "date ASC")
                ?: run {
                    _state.value = ImportState.Failed("Could not read SMS inbox")
                    return@withContext
                }

            var scanned = 0
            var imported = 0
            var duplicates = 0
            cursor.use { c ->
                val total = c.count
                val addrIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (c.moveToNext()) {
                    scanned++
                    val sender = c.getString(addrIdx)
                    val body = c.getString(bodyIdx) ?: continue
                    val date = c.getLong(dateIdx)
                    val ccPayment = SmsParser.parseCcBillPayment(body)
                    if (ccPayment != null) {
                        repo.recordCardPayment(ccPayment, date)
                        continue
                    }
                    val parsed = SmsParser.parse(sender, body, date)
                    if (parsed != null) {
                        when (repo.insertFromSms(parsed, sender, body, date)) {
                            is InsertResult.Inserted -> imported++
                            InsertResult.Duplicate -> duplicates++
                        }
                    }
                    if (scanned % 50 == 0) {
                        _state.value = ImportState.Running(scanned, total, imported)
                    }
                }
            }
            _state.value = ImportState.Done(scanned, imported, duplicates)
        } catch (e: Exception) {
            _state.value = ImportState.Failed(e.message ?: "Import failed")
        }
    }
}
