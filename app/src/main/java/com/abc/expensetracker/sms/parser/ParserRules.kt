package com.abc.expensetracker.sms.parser

/**
 * Rule tables for the SMS parser. To support a new bank/format, extend these
 * lists — the parser itself never needs to change.
 *
 * All patterns are case-insensitive unless noted.
 */
object ParserRules {

    private fun re(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    /** Messages matching any of these are never transactions (OTPs, promos, reminders, requests). */
    val rejectPatterns: List<Regex> = listOf(
        re("""\botp\b"""),
        re("""one[- ]?time password"""),
        re("""verification code"""),
        re("""\bis your code\b"""),
        re("""will be (?:debited|charged|deducted)"""),
        re("""\bis due\b"""),
        re("""\bdue on\b"""),
        re("""\boverdue\b"""),
        re("""minimum (?:amount )?due"""),
        re("""statement (?:is )?(?:ready|generated)"""),
        re("""has requested"""),
        re("""requested money"""),
        re("""payment request"""),
        re("""collect request"""),
        re("""\bdeclined\b"""),
        re("""\bfailed\b"""),
        re("""could not be processed"""),
        re("""insufficient (?:balance|funds)"""),
        re("""\bpre-?approved\b"""),
        re("""loan (?:approved|offer)"""),
        re("""\d+%\s*(?:off|discount|cashback)"""),
        re("""flat\s+(?:rs\.?|₹)?\s*\d+\s*off"""),
        re("""use code"""),
        re("""apply now"""),
        re("""\bhurry\b"""),
        re("""offer valid"""),
        re("""\bcongratulations\b"""),
        re("""you (?:have )?won"""),
        re("""\bwin\b"""),
        re("""\blucky draw\b"""),
        re("""recharge (?:now|and|&)"""),
        re("""reward points? (?:worth|expiring)"""),
        re("""\bmissed call\b"""),
        re("""invest(?:ment)? plan"""),
        re("""\bkyc\b.*(?:update|expir)"""),
    )

    /**
     * Credit-card BILL PAYMENT confirmations (the bank thanking you for paying
     * the card). These are transfers, not income — never stored as transactions.
     * Group 1 = amount; used by [SmsParser.parseCcBillPayment] so the matching
     * card can be auto-marked as paid.
     */
    val ccBillPaymentReceived: List<Regex> = listOf(
        re("""payment\s+of\s+(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)\s+(?:has\s+been\s+)?received\s+towards\s+your\s+.{0,40}?credit\s*card"""),
        re("""received\s+(?:a\s+)?payment\s+of\s+(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)\s+(?:for|towards)\s+(?:your\s+)?.{0,40}?credit\s*card"""),
        re("""received\s+payment\s+of\s+(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?).{0,80}?credited\s+to\s+your\s+.{0,40}?credit\s*card"""),
        re("""payment\s+of\s+(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)\s+(?:credited|posted)\s+to\s+your\s+.{0,40}?(?:credit\s*)?card\s+account"""),
    )

    /** Debit indicators, matched by earliest position in the message. */
    val debitPatterns: List<Regex> = listOf(
        re("""\bspent\b"""),
        re("""\bdebited\b"""),
        re("""\bdebit\b"""),
        re("""\bpaid\b"""),
        re("""\bpayment of\b"""),
        re("""\bsent\b"""),
        re("""\bpurchased?\b"""),
        re("""\bwithdrawn\b"""),
        re("""\bwithdrawal\b"""),
        re("""\bdeducted\b"""),
        re("""\bused at\b"""),
        re("""\bused for\b"""),
        re("""\btxn of\b"""),
        re("""\bcharged\b"""),
    )

    /** Credit indicators. */
    val creditPatterns: List<Regex> = listOf(
        re("""\bcredited\b"""),
        re("""\bcredit of\b"""),
        re("""\breceived\b"""),
        re("""\bdeposited\b"""),
        re("""\brefund(?:ed)?\b"""),
        re("""\bcashback of\b"""),
        re("""\breversed\b"""),
    )

    /** Amount immediately tied to the action verb, e.g. "debited by 125.0", "Spent Rs.125". */
    val amountNearVerb: List<Regex> = listOf(
        re("""(?:spent|debited|credited|paid|sent|received|deposited|withdrawn|deducted|charged|refunded)\s+(?:by|with|for|of)?\s*(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)"""),
        re("""(?:debited|credited)\s+(?:by|with|for)\s+([\d,]+\.\d{1,2})\b"""),
    )

    /** Any currency-tagged amount; the first match in the body is used as fallback. */
    val anyAmount: Regex = re("""(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)""")

    /**
     * Merchant extraction, tried in order — first match wins.
     * Group 1 must capture the merchant/payee text.
     */
    val merchantPatterns: List<Regex> = listOf(
        re("""used\s+at\s+(.{2,40}?)\s+for\s+(?:INR|Rs\.?|₹)"""),          // HSBC: used at zomato for INR
        re("""\bat\s+(.{2,40}?)\s+on\s+\d"""),                              // HDFC/SBI/slice: at Zepto on 09-Jul-26
        re("""\btrf\s+to\s+(.{2,40}?)\s+Ref"""),                            // SBI: trf to AMAZON Refno
        re(""";\s*(.{2,40}?)\s+credited\b"""),                              // ICICI UPI: ; PAYEE credited
        re("""\b(?:to|from)\s+([\w.\-]{2,}@[A-Za-z][\w]{1,15})"""),         // UPI VPA
        re("""\bVPA\s+([\w.\-]{2,}@[A-Za-z][\w]{1,15})"""),
        re("""\bto\s+(.{2,40}?)\s+on\s+\d"""),                              // paid to X on date
        re("""\bto\s+(.{2,40}?)\s*[(,.]"""),                                // sent ... to CheQ (UPI Ref...)
        re("""\bfrom\s+(.{2,40}?)\s+on\s+\d"""),
        re("""\btowards\s+(.{2,40}?)(?:[.,;]|$)"""),
        re("""\bat\s+(.{2,40}?)(?:[.,;]|$)"""),                             // last resort
    )

    /** Card / account last digits. Pair of (pattern, instrument). */
    val accountPatterns: List<Pair<Regex, Instrument>> = listOf(
        re("""(?:credit\s*card|debit\s*card|creditcard|bank\s*card|card|\bcc\b)\s*(?:ending(?:\s+(?:in|with))?|no\.?|number)?\s*[Xx*]*(\d{3,4})\b""") to Instrument.CARD,
        re("""(?:a/c|acct|account)\s*(?:no\.?)?\s*[Xx*]*(\d{3,6})\b""") to Instrument.ACCOUNT,
        re("""\bwallet\b.*?[Xx*]+(\d{3,4})\b""") to Instrument.WALLET,
    )

    /** Reference number (UPI ref, txn ref). */
    val refPatterns: List<Regex> = listOf(
        re("""upi\s*ref(?:erence)?(?:\s*no)?[:.\s]*(\d{9,18})"""),
        re("""\bref\s*(?:no)?[:.\s]*(\d{9,18})"""),
        re("""\brefno[:.\s]*(\d{9,18})"""),
        re("""\bupi[:\s]+(\d{9,18})"""),
    )

    /** Remaining balance / available limit. */
    val balancePatterns: List<Regex> = listOf(
        re("""(?:avl|avbl|available)\s*(?:bal|balance)[:\s\-]*(?:INR|Rs\.?|₹)?\s*([\d,]+(?:\.\d{1,2})?)"""),
        re("""(?:avl|avbl|available)\s*(?:lmt|limit)[:\s\-]*(?:INR|Rs\.?|₹)?\s*([\d,]+(?:\.\d{1,2})?)"""),
        re("""\blimit\s*(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)"""),
        re("""\bbal(?:ance)?[:\s]*(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)"""),
    )

    /** Bank / issuer detection keywords, checked against sender id first, then body. */
    val bankKeywords: List<Pair<String, String>> = listOf(
        "HDFC" to "HDFC Bank",
        "ICICI" to "ICICI Bank",
        "SBICRD" to "SBI Card",
        "SBI" to "SBI",
        "AXIS" to "Axis Bank",
        "KOTAK" to "Kotak Bank",
        "HSBC" to "HSBC",
        "SLICE" to "slice",
        "IDFC" to "IDFC First",
        "YESBNK" to "Yes Bank",
        "YES BANK" to "Yes Bank",
        "PNB" to "PNB",
        "BARODA" to "Bank of Baroda",
        "BOBCRD" to "Bank of Baroda",
        "CANARA" to "Canara Bank",
        "UNION" to "Union Bank",
        "INDUSIND" to "IndusInd Bank",
        "INDUSB" to "IndusInd Bank",
        "RBL" to "RBL Bank",
        "AUBANK" to "AU Bank",
        "FEDERAL" to "Federal Bank",
        "FEDBNK" to "Federal Bank",
        "CITI" to "Citi",
        "AMEX" to "Amex",
        "ONECARD" to "OneCard",
        "PAYTM" to "Paytm",
        "PYTM" to "Paytm",
        "PHONEPE" to "PhonePe",
        "GPAY" to "Google Pay",
        "GOOGLE PAY" to "Google Pay",
        "AMAZON PAY" to "Amazon Pay",
        "MOBIKWIK" to "MobiKwik",
        "JUPITER" to "Jupiter",
        "FIMONY" to "Fi",
    )
}
