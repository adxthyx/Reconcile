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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abc.expensetracker.ThemeMode
import com.abc.expensetracker.data.Account
import com.abc.expensetracker.data.Category
import com.abc.expensetracker.data.MerchantMapping
import com.abc.expensetracker.sms.ImportState
import com.abc.expensetracker.ui.common.AttentionTone
import com.abc.expensetracker.ui.common.StatusAttentionRow
import com.abc.expensetracker.ui.theme.KharchaTheme
import com.abc.expensetracker.ui.theme.ReconcileColors
import com.abc.expensetracker.ui.theme.ReconcileRadii
import com.abc.expensetracker.ui.theme.ReconcileSpacing
import com.abc.expensetracker.ui.vm.BudgetsVm
import com.abc.expensetracker.ui.vm.CardRow
import com.abc.expensetracker.ui.vm.DupPair
import com.abc.expensetracker.ui.vm.MoreVm
import com.abc.expensetracker.util.CsvExport
import com.abc.expensetracker.util.Money
import kotlinx.coroutines.launch

private enum class SettingsSheet { ACCOUNTS, CATEGORIES, RULES, DUPLICATES, SPLITS }

@Composable
fun SettingsScreen(
    vm: MoreVm,
    planVm: BudgetsVm,
    onBack: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenUncategorized: (Long) -> Unit,
    onTransactionClick: (Long) -> Unit,
) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val mappings by vm.merchantMappings.collectAsStateWithLifecycle()
    val duplicates by vm.dupPairs.collectAsStateWithLifecycle()
    val uncategorized by vm.uncategorized.collectAsStateWithLifecycle()
    val splits by vm.openSplits.collectAsStateWithLifecycle()
    val txnCount by vm.txnCount.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val cards by planVm.cards.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }

    SettingsContent(
        accounts = accounts,
        categories = categories,
        merchantMappings = mappings,
        cards = cards,
        duplicates = duplicates,
        uncategorizedCount = uncategorized.size,
        openSplitsCount = splits.size,
        txnCount = txnCount,
        importState = importState,
        onBack = onBack,
        onAccounts = { sheet = SettingsSheet.ACCOUNTS },
        onCategories = { sheet = SettingsSheet.CATEGORIES },
        onCards = onOpenBills,
        onRules = { sheet = SettingsSheet.RULES },
        onDuplicates = { sheet = SettingsSheet.DUPLICATES },
        onUncategorized = {
            categories.firstOrNull { it.key == "other" }?.id?.let(onOpenUncategorized)
        },
        onSplits = { sheet = SettingsSheet.SPLITS },
        onRescan = vm::rerunImport,
        onExport = { scope.launch { CsvExport.share(context, vm.buildCsv()) } },
    )

    when (sheet) {
        SettingsSheet.ACCOUNTS -> AccountsSheet(accounts, vm::renameAccount) { sheet = null }
        SettingsSheet.CATEGORIES -> CategoriesSheet(categories) { sheet = null }
        SettingsSheet.RULES -> MerchantRulesSheet(mappings, categories) { sheet = null }
        SettingsSheet.DUPLICATES -> DuplicateReviewSheet(
            pairs = duplicates,
            categories = categories,
            onDelete = vm::deleteDup,
            onKeepBoth = vm::dismissDup,
            onTransaction = onTransactionClick,
            onDismiss = { sheet = null },
        )
        SettingsSheet.SPLITS -> OpenSplitsSheet(
            transactions = splits,
            onSettle = vm::settleSplit,
            onTransaction = onTransactionClick,
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

@Composable
internal fun SettingsContent(
    accounts: List<Account>,
    categories: List<Category>,
    merchantMappings: List<MerchantMapping>,
    cards: List<CardRow>,
    duplicates: List<DupPair>,
    uncategorizedCount: Int,
    openSplitsCount: Int,
    txnCount: Int,
    importState: ImportState,
    onBack: () -> Unit,
    onAccounts: () -> Unit,
    onCategories: () -> Unit,
    onCards: () -> Unit,
    onRules: () -> Unit,
    onDuplicates: () -> Unit,
    onUncategorized: () -> Unit,
    onSplits: () -> Unit,
    onRescan: () -> Unit,
    onExport: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(ReconcileColors.Ink),
        contentPadding = PaddingValues(
            start = ReconcileSpacing.Screen,
            end = ReconcileSpacing.Screen,
            bottom = 28.dp,
        ),
    ) {
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                Text("Settings", style = MaterialTheme.typography.headlineLarge, color = ReconcileColors.Text)
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(ReconcileRadii.Medium),
                color = ReconcileColors.Surface,
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(ReconcileColors.MintContainer),
                        contentAlignment = Alignment.Center,
                    ) { Text("R", style = MaterialTheme.typography.titleMedium, color = ReconcileColors.Mint) }
                    Spacer(Modifier.padding(start = 12.dp))
                    Column {
                        Text("Reconcile", style = MaterialTheme.typography.bodyLarge, color = ReconcileColors.Text)
                        Text("Your data stays on this device", style = MaterialTheme.typography.bodySmall, color = ReconcileColors.TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            SettingsSectionLabel("Money setup")
            SettingsRow("Accounts", "${accounts.count { it.tail != null }} connected by SMS", onAccounts)
            SettingsRow("Categories", "${categories.size} categories", onCategories)
            SettingsRow("Credit cards", "${cards.size} cards", onCards)
            SettingsRow("Merchant rules", "${merchantMappings.size} learned rules", onRules, showDivider = false)
            Spacer(Modifier.height(24.dp))
            SettingsSectionLabel("Data quality")
        }

        if (duplicates.isNotEmpty()) {
            item {
                StatusAttentionRow(
                    title = "Possible duplicates",
                    subtitle = "Review transactions worth ${Money.format(duplicates.sumOf { it.a.amountPaise })}",
                    icon = Icons.Outlined.ErrorOutline,
                    tone = AttentionTone.Amber,
                    badge = duplicates.size.toString(),
                    onClick = onDuplicates,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        item {
            SettingsAttentionRow(
                title = "Uncategorized",
                subtitle = if (uncategorizedCount == 0) "No transactions need a category" else "Help Reconcile learn $uncategorizedCount transaction${if (uncategorizedCount == 1) "" else "s"}",
                count = uncategorizedCount,
                onClick = onUncategorized,
            )
            if (openSplitsCount > 0) {
                Spacer(Modifier.height(8.dp))
                SettingsAttentionRow(
                    title = "Open splits",
                    subtitle = "$openSplitsCount unsettled shared expense${if (openSplitsCount == 1) "" else "s"}",
                    count = openSplitsCount,
                    onClick = onSplits,
                )
            }
            Spacer(Modifier.height(24.dp))
            SettingsSectionLabel("Data & privacy")
            SettingsRow(
                "Rescan SMS inbox",
                when (importState) {
                    is ImportState.Running -> "Scanning ${importState.scanned}/${importState.total}"
                    else -> "Local only"
                },
                onRescan,
                enabled = importState !is ImportState.Running,
            )
            SettingsRow("Export data", "CSV · $txnCount transactions", onExport)
            SettingsRow("Appearance", "Dark", onClick = {}, enabled = false)
            SettingsRow(
                "Notifications",
                if (cards.any { it.card.enabled }) "Bill reminders" else "Off",
                onClick = onCards,
                showDivider = false,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "Reconcile 2.0 · Offline by design",
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = ReconcileColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = ReconcileColors.TextSecondary)
}

@Composable
private fun SettingsRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = ReconcileColors.Text)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = ReconcileColors.TextSecondary)
    }
    if (showDivider) Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(1.dp).background(ReconcileColors.Border.copy(alpha = 0.7f)))
}

@Composable
private fun SettingsAttentionRow(title: String, subtitle: String, count: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(ReconcileRadii.Medium),
        color = ReconcileColors.Surface,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = ReconcileColors.Text)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ReconcileColors.TextSecondary)
            }
            Text(count.toString(), style = MaterialTheme.typography.titleMedium, color = ReconcileColors.TextSecondary)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090C10, widthDp = 360, heightDp = 800)
@Composable
private fun SettingsPreview() {
    KharchaTheme(ThemeMode.DARK) {
        SettingsContent(
            accounts = emptyList(),
            categories = emptyList(),
            merchantMappings = emptyList(),
            cards = emptyList(),
            duplicates = emptyList(),
            uncategorizedCount = 3,
            openSplitsCount = 1,
            txnCount = 248,
            importState = ImportState.Idle,
            onBack = {},
            onAccounts = {},
            onCategories = {},
            onCards = {},
            onRules = {},
            onDuplicates = {},
            onUncategorized = {},
            onSplits = {},
            onRescan = {},
            onExport = {},
        )
    }
}
