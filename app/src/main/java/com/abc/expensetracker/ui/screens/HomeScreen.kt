package com.abc.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abc.expensetracker.data.Txn
import com.abc.expensetracker.sms.ImportState
import com.abc.expensetracker.ui.charts.DonutChart
import com.abc.expensetracker.ui.charts.Slice
import com.abc.expensetracker.ui.common.BudgetBar
import com.abc.expensetracker.ui.common.EmptyState
import com.abc.expensetracker.ui.common.SectionHeader
import com.abc.expensetracker.ui.common.TxnRow
import com.abc.expensetracker.ui.vm.HomeVm
import com.abc.expensetracker.util.Money

@Composable
fun HomeScreen(
    vm: HomeVm,
    onTxnClick: (Txn) -> Unit,
    onSeeAll: () -> Unit,
) {
    val expense by vm.expense.collectAsStateWithLifecycle()
    val income by vm.income.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val spendByCat by vm.spendByCategory.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val txnCount by vm.txnCount.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val forecast by vm.forecast.collectAsStateWithLifecycle()
    val anomalies by vm.anomalies.collectAsStateWithLifecycle()

    val catById = categories.associateBy { it.id }

    LazyColumn(Modifier.fillMaxSize()) {

        // ------------------------------------------------------------- hero
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0E2A55), Color(0xFF1A63C8), Color(0xFF2E86EB))
                        )
                    ),
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text(
                        vm.monthName,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFB9D3F5),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Money.format(income - expense),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        "net this month",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF9FC0EA),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Spent", style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF9FC0EA))
                            Text(
                                Money.format(expense),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB3C6),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Received", style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF9FC0EA))
                            Text(
                                Money.format(income),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF9BEBC9),
                            )
                        }
                    }
                    forecast?.let { f ->
                        if (f.daysLeft > 0 && f.avgDailyPaise > 0) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "📈 On track to spend ${Money.compact(f.projectedSpendPaise)} by month end" +
                                    " (~${Money.compact(f.avgDailyPaise)}/day)" +
                                    if (f.upcomingRecurringPaise > 0) {
                                        " · ${Money.compact(f.upcomingRecurringPaise)} in bills still due"
                                    } else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD7E6FA),
                            )
                        }
                    }
                }
            }
        }

        (importState as? ImportState.Running)?.let { st ->
            item {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Importing SMS history…", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (st.total > 0) st.scanned.toFloat() / st.total else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${st.scanned}/${st.total} scanned · ${st.imported} transactions found",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        (importState as? ImportState.Done)?.let { st ->
            item {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Text(
                        "✅ Inbox scan: ${st.imported} imported, ${st.duplicates} duplicates skipped (${st.scanned} SMS scanned)",
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // -------------------------------------------------------- anomalies
        if (anomalies.isNotEmpty()) {
            item { SectionHeader("Unusual spending") }
            items(anomalies, key = { "an${it.txn.id}" }) { a ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                a.txn.merchant ?: "Unknown",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${Money.format(a.txn.amountPaise)} — usually ~${Money.format(a.typicalPaise)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (spendByCat.isNotEmpty()) {
            item { SectionHeader("Where it went") }
            item {
                val top = spendByCat.take(6)
                val rest = spendByCat.drop(6).sumOf { it.totalPaise }
                val slices = top.mapNotNull { s ->
                    catById[s.categoryId]?.takeIf { !it.isIncome }?.let {
                        Slice(it.name, it.emoji, s.totalPaise, Color(it.color))
                    }
                } + if (rest > 0) {
                    val other = categories.find { it.key == "other" }
                    listOf(Slice("More", "📦", rest, other?.let { Color(it.color) } ?: Color.Gray))
                } else emptyList()
                DonutChart(
                    slices = slices,
                    total = expense,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        val budgetRows = budgets.mapNotNull { b ->
            val cat = b.categoryId?.let { catById[it] }
            val spent = if (b.categoryId == null) expense
            else spendByCat.find { it.categoryId == b.categoryId }?.totalPaise ?: 0L
            Triple(b, cat, spent)
        }
        if (budgetRows.isNotEmpty()) {
            item { SectionHeader("Budgets") }
            items(budgetRows, key = { "b${it.first.id}" }) { (b, cat, spent) ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        (cat?.let { "${it.emoji} ${it.name}" } ?: "🌐 Overall"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    BudgetBar(spent = spent, limit = b.limitPaise)
                }
            }
        }

        item {
            SectionHeader("Recent activity") {
                TextButton(onClick = onSeeAll) { Text("See all") }
            }
        }
        if (recent.isEmpty()) {
            item {
                EmptyState(
                    emoji = if (txnCount == 0) "📭" else "🔍",
                    title = "No transactions yet",
                    subtitle = "Grant SMS permission and your bank messages will appear here automatically.",
                )
            }
        } else {
            items(recent, key = { it.id }) { txn ->
                TxnRow(
                    txn = txn,
                    category = catById[txn.categoryId],
                    accountName = null,
                    onClick = { onTxnClick(txn) },
                )
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}
