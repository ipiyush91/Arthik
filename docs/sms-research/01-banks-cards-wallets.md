# Indian financial SMS formats: research for the parser build spec

Compiled 2026-10-30. This covers banks, cards and wallets other than the ICICI savings and ICICI/SBI Card/HDFC credit-card samples you already have. A few ICICI and SBI Card variants are included where they add something new.

## Sources and confidence labels

Open web pages were mostly unreachable from this session. The session's network allow-list blocked desidime.com, technofino.in, trenziq.web.app and kotak.bank.in. Web-search snippets rarely quoted full SMS text. Nearly every verbatim sample therefore comes from **test fixtures in open-source Indian SMS-parser repos**, which state that the fixtures are real messages with personal data removed:

| Key | Repo | Files used |
|---|---|---|
| **PW** | https://github.com/sarim2000/pennywiseai-tracker (PennyWise AI, Android/KMP, ~150 bank parsers) | `parser-core/src/test/kotlin/*.kt`, `parser-core/src/test/kotlin/com/pennywiseai/parser/core/bank/*Test.kt`, `parser-core/src/test/resources/ios-parity.tsv` (853 sender+body rows), `SmsReportRegressionTest.kt` (user-reported SMS), parser KDoc comments in `parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/*.kt` |
| **IBSP** | https://github.com/harshsingh-io/indian-bank-sms-parser (TS, JSON profiles). Its README says it is "parsed around 1,800 real messages"; the `exbank` profile is synthetic and is **not** used here | `tests/fixtures/{hdfc,sbi,icici,phonepe,payzapp,cred}/*.json` |
| **UMB** | https://github.com/DeepakSilaych/umber. The file header says "Templates captured from a real device inbox … Personal identifiers have been replaced with placeholders; the message *structure* … is untouched" | `app/src/test/java/com/deepak/umber/parse/RealTemplatesTest.kt`, `SmsParserTest.kt` |
| **CSH** | https://github.com/ritesh-kanwar/Cashiro (fork lineage of PennyWise) | `parser-core/src/test/kotlin/com/ritesh/parser/core/bank/SBIBankParserTest.kt`, `HDFCBankParserTest.kt` |

The labels mean:
- **verbatim**: the full message text appears in a source as a sample SMS. The repo authors already anonymised it (numbers such as `XX1234`, `123456789012`, names such as "JOHN DOE", "Example Shop"). Spacing, punctuation, line breaks (`\n`) and odd spacing such as `AMAZON                000` are kept exactly as published.
- **verbatim-comment**: the full message is quoted in a parser's code comment or KDoc rather than a test. It is a reliable template, but the source shows no test against it.
- **reconstructed**: the shape is inferred from regexes or partial quotes in parser code. No full sample was found, so do not treat it as ground truth.

Sender IDs: Indian DLT headers look like `XY-ABCDEF-S`. `XY` is the operator/circle prefix and varies by message. The suffix `-S` means service, `-T` transactional, `-P` promotional and `-G` government. Match on the **6-char core**, e.g. `HDFCBK`. Some fixtures show the sender with a `(smsft)` suffix; that is an export artefact, not part of the header.

---

## 1. Savings / current account formats

### SBI (State Bank of India). Sender cores: `SBIUPI`, `SBIINB`, `CBSSBI`, `ATMSBI`, `SBIPSG`, `SBIBNK`/`SBIBK`

**F01 · SBI · UPI debit (no currency marker!)**: verbatim
```
Dear UPI user A/C X1001 debited by 305.0 on date 08Oct25 trf to AAHARA CAFE Refno 100000000001 If not u? call-18001234567 for other services-9223766666-SBI
```
A second variant (UMB) is `Dear UPI user A/C X8912 debited by 120.0 on date 05Jul25 trf to ZEPTO MARKETPLA Refno 519612345678. If not u? call 1800111109. -SBI`
- Fields: acct last4 `X(\d+)`, amount after `debited by` (**no Rs/INR**, and `305.0` means ₹305.00), date `ddMMMyy`, payee after `trf to` up to `Refno`, ref (12-digit UPI RRN).
- Sender: `SBIUPI` (e.g. `VM-SBIUPI-S`).
- Source: IBSP `tests/fixtures/sbi/01-upi-debit-no-currency-marker.json`, `02-upi-debit-trailing-zero.json`; UMB `SmsParserTest.kt`.

**F02 · SBI · UPI credit**: verbatim
```
Dear SBI User, your A/c X1003-credited by Rs.90 on 28Apr26 transfer from HILLSIDE DINER Ref No 100000000011 -SBI
```
- Fields: last4 (note the hyphen glued on: `X1003-credited`), amount, date `ddMMMyy`, payer after `transfer from`, ref. Sender `SBIUPI`. Source: IBSP `sbi/03-upi-credit.json`.

**F03 · SBI · IMPS credit**: verbatim
```
Dear Customer, Your a/c no. XXXXXXXX4338 is credited by Rs.2.00 on 17-04-26 by a/c linked to mobile 1XXXXXX111-EXAMPLE RIDES (IMPS Ref# 100000000010)-SBI
```
- Fields: acct, amount, date `dd-MM-yy`, payer (text after the masked mobile and `-`), IMPS ref. Sender `SBIBNK`. Source: IBSP `sbi/04-imps-credit.json`.

**F04 · SBI · IMPS debit (to another account)**: verbatim
```
Dear Customer, Your a/c no. XXXXXXXX5045 is debited for Rs.500.00 on 31-03-26 and a/c XXXXXXX418 credited (IMPS Ref no ---------------). -SBI
```
- Fields: acct, amount, date, beneficiary acct, IMPS ref (can be dashes). Sender `VA-SBIPSG-T`. Source: CSH `SBIBankParserTest.kt`.

**F05 · SBI · CBS transfer debit**: verbatim
```
Dear Customer, Your A/C XXXXX901234 has a debit by transfer of Rs 230.00 on 18/09/25. Avl Bal Rs 6,500.00.-SBI
```
- Sender `AD-CBSSBI-S`. Fields: acct, amount, date `dd/MM/yy`, balance. Source: CSH `SBIBankParserTest.kt`.

**F06 · SBI · Salary credit / generic CBS credit**: verbatim
```
Your A/C XXXXX314567 has credit for BY SALARY of Rs 4,000.00 on 31/12/22. Avl Bal Rs 17,70,200.00.-SBI
```
Variants from the same source:
`Dear Customer, Your A/C XXXXX314567 has a credit by Cheque of Rs 12,07,000.00 on 07/10/22. Avl Bal Rs 18,06,500.00.-SBI`
`Your A/C XXXXX314502 has credit for AOFS23546782123411BHPL of Rs 10,700.00 on 02/05/22. Avl Bal Rs 13,50,000.00.-SBI`
- Fields: narration after `has credit for` / `has a credit by` (e.g. `BY SALARY` means salary), amount (Indian grouping `13,50,000.00`), date, balance. Sender `CBSSBI`. Source: CSH `SBIBankParserTest.kt`.

**F07 · SBI · Cash deposit**: verbatim
```
Your A/C XXXXX100002 Credited INR 31,700.00 on 28/01/26 -Deposited by Cash by SELF. Avl Bal INR 34,302.79-SBI
```
- Sender `CBSSBI`. Source: IBSP `sbi/05-cash-deposit.json`.

