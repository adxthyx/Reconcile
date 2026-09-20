package com.abc.expensetracker.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abc.expensetracker.AppContainer
import com.abc.expensetracker.ThemeMode
import com.abc.expensetracker.data.Account
import com.abc.expensetracker.data.Budget
import com.abc.expensetracker.data.CardBill
import com.abc.expensetracker.data.Category
import com.abc.expensetracker.data.CategorySum
import com.abc.expensetracker.data.DupDismissal
import com.abc.expensetracker.data.Goal
import com.abc.expensetracker.data.MonthSum
import com.abc.expensetracker.data.MerchantMapping
import com.abc.expensetracker.data.Txn
import com.abc.expensetracker.data.TxnSource
import com.abc.expensetracker.bills.BillReminders
import com.abc.expensetracker.sms.ImportState
import com.abc.expensetracker.sms.parser.Direction
import com.abc.expensetracker.util.Dates
import com.abc.expensetracker.util.Recurring
import com.abc.expensetracker.util.RecurringItem
import com.abc.expensetracker.widget.TodayWidget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

class VmFactory(private val c: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        HomeVm::class.java -> HomeVm(c)
        TxnListVm::class.java -> TxnListVm(c)
        StatsVm::class.java -> StatsVm(c)
        BudgetsVm::class.java -> BudgetsVm(c)
        MoreVm::class.java -> MoreVm(c)
        else -> throw IllegalArgumentException("Unknown VM $modelClass")
    } as T
}

private fun <T> Flow<T>.state(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(1000), initial)

// ------------------------------------------------------------------- HOME

class HomeVm(private val c: AppContainer) : ViewModel() {
    private val month = Dates.currentMonth()
    private val range = Dates.monthRange(month)

    val monthName = Dates.monthFull(month)
    val expense = c.db.txnDao().observeExpense(range.first, range.last).state(this, 0L)
    val income = c.db.txnDao().observeIncome(range.first, range.last).state(this, 0L)
    val recent = c.db.txnDao().observeRecent(8).state(this, emptyList())
    val categories = c.db.categoryDao().observeAll().state(this, emptyList())
    val accounts = c.db.accountDao().observeAll().state(this, emptyList())
    val dailySpend = c.db.txnDao().observeDailySpend(range.first, range.last).state(this, emptyList())
    val spendByCategory = c.db.txnDao().observeSpendByCategory(range.first, range.last)
        .state(this, emptyList())
    val budgets = c.db.budgetDao().observeAll().state(this, emptyList())
    val txnCount = c.db.txnDao().observeCount().state(this, 0)
    val importState: StateFlow<ImportState> = c.importer.state

    /** null = DataStore not loaded yet — callers must wait for a real value. */
    val importDone: StateFlow<Boolean?> =
        c.settings.historicalImportDone.map { it as Boolean? }.state(this, null)

    fun runHistoricalImport() {
        viewModelScope.launch {
            c.importer.importInbox()
            c.settings.setHistoricalImportDone()
        }
    }
}

// ----------------------------------------------------------- TRANSACTIONS

data class TxnFilter(
    val query: String = "",
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val direction: Direction? = null,
    val tag: String = "",
    val month: YearMonth? = Dates.currentMonth(), // null = all time
)

@OptIn(ExperimentalCoroutinesApi::class)
class TxnListVm(private val c: AppContainer) : ViewModel() {
    val filter = MutableStateFlow(TxnFilter())
    val categories = c.db.categoryDao().observeAll().state(this, emptyList())
    val accounts = c.db.accountDao().observeAll().state(this, emptyList())

    val txns: StateFlow<List<Txn>> = filter.flatMapLatest { f ->
        val range = f.month?.let { Dates.monthRange(it) } ?: 0L..Long.MAX_VALUE
        c.db.txnDao().observeFiltered(
            from = range.first,
            to = range.last,
            accountId = f.accountId,
            categoryId = f.categoryId,
            direction = f.direction?.name,
            tag = f.tag,
            query = f.query.trim(),
        )
    }.flowOn(Dispatchers.Default).state(this, emptyList())

