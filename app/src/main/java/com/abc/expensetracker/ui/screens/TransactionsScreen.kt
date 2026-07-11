package com.abc.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abc.expensetracker.data.Txn
import com.abc.expensetracker.sms.parser.Direction
import com.abc.expensetracker.ui.common.CategoryDot
import com.abc.expensetracker.ui.common.EmptyState
import com.abc.expensetracker.ui.common.TxnRow
import com.abc.expensetracker.ui.vm.TxnListVm
import com.abc.expensetracker.util.Dates
import com.abc.expensetracker.util.Money
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(vm: TxnListVm) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val txns by vm.txns.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val allTags by vm.allTags.collectAsStateWithLifecycle()
    val catById = categories.associateBy { it.id }
    val accById = accounts.associateBy { it.id }

    var selectedTxn by remember { mutableStateOf<Txn?>(null) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = vm::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search merchant or note") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (filter.query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = filter.month != null,
                    onClick = { vm.setMonth(if (filter.month == null) YearMonth.now() else null) },
                    label = { Text(filter.month?.let { Dates.month(it) } ?: "All time") },
                )
            }
            item {
                FilterChip(
                    selected = filter.direction == Direction.DEBIT,
                    onClick = {
                        vm.setDirection(if (filter.direction == Direction.DEBIT) null else Direction.DEBIT)
                    },
                    label = { Text("Spent") },
                )
            }
            item {
                FilterChip(
                    selected = filter.direction == Direction.CREDIT,
                    onClick = {
                        vm.setDirection(if (filter.direction == Direction.CREDIT) null else Direction.CREDIT)
                    },
                    label = { Text("Received") },
                )
            }
            items(allTags) { tag ->
                FilterChip(
                    selected = filter.tag == tag,
                    onClick = { vm.setTag(if (filter.tag == tag) "" else tag) },
                    label = { Text("#$tag") },
                )
            }
            items(categories) { cat ->
                FilterChip(
                    selected = filter.categoryId == cat.id,
                    onClick = { vm.setCategory(if (filter.categoryId == cat.id) null else cat.id) },
                    label = { Text("${cat.emoji} ${cat.name}") },
                )
            }
            items(accounts) { acc ->
                FilterChip(
                    selected = filter.accountId == acc.id,
                    onClick = { vm.setAccount(if (filter.accountId == acc.id) null else acc.id) },
                    label = { Text(acc.name) },
                )
            }
        }

        if (txns.isEmpty()) {
            EmptyState("🔍", "Nothing here", "Try clearing filters, or add a transaction with the + button.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                val grouped = txns.groupBy { Dates.toLocalDate(it.timestamp) }
                grouped.forEach { (date, dayTxns) ->
                    item(key = "h$date") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Text(
                                Dates.dayYear(dayTxns.first().timestamp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            val dayNet = dayTxns.filter { !it.excluded }.sumOf {
                                if (it.direction == Direction.DEBIT) -it.amountPaise else it.amountPaise
                            }
                            Text(
                                (if (dayNet >= 0) "+" else "−") + Money.format(kotlin.math.abs(dayNet)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(dayTxns, key = { it.id }) { txn ->
                        TxnRow(
                            txn = txn,
                            category = catById[txn.categoryId],
                            accountName = accById[txn.accountId]?.name,
                            onClick = { selectedTxn = txn },
                        )
                    }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    selectedTxn?.let { txn ->
        TxnDetailSheet(
            txn = txn,
            vm = vm,
            onDismiss = { selectedTxn = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TxnDetailSheet(txn: Txn, vm: TxnListVm, onDismiss: () -> Unit) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val cat = categories.find { it.id == txn.categoryId }
    var confirmDelete by remember { mutableStateOf(false) }
    var excluded by remember { mutableStateOf(txn.excluded) }
    var tags by remember { mutableStateOf(txn.tags ?: "") }
    var splitOwed by remember { mutableStateOf(txn.splitOwedPaise?.let { Money.format(it, withSymbol = false) } ?: "") }
    var splitWith by remember { mutableStateOf(txn.splitWith ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryDot(cat, size = 48)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        txn.merchant ?: cat?.name ?: "Unknown",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        Dates.dateTime(txn.timestamp) +
                            (accounts.find { it.id == txn.accountId }?.let { " · ${it.name}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                (if (txn.direction == Direction.CREDIT) "+" else "−") + Money.format(txn.amountPaise),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            txn.balancePaise?.let {
                Text(
                    "Balance after: ${Money.format(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ----------------------------------------------------- category
            Spacer(Modifier.height(16.dp))
            Text("Category", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                "Changing it teaches Reconcile — future SMSes from this " +
                    (if (txn.merchantNorm != null) "merchant" else "sender") + " use your choice automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { c ->
                    FilterChip(
                        selected = c.id == txn.categoryId,
                        onClick = {
                            vm.recategorize(txn, c.id)
                            onDismiss()
                        },
                        label = { Text("${c.emoji} ${c.name}") },
                    )
                }
            }

            // ----------------------------------------------------- exclude
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Exclude from totals", fontWeight = FontWeight.Medium)
                    Text(
                        "For self transfers & CC bill payments — kept in history, ignored everywhere else.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = excluded,
                    onCheckedChange = {
                        excluded = it
                        vm.setExcluded(txn, it, always = false)
                    },
                )
            }
            if (excluded != txn.excluded || excluded) {
                TextButton(onClick = {
                    vm.setExcluded(txn, excluded, always = true)
                    onDismiss()
                }) {
                    Text(
                        "Always ${if (excluded) "exclude" else "include"} " +
                            (txn.merchant ?: txn.smsSender ?: "this source")
                    )
                }
            }

            // -------------------------------------------------------- tags
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags (comma separated, e.g. trip:goa, reimbursable)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ------------------------------------------------------- split
            if (txn.direction == Direction.DEBIT) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = splitOwed,
                        onValueChange = { splitOwed = it },
                        label = { Text("Owed to you (₹)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = splitWith,
                        onValueChange = { splitWith = it },
                        label = { Text("By whom") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "Only your share counts in totals. Settle it later from More → Splits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    vm.setTags(txn, tags.trim())
                    if (txn.direction == Direction.DEBIT) {
                        vm.setSplit(txn, Money.parse(splitOwed), splitWith.trim())
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save changes") }

            txn.smsBody?.let { body ->
                Spacer(Modifier.height(16.dp))
                Text("Source SMS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete transaction?") },
            text = { Text("This removes it from all totals. The original SMS is untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(txn)
                    confirmDelete = false
                    onDismiss()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