**F08 · SBI · ATM withdrawal reversal (credit)**: verbatim
```
Your AC XXXXX314567 Credited INR 9,000.00 on 22/05/22 -REVERSE ATM WDL. Avl Bal INR 13,08,900.00.-SBI
```
- Treat as a refund/reversal credit. Sender `AD-CBSSBI`. Source: CSH `SBIBankParserTest.kt`.

**F09 · SBI · UPI reversal (credit)**: verbatim
```
Your A/C XXXXX100002 has credit for UPI/REVERSAL/100000000005/0112 of Rs 2.00 on 03/12/25. Avl Bal Rs 20,407.29.-SBI
```
- Key token `UPI/REVERSAL/<rrn>` means a refund of the original UPI debit. Note it says "credit", not "credited". Source: IBSP `sbi/06-upi-reversal.json`.

**F10 · SBI · Debit card POS**: verbatim
```
Dear Customer, transaction number 1234 for Rs.383.00 by SBI Debit Card 0000 done at merchant on 13Sep25 at 21:38:26. Your updated available balance is Rs.999999999. If not done by you, forward this SMS to 7400165218/ call 1800111109/9449112211 to block card. GOI helpline for cyber fraud 1930.
```
- Fields: amount, card last4, merchant after `done at` up to ` on `, date+time, balance. Sender `ATMSBI`. Source: PW `TestSBIParser.kt`.

**F11 · SBI · Short debit (ATM sender)**: verbatim
```
Rs.500 debited from A/c X1234 on 13Sep25. Avl Bal Rs.999999999
```
- Sender `ATMSBI`. Source: PW `TestSBIParser.kt`.

**F12 · SBI · YONO Cash / ATM withdrawal**: reconstructed
- Shape from PW `SBIBankParser.kt` comments: `Yono Cash Rs 3000 w/d@SBI ATM …` and `ATM withdrawal of Rs 500`. No full sample was found.

### HDFC Bank savings. Sender core: `HDFCBK` (also `HDFCBANK`)

**F13 · HDFC · UPI debit (new multi-line "Sent")**: verbatim
```
Sent Rs.750.00
From HDFC Bank A/C *1001
To AAHARA CAFE
On 02/10/25
Ref 100000000001
Not You?
Call 18001234567/SMS BLOCK UPI to 7308080808
```
The single-line variant (PW `TestHDFCBankParser.kt`) is `Sent Rs.15000.00 From HDFC Bank A/C *1234 To TEST MERCHANT PVT LTD On 01/01/26 Ref 567890567890 Not You? Call 18005556789/SMS BLOCK UPI to 7305556789`
- Fields: amount, last4 after `A/C *`, payee after `To` (name or VPA), date `dd/MM/yy`, ref. Source: IBSP `hdfc/01-upi-debit-merchant.json`, `02-upi-debit-person-vpa.json`; PW; CSH.

**F14 · HDFC · UPI debit (older one-liner)**: verbatim
```
Rs.500.00 debited from A/c XX1234 on 20-Oct-25 to merchant@upi (UPI Ref No 123456789012)
```
The account-to-account variant (IBSP `hdfc/10`) is `HDFC Bank:Rs. 14000.00 debited from a/c *1001 on 01/02/26 to a/c **1004 (UPI Ref No. 100000000369). Not you? Call on 18001234567`
- Source: PW `TestHDFCBankParser.kt`; IBSP.

**F15 · HDFC · UPI credit**: verbatim
```
Credit Alert!
Rs.1,150.00 credited to HDFC Bank A/c XX1001 on 04-10-25 from VPA payee2@axl (UPI 100000000003)
```
- Source: IBSP `hdfc/03-credit-alert.json`.

**F16 · HDFC · IMPS credit**: verbatim
```
Received!
INR 5,000.00 in HDFC Bank A/c xx1001
On 17-10-25
For IMPS -EXAMPLE PAYMENTS LTD- 100000000034
Avl bal INR 45,549.08
```
- Source: IBSP `hdfc/04-imps-credit.json`.

**F17 · HDFC · IMPS debit**: verbatim
```
IMPS INR 10,000.00
sent from HDFC Bank A/c XX1001 on 10-09-26
To A/c xxxxxxxxxx1007
Ref-100000001037
Not you?Call 18001234567/SMS BLOCK OB to 7308080808
```
- Source: IBSP `hdfc/05-imps-debit.json`.

**F18 · HDFC · NEFT credit / salary**: verbatim
```
Update! INR 50,000.00 deposited in HDFC Bank A/c XX1001 on 02-SEP-26 for NEFT Cr-EXBK0001234-EXAMPLE PAYROLL SERVICES-Account Holder-EXBK0000000000001.Avl bal INR 99,999.99. Cheque deposits in A/C are subject to clearing.
```
- Fields: amount, last4, date `dd-MMM-yy`, narration after `for` in the form `NEFT Cr-<IFSC>-<REMITTER>-<BENEFICIARY>-<UTR>`, balance. A remitter containing "PAYROLL"/"SALARY" suggests salary. Source: IBSP `hdfc/18-neft-salary-credit.json`; PW `TestHDFCBankParser.kt` (same template).

**F19 · HDFC · Interest credit**: verbatim
```
Update! INR 6,657.00 deposited in HDFC Bank A/c XX1111 on 30-SEP-26 for Interest paid till 30-SEP-2026.Avl bal INR 12,28,554.72. Cheque deposits in A/C are subject to clearing
```
- Sender `JM-HDFCBK-S`. Source: PW `SmsReportRegressionTest.kt`.

**F20 · HDFC · Net-banking / ACH debit ("UPDATE: … Info:")**: verbatim
```
UPDATE: INR 12,990.00 debited from HDFC Bank XX1001 on 18-DEC-25. Info: EXAMPLEAMC/1000000000000221-XXXXXXXXXX8730. Avl bal:INR 99,999.99
```
The bill-pay variant (PW) is `Payment Successful! Rs. 12345.67 from A/c ****4321 to ACMEPAYMENTS via HDFC Bank NetBanking. Not you?Call 18002586161`
- Source: IBSP `hdfc/09-netbanking-debit.json`; PW `TestHDFCBankParser.kt`.

**F21 · HDFC · Debit card POS (no card number)**: verbatim
```
Spent Rs.2 From DAILY FRESH MART On 2025-12-16:09:40:54 Bal Rs.95853.26 Not You? Call 18001234567/SMS BLOCK DC 3271 to 7308080808
```
- Fields: amount, merchant after `From` up to ` On `, timestamp `yyyy-MM-dd:HH:mm:ss`, balance. The card last4 only appears in the `BLOCK DC NNNN` tail. Source: IBSP `hdfc/08-debit-card-spend.json`.

**F22 · HDFC · ATM withdrawal (debit card)**: verbatim
```
Withdrawn Rs.800 From HDFC Bank Card x1002 At EXAMPLE ATM NAGAR On 2025-10-24:21:06:54 Bal Rs.40043.08 Not You? Call 18001234567/SMS BLOCK DC 3271 to 7308080808
```
- Source: IBSP `hdfc/06-atm-withdrawal.json`.

**F23 · HDFC · EMI / SmartEMI debit**: verbatim
```
Rs.3282.00 debited from HDFC Bank A/c XX1111 on 05-10-26 towards SmartEMI. Avl bal Rs.5000.00
```
- Source: PW `SmsReportRegressionTest.kt`.

### Axis Bank. Sender core: `AXISBK`

