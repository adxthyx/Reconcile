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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abc.expensetracker.ThemeMode
import com.abc.expensetracker.data.Budget
import com.abc.expensetracker.data.CardBill
import com.abc.expensetracker.data.Category
import com.abc.expensetracker.data.Goal
import com.abc.expensetracker.ui.common.AttentionTone
import com.abc.expensetracker.ui.common.CategoryIcon
import com.abc.expensetracker.ui.common.EmptyState
import com.abc.expensetracker.ui.common.MoneyText
import com.abc.expensetracker.ui.common.PrimaryButton
import com.abc.expensetracker.ui.common.ProgressBar
import com.abc.expensetracker.ui.common.SecondaryButton
import com.abc.expensetracker.ui.common.SectionHeader
import com.abc.expensetracker.ui.theme.KharchaTheme
import com.abc.expensetracker.ui.theme.ReconcileColors
import com.abc.expensetracker.ui.theme.ReconcileRadii
import com.abc.expensetracker.ui.theme.ReconcileSpacing
import com.abc.expensetracker.ui.vm.BudgetRow
import com.abc.expensetracker.ui.vm.BudgetsVm
import com.abc.expensetracker.ui.vm.CardRow
import com.abc.expensetracker.ui.vm.MoreVm
import com.abc.expensetracker.util.Cadence
import com.abc.expensetracker.util.Dates
import com.abc.expensetracker.util.Money
import com.abc.expensetracker.util.RecurringItem
import java.time.LocalDate

enum class PlanSection(val routeValue: String, val label: String) {
    BUDGET("budget", "Budget"),
    BILLS("bills", "Bills"),
    RECURRING("recurring", "Recurring"),
    GOALS("goals", "Goals");

    companion object {
        fun from(value: String): PlanSection = entries.firstOrNull { it.routeValue == value } ?: BUDGET
    }
}

@Composable
fun PlanScreen(
    vm: BudgetsVm,
    dataVm: MoreVm,
    initialSection: String,
    onTransactionClick: (Long) -> Unit,
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val recurring by vm.recurring.collectAsStateWithLifecycle()
    val goals by dataVm.goals.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(PlanSection.from(initialSection)) }
    LaunchedEffect(initialSection) { selected = PlanSection.from(initialSection) }

    var budgetToEdit by remember { mutableStateOf<Budget?>(null) }
    var addBudget by remember { mutableStateOf(false) }
    var cardToEdit by remember { mutableStateOf<CardBill?>(null) }
    var addCard by remember { mutableStateOf(false) }
    var statement by remember { mutableStateOf<CardRow?>(null) }
    var addGoal by remember { mutableStateOf(false) }
    var goalForMoney by remember { mutableStateOf<Goal?>(null) }

    PlanContent(
        selected = selected,
        onSelect = { selected = it },
        budgetRows = rows,
        cards = cards,
        recurring = recurring,
        goals = goals,
        onAdd = {
            when (selected) {
                PlanSection.BUDGET -> addBudget = true
                PlanSection.BILLS -> addCard = true
                PlanSection.RECURRING -> Unit
                PlanSection.GOALS -> addGoal = true
            }
        },
        onEditBudget = { budgetToEdit = it },
        onDeleteBudget = vm::removeBudget,
        onEditCard = { cardToEdit = it.card },
        onMarkCardPaid = vm::markPaid,
        onMarkCardUnpaid = vm::markUnpaid,
        onStatement = { statement = it },
        onAddToGoal = { goalForMoney = it },
        onDeleteGoal = dataVm::deleteGoal,
    )

    if (addBudget || budgetToEdit != null) {
        BudgetEditorSheet(
            vm = vm,
            existing = budgetToEdit,
            onDismiss = { addBudget = false; budgetToEdit = null },
        )
    }
    if (addCard || cardToEdit != null) {
        CardEditorSheet(
            vm = vm,
            existing = cardToEdit,
            onDismiss = { addCard = false; cardToEdit = null },
        )
    }
    statement?.let { row ->
        StatementSheet(
            vm = vm,
            row = row,
            onTransactionClick = onTransactionClick,
            onDismiss = { statement = null },
        )
    }
    if (addGoal) {
        GoalEditorSheet(vm = dataVm, onDismiss = { addGoal = false })
    }
    goalForMoney?.let { goal ->
        AddGoalMoneySheet(vm = dataVm, goal = goal, onDismiss = { goalForMoney = null })
    }
}