    /** distinct tags across all txns for the filter row */
    val allTags: StateFlow<List<String>> = c.db.txnDao().observeAllTxns().map { list ->
        list.asSequence()
            .mapNotNull { it.tags }
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .toList()
    }.state(this, emptyList())

    fun setQuery(q: String) = filter.update { copy(query = q) }
    fun setCategory(id: Long?) = filter.update { copy(categoryId = id) }
    fun setAccount(id: Long?) = filter.update { copy(accountId = id) }
    fun setDirection(d: Direction?) = filter.update { copy(direction = d) }
    fun setMonth(m: YearMonth?) = filter.update { copy(month = m) }
    fun setTag(t: String) = filter.update { copy(tag = t) }

    fun showUncategorized(categoryId: Long) {
        filter.value = TxnFilter(categoryId = categoryId, month = null)
    }

    fun observeTransaction(id: Long): Flow<Txn?> = c.db.txnDao().observeById(id)

    private inline fun MutableStateFlow<TxnFilter>.update(block: TxnFilter.() -> TxnFilter) {
        value = value.block()
    }

    /** Category correction — feeds the learning layer (merchant or sender). */
    fun recategorize(txn: Txn, categoryId: Long) {
        viewModelScope.launch { c.repo.setCategoryLearning(txn, categoryId) }
    }

    /** Change only this row; unlike [recategorize], this does not create a merchant rule. */
    fun recategorizeOnly(txn: Txn, categoryId: Long) {
        viewModelScope.launch {
            val category = c.db.categoryDao().all().firstOrNull { it.id == categoryId }
            c.db.txnDao().update(
                txn.copy(
                    categoryId = categoryId,
                    excluded = category?.excludeFromTotals ?: false,
                )
            )
        }
    }

    /** Explicit, warned action from the detail editor: update history and future matches. */
    fun applyMerchantRule(txn: Txn, categoryId: Long, excluded: Boolean) {
        viewModelScope.launch {
            c.repo.setCategoryLearning(txn, categoryId)
            c.repo.setExcludedLearning(txn.copy(categoryId = categoryId), excluded, always = true)
        }
    }

    /**
     * Saves the fields edited together in Transaction Detail as one Room row update.
     * When [learnRule] is true, category/exclusion are first propagated through the
     * existing repository learning path; tags and split data remain transaction-only.
     */
    fun updateTransactionDetails(
        txn: Txn,
        categoryId: Long,
        excluded: Boolean,
        tags: String,
        splitOwedPaise: Long?,
        splitWith: String,
        learnRule: Boolean,
    ) {
        viewModelScope.launch {
            if (learnRule) {
                c.repo.setCategoryLearning(txn, categoryId)
                c.repo.setExcludedLearning(
                    txn.copy(categoryId = categoryId),
                    excluded = excluded,
                    always = true,
                )
            }
            val current = c.db.txnDao().byId(txn.id) ?: return@launch
            c.db.txnDao().update(
                current.copy(
                    categoryId = categoryId,
                    excluded = excluded,
                    tags = tags.ifBlank { null },
                    splitOwedPaise = splitOwedPaise?.takeIf { it > 0 },
                    splitWith = splitWith.ifBlank { null },
                    splitSettled = if (splitOwedPaise != current.splitOwedPaise) false else current.splitSettled,
                )
            )
        }
    }

    /** Exclude from all totals (self transfers etc.); always=true teaches it. */
    fun setExcluded(txn: Txn, excluded: Boolean, always: Boolean) {
        viewModelScope.launch { c.repo.setExcludedLearning(txn, excluded, always) }
    }

    fun setTags(txn: Txn, tags: String) {
        viewModelScope.launch {
            c.db.txnDao().update(txn.copy(tags = tags.ifBlank { null }))
        }
    }

    fun setSplit(txn: Txn, owedPaise: Long?, with: String?) {
        viewModelScope.launch {
            c.db.txnDao().update(
                txn.copy(
                    splitOwedPaise = owedPaise?.takeIf { it > 0 },
                    splitWith = with?.ifBlank { null },
                    splitSettled = false,
                )
            )
        }
    }

