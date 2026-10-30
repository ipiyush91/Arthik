package app.arthik.core.categorise

/** Stable ids shared by the app database and the merchant dictionary. */
object CategoryIds {
    const val FOOD = "food"
    const val GROCERIES = "groceries"
    const val SHOPPING = "shopping"
    const val TRANSPORT = "transport"
    const val BILLS = "bills"
    const val RENT_EMI = "rent_emi"
    const val HEALTH = "health"
    const val ENTERTAINMENT = "entertainment"
    const val TRAVEL = "travel"
    const val CASH = "cash"
    const val TRANSFERS = "transfers"
    const val INVESTMENTS = "investments"
    const val OTHERS = "others"
    const val SALARY = "salary"
    const val INTEREST = "interest"
    const val DIVIDENDS = "dividends"
    const val REFUNDS = "refunds"
    const val OTHER_INCOME = "other_income"

    /** Excluded from spend and income totals. */
    val NEUTRAL = setOf(TRANSFERS, INVESTMENTS)
}

data class DefaultCategory(val id: String, val name: String, val colorArgb: Long, val isIncome: Boolean, val icon: String)

object DefaultCategories {
    val all = listOf(
        DefaultCategory(CategoryIds.FOOD, "Food & Dining", 0xFFFF8A65, false, "restaurant"),
        DefaultCategory(CategoryIds.GROCERIES, "Groceries", 0xFF81C784, false, "basket"),
        DefaultCategory(CategoryIds.SHOPPING, "Shopping", 0xFFBA68C8, false, "bag"),
        DefaultCategory(CategoryIds.TRANSPORT, "Transport / Fuel", 0xFF4FC3F7, false, "car"),
        DefaultCategory(CategoryIds.BILLS, "Bills & Utilities", 0xFFFFD54F, false, "bolt"),
        DefaultCategory(CategoryIds.RENT_EMI, "Rent / EMI", 0xFFE57373, false, "home"),
        DefaultCategory(CategoryIds.HEALTH, "Health", 0xFF4DB6AC, false, "health"),
        DefaultCategory(CategoryIds.ENTERTAINMENT, "Entertainment / Subscriptions", 0xFFF06292, false, "play"),
        DefaultCategory(CategoryIds.TRAVEL, "Travel", 0xFF7986CB, false, "plane"),
        DefaultCategory(CategoryIds.CASH, "Cash", 0xFFA1887F, false, "cash"),
        DefaultCategory(CategoryIds.TRANSFERS, "Transfers", 0xFF90A4AE, false, "swap"),
        DefaultCategory(CategoryIds.INVESTMENTS, "Investments", 0xFF26A69A, false, "trend"),
        DefaultCategory(CategoryIds.OTHERS, "Others", 0xFFB0BEC5, false, "dots"),
        DefaultCategory(CategoryIds.SALARY, "Salary", 0xFF66BB6A, true, "wallet"),
        DefaultCategory(CategoryIds.INTEREST, "Interest", 0xFF9CCC65, true, "percent"),
        DefaultCategory(CategoryIds.DIVIDENDS, "Dividends", 0xFF26C6DA, true, "trend"),
        DefaultCategory(CategoryIds.REFUNDS, "Refunds & Cashback", 0xFFFFB74D, true, "undo"),
        DefaultCategory(CategoryIds.OTHER_INCOME, "Other income", 0xFFAED581, true, "plus"),
    )
}
