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
import kotlin.test.assertTrue

/** Spec Section 14, savings / current accounts: verbatim formats from sms-research/01 (F01-F51) and 02. */
class PublicBanksTest {
    // ---- SBI ----
    @Test fun `F01 SBI UPI debit has no currency marker`() {
        val t = txn("Dear UPI user A/C X1001 debited by 305.0 on date 08Oct25 trf to AAHARA CAFE Refno 100000000001 If not u? call-18001234567 for other services-9223766666-SBI", "VM-SBIUPI-S", on("2025-10-08"))
        expect(t, "305.00", DEBIT, acct = "1001", merchant = "AAHARA CAFE", ref = "100000000001", date = "2025-10-08",
            institution = "SBI", accountType = AccountType.SAVINGS, channel = Channel.UPI, rule = "sbi.upi.debit")
        val t2 = txn("Dear UPI user A/C X8912 debited by 120.0 on date 05Jul25 trf to ZEPTO MARKETPLA Refno 519612345678. If not u? call 1800111109. -SBI", "VM-SBIUPI-S", on("2025-07-05"))
        expect(t2, "120", DEBIT, acct = "8912", merchant = "ZEPTO MARKETPLA", ref = "519612345678", date = "2025-07-05")
        assertEquals(CategoryIds.GROCERIES, categoriser.categorise(t2).categoryId)
    }

    @Test fun `F02 SBI UPI credit with hyphen glued to account`() {
        val t = txn("Dear SBI User, your A/c X1003-credited by Rs.90 on 28Apr26 transfer from HILLSIDE DINER Ref No 100000000011 -SBI", "VM-SBIUPI-S", on("2026-04-28"))
        expect(t, "90", CREDIT, acct = "1003", merchant = "HILLSIDE DINER", ref = "100000000011", date = "2026-04-28", channel = Channel.UPI)
    }

    @Test fun `F03 SBI IMPS credit`() {
        val t = txn("Dear Customer, Your a/c no. XXXXXXXX4338 is credited by Rs.2.00 on 17-04-26 by a/c linked to mobile 1XXXXXX111-EXAMPLE RIDES (IMPS Ref# 100000000010)-SBI", "AD-SBIBNK-S", on("2026-04-17"))
        expect(t, "2.00", CREDIT, acct = "4338", merchant = "EXAMPLE RIDES", ref = "100000000010", date = "2026-04-17", channel = Channel.IMPS)
    }

    @Test fun `F04 SBI IMPS debit to another account with dashed ref`() {
        val t = txn("Dear Customer, Your a/c no. XXXXXXXX5045 is debited for Rs.500.00 on 31-03-26 and a/c XXXXXXX418 credited (IMPS Ref no ---------------). -SBI", "VA-SBIPSG-T", on("2026-03-31"))
        expect(t, "500.00", DEBIT, acct = "5045", merchant = "a/c XXXXXXX418", ref = null, date = "2026-03-31", institution = "SBI", channel = Channel.IMPS)
    }

    @Test fun `F05 SBI CBS debit by transfer`() {
        val t = txn("Dear Customer, Your A/C XXXXX901234 has a debit by transfer of Rs 230.00 on 18/09/25. Avl Bal Rs 6,500.00.-SBI", "AD-CBSSBI-S", on("2025-09-18"))
        expect(t, "230.00", DEBIT, acct = "901234", date = "2025-09-18", balance = "6,500.00", rule = "sbi.cbs.debit")
    }

    @Test fun `F06 SBI salary, cheque and narration credits with lakh grouping`() {
        val sal = txn("Your A/C XXXXX314567 has credit for BY SALARY of Rs 4,000.00 on 31/12/22. Avl Bal Rs 17,70,200.00.-SBI", "AD-CBSSBI-S", on("2022-12-31"))
        expect(sal, "4,000.00", CREDIT, acct = "314567", merchant = "BY SALARY", date = "2022-12-31", balance = "17,70,200.00", kind = TxnKind.SALARY)
        assertEquals(CategoryIds.SALARY, categoriser.categorise(sal).categoryId)
        val chq = txn("Dear Customer, Your A/C XXXXX314567 has a credit by Cheque of Rs 12,07,000.00 on 07/10/22. Avl Bal Rs 18,06,500.00.-SBI", "AD-CBSSBI-S", on("2022-10-07"))
        expect(chq, "12,07,000.00", CREDIT, merchant = "Cheque", date = "2022-10-07", balance = "18,06,500.00")
        val other = txn("Your A/C XXXXX314502 has credit for AOFS23546782123411BHPL of Rs 10,700.00 on 02/05/22. Avl Bal Rs 13,50,000.00.-SBI", "AD-CBSSBI-S", on("2022-05-02"))
        expect(other, "10,700.00", CREDIT, acct = "314502", merchant = "AOFS23546782123411BHPL", date = "2022-05-02", balance = "13,50,000.00")
    }

    @Test fun `F07 SBI cash deposit is a transfer`() {
        val t = txn("Your A/C XXXXX100002 Credited INR 31,700.00 on 28/01/26 -Deposited by Cash by SELF. Avl Bal INR 34,302.79-SBI", "AD-CBSSBI-S", on("2026-01-28"))
        expect(t, "31,700.00", CREDIT, acct = "100002", date = "2026-01-28", balance = "34,302.79", kind = TxnKind.CASH_DEPOSIT)
        assertEquals(CategoryIds.TRANSFERS, categoriser.categorise(t).categoryId)
    }

    @Test fun `F08 SBI ATM withdrawal reversal is a refund`() {
        val t = txn("Your AC XXXXX314567 Credited INR 9,000.00 on 22/05/22 -REVERSE ATM WDL. Avl Bal INR 13,08,900.00.-SBI", "AD-CBSSBI", on("2022-05-22"))
        expect(t, "9,000.00", CREDIT, acct = "314567", merchant = "REVERSE ATM WDL", date = "2022-05-22", balance = "13,08,900.00", kind = TxnKind.REFUND)
    }

