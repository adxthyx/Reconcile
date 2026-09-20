package com.abc.expensetracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class CategorySum(val categoryId: Long, val totalPaise: Long)
data class DaySum(val day: String, val totalPaise: Long)
data class MonthSum(val month: String, val incomePaise: Long, val expensePaise: Long)
data class MonthCatSum(val month: String, val categoryId: Long, val totalPaise: Long)
data class MerchantStat(val merchantNorm: String, val merchant: String, val count: Int, val totalPaise: Long)

/*
 * Aggregate conventions:
 *  - excluded = 0 everywhere (self transfers / CC bill payments never count)
 *  - debit sums count only "my share": amountPaise - COALESCE(splitOwedPaise, 0)
 */
@Dao
interface TxnDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(txn: Txn): Long

    @Update
    suspend fun update(txn: Txn)

    @Delete
    suspend fun delete(txn: Txn)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): Txn?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<Txn?>

    @Query(
        """SELECT * FROM transactions
           WHERE timestamp BETWEEN :from AND :to
             AND (:accountId IS NULL OR accountId = :accountId)
             AND (:categoryId IS NULL OR categoryId = :categoryId)
             AND (:direction IS NULL OR direction = :direction)
             AND (:tag = '' OR tags LIKE '%' || :tag || '%')
             AND (:query = '' OR merchant LIKE '%' || :query || '%'
                  OR note LIKE '%' || :query || '%'
                  OR CAST(amountPaise / 100.0 AS TEXT) LIKE '%' || :query || '%')
           ORDER BY timestamp DESC"""
    )
    fun observeFiltered(
        from: Long,
        to: Long,
        accountId: Long?,
        categoryId: Long?,
        direction: String?,
        tag: String,
        query: String,
    ): Flow<List<Txn>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Txn>>

    @Query(
        """SELECT * FROM transactions
           WHERE direction = 'DEBIT' AND excluded = 0
           ORDER BY timestamp DESC LIMIT :limit"""
    )
    suspend fun recentExpenses(limit: Int): List<Txn>

    @Query("SELECT * FROM transactions ORDER BY timestamp")
    fun observeAllTxns(): Flow<List<Txn>>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    // ---- dedup helpers ----
    @Query("SELECT COUNT(*) FROM transactions WHERE refId = :refId AND amountPaise = :amountPaise")
    suspend fun countByRef(refId: String, amountPaise: Long): Int

    @Query(
        """SELECT COUNT(*) FROM transactions
           WHERE amountPaise = :amountPaise AND direction = :direction
             AND timestamp BETWEEN :from AND :to"""
    )
    suspend fun countNearDuplicates(amountPaise: Long, direction: String, from: Long, to: Long): Int

    // ---- aggregates ----
    @Query(
        """SELECT categoryId, SUM(amountPaise - COALESCE(splitOwedPaise, 0)) AS totalPaise
           FROM transactions
           WHERE direction = 'DEBIT' AND excluded = 0 AND timestamp BETWEEN :from AND :to
           GROUP BY categoryId ORDER BY totalPaise DESC"""
    )
    fun observeSpendByCategory(from: Long, to: Long): Flow<List<CategorySum>>

    @Query(
        """SELECT COALESCE(SUM(CASE WHEN direction = 'DEBIT'
                    THEN amountPaise - COALESCE(splitOwedPaise, 0) ELSE 0 END), 0)
           FROM transactions WHERE excluded = 0 AND timestamp BETWEEN :from AND :to"""
    )
    fun observeExpense(from: Long, to: Long): Flow<Long>

    @Query(
        """SELECT COALESCE(SUM(CASE WHEN direction = 'DEBIT'
                    THEN amountPaise - COALESCE(splitOwedPaise, 0) ELSE 0 END), 0)
           FROM transactions WHERE excluded = 0 AND timestamp BETWEEN :from AND :to"""
    )
    suspend fun expenseTotal(from: Long, to: Long): Long

    @Query(
        """SELECT categoryId, SUM(amountPaise - COALESCE(splitOwedPaise, 0)) AS totalPaise
           FROM transactions
           WHERE direction = 'DEBIT' AND excluded = 0 AND timestamp BETWEEN :from AND :to
           GROUP BY categoryId ORDER BY totalPaise DESC"""
    )
    suspend fun spendByCategory(from: Long, to: Long): List<CategorySum>

    @Query(
        """SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amountPaise ELSE 0 END), 0)
           FROM transactions WHERE excluded = 0 AND timestamp BETWEEN :from AND :to"""
    )
    fun observeIncome(from: Long, to: Long): Flow<Long>

    @Query(
        """SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day,
                  SUM(amountPaise - COALESCE(splitOwedPaise, 0)) AS totalPaise
           FROM transactions
           WHERE direction = 'DEBIT' AND excluded = 0 AND timestamp BETWEEN :from AND :to
           GROUP BY day ORDER BY day"""
    )
    fun observeDailySpend(from: Long, to: Long): Flow<List<DaySum>>

    @Query(
        """SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') AS month,
                  SUM(CASE WHEN direction = 'CREDIT' THEN amountPaise ELSE 0 END) AS incomePaise,
                  SUM(CASE WHEN direction = 'DEBIT'
                      THEN amountPaise - COALESCE(splitOwedPaise, 0) ELSE 0 END) AS expensePaise
           FROM transactions
           WHERE excluded = 0 AND timestamp >= :from
           GROUP BY month ORDER BY month"""
    )
    fun observeMonthlyTotals(from: Long): Flow<List<MonthSum>>

    @Query(
        """SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') AS month,
                  categoryId,
                  SUM(amountPaise - COALESCE(splitOwedPaise, 0)) AS totalPaise
           FROM transactions
           WHERE direction = 'DEBIT' AND excluded = 0
           GROUP BY month, categoryId"""
    )
    fun observeMonthlyCategorySpend(): Flow<List<MonthCatSum>>

    @Query(
        """SELECT merchantNorm, merchant, COUNT(*) AS count,
                  SUM(amountPaise - COALESCE(splitOwedPaise, 0)) AS totalPaise
           FROM transactions
           WHERE direction = 'DEBIT' AND excluded = 0 AND merchantNorm IS NOT NULL
             AND timestamp BETWEEN :from AND :to
           GROUP BY merchantNorm ORDER BY totalPaise DESC LIMIT :limit"""
    )
    fun observeTopMerchants(from: Long, to: Long, limit: Int): Flow<List<MerchantStat>>

    /** All debit txns of merchants seen >= 2 times — recurring detection input. */
    @Query(
        """SELECT * FROM transactions
           WHERE direction = 'DEBIT' AND excluded = 0 AND merchantNorm IN (
               SELECT merchantNorm FROM transactions
               WHERE merchantNorm IS NOT NULL AND direction = 'DEBIT' AND excluded = 0
               GROUP BY merchantNorm HAVING COUNT(*) >= 2
           )
           ORDER BY merchantNorm, timestamp"""
    )
    fun observeRecurringCandidates(): Flow<List<Txn>>

    /** This-month debits + full history of their merchants — anomaly detection input. */
    @Query(
        """SELECT * FROM transactions
           WHERE direction = 'DEBIT' AND excluded = 0 AND merchantNorm IN (
               SELECT DISTINCT merchantNorm FROM transactions
               WHERE merchantNorm IS NOT NULL AND direction = 'DEBIT' AND excluded = 0
                 AND timestamp BETWEEN :from AND :to
           )
           ORDER BY merchantNorm, timestamp"""
    )
    fun observeAnomalyCandidates(from: Long, to: Long): Flow<List<Txn>>

    /** Unsettled splits — money others owe you. */
    @Query(
        """SELECT * FROM transactions
           WHERE splitOwedPaise IS NOT NULL AND splitOwedPaise > 0 AND splitSettled = 0
           ORDER BY timestamp DESC"""
    )
    fun observeOpenSplits(): Flow<List<Txn>>

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE merchantNorm = :merchantNorm")
    suspend fun recategorizeMerchant(merchantNorm: String, categoryId: Long)

    @Query("UPDATE transactions SET excluded = :excluded WHERE merchantNorm = :merchantNorm")
    suspend fun setExcludedForMerchant(merchantNorm: String, excluded: Boolean)

    @Query(
        """UPDATE transactions SET categoryId = :categoryId, excluded = :excluded
           WHERE smsSender = :sender AND direction = :direction AND merchantNorm IS NULL"""
    )
    suspend fun recategorizeBySender(sender: String, direction: String, categoryId: Long, excluded: Boolean)

    @Query("UPDATE transactions SET categoryId = :fallback WHERE categoryId = :categoryId")
    suspend fun reassignCategory(categoryId: Long, fallback: Long)

    @Query("SELECT * FROM transactions ORDER BY timestamp ASC")
    suspend fun allForExport(): List<Txn>

    /** Sums per account for spends within a statement cycle. */
    @Query(
        """SELECT COALESCE(SUM(amountPaise - COALESCE(splitOwedPaise, 0)), 0)
           FROM transactions
           WHERE direction = 'DEBIT' AND excluded = 0 AND accountId = :accountId
             AND timestamp BETWEEN :from AND :to"""
    )
    suspend fun cycleSpend(accountId: Long, from: Long, to: Long): Long

    @Query(
        """SELECT * FROM transactions
           WHERE accountId = :accountId AND timestamp BETWEEN :from AND :to
           ORDER BY timestamp DESC"""
    )
    suspend fun cycleTxns(accountId: Long, from: Long, to: Long): List<Txn>
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<Category>): List<Long>

    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT * FROM categories ORDER BY sortOrder")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sortOrder")
    suspend fun all(): List<Category>

    @Query("SELECT * FROM categories WHERE `key` = :key LIMIT 1")
    suspend fun byKey(key: String): Category?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)

    @Query("SELECT * FROM accounts ORDER BY createdAt")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE tail = :tail LIMIT 1")
    suspend fun byTail(tail: String): Account?

    @Query("SELECT * FROM accounts ORDER BY createdAt")
    suspend fun all(): List<Account>
}

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: Budget): Long

    @Update
    suspend fun update(budget: Budget)

    @Delete
    suspend fun delete(budget: Budget)

    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE categoryId IS NULL LIMIT 1")
    suspend fun overall(): Budget?

    @Query(
        """SELECT * FROM budgets
           WHERE (:categoryId IS NULL AND categoryId IS NULL) OR categoryId = :categoryId
           LIMIT 1"""
    )
    suspend fun forCategory(categoryId: Long?): Budget?
}

