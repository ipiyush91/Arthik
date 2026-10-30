package app.arthik.core

import app.arthik.core.TestSupport.parse
import app.arthik.core.TestSupport.rupees
import app.arthik.core.TestSupport.txn
import app.arthik.core.categorise.CategoryIds
import app.arthik.core.model.AccountType
import app.arthik.core.model.Channel
import app.arthik.core.model.Direction
import app.arthik.core.model.ParseResult
import app.arthik.core.model.TxnKind
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Spec Section 13: the user's real (anonymised) messages. */
class UserSamplesTest {
    private val icici = "JD-ICICIT-S"
    private val sbiCard = "VM-SBICRD-S"
    private val hdfc = "AD-HDFCBK-S"

    @Test fun `I1 ICICI UPI debit`() {
        val t = txn("ICICI Bank Acct XX468 debited for Rs 91.00 on 30-Oct-26; Kumari Poonam credited. UPI:120807593798. Call 18002662 for dispute. SMS BLOCK 468 to 9215676766.", icici)
        assertEquals("ICICI Bank", t.institution)
        assertEquals(AccountType.SAVINGS, t.accountType)
        assertEquals("468", t.accountLast4)
        assertEquals(Direction.DEBIT, t.direction)
        assertEquals(9100L, t.amount)
        assertEquals("Kumari Poonam", t.merchant)
        assertEquals("120807593798", t.reference)
        assertEquals(LocalDate.of(2026, 10, 30), t.date)
        assertEquals(Channel.UPI, t.channel)
        val c = TestSupport.categoriser.categorise(t)
        assertEquals(CategoryIds.OTHERS, c.categoryId)
        assertTrue(c.personToPerson)
    }

    @Test fun `I1 large ICICI amounts and lender payees`() {
        val t = txn("ICICI Bank Acct XX468 debited for Rs 21011.80 on 11-Sep-26; INSTANTLOANPAY credited. UPI:140757997282. Call 18002662 for dispute. SMS BLOCK 468 to 9215676766.", icici, LocalDateTime.of(2026, 9, 11, 9, 0))
        assertEquals(2101180L, t.amount)
        assertEquals(TxnKind.EMI, t.kind)
        assertEquals(CategoryIds.RENT_EMI, TestSupport.categoriser.categorise(t).categoryId)
        val f = txn("ICICI Bank Acct XX468 debited for Rs 20000.00 on 28-Oct-26; KHOSYA FINLEASE credited. UPI:152626985243. Call 18002662 for dispute. SMS BLOCK 468 to 9215676766.", icici)
        assertEquals(CategoryIds.RENT_EMI, TestSupport.categoriser.categorise(f).categoryId)
        val w = txn("ICICI Bank Acct XX468 debited for Rs 21515.00 on 11-Sep-26; Weekline Invest credited. UPI:130825359277. Call 18002662 for dispute. SMS BLOCK 468 to 9215676766.", icici, LocalDateTime.of(2026, 9, 11, 9, 0))
        assertEquals(CategoryIds.INVESTMENTS, TestSupport.categoriser.categorise(w).categoryId)
    }

    @Test fun `CRED and Amazon Pay balance are transfers, merchants map to buckets`() {
        val cred = txn("ICICI Bank Acct XX468 debited for Rs 1046.00 on 23-Oct-26; CRED Club credited. UPI:666206435157. Call 18002662 for dispute. SMS BLOCK 468 to 9215676766.", icici)
        assertEquals(TxnKind.CARD_PAYMENT, cred.kind)
        assertEquals(CategoryIds.TRANSFERS, TestSupport.categoriser.categorise(cred).categoryId)
        val apay = txn("ICICI Bank Acct XX468 debited for Rs 942.00 on 24-Oct-26; Amazon Pay Bala credited. UPI:629703605310. Call 18002662 for dispute. SMS BLOCK 468 to 9215676766.", icici)
        assertEquals(CategoryIds.TRANSFERS, TestSupport.categoriser.categorise(apay).categoryId)
        val milk = txn("ICICI Bank Acct XX468 debited for Rs 100.00 on 15-Oct-26; Mother dairy 16 credited. UPI:111906274105. Call 18002662 for dispute. SMS BLOCK 468 to 9215676766.", icici)
        assertEquals(CategoryIds.GROCERIES, TestSupport.categoriser.categorise(milk).categoryId)
        val pharma = txn("ICICI Bank Acct XX468 debited for Rs 70.00 on 02-Oct-26; KRISHNA PHARMAC credited. UPI:104847624133. Call 18002662 for dispute. SMS BLOCK 468 to 9215676766.", icici)
        assertEquals(CategoryIds.HEALTH, TestSupport.categoriser.categorise(pharma).categoryId)
        val hw = txn("ICICI Bank Acct XX468 debited for Rs 120.00 on 06-Oct-26; Goyal Hardware credited. UPI:102832724935. Call 18002662 for dispute. SMS BLOCK 468 to 9215676766.", icici)
        assertEquals(CategoryIds.SHOPPING, TestSupport.categoriser.categorise(hw).categoryId)
    }

