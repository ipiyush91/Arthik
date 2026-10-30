package app.arthik.core

import app.arthik.core.Pub.expect
import app.arthik.core.Pub.on
import app.arthik.core.TestSupport.categoriser
import app.arthik.core.TestSupport.parse
import app.arthik.core.TestSupport.txn
import app.arthik.core.categorise.CategoryIds
import app.arthik.core.model.AccountType
import app.arthik.core.model.Channel
import app.arthik.core.model.Direction.CREDIT
import app.arthik.core.model.Direction.DEBIT
import app.arthik.core.model.ParseResult
import app.arthik.core.model.TxnKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Spec 14.4: mutual funds, EPF, depositories, exchanges, brokers and dividends (sms-research/02 and 03). */
class PublicInvestmentsTest {
    // ---- Mutual funds ----
    @Test fun `HDFC MF SIP allotment carries scheme, folio, units and NAV`() {
        val t = txn("Your SIP Purchase in Folio 38822412/82 under HDFC Mid Cap Fund-DG for Rs. 7,000.65 has been processed at the NAV of 224.603 for 31.169 units and 24-Feb-2026. Your smart statement https://shrtsms.in/HDFCMF/xYSrbW. Enter your PAN as the password. Sincerely, HDFCMF", "AD-HDFCMF-AC", on("2026-02-24"))
        expect(t, "7,000.65", CREDIT, date = "2026-02-24", institution = "HDFC MF", accountType = AccountType.INVESTMENT, kind = TxnKind.MF_ALLOTMENT)
        val inv = t.investment!!
        assertEquals("HDFC Mid Cap Fund-DG", inv.scheme)
        assertEquals("38822412/82", inv.folio)
        assertEquals(31.169, inv.units)
        assertEquals(224.603, inv.nav)
        assertEquals(CategoryIds.INVESTMENTS, categoriser.categorise(t).categoryId)
    }

    @Test fun `Navi MF SIP processed before allotment, lakh grouping`() {
        val a = txn("Unit Allotment Update:\nYour SIP purchase of Rs.499.98 in Navi Nifty Next 50 Index Fund DG has been processed at applicable NAV. The units will be alloted in 1-2 working days. For further queries, please visit the Navi app.\nTeam Navi Mutual Fund", "AD-NAVAMC-S")
        expect(a, "499.98", CREDIT, institution = "Navi MF", accountType = AccountType.INVESTMENT, kind = TxnKind.MF_ALLOTMENT)
        assertEquals("Navi Nifty Next 50 Index Fund DG", a.investment!!.scheme)
        assertNull(a.investment!!.units)
        val b = txn("Unit Allotment Update:\nYour SIP purchase of Rs. 1,49,999.98 in Navi Largecap Fund DG has been processed at applicable NAV. The units will be alloted in 1-2 working days.\nTeam Navi Mutual Fund", "VK-NAVAMC-T")
        expect(b, "1,49,999.98", CREDIT, kind = TxnKind.MF_ALLOTMENT)
        assertEquals("Navi Largecap Fund DG", b.investment!!.scheme)
    }

    @Test fun `Navi NAV update is ignored`() {
        assertIs<ParseResult.Ignored>(parse("NAV updated for Navi Largecap Fund. Latest NAV: 12.3456. Visit the Navi app for details.", "AD-NAVAMC-S"))
    }

    @Test fun `bank-side MF purchase via NEFT is an investment debit`() {
        val t = txn("Nippon India Mutual Fund has received Rs 1000.00 from your A/c 3363 via NEFT on 02-Jan-2026 06:50:32. Ref no. FBBT260023158681 - Federal Bank", "VM-FEDBNK-S", on("2026-01-02"))
        expect(t, "1000.00", DEBIT, acct = "3363", merchant = "Nippon India Mutual Fund", ref = "FBBT260023158681", date = "2026-01-02",
            institution = "Federal Bank", accountType = AccountType.SAVINGS, kind = TxnKind.INVESTMENT, channel = Channel.NEFT)
        assertEquals(CategoryIds.INVESTMENTS, categoriser.categorise(t).categoryId)
    }

