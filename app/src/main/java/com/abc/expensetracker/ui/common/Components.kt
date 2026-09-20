package com.abc.expensetracker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abc.expensetracker.data.Category
import com.abc.expensetracker.data.Txn
import com.abc.expensetracker.sms.parser.Direction
import com.abc.expensetracker.ui.theme.ReconcileColors
import com.abc.expensetracker.ui.theme.ReconcileRadii
import com.abc.expensetracker.ui.theme.ReconcileSpacing
import com.abc.expensetracker.util.Money
import kotlin.math.abs

enum class MoneyTone { Neutral, Income, Risk, Secondary }

@Composable
fun MoneyText(
    amountPaise: Long,
    modifier: Modifier = Modifier,
    prefix: String = "",
    tone: MoneyTone = MoneyTone.Neutral,
    style: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val color = when (tone) {
        MoneyTone.Neutral -> ReconcileColors.Text
        MoneyTone.Income -> ReconcileColors.Mint
        MoneyTone.Risk -> ReconcileColors.Coral
        MoneyTone.Secondary -> ReconcileColors.TextSecondary
    }
    Text(
        text = prefix + Money.format(abs(amountPaise)),
        modifier = modifier,
        style = style,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = ReconcileSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = ReconcileColors.TextSecondary,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun CategoryIcon(
    category: Category?,
    modifier: Modifier = Modifier,
    size: Int = 48,
    overrideIcon: ImageVector? = null,
) {
    val color = category?.let { Color(it.color) } ?: ReconcileColors.TextSecondary
    val icon = overrideIcon ?: categoryIconFor(category?.key)
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(if (size >= 48) 12.dp else 10.dp))
            .background(color.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = category?.name ?: "Category",
            tint = color,
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}

fun categoryIconFor(key: String?): ImageVector = when (key) {
    "food" -> Icons.Outlined.Restaurant
    "groceries" -> Icons.Outlined.ShoppingCart
    "transport" -> Icons.Outlined.DirectionsCar
    "shopping" -> Icons.Outlined.ShoppingBag
    "entertainment" -> Icons.Outlined.Movie
    "bills" -> Icons.Outlined.Bolt
    "health" -> Icons.Outlined.MedicalServices
    "education" -> Icons.Outlined.School
    "travel" -> Icons.Outlined.Flight
    "personal" -> Icons.Outlined.Spa
    "rent" -> Icons.Outlined.Home
    "investments" -> Icons.Outlined.TrendingUp
    "insurance" -> Icons.Outlined.Shield
    "fees" -> Icons.Outlined.ReceiptLong
    "transfer", "selftransfer" -> Icons.Outlined.SwapHoriz
    "income" -> Icons.Outlined.ArrowDownward
    "ccpayment" -> Icons.Outlined.CreditCard
    "friends" -> Icons.Outlined.Group
    "cash" -> Icons.Outlined.Payments
    else -> Icons.Outlined.MoreHoriz
}

data class TransactionPresentation(
    val subtitle: String,
    val status: String? = null,
    val needsReview: Boolean = false,
)

fun transactionPresentation(txn: Txn, category: Category?, accountName: String?): TransactionPresentation {
    val account = accountName?.takeIf { it.isNotBlank() }
    return when {
        txn.excluded -> {
            val reason = when (category?.key) {
                "ccpayment" -> "Card payment"
                "transfer", "selftransfer" -> "Transfer"
                "friends" -> "Shared settlement"
                else -> category?.name ?: "Excluded"
            }
            TransactionPresentation("$reason · Excluded", "not in spend")
        }
        txn.direction == Direction.CREDIT -> TransactionPresentation(
            listOfNotNull(category?.name ?: "Income", account).joinToString(" · ")
        )
        category?.key == "other" -> TransactionPresentation(
            listOfNotNull("Uncategorized", account).joinToString(" · "),
            status = "needs review",
            needsReview = true,
        )
        else -> TransactionPresentation(
            listOfNotNull(category?.name ?: "Other", account).joinToString(" · ")
        )
    }
}

@Composable
fun TransactionRow(
    txn: Txn,
    category: Category?,
    accountName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val presentation = transactionPresentation(txn, category, accountName)
    val credit = txn.direction == Direction.CREDIT
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(vertical = ReconcileSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(category)
        Spacer(Modifier.width(ReconcileSpacing.Md))
        Column(Modifier.weight(1f)) {
            Text(
                txn.merchant ?: category?.name ?: "Unknown",
                style = MaterialTheme.typography.bodyLarge,
                color = ReconcileColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                presentation.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (presentation.needsReview) ReconcileColors.TextSecondary else ReconcileColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(ReconcileSpacing.Sm))
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(
                amountPaise = txn.amountPaise,
                prefix = if (credit) "+" else "−",
                tone = when {
                    credit -> MoneyTone.Income
                    txn.excluded -> MoneyTone.Secondary
                    else -> MoneyTone.Neutral
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            presentation.status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (presentation.needsReview) ReconcileColors.TextSecondary else ReconcileColors.TextSecondary,
                )
            }
        }
    }
    if (showDivider) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 60.dp)
                .height(1.dp)
                .background(ReconcileColors.Border.copy(alpha = 0.7f))
        )
    }
}

enum class AttentionTone { Amber, Indigo, Neutral }

