package com.abc.expensetracker.data

/**
 * Default categories. Keys must match Categorizer rule keys.
 *
 * Colors: validated with the dataviz palette validator against light (#fcfcfb)
 * and dark (#1a1a19) surfaces — lightness band + chroma floor PASS in both
 * modes; remaining CVD/contrast WARNs are covered because every chart in the
 * app direct-labels its marks and separates fills with 2px surface gaps.
 * sortOrder doubles as the categorical adjacency order the validator checked.
 */
object Seed {
    val categories: List<Category> = listOf(
        Category(key = "food", name = "Food & Dining", emoji = "🍔", color = 0xFFE8590C, sortOrder = 0),
        Category(key = "groceries", name = "Groceries", emoji = "🛒", color = 0xFF5C940D, sortOrder = 1),
        Category(key = "transport", name = "Transport", emoji = "🚕", color = 0xFF1971C2, sortOrder = 2),
        Category(key = "shopping", name = "Shopping", emoji = "🛍️", color = 0xFFD6336C, sortOrder = 3),
        Category(key = "entertainment", name = "Entertainment", emoji = "🎬", color = 0xFF9C36B5, sortOrder = 4),
        Category(key = "bills", name = "Bills & Utilities", emoji = "💡", color = 0xFFB8860B, sortOrder = 5),
        Category(key = "health", name = "Health", emoji = "💊", color = 0xFF0CA678, sortOrder = 6),
        Category(key = "education", name = "Education", emoji = "🎓", color = 0xFF3B5BDB, sortOrder = 7),
        Category(key = "travel", name = "Travel", emoji = "✈️", color = 0xFF06A3C9, sortOrder = 8),
        Category(key = "personal", name = "Personal Care", emoji = "💇", color = 0xFFCC5DE8, sortOrder = 9),
        Category(key = "rent", name = "Rent & Housing", emoji = "🏠", color = 0xFFA85632, sortOrder = 10),
        Category(key = "investments", name = "Investments", emoji = "📈", color = 0xFF2F9E44, sortOrder = 11),
        Category(key = "insurance", name = "Insurance", emoji = "🛡️", color = 0xFF5F3DC4, sortOrder = 12),
        Category(key = "fees", name = "Fees & Charges", emoji = "🧾", color = 0xFFB02525, sortOrder = 13),
        Category(key = "other", name = "Other", emoji = "📦", color = 0xFFA9711C, sortOrder = 14),
        Category(key = "transfer", name = "Transfers", emoji = "🔁", color = 0xFF9775FA, sortOrder = 15),
        Category(key = "income", name = "Income", emoji = "💰", color = 0xFF099268, isIncome = true, sortOrder = 16),
        Category(
            key = "ccpayment", name = "CC Payment", emoji = "💳", color = 0xFF1E6FD9,
            sortOrder = 17, excludeFromTotals = true,
        ),
        Category(
            key = "selftransfer", name = "Self Transfer", emoji = "🔄", color = 0xFF5E718D,
            sortOrder = 18, excludeFromTotals = true,
        ),
    )

    val defaultAccount = Account(name = "Cash", type = AccountType.CASH)

    /** User's cards + bill due days. Editable in-app; seeded once. */
    val cardBills: List<CardBill> = listOf(
        CardBill(name = "slice", dueDay = 6, statementDay = 20, tail = "2544"),
        CardBill(name = "SBI Card", dueDay = 6, statementDay = 20, tail = "3455"),
        CardBill(name = "HDFC Bank", dueDay = 4, statementDay = 18, tail = "2854"),
        CardBill(name = "Axis Bank", dueDay = 30, statementDay = 13, tail = "0653"),
        CardBill(name = "IndusInd Bank", dueDay = 12, statementDay = 26, tail = null),
        CardBill(name = "HSBC", dueDay = 11, statementDay = 25, tail = "8117"),
    )
}