    @Test fun `F09 SBI UPI reversal keeps the original RRN`() {
        val t = txn("Your A/C XXXXX100002 has credit for UPI/REVERSAL/100000000005/0112 of Rs 2.00 on 03/12/25. Avl Bal Rs 20,407.29.-SBI", "AD-CBSSBI-S", on("2025-12-03"))
        expect(t, "2.00", CREDIT, acct = "100002", ref = "100000000005", date = "2025-12-03", balance = "20,407.29", kind = TxnKind.REFUND, channel = Channel.UPI)
    }

    @Test fun `F10 SBI debit card POS`() {
        val t = txn("Dear Customer, transaction number 1234 for Rs.383.00 by SBI Debit Card 0000 done at merchant on 13Sep25 at 21:38:26. Your updated available balance is Rs.999999999. If not done by you, forward this SMS to 7400165218/ call 1800111109/9449112211 to block card. GOI helpline for cyber fraud 1930.", "AD-ATMSBI-S", on("2025-09-13"))
        expect(t, "383.00", DEBIT, acct = "0000", merchant = "merchant", date = "2025-09-13", balance = "999999999",
            accountType = AccountType.SAVINGS, channel = Channel.CARD)
    }

    @Test fun `F11 SBI short debit`() {
        val t = txn("Rs.500 debited from A/c X1234 on 13Sep25. Avl Bal Rs.999999999", "AD-ATMSBI-S", on("2025-09-13"))
        expect(t, "500", DEBIT, acct = "1234", merchant = null, date = "2025-09-13", balance = "999999999")
    }

    // ---- HDFC Bank savings ----
    private val hdfc = "AD-HDFCBK-S"

    @Test fun `F13 HDFC multi-line and single-line UPI sent`() {
        val t = txn("Sent Rs.750.00\nFrom HDFC Bank A/C *1001\nTo AAHARA CAFE\nOn 02/10/25\nRef 100000000001\nNot You?\nCall 18001234567/SMS BLOCK UPI to 7308080808", hdfc, on("2025-10-02"))
        expect(t, "750.00", DEBIT, acct = "1001", merchant = "AAHARA CAFE", ref = "100000000001", date = "2025-10-02",
            accountType = AccountType.SAVINGS, channel = Channel.UPI, rule = "hdfc.sb.upi.sent")
        val t2 = txn("Sent Rs.15000.00 From HDFC Bank A/C *1234 To TEST MERCHANT PVT LTD On 01/01/26 Ref 567890567890 Not You? Call 18005556789/SMS BLOCK UPI to 7305556789", hdfc, on("2026-01-01"))
        expect(t2, "15000.00", DEBIT, acct = "1234", merchant = "TEST MERCHANT PVT LTD", ref = "567890567890", date = "2026-01-01")
    }

    @Test fun `F14 HDFC older UPI debit to VPA and to account`() {
        val t = txn("Rs.500.00 debited from A/c XX1234 on 20-Oct-25 to merchant@upi (UPI Ref No 123456789012)", hdfc, on("2025-10-20"))
        expect(t, "500.00", DEBIT, acct = "1234", merchant = "merchant@upi", ref = "123456789012", date = "2025-10-20")
        val t2 = txn("HDFC Bank:Rs. 14000.00 debited from a/c *1001 on 01/02/26 to a/c **1004 (UPI Ref No. 100000000369). Not you? Call on 18001234567", hdfc, on("2026-02-01"))
        expect(t2, "14000.00", DEBIT, acct = "1001", merchant = "a/c **1004", ref = "100000000369", date = "2026-02-01")
    }

    @Test fun `F15 HDFC UPI credit alert`() {
        val t = txn("Credit Alert!\nRs.1,150.00 credited to HDFC Bank A/c XX1001 on 04-10-25 from VPA payee2@axl (UPI 100000000003)", hdfc, on("2025-10-04"))
        expect(t, "1,150.00", CREDIT, acct = "1001", merchant = "payee2@axl", ref = "100000000003", date = "2025-10-04", channel = Channel.UPI)
    }

    @Test fun `F16 HDFC IMPS received`() {
        val t = txn("Received!\nINR 5,000.00 in HDFC Bank A/c xx1001\nOn 17-10-25\nFor IMPS -EXAMPLE PAYMENTS LTD- 100000000034\nAvl bal INR 45,549.08", hdfc, on("2025-10-17"))
        expect(t, "5,000.00", CREDIT, acct = "1001", merchant = "EXAMPLE PAYMENTS LTD", ref = "100000000034", date = "2025-10-17", balance = "45,549.08", channel = Channel.IMPS)
    }

    @Test fun `F17 HDFC IMPS sent`() {
        val t = txn("IMPS INR 10,000.00\nsent from HDFC Bank A/c XX1001 on 10-09-26\nTo A/c xxxxxxxxxx1007\nRef-100000001037\nNot you?Call 18001234567/SMS BLOCK OB to 7308080808", hdfc, on("2026-09-10"))
        expect(t, "10,000.00", DEBIT, acct = "1001", merchant = "A/c xxxxxxxxxx1007", ref = "100000001037", date = "2026-09-10", channel = Channel.IMPS)
    }

    @Test fun `F18 HDFC NEFT payroll credit is salary`() {
        val t = txn("Update! INR 50,000.00 deposited in HDFC Bank A/c XX1001 on 02-SEP-26 for NEFT Cr-EXBK0001234-EXAMPLE PAYROLL SERVICES-Account Holder-EXBK0000000000001.Avl bal INR 99,999.99. Cheque deposits in A/C are subject to clearing.", hdfc, on("2026-09-02"))
        expect(t, "50,000.00", CREDIT, acct = "1001", merchant = "EXAMPLE PAYROLL SERVICES", ref = "EXBK0000000000001", date = "2026-09-02",
            balance = "99,999.99", kind = TxnKind.SALARY, channel = Channel.NEFT)
    }

