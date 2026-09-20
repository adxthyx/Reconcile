package com.abc.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abc.expensetracker.ThemeMode
import com.abc.expensetracker.data.Account
import com.abc.expensetracker.data.Category
import com.abc.expensetracker.data.CategorySum
import com.abc.expensetracker.data.DaySum
import com.abc.expensetracker.data.Txn
import com.abc.expensetracker.sms.ImportState
import com.abc.expensetracker.sms.parser.Direction
import com.abc.expensetracker.ui.charts.MoneyPulseChart
import com.abc.expensetracker.ui.common.AttentionTone
import com.abc.expensetracker.ui.common.EmptyState
import com.abc.expensetracker.ui.common.Metric
import com.abc.expensetracker.ui.common.MoneyText
import com.abc.expensetracker.ui.common.MoneyTone
import com.abc.expensetracker.ui.common.ProgressBar
import com.abc.expensetracker.ui.common.SectionHeader
import com.abc.expensetracker.ui.common.StatusAttentionRow
import com.abc.expensetracker.ui.common.TransactionRow
import com.abc.expensetracker.ui.theme.KharchaTheme
import com.abc.expensetracker.ui.theme.ReconcileColors
import com.abc.expensetracker.ui.theme.ReconcileRadii
import com.abc.expensetracker.ui.theme.ReconcileSpacing
import com.abc.expensetracker.ui.vm.BudgetRow
import com.abc.expensetracker.ui.vm.BudgetsVm
import com.abc.expensetracker.ui.vm.CardRow
import com.abc.expensetracker.ui.vm.DupPair
import com.abc.expensetracker.ui.vm.HomeVm
import com.abc.expensetracker.ui.vm.MoreVm
import com.abc.expensetracker.util.Money
import java.time.LocalDate

@Composable
fun HomeScreen(
    vm: HomeVm,
    planVm: BudgetsVm,
    dataVm: MoreVm,
    onOpenSettings: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    onSeeActivity: () -> Unit,
    onSeeInsights: () -> Unit,
    onOpenPlan: (String) -> Unit,
    onOpenDataQuality: () -> Unit,
) {
    val expense by vm.expense.collectAsStateWithLifecycle()
    val income by vm.income.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val spendByCategory by vm.spendByCategory.collectAsStateWithLifecycle()
    val dailySpend by vm.dailySpend.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val budgetRows by planVm.rows.collectAsStateWithLifecycle()
    val cards by planVm.cards.collectAsStateWithLifecycle()
    val duplicates by dataVm.dupPairs.collectAsStateWithLifecycle()
    val uncategorized by dataVm.uncategorized.collectAsStateWithLifecycle()

    HomeContent(
        monthName = vm.monthName,
        expense = expense,
        income = income,
        recent = recent,
        categories = categories,
        accounts = accounts,
        spendByCategory = spendByCategory,
        dailySpend = dailySpend,
        overallBudget = budgetRows.firstOrNull { it.budget.categoryId == null },
        nextCard = cards.firstOrNull { !it.paidThisCycle },
        duplicates = duplicates,
        uncategorizedCount = uncategorized.size,
        importState = importState,
        onOpenSettings = onOpenSettings,
        onTransactionClick = onTransactionClick,
        onSeeActivity = onSeeActivity,
        onSeeInsights = onSeeInsights,
        onOpenPlan = onOpenPlan,
        onOpenDataQuality = onOpenDataQuality,
    )
}

