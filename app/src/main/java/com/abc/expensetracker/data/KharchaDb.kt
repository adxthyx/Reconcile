package com.abc.expensetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.abc.expensetracker.sms.parser.Direction
import com.abc.expensetracker.sms.parser.Instrument

class Converters {
    @TypeConverter fun directionToString(d: Direction): String = d.name
    @TypeConverter fun stringToDirection(s: String): Direction = Direction.valueOf(s)
    @TypeConverter fun instrumentToString(i: Instrument): String = i.name
    @TypeConverter fun stringToInstrument(s: String): Instrument = Instrument.valueOf(s)
    @TypeConverter fun sourceToString(s: TxnSource): String = s.name
    @TypeConverter fun stringToSource(s: String): TxnSource = TxnSource.valueOf(s)
    @TypeConverter fun accountTypeToString(t: AccountType): String = t.name
    @TypeConverter fun stringToAccountType(s: String): AccountType = AccountType.valueOf(s)
}

/** v1 → v2: excluded/tags/split on transactions, rollover budgets, learning-layer
 *  exclusion, excludable categories, card bills, duplicate-review dismissals. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN excluded INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN tags TEXT")
        db.execSQL("ALTER TABLE transactions ADD COLUMN splitOwedPaise INTEGER")
        db.execSQL("ALTER TABLE transactions ADD COLUMN splitWith TEXT")
        db.execSQL("ALTER TABLE transactions ADD COLUMN splitSettled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE budgets ADD COLUMN rollover INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE budgets ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE merchant_mappings ADD COLUMN excluded INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE categories ADD COLUMN excludeFromTotals INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS card_bills (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                tail TEXT,
                dueDay INTEGER NOT NULL,
                statementDay INTEGER NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                lastPaidCycle TEXT,
                createdAt INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS dup_dismissals (
                loId INTEGER NOT NULL,
                hiId INTEGER NOT NULL,
                PRIMARY KEY(loId, hiId)
            )"""
        )
    }
}

@Database(
    entities = [
        Txn::class, Category::class, Account::class, Budget::class, Goal::class,
        MerchantMapping::class, CardBill::class, DupDismissal::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class KharchaDb : RoomDatabase() {
    abstract fun txnDao(): TxnDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun merchantMappingDao(): MerchantMappingDao
    abstract fun cardBillDao(): CardBillDao
    abstract fun dupDismissalDao(): DupDismissalDao

    companion object {
        @Volatile private var instance: KharchaDb? = null

        fun get(context: Context): KharchaDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KharchaDb::class.java,
                    "kharcha.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
