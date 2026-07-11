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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abc.expensetracker.ThemeMode
import com.abc.expensetracker.data.Account
import com.abc.expensetracker.data.AccountType
import com.abc.expensetracker.data.Goal
import com.abc.expensetracker.sms.ImportState
import com.abc.expensetracker.ui.common.SectionHeader
import com.abc.expensetracker.ui.vm.MoreVm
import com.abc.expensetracker.util.CsvExport
import com.abc.expensetracker.util.Money
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(vm: MoreVm) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val txnCount by vm.txnCount.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val dupPairs by vm.dupPairs.collectAsStateWithLifecycle()
    val openSplits by vm.openSplits.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val catById = categories.associateBy { it.id }

    var showAddGoal by remember { mutableStateOf(false) }
    var goalForMoney by remember { mutableStateOf<Goal?>(null) }
    var accountToRename by remember { mutableStateOf<Account?>(null) }

    LazyColumn(Modifier.fillMaxSize()) {

        // ------------------------------------------------- duplicate review
        if (dupPairs.isNotEmpty()) {
            item { SectionHeader("Possible duplicates (${dupPairs.size})") }
            item {
                Text(
                    "Same amount within 6 hours, but too far apart for auto-merge. Delete one or keep both.",
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(dupPairs, key = { "d${it.a.id}-${it.b.id}" }) { pair ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            Money.format(pair.a.amountPaise),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        listOf(pair.a, pair.b).forEach { t ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        (t.merchant ?: catById[t.categoryId]?.name ?: "Unknown") +
                                            (t.smsSender?.let { "  ·  $it" } ?: ""),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                    )
                                    Text(
                                        com.abc.expensetracker.util.Dates.dateTime(t.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { vm.deleteDup(t) }) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        TextButton(onClick = { vm.dismissDup(pair) }) { Text("Not duplicates — keep both") }
                    }
                }
            }
        }

        // -------------------------------------------------------- splits
        if (openSplits.isNotEmpty()) {
            item { SectionHeader("Owed to you") }
            items(openSplits, key = { "s${it.id}" }) { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${t.splitWith ?: "Someone"} owes ${Money.format(t.splitOwedPaise ?: 0)}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "${t.merchant ?: "spend"} · ${Money.format(t.amountPaise)} total · " +
                                com.abc.expensetracker.util.Dates.day(t.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.settleSplit(t) }) { Text("Settled ✓") }
                }
            }
        }

        // ------------------------------------------------------------ goals
        item {
            SectionHeader("Savings goals") {
                IconButton(onClick = { showAddGoal = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add goal")
                }
            }
        }
        items(goals, key = { "g${it.id}" }) { goal ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(goal.emoji, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(8.dp))
                        Text(goal.name, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.deleteGoal(goal) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete goal",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val fraction = if (goal.targetPaise > 0) {
                        (goal.savedPaise.toFloat() / goal.targetPaise).coerceIn(0f, 1f)
                    } else 0f
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${Money.format(goal.savedPaise)} of ${Money.format(goal.targetPaise)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { goalForMoney = goal }) { Text("Add money") }
                    }
                }
            }
        }

        // --------------------------------------------------------- accounts
        item { SectionHeader("Accounts") }
        item {
            Text(
                "Accounts are created automatically from your bank SMSes. Tap to rename.",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(accounts, key = { "a${it.id}" }) { acc ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when (acc.type) {
                        AccountType.CARD -> "💳"
                        AccountType.BANK -> "🏦"
                        AccountType.WALLET -> "👛"
                        AccountType.CASH -> "💵"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(acc.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    acc.bank?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = { accountToRename = acc }) { Text("Rename") }
            }
        }

        // ----------------------------------------------------------- settings
        item { SectionHeader("Appearance") }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { vm.setTheme(mode) },
                        label = {
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        },
                    )
                }
            }
        }

        item { SectionHeader("Data") }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "$txnCount transactions stored — everything stays on this device. " +
                        "The app has no internet permission at all.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val csv = vm.buildCsv()
                                CsvExport.share(context, csv)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Export CSV") }
                    OutlinedButton(
                        onClick = { vm.rerunImport() },
                        enabled = importState !is ImportState.Running,
                        modifier = Modifier.weight(1f),
                    ) { Text("Rescan inbox") }
                }
                if (importState is ImportState.Running) {
                    val st = importState as ImportState.Running
                    Text(
                        "Scanning… ${st.scanned}/${st.total}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }

    if (showAddGoal) {
        ModalBottomSheet(onDismissRequest = { showAddGoal = false }) {
            var name by remember { mutableStateOf("") }
            var emoji by remember { mutableStateOf("🎯") }
            var target by remember { mutableStateOf("") }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("New goal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = emoji, onValueChange = { emoji = it.take(2) },
                        label = { Text("Icon") }, singleLine = true,
                        modifier = Modifier.width(80.dp),
                    )
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Goal name") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = target, onValueChange = { target = it },
                    label = { Text("Target amount (₹)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        Money.parse(target)?.let {
                            vm.addGoal(name.trim(), emoji.ifBlank { "🎯" }, it)
                            showAddGoal = false
                        }
                    },
                    enabled = name.isNotBlank() && Money.parse(target) != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Create goal") }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    goalForMoney?.let { goal ->
        ModalBottomSheet(onDismissRequest = { goalForMoney = null }) {
            var amount by remember { mutableStateOf("") }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Add to ${goal.emoji} ${goal.name}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("Amount (₹)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        Money.parse(amount)?.let {
                            vm.addToGoal(goal, it)
                            goalForMoney = null
                        }
                    },
                    enabled = Money.parse(amount) != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add") }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    accountToRename?.let { acc ->
        ModalBottomSheet(onDismissRequest = { accountToRename = null }) {
            var name by remember { mutableStateOf(acc.name) }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Rename account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        vm.renameAccount(acc, name.trim())
                        accountToRename = null
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save") }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