    @Test fun `F19 HDFC interest credit`() {
        val t = txn("Update! INR 6,657.00 deposited in HDFC Bank A/c XX1111 on 30-SEP-26 for Interest paid till 30-SEP-2026.Avl bal INR 12,28,554.72. Cheque deposits in A/C are subject to clearing", "JM-HDFCBK-S", on("2026-09-30"))
        expect(t, "6,657.00", CREDIT, acct = "1111", date = "2026-09-30", balance = "12,28,554.72", kind = TxnKind.INTEREST)
        assertEquals(CategoryIds.INTEREST, categoriser.categorise(t).categoryId)
    }

    @Test fun `F20 HDFC UPDATE debit with Info, and NetBanking payment`() {
        val t = txn("UPDATE: INR 12,990.00 debited from HDFC Bank XX1001 on 18-DEC-25. Info: EXAMPLEAMC/1000000000000221-XXXXXXXXXX8730. Avl bal:INR 99,999.99", hdfc, on("2025-12-18"))
        expect(t, "12,990.00", DEBIT, acct = "1001", merchant = "EXAMPLEAMC/1000000000000221-XXXXXXXXXX8730", date = "2025-12-18", balance = "99,999.99")
        val nb = txn("Payment Successful! Rs. 12345.67 from A/c ****4321 to ACMEPAYMENTS via HDFC Bank NetBanking. Not you?Call 18002586161", hdfc)
        expect(nb, "12345.67", DEBIT, acct = "4321", merchant = "ACMEPAYMENTS", date = "2026-10-30")
    }

    @Test fun `F21 HDFC debit card spend takes card digits from the BLOCK DC tail`() {
        val t = txn("Spent Rs.2 From DAILY FRESH MART On 2025-12-16:09:40:54 Bal Rs.95853.26 Not You? Call 18001234567/SMS BLOCK DC 3271 to 7308080808", hdfc, on("2025-12-16"))
        expect(t, "2", DEBIT, acct = "3271", merchant = "DAILY FRESH MART", date = "2025-12-16", balance = "95853.26",
            accountType = AccountType.SAVINGS, channel = Channel.CARD)
    }

    @Test fun `F22 HDFC ATM withdrawal`() {
        val t = txn("Withdrawn Rs.800 From HDFC Bank Card x1002 At EXAMPLE ATM NAGAR On 2025-10-24:21:06:54 Bal Rs.40043.08 Not You? Call 18001234567/SMS BLOCK DC 3271 to 7308080808", hdfc, on("2025-10-24"))
        expect(t, "800", DEBIT, acct = "1002", merchant = "EXAMPLE ATM NAGAR", date = "2025-10-24", balance = "40043.08",
            accountType = AccountType.SAVINGS, kind = TxnKind.CASH_WITHDRAWAL, channel = Channel.ATM)
        assertEquals(CategoryIds.CASH, categoriser.categorise(t).categoryId)
    }

    @Test fun `F23 HDFC SmartEMI debit is EMI`() {
        val t = txn("Rs.3282.00 debited from HDFC Bank A/c XX1111 on 05-10-26 towards SmartEMI. Avl bal Rs.5000.00", hdfc, on("2026-10-05"))
        expect(t, "3282.00", DEBIT, acct = "1111", merchant = "SmartEMI", date = "2026-10-05", balance = "5000.00", kind = TxnKind.EMI)
        assertEquals(CategoryIds.RENT_EMI, categoriser.categorise(t).categoryId)
    }

    @Test fun `HDFC debit mandate with ordinal date`() {
        val t = txn("Debit Alert! Your HDFC Bank A/c xxxxxx has been debited Rs 1117 on 06th Oct' 2026 via Debit Mandate for RAZORPAY_XXXXXX_XXXXX", hdfc, on("2026-10-06"))
        expect(t, "1117", DEBIT, acct = null, merchant = "RAZORPAY_XXXXXX_XXXXX", date = "2026-10-06", channel = Channel.MANDATE)
    }

    // ---- Axis ----
    @Test fun `F24 Axis debit card POS, masked account falls back to BLOCKCARD digits`() {
        val t = txn("INR 209.00 debited from A/c no. XXxxxxy on BURGRILL 04-12-2025 13:13:27 IST. Avl bal: INR xxxxxxx. Not you? SMS BLOCKCARD XX0023 to +919951860002 - Axis Bank", "JD-AXISBK-S", on("2025-12-04"))
        expect(t, "209.00", DEBIT, acct = "0023", merchant = "BURGRILL", date = "2025-12-04", balance = null, channel = Channel.CARD, institution = "Axis Bank")
        val atm = txn("INR 2000.00 debited from A/c no. XX589034 on AXIS BANK L 04-11-2025 16:06:39 IST. Avl bal: INR 98919.81. Not you? SMS BLOCKCARD XX0192 to +919951860002 - Axis Bank", "JD-AXISBK-S", on("2025-11-04"))
        expect(atm, "2000.00", DEBIT, acct = "589034", date = "2025-11-04", balance = "98919.81", kind = TxnKind.CASH_WITHDRAWAL, channel = Channel.ATM)
    }

    @Test fun `F26 Axis EMI auto-debit`() {
        val t = txn("INR 5,000.00 debited from A/c XX1111 for EMI due on 05-10-26. Avl Bal INR 10,000.00", "AX-AXISBK-S", on("2026-10-05"))
        expect(t, "5,000.00", DEBIT, acct = "1111", date = "2026-10-05", balance = "10,000.00", kind = TxnKind.EMI)
    }

