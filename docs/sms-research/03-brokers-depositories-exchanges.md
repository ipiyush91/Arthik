# Indian Broker / Depository / Exchange SMS Formats: Research Notes

Researched 2026-10-30 for an Android SMS-parser build spec.

## Method and limits (read first)

- **Network limits.** This session could not open most sites directly. Direct fetches of tradingqna.com, support.zerodha.com, nseindia.com, cdslindia.com, broker sites, reddit and huggingface were blocked by the session's network allowlist. GitHub code search was also blocked (403). Only `raw.githubusercontent.com` was reachable.
- **How the evidence was collected.** It comes from two places:
  1. Text quoted by the web-search index from the named source pages. These are snippets of those pages, not full-page reads.
  2. Test fixtures in the public GitHub repo `sarim2000/pennywiseai-tracker` (a parser-core module with Kotlin tests and `ios-parity.tsv`), read locally.
- **Confidence labels:**
  - `verbatim`: the message text, or a contiguous fragment of it, is quoted in the source. Fragments are marked `verbatim (fragment)`.
  - `illustrative`: a broker help page shows a made-up sample. It is not a captured real message.
  - `reconstructed`: the source describes the message's content but does not quote it. **No template is given for these. Do not write regexes from them until a real sample is captured.**
- **Masking.** Masked values in the samples (e.g. `DHPXXXXX5Q`, `*xxxxxxxx`) were already masked in the source. Any other personal values have been replaced with `XXXX`.
- **Before writing regexes,** confirm every format against real device inbox samples. Several sources are years old: the CDSL T-bill sample is from 2019 and the BSE balance thread from 2017.

---

## 1. Depositories (CDSL / NSDL)

### F1. CDSL: debit of securities, redemption or maturity (T-bill)
- **Institution / type:** CDSL, securities DEBIT alert
- **Sample (verbatim):**
  ```
  CDSL: DEBITED IN A/C *xxxxxxxx 100 TREASURE BILL OF GOVERNMENT OF INDIA TOWARDS REDEMPTION ON 18/01/2019. CONTACT YOUR DP FOR MORE INFORMATION.
  ```