@Composable
internal fun PlanContent(
    selected: PlanSection,
    onSelect: (PlanSection) -> Unit,
    budgetRows: List<BudgetRow>,
    cards: List<CardRow>,
    recurring: List<RecurringItem>,
    goals: List<Goal>,
    onAdd: () -> Unit,
    onEditBudget: (Budget) -> Unit,
    onDeleteBudget: (Budget) -> Unit,
    onEditCard: (CardRow) -> Unit,
    onMarkCardPaid: (CardRow) -> Unit,
    onMarkCardUnpaid: (CardRow) -> Unit,
    onStatement: (CardRow) -> Unit,
    onAddToGoal: (Goal) -> Unit,
    onDeleteGoal: (Goal) -> Unit,
) {
    val overall = budgetRows.firstOrNull { it.budget.categoryId == null }
    val categoryBudgets = budgetRows.filter { it.budget.categoryId != null }
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
            Text("Plan", style = MaterialTheme.typography.headlineLarge, color = ReconcileColors.Text)
            Text("Budgets, bills and what is next", style = MaterialTheme.typography.bodyMedium, color = ReconcileColors.TextSecondary)
            Spacer(Modifier.height(16.dp))
            PlanSegments(selected, onSelect)
            Spacer(Modifier.height(20.dp))
        }

        when (selected) {
            PlanSection.BUDGET -> {
                item {
                    if (overall == null) {
                        EmptyState("No monthly plan", "Add an overall budget to compare spending with your limit.")
                    } else {
                        MonthlyPlanCard(overall, onEdit = { onEditBudget(overall.budget) })
                    }
                    Spacer(Modifier.height(20.dp))
                    SectionHeader("Category limits")
                }
                if (categoryBudgets.isEmpty()) {
                    item { Text("No category limits yet.", style = MaterialTheme.typography.bodyMedium, color = ReconcileColors.TextSecondary) }
                }
                categoryBudgets.forEach { row ->
                    item(key = "budget-${row.budget.id}") {
                        BudgetLimitRow(row, onEdit = { onEditBudget(row.budget) }, onDelete = { onDeleteBudget(row.budget) })
                    }
                }
            }

            PlanSection.BILLS -> {
                item { SectionHeader("Next bills") }
                if (cards.isEmpty()) {
                    item { EmptyState("No cards added", "Add a card to track its cycle and due-date reminder.") }
                }
                cards.forEach { row ->
                    item(key = "card-${row.card.id}") {
                        CardPlanRow(
                            row = row,
                            onEdit = { onEditCard(row) },
                            onPaid = { if (row.paidThisCycle) onMarkCardUnpaid(row) else onMarkCardPaid(row) },
                            onStatement = { onStatement(row) },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            PlanSection.RECURRING -> {
                item { SectionHeader("Upcoming recurring") }
                if (recurring.isEmpty()) {
                    item { EmptyState("Nothing recurring yet", "Regular payments appear after at least two consistent cycles.") }
                }
                recurring.forEachIndexed { index, item ->
                    item(key = item.merchantNorm) {
                        RecurringRow(item, index)
                    }
                }
            }

            PlanSection.GOALS -> {
                item { SectionHeader("Savings goals") }
                if (goals.isEmpty()) {
                    item { EmptyState("No goals yet", "Create a target and track money saved on this device.") }
                }
                goals.forEach { goal ->
                    item(key = "goal-${goal.id}") {
                        GoalRow(goal, onAddMoney = { onAddToGoal(goal) }, onDelete = { onDeleteGoal(goal) })
                    }
                }
            }
        }

        if (selected != PlanSection.RECURRING) {
            item {
                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    text = when (selected) {
                        PlanSection.BUDGET -> "+ Add a spending limit"
                        PlanSection.BILLS -> "+ Add a credit card"
                        PlanSection.GOALS -> "+ Add a savings goal"
                        PlanSection.RECURRING -> ""
                    },
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PlanSegments(selected: PlanSection, onSelect: (PlanSection) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ReconcileColors.Surface)
            .padding(4.dp),
    ) {
        PlanSection.entries.forEach { section ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (section == selected) ReconcileColors.Mint else Color.Transparent)
                    .clickable { onSelect(section) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    section.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (section == selected) ReconcileColors.Ink else ReconcileColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun MonthlyPlanCard(row: BudgetRow, onEdit: () -> Unit) {
    val over = row.spentPaise > row.effectiveLimitPaise
    val today = LocalDate.now()
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = RoundedCornerShape(ReconcileRadii.Large),
        color = ReconcileColors.Surface,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("MONTHLY PLAN", style = MaterialTheme.typography.labelLarge, color = ReconcileColors.TextSecondary)
            Row(verticalAlignment = Alignment.Bottom) {
                MoneyText(row.spentPaise, style = MaterialTheme.typography.displayMedium)
                Text(
                    " of ${Money.format(row.effectiveLimitPaise)}",
                    modifier = Modifier.padding(bottom = 5.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReconcileColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(12.dp))
            ProgressBar(
                if (row.effectiveLimitPaise > 0) row.spentPaise.toFloat() / row.effectiveLimitPaise else 0f,
                color = if (over) ReconcileColors.Coral else ReconcileColors.Mint,
            )
            Spacer(Modifier.height(10.dp))
            Row {
                Text(
                    if (over) "You are ${Money.format(row.spentPaise - row.effectiveLimitPaise)} over"
                    else "${Money.format(row.effectiveLimitPaise - row.spentPaise)} left",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (over) ReconcileColors.Coral else ReconcileColors.Mint,
                )
                Text(
                    "${today.lengthOfMonth() - today.dayOfMonth} days left",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReconcileColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun BudgetLimitRow(row: BudgetRow, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryIcon(row.category)
        Spacer(Modifier.padding(start = 12.dp))
        Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
            Row {
                Text(row.category?.name ?: "Overall", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                MoneyText(row.spentPaise, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(
                if (row.effectiveLimitPaise > 0) row.spentPaise.toFloat() / row.effectiveLimitPaise else 0f,
                color = if (row.spentPaise > row.effectiveLimitPaise) ReconcileColors.Coral else ReconcileColors.Mint,
            )
            Text(
                "Limit ${Money.format(row.effectiveLimitPaise)}${if (row.budget.rollover) " · rollover" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = ReconcileColors.TextSecondary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete limit", tint = ReconcileColors.TextSecondary)
        }
    }
}

@Composable
private fun CardPlanRow(
    row: CardRow,
    onEdit: () -> Unit,
    onPaid: () -> Unit,
    onStatement: () -> Unit,
) {
    val urgent = !row.paidThisCycle && row.daysLeft in 0..3
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onStatement),
        shape = RoundedCornerShape(ReconcileRadii.Medium),
        color = if (urgent) ReconcileColors.IndigoContainer else ReconcileColors.Surface,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(ReconcileColors.Indigo.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.CreditCard, contentDescription = null, tint = ReconcileColors.Indigo) }
                Spacer(Modifier.padding(start = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.card.name + (row.card.tail?.let { " ·$it" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ReconcileColors.Text,
                    )
                    Text(
                        if (row.paidThisCycle) "Paid for this cycle" else cardDueLabel(row),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (urgent) ReconcileColors.Indigo else ReconcileColors.TextSecondary,
                    )
                }
                MoneyText(row.runningCycleSpendPaise ?: 0L, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(if (row.paidThisCycle) "Mark unpaid" else "Mark paid", onClick = onPaid, modifier = Modifier.weight(1f))
                SecondaryButton("Edit", onClick = onEdit, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RecurringRow(item: RecurringItem, index: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background((if (index % 2 == 0) ReconcileColors.Mint else ReconcileColors.Amber).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = if (index % 2 == 0) ReconcileColors.Mint else ReconcileColors.Amber,
            )
        }
        Spacer(Modifier.padding(start = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.merchant, style = MaterialTheme.typography.bodyLarge, color = ReconcileColors.Text)
            Text(
                "Around ${Dates.day(item.nextDueEstimate)} · ${item.cadence.display.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = ReconcileColors.TextSecondary,
            )
        }
        MoneyText(item.typicalAmountPaise, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun GoalRow(goal: Goal, onAddMoney: () -> Unit, onDelete: () -> Unit) {
    val fraction = if (goal.targetPaise > 0) goal.savedPaise.toFloat() / goal.targetPaise else 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(ReconcileColors.Indigo.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Savings, contentDescription = null, tint = ReconcileColors.Indigo) }
            Spacer(Modifier.padding(start = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(goal.name, style = MaterialTheme.typography.bodyLarge, color = ReconcileColors.Text)
                Text(
                    "${Money.format(goal.savedPaise)} of ${Money.format(goal.targetPaise)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ReconcileColors.TextSecondary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete goal", tint = ReconcileColors.TextSecondary)
            }
        }
        ProgressBar(fraction, color = ReconcileColors.Indigo)
        TextButton(onClick = onAddMoney, modifier = Modifier.align(Alignment.End)) { Text("Add money", color = ReconcileColors.Mint) }
    }
}

private fun cardDueLabel(row: CardRow): String = when {
    row.daysLeft < 0 -> "Overdue · ${row.dueDate.dayOfMonth} ${row.dueDate.month.name.take(3)}"
    row.daysLeft == 0L -> "Due today"
    row.daysLeft == 1L -> "Due tomorrow · ${row.dueDate.dayOfMonth} ${row.dueDate.month.name.take(3)}"
    else -> "Due ${row.dueDate.dayOfMonth} ${row.dueDate.month.name.take(3)}"
}

@Preview(showBackground = true, backgroundColor = 0xFF090C10, widthDp = 360, heightDp = 800)
@Composable
private fun PlanPreview() {
    val budget = Budget(id = 1, categoryId = null, limitPaise = 30_000_00)
    val card = CardBill(id = 1, name = "HDFC", tail = "2854", dueDay = 30, statementDay = 14)
    KharchaTheme(ThemeMode.DARK) {
        PlanContent(
            selected = PlanSection.BUDGET,
            onSelect = {},
            budgetRows = listOf(BudgetRow(budget, null, 37_940_00, 30_000_00)),
            cards = listOf(CardRow(card, LocalDate.now().plusDays(1), 1, false, 18_420_00, 1)),
            recurring = listOf(RecurringItem("Netflix", "netflix", Cadence.MONTHLY, 649_00, 0, System.currentTimeMillis(), 4)),
            goals = listOf(Goal(id = 1, name = "Emergency fund", targetPaise = 100_000_00, savedPaise = 40_000_00)),
            onAdd = {},
            onEditBudget = {},
            onDeleteBudget = {},
            onEditCard = {},
            onMarkCardPaid = {},
            onMarkCardUnpaid = {},
            onStatement = {},
            onAddToGoal = {},
            onDeleteGoal = {},
        )
    }
}