    @Test fun `I2 ICICI UPI credit, with and without the stray space`() {
        val t = txn("Dear Customer, Acct XX468 is credited with Rs 400.00 on 23-Oct-26 from ASHUTOSH DUBEY. UPI:131720997577-ICICI Bank.", icici)
        assertEquals(Direction.CREDIT, t.direction)
        assertEquals(40000L, t.amount)
        assertEquals("ASHUTOSH DUBEY", t.merchant)
        assertEquals("131720997577", t.reference)
        assertEquals(CategoryIds.OTHER_INCOME, TestSupport.categoriser.categorise(t).categoryId)
        val t2 = txn("Dear Customer, Acct XX468 is credited with Rs 835.00 on 18-Sep-26 from BIRENDRA KUMAR . UPI:662735084632-ICICI Bank.", icici, LocalDateTime.of(2026, 9, 18, 9, 0))
        assertEquals("BIRENDRA KUMAR", t2.merchant)
    }

    @Test fun `I3 ICICI IMPS credit`() {
        val t = txn("ICICI Bank Account XX468 is credited with Rs 21,000.00 on 30-Sep-26 by Account linked to mobile number XXXXX86407. IMPS Ref. no. 627317559036.", icici, LocalDateTime.of(2026, 9, 30, 9, 0))
        assertEquals(2100000L, t.amount)
        assertEquals(Channel.IMPS, t.channel)
        assertEquals("627317559036", t.reference)
    }

    @Test fun `I4 cash deposit credit with Indian grouped balance`() {
        val t = txn("Your ICICI Bank Account XX468 credited:Rs. 23,000.00 on 07-Sep-26. Info CAM*13321ORY*CA. Available Balance is Rs. 1,93,523.41.", icici, LocalDateTime.of(2026, 9, 7, 9, 0))
        assertEquals(2300000L, t.amount)
        assertEquals(19352341L, t.balance)
        assertEquals(TxnKind.CASH_DEPOSIT, t.kind)
        assertEquals(CategoryIds.TRANSFERS, TestSupport.categoriser.categorise(t).categoryId)
    }

    @Test fun `I5 online bill payment debit with no spaces`() {
        val t = txn("ICICI Bank Acc XX468 debited Rs. 27,534.82 on 04-Sep-26 InfoBIL*ONL*BIC03.Avl Bal Rs. 21,815.91.To dispute call 18002662 or SMS BLOCK 468 to 9215676766", icici, LocalDateTime.of(2026, 9, 4, 9, 0))
        assertEquals(2753482L, t.amount)
        assertEquals(2181591L, t.balance)
        assertEquals("BIL*ONL*BIC03", t.merchant)
        assertEquals(Channel.BILLPAY, t.channel)
        assertEquals(CategoryIds.BILLS, TestSupport.categoriser.categorise(t).categoryId)
    }

    @Test fun `I6 ATM withdrawal is cash`() {
        val t = txn("ICICI Bank Acc XX468 debited Rs. 1,000.00 on 20-Aug-26 NFS*CASH WDL*. Avb Bal Rs. 8,390.72. To dispute Call 18002662 or SMS BLOCK 468 to 9215676766 .", icici, LocalDateTime.of(2026, 8, 20, 9, 0))
        assertEquals(TxnKind.CASH_WITHDRAWAL, t.kind)
        assertEquals(839072L, t.balance)
        assertEquals(CategoryIds.CASH, TestSupport.categoriser.categorise(t).categoryId)
    }

    @Test fun `I7 KYC notice ignored`() {
        assertIs<ParseResult.Ignored>(parse("KYC updation for your ICICI Bank Savings Account XX468 will be due on Oct 06, 2026. Please update KYC through the iMobile app or Net Banking or visit any Branch for KYC updation or contact your Relationship Manager for assistance", icici))
    }

