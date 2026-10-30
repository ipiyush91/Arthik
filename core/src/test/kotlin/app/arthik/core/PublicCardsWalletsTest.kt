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
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Spec Section 14, credit cards (F52-F67) and wallets (F68-F72): verbatim formats from sms-research/01. */
class PublicCardsWalletsTest {
    private val cc = AccountType.CREDIT_CARD

    // ---- HDFC credit cards ----
    @Test fun `F52 HDFC card spend and PIXEL card used`() {
        val t = txn("Spent Rs.2049 On HDFC Bank Card 1003 At BLUE LEAF STORES On 2025-11-11:11:04:40.Not You? To Block+Reissue Call 18001234567/SMS BLOCK CC 8515 to 7308080808", "AD-HDFCBK-S", on("2025-11-11"))
        expect(t, "2049", DEBIT, acct = "1003", merchant = "BLUE LEAF STORES", date = "2025-11-11", accountType = cc, kind = TxnKind.PURCHASE)
        val px = txn("Rs 1368.00 used on HDFC Bank PIXEL Card at TEST MERCHANT on 05/10/26 09:03.Not You? SMS BLOCKPCC 2222 to 1800000000 or https://example.invalid/b", "AD-HDFCBK-S", on("2026-10-05"))
        expect(px, "1368.00", DEBIT, acct = "2222", merchant = "TEST MERCHANT", date = "2026-10-05", accountType = cc)
    }

    @Test fun `F53 HDFC card reversal and refund initiated`() {
        val rev = txn("Transaction Reversed!On HDFC Bank CREDIT Card xx5555 Amt: Rs.862.16 By AMAZON                000 On 2026-08-21:05:20:26", "AD-HDFCBK-S", on("2026-08-21"))
        expect(rev, "862.16", CREDIT, acct = "5555", merchant = "AMAZON", date = "2026-08-21", accountType = cc, kind = TxnKind.REFUND)
        assertEquals(CategoryIds.REFUNDS, categoriser.categorise(rev).categoryId)
        val ref = txn("Refund initiated: Amt: Rs.34274.66 on HDFC Bank Credit Card 1111. To receive your Refund,please update your Bank details: TnC.", "AD-HDFCBK-S")
        expect(ref, "34274.66", CREDIT, acct = "1111", accountType = cc, kind = TxnKind.REFUND)
    }

    // ---- Axis ----
    @Test fun `F54 Axis card spend in all three layouts plus foreign currency`() {
        val a = txn("Spent INR 131\nAxis Bank Card no. XX0818\n05-10-25 09:43:27 IST\nSwiggy Limi\nAvl Limit: INR 217162.72\nNot you? SMS BLOCK 0818 to 919951860002", "AX-AXISBK-S", on("2025-10-05"))
        expect(a, "131", DEBIT, acct = "0818", merchant = "Swiggy Limi", date = "2025-10-05", limit = "217162.72", accountType = cc)
        assertEquals("Swiggy", categoriser.categorise(a).merchant)
        assertEquals(CategoryIds.FOOD, categoriser.categorise(a).categoryId)
        val b = txn("Spent\nCard no. XX7441\nINR 562\n01-09-25 12:04:18\nAVENUE SUPE\nAvl Lmt INR 5120.87\nSMS BLOCK 7441 to 919951860002, if not you - Axis Bank", "CP-AXISBK-S", on("2025-09-01"))
        expect(b, "562", DEBIT, acct = "7441", merchant = "AVENUE SUPE", date = "2025-09-01", limit = "5120.87")
        val c = txn("Spent INR 1568 using Axis Bank Credit Card no. XX1234 on 24-10-26 11:07:10 IST at BLINKIT. Avl limit: INR 50999.49. Not you? SMS BLOCK 1234 to +919951860002.", "JX-AXISBK-S", on("2026-10-24"))
        expect(c, "1568", DEBIT, acct = "1234", merchant = "BLINKIT", date = "2026-10-24", limit = "50999.49")
        val fx = txn("Spent USD 100 Axis Bank Card no. XX0000 14-12-25 23:32:40 IST TESTMERCHANT / Avl Limit: INR 57888.3 Not you? SMS BLOCK 0000 to 1800000000", "AX-AXISBK-S", on("2025-12-14"))
        expect(fx, "100", DEBIT, acct = "0000", merchant = "TESTMERCHANT", date = "2025-12-14", limit = "57888.3")
        assertEquals("USD", fx.foreignCurrency)
    }