**F24 · Axis · Debit-card POS / ATM debit from savings**: verbatim
```
INR 209.00 debited from A/c no. XXxxxxy on BURGRILL 04-12-2025 13:13:27 IST. Avl bal: INR xxxxxxx. Not you? SMS BLOCKCARD XX0023 to +919951860002 - Axis Bank
```
The ATM variant uses `on AXIS BANK L` as the merchant: `INR 2000.00 debited from A/c no. XX589034 on AXIS BANK L 04-11-2025 16:06:39 IST. Avl bal: INR 98919.81. Not you? SMS BLOCKCARD XX0192 to +919951860002 - Axis Bank`
- Fields: amount, acct, merchant between `on` and the `dd-MM-yyyy HH:mm:ss IST` timestamp, balance, card last4 in `BLOCKCARD XXnnnn`. Sender `JD-AXISBK-S`. Source: PW `TestAxisBankParser.kt`.

**F25 · Axis · UPI debit/credit with `UPI/P2M/…` / `UPI/P2A/…` narration**: reconstructed
- The PW `AxisBankParser.kt` merchant regexes `UPI/[^/]+/[^/]+/(merchant)(?:\s*Not you|$)` and `UPI/P2A/[^/]+/(name)` imply a body of the form `INR <amt> debited\nA/c no. XX1234\n<dd-MM-yy, HH:mm:ss>\nUPI/P2M/<rrn>/<MERCHANT>\nNot you? …`. Credits use an `Info- <narration>` tail (salary is detected by `Info- … SALARY`). No full text was found.

**F26 · Axis · EMI auto-debit**: verbatim
```
INR 5,000.00 debited from A/c XX1111 for EMI due on 05-10-26. Avl Bal INR 10,000.00
```
- Sender `AX-AXISBK-S`. Source: PW `SmsReportRegressionTest.kt`.

### Kotak Mahindra Bank. Sender cores: `KOTAKB`, `KOTAKD` (new), plain "Kotak Mahindra Bank"

**F27 · Kotak · UPI debit (classic)**: verbatim
```
Sent Rs.15.00 from Kotak Bank AC X1234 to paytmqr288005050101t74afkchmxjd@paytm on 14-10-25.UPI Ref 1234567890. Not you, https://kotak.com/KBANKT/Fraud
```
- Sender `JD-KOTAKB-S`. Source: PW `TestKotakBankParser.kt`.

**F28 · Kotak · UPI debit (2026 format, sender KOTAKD)**: verbatim
```
Sent Rs.51.00 from XXXXXX9722 to DINESHBHAI RAMJIBHAI on 30/04/2026. UPI ref no. 648604626824. Not you? Tap https://kotk.in/KOTAKD/Eg9Cu0 to report -Kotak
```
- Sender `VM-KOTAKD-S`. Source: PW `TestKotakBankParser.kt`.

**F29 · Kotak · UPI credit**: verbatim
```
Received Rs.250.00 in your Kotak Bank AC X3333 from john.doe@oksbi on 14-10-25.UPI Ref 2222222222. Not you, https://kotak.com/KBANKT/Fraud
```
- Source: PW `TestKotakBankParser.kt`.

**F30 · Kotak · IMPS credit**: verbatim
```
Received Rs. 329.00 on 15-04-26 in your Kotak Bank A/C x2451 by an A/C linked to mobile x111. IMPS Ref no 610511412340.
```
- Source: PW `TestKotakBankParser.kt`.

**F31 · Kotak · NEFT credit**: verbatim
```
Rs. 5000 credited to your Kotak Bank a/c XX1111 via NEFT from beneficiary John Doe. UTR Ref. HDFCH00000000000
```
- Sender `VM-KOTAKB-S`. Source: PW `SmsReportRegressionTest.kt`.

**F32 · Kotak · Generic debit / cashback credit**: verbatim
```
Rs.1000.00 debited from your Kotak Bank AC X4444 on 15-10-25. Avl Bal Rs.10000.00
```
`Cashback of Rs.50.00 has been sent to your Kotak Bank A/c x5555. Credited on 14-10-25. UPI Ref 9999999999`
- Source: PW `TestKotakBankParser.kt`.

### Bank of Baroda. Sender cores: `BOBTXN`, `BOBSMS`, `BOBCRD` (card)

**F33 · BoB · UPI / transfer debit**: verbatim
```
Rs.29 transferred from A/c ...5494 to:Loan Recovery Fo. Total Bal:Rs.24898.57CR. Avlbl Amt:Rs.24898.57(04-11-2025 04:03:09) - Bank of Baroda
```
The short variant is `Rs.80.00 Dr. from A/c XX123456 on 12-11-2024. AvlBal:Rs1234.56cx. Ref:52211012345 -Bank of Baroda`
- Fields: amount, acct (`...5494`), payee after `to:` (truncated to ~16 chars), balance with `CR` suffix, datetime in parentheses. Source: PW `TestBankOfBarodaParser.kt`.

**F34 · BoB · UPI credit (confusing "Cr. to <vpa>")**: verbatim
```
Rs.500.00 Cr. to redacted@ybl A/c XX789012 on 15-11-2024. AvlBal:Rs5678.90. Ref:987654321 -Bank of Baroda
```
- Caution: this format puts the VPA right after `Cr. to`. Source: PW `TestBankOfBarodaParser.kt`.

**F35 · BoB · IMPS / NACH(ACH) credit / cash deposit**: verbatim
```
Rs.2500.00 credited to A/c XX456789 via IMPS/518233445566 by JOHN DOE. AvlBal:Rs25000.00 -Bank of Baroda
```
`Rs..6 Credited to A/c ...4444 from:ACHCR/JIO FINANC. Total Bal:Rs.22976.31CR. Avlbl Amt:Rs.22976.31(26-08-2026 18:20:36) - Bank of Baroda` (note the `Rs..` double dot; ACH CR is a dividend/NACH credit)
`Rs.10000.00 deposited in cash to A/c XX234567 on 20-11-2024. AvlBal:Rs45000.00 -Bank of Baroda`
- Source: PW `TestBankOfBarodaParser.kt`, `SmsReportRegressionTest.kt`.

### Punjab National Bank. Sender cores: `PNBSMS`, `PNBCCD` (credit card)

**F36 · PNB · UPI debit/credit**: verbatim
```
A/c X0000 debited INR 125.00 Dt 01-01-26 12:00:00 to Swiggy thru UPI:000000000001..Bal INR 9000.00-PNB
```
`Ac XX7582 Credited with Rs.4750.00 11-07-2025 08:53:21 thru UPI . Aval Bal Rs.82431.67 CR. (UPI Ref ID:714628305917) Helpline 18001800/18002021-PNB`
`A/c X0000 credited INR 125.00 From Example Shop Ltd/UPI:000000000003. Bal INR 9000.00-PNB`
- Source: PW `PNBBankParserTest.kt`, `ios-parity.tsv`.

**F37 · PNB · IMPS debit**: verbatim
```
Your a/c no XX847 is debited for Rs 3200 on 11-07-25 03:14:52 and a/c XX291 credited (IMPS Ref no 712845931206) .If not done by you, pl. forward this SMS from registered mobile to 9264092640 to report unauthorized txn & block IBS/MBS. Download PNB ONE.-PNB
```
- Source: PW `PNBBankParserTest.kt`.

