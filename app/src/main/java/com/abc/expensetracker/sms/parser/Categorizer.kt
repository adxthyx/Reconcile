package com.abc.expensetracker.sms.parser

/**
 * Keyword-based auto-categorization. Returns a category KEY (stable string);
 * the data layer maps keys to Category rows. User corrections are stored as
 * MerchantMapping rows and always override this table.
 */
object Categorizer {

    const val OTHER = "other"
    const val INCOME = "income"
    const val CC_PAYMENT = "ccpayment"
    const val SELF_TRANSFER = "selftransfer"

    /** Short/ambiguous merchant names that must match exactly, never by substring. */
    private val exactMerchant: Map<String, String> = mapOf(
        "cred" to CC_PAYMENT,
        "cheq" to CC_PAYMENT,
    )

    /** category key -> merchant/body keywords (lowercase, substring match). */
    val rules: List<Pair<String, List<String>>> = listOf(
        "food" to listOf(
            "swiggy", "zomato", "dominos", "domino's", "pizza", "kfc", "mcdonald", "burger",
            "cafe", "coffee", "starbucks", "barista", "restaurant", "biryani", "eatsure",
            "box8", "faasos", "behrouz", "dunkin", "subway", "wow momo", "haldiram", "chai",
        ),
        "groceries" to listOf(
            "zepto", "blinkit", "bigbasket", "instamart", "grofers", "jiomart", "dmart",
            "d-mart", "kirana", "supermarket", "grocery", "fresh mart", "nature basket",
            "milk", "dairy",
        ),
        "transport" to listOf(
            "uber", "ola", "rapido", "redbus", "irctc", "metro", "namma metro", "bmtc",
            "petrol", "fuel", "hpcl", "iocl", "bpcl", "shell", "fastag", "parking",
            "yulu", "blusmart",
        ),
        "shopping" to listOf(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "snapdeal", "croma",
            "reliance digital", "vijay sales", "decathlon", "ikea", "lifestyle", "westside",
            "zudio", "max fashion", "pantaloons", "tata cliq",
        ),
        "entertainment" to listOf(
            "netflix", "hotstar", "spotify", "prime video", "primevideo", "bookmyshow",
            "pvr", "inox", "youtube", "sonyliv", "sony liv", "zee5", "jiocinema", "steam",
            "playstation", "xbox", "gaana", "wynk",
        ),
        "bills" to listOf(
            "electricity", "bescom", "mseb", "tneb", "kseb", "tata power", "adani electricity",
            "airtel", "jio", "vodafone", "vi ", "bsnl", "broadband", "act fibernet", "hathway",
            "dth", "tata sky", "tatasky", "d2h", "gas", "indane", "hp gas", "bharatgas",
            "water bill", "postpaid", "billdesk", "bbps",
        ),
        "health" to listOf(
            "pharmacy", "apollo", "medplus", "netmeds", "pharmeasy", "1mg", "tata 1mg",
            "practo", "hospital", "clinic", "diagnostic", "lab", "wellness", "cult.fit",
            "cultfit", "healthkart",
        ),
        "education" to listOf(
            "udemy", "coursera", "byjus", "unacademy", "school", "college", "university",
            "tuition", "course fee", "exam fee",
        ),
        "travel" to listOf(
            "makemytrip", "goibibo", "cleartrip", "ixigo", "easemytrip", "oyo", "airbnb",
            "indigo", "air india", "vistara", "spicejet", "akasa", "hotel", "resort",
            "treebo", "fabhotel",
        ),
        "personal" to listOf(
            "salon", "spa", "urban company", "urbanclap", "barber", "grooming",
        ),
        "rent" to listOf(
            "rent", "nobroker", "housing.com", "nestaway", "landlord", "society maintenance",
            "mygate",
        ),
        "investments" to listOf(
            "zerodha", "groww", "upstox", "kuvera", "etmoney", "indmoney", "mutual fund",
            "sip ", "nps", "ppf", "smallcase", "coin dcx", "angel one",
        ),
        "insurance" to listOf(
            "lic ", "lic premium", "policybazaar", "insurance", "acko", "digit", "star health",
            "icici lombard", "hdfc ergo", "premium paid",
        ),
        "fees" to listOf(
            "annual fee", "joining fee", "late fee", "charges", "gst", "convenience fee",
            "service charge", "amc",
        ),
        INCOME to listOf(
            "salary", "sal credit", "payroll", "stipend", "dividend", "interest credited",
            "interest paid",
        ),
        // Paying off a credit card bill = transfer between own accounts, not spend.
        CC_PAYMENT to listOf(
            "cheq", "cred club", "cred.club", "credit card bill", "card bill payment",
            "towards your credit card", "cc payment",
        ),
    )

    /**
     * @param merchant parsed merchant/payee, may be null
     * @param body full SMS body as secondary signal
     */
    fun categorize(merchant: String?, body: String, direction: Direction): String {
        val m = merchant?.lowercase()?.trim() ?: ""
        val b = body.lowercase()
        exactMerchant[m]?.let { return it }
        for ((key, keywords) in rules) {
            if (keywords.any { m.contains(it) }) return key
        }
        for ((key, keywords) in rules) {
            if (keywords.any { b.contains(it) }) return key
        }
        return if (direction == Direction.CREDIT) INCOME else OTHER
    }
}