    // ---- Kotak ----
    @Test fun `F27 Kotak classic UPI sent to a QR VPA`() {
        val t = txn("Sent Rs.15.00 from Kotak Bank AC X1234 to paytmqr288005050101t74afkchmxjd@paytm on 14-10-25.UPI Ref 1234567890. Not you, https://kotak.com/KBANKT/Fraud", "JD-KOTAKB-S", on("2025-10-14"))
        expect(t, "15.00", DEBIT, acct = "1234", merchant = "paytmqr288005050101t74afkchmxjd@paytm", ref = "1234567890", date = "2025-10-14", channel = Channel.UPI)
    }

    @Test fun `F28 Kotak 2026 UPI sent from KOTAKD`() {
        val t = txn("Sent Rs.51.00 from XXXXXX9722 to DINESHBHAI RAMJIBHAI on 30/04/2026. UPI ref no. 648604626824. Not you? Tap https://kotk.in/KOTAKD/Eg9Cu0 to report -Kotak", "VM-KOTAKD-S", on("2026-04-30"))
        expect(t, "51.00", DEBIT, acct = "9722", merchant = "DINESHBHAI RAMJIBHAI", ref = "648604626824", date = "2026-04-30", institution = "Kotak Bank")
        assertTrue(categoriser.categorise(t).personToPerson)
    }

    @Test fun `F29 Kotak UPI received`() {
        val t = txn("Received Rs.250.00 in your Kotak Bank AC X3333 from john.doe@oksbi on 14-10-25.UPI Ref 2222222222. Not you, https://kotak.com/KBANKT/Fraud", "JD-KOTAKB-S", on("2025-10-14"))
        expect(t, "250.00", CREDIT, acct = "3333", merchant = "john.doe@oksbi", ref = "2222222222", date = "2025-10-14")
    }

    @Test fun `F30 Kotak IMPS received`() {
        val t = txn("Received Rs. 329.00 on 15-04-26 in your Kotak Bank A/C x2451 by an A/C linked to mobile x111. IMPS Ref no 610511412340.", "JD-KOTAKB-S", on("2026-04-15"))
        expect(t, "329.00", CREDIT, acct = "2451", merchant = "A/C linked to mobile x111", ref = "610511412340", date = "2026-04-15", channel = Channel.IMPS)
    }

    @Test fun `F31 Kotak NEFT credit`() {
        val t = txn("Rs. 5000 credited to your Kotak Bank a/c XX1111 via NEFT from beneficiary John Doe. UTR Ref. HDFCH00000000000", "VM-KOTAKB-S")
        expect(t, "5000", CREDIT, acct = "1111", merchant = "John Doe", ref = "HDFCH00000000000", channel = Channel.NEFT)
    }

    @Test fun `F32 Kotak generic debit and cashback credit`() {
        val t = txn("Rs.1000.00 debited from your Kotak Bank AC X4444 on 15-10-25. Avl Bal Rs.10000.00", "JD-KOTAKB-S", on("2025-10-15"))
        expect(t, "1000.00", DEBIT, acct = "4444", merchant = null, date = "2025-10-15", balance = "10000.00")
        val cb = txn("Cashback of Rs.50.00 has been sent to your Kotak Bank A/c x5555. Credited on 14-10-25. UPI Ref 9999999999", "JD-KOTAKB-S", on("2025-10-14"))
        expect(cb, "50.00", CREDIT, acct = "5555", ref = "9999999999", date = "2025-10-14", kind = TxnKind.REFUND)
        assertEquals(CategoryIds.REFUNDS, categoriser.categorise(cb).categoryId)
    }

    // ---- Bank of Baroda ----
    private val bob = "VM-BOBTXN-S"

    @Test fun `F33 BoB transfer to loan recovery and short Dr`() {
        val t = txn("Rs.29 transferred from A/c ...5494 to:Loan Recovery Fo. Total Bal:Rs.24898.57CR. Avlbl Amt:Rs.24898.57(04-11-2025 04:03:09) - Bank of Baroda", bob, on("2025-11-04"))
        expect(t, "29", DEBIT, acct = "5494", merchant = "Loan Recovery Fo", date = "2025-11-04", balance = "24898.57", kind = TxnKind.EMI)
        val dr = txn("Rs.80.00 Dr. from A/c XX123456 on 12-11-2024. AvlBal:Rs1234.56cx. Ref:52211012345 -Bank of Baroda", bob, on("2024-11-12"))
        expect(dr, "80.00", DEBIT, acct = "123456", ref = "52211012345", date = "2024-11-12", balance = "1234.56")
    }

    @Test fun `F34 BoB credit with VPA right after Cr to`() {
        val t = txn("Rs.500.00 Cr. to redacted@ybl A/c XX789012 on 15-11-2024. AvlBal:Rs5678.90. Ref:987654321 -Bank of Baroda", bob, on("2024-11-15"))
        expect(t, "500.00", CREDIT, acct = "789012", merchant = "redacted@ybl", ref = "987654321", date = "2024-11-15", balance = "5678.90")
    }

    @Test fun `F35 BoB IMPS credit, ACH dividend with double dot, cash deposit`() {
        val imps = txn("Rs.2500.00 credited to A/c XX456789 via IMPS/518233445566 by JOHN DOE. AvlBal:Rs25000.00 -Bank of Baroda", bob)
        expect(imps, "2500.00", CREDIT, acct = "456789", merchant = "JOHN DOE", ref = "518233445566", balance = "25000.00", channel = Channel.IMPS)
        val ach = txn("Rs..6 Credited to A/c ...4444 from:ACHCR/JIO FINANC. Total Bal:Rs.22976.31CR. Avlbl Amt:Rs.22976.31(26-08-2026 18:20:36) - Bank of Baroda", bob, on("2026-08-26"))
        expect(ach, "6", CREDIT, acct = "4444", merchant = "JIO FINANC", date = "2026-08-26", balance = "22976.31", kind = TxnKind.DIVIDEND)
        assertEquals(CategoryIds.DIVIDENDS, categoriser.categorise(ach).categoryId)
        val cash = txn("Rs.10000.00 deposited in cash to A/c XX234567 on 20-11-2024. AvlBal:Rs45000.00 -Bank of Baroda", bob, on("2024-11-20"))
        expect(cash, "10000.00", CREDIT, acct = "234567", date = "2024-11-20", balance = "45000.00", kind = TxnKind.CASH_DEPOSIT)
    }