    // ---- YES, Kotak, IDFC FIRST, IndusInd ----
    @Test fun `F55 YES Bank RuPay card spent on UPI`() {
        val t = txn("INR 404.36 spent on YES BANK Card X3349 @UPI_C N S FUEL PORT 24-08-2025 06:17:25 pm. Avl Lmt INR 211,476.24. SMS BLKCC 3349 to 9840909000 if not you", "AD-YESBNK-S", on("2025-08-24"))
        expect(t, "404.36", DEBIT, acct = "3349", merchant = "C N S FUEL PORT", date = "2025-08-24", limit = "211,476.24", accountType = cc, channel = Channel.UPI)
        assertEquals(CategoryIds.TRANSPORT, categoriser.categorise(t).categoryId)
    }

    @Test fun `F56 Kotak card spend, refund and due reminder`() {
        val k = "AD-KOTAKB-S"
        val s = txn("INR 20 spent on Kotak Credit Card x5236 on 23-JAN-2026 at UPI-638903921672-CORN. Avl limit INR 73733.02 Fraud? https://www.kotak.bank.in/KBANKT/querytxn", k, on("2026-01-23"))
        expect(s, "20", DEBIT, acct = "5236", merchant = "CORN", ref = "638903921672", date = "2026-01-23", limit = "73733.02", accountType = cc, channel = Channel.UPI)
        val r = txn("INR 2 from Airport Lounge refunded to your Kotak Credit Card x8848 on 17-Jul-2026. Opted for EMI? Please call 18602662666 to cancel.", k, on("2026-07-17"))
        expect(r, "2", CREDIT, acct = "8848", merchant = "Airport Lounge", date = "2026-07-17", kind = TxnKind.REFUND)
        // Published via an AD-AXISBK-S header in a user report; the Kotak header is the normal route.
        val due = parse("Payment of INR 1577 on Kotak Credit Card xx2222 is due on 13-07-26. Min due: INR 100. Tap to pay: https://example.invalid/pay Ignore if paid", k, on("2026-07-01"))
        assertIs<ParseResult.Statement>(due)
        assertEquals("2222", due.cardLast4)
        assertEquals(157700L, due.totalDue)
        assertEquals(10000L, due.minimumDue)
        assertEquals(Pub.day("2026-07-13"), due.dueDate)
    }

    @Test fun `F57 IDFC FIRST card forex spend`() {
        val t = txn("Transaction Successful! EUR 500.00 spent on your IDFC FIRST Bank Credit Card ending XX1234 at AMAZON EU on 08-FEB-2025 at 01:28 PM Avbl Limit: INR 4074.10 If not done by you, call 180010888", "AD-IDFCFB-S", on("2025-02-08"))
        expect(t, "500.00", DEBIT, acct = "1234", merchant = "AMAZON EU", date = "2025-02-08", limit = "4074.10", accountType = cc)
        assertEquals("EUR", t.foreignCurrency)
    }

    @Test fun `F58 IndusInd card spend and refund`() {
        val s = txn("INR 1,250.00 spent on IndusInd Card XX1234 on 14-06-2026 04:21:45 pm at INSTAMART. Avl Lmt: INR 48,750.00. To dispute, call 18602677777/SMS BLOCK 1234 to 5676757", "AD-INDUSB-S", on("2026-06-14"))
        expect(s, "1,250.00", DEBIT, acct = "1234", merchant = "INSTAMART", date = "2026-06-14", limit = "48,750.00", accountType = cc)
        assertEquals(CategoryIds.GROCERIES, categoriser.categorise(s).categoryId)
        val r = txn("Dear Customer, refund of INR 250 from Swiggy Limited Banga has been credited to your IndusInd Bank Credit Card XX1234 on 11-Jun-26 and the refund amount has been adjusted against the outstanding on your card account - IndusInd Bank", "AD-INDUSB-S", on("2026-06-11"))
        expect(r, "250", CREDIT, acct = "1234", merchant = "Swiggy Limited Banga", date = "2026-06-11", accountType = cc, kind = TxnKind.REFUND)
    }