**F38 · PNB · ATM withdrawal / debit-card POS**: verbatim
```
A/c XX1234 debited with Rs.5000.00,21-11-2025 13:23:22 thru card XX9239  . Out of 5 free txn on PNB ATM, you utilized 1 txn. Chrgs applicable as per policy. Bal 27000.00 CR. If not done, fwd SMS to 9264192641 to block card/call 18001800/18002021-PNB
```
`Ac XX0000 Debited by INR 125.00 thru Debitcard XXXX0000 at Example Shop on 01-01-2026. Aval Bal INR 9000.00-PNB`
`Ac XX0000 debited with Rs.25.00,01-01-2026 12:00:00 through Debitcard XXXX0000 on other bank ATM. Balance Rs.9000.00-PNB`
- Source: PW `PNBBankParserTest.kt`.

**F39 · PNB · Generic debit (no channel)**: verbatim
```
Ac XX1234 Debited with Rs.5000.00, 20-02-2026 07:47:16. Aval Bal Rs.27000.00 CR. Helpline 18001800/18002021-PNB
```
- Source: PW `PNBBankParserTest.kt`.

### Canara Bank. Sender core: `CANBNK`

**F40 · Canara · UPI debit**: verbatim
```
Dear Customer, Acct XXX123 Dr. INR 260.00 on 06/07/26 to SAMPLE MART; UPI: 123456789012; Bal INR 12,345.67.Not you? SMS BLOCKUPI to XXXXXXXXXX-CanaraBank
```
The same template also appears with `Rs.` and `₹` amounts.
- Source: PW `CanaraBankParserTest.kt`.

**F41 · Canara · RTGS credit**: verbatim
```
An amount of INR 13,30,614.75 has been credited to XXXX6785 on 02/12/2025 towards RTGS by Sender AXIS MUTUAL FUND REDEMPTION PO, IFSC UTIB0000004, Sender A/c XXXX9108, AXIS BANK, MUMBAI BRANCH, UTR UTIBR72025120200011461, Total Avail. Bal INR 2679815.88- Canara Bank
```
- Source: PW `CanaraBankParserTest.kt`.

### Union Bank of India. Sender core: `UNIONB` (also `UNIONBANK`)

**F42 · Union · Debit (mobile banking)**: verbatim-comment
```
A/c *1234 Debited for Rs:100.00 on 11-08-2025 18:28:02 by Mob Bk ref no 123456789000 Avl Bal Rs:12345.67
```
- Note the `Rs:` with a colon. The parser comment also says real messages carry a "Never Share OTP/PIN/CVV" tail, so **don't reject on the "OTP" keyword alone**. Source: PW `UnionBankParser.kt` KDoc.
- UPI/credit variants are reconstructed only (`UPI/<merchant>`, `from <sender>`).

### IDFC FIRST Bank. Sender cores: `IDFCBK`, `IDFCFB`

**F43 · IDFC · Debit/credit**: verbatim
```
Your A/C XXXXXXX1234 is debited by INR 68.00 on 06/08/25 17:36. New Bal :INR 5000.00
```
`Your A/c XX4614 debited by Rs. 1,172.06 on 15/01/26; REDBUS credited. RRN 060649915527. Available balance Rs. 9,134.15. Team IDFC FIRST Bank` (UPI P2M; merchant before `credited`)
- Source: PW `TestIDFCFirstBankParser.kt`.

**F44 · IDFC · Interest credit**: verbatim
```
Monthly interest of INR.3,738.00 earned on your Savings A/c XX3344 has been credited to your A/C on 30/04/26. New bal: INR.9,27,610.90. IDFC FIRST Bank
```
- Note `INR.` with a dot. IDFC credits interest **monthly**. Source: UMB `RealTemplatesTest.kt`.

### YES Bank. Sender core: `YESBNK`
Only credit-card / UPI-on-card samples were found; see F55. **The savings format is a gap.**

### IndusInd Bank. Sender cores: `INDUSB`, `INDUSIND`

**F45 · IndusInd · UPI debit/credit**: verbatim
```
A/c *XX1234 debited by Rs 1234.00 towards xxxx.yyyy@icici. RRN: 510048508040. Not You? call 18602677777- IndusInd Bank.
```
`A/C *XX1234 credited by Rs 25000.00 from xxxx.yyyy@ybl. RRN:510048508040. Avl Bal:105502.12. Not you? Call 18602677777 - IndusInd bank.`
- Source: PW `TestIndusIndBankParser.kt`.

**F46 · IndusInd · IMPS / NACH (ACH DR) / debit-card purchase / FD interest**: verbatim
```
Your IndusInd Account 20XXXXX1234 has been debited for INR 6440 towards IMPS/12345678901. Call 18602677777 to report issue-IndusInd Bank.
```
`IndusInd A/C  Debited; INR 4,500.00 Ref-ACH DR INW PAY/0000WD2CEFDT2Z58B2202320321456/Grow.Bal INR 141,999.93.Dispute-Call 18602677777-IndusInd Bank.` (NACH debit to Groww; note the double space and the "Grow.Bal" run-on)
`INR 1,101.53 debited from your A/C 201***123456 towards Debit Card Purchase. Avl BAL INR 400.20 - Not you? Call 18602677777 to report issue - IndusInd Bank.`
`Net interest INR 248.07 paid on your IndusInd Deposit No 300***123456 on 17/09/25. Call 18602677777 for assistance - IndusInd Bank`
- Source: PW `TestIndusIndBankParser.kt`.

### AU Small Finance Bank. Sender core: `AUBANK`

**F47 · AU · UPI debit/credit (multi-line, 2026)**: verbatim
```
Dr INR 10.00 - AU A/c X4541 12-MAY-2026
UPI/DR/424941009000/ALADDIN SHAWARMA/Y
Bal INR 18,838.10
Fraud? Call 180012001200/SMS BLOCK UPI to 5676767
```
```
Cr INR 5,000.00 to A/c X4541 12-MAY-2026
UPI/CR/424941009999/JANE DOE/Y
Bal INR 23,838.10
-AU Bank
```
The older variant is `Debited INR 165.00 from A/c X7013 on 01-MAR-2026\nUPI/DR/651122781360/UK FOOD/UTIB/9180201\nBal INR 900000000\nNot you? Call 180012001200 & dial 0\n-AU Bank`
- Fields: `UPI/(DR|CR)/<rrn>/<counterparty>/…`. Source: PW `TestAUBankParser.kt`.

### Federal Bank. Sender cores: `FEDBNK`, `FEDSMS`, `FEDSCP` (Scapia card)

**F48 · Federal · UPI debit**: verbatim
```
Rs 150.00 debited via UPI on 15-08-2024 10:30:25 to VPA john.doe123@okbank.Ref No 987654321098.Small txns?Use UPI Lite!-Federal Bank
```
- Source: PW `TestFederalBankParser.kt`.

**F49 · Federal · IMPS credit / "Woohoo" credit / NEFT debit / ATM / CDM**: verbatim
```
Rs 3500.50 credited to your A/c XX4567 via IMPS on 18AUG2024 11:45:30 IMPS Ref no 654321098765 Bal:Rs 25000.75 -Federal Bank
```
`John, you've received INR 10,509.09 in your Account XXXXXXXX1896. Woohoo! It was sent by TESTUSER on March 19, 2025. -Federal Bank`
`Debited Rs 6000 from a/c XX3343 on 24JUN2026 21:35 via NEFT to Jerry.Ref FDRLM4175007432.Bal Rs 76.82.Not you?Call 18004251199 -Federal Bank`
`Rs 1500 withdrawn@ YBL CHAN on 21JAN26 17:59 Bal Rs 7517.94 Ref 602117126490. Not you? Call 18004251199/ SMS NO 3683 to 9895088888 -Federal Bank`
`Rs 1500.00 debited via card XX**3456 at MERCHANT on 14-05-2025 15:30:45. Current Bal: Rs.250.75 -Federal Bank` (debit-card POS)
- Source: PW `TestFederalBankParser.kt`.