@Composable
internal fun HomeContent(
    monthName: String,
    expense: Long,
    income: Long,
    recent: List<Txn>,
    categories: List<Category>,
    accounts: List<Account>,
    spendByCategory: List<CategorySum>,
    dailySpend: List<DaySum>,
    overallBudget: BudgetRow?,
    nextCard: CardRow?,
    duplicates: List<DupPair>,
    uncategorizedCount: Int,
    importState: ImportState,
    onOpenSettings: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    onSeeActivity: () -> Unit,
    onSeeInsights: () -> Unit,
    onOpenPlan: (String) -> Unit,
    onOpenDataQuality: () -> Unit,
) {
    val categoryById = categories.associateBy { it.id }
    val accountById = accounts.associateBy { it.id }
    val net = income - expense
    val overBudget = overallBudget?.let { expense > it.effectiveLimitPaise } == true
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize().background(ReconcileColors.Ink),
        contentPadding = PaddingValues(
            start = ReconcileSpacing.Screen,
            end = ReconcileSpacing.Screen,
            bottom = 28.dp,
        ),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        monthName.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = ReconcileColors.Mint,
                    )
                    Text(
                        "Your money, at a glance",
                        style = MaterialTheme.typography.headlineLarge,
                        color = ReconcileColors.Text,
                    )
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ReconcileColors.Elevated)
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("A", style = MaterialTheme.typography.titleMedium, color = ReconcileColors.Mint)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("SPENT THIS MONTH", style = MaterialTheme.typography.labelLarge, color = ReconcileColors.TextSecondary)
            MoneyText(expense, style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(12.dp))
            overallBudget?.let { budget ->
                ProgressBar(
                    progress = if (budget.effectiveLimitPaise > 0) expense.toFloat() / budget.effectiveLimitPaise else 0f,
                    color = if (overBudget) ReconcileColors.Coral else ReconcileColors.Mint,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (overBudget) {
                        "${Money.format(expense - budget.effectiveLimitPaise)} over your ${Money.format(budget.effectiveLimitPaise)} plan"
                    } else {
                        "${Money.format(budget.effectiveLimitPaise - expense)} left in your ${Money.format(budget.effectiveLimitPaise)} plan"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (overBudget) ReconcileColors.Coral else ReconcileColors.Mint,
                )
            } ?: Text(
                "Set a monthly plan to track your pace",
                modifier = Modifier.clickable { onOpenPlan("budget") },
                style = MaterialTheme.typography.bodyLarge,
                color = ReconcileColors.Mint,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                Metric("In", Money.format(income), Modifier.weight(1f), MoneyTone.Income)
                Metric("Out", Money.format(expense), Modifier.weight(1f))
                Metric(
                    "Net",
                    (if (net < 0) "−" else "+") + Money.format(kotlin.math.abs(net)),
                    Modifier.weight(1f),
                    if (net < 0) MoneyTone.Risk else MoneyTone.Income,
                )
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
            MoneyPulseChart(dailySpend = dailySpend, modifier = Modifier.fillMaxWidth())
        }

        if (duplicates.isNotEmpty() || nextCard != null || uncategorizedCount > 0 || importState is ImportState.Running) {
            item { Spacer(Modifier.height(24.dp)); SectionHeader("Needs your eyes") }
            if (duplicates.isNotEmpty()) {
                item {
                    val worth = duplicates.sumOf { it.a.amountPaise }
                    StatusAttentionRow(
                        title = "${duplicates.size} possible duplicate${if (duplicates.size == 1) "" else "s"}",
                        subtitle = "${Money.format(worth)} ready to review",
                        icon = Icons.Outlined.ErrorOutline,
                        tone = AttentionTone.Amber,
                        onClick = onOpenDataQuality,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            nextCard?.let { card ->
                item {
                    StatusAttentionRow(
                        title = "${card.card.name} card ${dueLabel(card)}",
                        subtitle = card.runningCycleSpendPaise?.let { Money.format(it) } ?: "Amount unavailable",
                        icon = Icons.Outlined.CreditCard,
                        tone = AttentionTone.Indigo,
                        onClick = { onOpenPlan("bills") },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (uncategorizedCount > 0) {
                item {
                    StatusAttentionRow(
                        title = "$uncategorizedCount uncategorized transaction${if (uncategorizedCount == 1) "" else "s"}",
                        subtitle = "Help Reconcile learn these merchants",
                        icon = Icons.Outlined.Tune,
                        onClick = onOpenDataQuality,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            (importState as? ImportState.Running)?.let { running ->
                item {
                    StatusAttentionRow(
                        title = "Scanning SMS inbox",
                        subtitle = "${running.scanned}/${running.total} checked · ${running.imported} imported",
                        icon = Icons.Outlined.ErrorOutline,
                        onClick = {},
                    )
                }
            }
        }

        if (spendByCategory.isNotEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Top spend") {
                    TextButton(onClick = onSeeInsights) { Text("See all", color = ReconcileColors.Mint) }
                }
            }
            spendByCategory.take(3).forEach { spend ->
                item(key = "category-${spend.categoryId}") {
                    val category = categoryById[spend.categoryId]
                    val maximum = spendByCategory.firstOrNull()?.totalPaise?.coerceAtLeast(1L) ?: 1L
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            category?.name ?: "Other",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ReconcileColors.Text,
                        )
                        MoneyText(spend.totalPaise, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(
                        progress = spend.totalPaise.toFloat() / maximum,
                        color = category?.let { Color(it.color) } ?: ReconcileColors.Indigo,
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            SectionHeader("Recent activity") {
                TextButton(onClick = onSeeActivity) { Text("See all", color = ReconcileColors.Mint) }
            }
        }
        if (recent.isEmpty()) {
            item {
                EmptyState(
                    title = "No activity yet",
                    subtitle = "Bank transactions will appear after the local SMS scan.",
                )
            }
        } else {
            recent.take(4).forEach { txn ->
                item(key = "recent-${txn.id}") {
                    TransactionRow(
                        txn = txn,
                        category = categoryById[txn.categoryId],
                        accountName = accountById[txn.accountId]?.displayLabel(),
                        onClick = { onTransactionClick(txn.id) },
                    )
                }
            }
        }
    }
}

private fun dueLabel(row: CardRow): String = when {
    row.daysLeft < 0 -> "overdue"
    row.daysLeft == 0L -> "due today"
    row.daysLeft == 1L -> "due tomorrow"
    else -> "due in ${row.daysLeft} days"
}

private fun Account.displayLabel(): String =
    tail?.takeUnless { name.contains(it) }?.let { "$name ·$it" } ?: name

@Preview(showBackground = true, backgroundColor = 0xFF090C10, widthDp = 360, heightDp = 800)
@Composable
private fun HomePreview() {
    val food = Category(1, "food", "Food & Dining", "", 0xFFFF8A65, sortOrder = 0)
    val shopping = Category(2, "shopping", "Shopping", "", 0xFFFF72AF, sortOrder = 1)
    val txn = Txn(1, 508_00, Direction.DEBIT, "Swiggy", "swiggy", 1, null, System.currentTimeMillis())
    KharchaTheme(ThemeMode.DARK) {
        HomeContent(
            monthName = "August 2026",
            expense = 37_940_00,
            income = 25_818_00,
            recent = listOf(txn),
            categories = listOf(food, shopping),
            accounts = emptyList(),
            spendByCategory = listOf(CategorySum(1, 8_320_00), CategorySum(2, 7_980_00)),
            dailySpend = listOf(
                DaySum("2026-08-21", 300_00),
                DaySum("2026-08-22", 500_00),
                DaySum("2026-08-23", 200_00),
                DaySum("2026-08-24", 900_00),
                DaySum("2026-08-25", 600_00),
                DaySum("2026-08-26", 1_100_00),
                DaySum("2026-08-27", 800_00),
            ),
            overallBudget = null,
            nextCard = null,
            duplicates = emptyList(),
            uncategorizedCount = 2,
            importState = ImportState.Idle,
            onOpenSettings = {},
            onTransactionClick = {},
            onSeeActivity = {},
            onSeeInsights = {},
            onOpenPlan = {},
            onOpenDataQuality = {},
        )
    }
}
