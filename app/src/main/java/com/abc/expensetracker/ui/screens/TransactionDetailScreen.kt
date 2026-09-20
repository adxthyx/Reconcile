package com.abc.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abc.expensetracker.ThemeMode
import com.abc.expensetracker.data.Account
import com.abc.expensetracker.data.Category
import com.abc.expensetracker.data.Txn
import com.abc.expensetracker.sms.parser.Direction
import com.abc.expensetracker.sms.parser.Instrument
import com.abc.expensetracker.ui.common.CategoryIcon
import com.abc.expensetracker.ui.common.DestructiveButton
import com.abc.expensetracker.ui.common.FilterControl
import com.abc.expensetracker.ui.common.MoneyText
import com.abc.expensetracker.ui.common.MoneyTone
import com.abc.expensetracker.ui.common.PrimaryButton
import com.abc.expensetracker.ui.common.SecondaryButton
import com.abc.expensetracker.ui.theme.KharchaTheme
import com.abc.expensetracker.ui.theme.ReconcileColors
import com.abc.expensetracker.ui.theme.ReconcileRadii
import com.abc.expensetracker.ui.theme.ReconcileSpacing
import com.abc.expensetracker.ui.vm.TxnListVm
import com.abc.expensetracker.util.Dates
import com.abc.expensetracker.util.Money

@Composable
fun TransactionDetailScreen(
    transactionId: Long,
    vm: TxnListVm,
    onClose: () -> Unit,
) {
    val transactionFlow = remember(transactionId) { vm.observeTransaction(transactionId) }
    val txn by transactionFlow.collectAsStateWithLifecycle(initialValue = null)
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val current = txn
    if (current == null) {
        Box(Modifier.fillMaxSize().background(ReconcileColors.Ink), contentAlignment = Alignment.Center) {
            Text("Transaction not found", color = ReconcileColors.TextSecondary)
        }
        return
    }

    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showSms by remember { mutableStateOf(false) }

    TransactionDetailContent(
        txn = current,
        category = categories.find { it.id == current.categoryId },
        account = accounts.find { it.id == current.accountId },
        onClose = onClose,
        onEdit = { editing = true },
        onDelete = { confirmDelete = true },
        onViewSms = { showSms = true },
    )

    if (editing) {
        TransactionEditSheet(
            txn = current,
            vm = vm,
            onDismiss = { editing = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = ReconcileColors.Surface,
            title = { Text("Delete transaction?") },
            text = { Text("This removes it from all totals. The original SMS remains on your phone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(current)
                    confirmDelete = false
                    onClose()
                }) { Text("Delete", color = ReconcileColors.Coral) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (showSms) {
        AlertDialog(
            onDismissRequest = { showSms = false },
            containerColor = ReconcileColors.Surface,
            title = { Text("Original SMS") },
            text = {
                Text(
                    current.smsBody ?: "This transaction was entered manually and has no source SMS.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReconcileColors.TextSecondary,
                )
            },
            confirmButton = { TextButton(onClick = { showSms = false }) { Text("Done") } },
        )
    }
}

@Composable
internal fun TransactionDetailContent(
    txn: Txn,
    category: Category?,
    account: Account?,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewSms: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReconcileColors.Ink)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ReconcileSpacing.Screen),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Close transaction")
            }
            Text(
                "Transaction",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = ReconcileColors.Text,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.MoreHoriz, contentDescription = "Transaction actions")
            }
        }

        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CategoryIcon(category, size = 64)
            Spacer(Modifier.height(8.dp))
            MoneyText(
                amountPaise = txn.amountPaise,
                prefix = if (txn.direction == Direction.CREDIT) "+" else "−",
                tone = if (txn.direction == Direction.CREDIT) MoneyTone.Income else MoneyTone.Neutral,
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                txn.merchant ?: category?.name ?: "Unknown",
                style = MaterialTheme.typography.titleLarge,
                color = ReconcileColors.Text,
            )
            Text(
                Dates.dateTime(txn.timestamp),
                style = MaterialTheme.typography.bodyMedium,
                color = ReconcileColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(28.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ReconcileRadii.Large))
                .background(ReconcileColors.Surface)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            DetailLine("Category", category?.name ?: "Other")
            DetailLine("Account", account?.displayLabel() ?: "Not linked")
            DetailLine("Payment", instrumentLabel(txn.instrument))
            txn.refId?.let { DetailLine("Reference", it) }
            txn.balancePaise?.let { DetailLine("Balance after", Money.format(it), showDivider = false) }
                ?: DetailLine("Balance after", "Not available", showDivider = false)
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ReconcileRadii.Medium))
                .background(ReconcileColors.Surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (txn.direction == Direction.CREDIT) "Included in received" else "Included in spending",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ReconcileColors.Text,
                )
                Text(
                    if (txn.excluded) exclusionReason(category) else "Counts toward ${Dates.monthFull(java.time.YearMonth.from(Dates.toLocalDate(txn.timestamp)))} totals",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (txn.excluded) ReconcileColors.Amber else ReconcileColors.TextSecondary,
                )
            }
            Switch(
                checked = !txn.excluded,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ReconcileColors.Ink,
                    checkedTrackColor = ReconcileColors.Mint,
                    uncheckedThumbColor = ReconcileColors.TextSecondary,
                    uncheckedTrackColor = ReconcileColors.Border,
                ),
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoTile(
                label = "Tags",
                value = txn.tags?.takeIf { it.isNotBlank() } ?: "No tags",
                modifier = Modifier.weight(1f),
            )
            InfoTile(
                label = "Split",
                value = txn.splitOwedPaise?.let {
                    "${Money.format(it)} owed${txn.splitWith?.let { person -> " · $person" } ?: ""}${if (txn.splitSettled) " · settled" else ""}"
                } ?: "Not split",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ReconcileRadii.Medium))
                .background(ReconcileColors.Surface)
                .clickable(onClick = onViewSms)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("View original SMS", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("›", style = MaterialTheme.typography.titleLarge, color = ReconcileColors.TextSecondary)
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("Edit transaction", onClick = onEdit, modifier = Modifier.weight(1f))
            DestructiveButton("Delete", onClick = onDelete)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DetailLine(label: String, value: String, showDivider: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = ReconcileColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = ReconcileColors.Text)
    }
    if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(ReconcileColors.Border.copy(alpha = 0.7f)))
}