@Composable
fun StatusAttentionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: AttentionTone = AttentionTone.Neutral,
    badge: String? = null,
) {
    val colors = when (tone) {
        AttentionTone.Amber -> ReconcileColors.AmberContainer to ReconcileColors.Amber
        AttentionTone.Indigo -> ReconcileColors.IndigoContainer to ReconcileColors.Indigo
        AttentionTone.Neutral -> ReconcileColors.Surface to ReconcileColors.TextSecondary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ReconcileRadii.Medium))
            .background(colors.first)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = ReconcileSpacing.Lg, vertical = ReconcileSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.second, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(ReconcileSpacing.Md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = ReconcileColors.Text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.second)
        }
        badge?.let {
            Surface(shape = RoundedCornerShape(24.dp), color = colors.second) {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = ReconcileColors.Ink,
                )
            }
        } ?: Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.second)
    }
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, tone: MoneyTone = MoneyTone.Neutral) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = ReconcileColors.TextSecondary)
        Spacer(Modifier.height(ReconcileSpacing.Xs))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = when (tone) {
                MoneyTone.Neutral -> ReconcileColors.Text
                MoneyTone.Income -> ReconcileColors.Mint
                MoneyTone.Risk -> ReconcileColors.Coral
                MoneyTone.Secondary -> ReconcileColors.TextSecondary
            },
        )
    }
}

@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = ReconcileColors.Mint,
) {
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(ReconcileColors.Border.copy(alpha = 0.55f)),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun FilterControl(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val background = if (selected) ReconcileColors.Mint else ReconcileColors.Elevated
    val foreground = if (selected) ReconcileColors.Ink else ReconcileColors.TextSecondary
    Row(
        modifier = modifier
            .heightIn(min = ReconcileSpacing.TouchTarget)
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .border(1.dp, if (selected) Color.Transparent else ReconcileColors.Border, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = foreground, maxLines = 1)
    }
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun ReconcileBottomNavigation(
    items: List<BottomNavItem>,
    selectedRoute: String?,
    onSelect: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ReconcileColors.Ink)
            .border(width = 1.dp, color = ReconcileColors.Border.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = selectedRoute == item.route || selectedRoute?.startsWith(item.route + "/") == true
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 60.dp)
                    .clip(RoundedCornerShape(ReconcileRadii.Small))
                    .clickable(role = Role.Tab) { onSelect(item) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) ReconcileColors.MintContainer else Color.Transparent)
                        .padding(horizontal = 18.dp, vertical = 7.dp),
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (selected) ReconcileColors.Mint else ReconcileColors.TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) ReconcileColors.Mint else ReconcileColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ReconcileSpacing.TouchTarget),
        enabled = enabled,
        shape = RoundedCornerShape(ReconcileRadii.Small),
        colors = ButtonDefaults.buttonColors(
            containerColor = ReconcileColors.Mint,
            contentColor = ReconcileColors.Ink,
            disabledContainerColor = ReconcileColors.Elevated,
            disabledContentColor = ReconcileColors.TextSecondary,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ReconcileSpacing.TouchTarget),
        enabled = enabled,
        shape = RoundedCornerShape(ReconcileRadii.Small),
        border = androidx.compose.foundation.BorderStroke(1.dp, ReconcileColors.Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ReconcileColors.Text),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ReconcileSpacing.TouchTarget),
        shape = RoundedCornerShape(ReconcileRadii.Small),
        colors = ButtonDefaults.buttonColors(
            containerColor = ReconcileColors.CoralContainer,
            contentColor = ReconcileColors.Coral,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.AutoMirrored.Outlined.ListAlt,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(ReconcileRadii.Small))
                .background(ReconcileColors.Elevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ReconcileColors.TextSecondary)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = ReconcileColors.Text)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ReconcileColors.TextSecondary)
    }
}

@Composable
fun ChartContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(ReconcileRadii.Large),
        color = ReconcileColors.Surface,
    ) {
        Box(Modifier.padding(ReconcileSpacing.Lg)) { content() }
    }
}

// ---------------------------------------------------------------- Legacy-compatible wrappers used by the old, now-unrouted screens.

@Composable
fun CategoryDot(category: Category?, size: Int = 40) = CategoryIcon(category = category, size = size)

@Composable
fun TxnRow(txn: Txn, category: Category?, accountName: String?, onClick: () -> Unit) =
    TransactionRow(txn, category, accountName, onClick, modifier = Modifier.padding(horizontal = 16.dp))

@Composable
fun StatCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(ReconcileRadii.Large),
        colors = CardDefaults.cardColors(containerColor = ReconcileColors.Surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = ReconcileColors.TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
        }
    }
}

@Composable
fun BudgetBar(spent: Long, limit: Long, modifier: Modifier = Modifier) {
    val fraction = if (limit > 0) spent.toFloat() / limit else 0f
    val over = spent > limit
    Column(modifier) {
        ProgressBar(progress = fraction, color = if (over) ReconcileColors.Coral else ReconcileColors.Mint)
        Spacer(Modifier.height(4.dp))
        Row {
            Text(
                "${Money.format(spent)} of ${Money.format(limit)}",
                style = MaterialTheme.typography.labelSmall,
                color = ReconcileColors.TextSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (over) "Over by ${Money.format(spent - limit)}" else "${Money.format(limit - spent)} left",
                style = MaterialTheme.typography.labelSmall,
                color = if (over) ReconcileColors.Coral else ReconcileColors.TextSecondary,
            )
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, subtitle: String, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val ignoredEmoji = emoji
    EmptyState(title = title, subtitle = subtitle, modifier = modifier)
}
