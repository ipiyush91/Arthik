# Indian investment / retirement / loan SMS formats: research notes

Scope: mutual funds (AMC / RTA / bank side), EPFO, NPS, FD/RD/PPF/SGB, loan EMI, insurance premium.
Compiled 2026-10-30 for an Android SMS-parser build spec.

## How to read this file

- **Confidence**
  - `verbatim`: the message text is copied exactly from a public source. Almost all of these are
    anonymised test fixtures or user-contributed issue reports in open-source Indian SMS-parser
    projects on GitHub. Digits and names were already masked by those projects, and I kept their
    masking as published. Where a fixture *looks* synthetic (for example IDs like `LI789GHI456`),
    the row says so.
  - `verbatim-fragment`: only part of the message is quoted in the source.
  - `reconstructed`: a source describes the message (when it is sent and what it contains) but does
    not quote it. **No sample text is given for these rows.** Only a field list and the trigger are
    given, so nobody codes a regex against invented wording. Capture real samples before writing
    regexes for them.
- **Sender ID core** is the 6-character DLT header between the operator/circle prefix and the
  suffix (for example `AD-HDFCMF-AC` has core `HDFCMF`). Cores marked *(observed)* appear in a
  source next to a real message. Cores marked *(unverified)* are plausible guesses that **no source
  confirmed**. Do not hard-code those. Match on message body as well.
- `\n` inside a sample means a literal line break in the original SMS.

### Research limitations (read first)

The session's network allowlist blocked most non-GitHub sites: reddit.com, scribd.com,
consumercomplaints.in, camsonline.com, hdfcfund.com, relakhs.com, technofino, freefincal, quora,
tradingqna and the Wayback Machine. GitHub (repo pages, issues, raw files) was reachable. Web search
returned summaries only, and none of them reproduced CAMS/KFintech/NPS/LIC/Bajaj SMS wording. As a
result, the verbatim coverage is strong for the **bank-side** view of investments (SIP debits,
redemption credits, FD interest) and for HDFC MF, Navi MF and EPFO. It is **weak for CAMS/KFintech
generic templates, NPS, SGB, LIC and NBFC EMIs**. Those are marked `reconstructed` and need real
samples from user inboxes or from the blocked sites once they are allowed.

### Primary sources

| Key | Source | Notes |
|---|---|---|
| S1 | https://github.com/sarim2000/pennywiseai-tracker/blob/main/parser-core/src/test/resources/ios-parity.tsv | 853 sender→SMS→expected-parse rows (Android/iOS parity fixture). Most Indian rows come from user reports and are anonymised. |
| S2 | https://github.com/sarim2000/pennywiseai-tracker/blob/main/parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/NaviMutualFundParser.kt and `HDFCMutualFundParser.kt` | Sample SMS in KDoc, plus sender IDs. |
| S3 | https://github.com/auraspend/auraspend/blob/main/app/src/test/java/com/awbuilds/auraspend/data/classification/bank/EpfoParserTest.kt (+ `EpfoParser.kt`, PR https://github.com/auraspend/auraspend/pull/62) | EPFO fixtures and sender IDs. |
| S4 | https://github.com/auraspend/auraspend/blob/main/app/src/test/java/com/awbuilds/auraspend/data/classification/bank/WaveFiveBankParsersTest.kt | HDFC MF / Navi MF fixtures (same text as S1). |
| S5 | https://github.com/Jayachandran-jayy/TU-Expense-Manager/issues/89 , /90 , /91 , /95 | User-contributed HDFC Bank SMS ("Format Contribution" issues). |
| S6 | https://github.com/sarim2000/pennywiseai-tracker/blob/main/parser-core/src/test/kotlin/com/pennywiseai/parser/core/bank/DOPBankParserTest.kt | India Post (DOP) fixtures. |
| S7 | https://github.com/TVRCloud/expense-tracker/issues/149 | NACH narration fragment naming Bajaj Finance. |
| S8 | https://github.com/ayush3224/sms-parser/pull/3 | Skip-rule list and HDFC mandate fragment. |
| S9 | NSDL/Protean CRA FAQs: https://npscra.nsdl.co.in/all-faq-contribution.php , https://www.gconnect.in/nps-2/sms-and-email-alert-facility-for-nps.html , HDFC Bank NPS page https://www.hdfc.bank.in/nps-national-pension-system | NPS alert behaviour (described, not quoted). |
| S10 | SEBI MF investor FAQ https://www.sebi.gov.in/sebi_data/faqfiles/sep-2024/1727242783639.pdf | AMCs must send units-allotted confirmation by SMS/email. |
| S11 | EPFO press release https://www.epfindia.gov.in/site_docs/PDFs/EPFO_PRESS_RELEASES/PressRelease_25042018.pdf ; PIB https://www.pib.gov.in/PressReleasePage.aspx?PRID=1523909 | EPFO credit SMS and missed-call/SMS balance service (described). |
| S12 | SBI RD page https://sbi.bank.in/web/personal-banking/investments-deposits/deposits/recurring-deposit | Maturity-instruction SMS (described). |
| S13 | jagoinvestor LIC SMS article https://www.jagoinvestor.com/2011/10/lic-details-by-sms.html (seen only through a search-engine summary) | LIC ASKLIC reply fragment. |
| S14 | Scribd "First EMI Breakup" https://www.scribd.com/document/872499185/First-EMI-Breakup (seen only through a search-engine summary) | Bajaj Finance first-EMI reminder (described). |