    @Test fun `F59 Scapia card spend with rupee sign`() {
        val a = txn("Hi! Your txn of ₹882.00 at Carnatic Cafe Gurgaon In on your Scapia Federal Visa credit card was successful. And you've earned 10% rewards on this spend! Not you? Go to Scapia support on the app or call 18002961199. -Federal Bank", "AD-FEDSCP-S")
        expect(a, "882.00", DEBIT, merchant = "Carnatic Cafe Gurgaon In", institution = "Scapia", accountType = cc)
        val b = txn("Your txn of ₹2,351.00 at Amazon on your Scapia Federal Visa credit card earned you 10% rewards! Not you? Call 18002961199. - Federal Bank", "AD-FEDSCP-S")
        expect(b, "2,351.00", DEBIT, merchant = "Amazon", accountType = cc)
    }

    // ---- SBI Card, ICICI ----
    @Test fun `F60 SBI Card spend in math-sans letters and payments`() {
        val s = txn("Rs.90.00 𝗌𝗉𝖾𝗇𝗍 𝗈𝗇 𝗒𝗈𝗎𝗋 𝖲𝖡𝖨 𝖢𝗋𝖾𝖽𝗂𝗍 𝖢𝖺𝗋𝖽 𝖾𝗇𝖽𝗂𝗇𝗀 5667 at SUPREMEGOURMET on 13/02/26. 𝖳𝗋𝗑𝗇. 𝗇𝗈𝗍 𝖽𝗈𝗇𝖾 𝖻𝗒 𝗒𝗈𝗎? 𝖱𝖾𝗉𝗈𝗋𝗍 𝖺𝗍 https://sbicard.com/Dispute", "VM-SBICRD-S", on("2026-02-13"))
        expect(s, "90.00", DEBIT, acct = "5667", merchant = "SUPREMEGOURMET", date = "2026-02-13", institution = "SBI Card")
        val p = txn("Your payment of Rs.1,644.55 has been credited to your SBI Credit Card ending with 5667. Your available limit is Rs.48,355.45.", "VM-SBICRD-S")
        expect(p, "1,644.55", CREDIT, acct = "5667", limit = "48,355.45", kind = TxnKind.CARD_PAYMENT)
        assertEquals(CategoryIds.TRANSFERS, categoriser.categorise(p).categoryId)
        val p2 = txn("Dear SBI Cardholder, payment of Rs. 8857.32 for your SBI Credit Card has been successfully processed. ref no : ABC12XY34ZQ56.", "VM-SBICRD-S")
        expect(p2, "8857.32", CREDIT, acct = null, ref = "ABC12XY34ZQ56", kind = TxnKind.CARD_PAYMENT)
    }