    @Test fun `HDFC BillPay to AMC billers is an investment debit`() {
        listOf(
            Triple("Bill Paid!\nAxisMF Bill XXXXXX-XXXXXX of Rs. 500.00 paid on 05-Oct-2026 from HDFC Bank Account XXXXXXXXXXXXXX.\nBillPay Ref: XXXXXXXXXXXXXX", "AxisMF", "500.00") to "2026-10-05",
            Triple("Bill Paid!\nICICIPruMF Bill XXBROKER-XXXXXX of Rs. 1000.00 paid on 07-Oct-2026 from HDFC Bank Account XXXXXXXXXXXXXX.\nBillPay Ref: XXXXXXXXXXXXXX", "ICICIPruMF", "1000.00") to "2026-10-07",
            Triple("Bill Paid!\nMiraeAssetMF Bill XXXXXX-XXXXXX of Rs. 1000.00 paid on 12-Oct-2026 from HDFC Bank Account XXXXXXXXXXXXXX.\nBillPay Ref: XXXXXXXXXXXXXX", "MiraeAssetMF", "1000.00") to "2026-10-12",
        ).forEach { (s, date) ->
            val t = txn(s.first, "AD-HDFCBK-S", on(date))
            expect(t, s.third, DEBIT, acct = null, merchant = s.second, date = date, kind = TxnKind.INVESTMENT, channel = Channel.BILLPAY, accountType = AccountType.SAVINGS)
            assertEquals(CategoryIds.INVESTMENTS, categoriser.categorise(t).categoryId)
        }
    }

    @Test fun `platform SIP executed is booked, SIP registration is not`() {
        val t = txn("SIP Txn Successful!\nYour Monthly SIP for Rs.1000.0 placed on 23/05/2026 …", "VM-GROWWS-S", on("2026-05-23"))
        expect(t, "1000.0", CREDIT, date = "2026-05-23", institution = "Groww", accountType = AccountType.INVESTMENT, kind = TxnKind.INVESTMENT)
        assertIs<ParseResult.Ignored>(parse("You made a Smart Move!\nYour Monthly SIP request is registered …", "VM-GROWWS-S"))
    }

    // ---- EPFO ----
    @Test fun `EPFO contribution books the contribution, not the passbook balance`() {
        val t = txn("Dear XXXXXXXX41 your passbook balance against PYKRP1234567 is Rs. 1,23,456.78. Contribution of Rs. 3,600/- for due month Jun-26 has been received.", "VA-EPFOHO-G", on("2026-07-15"))
        expect(t, "3,600", CREDIT, acct = "4567", balance = "1,23,456.78", date = "2026-07-15", institution = "EPFO",
            accountType = AccountType.EPF, kind = TxnKind.EPF_CONTRIBUTION)
        assertEquals(CategoryIds.INVESTMENTS, categoriser.categorise(t).categoryId)
    }

    @Test fun `EPFO degenerate contribution, OTP and UAN link are never booked`() {
        val dash = parse("Dear Member, contribution of Rs. -- for due month Jun-26 has been received.", "VA-EPFOHO-G")
        assertFalse(dash is ParseResult.Transaction, "$dash")
        assertIs<ParseResult.Ignored>(parse("Dear Member (UAN : XXXX XXXX 4242), OTP to login to Member Interface is 123456 (valid for 10 mins) at EPFO.", "VA-EPFOHO-G"))
        assertIs<ParseResult.Ignored>(parse("Your UAN 101234567890 has been linked by establishment ACME SOLUTIONS PVT LTD with date 01/07/2026.", "AX-EPFOHO-S"))
    }

    // ---- Depositories ----
    @Test fun `CDSL T-bill redemption debit has quantity and security but no amount`() {
        val t = txn("CDSL: DEBITED IN A/C *xxxxxxxx 100 TREASURE BILL OF GOVERNMENT OF INDIA TOWARDS REDEMPTION ON 18/01/2019. CONTACT YOUR DP FOR MORE INFORMATION.", "CP-CDSLIN-S", on("2019-01-18"))
        expect(t, "0", DEBIT, acct = null, date = "2019-01-18", institution = "CDSL", accountType = AccountType.INVESTMENT, kind = TxnKind.SECURITIES_DEBIT)
        assertEquals(100.0, t.investment!!.quantity)
        assertEquals("TREASURE BILL OF GOVERNMENT OF INDIA", t.investment!!.security)
    }

    @Test fun `CDSL capital-reduction debit fragment`() {
        val t = txn("DEBITED IN A/C * PURSUANT TO REDUCTION OF SHARE CAPITAL FOR 1000 SHARES OF SUBEX LIMITED", "CP-CDSLIN-S")
        expect(t, "0", DEBIT, kind = TxnKind.SECURITIES_DEBIT)
        assertEquals(1000.0, t.investment!!.quantity)
        assertEquals("SUBEX LIMITED", t.investment!!.security)
    }

    @Test fun `CDSL TPIN and pledge messages are ignored`() {
        assertIs<ParseResult.Ignored>(parse("Your Generated TPIN code is 123456", "AD-CDSLIN-S"))
        assertIs<ParseResult.Ignored>(parse("TPIN to avail CDSL e-DIS Facility for BOID XXXXXXXXXXXXXXXX", "AD-CDSLIN-S"))
        assertIs<ParseResult.Ignored>(parse("Dear Investor, your DP GEOJIT INVESTMENTS LIMITED has initiated a margin pledge from your (NSDL/ CDSL) demat account", "AD-CDSLIN-S"))
    }