    // ---- PNB ----
    private val pnb = "JD-PNBSMS-S"

    @Test fun `F36 PNB UPI debit and credits`() {
        val t = txn("A/c X0000 debited INR 125.00 Dt 01-01-26 12:00:00 to Swiggy thru UPI:000000000001..Bal INR 9000.00-PNB", pnb, on("2026-01-01"))
        expect(t, "125.00", DEBIT, acct = "0000", merchant = "Swiggy", ref = "000000000001", date = "2026-01-01", balance = "9000.00", channel = Channel.UPI)
        assertEquals(CategoryIds.FOOD, categoriser.categorise(t).categoryId)
        val c1 = txn("Ac XX7582 Credited with Rs.4750.00 11-07-2025 08:53:21 thru UPI . Aval Bal Rs.82431.67 CR. (UPI Ref ID:714628305917) Helpline 18001800/18002021-PNB", pnb, on("2025-07-11"))
        expect(c1, "4750.00", CREDIT, acct = "7582", ref = "714628305917", date = "2025-07-11", balance = "82431.67")
        val c2 = txn("A/c X0000 credited INR 125.00 From Example Shop Ltd/UPI:000000000003. Bal INR 9000.00-PNB", pnb)
        expect(c2, "125.00", CREDIT, acct = "0000", merchant = "Example Shop Ltd", ref = "000000000003", balance = "9000.00")
    }

    @Test fun `F37 PNB IMPS debit to another account`() {
        val t = txn("Your a/c no XX847 is debited for Rs 3200 on 11-07-25 03:14:52 and a/c XX291 credited (IMPS Ref no 712845931206) .If not done by you, pl. forward this SMS from registered mobile to 9264092640 to report unauthorized txn & block IBS/MBS. Download PNB ONE.-PNB", pnb, on("2025-07-11"))
        expect(t, "3200", DEBIT, acct = "847", merchant = "a/c XX291", ref = "712845931206", date = "2025-07-11", institution = "PNB", channel = Channel.IMPS)
    }

    @Test fun `F38 PNB ATM withdrawals and debit card POS`() {
        val atm = txn("A/c XX1234 debited with Rs.5000.00,21-11-2025 13:23:22 thru card XX9239  . Out of 5 free txn on PNB ATM, you utilized 1 txn. Chrgs applicable as per policy. Bal 27000.00 CR. If not done, fwd SMS to 9264192641 to block card/call 18001800/18002021-PNB", pnb, on("2025-11-21"))
        expect(atm, "5000.00", DEBIT, acct = "1234", date = "2025-11-21", balance = "27000.00", kind = TxnKind.CASH_WITHDRAWAL, channel = Channel.ATM)
        val pos = txn("Ac XX0000 Debited by INR 125.00 thru Debitcard XXXX0000 at Example Shop on 01-01-2026. Aval Bal INR 9000.00-PNB", pnb, on("2026-01-01"))
        expect(pos, "125.00", DEBIT, acct = "0000", merchant = "Example Shop", date = "2026-01-01", balance = "9000.00", channel = Channel.CARD, accountType = AccountType.SAVINGS)
        val other = txn("Ac XX0000 debited with Rs.25.00,01-01-2026 12:00:00 through Debitcard XXXX0000 on other bank ATM. Balance Rs.9000.00-PNB", pnb, on("2026-01-01"))
        expect(other, "25.00", DEBIT, acct = "0000", date = "2026-01-01", balance = "9000.00", kind = TxnKind.CASH_WITHDRAWAL)
    }

    @Test fun `F39 PNB generic debit`() {
        val t = txn("Ac XX1234 Debited with Rs.5000.00, 20-02-2026 07:47:16. Aval Bal Rs.27000.00 CR. Helpline 18001800/18002021-PNB", pnb, on("2026-02-20"))
        expect(t, "5000.00", DEBIT, acct = "1234", merchant = null, date = "2026-02-20", balance = "27000.00")
    }

    // ---- Canara, Union, IDFC FIRST ----
    @Test fun `F40 Canara UPI debit with Dr abbreviation`() {
        val t = txn("Dear Customer, Acct XXX123 Dr. INR 260.00 on 06/07/26 to SAMPLE MART; UPI: 123456789012; Bal INR 12,345.67.Not you? SMS BLOCKUPI to XXXXXXXXXX-CanaraBank", "AD-CANBNK-S", on("2026-07-06"))
        expect(t, "260.00", DEBIT, acct = "123", merchant = "SAMPLE MART", ref = "123456789012", date = "2026-07-06", balance = "12,345.67", channel = Channel.UPI)
    }

    @Test fun `F41 Canara RTGS mutual fund redemption credit`() {
        val t = txn("An amount of INR 13,30,614.75 has been credited to XXXX6785 on 02/12/2025 towards RTGS by Sender AXIS MUTUAL FUND REDEMPTION PO, IFSC UTIB0000004, Sender A/c XXXX9108, AXIS BANK, MUMBAI BRANCH, UTR UTIBR72025120200011461, Total Avail. Bal INR 2679815.88- Canara Bank", "VA-CANBNK-S", on("2025-12-02"))
        expect(t, "13,30,614.75", CREDIT, acct = "6785", merchant = "AXIS MUTUAL FUND REDEMPTION PO", ref = "UTIBR72025120200011461",
            date = "2025-12-02", balance = "2679815.88", kind = TxnKind.MF_REDEMPTION)
        assertEquals(CategoryIds.INVESTMENTS, categoriser.categorise(t).categoryId)
    }