    @Test fun `C1 ICICI credit card spend in INR`() {
        val t = txn("INR 746.00 spent using ICICI Bank Card XX2002 on 12-Aug-26 on RAZORPAY RETAIL. Avl Limit: INR 27,765.00. If not you, call 1800 2662/SMS BLOCK 2002 to 9215676766.", icici, LocalDateTime.of(2026, 8, 12, 9, 0))
        assertEquals(AccountType.CREDIT_CARD, t.accountType)
        assertEquals("2002", t.accountLast4)
        assertEquals(74600L, t.amount)
        assertEquals(2776500L, t.availableLimit)
        assertEquals("RAZORPAY RETAIL", t.merchant)
    }

    @Test fun `C2 ICICI statement`() {
        val s = parse("ICICI Bank Credit Card XX5008 Statement is sent to pi************di@live.in. Total of Rs 1,640.00 or minimum of Rs 100.00 is due by 07-NOV-26.", icici)
        assertIs<ParseResult.Statement>(s)
        assertEquals("5008", s.cardLast4)
        assertEquals(164000L, s.totalDue)
        assertEquals(10000L, s.minimumDue)
        assertEquals(LocalDate.of(2026, 11, 7), s.dueDate)
    }

    @Test fun `F1 F2 FASTag toll and recharge`() {
        val toll = txn("Rs.130 paid at Basheerapur Toll Plaza for DL10CC2010 on 21-10-2026 10:24:13 with ICICI Bank FASTag. Bal Rs.325. Call 18002100104 for dispute", icici)
        assertEquals(AccountType.FASTAG, toll.accountType)
        assertEquals(13000L, toll.amount)
        assertEquals(32500L, toll.balance)
        assertEquals("Basheerapur Toll Plaza", toll.merchant)
        assertEquals(LocalDate.of(2026, 10, 21), toll.date)
        assertEquals(CategoryIds.TRANSPORT, TestSupport.categoriser.categorise(toll).categoryId)
        val rc = txn("Recharge of Rs. 500.00 for ICICI Bank FASTag linked veh. no. DL10CC2010 is processed on 17/10/2026 17:07:41 . Balance is Rs. 730.00 .", icici)
        assertEquals(Direction.CREDIT, rc.direction)
        assertEquals(73000L, rc.balance)
        assertEquals(CategoryIds.TRANSFERS, TestSupport.categoriser.categorise(rc).categoryId)
    }

    @Test fun `S1 SBI Card spend maps legal entity to Meesho`() {
        val t = txn("Rs.240.00 spent on your SBI Credit Card ending 2383 at FASHNEARTECHNOLOGIESPRI on 23/10/26. Trxn. not done by you? Report at https://sbicard.com/Dispute", sbiCard)
        assertEquals("SBI Card", t.institution)
        assertEquals("2383", t.accountLast4)
        assertEquals(24000L, t.amount)
        assertEquals(LocalDate.of(2026, 10, 23), t.date)
        val c = TestSupport.categoriser.categorise(t)
        assertEquals("Meesho", c.merchant)
        assertEquals(CategoryIds.SHOPPING, c.categoryId)
    }

    @Test fun `S2 SBI Card payment with western grouping and no card number`() {
        val t = txn("We have received payment of Rs.1,628.12 via BBPS & the same has been credited to your SBI Credit Card. Your available limit is Rs.196,000.00.", sbiCard)
        assertEquals(Direction.CREDIT, t.direction)
        assertEquals(TxnKind.CARD_PAYMENT, t.kind)
        assertEquals(162812L, t.amount)
        assertEquals(rupees(196000), t.availableLimit)
        assertNull(t.accountLast4)
    }

    @Test fun `S3 SBI Card e-mandate is an upcoming debit`() {
        val r = parse("Dear Cardholder, your payment of INR 906.30 at Amazon is due on 29/08/2026 and will be processed through your credit card ending 2383 as per e-Mandate (SiHub ID: XViEwKDt07) registered by you. To opt out of this e-Mandate, please log in to www.sbicard.com/emandates.", sbiCard, LocalDateTime.of(2026, 8, 26, 9, 0))
        assertIs<ParseResult.UpcomingDebit>(r)
        assertEquals(90630L, r.amount)
        assertEquals("Amazon", r.merchant)
        assertEquals("2383", r.accountLast4)
        assertEquals(LocalDate.of(2026, 8, 29), r.dueDate)
    }