    // ---- Exchanges ----
    @Test fun `NSE traded value is a snapshot, never money`() {
        val s = parse("Dear DHPXXXXX5Q,Your traded value for 09-FEB-24 CM Rs 4640 FO Rs 37649.25 Check your registered email.", "VK-NSESMS-S", on("2024-02-09"))
        assertIs<ParseResult.Snapshot>(s)
        assertEquals("NSE", s.institution)
        assertEquals(AccountType.INVESTMENT, s.accountType)
        assertNull(s.balance)
        assertEquals("traded value for 09-FEB-24 CM Rs 4640 FO Rs 37649.25", s.note)
    }

    @Test fun `NSE funds and securities balance is a broker snapshot`() {
        val s = parse("ICICI SECURITIES LIMITED on DD-MM-YY reported your Fund bal Rs. 0 & Securities bal 0. This excludes your Bank, DP & PMS bal with the broker - NSE.", "JS-NSESMS-S")
        assertIs<ParseResult.Snapshot>(s)
        assertEquals(0L, s.balance)
        assertEquals("ICICI SECURITIES LIMITED", s.note)
        // Fund balances can be negative.
        val neg = parse("ICICI SECURITIES LIMITED on 12-10-26 reported your Fund bal Rs. -1,250.50 & Securities bal 0. This excludes your Bank, DP & PMS bal with the broker - NSE.", "JS-NSESMS-S")
        assertIs<ParseResult.Snapshot>(neg)
        assertEquals(-125050L, neg.balance)
    }

    // ---- Brokers ----
    @Test fun `Zerodha payout is a withdrawal from the broker account`() {
        val t = txn("withdrawal request for Rs 13916.620 has been processed", "VM-ZERODH-S")
        expect(t, "13916.62", DEBIT, institution = "Zerodha", accountType = AccountType.INVESTMENT, kind = TxnKind.BROKER_PAYOUT)
        assertEquals(CategoryIds.INVESTMENTS, categoriser.categorise(t).categoryId)
    }

    // ---- Dividends and ACH ----
    @Test fun `ICICI ACH dividend credit`() {
        val t = txn("ICICI Bank Account XX227 credited:Rs. 104.00 on 19-May-26. Info ACH*VARUNBEVERAGES LIMI*778. Available Balance is Rs 1,11,231.93", "AD-ICICIT-S", on("2026-05-19"))
        expect(t, "104.00", CREDIT, acct = "227", date = "2026-05-19", balance = "1,11,231.93", kind = TxnKind.DIVIDEND, channel = Channel.MANDATE)
        assertTrue(t.merchant!!.contains("VARUNBEVERAGES LIMI"))
        assertEquals(CategoryIds.DIVIDENDS, categoriser.categorise(t).categoryId)
    }

    @Test fun `Saraswat ACH credit is a dividend`() {
        val t = txn("Your A/c no. 1234 is credited with INR 100.50 on 13-10-2025 towards ACH Credit:MERCHANT NAME. Current Bal is INR 950.00 CR  - Saraswat Bank", "JD-SARBNK-S", on("2025-10-13"))
        expect(t, "100.50", CREDIT, acct = "1234", merchant = "MERCHANT NAME", date = "2025-10-13", balance = "950.00", institution = "Saraswat Bank", kind = TxnKind.DIVIDEND)
        val gas = txn("Your A/c no. 1234 is credited with INR 100.50 on 13-10-2025 towards ACH Credit:GUJARAT GAS LIMITED. Current Bal is INR 941.23 CR  - Saraswat Bank", "JD-SARBNK-S", on("2025-10-13"))
        expect(gas, "100.50", CREDIT, merchant = "GUJARAT GAS LIMITED", balance = "941.23", kind = TxnKind.DIVIDEND)
    }

    @Test fun `ICICI ACH debit without space after Info is a mandate debit, not a dividend`() {
        val t = txn("ICICI Bank Account XX123 debited Rs. 5,000.00 on 19-May-26 InfoACH*BD-ACHKFL.Avl Bal Rs. 3,25,000.04.To dispute call 1800 blah blah", "AX-ICICIT-S", on("2026-05-19"))
        expect(t, "5,000.00", DEBIT, acct = "123", merchant = "ACH*BD-ACHKFL", date = "2026-05-19", balance = "3,25,000.04", channel = Channel.MANDATE)
        assertTrue(t.kind != TxnKind.DIVIDEND)
    }

    @Test fun `NACH mandate to NSE Clearing is not a transaction`() {
        val r = parse("Auto Pay HDFC Bank NACH Mandate : Rs. 100000.00 UMRN:HDFC7031703262015557 To:NationalSecuritiesClearin Freq ADHO received today for processing.", "VM-HDFCBK-S")
        assertIs<ParseResult.Ignored>(r)
    }
}