    @Test fun `F42 Union Bank debit with Rs colon (generic fallback)`() {
        val t = txn("A/c *1234 Debited for Rs:100.00 on 11-08-2025 18:28:02 by Mob Bk ref no 123456789000 Avl Bal Rs:12345.67", "AD-UNIONB-S", on("2025-08-11"))
        expect(t, "100.00", DEBIT, acct = "1234", ref = "123456789000", date = "2025-08-11", balance = "12345.67", institution = "Union Bank")
    }

    @Test fun `F43 IDFC FIRST debits (generic fallback)`() {
        val t = txn("Your A/C XXXXXXX1234 is debited by INR 68.00 on 06/08/25 17:36. New Bal :INR 5000.00", "AD-IDFCBK-S", on("2025-08-06"))
        expect(t, "68.00", DEBIT, acct = "1234", date = "2025-08-06", balance = "5000.00")
        val p2m = txn("Your A/c XX4614 debited by Rs. 1,172.06 on 15/01/26; REDBUS credited. RRN 060649915527. Available balance Rs. 9,134.15. Team IDFC FIRST Bank", "AD-IDFCBK-S", on("2026-01-15"))
        expect(p2m, "1,172.06", DEBIT, acct = "4614", merchant = "REDBUS", ref = "060649915527", date = "2026-01-15", balance = "9,134.15")
        assertEquals(CategoryIds.TRAVEL, categoriser.categorise(p2m).categoryId)
    }

    @Test fun `F44 IDFC FIRST monthly interest with INR dot`() {
        val t = txn("Monthly interest of INR.3,738.00 earned on your Savings A/c XX3344 has been credited to your A/C on 30/04/26. New bal: INR.9,27,610.90. IDFC FIRST Bank", "AD-IDFCBK-S", on("2026-04-30"))
        expect(t, "3,738.00", CREDIT, acct = "3344", merchant = null, date = "2026-04-30", balance = "9,27,610.90", kind = TxnKind.INTEREST)
    }

    @Test fun `savings interest credits from IDFC and Bandhan (generic fallback)`() {
        val idfc = txn("Your A/C XXXXXXX1234 is credited by INR 125.50 on 01/01/25 for monthly interest. New Bal :INR 15125.50", "BM-IDFCBK-S", on("2025-01-01"))
        expect(idfc, "125.50", CREDIT, acct = "1234", date = "2025-01-01", balance = "15125.50", kind = TxnKind.INTEREST)
        val bandhan = txn("Dear Customer, your account XXXXXXXXXX1234 is credited with INR 3.00 on 01-OCT-2025 towards interest. Bandhan Bank", "XY-BDNSMS-S", on("2025-10-01"))
        expect(bandhan, "3.00", CREDIT, acct = "1234", date = "2025-10-01", kind = TxnKind.INTEREST)
    }

    // ---- IndusInd ----
    @Test fun `F45 IndusInd UPI debit and credit`() {
        val d = txn("A/c *XX1234 debited by Rs 1234.00 towards xxxx.yyyy@icici. RRN: 510048508040. Not You? call 18602677777- IndusInd Bank.", "AD-INDUSB-S")
        expect(d, "1234.00", DEBIT, acct = "1234", merchant = "xxxx.yyyy@icici", ref = "510048508040", channel = Channel.UPI)
        val c = txn("A/C *XX1234 credited by Rs 25000.00 from xxxx.yyyy@ybl. RRN:510048508040. Avl Bal:105502.12. Not you? Call 18602677777 - IndusInd bank.", "AD-INDUSB-S")
        expect(c, "25000.00", CREDIT, acct = "1234", merchant = "xxxx.yyyy@ybl", ref = "510048508040", balance = "105502.12")
    }

    @Test fun `F46 IndusInd IMPS, ACH to Groww, debit card purchase`() {
        val imps = txn("Your IndusInd Account 20XXXXX1234 has been debited for INR 6440 towards IMPS/12345678901. Call 18602677777 to report issue-IndusInd Bank.", "AD-INDUSB-S")
        expect(imps, "6440", DEBIT, acct = "1234", ref = "12345678901", channel = Channel.IMPS)
        val ach = txn("IndusInd A/C  Debited; INR 4,500.00 Ref-ACH DR INW PAY/0000WD2CEFDT2Z58B2202320321456/Grow.Bal INR 141,999.93.Dispute-Call 18602677777-IndusInd Bank.", "AD-INDUSIND-S")
        expect(ach, "4,500.00", DEBIT, merchant = "Grow", ref = "0000WD2CEFDT2Z58B2202320321456", balance = "141,999.93", channel = Channel.MANDATE)
        val c = categoriser.categorise(ach)
        assertEquals("Groww", c.merchant)
        assertEquals(CategoryIds.INVESTMENTS, c.categoryId)
        val pos = txn("INR 1,101.53 debited from your A/C 201***123456 towards Debit Card Purchase. Avl BAL INR 400.20 - Not you? Call 18602677777 to report issue - IndusInd Bank.", "AD-INDUSB-S")
        expect(pos, "1,101.53", DEBIT, acct = "123456", balance = "400.20", channel = Channel.CARD)
    }

    @Test fun `IndusInd FD interest is a deposit snapshot, not income`() {
        listOf(
            "Net interest INR 248.07 paid on your IndusInd Deposit No 300***123456 on 17/09/25. Call 18602677777 for assistance - IndusInd Bank" to "123456",
            "Net interest INR 96.61 paid on your IndusInd Deposit No 371***060020 on 30/06/25. Call 18602677777 for assistance - IndusInd Bank" to "060020",
        ).forEach { (body, acct) ->
            val s = parse(body, "AD-INDUSIND-S", on("2025-09-17"))
            assertIs<ParseResult.Snapshot>(s, body)
            assertEquals(AccountType.DEPOSIT, s.accountType)
            assertEquals(acct, s.accountLast4)
            assertTrue(s.note!!.startsWith("Net interest Rs "))
        }
    }

