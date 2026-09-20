package com.abc.expensetracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abc.expensetracker.data.Account
import com.abc.expensetracker.data.Category
import com.abc.expensetracker.data.MerchantMapping
import com.abc.expensetracker.data.Txn
import com.abc.expensetracker.ui.common.CategoryIcon
import com.abc.expensetracker.ui.common.DestructiveButton
import com.abc.expensetracker.ui.common.MoneyText
import com.abc.expensetracker.ui.common.PrimaryButton
import com.abc.expensetracker.ui.common.SecondaryButton
import com.abc.expensetracker.ui.theme.ReconcileColors
import com.abc.expensetracker.ui.theme.ReconcileSpacing
import com.abc.expensetracker.ui.vm.DupPair
import com.abc.expensetracker.util.Dates
import com.abc.expensetracker.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsSheet(
    accounts: List<Account>,
    onRename: (Account, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var accountToRename by remember { mutableStateOf<Account?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ReconcileColors.Surface) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ReconcileSpacing.Screen),
        ) {
            Text("Accounts", style = MaterialTheme.typography.titleLarge)
            Text("Created automatically from account tails found in bank SMS messages.", style = MaterialTheme.typography.bodySmall, color = ReconcileColors.TextSecondary)
            Spacer(Modifier.height(12.dp))
            accounts.forEach { account ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable { accountToRename = account },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.AccountBalance, contentDescription = null, tint = ReconcileColors.Indigo)
                    Spacer(Modifier.padding(start = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(account.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(account.bank, account.tail?.let { "••$it" }, account.type.name.lowercase()).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = ReconcileColors.TextSecondary,
                        )
                    }
                    Text("Rename", style = MaterialTheme.typography.labelMedium, color = ReconcileColors.Mint)
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
    accountToRename?.let { account ->
        var name by remember(account) { mutableStateOf(account.name) }
        AlertDialog(
            onDismissRequest = { accountToRename = null },
            containerColor = ReconcileColors.Surface,
            title = { Text("Rename account") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Account name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) onRename(account, name.trim())
                    accountToRename = null
                }) { Text("Save", color = ReconcileColors.Mint) }
            },
            dismissButton = { TextButton(onClick = { accountToRename = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesSheet(categories: List<Category>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ReconcileColors.Surface) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ReconcileSpacing.Screen),
        ) {
            Text("Categories", style = MaterialTheme.typography.titleLarge)
            Text("Stable category keys used by SMS categorization and existing transaction history.", style = MaterialTheme.typography.bodySmall, color = ReconcileColors.TextSecondary)
            Spacer(Modifier.height(12.dp))
            categories.forEach { category ->
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryIcon(category, size = 40)
                    Spacer(Modifier.padding(start = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(category.name, style = MaterialTheme.typography.bodyLarge)
                        Text(category.key, style = MaterialTheme.typography.bodySmall, color = ReconcileColors.TextSecondary)
                    }
                    if (category.excludeFromTotals) {
                        Text("Excluded by default", style = MaterialTheme.typography.labelMedium, color = ReconcileColors.Amber)
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantRulesSheet(
    mappings: List<MerchantMapping>,
    categories: List<Category>,
    onDismiss: () -> Unit,
) {
    val categoryById = categories.associateBy { it.id }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ReconcileColors.Surface) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ReconcileSpacing.Screen),
        ) {
            Text("Merchant rules", style = MaterialTheme.typography.titleLarge)
            Text("Rules learned only after you explicitly apply a change to history and future transactions.", style = MaterialTheme.typography.bodySmall, color = ReconcileColors.TextSecondary)
            Spacer(Modifier.height(12.dp))
            if (mappings.isEmpty()) Text("No learned rules yet.", color = ReconcileColors.TextSecondary)
            mappings.forEach { mapping ->
                val category = categoryById[mapping.categoryId]
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryIcon(category, size = 40)
                    Spacer(Modifier.padding(start = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ruleLabel(mapping.merchantNorm), style = MaterialTheme.typography.bodyLarge)
                        Text(category?.name ?: "Unknown category", style = MaterialTheme.typography.bodySmall, color = ReconcileColors.TextSecondary)
                    }
                    if (mapping.excluded) Text("Excluded", style = MaterialTheme.typography.labelMedium, color = ReconcileColors.Amber)
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateReviewSheet(
    pairs: List<DupPair>,
    categories: List<Category>,
    onDelete: (Txn) -> Unit,
    onKeepBoth: (DupPair) -> Unit,
    onTransaction: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val categoryById = categories.associateBy { it.id }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ReconcileColors.Surface) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ReconcileSpacing.Screen),
        ) {
            Text("Possible duplicates", style = MaterialTheme.typography.titleLarge)
            Text("Same amount and direction within six hours, outside the automatic three-minute merge window.", style = MaterialTheme.typography.bodySmall, color = ReconcileColors.TextSecondary)
            Spacer(Modifier.height(12.dp))
            if (pairs.isEmpty()) Text("No duplicate suggestions.", color = ReconcileColors.TextSecondary)
            pairs.forEach { pair ->
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    MoneyText(pair.a.amountPaise, style = MaterialTheme.typography.titleMedium)
                    listOf(pair.a, pair.b).forEach { txn ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onDismiss(); onTransaction(txn.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIcon(categoryById[txn.categoryId], size = 40)
                            Spacer(Modifier.padding(start = 10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(txn.merchant ?: "Unknown", style = MaterialTheme.typography.bodyLarge)
                                Text(Dates.dateTime(txn.timestamp), style = MaterialTheme.typography.bodySmall, color = ReconcileColors.TextSecondary)
                            }
                            TextButton(onClick = { onDelete(txn) }) { Text("Delete", color = ReconcileColors.Coral) }
                        }
                    }
                    SecondaryButton("Keep both", onClick = { onKeepBoth(pair) }, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSplitsSheet(
    transactions: List<Txn>,
    onSettle: (Txn) -> Unit,
    onTransaction: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ReconcileColors.Surface) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ReconcileSpacing.Screen),
        ) {
            Text("Open splits", style = MaterialTheme.typography.titleLarge)
            Text("Only your share of these expenses contributes to spending totals.", style = MaterialTheme.typography.bodySmall, color = ReconcileColors.TextSecondary)
            Spacer(Modifier.height(12.dp))
            transactions.forEach { txn ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onDismiss(); onTransaction(txn.id) }
                        .padding(vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${txn.splitWith ?: "Someone"} owes ${Money.format(txn.splitOwedPaise ?: 0L)}", style = MaterialTheme.typography.bodyLarge)
                            Text("${txn.merchant ?: "Expense"} · ${Dates.day(txn.timestamp)}", style = MaterialTheme.typography.bodySmall, color = ReconcileColors.TextSecondary)
                        }
                        TextButton(onClick = { onSettle(txn) }) { Text("Settled", color = ReconcileColors.Mint) }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

private fun ruleLabel(key: String): String = if (key.startsWith("sender:")) {
    "SMS sender · ${key.substringAfter("sender:").substringBeforeLast(":")}" 
} else key.replaceFirstChar { it.uppercase() }