    @Test fun `F61 ICICI statement, refund, reversal and BBPS payment`() {
        val i = "JD-ICICIT-S"
        val st = parse("ICICI Bank Credit Card XX1005 Statement is sent to yo************me@example.com. Total of Rs 5,227.02 or minimum of Rs 270.00 is due by 30-MAR-26.", i, on("2026-03-10"))
        assertIs<ParseResult.Statement>(st)
        assertEquals("1005", st.cardLast4)
        assertEquals(522702L, st.totalDue)
        assertEquals(27000L, st.minimumDue)
        assertEquals(Pub.day("2026-03-30"), st.dueDate)
        expect(txn("EXAMPLE STORE GROCERY refund of Rs 202.00 credited to ICICI Bank Card XX1005 on 17-MAY-26.", i, on("2026-05-17")),
            "202.00", CREDIT, acct = "1005", merchant = "EXAMPLE STORE GROCERY", date = "2026-05-17", accountType = cc, kind = TxnKind.REFUND)
        expect(txn("Dear Customer, your transaction of Rs.3,587.00 on ICICI Bank Credit Card XX1001 at FITNESS STUDIO dated 08-Aug-26 at 19:31:50 PM has been reversed. A credit will reflect shortly.", i, on("2026-08-08")),
            "3,587.00", CREDIT, acct = "1001", merchant = "FITNESS STUDIO", date = "2026-08-08", kind = TxnKind.REFUND)
        expect(txn("Payment of Rs 26,266.00 has been received on your ICICI Bank Credit Card XX9006 through Bharat Bill Payment System on 06-DEC-25.", i, on("2025-12-06")),
            "26,266.00", CREDIT, acct = "9006", date = "2025-12-06", accountType = cc, kind = TxnKind.CARD_PAYMENT, channel = Channel.BILLPAY)
    }

    // ---- BOBCARD, PNB, AU, AmEx, CRED ----
    @Test fun `F62 BOBCARD spend and payment`() {
        val s = txn("ALERT: INR 1,500.00 is spent on your BOBCARD ending 1234 at AMAZON on 25-11-2024 10:30:00. Available credit limit is Rs 42,981.46 -Bank of Baroda", "AD-BOBCRD-S", on("2024-11-25"))
        expect(s, "1,500.00", DEBIT, acct = "1234", merchant = "AMAZON", date = "2024-11-25", limit = "42,981.46", accountType = cc)
        val p = txn("Payment of Rs 1317.80 received for your BOBCARD ending 2844 on 2026-06-17. Visit bobcard app: bobcard.io/App or online card account at https://www.bobcard.co.in/ for details.", "AD-BOBCRD-S", on("2026-06-17"))
        expect(p, "1317.80", CREDIT, acct = "2844", date = "2026-06-17", accountType = cc, kind = TxnKind.CARD_PAYMENT)
    }

    @Test fun `F63 PNB card UPI spend and payment`() {
        val s = txn("PNB Credit Card 1234 debited with Rs.1,499 [CODE:AB1234] at AMAZON INDIA on 05-10-2026 10:12 through UPI: 123456789012 Avl limit Rs. 47383.5. -PNB", "AD-PNBCCD-S", on("2026-10-05"))
        expect(s, "1,499", DEBIT, acct = "1234", merchant = "AMAZON INDIA", ref = "123456789012", date = "2026-10-05", limit = "47383.5", accountType = cc)
        val p = txn("Thank you Rs.668.79/- has been received as payment towards your PNB credit card  XX1234 via Online Payment. Your available credit limit is Rs.50000. - PNB", "AD-PNBCCD-S")
        expect(p, "668.79", CREDIT, acct = "1234", limit = "50000", kind = TxnKind.CARD_PAYMENT)
    }

    @Test fun `F64 AU card spend`() {
        val t = txn("INR 259.90 spent at TELEGRAM PREMIUM on AU Bank Credit Card x1234 21-03-2026 05:49:40 PM. Not you? Call 180012001500 or SMS PBLOCK 1234 to 5676767", "AD-AUBANK-S", on("2026-03-21"))
        expect(t, "259.90", DEBIT, acct = "1234", merchant = "TELEGRAM PREMIUM", date = "2026-03-21", accountType = cc)
    }

    @Test fun `F65 AmEx spend with five-digit card tail and long date`() {
        val t = txn("Alert: You've spent INR 1,017.70 on your AMEX card ** 91000 at VOUCHER PLAT on 20 August 2025", "TX-AMEXIN-S", on("2025-08-20"))
        expect(t, "1,017.70", DEBIT, acct = "91000", merchant = "VOUCHER PLAT", date = "2025-08-20", institution = "AmEx", accountType = cc)
    }