    // ---- AU, Federal ----
    @Test fun `F47 AU multi-line UPI debit, credit and older debit`() {
        val d = txn("Dr INR 10.00 - AU A/c X4541 12-MAY-2026\nUPI/DR/424941009000/ALADDIN SHAWARMA/Y\nBal INR 18,838.10\nFraud? Call 180012001200/SMS BLOCK UPI to 5676767", "AD-AUBANK-S", on("2026-05-12"))
        expect(d, "10.00", DEBIT, acct = "4541", merchant = "ALADDIN SHAWARMA", ref = "424941009000", date = "2026-05-12", balance = "18,838.10")
        val c = txn("Cr INR 5,000.00 to A/c X4541 12-MAY-2026\nUPI/CR/424941009999/JANE DOE/Y\nBal INR 23,838.10\n-AU Bank", "AD-AUBANK-S", on("2026-05-12"))
        expect(c, "5,000.00", CREDIT, acct = "4541", merchant = "JANE DOE", ref = "424941009999", balance = "23,838.10")
        val old = txn("Debited INR 165.00 from A/c X7013 on 01-MAR-2026\nUPI/DR/651122781360/UK FOOD/UTIB/9180201\nBal INR 900000000\nNot you? Call 180012001200 & dial 0\n-AU Bank", "AD-AUBANK-S", on("2026-03-01"))
        expect(old, "165.00", DEBIT, acct = "7013", merchant = "UK FOOD", ref = "651122781360", date = "2026-03-01", balance = "900000000")
    }

    @Test fun `F48 Federal UPI debit to VPA`() {
        val t = txn("Rs 150.00 debited via UPI on 15-08-2024 10:30:25 to VPA john.doe123@okbank.Ref No 987654321098.Small txns?Use UPI Lite!-Federal Bank", "AD-FEDBNK-S", on("2024-08-15"))
        expect(t, "150.00", DEBIT, merchant = "john.doe123@okbank", ref = "987654321098", date = "2024-08-15", channel = Channel.UPI)
    }

    @Test fun `F49 Federal IMPS credit, Woohoo credit, NEFT debit, ATM, debit card`() {
        val f = "AD-FEDBNK-S"
        expect(txn("Rs 3500.50 credited to your A/c XX4567 via IMPS on 18AUG2024 11:45:30 IMPS Ref no 654321098765 Bal:Rs 25000.75 -Federal Bank", f, on("2024-08-18")),
            "3500.50", CREDIT, acct = "4567", ref = "654321098765", date = "2024-08-18", balance = "25000.75")
        expect(txn("John, you've received INR 10,509.09 in your Account XXXXXXXX1896. Woohoo! It was sent by TESTUSER on March 19, 2025. -Federal Bank", f, on("2025-03-19")),
            "10,509.09", CREDIT, acct = "1896", merchant = "TESTUSER", date = "2025-03-19")
        expect(txn("Debited Rs 6000 from a/c XX3343 on 24JUN2026 21:35 via NEFT to Jerry.Ref FDRLM4175007432.Bal Rs 76.82.Not you?Call 18004251199 -Federal Bank", f, on("2026-06-24")),
            "6000", DEBIT, acct = "3343", merchant = "Jerry", ref = "FDRLM4175007432", date = "2026-06-24", balance = "76.82", channel = Channel.NEFT)
        expect(txn("Rs 1500 withdrawn@ YBL CHAN on 21JAN26 17:59 Bal Rs 7517.94 Ref 602117126490. Not you? Call 18004251199/ SMS NO 3683 to 9895088888 -Federal Bank", f, on("2026-01-21")),
            "1500", DEBIT, merchant = "YBL CHAN", ref = "602117126490", date = "2026-01-21", balance = "7517.94", kind = TxnKind.CASH_WITHDRAWAL)
        expect(txn("Rs 1500.00 debited via card XX**3456 at MERCHANT on 14-05-2025 15:30:45. Current Bal: Rs.250.75 -Federal Bank", f, on("2025-05-14")),
            "1500.00", DEBIT, acct = "3456", merchant = "MERCHANT", date = "2025-05-14", balance = "250.75", accountType = AccountType.SAVINGS)
    }

    @Test fun `F50 Federal debit card e-mandate processed (Netflix and insurance)`() {
        val t = txn("Hi, payment of INR 199.00 for Netflix via e-mandate ID: NX789XYZABC on Federal Bank Debit Card 3456 is processed successfully. To manage, visit: https://www.sihub.in/managesi/federal T&CA - Federal Bank", "AD-FEDBNK-S")
        expect(t, "199.00", DEBIT, acct = "3456", merchant = "Netflix", ref = "NX789XYZABC", channel = Channel.MANDATE)
        assertEquals(CategoryIds.ENTERTAINMENT, categoriser.categorise(t).categoryId)
        val ins = txn("Hi, payment of INR 2500.00 for LifeInsurance via e-mandate ID: LI789GHI456 on Federal Bank Debit Card 1234 is processed successfully. To manage, visit: https://www.sihub.in/managesi/federal T&CA - Federal Bank", "AD-FEDBNK")
        expect(ins, "2500.00", DEBIT, acct = "1234", merchant = "LifeInsurance", ref = "LI789GHI456")
    }

    // ---- F51: BOI, Indian Bank, Central Bank, Bandhan ----
    @Test fun `F51 BOI UPI debit and NEFT mutual fund credit`() {
        val d = txn("Rs.200.00 debited A/cXX5468 and credited to SAI MISAL via UPI Ref No 315439383341 on 23Aug25. Call 18001031906, if not done by you. -BOI", "JX-BOIIND-S", on("2025-08-23"))
        expect(d, "200.00", DEBIT, acct = "5468", merchant = "SAI MISAL", ref = "315439383341", date = "2025-08-23", institution = "Bank of India")
        val c = txn("BOI - Rs 15,000.00 Credited in your Ac XX5468 on 04-02-2026 By NEFTINWARD HDFCH00778553836/HDFC MUTUAL F .Avl Bal 18679.91", "JX-BOIIND-S", on("2026-02-04"))
        expect(c, "15,000.00", CREDIT, acct = "5468", merchant = "HDFC MUTUAL F", ref = "HDFCH00778553836", date = "2026-02-04", balance = "18679.91", kind = TxnKind.MF_REDEMPTION)
    }