**F50 · Federal · Debit-card e-mandate processed**: verbatim
```
Hi, payment of INR 199.00 for Netflix via e-mandate ID: NX789XYZABC on Federal Bank Debit Card 3456 is processed successfully. To manage, visit: https://www.sihub.in/managesi/federal T&CA - Federal Bank
```
- Source: PW `TestFederalBankParser.kt`.

### Other PSU/private banks seen (bonus)
**F51 · Bank of India / Indian Bank / Central Bank / Bandhan**: verbatim
- BOI (`BOIIND`): `Rs.200.00 debited A/cXX5468 and credited to SAI MISAL via UPI Ref No 315439383341 on 23Aug25. Call 18001031906, if not done by you. -BOI`
- BOI NEFT credit: `BOI - Rs 15,000.00 Credited in your Ac XX5468 on 04-02-2026 By NEFTINWARD HDFCH00778553836/HDFC MUTUAL F .Avl Bal 18679.91`
- Indian Bank (`INDBNK`): `Sent Rs.440.00 from A/c *4512 on 17-07-26 to NARMADA FOODS.RRN 213416112187.Avl Bal Rs.4585.65.Not you? SMS BLOCK to 9289592895-Indian Bank`
- Indian Bank UPI credit: `Rs.2.00 credited to a/c *8175 on 07/10/2025 by a/c linked to VPA poweraccess.paytm3@axisbank (UPI Ref no 981408452805).Indian Bank`
- Central Bank (`CENTBK`): `Rs. 5.000 credited to your A/c xxxxxx1234 on 03/01/2026 through NEFT vide Ref No./XUTR/IN22XX...XX24 By.NEXTBILLION TECHNOLOGY PRIVATE LIMI-CBoI` (note `5.000` with three decimals)
- Bandhan (`BDNSMS`): `INR 180.00 debited from A/c XXXXXXXXXX1234 towards UPI/DR/D123013240123/Amazon Pa Value 16-NOV-2025 . Clear Bal is INR 9999.99. Bandhan Bank`
- Source: PW `ios-parity.tsv`.

---

## 2. Credit cards

**F52 · HDFC CC · Spend ("Spent Rs … On HDFC Bank Card … At … On …")**: verbatim
```
Spent Rs.2049 On HDFC Bank Card 1003 At BLUE LEAF STORES On 2025-11-11:11:04:40.Not You? To Block+Reissue Call 18001234567/SMS BLOCK CC 8515 to 7308080808
```
The PIXEL card variant (PW) is `Rs 1368.00 used on HDFC Bank PIXEL Card at TEST MERCHANT on 05/10/26 09:03.Not You? SMS BLOCKPCC 2222 to 1800000000 or https://example.invalid/b`
- Fields: amount, card last4, merchant between `At` and ` On `, timestamp `yyyy-MM-dd:HH:mm:ss`. Source: IBSP `hdfc/07-credit-card-spend.json`; PW `SmsReportRegressionTest.kt`. The PW parser also handles `Txn Rs.X On HDFC Bank Card At <M> by UPI` (reconstructed, from a code comment).

**F53 · HDFC CC · Refund / reversal**: verbatim
```
Transaction Reversed!On HDFC Bank CREDIT Card xx5555 Amt: Rs.862.16 By AMAZON                000 On 2026-08-21:05:20:26
```
`Refund initiated: Amt: Rs.34274.66 on HDFC Bank Credit Card 1111. To receive your Refund,please update your Bank details: TnC.`
- Note: the reversal has no space after `!` and pads the merchant with spaces. Source: PW `TestHDFCBankParser.kt` (and commits c5b658a5 / ceae479f).

**F54 · Axis CC · Spend (three layouts)**: verbatim
```
Spent INR 131
Axis Bank Card no. XX0818
05-10-25 09:43:27 IST
Swiggy Limi
Avl Limit: INR 217162.72
Not you? SMS BLOCK 0818 to 919951860002
```
```
Spent
Card no. XX7441
INR 562
01-09-25 12:04:18
AVENUE SUPE
Avl Lmt INR 5120.87
SMS BLOCK 7441 to 919951860002, if not you - Axis Bank
```
`Spent INR 1568 using Axis Bank Credit Card no. XX1234 on 24-10-26 11:07:10 IST at BLINKIT. Avl limit: INR 50999.49. Not you? SMS BLOCK 1234 to +919951860002.`
The foreign-currency variant is `Spent USD 100 Axis Bank Card no. XX0000 14-12-25 23:32:40 IST TESTMERCHANT / Avl Limit: INR 57888.3 Not you? SMS BLOCK 0000 to 1800000000`
- The merchant is truncated to ~11 chars ("Swiggy Limi", "AVENUE SUPE"). Senders: `AX-/CP-/JX-AXISBK-S`. Source: PW `TestAxisBankParser.kt`, `SmsReportRegressionTest.kt`.

**F55 · YES Bank CC · Spend (incl. RuPay-on-UPI)**: verbatim
```
INR 404.36 spent on YES BANK Card X3349 @UPI_C N S FUEL PORT 24-08-2025 06:17:25 pm. Avl Lmt INR 211,476.24. SMS BLKCC 3349 to 9840909000 if not you
```
- `@UPI_<merchant>` marks a credit-card UPI spend. Source: PW `TestYesBankParser.kt`.

**F56 · Kotak CC · Spend / refund / due reminder**: verbatim
```
INR 20 spent on Kotak Credit Card x5236 on 23-JAN-2026 at UPI-638903921672-CORN. Avl limit INR 73733.02 Fraud? https://www.kotak.bank.in/KBANKT/querytxn
```
`INR 2 from Airport Lounge refunded to your Kotak Credit Card x8848 on 17-Jul-2026. Opted for EMI? Please call 18602662666 to cancel.`
`Payment of INR 1577 on Kotak Credit Card xx2222 is due on 13-07-26. Min due: INR 100. Tap to pay: https://example.invalid/pay Ignore if paid` (statement/due reminder; the published sample came via sender `AD-AXISBK-S` in a user report)
- Source: PW `TestKotakBankParser.kt`, `SmsReportRegressionTest.kt`.

**F57 · IDFC FIRST CC · Spend (incl. forex)**: verbatim
```
Transaction Successful! EUR 500.00 spent on your IDFC FIRST Bank Credit Card ending XX1234 at AMAZON EU on 08-FEB-2025 at 01:28 PM Avbl Limit: INR 4074.10 If not done by you, call 180010888
```
- Source: PW `TestIDFCFirstBankParser.kt`.

**F58 · IndusInd CC · Spend / refund**: verbatim
```
INR 1,250.00 spent on IndusInd Card XX1234 on 14-06-2026 04:21:45 pm at INSTAMART. Avl Lmt: INR 48,750.00. To dispute, call 18602677777/SMS BLOCK 1234 to 5676757
```
`Dear Customer, refund of INR 250 from Swiggy Limited Banga has been credited to your IndusInd Bank Credit Card XX1234 on 11-Jun-26 and the refund amount has been adjusted against the outstanding on your card account - IndusInd Bank`
- Source: PW `TestIndusIndBankParser.kt`.

