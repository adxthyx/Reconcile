package com.abc.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abc.expensetracker.data.Budget
import com.abc.expensetracker.data.CardBill
import com.abc.expensetracker.data.Goal
import com.abc.expensetracker.data.Txn
import com.abc.expensetracker.ui.common.CategoryIcon
import com.abc.expensetracker.ui.common.DestructiveButton
import com.abc.expensetracker.ui.common.MoneyText
import com.abc.expensetracker.ui.common.PrimaryButton
import com.abc.expensetracker.ui.common.SecondaryButton
import com.abc.expensetracker.ui.theme.ReconcileColors
import com.abc.expensetracker.ui.theme.ReconcileRadii
import com.abc.expensetracker.ui.theme.ReconcileSpacing
import com.abc.expensetracker.ui.vm.BudgetsVm
import com.abc.expensetracker.ui.vm.CardRow
import com.abc.expensetracker.ui.vm.MoreVm
import com.abc.expensetracker.util.Dates
import com.abc.expensetracker.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditorSheet(vm: BudgetsVm, existing: Budget?, onDismiss: () -> Unit) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    var amount by remember(existing) {
        mutableStateOf(existing?.limitPaise?.let { Money.format(it, withSymbol = false) }.orEmpty())
    }
    var selectedCategory by remember(existing) { mutableStateOf(existing?.categoryId) }
    var rollover by remember(existing) { mutableStateOf(existing?.rollover ?: false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ReconcileColors.Surface) {
        Column(Modifier.padding(horizontal = ReconcileSpacing.Screen)) {
            Text(if (existing == null) "Add spending limit" else "Edit spending limit", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Monthly limit") },
                prefix = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text("Applies to", style = MaterialTheme.typography.labelLarge, color = ReconcileColors.TextSecondary)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    CategoryChoice(
                        label = "Overall",
                        selected = selectedCategory == null,
                        enabled = existing == null,
                        onClick = { selectedCategory = null },
                    )
                }
                items(categories.filter { !it.isIncome && !it.excludeFromTotals }) { category ->
                    CategoryChoice(
                        label = category.name,
                        selected = selectedCategory == category.id,
                        enabled = existing == null,
                        leading = { CategoryIcon(category, size = 36) },
                        onClick = { selectedCategory = category.id },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rollover unused budget", style = MaterialTheme.typography.bodyLarge)
                    Text("Carries the remaining envelope into next month", style = MaterialTheme.typography.bodySmall, color = ReconcileColors.TextSecondary)
                }
                Switch(checked = rollover, onCheckedChange = { rollover = it })
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                PrimaryButton(
                    "Save limit",
                    onClick = {
                        Money.parse(amount)?.let { parsed ->
                            vm.setBudget(existing, selectedCategory, parsed, rollover)
                            onDismiss()
                        }
                    },
                    enabled = Money.parse(amount) != null,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun CategoryChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) ReconcileColors.Mint else ReconcileColors.Elevated)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        if (leading != null) Spacer(Modifier.padding(start = 6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) ReconcileColors.Ink else ReconcileColors.TextSecondary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditorSheet(vm: BudgetsVm, existing: CardBill?, onDismiss: () -> Unit) {
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var tail by remember(existing) { mutableStateOf(existing?.tail.orEmpty()) }
    var dueDay by remember(existing) { mutableStateOf(existing?.dueDay?.toString().orEmpty()) }
    var statementDay by remember(existing) { mutableStateOf(existing?.statementDay?.toString().orEmpty()) }
    var enabled by remember(existing) { mutableStateOf(existing?.enabled ?: true) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dueValid = dueDay.toIntOrNull() in 1..31
    val statementValid = statementDay.toIntOrNull() in 1..31
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ReconcileColors.Surface) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ReconcileSpacing.Screen),
        ) {
            Text(if (existing == null) "Add credit card" else "Edit credit card", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Card name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = tail,
                onValueChange = { tail = it.filter(Char::isDigit).take(4) },
                label = { Text("Last 4 digits, optional") },
                supportingText = { Text("Links this card to account SMS transactions") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dueDay,
                    onValueChange = { dueDay = it.filter(Char::isDigit).take(2) },
                    label = { Text("Due day") },
                    isError = dueDay.isNotBlank() && !dueValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = statementDay,
                    onValueChange = { statementDay = it.filter(Char::isDigit).take(2) },
                    label = { Text("Statement day") },
                    isError = statementDay.isNotBlank() && !statementValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bill reminders", style = MaterialTheme.typography.bodyLarge)
                    Text("Daily from three days before the due date", style = MaterialTheme.typography.bodySmall, color = ReconcileColors.TextSecondary)
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (existing != null) {
                    DestructiveButton("Delete", onClick = { confirmDelete = true })
                }
                PrimaryButton(
                    "Save card",
                    onClick = {
                        vm.saveCard(
                            existing = existing,
                            name = name.trim(),
                            tail = tail.ifBlank { null },
                            dueDay = dueDay.toInt(),
                            statementDay = statementDay.toInt(),
                            enabled = enabled,
                        )
                        onDismiss()
                    },
                    enabled = name.isNotBlank() && dueValid && statementValid,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = ReconcileColors.Surface,
            title = { Text("Delete this card?") },
            text = { Text("Transactions remain intact. Only bill-cycle tracking and reminders are removed.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCard(existing)
                    confirmDelete = false
                    onDismiss()
                }) { Text("Delete", color = ReconcileColors.Coral) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementSheet(
    vm: BudgetsVm,
    row: CardRow,
    onTransactionClick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var transactions by remember { mutableStateOf<List<Txn>>(emptyList()) }
    LaunchedEffect(row) { transactions = vm.cycleTxns(row) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ReconcileColors.Surface) {
        Column(Modifier.padding(horizontal = ReconcileSpacing.Screen)) {
            Text("${row.card.name} · current cycle", style = MaterialTheme.typography.titleLarge)
            Text(
                "Since statement day ${row.card.statementDay} · ${Money.format(transactions.filter { !it.excluded }.sumOf { it.amountPaise - (it.splitOwedPaise ?: 0L) })}",
                style = MaterialTheme.typography.bodySmall,
                color = ReconcileColors.TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            if (transactions.isEmpty()) {
                Text("No transactions in this cycle.", color = ReconcileColors.TextSecondary)
            }
            transactions.take(30).forEach { txn ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onDismiss(); onTransactionClick(txn.id) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(txn.merchant ?: "Unknown", style = MaterialTheme.typography.bodyLarge)
                        Text(Dates.dateTime(txn.timestamp), style = MaterialTheme.typography.bodySmall, color = ReconcileColors.TextSecondary)
                    }
                    MoneyText(txn.amountPaise, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorSheet(vm: MoreVm, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ReconcileColors.Surface) {
        Column(Modifier.padding(horizontal = ReconcileSpacing.Screen)) {
            Text("Add savings goal", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Goal name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("Target amount") },
                prefix = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                "Create goal",
                onClick = {
                    Money.parse(target)?.let { amount ->
                        vm.addGoal(name.trim(), "🎯", amount)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank() && Money.parse(target) != null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGoalMoneySheet(vm: MoreVm, goal: Goal, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ReconcileColors.Surface) {
        Column(Modifier.padding(horizontal = ReconcileSpacing.Screen)) {
            Text("Add to ${goal.name}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                prefix = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                "Add money",
                onClick = {
                    Money.parse(amount)?.let { parsed -> vm.addToGoal(goal, parsed); onDismiss() }
                },
                enabled = Money.parse(amount) != null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}