@Dao
interface GoalDao {
    @Insert
    suspend fun insert(goal: Goal): Long

    @Update
    suspend fun update(goal: Goal)

    @Delete
    suspend fun delete(goal: Goal)

    @Query("SELECT * FROM goals ORDER BY createdAt")
    fun observeAll(): Flow<List<Goal>>
}

@Dao
interface MerchantMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: MerchantMapping)

    @Query("SELECT * FROM merchant_mappings WHERE merchantNorm = :merchantNorm")
    suspend fun byMerchant(merchantNorm: String): MerchantMapping?

    @Query("SELECT * FROM merchant_mappings")
    suspend fun all(): List<MerchantMapping>

    @Query("SELECT * FROM merchant_mappings ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MerchantMapping>>
}

@Dao
interface CardBillDao {
    @Insert
    suspend fun insert(card: CardBill): Long

    @Update
    suspend fun update(card: CardBill)

    @Delete
    suspend fun delete(card: CardBill)

    @Query("SELECT * FROM card_bills ORDER BY dueDay")
    fun observeAll(): Flow<List<CardBill>>

    @Query("SELECT * FROM card_bills")
    suspend fun all(): List<CardBill>

    @Query("SELECT * FROM card_bills WHERE tail = :tail LIMIT 1")
    suspend fun byTail(tail: String): CardBill?

    @Query("SELECT COUNT(*) FROM card_bills")
    suspend fun count(): Int
}

@Dao
interface DupDismissalDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(dismissal: DupDismissal)

    @Query("SELECT * FROM dup_dismissals")
    suspend fun all(): List<DupDismissal>
}