    @Test fun `S4 S5 S6 SBI Card promotions, service requests and OTPs are ignored`() {
        listOf(
            "Dear Cardholder, with the Balance Transfer on EMI facility on your credit card, you can avoid high interest rates! Simply transfer your other bank credit card's outstanding to your SBI Credit Card and pay in easy EMIs. Visit https://app.sbicard.com/SBICRD/BTEMI - SBI Card",
            "Dear Cardholder, we have received your request to increase the Credit Limit on your SBI Credit Card XXXXXX2383 (SR No. 1412448575794). It will be processed within 7 to 11 working days as per our internal guidelines.",
            "Dear SBI Cardholder, based on your request vide Service Request Number 1412448575794 for credit limit enhancement of your SBI Card Number XXXX2383 cannot be processed due to internal policy. For more details log onto sbicard.com",
            "OTP to view your card details on SBI Card Mobile App is 567221 for card ending with 7264. OTP is valid for 5 mins only. Do not share with anyone - SBI Card",
            "The verification code to confirm your request for Credit Limit Increase on your SBI Credit Card is 534244. This code is valid for 10 minutes only.",
            "464112 is the OTP to link mobile device with SBI Card App,for card ending XX2383.OTP is valid for 30 mins only.Do not share with anyone.",
            "OTP to view your CKYC Number XX2211 is 113546. It is valid for a single attempt or 5 minutes only. Do not share your OTP with anyone - SBI Card",
            "090965 is your SECRET One Time Password (OTP) on your HDFC BANK Credit Card transaction for Loan on Card/BTEMI/LE/Upgrade/SmartPay/Insurance. Never Share OTP",
        ).forEach { assertIs<ParseResult.Ignored>(parse(it, sbiCard), it) }
    }

    @Test fun `H1 HDFC credit card on UPI, multi-line, year inferred`() {
        val t = txn("Txn Rs.110.00\nOn HDFC Bank Card 0021\nAt *ymkc@ptys \nby UPI 222444231460\nOn 28-10\nNot You?\nCall 18002586161/SMS BLOCK CC 0021 to 7308080808", hdfc)
        assertEquals(AccountType.CREDIT_CARD, t.accountType)
        assertEquals("0021", t.accountLast4)
        assertEquals(11000L, t.amount)
        assertEquals("*ymkc@ptys", t.merchant)
        assertEquals("222444231460", t.reference)
        assertEquals(LocalDate.of(2026, 10, 28), t.date)
    }

    @Test fun `H1 date in January inferred from received year`() {
        val t = txn("Txn Rs.50.00\nOn HDFC Bank Card 0021\nAt Q696637768@ybl \nby UPI 215150402833\nOn 31-12\nNot You?\nCall 18002586161/SMS BLOCK CC 0021 to 7308080808", hdfc, LocalDateTime.of(2027, 1, 1, 10, 0))
        assertEquals(LocalDate.of(2026, 12, 31), t.date)
    }

    @Test fun `H2 HDFC payee NA becomes no merchant`() {
        val t = txn("Txn Rs.210.00\nOn HDFC Bank Card 0021\nAt NA \nby UPI 201248632372\nOn 22-10\nNot You?\nCall 18002586161/SMS BLOCK CC 0021 to 7308080808", hdfc)
        assertNull(t.merchant)
        assertEquals(CategoryIds.OTHERS, TestSupport.categoriser.categorise(t).categoryId)
    }

    @Test fun `H4 HDFC statement without amounts`() {
        val s = parse("Statement Generated:\nFor HDFC Bank Credit Card XX7674.\nView: https://1.hdfc.bank.in/HDFCBK/a/wMhWep\nPay: https://1.hdfc.bank.in/HDFCBK/a/wLUzEK", hdfc)
        assertIs<ParseResult.Statement>(s)
        assertEquals("7674", s.cardLast4)
        assertNull(s.totalDue)
    }

    @Test fun `phishing from a mobile number is never booked`() {
        val r = parse("ICICI Bank Acct XX468 debited for Rs 91.00 on 30-Oct-26; X credited. UPI:120807593798.", "+919876543210")
        assertIs<ParseResult.Ignored>(r)
    }
}