**F59 · Scapia (Federal) CC · Spend**: verbatim
```
Hi! Your txn of ₹882.00 at Carnatic Cafe Gurgaon In on your Scapia Federal Visa credit card was successful. And you've earned 10% rewards on this spend! Not you? Go to Scapia support on the app or call 18002961199. -Federal Bank
```
`Your txn of ₹2,351.00 at Amazon on your Scapia Federal Visa credit card earned you 10% rewards! Not you? Call 18002961199. - Federal Bank`
- Sender `FEDSCP`. Uses the `₹` sign. Source: PW `TestFederalBankParser.kt`.

**F60 · SBI Card · Spend (incl. Unicode "math sans" font!) and payment received**: verbatim
```
Rs.90.00 𝗌𝗉𝖾𝗇𝗍 𝗈𝗇 𝗒𝗈𝗎𝗋 𝖲𝖡𝖨 𝖢𝗋𝖾𝖽𝗂𝗍 𝖢𝖺𝗋𝖽 𝖾𝗇𝖽𝗂𝗇𝗀 5667 at SUPREMEGOURMET on 13/02/26. 𝖳𝗋𝗑𝗇. 𝗇𝗈𝗍 𝖽𝗈𝗇𝖾 𝖻𝗒 𝗒𝗈𝗎? 𝖱𝖾𝗉𝗈𝗋𝗍 𝖺𝗍 https://sbicard.com/Dispute
```
`Your payment of Rs.1,644.55 has been credited to your SBI Credit Card ending with 5667. Your available limit is Rs.48,355.45.`
`Dear SBI Cardholder, payment of Rs. 8857.32 for your SBI Credit Card has been successfully processed. ref no : ABC12XY34ZQ56.` (UMB)
- **Important:** SBI Card has started sending keywords in Unicode Mathematical Sans-Serif letters (U+1D5A0 block). **Run NFKC normalisation before regex matching.** Sender `SBICRD` / "SBI CARDS AND PAYMENT SERVICES". Source: PW `TestSBIParser.kt`; UMB.

**F61 · ICICI CC · Statement / refund / reversal / BBPS payment**: verbatim (extra variants beyond your set)
```
ICICI Bank Credit Card XX1005 Statement is sent to yo************me@example.com. Total of Rs 5,227.02 or minimum of Rs 270.00 is due by 30-MAR-26.
```
`EXAMPLE STORE GROCERY refund of Rs 202.00 credited to ICICI Bank Card XX1005 on 17-MAY-26.`
`Dear Customer, your transaction of Rs.3,587.00 on ICICI Bank Credit Card XX1001 at FITNESS STUDIO dated 08-Aug-26 at 19:31:50 PM has been reversed. A credit will reflect shortly.`
`Payment of Rs 26,266.00 has been received on your ICICI Bank Credit Card XX9006 through Bharat Bill Payment System on 06-DEC-25.`
- Source: IBSP `icici/05-statement-notice.json`, `04-refund.json`, `09-reversal.json`; PW.

**F62 · BOBCARD · Spend / payment received**: verbatim
```
ALERT: INR 1,500.00 is spent on your BOBCARD ending 1234 at AMAZON on 25-11-2024 10:30:00. Available credit limit is Rs 42,981.46 -Bank of Baroda
```
`Payment of Rs 1317.80 received for your BOBCARD ending 2844 on 2026-06-17. Visit bobcard app: bobcard.io/App or online card account at https://www.bobcard.co.in/ for details.`
- Sender `BOBCRD`. Source: PW `TestBankOfBarodaParser.kt`.

**F63 · PNB CC · UPI spend / payment received**: verbatim
```
PNB Credit Card 1234 debited with Rs.1,499 [CODE:AB1234] at AMAZON INDIA on 05-10-2026 10:12 through UPI: 123456789012 Avl limit Rs. 47383.5. -PNB
```
`Thank you Rs.668.79/- has been received as payment towards your PNB credit card  XX1234 via Online Payment. Your available credit limit is Rs.50000. - PNB`
- Sender `PNBCCD`. Source: PW `PNBBankParserTest.kt`.

**F64 · AU CC · Spend**: verbatim
```
INR 259.90 spent at TELEGRAM PREMIUM on AU Bank Credit Card x1234 21-03-2026 05:49:40 PM. Not you? Call 180012001500 or SMS PBLOCK 1234 to 5676767
```
- Source: PW `TestAUBankParser.kt`.

**F65 · AmEx · Spend**: verbatim-comment
```
Alert: You've spent INR 1,017.70 on your AMEX card ** 91000 at VOUCHER PLAT on 20 August 2025
```
- Note the 5-digit card tail (`** 91000`) and the long date `dd MMMM yyyy`. Senders `AMEXIN` (e.g. `TX-AMEXIN-S`). Source: PW `AMEXBankParser.kt` KDoc. An AmEx SafeKey OTP that arrived from a **Kotak** header (`VM-KOTAKB-S`) is in PW `SmsReportRegressionTest.kt` and must be ignored.

**F66 · OneCard · Spend**: reconstructed
- PW `OneCardParser.kt` KDoc lists templates without real values: `You've made a booking of Rs. X on MERCHANT on card ending XXXX`, `You've fueled up for Rs. X at MERCHANT on card ending XXXX`, `You've made a transaction of Rs. X on MERCHANT on card ending XXXX`, plus "hand-picked groceries for Rs. X at". The code also notes "OneCard always starts with 'You've'". Senders `ONECRD` / `CP-OneCrd-S`.

**F67 · CRED · Card bill payment confirmation (any issuer)**: verbatim
```
Payment of Rs.50000 has been successfully credited towards your ICICI Bank Credit Card. Your payment was settled in 3 seconds - CRED
```
- Sender `CREDIN`. Dedupe against the bank's own "payment received" SMS. Source: PW `CredParserTest.kt`.

**Statement SMS gap:** no verbatim HDFC, SBI Card, Axis or AmEx statement ("Total Amt Due / Min Amt Due / due date") SMS was found. Only ICICI (F61) and the Kotak due reminder (F56) are verbatim. Web searches confirmed the fields exist but quoted no text. Build a generic statement detector instead: keywords `statement|total (amt|amount)? ?due|min(imum)? (amt|amount)? ?due|due (by|on)`.

---

## 3. Wallets / UPI apps / payments banks

**F68 · Amazon Pay balance (sent by Juspay)**: verbatim
```
Payment of Rs 150.50 using Apay Balance successful at merchant. Updated Balance is Rs 850.00 - SMS by Juspay
```
`Your Apay Wallet balance is debited for INR 250.00 Transaction Reference Number is 123456789012 - Powered by Juspay`
- Senders `JUSPAY`, `APAY` (also seen as "AMAZON PAY"). Source: PW `TestJuspayParser.kt`.

**F69 · PhonePe · Gift-card / wallet payment**: verbatim
```
You've paid Rs.3 via PhonePe gift card to EXAMPLE HOSIERY on May 9, 2026 at 7:06:36 PM. Not you? Call us on 022-10000002. Remaining balance Rs.0.
```
- Sender `PHONPE`. Source: IBSP `phonepe/01-giftcard-payment.json`. PhonePe UPI payments from a bank account produce only the **bank's** SMS.