    fun delete(txn: Txn) {
        viewModelScope.launch { c.db.txnDao().delete(txn) }
    }

    fun addManual(
        amountPaise: Long,
        direction: Direction,
        merchant: String,
        categoryId: Long,
        accountId: Long?,
        note: String?,
    ) {
        viewModelScope.launch {
            val category = c.db.categoryDao().all().firstOrNull { it.id == categoryId }
            c.db.txnDao().insert(
                Txn(
                    amountPaise = amountPaise,
                    direction = direction,
                    merchant = merchant.ifBlank { null },
                    merchantNorm = merchant.ifBlank { null }?.let { c.repo.normalizeMerchant(it) },
                    categoryId = categoryId,
                    accountId = accountId,
                    timestamp = System.currentTimeMillis(),
                    note = note?.ifBlank { null },
                    source = TxnSource.MANUAL,
                    // Built-in non-spend categories (Friends, transfers and
                    // card payments) must behave the same for manual entries
                    // as they do for imported or recategorized transactions.
                    excluded = category?.excludeFromTotals ?: false,
                )
            )
        }
    }
}

// ------------------------------------------------------------------ STATS

data class CategorySpend(val category: Category, val totalPaise: Long)
data class CategoryShift(
    val category: Category,
    val currentPaise: Long,
    val previousPaise: Long,
) {
    val deltaPaise: Long get() = currentPaise - previousPaise
}

class StatsVm(private val c: AppContainer) : ViewModel() {
    val month = MutableStateFlow(Dates.currentMonth())