---

## 1. Mutual funds

### 1.1 HDFC Mutual Fund: SIP purchase processed (AMC SMS, CAMS-serviced AMC)
- **Confidence:** verbatim (S1, S2, S4)
- **Sender:** `AD-HDFCMF-AC`, `AD-HDFCMF-S`, `VM-HDFCMF` → core `HDFCMF` *(observed)*
- **Sample:**
  ```
  Your SIP Purchase in Folio 38822412/82 under HDFC Mid Cap Fund-DG for Rs. 7,000.65 has been processed at the NAV of 224.603 for 31.169 units and 24-Feb-2026. Your smart statement https://shrtsms.in/HDFCMF/xYSrbW. Enter your PAN as the password. Sincerely, HDFCMF
  ```
  (folio and link are as published in the fixture; mask to `XXXXXXXX/XX` in your own fixtures)
- **Fields:** txn type (`SIP Purchase` | presumably `Purchase`/`Redemption` variants), folio
  (`Folio (\d+/\d+)`), scheme (`under (.+?) for Rs`), amount (`for Rs\. ([\d,]+\.\d+)`),
  NAV (`NAV of ([\d.]+)`), units (`for ([\d.]+) units`), NAV date (`units and (\d{2}-[A-Za-z]{3}-\d{4})`).
- **Note:** the "-DG" suffix means Direct Growth. The amount looks like the net amount after 0.005% stamp duty, so it does not equal the bank debit exactly (7,000.65 fits a ₹7,001 debit). PennyWise's parser also keys on the word `redemption` for the same sender, but no redemption sample is published.

### 1.2 Navi Mutual Fund: SIP purchase processed, units pending
- **Confidence:** verbatim (S1, S2, S4). Two variants.
- **Sender:** `AD-NAVAMC-S`, `VK-NAVAMC-T` → core `NAVAMC` *(observed)*. Do not match generic `NAVI` senders, which belong to Navi's lending/UPI arm.
- **Sample A:**
  ```
  Unit Allotment Update:\nYour SIP purchase of Rs.499.98 in Navi Nifty Next 50 Index Fund DG has been processed at applicable NAV. The units will be alloted in 1-2 working days. For further queries, please visit the Navi app.\nTeam Navi Mutual Fund
  ```
- **Sample B:**
  ```
  Unit Allotment Update:\nYour SIP purchase of Rs. 1,49,999.98 in Navi Largecap Fund DG has been processed at applicable NAV. The units will be alloted in 1-2 working days.\nTeam Navi Mutual Fund
  ```
- **Fields:** amount (`purchase of Rs\.?\s*([\d,]+(?:\.\d+)?)`; note Indian lakh grouping), scheme (`in (.+?) has been processed`). No NAV, units or folio are given, so model this as a "pending allotment" state and expect a later statement.
- Note the spelling "alloted" (sic). Keep regexes tolerant of it.

### 1.3 Navi MF NAV update (IGNORE)
- **Confidence:** verbatim fixture (S1, S4). It may be synthetic, since the NAV is the round-looking `12.3456`.
- **Sender:** `AD-NAVAMC-S`
- **Sample:** `NAV updated for Navi Largecap Fund. Latest NAV: 12.3456. Visit the Navi app for details.`
- **Action:** ignore. The fixture's expected parse is `NONE`.

### 1.4 Bank-side: MF purchase debited via NEFT (Federal Bank → Nippon India MF)
- **Confidence:** verbatim (S1)
- **Sender:** `VM-FEDBNK-S` → core `FEDBNK` *(observed)*
- **Sample:**
  ```
  Nippon India Mutual Fund has received Rs 1000.00 from your A/c 3363 via NEFT on 02-Jan-2026 06:50:32. Ref no. FBBT260023158681 - Federal Bank
  ```
