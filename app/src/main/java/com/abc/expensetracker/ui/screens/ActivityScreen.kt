package com.abc.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abc.expensetracker.data.Account
import com.abc.expensetracker.data.AccountType
import com.abc.expensetracker.data.Category
import com.abc.expensetracker.data.Txn
import com.abc.expensetracker.sms.parser.Direction
import com.abc.expensetracker.ui.common.CategoryIcon
import com.abc.expensetracker.ui.common.EmptyState
import com.abc.expensetracker.ui.common.FilterControl
import com.abc.expensetracker.ui.common.MoneyText
import com.abc.expensetracker.ui.common.MoneyTone
import com.abc.expensetracker.ui.common.TransactionRow
import com.abc.expensetracker.ui.theme.KharchaTheme
import com.abc.expensetracker.ui.theme.ReconcileColors
import com.abc.expensetracker.ui.theme.ReconcileSpacing
import com.abc.expensetracker.ui.vm.TxnFilter
import com.abc.expensetracker.ui.vm.TxnListVm
import com.abc.expensetracker.util.Dates
import com.abc.expensetracker.util.FinanceMath
import java.time.LocalDate
import java.time.YearMonth

private enum class ActivityFilterSheet { MONTH, TYPE, CATEGORY, ACCOUNT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(vm: TxnListVm, onTransactionClick: (Long) -> Unit) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val txns by vm.txns.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<ActivityFilterSheet?>(null) }

    ActivityContent(
        txns = txns,
        categories = categories,
        accounts = accounts,
        filter = filter,
        onQueryChange = vm::setQuery,
        onOpenMonth = { sheet = ActivityFilterSheet.MONTH },
        onOpenType = { sheet = ActivityFilterSheet.TYPE },
        onOpenCategory = { sheet = ActivityFilterSheet.CATEGORY },
        onOpenAccount = { sheet = ActivityFilterSheet.ACCOUNT },
        onTransactionClick = onTransactionClick,
    )

    sheet?.let { active ->
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            containerColor = ReconcileColors.Surface,
        ) {
            ActivityFilterSheetContent(
                active = active,
                filter = filter,
                categories = categories,
                accounts = accounts,
                onMonth = { vm.setMonth(it); sheet = null },
                onDirection = { vm.setDirection(it); sheet = null },
                onCategory = { vm.setCategory(it); sheet = null },
                onAccount = { vm.setAccount(it); sheet = null },
            )
        }
    }
}

