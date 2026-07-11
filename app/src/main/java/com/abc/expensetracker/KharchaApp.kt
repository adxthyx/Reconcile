package com.abc.expensetracker

import android.app.Application
import android.content.Context
import com.abc.expensetracker.data.KharchaDb
import com.abc.expensetracker.data.TxnRepository
import com.abc.expensetracker.sms.SmsImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db = KharchaDb.get(context)
    val repo = TxnRepository(db)
    val settings = SettingsStore(context)
    val importer = SmsImporter(context, repo)
}

class KharchaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.repo.ensureSeeded() }
        com.abc.expensetracker.bills.BillReminders.scheduleNextCheck(this)
    }

    companion object {
        fun from(context: Context): AppContainer =
            (context.applicationContext as KharchaApp).container
    }
}