    @OptIn(ExperimentalCoroutinesApi::class)
    val spendByCategory: StateFlow<List<CategorySpend>> = month.flatMapLatest { m ->
        val r = Dates.monthRange(m)
        combine(
            c.db.txnDao().observeSpendByCategory(r.first, r.last),
            c.db.categoryDao().observeAll(),
        ) { sums: List<CategorySum>, cats: List<Category> ->
            val byId = cats.associateBy { it.id }
            sums.mapNotNull { s -> byId[s.categoryId]?.let { CategorySpend(it, s.totalPaise) } }
                .filter { !it.category.isIncome }
        }
    }.state(this, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSpend: StateFlow<Long> = month.flatMapLatest { m ->
        val r = Dates.monthRange(m)
        c.db.txnDao().observeExpense(r.first, r.last)
    }.state(this, 0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val previousSpend: StateFlow<Long> = month.flatMapLatest { m ->
        val r = Dates.monthRange(m.minusMonths(1))
        c.db.txnDao().observeExpense(r.first, r.last)
    }.state(this, 0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val categoryShifts: StateFlow<List<CategoryShift>> = month.flatMapLatest { m ->
        val currentRange = Dates.monthRange(m)
        val previousRange = Dates.monthRange(m.minusMonths(1))
        combine(
            c.db.txnDao().observeSpendByCategory(currentRange.first, currentRange.last),
            c.db.txnDao().observeSpendByCategory(previousRange.first, previousRange.last),
            c.db.categoryDao().observeAll(),
        ) { current, previous, categories ->
            val currentById = current.associate { it.categoryId to it.totalPaise }
            val previousById = previous.associate { it.categoryId to it.totalPaise }
            categories.asSequence()
                .filter { !it.isIncome && !it.excludeFromTotals }
                .map { category ->
                    CategoryShift(
                        category = category,
                        currentPaise = currentById[category.id] ?: 0L,
                        previousPaise = previousById[category.id] ?: 0L,
                    )
                }
                .filter { it.deltaPaise != 0L }
                .sortedByDescending { abs(it.deltaPaise) }
                .toList()
        }
    }.state(this, emptyList())

    val overallBudget: StateFlow<Budget?> = c.db.budgetDao().observeAll()
        .map { budgets -> budgets.firstOrNull { it.categoryId == null } }
        .state(this, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailySpend = month.flatMapLatest { m ->
        val r = Dates.monthRange(m)
        c.db.txnDao().observeDailySpend(r.first, r.last)
    }.state(this, emptyList())

    val monthlyTotals: StateFlow<List<MonthSum>> = run {
        val from = Dates.monthRange(Dates.currentMonth().minusMonths(5)).first
        c.db.txnDao().observeMonthlyTotals(from).state(this, emptyList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val topMerchants = month.flatMapLatest { m ->
        val r = Dates.monthRange(m)
        c.db.txnDao().observeTopMerchants(r.first, r.last, 8)
    }.state(this, emptyList())

    fun prevMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() {
        if (month.value < Dates.currentMonth()) month.value = month.value.plusMonths(1)
    }
}

// ---------------------------------------------------------------- BUDGETS

data class BudgetRow(
    val budget: Budget,
    val category: Category?,
    val spentPaise: Long,
    /** limit + rollover carry when envelope mode is on */
    val effectiveLimitPaise: Long,
)

data class CardRow(
    val card: CardBill,
    val dueDate: LocalDate,
    val daysLeft: Long,
    val paidThisCycle: Boolean,
    /** spend on the linked account since the last statement date */
    val runningCycleSpendPaise: Long?,
    val accountId: Long?,
)

class BudgetsVm(private val c: AppContainer) : ViewModel() {
    private val range = Dates.monthRange(Dates.currentMonth())

    val categories = c.db.categoryDao().observeAll().state(this, emptyList())

    val rows: StateFlow<List<BudgetRow>> = combine(
        c.db.budgetDao().observeAll(),
        c.db.categoryDao().observeAll(),
        c.db.txnDao().observeSpendByCategory(range.first, range.last),
        c.db.txnDao().observeExpense(range.first, range.last),
        c.db.txnDao().observeMonthlyCategorySpend(),
    ) { budgets, cats, sums, totalExpense, monthlySpend ->
        val catById = cats.associateBy { it.id }
        val spentByCat = sums.associate { it.categoryId to it.totalPaise }
        val thisMonth = Dates.currentMonth()
        budgets.map { b ->
            val spent = if (b.categoryId == null) totalExpense else spentByCat[b.categoryId] ?: 0L
            val effective = if (!b.rollover) b.limitPaise else {
                // envelope: carry = clamp(carry + limit - spent) month by month
                val startMonth = if (b.createdAt > 0) {
                    YearMonth.from(Dates.toLocalDate(b.createdAt))
                } else {
                    monthlySpend.minOfOrNull { YearMonth.parse(it.month) } ?: thisMonth
                }
                val spendPerMonth = monthlySpend
                    .filter { b.categoryId == null || it.categoryId == b.categoryId }
                    .groupBy { it.month }
                    .mapValues { (_, v) -> v.sumOf { it.totalPaise } }
                var carry = 0L
                var m = startMonth
                while (m < thisMonth) {
                    carry = (carry + b.limitPaise - (spendPerMonth[m.toString()] ?: 0L))
                        .coerceAtLeast(0L)
                    m = m.plusMonths(1)
                }
                b.limitPaise + carry
            }
            BudgetRow(
                budget = b,
                category = b.categoryId?.let { catById[it] },
                spentPaise = spent,
                effectiveLimitPaise = effective,
            )
        }.sortedBy { it.category?.sortOrder ?: -1 }
    }.flowOn(Dispatchers.Default).state(this, emptyList())

    val recurring: StateFlow<List<RecurringItem>> =
        c.db.txnDao().observeRecurringCandidates()
            .map { Recurring.detect(it) }
            .flowOn(Dispatchers.Default)
            .state(this, emptyList())

    // ------------------------------------------------------------ cards

    val cards: StateFlow<List<CardRow>> = combine(
        c.db.cardBillDao().observeAll(),
        c.db.accountDao().observeAll(),
        c.db.txnDao().observeAllTxns(),
    ) { cards, accounts, txns ->
        val today = LocalDate.now()
        cards.map { card ->
            val due = BillReminders.dueDateFor(card, today)
            val account = card.tail?.let { t -> accounts.find { it.tail == t } }
            val cycleSpend = account?.let { acc ->
                val lastStatement = lastStatementDate(card.statementDay, today)
                val fromMs = lastStatement.plusDays(1)
                    .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                txns.asSequence()
                    .filter {
                        it.accountId == acc.id && it.direction == Direction.DEBIT &&
                            !it.excluded && it.timestamp >= fromMs
                    }
                    .sumOf { it.amountPaise - (it.splitOwedPaise ?: 0L) }
            }
            CardRow(
                card = card,
                dueDate = due,
                daysLeft = due.toEpochDay() - today.toEpochDay(),
                paidThisCycle = card.lastPaidCycle == BillReminders.paidStateCycleKey(today),
                runningCycleSpendPaise = cycleSpend,
                accountId = account?.id,
            )
        }.sortedBy { it.dueDate }
    }.flowOn(Dispatchers.Default).state(this, emptyList())

    private fun lastStatementDate(statementDay: Int, today: LocalDate): LocalDate {
        val ym = YearMonth.from(today)
        val thisMonth = ym.atDay(statementDay.coerceAtMost(ym.lengthOfMonth()))
        return if (thisMonth.isAfter(today)) {
            val prev = ym.minusMonths(1)
            prev.atDay(statementDay.coerceAtMost(prev.lengthOfMonth()))
        } else thisMonth
    }

    fun markPaid(row: CardRow) {
        viewModelScope.launch {
            c.db.cardBillDao().update(
                row.card.copy(lastPaidCycle = BillReminders.paidStateCycleKey(LocalDate.now()))
            )
        }
    }

    fun markUnpaid(row: CardRow) {
        viewModelScope.launch { c.db.cardBillDao().update(row.card.copy(lastPaidCycle = null)) }
    }

    fun saveCard(
        existing: CardBill?,
        name: String,
        tail: String?,
        dueDay: Int,
        statementDay: Int,
        enabled: Boolean = existing?.enabled ?: true,
    ) {
        viewModelScope.launch {
            if (existing == null) {
                c.db.cardBillDao().insert(
                    CardBill(
                        name = name,
                        tail = tail,
                        dueDay = dueDay,
                        statementDay = statementDay,
                        enabled = enabled,
                    )
                )
            } else {
                c.db.cardBillDao().update(
                    existing.copy(
                        name = name,
                        tail = tail,
                        dueDay = dueDay,
                        statementDay = statementDay,
                        enabled = enabled,
                    )
                )
            }
        }
    }

    fun deleteCard(card: CardBill) {
        viewModelScope.launch { c.db.cardBillDao().delete(card) }
    }

    /** Transactions of a card's current statement cycle for the reconcile view. */
    suspend fun cycleTxns(row: CardRow): List<Txn> {
        val acc = row.accountId ?: return emptyList()
        val today = LocalDate.now()
        val from = lastStatementDate(row.card.statementDay, today).plusDays(1)
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return c.db.txnDao().cycleTxns(acc, from, System.currentTimeMillis())
    }

    fun setBudget(existing: Budget?, categoryId: Long?, limitPaise: Long, rollover: Boolean) {
        viewModelScope.launch {
            val current = existing ?: c.db.budgetDao().forCategory(categoryId)
            if (current == null) {
                c.db.budgetDao().upsert(Budget(
                    categoryId = categoryId,
                    limitPaise = limitPaise,
                    rollover = rollover,
                    createdAt = System.currentTimeMillis(),
                ))
            } else {
                c.db.budgetDao().update(
                    current.copy(limitPaise = limitPaise, rollover = rollover)
                )
            }
        }
    }

    fun removeBudget(budget: Budget) {
        viewModelScope.launch { c.db.budgetDao().delete(budget) }
    }
}

// ------------------------------------------------------------------- MORE

data class DupPair(val a: Txn, val b: Txn)

class MoreVm(private val c: AppContainer) : ViewModel() {
    val accounts = c.db.accountDao().observeAll().state(this, emptyList())
    val goals = c.db.goalDao().observeAll().state(this, emptyList())
    val themeMode = c.settings.themeMode.state(this, ThemeMode.SYSTEM)
    val txnCount = c.db.txnDao().observeCount().state(this, 0)
    val importState: StateFlow<ImportState> = c.importer.state
    val categories = c.db.categoryDao().observeAll().state(this, emptyList())
    val merchantMappings: StateFlow<List<MerchantMapping>> =
        c.db.merchantMappingDao().observeAll().state(this, emptyList())
    val uncategorized: StateFlow<List<Txn>> = combine(
        c.db.txnDao().observeAllTxns(),
        c.db.categoryDao().observeAll(),
    ) { txns, categories ->
        val otherId = categories.firstOrNull { it.key == "other" }?.id
        if (otherId == null) emptyList()
        else txns.filter { it.categoryId == otherId && !it.excluded }
            .sortedByDescending { it.timestamp }
    }.state(this, emptyList())

    private val dismissalsChanged = MutableStateFlow(0)

    /**
     * Near-duplicate review queue: same amount+direction within 6h that the
     * auto-dedup (±3 min) didn't merge, minus pairs already dismissed.
     */
    val dupPairs: StateFlow<List<DupPair>> = combine(
        c.db.txnDao().observeAllTxns(),
        dismissalsChanged,
    ) { txns, _ ->
        val dismissed = c.db.dupDismissalDao().all()
            .map { it.loId to it.hiId }.toHashSet()
        val window = 6 * 60 * 60 * 1000L
        val autoWindow = 3 * 60 * 1000L
        val pairs = mutableListOf<DupPair>()
        val byKey = txns.groupBy { it.amountPaise to it.direction }
        for ((_, group) in byKey) {
            if (group.size < 2) continue
            val sorted = group.sortedBy { it.timestamp }
            for (i in sorted.indices) {
                for (j in i + 1 until sorted.size) {
                    val a = sorted[i]
                    val b = sorted[j]
                    val gap = b.timestamp - a.timestamp
                    if (gap > window) break
                    if (gap <= autoWindow) continue // auto-layer already judged these
                    val key = minOf(a.id, b.id) to maxOf(a.id, b.id)
                    if (key !in dismissed) pairs += DupPair(a, b)
                }
            }
        }
        pairs.sortedByDescending { it.b.timestamp }.take(20)
    }.flowOn(Dispatchers.Default).state(this, emptyList())

    val openSplits: StateFlow<List<Txn>> = c.db.txnDao().observeOpenSplits().state(this, emptyList())

    fun dismissDup(pair: DupPair) {
        viewModelScope.launch {
            c.db.dupDismissalDao().insert(
                DupDismissal(minOf(pair.a.id, pair.b.id), maxOf(pair.a.id, pair.b.id))
            )
            dismissalsChanged.value++
        }
    }

    fun deleteDup(txn: Txn) {
        viewModelScope.launch { c.db.txnDao().delete(txn) }
    }

    fun settleSplit(txn: Txn) {
        viewModelScope.launch { c.db.txnDao().update(txn.copy(splitSettled = true)) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch {
            c.settings.setThemeMode(mode)
            TodayWidget.refresh(c.appContext)
        }
    }

    fun addGoal(name: String, emoji: String, targetPaise: Long) {
        viewModelScope.launch { c.db.goalDao().insert(Goal(name = name, emoji = emoji, targetPaise = targetPaise)) }
    }

    fun addToGoal(goal: Goal, amountPaise: Long) {
        viewModelScope.launch {
            c.db.goalDao().update(goal.copy(savedPaise = (goal.savedPaise + amountPaise).coerceAtLeast(0)))
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch { c.db.goalDao().delete(goal) }
    }

    fun renameAccount(account: Account, name: String) {
        viewModelScope.launch { c.db.accountDao().update(account.copy(name = name)) }
    }

    fun rerunImport() {
        viewModelScope.launch { c.importer.importInbox() }
    }

    suspend fun buildCsv(): String {
        val txns = c.db.txnDao().allForExport()
        val cats = c.db.categoryDao().all()
        val accs = c.db.accountDao().all()
        return com.abc.expensetracker.util.CsvExport.buildCsv(txns, cats, accs)
    }
}