@Composable
internal fun ActivityContent(
    txns: List<Txn>,
    categories: List<Category>,
    accounts: List<Account>,
    filter: TxnFilter,
    onQueryChange: (String) -> Unit,
    onOpenMonth: () -> Unit,
    onOpenType: () -> Unit,
    onOpenCategory: () -> Unit,
    onOpenAccount: () -> Unit,
    onTransactionClick: (Long) -> Unit,
) {
    val categoryById = categories.associateBy { it.id }
    val accountById = accounts.associateBy { it.id }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ReconcileColors.Ink),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = ReconcileSpacing.Screen)) {
                Spacer(Modifier.height(16.dp))
                Text("Activity", style = MaterialTheme.typography.headlineLarge, color = ReconcileColors.Text)
                Text(
                    "Every transaction, clearly",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReconcileColors.TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search merchant, note or amount") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (filter.query.isNotBlank()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ReconcileColors.Surface,
                        unfocusedContainerColor = ReconcileColors.Surface,
                        focusedBorderColor = ReconcileColors.Border,
                        unfocusedBorderColor = ReconcileColors.Border,
                        focusedTextColor = ReconcileColors.Text,
                        unfocusedTextColor = ReconcileColors.Text,
                        focusedLeadingIconColor = ReconcileColors.TextSecondary,
                        unfocusedLeadingIconColor = ReconcileColors.TextSecondary,
                    ),
                )
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ReconcileSpacing.Screen, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterControl(
                        label = filter.month?.let { if (it == Dates.currentMonth()) "This month" else Dates.month(it) }
                            ?: "All time",
                        selected = filter.month != null,
                        onClick = onOpenMonth,
                        icon = Icons.Outlined.DateRange,
                    )
                }
                item {
                    FilterControl(
                        label = when (filter.direction) {
                            Direction.DEBIT -> "Spent"
                            Direction.CREDIT -> "Received"
                            null -> "Type"
                        },
                        selected = filter.direction != null,
                        onClick = onOpenType,
                    )
                }
                item {
                    FilterControl(
                        label = filter.categoryId?.let { id -> categories.find { it.id == id }?.name } ?: "Category",
                        selected = filter.categoryId != null,
                        onClick = onOpenCategory,
                        icon = Icons.Outlined.Category,
                    )
                }
                item {
                    FilterControl(
                        label = filter.accountId?.let { id -> accounts.find { it.id == id }?.name } ?: "Account",
                        selected = filter.accountId != null,
                        onClick = onOpenAccount,
                        icon = Icons.Outlined.AccountBalance,
                    )
                }
            }
        }

        if (txns.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing here",
                    subtitle = "Try clearing a filter, or add a transaction.",
                )
            }
        } else {
            val grouped = txns.groupBy { Dates.toLocalDate(it.timestamp) }
            grouped.forEach { (date, dayTxns) ->
                item(key = "header-$date") {
                    val net = FinanceMath.dayNet(dayTxns)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ReconcileSpacing.Screen, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            dayLabel(date).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = ReconcileColors.TextSecondary,
                        )
                        Spacer(Modifier.weight(1f))
                        MoneyText(
                            amountPaise = net,
                            prefix = if (net >= 0) "+" else "−",
                            tone = if (net >= 0) MoneyTone.Income else MoneyTone.Neutral,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                items(dayTxns, key = { it.id }) { txn ->
                    TransactionRow(
                        txn = txn,
                        category = categoryById[txn.categoryId],
                        accountName = accountById[txn.accountId]?.displayLabel(),
                        onClick = { onTransactionClick(txn.id) },
                        modifier = Modifier.padding(horizontal = ReconcileSpacing.Screen),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityFilterSheetContent(
    active: ActivityFilterSheet,
    filter: TxnFilter,
    categories: List<Category>,
    accounts: List<Account>,
    onMonth: (YearMonth?) -> Unit,
    onDirection: (Direction?) -> Unit,
    onCategory: (Long?) -> Unit,
    onAccount: (Long?) -> Unit,
) {
    val title = when (active) {
        ActivityFilterSheet.MONTH -> "Month"
        ActivityFilterSheet.TYPE -> "Transaction type"
        ActivityFilterSheet.CATEGORY -> "Category"
        ActivityFilterSheet.ACCOUNT -> "Account"
    }
    Column(Modifier.padding(horizontal = ReconcileSpacing.Screen)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = ReconcileColors.Text)
        Spacer(Modifier.height(12.dp))
        when (active) {
            ActivityFilterSheet.MONTH -> {
                FilterChoice("All time", filter.month == null) { onMonth(null) }
                repeat(12) { offset ->
                    val month = Dates.currentMonth().minusMonths(offset.toLong())
                    FilterChoice(
                        if (offset == 0) "This month" else Dates.monthFull(month),
                        filter.month == month,
                    ) { onMonth(month) }
                }
            }
            ActivityFilterSheet.TYPE -> {
                FilterChoice("All transactions", filter.direction == null) { onDirection(null) }
                FilterChoice("Spent", filter.direction == Direction.DEBIT, Icons.Outlined.ArrowUpward) {
                    onDirection(Direction.DEBIT)
                }
                FilterChoice("Received", filter.direction == Direction.CREDIT, Icons.Outlined.ArrowDownward) {
                    onDirection(Direction.CREDIT)
                }
            }
            ActivityFilterSheet.CATEGORY -> {
                FilterChoice("All categories", filter.categoryId == null) { onCategory(null) }
                categories.forEach { category ->
                    FilterChoice(
                        label = category.name,
                        selected = filter.categoryId == category.id,
                        leading = { CategoryIcon(category, size = 40) },
                    ) { onCategory(category.id) }
                }
            }
            ActivityFilterSheet.ACCOUNT -> {
                FilterChoice("All accounts", filter.accountId == null) { onAccount(null) }
                accounts.forEach { account ->
                    FilterChoice(account.displayLabel(), filter.accountId == account.id, Icons.Outlined.AccountBalance) {
                        onAccount(account.id)
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun FilterChoice(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke() ?: icon?.let {
            Icon(it, contentDescription = null, tint = ReconcileColors.TextSecondary)
        }
        if (leading != null || icon != null) Spacer(Modifier.padding(start = 10.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = ReconcileColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Text("Selected", style = MaterialTheme.typography.labelMedium, color = ReconcileColors.Mint)
        }
    }
}

private fun Account.displayLabel(): String =
    tail?.takeUnless { name.contains(it) }?.let { "$name ·$it" } ?: name

private fun dayLabel(date: LocalDate): String = when (date) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
}

@Preview(showBackground = true, backgroundColor = 0xFF090C10, widthDp = 360, heightDp = 780)
@Composable
private fun ActivityPreview() {
    val food = Category(1, "food", "Food & Dining", "", 0xFFFF8A65, sortOrder = 0)
    val income = Category(2, "income", "Income", "", 0xFF65F2C3, isIncome = true, sortOrder = 1)
    val transfer = Category(3, "ccpayment", "CC Payment", "", 0xFF7C8CFF, sortOrder = 2, excludeFromTotals = true)
    val account = Account(1, "Kotak", "Kotak", "5410", AccountType.BANK)
    val now = System.currentTimeMillis()
    val data = listOf(
        Txn(1, 508_00, Direction.DEBIT, "Swiggy", "swiggy", 1, 1, now),
        Txn(2, 8_500_00, Direction.CREDIT, "Salary", "salary", 2, 1, now - 86_400_000),
        Txn(3, 89_599_00, Direction.DEBIT, "CheQ", "cheq", 3, 1, now - 86_400_000, excluded = true),
    )
    KharchaTheme(com.abc.expensetracker.ThemeMode.DARK) {
        ActivityContent(
            txns = data,
            categories = listOf(food, income, transfer),
            accounts = listOf(account),
            filter = TxnFilter(month = Dates.currentMonth()),
            onQueryChange = {},
            onOpenMonth = {},
            onOpenType = {},
            onOpenCategory = {},
            onOpenAccount = {},
            onTransactionClick = {},
        )
    }
}