    @Test fun `F67 CRED card-payment confirmation is not booked twice`() {
        val r = parse("Payment of Rs.50000 has been successfully credited towards your ICICI Bank Credit Card. Your payment was settled in 3 seconds - CRED", "AD-CREDIN-S")
        assertIs<ParseResult.Ignored>(r)
        assertEquals("ignore.cred.card.payment", r.ruleId)
    }

    // ---- Wallets ----
    @Test fun `F68 Amazon Pay balance payment and debit`() {
        val a = txn("Payment of Rs 150.50 using Apay Balance successful at merchant. Updated Balance is Rs 850.00 - SMS by Juspay", "AD-JUSPAY-S")
        expect(a, "150.50", DEBIT, merchant = "merchant", balance = "850.00", institution = "Amazon Pay", accountType = AccountType.WALLET, channel = Channel.WALLET)
        val b = txn("Your Apay Wallet balance is debited for INR 250.00 Transaction Reference Number is 123456789012 - Powered by Juspay", "AD-APAY-S")
        expect(b, "250.00", DEBIT, ref = "123456789012", accountType = AccountType.WALLET)
    }

    @Test fun `F69 PhonePe gift card payment`() {
        val t = txn("You've paid Rs.3 via PhonePe gift card to EXAMPLE HOSIERY on May 9, 2026 at 7:06:36 PM. Not you? Call us on 022-10000002. Remaining balance Rs.0.", "AD-PHONPE-S", on("2026-05-09"))
        expect(t, "3", DEBIT, merchant = "EXAMPLE HOSIERY", date = "2026-05-09", balance = "0", accountType = AccountType.WALLET)
    }

    @Test fun `F70 PayZapp wallet spend and load`() {
        val s = txn("Rs.60 debited from your PayZapp Wallet on 08-10-2025 13:37:29\nBal: Rs.2440\nNot you?  Report:\nhttps://example.invalid/x or SMS BLOCKPZW", "AD-PAYZAP-S", on("2025-10-08"))
        expect(s, "60", DEBIT, date = "2025-10-08", balance = "2440", accountType = AccountType.WALLET, kind = null)
        val l = txn("Rs.2500\ncredited to your PayZapp Wallet\nOn 08-10-2025 13:37:18\nBal:Rs.2500\nNot you? Report:https://example.invalid/y -HDFC Bank", "AD-PAYZAP-S", on("2025-10-08"))
        expect(l, "2500", CREDIT, date = "2025-10-08", balance = "2500", institution = "PayZapp", kind = TxnKind.WALLET_TOPUP)
        assertEquals(CategoryIds.TRANSFERS, categoriser.categorise(l).categoryId)
    }

    @Test fun `F71 CRED wallet load`() {
        val t = txn("Update from CRED wallet: Rs.208.00 has been successfully credited to your CRED wallet. Your updated CRED wallet balance is Rs.243.00. For any queries or support, call 080-10000000.", "AD-CREDWL-S")
        expect(t, "208.00", CREDIT, balance = "243.00", institution = "CRED", accountType = AccountType.WALLET, kind = TxnKind.WALLET_TOPUP)
    }

    @Test fun `F72 slice account UPI sent and received`() {
        val s = txn("Rs. 100 sent from a/c xx2743 on 17-Jun-26 to Hussain Shaikh (UPI Ref: 616851070000). Not you? Call 08048329999 - slice", "AD-SLICEIT-S", on("2026-06-17"))
        expect(s, "100", DEBIT, acct = "2743", merchant = "Hussain Shaikh", ref = "616851070000", date = "2026-06-17", institution = "slice")
        val r = txn("Rs. 2,000 received in slice A/c xx2743 on 15-Jun-26 from NASIMUDDIN NAJAMUDDIN SHAIKH via UPI (Ref ID: 212756500000). Avl. Bal. Rs. 2,203.56 - slice", "AD-SLCEIT-S", on("2026-06-15"))
        expect(r, "2,000", CREDIT, acct = "2743", merchant = "NASIMUDDIN NAJAMUDDIN SHAIKH", ref = "212756500000", date = "2026-06-15", balance = "2,203.56")
        assertNull(r.foreignCurrency)
    }
}