- **Fields:** AMC name (`^(.+? Mutual Fund) has received`), amount, account last-4, channel (NEFT), datetime, ref no.
- **Note:** this is the bank's message for a lump-sum or SIP payment to an AMC. It duplicates the AMC allotment SMS, so dedupe on amount and date (±3 days).

### 1.5 Bank-side: MF SIP paid via HDFC BillPay (AxisMF / ICICIPruMF / MiraeAssetMF)
- **Confidence:** verbatim (S5: issues #89, #90, #91; user-contributed, account and ref masked by the reporter)
- **Sender:** not given in the issues. HDFC Bank's usual core is `HDFCBK` *(observed elsewhere in S1 for HDFC Bank)*.
- **Samples:**
  ```
  Bill Paid!\nAxisMF Bill XXXXXX-XXXXXX of Rs. 500.00 paid on 05-Oct-2026 from HDFC Bank Account XXXXXXXXXXXXXX.\nBillPay Ref: XXXXXXXXXXXXXX
  ```
  ```
  Bill Paid!\nICICIPruMF Bill XXBROKER-XXXXXX of Rs. 1000.00 paid on 07-Oct-2026 from HDFC Bank Account XXXXXXXXXXXXXX.\nBillPay Ref: XXXXXXXXXXXXXX
  ```
  ```
  Bill Paid!\nMiraeAssetMF Bill XXXXXX-XXXXXX of Rs. 1000.00 paid on 12-Oct-2026 from HDFC Bank Account XXXXXXXXXXXXXX.\nBillPay Ref: XXXXXXXXXXXXXX
  ```
- **Fields:** biller (`^(\w+MF) Bill`, which maps to the AMC), biller ref (often the folio or SIP reg no.), amount, date, BillPay ref.
- **Note:** the biller token is the AMC's BillDesk/BBPS biller name. Keep a lookup table (`AxisMF`, `ICICIPruMF`, `MiraeAssetMF`, …).

### 1.6 Bank-side: SIP debit via ACH/NACH to a platform (IndusInd → Groww)
- **Confidence:** verbatim (S1)
- **Sender:** `AD-INDUSIND-S` → core `INDUSIND`/`INDUSB` *(observed; IndusInd uses both)*
- **Sample:**
  ```
  IndusInd A/C  Debited; INR 4,500.00 Ref-ACH DR INW PAY/0000WD2CEFDT2Z58B2202320321456/Grow.Bal INR 141,999.93.Dispute-Call 18602677777-IndusInd Bank.
  ```
- **Fields:** amount, narration (`ACH DR INW PAY/<UMRN-ish>/<payee>`), payee token (`Grow` is truncated "Groww"), balance.
- **Note:** narration payee tokens seen for MF/platform SIPs typically include Groww, Zerodha/BSE, ICCL (Indian Clearing Corp, used for BSE StAR MF) and NSE Clearing (used for NSE NMF/Coin). Only `Grow` and `NationalSecuritiesClearin` (1.8) are observed in sources.

### 1.7 Bank-side: MF redemption credited (NEFT / RTGS)
- **Confidence:** verbatim (S1). Two banks.
- **Sample A (Bank of India, `JX-BOIIND-S` → `BOIIND`):**
  ```
  BOI - Rs 15,000.00 Credited in your Ac XX5468 on 04-02-2026 By NEFTINWARD HDFCH00778553836/HDFC MUTUAL F .Avl Bal 18679.91
  ```
- **Sample B (Canara Bank, `VA-CANBNK-S` → `CANBNK`):**
  ```
  An amount of INR 13,30,614.75 has been credited to XXXX6785 on 02/12/2025 towards RTGS by Sender AXIS MUTUAL FUND REDEMPTION PO, IFSC UTIB0000004, Sender A/c XXXX9108, AXIS BANK, MUMBAI BRANCH, UTR UTIBR72025120200011461, Total Avail. Bal INR 2679815.88- Canara Bank
  ```
- **Fields:** amount, payer (`MUTUAL F(UND)?( REDEMPTION)?`), UTR, balance.
- **Classification:** redemption proceeds or IDCW payout. The bank SMS cannot tell them apart. Treat any credit whose payer matches `MUTUAL F|MF |AMC|REDEMPTION` as an MF inflow and reconcile against the AMC SMS.

### 1.8 Bank-side: NACH mandate registered for an exchange MF platform (IGNORE as txn)
- **Confidence:** verbatim (S1)
- **Sender:** `VM-HDFCBK-S` → `HDFCBK`
- **Sample:**
  ```
  Auto Pay HDFC Bank NACH Mandate : Rs. 100000.00 UMRN:HDFC7031703262015557 To:NationalSecuritiesClearin Freq ADHO received today for processing.
  ```
- **Fields (for a mandate registry, not a ledger):** max amount, UMRN, beneficiary (`NationalSecuritiesClearin` = NSE Clearing Ltd, as used by NSE NMF II / Zerodha Coin), frequency (`ADHO` = as and when presented).
- **Action:** do not book a transaction. Optionally store the mandate so you can link future debits by UMRN.

### 1.9 Bank-side: Debit Mandate via Razorpay (possible SIP/insurance autopay)
- **Confidence:** verbatim (S5 #95)
- **Sample:**
  ```
  Debit Alert! Your HDFC Bank A/c xxxxxx has been debited Rs 1117 on 06th Oct' 2026 via Debit Mandate for RAZORPAY_XXXXXX_XXXXX
  ```
- **Fields:** amount, date (`06th Oct' 2026` has an ordinal and an apostrophe-year), mandate payee.
- **Note:** the payee is the payment aggregator, not the AMC or insurer. The category is unknown without user tagging.

### 1.10 CAMS-serviced AMC: purchase / SIP allotment (generic)
- **Confidence:** reconstructed (S10 says AMCs must SMS the units allotted; CAMS FAQ says a "request confirmation… SMS will be sent to your registered mobile number").
- AMCs serviced by CAMS include HDFC, ICICI Pru, SBI, Aditya Birla SL, Kotak, DSP, Franklin, Tata, Bandhan, PPFAS and others. The one CAMS-AMC sample found is HDFC (1.1). Wording differs per AMC because the AMC owns the DLT template.
- **Expected fields:** folio, scheme/plan/option, txn type (Purchase / SIP Purchase / Additional Purchase / NFO), amount, NAV, NAV date, units.
- **Sender cores (unverified):** AMC-specific, e.g. `SBIMF`, `ICICIP`, `PPFAS`, `KOTAKM`, `BSLAMC`. CAMS itself has been reported as `CAMSPL`/`CAMSMF`, but no source confirmed either.

### 1.11 KFintech-serviced AMC: purchase / SIP allotment (generic)
- **Confidence:** reconstructed (no source quoted any KFintech SMS. KFintech publishes an "Easy SMS Services Guide" at https://mfs.kfintech.com/investor/General/ServicesAndDownloads, which was not reachable).
- AMCs serviced by KFintech include Axis, Mirae, Nippon, UTI (UTI uses KFin since 2021), Quant, Motilal Oswal, Edelweiss, Invesco and others.
- **Expected fields:** same as 1.10.
- **Sender core:** `KFINTK` / `KFINTC` *(unverified)*.

### 1.12 Redemption processed (AMC side)
- **Confidence:** reconstructed. PennyWise's HDFCMF parser treats `redemption` as income, which implies a redemption SMS exists from `HDFCMF`, but no text is published.
- **Expected fields:** folio, scheme, units redeemed or amount, NAV, NAV date, payout bank last-4, expected credit date.

### 1.13 Switch (switch-out / switch-in)
- **Confidence:** reconstructed (no source).
- **Expected fields:** folio, from-scheme, to-scheme, units/amount, NAV(s). Book as an internal transfer (no cash movement).

### 1.14 IDCW (dividend) payout / reinvestment
- **Confidence:** reconstructed (no source).
- **Expected fields:** folio, scheme, rate per unit, amount, reinvest NAV and units (if reinvest), or a bank credit (if payout). A payout also arrives as a bank NEFT credit like 1.7.

### 1.15 MF Central / BSE StAR MF / NSE NMF II order and allotment
- **Confidence:** reconstructed (no source). The only observed artefacts are the bank-side mandate (1.8) and the ACH debit (1.6).
- **Expected:** order placed (IGNORE until allotted), payment received, units allotted. Platforms (Groww, Zerodha Coin, Kuvera, Paytm Money) also send their own SMS. Treat those as secondary to the AMC/RTA allotment to avoid triple counting (platform + AMC + bank).

---

## 2. EPFO

### 2.1 Monthly contribution received + passbook balance
- **Confidence:** verbatim (S3, anonymised fixture. PR #62 confirms the fragment "...Contribution of Rs X/- for due month ... has been received" from real messages)
- **Sender:** `VA-EPFOHO-G`, `AX-EPFOHO-S` → core `EPFOHO` *(observed)*
- **Sample:**
  ```
  Dear XXXXXXXX41 your passbook balance against PYKRP1234567 is Rs. 1,23,456.78. Contribution of Rs. 3,600/- for due month Jun-26 has been received.
  ```
- **Fields:** masked UAN (`Dear (X+\d+)`), member/establishment ID (`against ([A-Z]{5}\d+)`, the PF account number prefix of region + office + establishment code), passbook balance (`balance against \S+ is Rs\. ([\d,]+\.\d+)`), contribution (`Contribution of Rs\. ([\d,]+)/-`), due month (`due month ([A-Za-z]{3}-\d{2})`).
- **Gotchas (from S3):** two amounts appear and the **balance comes first**, so pin the contribution clause. The due month is a period, not the transaction date, so use SMS arrival time. The amount can include `/-`. A degenerate variant with `Rs. --` exists (`Dear Member, contribution of Rs. -- for due month Jun-26 has been received.`), and the parser must not crash on it. The contribution figure is typically the **employee share** (e.g. 12% of a ₹15k wage ceiling = 1,800, or 3,600 for the combined share). Do not assume which share it is.

### 2.2 EPFO OTP (IGNORE)
- **Confidence:** verbatim (S3 fixture)
- **Sample:** `Dear Member (UAN : XXXX XXXX 4242), OTP to login to Member Interface is 123456 (valid for 10 mins) at EPFO.`

### 2.3 UAN linked by establishment (informational; IGNORE or store employer)
- **Confidence:** verbatim (S3 fixture; the employer name is fictional)
- **Sample:** `Your UAN 101234567890 has been linked by establishment ACME SOLUTIONS PVT LTD with date 01/07/2026.`
- **Fields (optional):** employer name, date of joining. These are useful for labelling the PF account.

### 2.4 EPFO balance reply (missed call to 9966044425 / SMS `EPFOHO UAN ENG` to 7738299899)
- **Confidence:** reconstructed (S11 and several guides: the reply contains UAN, name, DOB, KYC seeding status (PAN/Aadhaar/bank), last contribution and total PF balance).
- **Action:** parse the balance only as a snapshot (not a transaction). The sender is likely also `EPFOHO` *(unverified)*.

### 2.5 Interest credit, claim settlement, withdrawal
- **Confidence:** reconstructed / possibly absent. Business Standard (FY25) reported no official SMS for interest credit, and subscribers saw it only in the passbook. No claim-settlement text was found. Claim payouts will show up as bank NEFT credits (narration typically names EPFO / the regional office).

---

## 3. NPS (Protean/NSDL CRA, KFintech CRA, CAMS CRA)

### 3.1 Contribution credited / units allotted
- **Confidence:** reconstructed (S9: "On every successful credit of units to the subscriber account an email and SMS alert is sent". Alerts arrive "a day after the units get credited". Separate Tier I / Tier II. Units are invested T+2 from trustee-bank receipt. The PRAN is shown masked (last 4 digits) in alerts).
- **Expected fields:** masked PRAN, tier (I/II), contribution amount, credit/settlement date, possibly units and NAV per scheme (E/C/G/A). Employer (corporate/govt) contributions are batched monthly.
- **Sender core:** `NPSTRX`/`NSDLCR`/`PROTEN`/`KFINPS` *(all unverified)*.

### 3.2 Payment received by POP/eNPS (pre-allotment)
- **Confidence:** reconstructed. eNPS/D-Remit/bank POP send a payment acknowledgement before units are credited. Treat it as pending and dedupe with 3.1 and the bank debit.

### 3.3 PRAN generated (IGNORE as txn)
- **Confidence:** reconstructed (S9: PRAN-generation alert shows last 4 digits of PRAN).

### 3.4 NPS balance reply
- **Confidence:** reconstructed (an SMS keyword service is reported in third-party guides, e.g. `NPSBAL <PRAN>` to 9212993399. The keyword and number are **unverified**). Snapshot only.

---

## 4. Deposits: FD / RD / PPF / SGB

### 4.1 FD (term deposit) interest paid: IndusInd
- **Confidence:** verbatim (S1). Two samples.
- **Sender:** `AD-INDUSIND-S`, `JM-INDUSB-S` → cores `INDUSIND`, `INDUSB` *(observed)*
- **Samples:**
  ```
  Net interest INR 248.07 paid on your IndusInd Deposit No 300***123456 on 17/09/25. Call 18602677777 for assistance - IndusInd Bank
  ```
  ```
  Net interest INR 96.61 paid on your IndusInd Deposit No 371***060020 on 30/06/25. Call 18602677777 for assistance - IndusInd Bank
  ```
- **Fields:** net interest (post-TDS), masked deposit no., date.
- **Note:** PennyWise's expected parse is `NONE`, because the interest separately appears as a savings-account credit. Record it on the FD holding, and do not double-book it as income.

### 4.2 Savings/deposit interest credit (bank account)
- **Confidence:** verbatim (S1)
- **IDFC First (`BM-IDFCBK-S`):** `Your A/C XXXXXXX1234 is credited by INR 125.50 on 01/01/25 for monthly interest. New Bal :INR 15125.50`
- **Bandhan (`XY-BDNSMS-S`):** `Dear Customer, your account XXXXXXXXXX1234 is credited with INR 3.00 on 01-OCT-2025 towards interest. Bandhan Bank`
- **Use:** generic "interest income". These are SB interest, not FD.

### 4.3 India Post (DOP): account credit/debit (covers PPF, RD, SB, TD, SSY, SCSS)
- **Confidence:** verbatim (S6 fixtures)
- **Sender:** `VM-DOPBNK-G`, `BZ-DOPBNK-G`, `BV-DOPBNK-S`, `BT-DOPBNK-G` → core `DOPBNK` *(observed)*
- **Samples:**
  ```
  Account  No. XXXXXXXX1234 CREDIT with amount Rs. 5550.00 on 02-03-2026. Balance: Rs.40000.00. [S76543210]
  ```
  ```
  Account No. XXXXXXXX5678 DEBIT with amount Rs. 2000.00 on 15-03-2026. Balance: Rs.18000.00. [D12345678]
  ```
- **Fields:** account last-4, CREDIT|DEBIT, amount, date, balance, bracketed ref.
- **Note:** note the double space in `Account  No.` (sic). The message **does not name the scheme**. The recurring 5550.00 monthly credits in the fixture suggest an RD/PPF-type deposit, so let the user map each DOP account number to a scheme. A third-party explainer (moneymoksh) says DOPBNK covers PPF/SSY/SCSS interest credits too.

### 4.4 Bank FD/RD booked, matured, renewed, RD instalment debited
- **Confidence:** reconstructed. SBI's RD page says SBI sends an "intimation… by way of SMS calling for instructions on disposal of maturity proceeds", and auto-renews for the same period if there is no instruction (S12). No bank's FD booking/maturity SMS text was found.
- **Expected fields:** deposit no. (masked), principal, rate, tenure, maturity date, maturity amount, linked account. For an RD instalment: amount, instalment no., due date.
- **Bank-side signal you will definitely see:** a debit from SB with narration such as "TRF TO FD/RD/TD …" or "Deposit A/c …". These are internal transfers, not expenses.

### 4.5 PPF deposit at a bank (SBI/ICICI/HDFC etc.)
- **Confidence:** reconstructed (no bank PPF SMS found). It usually appears as an SB debit/transfer whose narration names PPF, plus (for some banks) a separate credit SMS on the PPF account. Map it to an internal transfer.

### 4.6 Sovereign Gold Bonds: allotment and semi-annual interest
- **Confidence:** reconstructed. Sources say allotment confirmation comes as an **email** and demat credit (NSDL/CDSL), not a documented SMS. Interest is 2.5% p.a. paid half-yearly to the linked bank account. Expect a bank NEFT credit whose payer is RBI/the receiving bank. No narration text was found. Note that new SGB issuance stopped in 2024, so expect only interest and maturity/redemption credits.

---

## 5. Loans (EMI) and insurance

### 5.1 EMI upcoming-debit reminder (IGNORE as txn)
- **Confidence:** verbatim fixture (S1; the short text may be simplified)
- **Sender:** `AX-ICICIT-S` → `ICICIT` *(observed)*
- **Sample:** `Rs. 500.00 will be debited from your account on 05-Oct-25 for EMI payment`
- **Action:** future-tense "will be debited". Ignore, or store as a due-date reminder.

### 5.2 EMI debited from bank (auto loan via bill pay)
- **Confidence:** verbatim (S1, S4)
- **Sample:** `ICICI Bank Acc XX342 debited Rs. 8,927.00 on 07-Jun-26 InfoBIL*Auto Loan.Avl Bal Rs. 1,248.78.To dispute call 18002662 or SMS BLOCK 342 to 9215676766`
- **Fields:** amount, account, narration `InfoBIL*<biller>` (ICICI bill-pay), balance.

### 5.3 EMI/loan transfer from bank (Bank of Baroda)
- **Confidence:** verbatim (S1)
- **Sender:** `VM-BOBTXN-S` → `BOBTXN` *(observed)*
- **Sample:** `Rs.29 transferred from A/c ...5494 to:Loan Recovery Fo. Total Bal:Rs.24898.57CR. Avlbl Amt:Rs.24898.57(04-11-2025 04:03:09) - Bank of Baroda`
- **Note:** "Loan Recovery Fo[r]" is the bank's internal loan-recovery narration. Treat it as an EMI.

### 5.4 Loan account credited by recovery (loan-side view; Kerala Bank)
- **Confidence:** verbatim (S1)
- **Sender:** `VM-KELBNK-S` → `KELBNK`
- **Sample:** `Dear Customer Your A/c no XXXX0024 is credited with 15000.00 on 06-06-2026 by Loan Recovery From : 139451061. Balance is -579822.00 - Kerala Bank`
- **Note:** a negative balance means a loan account. Record it as a repayment that reduces the outstanding, not as income.

### 5.5 NBFC EMI collected via NACH (Bajaj Finance etc.)
- **Confidence:** verbatim-fragment (S7): narration `NACH-ACH-DR BAJAJ FINANCE`
- **Also:** S8 reports HDFC `PAYMENT ALERT ... UMRN` mandate alerts duplicating a PNB loan EMI ACH debit. Dedupe mandate alerts against the actual ACH debit.
- **Fields:** amount and lender from the bank debit narration (`ACH[- ]DR.*?(BAJAJ FINANCE|HDB FINANCIAL|TATA CAPITAL|…)`).

### 5.6 NBFC EMI due reminder / EMI received / EMI bounce (Bajaj Finance, HDB, Tata Capital)
- **Confidence:** reconstructed. S14 describes a Bajaj Finance first-EMI reminder with EMI amount (₹2,250), a one-time convenience fee (₹300), total (₹2,550) and due date. Bajaj Finserv says an SMS and email confirmation are sent after an online EMI payment. No bounce text was found.
- **Expected fields:** loan a/c (masked), EMI amount, due date, (bounce) charges, payment link.
- **Sender cores (unverified):** `BAJAJF`/`BFLSMS`, `HDBFSL`, `TATACP`. Gist https://gist.github.com/abhishekjnvk/0a2d2ae19ba566354ada96048458434b confirms that "BAJAJ FINANCE LIMITED" and "HDB FINANCIAL SERVICES LIMITED" hold DLT registrations, but only with numeric IDs.

### 5.7 Insurance premium paid via card e-mandate (Federal Bank)
- **Confidence:** verbatim fixture (S1), **probably synthetic**. The mandate IDs `LI789GHI456` and `NX789XYZABC` follow a pattern, but the template matches the real Federal Bank Netflix/Spotify rows.
- **Sender:** `AD-FEDBNK`
- **Sample:** `Hi, payment of INR 2500.00 for LifeInsurance via e-mandate ID: LI789GHI456 on Federal Bank Debit Card 1234 is processed successfully. To manage, visit: https://www.sihub.in/managesi/federal T&CA - Federal Bank`
- **Declined variant (IGNORE):** `Hi, payment of INR 199.00 via e-mandate declined for ID: NX789XYZABC on Federal Bank Debit Card 3456. To manage, visit: https://www.sihub.in/managesi/federal T&CA - Federal Bank`

### 5.8 LIC premium due / received / ASKLIC reply
- **Confidence:** verbatim-fragment for the query reply only (S13, via search summary): `Premium for policy is Rs 1516.0` (reply to `ASKLIC <policy no> PREMIUM` sent to 9222492224). Premium-due and premium-received alerts are **reconstructed** (no text found).
- **Expected fields:** policy no. (masked), premium amount, due date / paid date, FUP (first unpaid premium) date, receipt no.
- **Sender core:** `LICIND`/`LICHFL` *(unverified; LICHFL is LIC Housing Finance, not insurance. Do not conflate them)*.

---

## 6. Message types to IGNORE (no ledger entry)

| Pattern | Example source | Reason |
|---|---|---|
| OTPs from EPFO/CAMS/KFin/MF Central/NPS/insurers | 2.2 (verbatim) | Not a transaction |
| `NAV updated…` / daily NAV broadcasts | 1.3 (verbatim) | Informational |
| NACH/e-mandate **registration** ("Mandate … received today for processing", "auto pay facility has been successfully activated") | 1.8 (verbatim); PNB autopay row in S1 | Store the mandate optionally; no money moved (PNB's ₹2 verification debit is real but trivial) |
| Future-tense debits ("will be debited", "AutoPay: Rs X will be debited on…") | 5.1 (verbatim) | Reminder; the actual debit SMS follows |
| e-mandate **declined** / UPI failed / reversed | 5.7 (verbatim) | No net movement (but track bounces separately for loans) |
| FD interest SMS when the SB credit SMS also arrives | 4.1 | Avoid double-counting; attach to the FD holding |
| Marketing: "offers 7% interest on fixed deposits", "Opted for EMI? … to cancel", insurance quotations, NFO promos, pre-approved loan offers | S1 (`XY-BDNSMS-S` FD promo verbatim; Kotak "Opted for EMI?" verbatim) | Promotional (often a `-P` DLT suffix) |
| UAN linked by establishment / KYC approved / PRAN generated / folio created / SIP registered | 2.3 (verbatim), 3.3 | Account lifecycle, not money |
| EPFO / NPS / MF balance-query replies and missed-call valuations | 2.4, 3.4 | Snapshots; store as balance, not txn |
| SIP **due** reminders ("your SIP of Rs X is due on…") | S8 skip list | Pre-debit notice |
| CDSL/NSDL e-voting, NSE/BSE fund-balance and trade confirmations | S8 skip list | Out of scope / duplicates broker data |
| Platform (Groww/Zerodha/Kuvera) "order placed" messages | 1.15 | Duplicates AMC allotment + bank debit |

## 7. Dedupe model (recommended)

One SIP instalment can produce up to four SMS: (a) a bank ACH/NACH or BillPay debit (1.5, 1.6), (b) a platform order notice, (c) an AMC "processed/allotment pending" message (1.2), and (d) an AMC allotment with NAV/units (1.1). Book cash movement from (a) and units/NAV from (d), and link them on amount and a date window. Allow a small tolerance, because the AMC amount is net of 0.005% stamp duty: 7,000.65 in 1.1 is consistent with a ₹7,001 debit less ₹0.35, and 499.98 in 1.2 with a ₹500 debit. Use of T to T+5 working days. The same applies to EPF (single SMS), NPS (bank debit + CRA credit) and loans (mandate alert + ACH debit, S8).

## 8. Sender-ID cores summary

| Core | Institution | Status |
|---|---|---|
| `HDFCMF` | HDFC Mutual Fund | observed |
| `NAVAMC` | Navi Mutual Fund | observed |
| `EPFOHO` | EPFO | observed |
| `DOPBNK` | India Post (POSB, PPF, RD, etc.) | observed |
| `HDFCBK`, `ICICIT`, `FEDBNK`, `BOIIND`, `CANBNK`, `INDUSIND`/`INDUSB`, `IDFCBK`, `BDNSMS`, `BOBTXN`, `KELBNK` | Banks (bank-side investment and loan messages) | observed |
| `CAMSPL`, `CAMSMF`, `KFINTK`, `SBIMF`, `ICICIP`, `PPFAS`, `NPSTRX`, `LICIND`, `BAJAJF`, `HDBFSL`, `TATACP` | CAMS, KFintech, AMCs, NPS CRA, LIC, NBFCs | **unverified guesses**. Use body keywords (`Folio`, `NAV`, `units`, `PRAN`, `Policy`, `Loan A/c`, `EMI`) as the primary routing signal |

## 9. Gaps / next steps

1. **CAMS and KFintech generic allotment, redemption, switch and IDCW templates (1.10–1.14):** no verbatim text. Collect from r/IndiaInvestments, Technofino, the KFintech "Easy SMS Services Guide" PDF, or volunteers' inboxes.
2. **NPS CRA SMS (all of section 3):** no verbatim text. Check Protean/KFin CRA subscriber FAQs and inboxes.
3. **Bank FD/RD booking, maturity and renewal, PPF at banks, SGB interest narration (4.4–4.6):** no verbatim text.
4. **NBFC EMI due/paid/bounce and LIC premium due/paid (5.6, 5.8):** no verbatim text beyond fragments.
5. **Sender IDs** for CAMS, KFintech, NPS, LIC and NBFCs are unconfirmed.
6. Domains to allow for a second pass: reddit.com, scribd.com, consumercomplaints.in, camsonline.com, mfs.kfintech.com, npscra.nsdl.co.in, hdfcfund.com, technofino.in, freefincal.com, jagoinvestor.com, relakhs.com.
