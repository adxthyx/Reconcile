package com.abc.expensetracker.sms.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SmsParserTest {

    private val now = 1783968000000L // arbitrary sms receive time

    private fun parse(body: String, sender: String = "VM-BANK") =
        SmsParser.parse(sender, body, now)

    // ------------------------------------------------- user-provided samples

    @Test
    fun `hdfc card spent`() {
        val p = parse(
            "Spent Rs.125 On HDFC Bank Card 2854 At ..ARHAM ENTERPRISE_ On 2026-07-09:21:35:29." +
                "Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 2854 to 7308080808",
            sender = "VM-HDFCBK",
        )
        assertNotNull(p)
        p!!
        assertEquals(12500L, p.amountPaise)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("ARHAM ENTERPRISE", p.merchant)
        assertEquals("2854", p.accountTail)
        assertEquals("HDFC Bank", p.bank)
        assertEquals(Instrument.CARD, p.instrument)
        val dt = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(p.timestamp), ZoneId.systemDefault()
        )
        assertEquals(2026, dt.year)
        assertEquals(7, dt.monthValue)
        assertEquals(9, dt.dayOfMonth)
        assertEquals(21, dt.hour)
        assertEquals(35, dt.minute)
    }

    @Test
    fun `slice card upi spent`() {
        val p = parse(
            "Rs. 319 spent on your credit card xx2544 at Zepto on 09-Jul-26 (UPI Ref: 619031514066). " +
                "Not you? Call 080-4832-9999 - slice",
            sender = "JD-SLICE",
        )
        assertNotNull(p)
        p!!
        assertEquals(31900L, p.amountPaise)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("Zepto", p.merchant)
        assertEquals("2544", p.accountTail)
        assertEquals("slice", p.bank)
        assertEquals(Instrument.CARD, p.instrument)
        assertEquals("619031514066", p.refId)
    }

    @Test
    fun `sbi card spent`() {
        val p = parse(
            "Rs.205.00 spent on your SBI Credit Card ending 3455 at PYUMYNTRADESIGNSPRI on 10/07/26. " +
                "Trxn. not done by you? Report at https://sbicard.com/Dispute",
            sender = "AD-SBICRD",
        )
        assertNotNull(p)
        p!!
        assertEquals(20500L, p.amountPaise)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("PYUMYNTRADESIGNSPRI", p.merchant)
        assertEquals("3455", p.accountTail)
        assertEquals("SBI Card", p.bank)
        assertEquals(Instrument.CARD, p.instrument)
    }

    @Test
    fun `hsbc card used at`() {
        val p = parse(
            "HSBC creditcard xxxxx8117 used at zomato for INR 158.65 on 10/07/26." +
                "Limit Rs 65221.54 Due Rs 135778.46.Report fraud on +914061268002",
            sender = "VM-HSBC",
        )
        assertNotNull(p)
        p!!
        assertEquals(15865L, p.amountPaise)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("zomato", p.merchant)
        assertEquals("8117", p.accountTail)
        assertEquals("HSBC", p.bank)
        assertEquals(6522154L, p.balancePaise)
    }

    // ------------------------------------------------------ other bank forms

    @Test
    fun `sbi account debit trf`() {
        val p = parse(
            "Dear UPI user A/C X4321 debited by 125.0 on date 09Jul26 trf to AMAZON PAY Refno 519876543210. " +
                "If not u? call 1800111109. -SBI",
            sender = "CP-SBIINB",
        )
        assertNotNull(p)
        p!!
        assertEquals(12500L, p.amountPaise)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("AMAZON PAY", p.merchant)
        assertEquals("4321", p.accountTail)
    }

    @Test
    fun `icici upi debit with payee credited`() {
        val p = parse(
            "ICICI Bank Acct XX123 debited for Rs 200.00 on 09-Jul-26; JOHN DOE credited. " +
                "UPI: 519912345678. Call 18002662 for dispute.",
            sender = "QP-ICICIB",
        )
        assertNotNull(p)
        p!!
        assertEquals(20000L, p.amountPaise)
        assertEquals(Direction.DEBIT, p.direction) // "debited" comes before "credited"
        assertEquals("JOHN DOE", p.merchant)
        assertEquals("123", p.accountTail)
        assertEquals("ICICI Bank", p.bank)
    }

    @Test
    fun `salary credit`() {
        val p = parse(
            "INR 85,000.00 credited to your A/c No XX9876 on 01/07/26 by NEFT. Avl Bal INR 1,25,430.50 - Axis Bank",
            sender = "AX-AXISBK",
        )
        assertNotNull(p)
        p!!
        assertEquals(8500000L, p.amountPaise)
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals("9876", p.accountTail)
        assertEquals("Axis Bank", p.bank)
    }

    @Test
    fun `kotak upi debit to vpa`() {
        val p = parse(
            "Sent Rs.450.00 from Kotak Bank AC X1234 to swiggy@icici on 08-07-26.UPI Ref 556677889900. " +
                "Not you, kotak.com/fraud",
            sender = "KM-KOTAKB",
        )
        assertNotNull(p)
        p!!
        assertEquals(45000L, p.amountPaise)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("swiggy@icici", p.merchant)
    }

    // -------------------------------------------------------------- rejects

    @Test
    fun `otp rejected`() {
        assertNull(parse("123456 is the OTP for your txn of Rs.500 at Amazon. Do not share with anyone. -HDFC Bank"))
    }

    @Test
    fun `promo rejected`() {
        assertNull(parse("Get 50% off + extra cashback offer on your first order! Use code WELCOME. Hurry, offer valid till tonight."))
    }

    @Test
    fun `future autopay rejected`() {
        assertNull(parse("Rs.649 will be debited from your account for Netflix subscription on 15/07/26."))
    }

    @Test
    fun `payment request rejected`() {
        assertNull(parse("john@okaxis has requested Rs.900 from you. Approve in your UPI app before 10pm."))
    }

    @Test
    fun `bill due reminder rejected`() {
        assertNull(parse("Your credit card bill of Rs.4,530 is due on 18/07/26. Pay now to avoid late fee."))
    }

    @Test
    fun `failed txn rejected`() {
        assertNull(parse("Your payment of Rs.250 to Uber failed. Any amount debited will be reversed within 5 days."))
    }

    @Test
    fun `plain chat message rejected`() {
        assertNull(parse("Hey, are we still on for dinner tomorrow?"))
    }

    // -------------------------------------- credit-card bill payments (v2)

    @Test
    fun `sbi cc bbps payment rejected and detected`() {
        val body = "We have received payment of Rs.70,000.00 via BBPS & the same has been credited to your " +
            "SBI Credit Card. Your available limit is Rs.71,744.37."
        assertNull(parse(body, sender = "AD-SBICRD"))
        val cc = SmsParser.parseCcBillPayment(body)
        assertNotNull(cc)
        assertEquals(7_000_000L, cc!!.amountPaise)
    }

    @Test
    fun `hdfc cc payment rejected and detected with tail`() {
        val body = "DEAR HDFCBANK CARDMEMBER, PAYMENT OF Rs. 74491.00 RECEIVED TOWARDS YOUR CREDIT CARD " +
            "ENDING WITH 2854 ON 29-6-2026.YOUR AVAILABLE LIMIT IS RS. 95775.75"
        assertNull(parse(body, sender = "VM-HDFCBK"))
        val cc = SmsParser.parseCcBillPayment(body)
        assertNotNull(cc)
        assertEquals(7_449_100L, cc!!.amountPaise)
        assertEquals("2854", cc.cardTail)
    }

    @Test
    fun `hsbc cc payment rejected and detected`() {
        val body = "Dear Customer, we have received a payment of INR 37025.87 for credit card ending 8117 " +
            "on 08-JUL-26. Thank you for using HSBC credit card."
        assertNull(parse(body, sender = "VM-HSBC"))
        val cc = SmsParser.parseCcBillPayment(body)
        assertNotNull(cc)
        assertEquals(3_702_587L, cc!!.amountPaise)
        assertEquals("8117", cc.cardTail)
    }

    @Test
    fun `axis cc payment rejected and detected`() {
        val body = "Payment of INR 16823 has been received towards your Axis Bank Credit Card XX0653 " +
            "on 02-03-26 - Axis Bank"
        assertNull(parse(body, sender = "AX-AXISBK"))
        val cc = SmsParser.parseCcBillPayment(body)
        assertNotNull(cc)
        assertEquals(1_682_300L, cc!!.amountPaise)
        assertEquals("0653", cc.cardTail)
    }

    @Test
    fun `cheq debit parses with merchant and categorizes as cc payment`() {
        val body = "Rs. 36,925.87 sent from a/c xx1808 on 07-Jul-26 to CheQ (UPI Ref: 110157356923). " +
            "Not you? Call 08048329999 - slice"
        assertNull(SmsParser.parseCcBillPayment(body)) // debit side, not a confirmation
        val p = parse(body, sender = "JD-SLICE")
        assertNotNull(p)
        p!!
        assertEquals(3_692_587L, p.amountPaise)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("CheQ", p.merchant)
        assertEquals("1808", p.accountTail)
        assertEquals(Categorizer.CC_PAYMENT, Categorizer.categorize(p.merchant, body, p.direction))
    }

    @Test
    fun `normal card spend still parses after cc rules`() {
        val p = parse("Spent Rs.500 On HDFC Bank Card 2854 At SWIGGY On 2026-07-10:12:00:00.")
        assertNotNull(p)
        assertEquals(50_000L, p!!.amountPaise)
    }

    // -------------------------------------------------------- categorization

    @Test
    fun `categorizer basics`() {
        assertEquals("food", Categorizer.categorize("Zomato", "", Direction.DEBIT))
        assertEquals("groceries", Categorizer.categorize("Zepto", "", Direction.DEBIT))
        assertEquals("transport", Categorizer.categorize("UBER INDIA", "", Direction.DEBIT))
        assertEquals("food", Categorizer.categorize("SWIGGY", "", Direction.DEBIT))
        assertEquals("other", Categorizer.categorize("ARHAM ENTERPRISE", "", Direction.DEBIT))
        assertEquals("income", Categorizer.categorize(null, "salary credited", Direction.CREDIT))
        assertEquals("income", Categorizer.categorize("RANDOM SENDER", "", Direction.CREDIT))
    }
}