    @Test fun `F51 Indian Bank sent and UPI credit (generic fallback)`() {
        val d = txn("Sent Rs.440.00 from A/c *4512 on 17-07-26 to NARMADA FOODS.RRN 213416112187.Avl Bal Rs.4585.65.Not you? SMS BLOCK to 9289592895-Indian Bank", "AD-INDBNK-S", on("2026-07-17"))
        expect(d, "440.00", DEBIT, acct = "4512", merchant = "NARMADA FOODS", ref = "213416112187", date = "2026-07-17", balance = "4585.65")
        val c = txn("Rs.2.00 credited to a/c *8175 on 07/10/2025 by a/c linked to VPA poweraccess.paytm3@axisbank (UPI Ref no 981408452805).Indian Bank", "AD-INDBNK-S", on("2025-10-07"))
        expect(c, "2.00", CREDIT, acct = "8175", merchant = "poweraccess.paytm3@axisbank", ref = "981408452805", date = "2025-10-07")
    }

    @Test fun `F51 Central Bank NEFT credit with three decimals`() {
        val t = txn("Rs. 5.000 credited to your A/c xxxxxx1234 on 03/01/2026 through NEFT vide Ref No./XUTR/IN22XX...XX24 By.NEXTBILLION TECHNOLOGY PRIVATE LIMI-CBoI", "AD-CENTBK-S", on("2026-01-03"))
        expect(t, "5.00", CREDIT, acct = "1234", merchant = "NEXTBILLION TECHNOLOGY PRIVATE LIMI", date = "2026-01-03", channel = Channel.NEFT)
    }

    @Test fun `F51 Bandhan UPI debit`() {
        val t = txn("INR 180.00 debited from A/c XXXXXXXXXX1234 towards UPI/DR/D123013240123/Amazon Pa Value 16-NOV-2025 . Clear Bal is INR 9999.99. Bandhan Bank", "XY-BDNSMS-S", on("2025-11-16"))
        expect(t, "180.00", DEBIT, acct = "1234", merchant = "Amazon Pa", ref = "D123013240123", date = "2025-11-16", balance = "9999.99")
    }

    // ---- India Post, Kerala Bank, Airtel Payments Bank, loans ----
    @Test fun `India Post account credit and debit`() {
        val c = txn("Account  No. XXXXXXXX1234 CREDIT with amount Rs. 5550.00 on 02-03-2026. Balance: Rs.40000.00. [S76543210]", "VM-DOPBNK-G", on("2026-03-02"))
        expect(c, "5550.00", CREDIT, acct = "1234", ref = "S76543210", date = "2026-03-02", balance = "40000.00", institution = "India Post")
        val d = txn("Account No. XXXXXXXX5678 DEBIT with amount Rs. 2000.00 on 15-03-2026. Balance: Rs.18000.00. [D12345678]", "BZ-DOPBNK-G", on("2026-03-15"))
        expect(d, "2000.00", DEBIT, acct = "5678", ref = "D12345678", date = "2026-03-15", balance = "18000.00")
    }

    @Test fun `Kerala Bank loan recovery credit keeps the negative loan balance`() {
        val t = txn("Dear Customer Your A/c no XXXX0024 is credited with 15000.00 on 06-06-2026 by Loan Recovery From : 139451061. Balance is -579822.00 - Kerala Bank", "VM-KELBNK-S", on("2026-06-06"))
        expect(t, "15000.00", CREDIT, acct = "0024", ref = "139451061", date = "2026-06-06", accountType = AccountType.LOAN, kind = TxnKind.TRANSFER)
        assertEquals(-57982200L, t.balance)
        assertEquals(CategoryIds.TRANSFERS, categoriser.categorise(t).categoryId)
    }

    @Test fun `F73 Airtel Payments Bank credit and debit`() {
        val c = txn("Airtel Payments Bank a/c is credited with Rs.20.00. Txn ID: 560992310006. Call 180023400 for help", "AD-AIRBNK-S")
        expect(c, "20.00", CREDIT, ref = "560992310006", institution = "Airtel Payments Bank")
        val d = txn("Rs. 5.00 debited from Airtel Payments Bank a/c Txn ID xxxxxxxx Bal:15.56 Call 180023400 for help", "AD-AIRBNK-S")
        expect(d, "5.00", DEBIT, balance = "15.56")
    }

    @Test fun `ICICI auto-loan bill pay debit is EMI`() {
        val t = txn("ICICI Bank Acc XX342 debited Rs. 8,927.00 on 07-Jun-26 InfoBIL*Auto Loan.Avl Bal Rs. 1,248.78.To dispute call 18002662 or SMS BLOCK 342 to 9215676766", "AX-ICICIT-S", on("2026-06-07"))
        expect(t, "8,927.00", DEBIT, acct = "342", merchant = "BIL*Auto Loan", date = "2026-06-07", balance = "1,248.78", kind = TxnKind.EMI, channel = Channel.BILLPAY)
    }

    @Test fun `EMI reminder is an upcoming debit, not a transaction`() {
        val r = parse("Rs. 500.00 will be debited from your account on 05-Oct-25 for EMI payment", "AX-ICICIT-S", on("2025-10-01"))
        assertIs<ParseResult.UpcomingDebit>(r)
        assertEquals(50000L, r.amount)
        assertEquals(Pub.day("2025-10-05"), r.dueDate)
        assertEquals("EMI payment", r.merchant)
        assertNull(r.accountLast4)
    }
}