@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(ReconcileRadii.Medium))
            .background(ReconcileColors.Surface)
            .padding(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = ReconcileColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = ReconcileColors.Text, maxLines = 2)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionEditSheet(txn: Txn, vm: TxnListVm, onDismiss: () -> Unit) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    var categoryId by remember(txn) { mutableStateOf(txn.categoryId) }
    var included by remember(txn) { mutableStateOf(!txn.excluded) }
    var tags by remember(txn) { mutableStateOf(txn.tags.orEmpty()) }
    var splitOwed by remember(txn) {
        mutableStateOf(txn.splitOwedPaise?.let { Money.format(it, withSymbol = false) }.orEmpty())
    }
    var splitWith by remember(txn) { mutableStateOf(txn.splitWith.orEmpty()) }
    var confirmRule by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ReconcileColors.Surface) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ReconcileSpacing.Screen),
        ) {
            Text("Edit transaction", style = MaterialTheme.typography.titleLarge)
            Text(
                "Changes apply to this transaction unless you explicitly update the merchant rule.",
                style = MaterialTheme.typography.bodySmall,
                color = ReconcileColors.TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            Text("Category", style = MaterialTheme.typography.labelLarge, color = ReconcileColors.TextSecondary)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { category ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { categoryId = category.id }
                                .background(if (categoryId == category.id) ReconcileColors.Elevated else ReconcileColors.Surface)
                                .padding(4.dp),
                        ) { CategoryIcon(category, size = 44) }
                        Text(category.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Included in totals", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (included) "Counts in spending or received" else "Visible, but does not count",
                        style = MaterialTheme.typography.bodySmall,
                        color = ReconcileColors.TextSecondary,
                    )
                }
                Switch(checked = included, onCheckedChange = { included = it })
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags, comma separated") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (txn.direction == Direction.DEBIT) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = splitOwed,
                        onValueChange = { splitOwed = it },
                        label = { Text("Owed to you") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = splitWith,
                        onValueChange = { splitWith = it },
                        label = { Text("By whom") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = "Save this transaction",
                onClick = {
                    vm.updateTransactionDetails(
                        txn = txn,
                        categoryId = categoryId,
                        excluded = !included,
                        tags = tags.trim(),
                        splitOwedPaise = if (txn.direction == Direction.DEBIT) Money.parse(splitOwed) else txn.splitOwedPaise,
                        splitWith = if (txn.direction == Direction.DEBIT) splitWith.trim() else txn.splitWith.orEmpty(),
                        learnRule = false,
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (txn.merchantNorm != null || txn.smsSender != null) {
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Apply to history & future",
                    onClick = { confirmRule = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (confirmRule) {
        AlertDialog(
            onDismissRequest = { confirmRule = false },
            containerColor = ReconcileColors.Surface,
            title = { Text("Update matching transactions?") },
            text = {
                Text(
                    "This changes the category and included/excluded state for matching history and teaches Reconcile to use the rule for future SMS transactions.",
                    color = ReconcileColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateTransactionDetails(
                        txn = txn,
                        categoryId = categoryId,
                        excluded = !included,
                        tags = tags.trim(),
                        splitOwedPaise = if (txn.direction == Direction.DEBIT) Money.parse(splitOwed) else txn.splitOwedPaise,
                        splitWith = if (txn.direction == Direction.DEBIT) splitWith.trim() else txn.splitWith.orEmpty(),
                        learnRule = true,
                    )
                    confirmRule = false
                    onDismiss()
                }) { Text("Apply rule", color = ReconcileColors.Mint) }
            },
            dismissButton = { TextButton(onClick = { confirmRule = false }) { Text("Cancel") } },
        )
    }
}

private fun instrumentLabel(instrument: Instrument): String = when (instrument) {
    Instrument.UPI -> "UPI"
    Instrument.CARD -> "Card"
    Instrument.ACCOUNT -> "Bank account"
    Instrument.WALLET -> "Wallet"
    Instrument.UNKNOWN -> "Unknown"
}

private fun exclusionReason(category: Category?): String = when (category?.key) {
    "ccpayment" -> "Card payment · not counted as spending"
    "transfer", "selftransfer" -> "Transfer · not counted as spending"
    "friends" -> "Shared settlement · not counted as spending"
    else -> "Excluded from financial totals"
}

private fun Account.displayLabel(): String =
    tail?.takeUnless { name.contains(it) }?.let { "$name ·$it" } ?: name

@Preview(showBackground = true, backgroundColor = 0xFF090C10, widthDp = 360, heightDp = 800)
@Composable
private fun TransactionDetailPreview() {
    val category = Category(1, "food", "Food & Dining", "", 0xFFFF8A65, sortOrder = 0)
    val txn = Txn(
        id = 1,
        amountPaise = 508_00,
        direction = Direction.DEBIT,
        merchant = "Swiggy",
        merchantNorm = "swiggy",
        categoryId = 1,
        accountId = 1,
        timestamp = System.currentTimeMillis(),
        instrument = Instrument.UPI,
        refId = "UPI/248102193",
        balancePaise = 41_208_00,
        tags = "dinner, work",
    )
    KharchaTheme(ThemeMode.DARK) {
        TransactionDetailContent(
            txn = txn,
            category = category,
            account = Account(id = 1, name = "Kotak", tail = "5410"),
            onClose = {},
            onEdit = {},
            onDelete = {},
            onViewSms = {},
        )
    }
}