**F70 · HDFC PayZapp wallet**: verbatim
```
Rs.60 debited from your PayZapp Wallet on 08-10-2025 13:37:29
Bal: Rs.2440
Not you?  Report:
https://example.invalid/x or SMS BLOCKPZW …
```
`Rs.2500\ncredited to your PayZapp Wallet\nOn 08-10-2025 13:37:18\nBal:Rs.2500\nNot you? Report:https://example.invalid/y -HDFC Bank`
- Sender `PAYZAP`. Source: IBSP `payzapp/01-wallet-spend.json`, `02-wallet-load.json`. The spend sample is shown truncated after `SMS BLOCKPZW`; the rest is in the fixture.

**F71 · CRED wallet load**: verbatim
```
Update from CRED wallet: Rs.208.00 has been successfully credited to your CRED wallet. Your updated CRED wallet balance is Rs.243.00. For any queries or support, call 080-10000000.
```
- Sender `CREDWL`. Source: IBSP `cred/02-wallet-load.json`.

**F72 · slice (NESFB) · UPI from slice account**: verbatim
```
Rs. 100 sent from a/c xx2743 on 17-Jun-26 to Hussain Shaikh (UPI Ref: 616851070000). Not you? Call 08048329999 - slice
```
`Rs. 2,000 received in slice A/c xx2743 on 15-Jun-26 from NASIMUDDIN NAJAMUDDIN SHAIKH via UPI (Ref ID: 212756500000). Avl. Bal. Rs. 2,203.56 - slice`
- Senders `SLICEIT`, `SLCEIT`, `SLCBNK`. Source: PW `SliceParserTest.kt`.

**F73 · Airtel Payments Bank**: verbatim-comment
```
Airtel Payments Bank a/c is credited with Rs.20.00. Txn ID: 560992310006. Call 180023400 for help
```
`Rs. 5.00 debited from Airtel Payments Bank a/c Txn ID xxxxxxxx Bal:15.56 Call 180023400 for help`
- Sender `AIRBNK`. Source: PW `AirtelPaymentsBankParser.kt` KDoc.

**Paytm Payments Bank / Paytm wallet, MobiKwik**: **not found** (gap). Paytm Payments Bank stopped new deposits and wallet top-ups in 2024 (RBI action). Its rates page says SMS goes out for all debits except UPI under ₹100, and for credits above ₹2,500. Low priority. No MobiKwik SMS sample exists in any of the repos.

---

## 4. Reversals, refunds and failures (cross-reference plus extras)

| # | Bank | Sample | Status |
|---|---|---|---|
| F08 | SBI | `… Credited INR 9,000.00 on 22/05/22 -REVERSE ATM WDL. …` | verbatim |
| F09 | SBI | `… has credit for UPI/REVERSAL/<rrn>/0112 of Rs 2.00 …` | verbatim |
| F53 | HDFC CC | `Transaction Reversed!On HDFC Bank CREDIT Card …` / `Refund initiated: Amt: …` | verbatim |
| F56 | Kotak CC | `INR 2 from Airport Lounge refunded to your Kotak Credit Card …` | verbatim |
| F58 | IndusInd CC | `refund of INR 250 from Swiggy … credited to your IndusInd Bank Credit Card …` | verbatim |
| F61 | ICICI CC | `… has been reversed. A credit will reflect shortly.` | verbatim |

**F74 · Failed / declined (ignore as non-transactions, optionally log)**: verbatim
- Federal: `Hi, txn of Rs. 1500.00 using card XX**3456 failed due to insufficient funds. Current Bal: Rs.250.75. Call 18004251199 if txn not initiated by you -Federal Bank`
- Federal mandate: `Hi, payment of INR 199.00 via e-mandate declined for ID: NX789XYZABC on Federal Bank Debit Card 3456. …`
- HDFC: `UPI Transaction Declined for your HDFC Bank\naccount ending 1001 for security reasons.For\nassistance, Call 18001234567`
- ICICI: `Your account is not debited with Rs 500.00 towards ARJUN MEHTA for UPI Mandate due to cbs rejection 0116, RRN 512345678905-ICICI Bank.` (**contains "debited"**, so a negation check for `not debited` is needed)
- slice CC: `Your slice credit card transaction of RS. 50000 on amazon.in was declined.`
- CRED: `Payment of Rs.50000 could not be processed. Please try again later.`
- Sources: PW `TestFederalBankParser.kt`, `SliceParserTest.kt`, `CredParserTest.kt`; IBSP `hdfc/13-declined.json`; UMB `RealTemplatesTest.kt`.

---

## 5. Messages to IGNORE (all verbatim from the sources above)

| Category | Example | Why it's dangerous |
|---|---|---|
| Pre-debit / mandate notice | `E-Mandate!\nRs.3821.00 will be deducted on 02/03/26, 00:00:00\nFor Rentomojo mandate\nUMN …@ybl\nMaintain Balance\n-HDFC Bank` | has an amount and "deducted" |
| | `Your account will be debited with Rs 649.00 on 03-Oct-25 towards Netflix Entertainment Ser for AutoPay MERCHANTMANDATE, RRN 421723106963-ICICI Bank.` | "will be debited" |
| | `INR 587.64 for Airtel Payments Bank Limited will be auto-debited via Axis Bank Card no. XXxxxx by 27-07-26. …` (IDFC sender) | |
| | `Auto Pay HDFC Bank NACH Mandate : Rs. 100000.00 UMRN:… Freq ADHO received today for processing.` | huge amount |
| Mandate created/cancelled | `Your UPI-Mandate for Rs 119.00 is successfully created towards … UMN:payee1@pthdfc.` / `Your UPI-Mandate is successfully cancelled towards … for 139.00 …-SBI` | |
| Collect request | `EXAMPLE FD JOURNEY has requested Rs10000 frm u on a UPI app. Once approved, money will be debited frm ur a/c -SBI` | |
| | `You have received a collect request of Rs.500.00 from john@ybl on PhonePe.` | |
| | `PhonePe UPI Circle payment request: 1948 has requested a payment of Rs.263 to …` | |
| OTP | `123456 is your OTP for a Dr. INR 500.00 transaction on your Canara Bank card.` / `Your Amex SafeKey One-Time Password for INR 213.50, at X CORP- PAID FEATURES is 000000.` | contains "Dr." and an amount |
| Bill due (biller) | `New Bill Alert:\nYour XUBA00000TST1A Bill 1234567890 of Rs.1500.00 is due on 15-Jan-2026. …` / `Airtel Postpaid Fetch and Pay bill of INR 293.82 due on 28-09-26. …` | |
| Balance-only | `Your A/C XXXXX100002 has Avl Bal Rs 20,407.29 as on 03/12/25.-SBI` / `Your A/C 2134***12345 has Avl BAL of INR 1,234.56 as on 05/10/25 04:10 AM. …-IndusInd` | |
| EMI offer / loan marketing | `UPDATE : You can convert  your Credit Card xx0000 outstanding bill of Rs.27169.6 into SmartEMI of Rs.3282 + GST …` / `Get a pre-approved personal loan of Rs.5,00,000 …` | |
| ASBA / IPO block | `Your ASBA application for AUGMONT is received and Application value of Rs 14972 is blocked …` | lien, not debit |
| Vouchers / rewards | `You have received Amazon Shopping E-voucher Rs.500/- from HDFC Bank My Rewards …` | |
| ATM count notice | `Card ending 4321: … you have done 02 out of 06 free transactions at Canara ATMs …` | |
| SIP *registration* (vs. executed SIP, which is a real debit) | `You made a Smart Move!\nYour Monthly SIP request is registered …` vs `SIP Txn Successful!\nYour Monthly SIP for Rs.1000.0 placed on 23/05/2026 …` | |
| UPI Lite disable | `UPI LITE has been disabled. An amount of Rs 0.27 will be transferred to your Savings Account …` | own-account transfer |
| Standing-instruction setup | `We have activated Standing Instruction on ICICI Bank Credit Card 1005. Merchant: …, Maximum Amount: USD 150.00 …` | |
| Telco | `Recharge of INR 100.00 is successful for your Airtel Mobile …` / data-usage alerts | not from a bank sender |
| Spoofed sender | Any bank-format text from a `+91…` 10-digit number | phishing; whitelist DLT headers |

