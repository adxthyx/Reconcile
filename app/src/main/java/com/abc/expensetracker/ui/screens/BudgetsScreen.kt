package com.abc.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abc.expensetracker.data.CardBill
import com.abc.expensetracker.data.Budget
import com.abc.expensetracker.data.Txn
import com.abc.expensetracker.ui.common.BudgetBar
import com.abc.expensetracker.ui.common.CategoryDot
import com.abc.expensetracker.ui.common.EmptyState
import com.abc.expensetracker.ui.common.SectionHeader
import com.abc.expensetracker.ui.vm.BudgetsVm
import com.abc.expensetracker.ui.vm.CardRow
import com.abc.expensetracker.util.Dates
import com.abc.expensetracker.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(vm: BudgetsVm) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val recurring by vm.recurring.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    var showAddBudget by remember { mutableStateOf(false) }
    var budgetToEdit by remember { mutableStateOf<Budget?>(null) }
    var cardToEdit by remember { mutableStateOf<CardBill?>(null) }
    var showAddCard by remember { mutableStateOf(false) }
    var cycleFor by remember { mutableStateOf<CardRow?>(null) }
    val overallBudget = rows.firstOrNull { it.budget.categoryId == null }
    val categoryBudgets = rows.filter { it.budget.categoryId != null }

    LazyColumn(Modifier.fillMaxSize()) {
        // Keep the overall limit first and give it an explicit action. It is
        // the number used by the home-screen widget, so it must not be hidden
        // behind an unlabeled add icon or below the card-reminder list.
        item {
            SectionHeader("Monthly budget")
        }
        item {
            OverallBudgetCard(
                row = overallBudget,
                onSetOrUpdate = {
                    if (overallBudget == null) showAddBudget = true
                    else budgetToEdit = overallBudget.budget
                },
            )
        }

        // ---------------------------------------------------------- budgets
        item {
            SectionHeader("Category budgets") {
                TextButton(onClick = { showAddBudget = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }
        if (categoryBudgets.isEmpty()) {
            item {
                EmptyState(
                    "🎯", "No category budgets",
                    "Optional: add a separate monthly cap for food, shopping, travel, or any other category.",
                )
            }
        }
        items(categoryBudgets, key = { "b${it.budget.id}" }) { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryDot(row.category)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.category?.name ?: "Overall spending",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        if (row.budget.rollover) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "envelope",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    BudgetBar(spent = row.spentPaise, limit = row.effectiveLimitPaise)
                    if (row.budget.rollover && row.effectiveLimitPaise != row.budget.limitPaise) {
                        Text(
                            "${Money.format(row.budget.limitPaise)}/mo + " +
                                Money.format(row.effectiveLimitPaise - row.budget.limitPaise) + " rolled over",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { budgetToEdit = row.budget }) {
                    Icon(
                        Icons.Default.Edit, contentDescription = "Edit budget",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { vm.removeBudget(row.budget) }) {
                    Icon(
                        Icons.Default.Delete, contentDescription = "Remove budget",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ------------------------------------------------------------ cards
        item {
            SectionHeader("Credit card bills") {
                TextButton(onClick = { showAddCard = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add card")
                }
            }
        }
        items(cards, key = { "c${it.card.id}" }) { row ->
            CardBillRow(
                row = row,
                onMarkPaid = { vm.markPaid(row) },
                onMarkUnpaid = { vm.markUnpaid(row) },
                onEdit = { cardToEdit = row.card },
                onCycle = { cycleFor = row },
            )
        }

        // -------------------------------------------------------- recurring
        item { SectionHeader("Detected subscriptions & recurring") }
        if (recurring.isEmpty()) {
            item {
                EmptyState(
                    "🔁", "Nothing recurring yet",
                    "Rent, OTT subscriptions and other repeating payments are detected automatically after a couple of cycles.",
                )
            }
        }
        items(recurring, key = { it.merchantNorm }) { r ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.merchant, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(
                        "${r.cadence.display} · ${r.occurrences}× · next ~${Dates.day(r.nextDueEstimate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    Money.format(r.typicalAmountPaise),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }

    if (showAddBudget || budgetToEdit != null) {
        BudgetSheet(
            vm = vm,
            existing = budgetToEdit,
            onDismiss = { showAddBudget = false; budgetToEdit = null },
        )
    }
    if (showAddCard || cardToEdit != null) {
        CardSheet(
            vm = vm,
            existing = cardToEdit,
            onDismiss = { showAddCard = false; cardToEdit = null },
        )
    }
    cycleFor?.let { row ->
        CycleSheet(vm = vm, row = row, onDismiss = { cycleFor = null })
    }
}

@Composable
private fun OverallBudgetCard(
    row: com.abc.expensetracker.ui.vm.BudgetRow?,
    onSetOrUpdate: () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            if (row == null) {
                Text(
                    "Set your overall monthly limit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "This powers budget utilization in the app and home-screen widget.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onSetOrUpdate, modifier = Modifier.fillMaxWidth()) {
                    Text("Set monthly budget")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Overall spending limit",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            Money.format(row.budget.limitPaise),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Button(onClick = onSetOrUpdate) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Update")
                    }
                }
                Spacer(Modifier.height(12.dp))
                BudgetBar(spent = row.spentPaise, limit = row.effectiveLimitPaise)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${Money.format(row.spentPaise)} used this month",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CardBillRow(
    row: CardRow,
    onMarkPaid: () -> Unit,
    onMarkUnpaid: () -> Unit,
    onEdit: () -> Unit,
    onCycle: () -> Unit,
) {
    val urgent = !row.paidThisCycle && row.daysLeft in 0..3
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                urgent -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                row.paidThisCycle -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💳", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.card.name + (row.card.tail?.let { " ••$it" } ?: ""),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (row.paidThisCycle) {
                            "Paid ✓ · next due ${row.dueDate.dayOfMonth}/${row.dueDate.monthValue}"
                        } else {
                            when {
                                row.daysLeft == 0L -> "Due TODAY (${row.dueDate.dayOfMonth}/${row.dueDate.monthValue})"
                                row.daysLeft == 1L -> "Due tomorrow"
                                row.daysLeft < 0L -> "Overdue"
                                else -> "Due in ${row.daysLeft} days (${row.dueDate.dayOfMonth}/${row.dueDate.monthValue})"
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (urgent) FontWeight.Bold else FontWeight.Normal,
                        color = if (urgent) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    row.runningCycleSpendPaise?.let {
                        Text(
                            "This cycle so far: ${Money.format(it)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.paidThisCycle) {
                    AssistChip(onClick = onMarkUnpaid, label = { Text("Undo paid") })
                } else {
                    AssistChip(onClick = onMarkPaid, label = { Text("Mark paid ✓") })
                }
                if (row.accountId != null) {
                    AssistChip(onClick = onCycle, label = { Text("Statement") })
                }
                AssistChip(onClick = onEdit, label = { Text("Edit") })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetSheet(vm: BudgetsVm, existing: Budget?, onDismiss: () -> Unit) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    var amount by remember(existing) {
        mutableStateOf(existing?.limitPaise?.let { Money.format(it, withSymbol = false) } ?: "")
    }
    var selectedCat by remember(existing) { mutableStateOf(existing?.categoryId) }
    var rollover by remember(existing) { mutableStateOf(existing?.rollover ?: false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                if (existing == null) "New budget" else "Update budget",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Monthly limit (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCat == null,
                        onClick = { selectedCat = null },
                        label = { Text("🌐 Overall") },
                        enabled = existing == null,
                    )
                }
                items(categories.filter { !it.isIncome && !it.excludeFromTotals }) { c ->
                    FilterChip(
                        selected = selectedCat == c.id,
                        onClick = { selectedCat = c.id },
                        label = { Text("${c.emoji} ${c.name}") },
                        enabled = existing == null,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Envelope mode", fontWeight = FontWeight.Medium)
                    Text(
                        "Unspent budget rolls over to next month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = rollover, onCheckedChange = { rollover = it })
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        Money.parse(amount)?.let {
                            vm.setBudget(existing, selectedCat, it, rollover)
                            onDismiss()
                        }
                    },
                    enabled = Money.parse(amount) != null,
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardSheet(vm: BudgetsVm, existing: CardBill?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var tail by remember { mutableStateOf(existing?.tail ?: "") }
    var dueDay by remember { mutableStateOf(existing?.dueDay?.toString() ?: "") }
    var stmtDay by remember { mutableStateOf(existing?.statementDay?.toString() ?: "") }
    val dueOk = dueDay.toIntOrNull() in 1..31
    val stmtOk = stmtDay.toIntOrNull() in 1..31
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                if (existing == null) "Add card" else "Edit card",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Card name (e.g. HDFC Bank)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = tail, onValueChange = { tail = it.filter(Char::isDigit).take(4) },
                label = { Text("Last 4 digits (links SMSes; optional)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dueDay, onValueChange = { dueDay = it.filter(Char::isDigit).take(2) },
                    label = { Text("Bill due day") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    isError = dueDay.isNotEmpty() && !dueOk,
                )
                OutlinedTextField(
                    value = stmtDay, onValueChange = { stmtDay = it.filter(Char::isDigit).take(2) },
                    label = { Text("Statement day") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    isError = stmtDay.isNotEmpty() && !stmtOk,
                )
            }
            Text(
                "Reminders fire daily at 10am from 3 days before the due day. Paid status resets automatically on the 15th of every month.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (existing != null) {
                    OutlinedButton(
                        onClick = { vm.deleteCard(existing); onDismiss() },
                        modifier = Modifier.weight(1f),
                    ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                Button(
                    onClick = {
                        vm.saveCard(existing, name.trim(), tail.ifBlank { null }, dueDay.toInt(), stmtDay.toInt())
                        onDismiss()
                    },
                    enabled = name.isNotBlank() && dueOk && stmtOk,
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CycleSheet(vm: BudgetsVm, row: CardRow, onDismiss: () -> Unit) {
    var txns by remember { mutableStateOf<List<Txn>>(emptyList()) }
    LaunchedEffect(row) { txns = vm.cycleTxns(row) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                "${row.card.name} — current cycle",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            )
            Text(
                "Since statement day ${row.card.statementDay} · " +
                    Money.format(txns.filter { !it.excluded }.sumOf { it.amountPaise - (it.splitOwedPaise ?: 0L) }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (txns.isEmpty()) {
                Text(
                    "No transactions in this cycle yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            txns.take(30).forEach { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.merchant ?: "—", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(Dates.dateTime(t.timestamp), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        Money.format(t.amountPaise),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
