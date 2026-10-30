package app.arthik.core

import app.arthik.core.Pub.on
import app.arthik.core.TestSupport.parse
import app.arthik.core.model.ParseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Spec 14.3 and sms-research/01 sections 4-5: failures, pre-debit notices and other non-transactions (all verbatim). */
class PublicIgnoreTest {
    private fun ignored(body: String, sender: String) = assertIs<ParseResult.Ignored>(parse(body, sender), body)

    @Test fun `F74 failed and declined transactions are ignored`() {
        ignored("Hi, txn of Rs. 1500.00 using card XX**3456 failed due to insufficient funds. Current Bal: Rs.250.75. Call 18004251199 if txn not initiated by you -Federal Bank", "AD-FEDBNK-S")
        ignored("Hi, payment of INR 199.00 via e-mandate declined for ID: NX789XYZABC on Federal Bank Debit Card 3456. To manage, visit: https://www.sihub.in/managesi/federal T&CA - Federal Bank", "AD-FEDBNK-S")
        ignored("UPI Transaction Declined for your HDFC Bank\naccount ending 1001 for security reasons.For\nassistance, Call 18001234567", "AD-HDFCBK-S")
        ignored("Your account is not debited with Rs 500.00 towards ARJUN MEHTA for UPI Mandate due to cbs rejection 0116, RRN 512345678905-ICICI Bank.", "AD-ICICIT-S")
        ignored("Your slice credit card transaction of RS. 50000 on amazon.in was declined.", "AD-SLICEIT-S")
        ignored("Payment of Rs.50000 could not be processed. Please try again later.", "AD-CREDIN-S")
    }

    @Test fun `pre-debit and mandate notices become upcoming debits`() {
        val hdfc = parse("E-Mandate!\nRs.3821.00 will be deducted on 02/03/26, 00:00:00\nFor Rentomojo mandate\nUMN …@ybl\nMaintain Balance\n-HDFC Bank", "AD-HDFCBK-S", on("2026-02-28"))
        assertIs<ParseResult.UpcomingDebit>(hdfc)
        assertEquals(382100L, hdfc.amount)
        assertEquals("Rentomojo", hdfc.merchant)
        assertEquals(Pub.day("2026-03-02"), hdfc.dueDate)
        val icici = parse("Your account will be debited with Rs 649.00 on 03-Oct-25 towards Netflix Entertainment Ser for AutoPay MERCHANTMANDATE, RRN 421723106963-ICICI Bank.", "AD-ICICIT-S", on("2025-10-01"))
        assertIs<ParseResult.UpcomingDebit>(icici)
        assertEquals(64900L, icici.amount)
        assertEquals("Netflix Entertainment Ser", icici.merchant)
        assertEquals(Pub.day("2025-10-03"), icici.dueDate)
        val idfc = parse("INR 587.64 for Airtel Payments Bank Limited will be auto-debited via Axis Bank Card no. XXxxxx by 27-07-26. …", "AD-IDFCBK-S", on("2026-07-25"))
        assertIs<ParseResult.UpcomingDebit>(idfc)
        assertEquals(58764L, idfc.amount)
        assertEquals(Pub.day("2026-07-27"), idfc.dueDate)
    }

    @Test fun `mandate setup, collect requests and OTPs with amounts are ignored`() {
        ignored("Auto Pay HDFC Bank NACH Mandate : Rs. 100000.00 UMRN:HDFC7031703262015557 To:NationalSecuritiesClearin Freq ADHO received today for processing.", "VM-HDFCBK-S")
        ignored("Your UPI-Mandate for Rs 119.00 is successfully created towards … UMN:payee1@pthdfc.", "AD-HDFCBK-S")
        ignored("Your UPI-Mandate is successfully cancelled towards … for 139.00 …-SBI", "VM-SBIUPI-S")
        ignored("EXAMPLE FD JOURNEY has requested Rs10000 frm u on a UPI app. Once approved, money will be debited frm ur a/c -SBI", "VM-SBIUPI-S")
        ignored("You have received a collect request of Rs.500.00 from john@ybl on PhonePe.", "AD-PHONPE-S")
        ignored("PhonePe UPI Circle payment request: 1948 has requested a payment of Rs.263 to …", "AD-PHONPE-S")
        assertEquals(ParseResult.Ignored("otp"), parse("123456 is your OTP for a Dr. INR 500.00 transaction on your Canara Bank card.", "AD-CANBNK-S"))
        assertEquals(ParseResult.Ignored("otp"), parse("Your Amex SafeKey One-Time Password for INR 213.50, at X CORP- PAID FEATURES is 000000.", "VM-KOTAKB-S"))
    }

    @Test fun `bill alerts, balance-only, offers, ASBA, vouchers and service notices are ignored`() {
        ignored("New Bill Alert:\nYour XUBA00000TST1A Bill 1234567890 of Rs.1500.00 is due on 15-Jan-2026. …", "AD-HDFCBK-S")
        ignored("Airtel Postpaid Fetch and Pay bill of INR 293.82 due on 28-09-26. …", "AD-HDFCBK-S")
        ignored("Your A/C XXXXX100002 has Avl Bal Rs 20,407.29 as on 03/12/25.-SBI", "AD-CBSSBI-S")
        ignored("Your A/C 2134***12345 has Avl BAL of INR 1,234.56 as on 05/10/25 04:10 AM. …-IndusInd", "AD-INDUSB-S")
        ignored("UPDATE : You can convert  your Credit Card xx0000 outstanding bill of Rs.27169.6 into SmartEMI of Rs.3282 + GST …", "AD-HDFCBK-S")
        ignored("Get a pre-approved personal loan of Rs.5,00,000 …", "AD-HDFCBK-S")
        ignored("Your ASBA application for AUGMONT is received and Application value of Rs 14972 is blocked …", "AD-HDFCBK-S")
        ignored("You have received Amazon Shopping E-voucher Rs.500/- from HDFC Bank My Rewards …", "AD-HDFCBK-S")
        ignored("Card ending 4321: … you have done 02 out of 06 free transactions at Canara ATMs …", "AD-CANBNK-S")
        ignored("UPI LITE has been disabled. An amount of Rs 0.27 will be transferred to your Savings Account …", "VM-SBIUPI-S")
        ignored("We have activated Standing Instruction on ICICI Bank Credit Card 1005. Merchant: …, Maximum Amount: USD 150.00 …", "AD-ICICIT-S")
        ignored("Recharge of INR 100.00 is successful for your Airtel Mobile …", "AD-AIRTEL-S")
    }

    @Test fun `bank-format text from a mobile number is never booked`() {
        val r = parse("Sent Rs.750.00\nFrom HDFC Bank A/C *1001\nTo AAHARA CAFE\nOn 02/10/25\nRef 100000000001\nNot You?\nCall 18001234567/SMS BLOCK UPI to 7308080808", "+919812345678")
        assertEquals(ParseResult.Ignored("untrusted-sender"), r)
    }
}
