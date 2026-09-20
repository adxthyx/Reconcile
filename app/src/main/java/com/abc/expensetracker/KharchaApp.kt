package com.abc.expensetracker

import android.app.Application
import android.content.Context
import androidx.room.InvalidationTracker
import com.abc.expensetracker.data.KharchaDb
import com.abc.expensetracker.data.TxnRepository
import com.abc.expensetracker.sms.SmsImporter
import com.abc.expensetracker.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db = KharchaDb.get(appContext)
    val repo = TxnRepository(db)
    val settings = SettingsStore(appContext)
    val importer = SmsImporter(appContext, repo)
}

class KharchaApp : Application() {
    lateinit var container: AppContainer
        private set
    private lateinit var widgetObserver: InvalidationTracker.Observer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.repo.ensureSeeded() }
        widgetObserver = object : InvalidationTracker.Observer(
            "transactions",
            "categories",
            "budgets",
            "card_bills",
        ) {
            override fun onInvalidated(tables: Set<String>) {
                TodayWidget.refresh(this@KharchaApp)
            }
        }
        container.db.invalidationTracker.addObserver(widgetObserver)
        com.abc.expensetracker.bills.BillReminders.scheduleNextCheck(this)
    }

    companion object {
        fun from(context: Context): AppContainer =
            (context.applicationContext as KharchaApp).container
    }
}