- **Fields:** masked BO account tail (`*xxxxxxxx`), quantity (`100`), security name (free text, upper case; note CDSL's own typo "TREASURE"), reason (`TOWARDS <REASON>`), date (`dd/MM/yyyy`)
- **Sender core:** CDSL. The exact header was not confirmed in any source. Candidates seen in the wild include `CDSLIN` and `CDSLEA`, but treat both as **unverified**. Match on body prefix `CDSL:` too.
- **Source:** https://support.zerodha.com/category/mutual-funds/government-securities/articles/tbill-maturity-sms-cdsl (also https://erp.jainam.in/G-%20Sec.Maturity-related%20questions/why-was-an-sms-received-from-cdsl-informing-debit-of-t-billsall-when-a-sell-order-was-not-placed)
- **Confidence:** verbatim (2019 sample)
- **Suggested regex skeleton:** `CDSL:\s*DEBITED IN A/C\s*\*?(\w+)\s+([\d,]+)\s+(.+?)\s+TOWARDS\s+(.+?)\s+ON\s+(\d{2}/\d{2}/\d{4})`

### F2. CDSL: debit of securities, corporate action (capital reduction)
- **Sample (verbatim fragment):**
  ```
  DEBITED IN A/C * PURSUANT TO REDUCTION OF SHARE CAPITAL FOR 1000 SHARES OF SUBEX LIMITED
  ```
- **Fields:** reason (`PURSUANT TO ...`), quantity, company
- **Note:** A credit under a new or temporary ISIN arrives alongside it. The parser should treat this pair as a corporate action, not a sale.
- **Source:** https://tradingqna.com/t/cdsl-sms-related-to-subex/93704
- **Confidence:** verbatim (fragment; the start and end of the SMS are not shown)
- **Spec note:** The debit body uses either `FOR <qty> SHARES OF <name>` or `<qty> <name> TOWARDS ...`. Handle both orderings.

### F3. CDSL: debit after an ordinary on-market sale (earmark / pay-in)
- **Institution / type:** CDSL, securities DEBIT after a sell (settled T+1)
- **Sample:** none found. Sources say CDSL "sends a message whenever there is a debit of shares, mutual fund or G-sec holdings" and sends "email and SMS containing details of debits and credits on the T+1 day when your transactions are settled". Each earmark is debited separately, so selling 60+60+5+2 can produce 4 debits.
- **Fields expected:** BO tail, qty, security, date, reason
- **Sources:**
  - https://support.zerodha.com/category/your-zerodha-account/transfer-of-shares-and-conversion-of-shares/articles/email-from-cdsl
  - https://tradingqna.com/t/demat-transaction-charges/173219
  - https://www.quora.com/I-got-a-message-from-cdsl-saying-I-have-a-debt-in-my-account-for-the-shares-which-I-have-sold-Can-anyone-explain-what-it-mean-by-debt-in-cdsl
- **Confidence:** reconstructed. It most likely uses the same `CDSL: DEBITED IN A/C ...` family as F1.

### F4. CDSL: credit of securities (IPO allotment, bonus, corporate action)
- **Sample:** none found. CDSL's FAQ says BOs registered for SMART get alerts "for all types of debits and for credits due to IPO allotments and corporate actions". Zerodha says "CDSL will send you an SMS once your bonus shares have been credited", initially under a temporary ISIN. Ordinary market-purchase credits are **not** SMS-alerted ("CDSL sends messages on debits and not credits").
- **Fields expected:** BO tail, qty, security/ISIN, reason (ALLOTMENT / BONUS / CORPORATE ACTION), date
- **Sources:**
  - https://www.cdslindia.com/Footer/Smart.html
  - https://www.cdslindia.com/downloads/Investors/FAQs/08%20Demat%20CDSL%20Way%20-%20VIII%20-%20Corporate%20actions%20%20IPO%20%20-%202024.pdf
  - https://support.zerodha.com/category/console/portfolio/console-holdings/articles/why-are-the-bonus-shares-not-visible-in-my-holdings-even-though-the-company-has-issued-it
  - https://tradingqna.com/t/fraudlent-debits-from-my-cdsl-demat-account/7018
- **Confidence:** reconstructed. The likely form is `CDSL: CREDITED IN A/C ...`, but no source confirms it.

### F5. CDSL: block / early pay-in / UCC mapping notification
- **Type:** informational. CDSL validates transfer instructions against Clearing Corporation obligations, and sends SMS/email about early pay-in and UCC mapping. CDSL circular DP-752 uses the transaction remark `PAY IN THROUGH SMS`.
- **Sources:**
  - https://support.zerodha.com/category/your-zerodha-account/your-profile/mail-queries/articles/block-mechanism-for-payin-of-securities
  - https://tradingqna.com/t/meaning-of-the-message-received-from-cdsl/139194
  - https://www.cdslindia.com/downloads/DP/DP-752-Trading-supported-by-blocked-amount-Secondary-market.pdf
- **Confidence:** reconstructed
- **Parser action:** Record it as an event at most. **Do not count it as a holdings change.** The F3 debit is the settlement event.

### F6. NSDL: securities blocked for early pay-in (block mechanism)
- **Type:** NSDL, sale block. Shares stay in the client's demat account under a lien in favour of the clearing corporation, and are unblocked end of T-day if the trade does not execute. The alert names the date, quantity and share, and says the shares are "blocked ... for early pay-in". It is sent from the NSDL DP.
- **Sources:**
  - https://support.rupeezy.in/support/solutions/articles/21000004989-why-did-nsdl-send-me-an-sms-and-email-about-blocking-my-securities-for-pay-in-
  - https://investor.nsdl.com/portal/en/kb/articles/faqs-on-block-mechanism
- **Confidence:** reconstructed. The broker page only paraphrases the sample.

### F7. NSDL: debit / credit alert (SMS Alert facility)
- **Coverage:** alerts for all debits, credits for IPO / sub-division / bonus, and failed or overdue instructions. Debits are listed per ISIN for up to 5 ISINs a day; beyond that, a single "debits for more than five ISINs" message.
- **Sample (illustrative, third-party blog on an off-market transfer):** `A/c [ID] debited with Share name.`
- **Sources:**
  - https://nsdl.com/investor-services-sms-alert
  - https://nsdl.co.in/termsandconditions.php
  - https://unlistednetwork.com/blog/transferring-the-shares-from-nsdl-to-any-demat-account/
- **Confidence:** reconstructed / illustrative
- **Spec note:** Handle the "more than five ISINs" aggregate message as an event with no per-security detail.

---

## 2. Exchanges (NSE / BSE / MCX)

### F8. NSE: end-of-day traded value SMS (SEBI trade alert)
- **Institution / type:** NSE, daily trade alert. It is sent after market close to retail clients who traded that day, consolidated by PAN + mobile. Values are summed per segment: CM = cash market (buy + sell value), FO = F&O. It excludes P&L and margin. The detailed per-trade list arrives by email from NSE.
- **Sample (verbatim):**
  ```
  Dear DHPXXXXX5Q,Your traded value for 09-FEB-24 CM Rs 4640 FO Rs 37649.25 Check your registered email.
  ```
  Also seen as the YouTube title `Dear EXIXXXXX8B,Your traded value for ?`, which confirms the same opening.
- **Fields:** masked PAN (`[A-Z]{3}X{5}\d[A-Z]` style), trade date (`dd-MMM-yy`), one or more `<SEG> Rs <amount>` pairs. Segments are not fixed: expect CM, FO, and possibly CD (currency) or CO (commodity). Parse them as repeated pairs.
- **Sender core:** `NSESMS` (e.g. `VK-NSESMS`, `JS-NSESMS`), per the rupeemagnet source below. The `NSEALR` header was not confirmed.
- **Sources:**
  - https://tradingqna.com/t/data-traded-value-sms/17646 (post #10: https://tradingqna.com/t/data-traded-value-sms/17646/10)
  - https://www.youtube.com/watch?v=odacXEEFU_w
  - https://www.nseindia.com/static/invest/first-time-investor-trade-alerts
  - https://resources.groww.in/wp-assets/faq-on-sms-email-based-trade-alerts-facility-for-investors.pdf
- **Confidence:** verbatim (Feb 2024 sample)
- **Regex skeleton:** `Dear\s+(\w+),\s*Your traded value for\s+(\d{2}-[A-Z]{3}-\d{2})\s+((?:[A-Z]{2}\s+Rs\s+[\d,.]+\s*)+)`
- **Spec note:** This is turnover, **not** a cash movement. Store it as a trade-activity record and do not book it as income or expense.

### F9. NSE: funds and securities balance reported by broker (SEBI enhanced supervision)
- **Institution / type:** NSE, periodic balance SMS (weekly / monthly). It shows the funds and securities **held by the broker** (not demat holdings) as on a past date. Email counterpart: `nse_alerts@nse.co.in`, subject "Funds/Securities Balance".
- **Sample (verbatim, with the broker's own placeholders):**
  ```
  ICICI SECURITIES LIMITED on DD-MM-YY reported your Fund bal Rs. 0 & Securities bal 0. This excludes your Bank, DP & PMS bal with the broker - NSE.
  ```
- **Fields:** broker name (upper case), as-on date (`dd-MM-yy`), fund balance (can be **negative**; users report negative values), securities balance (value or count)
- **Sender core:** `NSESMS`
- **Sources:**
  - https://www.icicidirect.com/faqs/stocks/why-am-i-getting-frequent-messages-from-nse-regarding-funds-and-securities-balance-icici-securities-limited-on-dd-mm-yy-reported-your-fund-bal-rs-0-securities-bal-0-this-excludes-your-bank-dp-pms-bal-with-the-broker-nse
  - https://www.hdfcsec.com/exchange-notification
  - https://rupeemagnet.com/your-stock-broker-reported-your-fund-balance/
  - https://www.youtube.com/watch?v=CIWIAhitxx8
- **Confidence:** verbatim (template text as published by ICICI Direct)
- **Regex skeleton:** `^(.+?)\s+on\s+(\d{2}-\d{2}-\d{2})\s+reported your Fund bal Rs\.?\s*(-?[\d,.]+)\s*&\s*Securities bal\s*(-?[\d,.]+)`
- **Spec note:** This is a balance snapshot. It is useful for reconciling a "broker cash" pseudo-account, but it is not a transaction.

### F10. BSE: funds and securities balance (older format)
- **Format (structure as transcribed by a user, 2017):** `Broker, BSE FundBal : NetFundBal : QtyShares AsOn31072017`
- **Fields:** broker, fund balance, net fund balance, quantity of shares held or pledged with the broker, as-on date (`ddMMyyyy`, no separators)
- **Sender core:** `BSELTD` (e.g. `QP-BSELTD`, `VM-BSELTD`). The `BSESMS` header was not confirmed. The email comes from "BSE Ltd".
- **Sources:**
  - https://tradingqna.com/t/received-sms-from-bse/19117
  - https://rupeemagnet.com/your-stock-broker-reported-your-fund-balance/
- **Confidence:** reconstructed. This is the user's structural transcription, not the full text, and the current BSE wording is unknown.

### F11. BSE: end-of-day trade alert
- **Type:** BSE daily trade SMS/email under SEBI circular CIR/MIRSD/15/2011. The FYERS help page has an illustrative sample that points to `bseindia.com/investors/trade_check.aspx`.
- **Sources:**
  - https://beta.bseindia.com/static/investors/Tradealert.aspx
  - https://support.fyers.in/portal/en/kb/articles/why-do-i-receive-sms-and-email-notifications-from-nse-bse-or-mcx-when-i-trade
- **Confidence:** reconstructed. No real BSE trade SMS text was found.

### F12. MCX: trade alert
- **Sample (illustrative, labelled as an email by FYERS):** `MCX Trade Alert: Order ID 0042 executed for SILVERM NOV FUT.`
- **Background:** MCX has sent daily SMS trade confirmations since 2013.
- **Sources:**
  - https://support.fyers.in/portal/en/kb/articles/why-do-i-get-an-sms-and-email-from-nse-bse-mcx-when-i-trade
  - https://www.moneylife.in/article/mcx-starts-trade-alert-confirmation-service-through-sms-emails/32140.html
- **Confidence:** illustrative / reconstructed

### F13. NSE: welcome / registration SMS
- **Type:** sent once when a broker registers the mobile number in the NSE UCI system. Trade alerts start 7 working days later.
- **Source:** https://www.nseindia.com/static/invest/first-time-investor-trade-alerts
- **Confidence:** reconstructed (no text found)
- **Parser action:** IGNORE. It could optionally record "new broker relationship".

---

## 3. Brokers

Broker SMS text is poorly indexed publicly. No verbatim full-message samples were found for Groww, Angel One, Upstox, Dhan, ICICI Direct, HDFC Securities, Kotak Securities, 5paisa or Paytm Money.

### F14. Zerodha: withdrawal (payout) processed
- **Sample (verbatim fragment from a user post, 2018):** `withdrawal request for Rs 13916.620 has been processed`
- **Fields:** amount (note the 3-decimal format `13916.620`)
- **Note:** The same SMS is sent for automatic **quarterly running-account settlements**, which the user did not request. The SMS "does not have any transfer details". The bank-side NEFT/IMPS credit is the authoritative money movement, so de-duplicate against the bank SMS.
- **Sources:**
  - https://support.zerodha.com/category/funds/fund-withdrawal/other-withdrawal-related-queries/articles/quarterly-settlement-payout-processed
  - https://zerodha.com/z-connect/general/zerodha-fund-withdrawal-from-trading-account
- **Confidence:** verbatim (fragment; the prefix and suffix are unknown)

### F15. Zerodha: withdrawal rejected
- **Fragment as quoted by the search summary:** `...the same request has been rejected due to insufficient balance`
- **Source:** https://zerodha.com/z-connect/general/zerodha-fund-withdrawal-from-trading-account (comments)
- **Confidence:** reconstructed (fragment, wording uncertain)
- **Parser action:** a non-transaction status. Do not book it.

### F16. Zerodha: funds added (pay-in)
- **Type:** For NEFT/RTGS, Zerodha says it "will update your trading limits as soon as the funds are available ... and send you an SMS confirming the same". UPI pay-ins appear instantly under Pay-in.
- **Sources:**
  - https://zerodha.com/z-connect/general/zt-transfer-funds-into-trading-account
  - https://support.zerodha.com/category/funds/adding-funds/how-to-add-funds/articles/add-money-using-upi-to-my-trading-account
- **Confidence:** reconstructed (no text)
- **Parser note:** The bank debit SMS (UPI to `zerodha...@validhdfc` / `@validicici` / `@validaxis`) is easier to capture than the broker SMS. Treat it as a transfer to the broker account, not an expense.

### F17. Angel One: fund settlement / payout intimation
- **Type:** Angel One's settlement policy says "on settling client funds, we have a practice of sending an intimation to the client by SMS ... and also by email". Partial payouts happen above Rs 50,000 because of peak margin.
- **Sources:**
  - https://w3assets.angelone.in/wp-content/uploads/pdfs/3.-Actual-Settlement-policy-final-website.pdf
  - https://www.angelone.in/announcements/market/explained-why-you-may-be-getting-partial-fund-payouts
- **Confidence:** reconstructed (no text)

### F18. Groww: balance added / withdrawal
- **Type:** Groww says withdrawals under Rs 1 lakh requested 9:30 AM to 4:00 PM are credited instantly. Sale proceeds reach the Groww balance in two parts (about 80% quickly, the rest later).
- **Source:** https://groww.in/help/payments-&-withdrawals/payments/money-has-been-deducted-from-my-groww-balance-but-i-havent-received-it-in-my-bank-account-2
- **Confidence:** reconstructed (no SMS text found)

### F19. Contract note notification (all brokers)
- **Type:** Contract notes are **emailed** on trade day (PDF, PAN-password). No source documents a broker SMS for contract notes. Any SMS is at most "contract note sent to your email".
- **Sources:**
  - https://support.zerodha.com/category/console/reports/contract-note/articles/where-can-i-get-the-contract-notes-for-the-trades-i-ve-taken
  - https://upstox.com/glossary/contract-note-2/
- **Confidence:** reconstructed
- **Parser action:** IGNORE. Rely on F8 for trade activity.

### F20. Broker order-executed SMS (ICICI Direct and others)
- **Type:** Most discount brokers (e.g. Zerodha) send no per-order SMS; they use app notifications. ICICI Direct's statuses are "Executed" and "Part executed", each with a Trade Ref no. No SMS text was found.
- **Sources:**
  - https://support.zerodha.com/category/your-zerodha-account/your-profile/mail-queries/articles/sms-alert-or-notifications-from-zerodha
  - http://content.icicidirect.com/SelfHelp/Equity.asp
- **Confidence:** reconstructed

---

## 4. Dividends and other bank-side investment credits

Verbatim samples in this section come from public parser test fixtures in https://github.com/sarim2000/pennywiseai-tracker:
- `parser-core/src/test/kotlin/TestICICIBankParser.kt`
- `parser-core/src/test/kotlin/TestSaraswatBankParser.kt`
- `parser-core/src/test/resources/ios-parity.tsv`
- `parser-core/src/test/kotlin/TestHDFCBankParser.kt`

Listed companies must pay dividends electronically (NACH / ACH / NEFT / Direct Credit), per HDFC Bank's annual report shareholder info: https://v.hdfcbank.com/content/dam/hdfc-aem-microsites/Annual_Report_FY22/pdfs/Shareholder%20Information.pdf

### F21. ICICI Bank: ACH dividend credit
- **Sender:** `AD-ICICIT-S` (core `ICICIT`)
- **Sample (verbatim fixture):**
  ```
  ICICI Bank Account XX227 credited:Rs. 104.00 on 19-May-26. Info ACH*VARUNBEVERAGES LIMI*778. Available Balance is Rs 1,11,231.93
  ```
- **Fields:** a/c tail, amount, date (`dd-MMM-yy`), payer company (between the `ACH*` and `*` markers, truncated to about 19 chars), reference, balance
- **Regex (from repo):** `Info\s+(?:ACH|NACH)\*([^*]+)\*`. The repo labels the merchant "<company> Dividend".
- **Confidence:** verbatim (fixture)
- **Caveat:** ACH credits are not always dividends (they can be refunds or vendor payments). Use a known-company or "LTD/LIMI" heuristic and let the user confirm.

### F22. Saraswat Co-op Bank: ACH credit (dividend)
- **Sender:** `JD-SARBNK-S`
- **Sample (verbatim fixture):**
  ```
  Your A/c no. 1234 is credited with INR 100.50 on 13-10-2025 towards ACH Credit:MERCHANT NAME. Current Bal is INR 950.00 CR  - Saraswat Bank
  ```
- **Real-payer example from a code comment:** `...towards ACH Credit:GUJARAT GAS LIMITED. Current Bal is INR 941.23 CR  - Saraswat Bank`
- **Note:** there are two spaces before `- Saraswat Bank`.
- **Fields:** a/c, amount, date (`dd-MM-yyyy`), payer after `ACH Credit:`, balance
- **Confidence:** verbatim (fixture; the account was anonymised by the repo)

### F23. ICICI Bank: ACH debit (negative case, not a dividend)
- **Sender:** `AX-ICICIT`
- **Sample (verbatim fixture):**
  ```
  ICICI Bank Account XX123 debited Rs. 5,000.00 on 19-May-26 InfoACH*BD-ACHKFL.Avl Bal Rs. 3,25,000.04.To dispute call 1800 blah blah
  ```
- **Note:** The repo classifies this as INVESTMENT (an ACH mandate debit, e.g. SIP or loan). There is no space in `InfoACH*`, so the regex must allow `Info\s*`.
- **Confidence:** verbatim (fixture, test-edited tail)

### F24. HDFC Bank: NACH mandate to NSE Clearing registered (IGNORE)
- **Sender:** `VM-HDFCBK-S`
- **Sample (verbatim fixture):**
  ```
  Auto Pay HDFC Bank NACH Mandate : Rs. 100000.00 UMRN:HDFC7031703262015557 To:NationalSecuritiesClearin Freq ADHO received today for processing.
  ```
- **Note:** This is a broker auto-pay / UPI-mandate setup to NSE Clearing (NSCCL), as used for broker fund pay-in. The repo marks it as "should NOT parse". It is useful as a signal that the user has a broker account.
- **Confidence:** verbatim (fixture)

### F25. HDFC Bank / SBI: NACH dividend credit (HDFC "NACH credit ... DIV" style)
- **Not found.** No public sample of HDFC or SBI dividend-credit SMS text was located. The forum thread https://tradingqna.com/t/hdfc-dividend-credit/166787 discusses timing only (30 to 45 days after the record date).
- **Confidence:** reconstructed / gap

---

## 5. Messages to IGNORE (classify, do not create transactions)

| Message | Example / evidence | Source | Confidence |
|---|---|---|---|
| CDSL TPIN generated | `Your Generated TPIN code is XXXXXX` (6 digits) | https://www.chittorgarh.com/faq_pg/how-to-generate-cdsl-tpin/3168/ | verbatim (fragment) |
| CDSL TPIN for eDIS | `TPIN to avail CDSL e-DIS Facility for BOID XXXXXXXXXXXXXXXX ...` (email from edis@cdslindia.co.in / .com) | https://groww.in/help/stocks,-f&o,-ipo-&-mtf/searchable/i-havent-received-tpin-from-cdsl-what-to-do | verbatim (fragment) |
| CDSL TPIN-generation OTP / eDIS authorisation OTP | OTP sent on BO ID + PAN entry | https://www.chittorgarh.com/glossary/cdsl-tpin/157/ | reconstructed |
| Margin pledge / MTF pledge initiation (link + OTP) | `Dear Investor, your DP GEOJIT INVESTMENTS LIMITED has initiated a margin pledge from your (NSDL/ CDSL) demat account` + link | https://support.geojit.com/support/solutions/articles/89000008005-how-can-i-place-a-pledge-request-on-mygeojit- | verbatim (fragment) |
| CDSL pledge OTP (valid 15 minutes, auto-cancelled end of day) | CDSL template | https://www.cdslindia.com/Authentication/PledgeMarginMailtemplate.htm | reconstructed |
| CDSL UCC mapping / early pay-in info (F5) | n/a | https://tradingqna.com/t/meaning-of-the-message-received-from-cdsl/139194 | reconstructed |
| CDSL UCC delinked notice | n/a | https://support.zerodha.com/category/your-zerodha-account/transfer-of-shares-and-conversion-of-shares/cdsl-easi-easiest/articles/received-email-from-csdl-saying-that-my-ucc-is-delinked | reconstructed |
| NSE welcome / registration SMS (F13) | n/a | NSE trade-alerts page | reconstructed |
| Broker login OTP, order-placement 2FA, Kite/Groww/Angel app OTPs | generic: match `OTP`, `one time password`, `do not share` | n/a | general rule |
| NACH mandate registration (F24) | see F24 | pennywiseai fixture | verbatim |
| Contract-note-emailed notices (F19) | n/a | n/a | reconstructed |
| Withdrawal rejected (F15) | n/a | n/a | reconstructed |
| Marketing: IPO open, "invest now", MTF promos, stock tips | NSE warns about stock-tip SMS | https://www.business-standard.com/amp/article/markets/nse-cautions-investors-against-stock-tips-via-sms-whatsapp-116102501489_1.html | general rule |
| Phishing look-alikes (fake "money credited", fake Paytm Money) | Validate the sender header against an allowlist; never trust the body alone | https://www.paytmmoney.com/blog/beware-of-fraudulent-activities-posing-as-paytm-money/ and https://www.phishguard.co.in/blog/money-received-message-real-or-fake-read-the-sms | general rule |

**Classify as informational (store, do not book cash):** F8 (traded value), F9 and F10 (balance snapshots), F5 and F6 (blocks).

---

## 6. Sender-ID summary

| Institution | Header core | Status |
|---|---|---|
| NSE | `NSESMS` (`VK-`, `JS-` prefixes seen) | confirmed by rupeemagnet / broker FAQs |
| BSE | `BSELTD` (`QP-`, `VM-`) | confirmed by rupeemagnet / broker FAQs |
| CDSL | unknown. Match on body prefix `CDSL:` | **unverified** (`CDSLEA` / `CDSLIN` not confirmed) |
| NSDL | unknown. The alert is sent by NSDL or the DP | **unverified** |
| ICICI Bank | `ICICIT` (`AD-`, `AX-`) | fixture |
| HDFC Bank | `HDFCBK` (`VM-`) | fixture |
| Saraswat Bank | `SARBNK` (`JD-`) | fixture |
| Brokers (Zerodha, Groww, Angel, Upstox, Dhan, etc.) | unknown | gap |

Indian DLT headers are `XX-ABCDEF[-S/-T/-P/-G]`. Strip the 2-letter operator/circle prefix and the suffix, then match on the 6-character core.

---

## 7. Gaps and next steps

1. **No verbatim broker SMS** for Groww, Angel One, Upstox, Dhan, ICICI Direct, HDFC Securities, Kotak, 5paisa or Paytm Money (pay-in, payout or order-executed). Only a Zerodha payout fragment was found.
2. **Current CDSL equity-sale debit text** is unconfirmed (only the 2019 redemption sample and the Subex fragment exist). The CDSL credit text (IPO/bonus) and NSDL debit/block texts are also unconfirmed.
3. **BSE trade-alert SMS** and the **current BSE balance SMS** wording are not found.
4. **HDFC / SBI / Axis dividend-credit SMS** is not found. Only ICICI (`Info ACH*<CO>*<ref>`) and Saraswat (`ACH Credit:<CO>`) are verbatim.
5. **CDSL and NSDL sender IDs** are unverified.
6. **Recommendation:** Ship an in-app "unrecognised financial SMS → share anonymised sample" flow (as pennywiseai does), and build an allow-list of sender cores. Re-run this research with network access to tradingqna.com, support.zerodha.com, reddit.com and github.com code search to fill the gaps.