---

## 6. Cross-bank patterns for a generic fallback parser

1. **Normalise first.** Apply NFKC (handles SBI Card's 𝗌𝗉𝖾𝗇𝗍 Unicode letters). Collapse runs of whitespace but keep `\n` as a separator. Normalise `₹`, `Rs`, `Rs.`, `Rs:`, `Rs..`, `INR`, `INR.` to a single currency token.
2. **Amount:** `(?:Rs\.?|Rs:|INR\.?|₹)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.\d{1,3})?|\d+(?:\.\d{1,3})?)`. Allow Indian grouping (`13,50,000.00`), 1–3 decimals (`305.0`, `5.000`) and **no currency marker** after `debited by` for SBI UPI. Take the **first** amount that is not preceded by `Bal|Avl|Avbl|Available|limit|Lmt`.
3. **Direction keywords:**
   - Debit: `debited|Dr\.?|spent|Sent|paid|withdrawn|w/d|transferred from|used on|debit by transfer`.
   - Credit: `credited|Cr\.?|received|deposited|has credit for|refund(ed)?|reversed|REVERSAL|cashback`.
   - Check negation and future tense **before** these: `not debited|will be (debited|deducted)|auto-debited|due (on|by)|requested|declined|failed|could not`.
4. **Account / card tail:** `(?:A/?c|Acct?|AC|Account|Card(?: no\.)?|ending(?: with)?)\s*(?:no\.?\s*)?[:\s]*[*Xx.]+\s*(\d{3,6})`. Also try `BLOCK(?:CARD| CC| DC)? (?:XX)?(\d{4})` as a fallback (HDFC/Axis put the card only there).
5. **Counterparty:** take the first match of `to VPA (\S+@\S+)`, `trf to (.+?) Refno`, `To (.+?)\n`, `to:(.+?)\.`, `at (.+?) on`, `At (.+?) On`, `from (.+?) (?:on|via|\.)`, `; (.+?) credited`, `UPI/(?:DR|CR|P2M|P2A)/\d+/([^/\n]+)`, `@UPI_(.+?) \d{2}-`, `Info:?\s*-?\s*(.+?)\.`. A VPA (`\S+@[a-z]+`) is a strong P2P/P2M signal.
6. **Reference:** `(?:UPI Ref(?: No\.?| ID)?|Ref(?:no| No\.?|#)?|RRN|UTR(?: Ref\.?)?|IMPS Ref(?: no\.?)?|UPI)[:\s-]*([A-Z0-9]{10,22})`. UPI RRNs are 12 digits; NEFT UTRs are like `HDFCH00000000000` or `FDRLM4175007432`.
7. **Date:** support `ddMMMyy` (SBI), `dd-MMM-yy`, `dd-MM-yy`, `dd/MM/yy`, `dd-MM-yyyy HH:mm:ss`, `yyyy-MM-dd:HH:mm:ss` (HDFC cards), `ddMMMyyyy HH:mm` (Federal), `MMMM d, yyyy` and `dd MMMM yyyy` (AmEx/Federal). Fall back to the SMS received time when the date is missing or more than 2 days off (IBSP uses this rule).
8. **Balance / limit (optional):** `(?:Avl\.? ?Bal|Aval Bal|AvlBal|Avbl Amt|Total Bal|New Bal|Bal|Clear Bal|Current Bal|Final balance)\s*[:\-]?\s*(?:is\s*)?(?:Rs\.?|INR\.?|₹)?\s*([\d,]+\.?\d*)\s*(CR)?` and `(?:Avl|Avbl|Available)\.? ?(?:Limit|Lmt|credit limit)`.
9. **Channel tags:** `UPI|VPA|@` → UPI; `IMPS` → IMPS; `NEFT|RTGS|UTR` → bank transfer; `ATM|WDL|withdrawn|NFS` → ATM; `ACH|NACH|ECS|e-mandate|AutoPay|SI` → mandate; `Card … at|POS|Debit Card Purchase` → card; `SALARY|PAYROLL|SAL ` → salary; `interest` → interest.
10. **Self-transfers and card bill payments:** suppress or mark `towards PNB credit card payment`, CRED `credited towards your … Credit Card`, `Bharat Bill Payment System`, and wallet loads (PayZapp/CRED wallet) so they aren't double-counted as expense and income.
11. **Sender gating:** accept only DLT headers (`^[A-Z]{2}-[A-Za-z0-9]{6}(-[STPG])?$`, or a bare 6-char core) on a bank/card/wallet whitelist. Reject plain mobile numbers.

### Sender ID cores (from parser `canHandle` code in PW plus fixtures)
SBI: `SBIUPI SBIINB CBSSBI ATMSBI SBIPSG SBIBNK SBIBK SBIOTP(ignore) SBICRD`(card) · HDFC: `HDFCBK HDFCBANK` (+ `HDFCMF` mutual fund, `PAYZAP` wallet) · ICICI: `ICICIT ICICIB` · Axis: `AXISBK AXISBANK` · Kotak: `KOTAKB KOTAKD` · BoB: `BOBTXN BOBSMS BOBCRD BARODA` · PNB: `PNBSMS PNBBNK PUNBN PNBCCD` · Canara: `CANBNK` · Union: `UNIONB UNIONBANK` · IDFC: `IDFCBK IDFCFB` · YES: `YESBNK` · IndusInd: `INDUSB INDUSIND` · AU: `AUBANK` · Federal: `FEDBNK FEDSMS FEDSCP` · BOI: `BOIIND` · Indian Bank: `INDBNK` · Central Bank: `CENTBK` · Bandhan: `BDNSMS` · AmEx: `AMEXIN` · OneCard: `ONECRD` · Airtel PB: `AIRBNK` · Jio PB: `JIOPBS` · LazyPay: `LZYPAY` · Amazon Pay: `JUSPAY APAY` · PhonePe: `PHONPE` · CRED: `CREDIN CREDWL` · slice: `SLICEIT SLCEIT SLCBNK`.

---

## 7. Gaps and suggestions
- **No verbatim sample found:** Union Bank UPI/credit; OneCard (templates only); Axis savings UPI (regex only); YES Bank savings; HDFC / SBI Card / Axis / AmEx statement SMS; Paytm Payments Bank and Paytm wallet; MobiKwik; PhonePe wallet top-up; Kotak or Axis salary-credit wording; NACH/ECS debits for SBI, HDFC and Axis (IndusInd ACH DR and BoB ACHCR were found).
- Blocked hosts that could fill these gaps: desidime.com, technofino.in, kotak.bank.in, trenziq.web.app. Reddit was not tried. Allowing these hosts, or collecting samples from beta testers' inboxes, would help most.
- PennyWise's `ios-parity.tsv` (853 sender/body/expected rows) is the best ready-made regression corpus. **Licence warning:** PennyWise is **AGPL-3.0**, so use its samples only as a reference and write your own fixtures; do not copy its parser code into a closed-source app. IBSP and Umber are MIT.
