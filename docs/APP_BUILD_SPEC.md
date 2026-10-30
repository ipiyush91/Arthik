# Build Spec: SMS-Based Personal Finance App for Android (India)

> **How to use this file:** Paste this whole document into a new Claude session as the brief.
> It contains every requirement and decision made so far. Nothing has been built yet.

---

## 1. Goal

Build an **Android app** (target **Android 16 / API 36**) that:

1. Reads the user's **SMS** and turns bank, card, UPI and investment messages into transactions.
2. Gives an **overall picture of monthly expenses**, split into **spending buckets** (categories).
3. Tracks **savings accounts**, **credit cards** and **investment accounts**.
4. Is **simple and interactive**, with a **dark "liquid glass" theme**.

Reference project: https://github.com/wealthfolio/wealthfolio
(Wealthfolio is a local-first desktop investment tracker built with Tauri, Rust, React and SQLite.)

---

## 2. Key decisions (already made by the user)

| Topic | Decision |
|---|---|
| Use of Wealthfolio | **Option (a):** build a **native Android app (Kotlin + Jetpack Compose)**. Reuse Wealthfolio's *ideas* for investment tracking: accounts, holdings, activities and performance. **Support import and export of Wealthfolio's CSV format** so data can move between phone and desktop. Do not fork the Tauri app. |
| Country | **India** (INR, Indian bank, card and UPI SMS formats). |
| Sample SMS | **Real samples for ICICI Bank (savings, credit card, FASTag), SBI Card and HDFC Bank credit card are in Section 13.** They are anonymised. Build exact templates and tests for these. Use publicly known formats for all other institutions, and keep the parser rule-based and easy to extend. |
| Spending buckets | Accept the default list (Section 5). |
| Investments | **SMS only** is enough. No live market prices and no manual entry required (a small manual-edit option is fine). |
| Credit cards | **Track everything:** spends, statement amount, minimum due, due date, credit limit and available limit, payments, plus payment reminder notifications. |
| SMS history | Scan the **last 12 months** on first launch, then process new SMS **in real time**. |
| Extras | **Yes:** monthly budgets with alerts · recurring and subscription detection · encrypted backup and restore to a file · home-screen widget. **No** AI or cloud features. |
| Delivery | **Hand over a signed APK plus the full source code** in the session. Do **not** push to GitHub. |
| Theme | **Dark mode only**, frosted translucent "liquid glass" cards, soft gradient blobs in the background, one accent colour (default teal/violet), animated donut and bar charts. |
| App weight | **Lightweight, fluid and smooth is the priority.** Prefer platform APIs and hand-written code over extra libraries. See Section 12 for the performance budget. |
| Open decisions | The developer chose defaults for every open question. See **Section 11**. Do not re-ask these. |
| App name | **Arthik** (Hindi आर्थिक, "financial"). Use `applicationId = "app.arthik"` (a Kotlin package cannot start with the keyword `in`). Keep the display name only in `strings.xml` (`app_name`), so it is easy to change later. |

---

## 3. Constraints and non-functional requirements

- **Sideloaded APK.** Google Play only allows `READ_SMS` and `RECEIVE_SMS` for default-SMS-handler apps, so this app is **not** meant for the Play Store.
- **100% on-device and offline.** No servers, no analytics, no internet permission needed.
- **Security:**
  - Lock the app with biometrics (fingerprint or face) using `BiometricPrompt`, falling back to the device PIN.
  - Encrypt the database (SQLCipher or similar) with a key held in the Android Keystore.
  - Set `FLAG_SECURE` on sensitive screens.
- **Permissions:**
  - Request `READ_SMS`, `RECEIVE_SMS` and `POST_NOTIFICATIONS` at runtime, with a clear explanation screen before each request.
  - Handle denial gracefully.
- **Android 16 specifics:**
  - Target SDK 36 with edge-to-edge layout (mandatory).
  - Support predictive back.
  - Follow the current foreground-service and background-work limits: use WorkManager for scans.
- **Performance:** scanning 12 months of SMS must run in the background with a progress UI and must not block the main thread.
- **Deduplication:** the same transaction often arrives more than once (for example a bank SMS plus a UPI-app SMS, or a card-spend SMS plus an OTP). Deduplicate by amount, time window, account and reference/UTR number.
- **Ignore:** OTPs, promotional and marketing SMS, loan offers, and "pre-approved" offers.

---

## 4. Suggested tech stack

Keep the dependency list short. Every library has to justify its size.

- **minSdk 31 (Android 12)**, targetSdk 36. One code path for blur, with no fallback libraries.
- Kotlin, Jetpack Compose (Material 3, customised), MVVM with ViewModel and StateFlow.
- **Storage:** Room (KSP) with SQLCipher (`net.zetetic:sqlcipher-android`), Paging 3 for the transaction list, and DataStore for preferences.
- **Dependency injection:** **manual** (a single `AppContainer`). **No Hilt or Dagger.**
- **Background work:** WorkManager only. No foreground services and no exact alarms.
- **JSON:** `kotlinx.serialization` (no reflection, no Gson or Moshi).
- **Charts:** **custom Compose Canvas** donut and bar charts. **No chart library.**
- **Glass effect:** "cheap glass". **No blur library.** See Section 8.
- **Icons:** copy only the ~25 vector icons the app needs. **Do not add `material-icons-extended`.**
- **Font:** system font (no bundled fonts).
- **Widget:** Jetpack Glance.
- **Biometrics:** `androidx.biometric`.
- **Build:**
  - Gradle KTS with a version catalog.
  - R8 full mode and resource shrinking.
  - A **Baseline Profile** for startup and scroll.
  - Release APK for **arm64-v8a only**, which keeps the SQLCipher native library small. Optionally also build a universal APK.
- Produce a signed release APK and include the signing instructions.

---

## 5. Spending buckets (categories)

1. Food & Dining
2. Groceries
3. Shopping
4. Transport / Fuel
5. Bills & Utilities (electricity, mobile, broadband, DTH, gas, water)
6. Rent / EMI
7. Health
8. Entertainment / Subscriptions
9. Travel
10. Cash: ATM withdrawals, counted as spend (can be recategorised)
11. Transfers: **excluded from spend totals** (self-transfers, transfers between your own accounts, credit card bill payments)
12. Investments: **excluded from spend totals** (SIP, broker funding, mutual fund purchases)
13. Others

**Income buckets:** Salary · Interest · Dividends · Refunds & Cashback · Other income.

**Categorisation rules:**
- Categorise using a merchant keyword dictionary (Swiggy, Zomato, BigBasket, Blinkit, Zepto, Amazon, Flipkart, Myntra, Uber, Ola, Rapido, IRCTC, MakeMyTrip, Netflix, Spotify, Hotstar, Jio, Airtel, BESCOM, and so on), plus UPI handle hints.
- The user can **recategorise** any transaction.
- When they do, the app **learns a rule** for that merchant (merchant → category) and applies it to past and future transactions. Ask first before applying it to past transactions.
- Users can add, rename or recolour categories.

---

## 6. Institutions to support (sender IDs and parsers)

Indian SMS sender IDs look like `XX-ABCDEF`. The 2-letter prefix (AD-, VM-, JD-, BZ-, and so on) varies, and newer IDs may end in a `-S`, `-T` or `-P` suffix. Match on the **6-character core** and ignore the prefix and suffix.

The sender cores below are **starting points and must be verified**. Keep them in a config or JSON file that is easy to extend.

### 6.1 Banks
- **Public sector:**
  - State Bank of India (SBI): SBIINB, SBIPSG, CBSSBI, ATMSBI
  - Bank of Baroda: BOBTXN, BOBSMS
  - Canara Bank: CANBNK
  - Punjab National Bank: PNBSMS
  - Bank of India: BOIIND
  - Union Bank of India: UNIONB
  - Indian Bank: INDBNK
  - Central Bank of India: CENTBK
  - Indian Overseas Bank: IOBCHN
  - UCO Bank: UCOBNK
  - Bank of Maharashtra: MAHABK
  - Punjab & Sind Bank: PSBANK
- **Private sector:**
  - HDFC Bank: HDFCBK
  - ICICI Bank: ICICIB, ICICIT
  - Axis Bank: AXISBK
  - Kotak Mahindra Bank: KOTAKB
  - YES Bank: YESBNK
  - IndusInd Bank: INDUSB
  - Federal Bank: FEDBNK
  - IDFC FIRST Bank: IDFCFB
  - RBL Bank: RBLBNK
  - Bandhan Bank: BANDHN
  - South Indian Bank: SIBSMS
  - Karur Vysya Bank: KVBANK
  - City Union Bank: CUBANK
  - DCB Bank: DCBBNK
  - Karnataka Bank: KBLBNK
  - Tamilnad Mercantile Bank: TMBANK
  - IDBI Bank: IDBIBK
- **Small finance and payments banks:**
  - AU Small Finance Bank: AUBANK (Fincare SFB merged into AU in 2024)
  - Ujjivan SFB: UJJIVN
  - Equitas SFB: EQUTAS
  - Jana SFB: JANABK
  - Suryoday SFB
  - ESAF SFB
  - Airtel Payments Bank: AIRBNK
  - India Post Payments Bank: IPBMSG
  - Fino Payments Bank
  - Jio Payments Bank

### 6.2 Credit cards
- Cards from all of the banks above, plus:
  - SBI Card: SBICRD
  - OneCard
  - Scapia
  - Jupiter / Edge
  - AmEx: AMEXIN
  - HSBC
  - Standard Chartered
- Parse each of these message types:
  - spend: "spent / used / debited on card XX1234 at MERCHANT"
  - statement generated: total due, minimum due, due date
  - payment received
  - available / total limit
  - reversals and refunds

### 6.3 NBFCs, loans and insurance (EMI and premium tracking)
- **NBFCs and lenders:** Bajaj Finance / Bajaj Finserv, Aditya Birla Capital, Cholamandalam, IIFL Finance, Muthoot Finance, Tata Capital, HDB Financial, L&T Finance, Poonawalla, Home Credit, Moneyview, Lendingkart, KreditBee, Navi.
- **Infrastructure financiers:** Power Finance Corporation (PFC) and Northern Arc. Treat these as bonds or NCD investments if their SMS appear.
- **Insurance:** LIC, Policybazaar / Paisabazaar, Acko, HDFC Life, ICICI Prudential, SBI Life, Max Life, Star Health, Niva Bupa, Care Health, Tata AIG, Digit.
- Map EMIs to **Rent / EMI** and insurance premiums to **Bills & Utilities** (or a user-chosen bucket).

### 6.4 Payments and fintech (UPI and wallets)
- **UPI apps:** Paytm, PhonePe, Google Pay, Amazon Pay, MobiKwik, CRED, BHIM, Navi, Super.money, Pop.
- **Payment gateways:** Razorpay, Pine Labs, Cashfree, PayU. These appear as merchant descriptors, so strip them to find the real merchant.
- **Prepaid / expense cards:** Zaggle.
- **Ignore as SMS sources:** Perfios, Signzy and Manipal Payment & Technology Services (RegTech / infrastructure; they rarely send user transaction SMS), unless messages from them are detected.
- Extract from UPI SMS: VPA / payee, UTR reference number, debit/credit, amount and account.
- Avoid double counting when a bank SMS and an app SMS describe the same UPI transaction.

### 6.5 Brokers and investment platforms (the user asked for this list to be expanded)
- **Discount / digital brokers:** Zerodha (Kite / Coin), Groww, Angel One, Upstox, 5paisa, Dhan, Fyers, Paytm Money, INDmoney, Kotak Neo, m.Stock (Mirae Asset), Samco, Alice Blue, Shoonya (Finvasia), Sahi, Lemonn.
- **Traditional / full-service brokers:** ICICI Securities (ICICIdirect), HDFC Securities (HDFC Sky), Kotak Securities, Motilal Oswal, Sharekhan (Mirae), Axis Direct, SBI Securities, IIFL Securities, Nuvama (formerly Edelweiss), 360 ONE, JM Financial, Geojit, Anand Rathi, Religare, Choice, Prabhudas Lilladher, Ventura.
- **Depositories (very important, because they send trade and holding confirmations):** CDSL (sender CDSLEA / CDSLTX), NSDL. NSE and BSE also send trade-confirmation SMS.
- **Mutual fund registrars (RTAs):** CAMS, KFintech. Also the AMCs themselves (SBI MF, HDFC MF, ICICI Pru MF, Nippon, Axis MF, Kotak MF, Parag Parikh, Mirae, UTI, DSP, Tata MF, and others).
- **Other:** NPS (Protean / KFintech CRA), PPF/EPF (EPFO sends "UAN passbook / contribution" SMS), Sovereign Gold Bonds, fixed and recurring deposits (FD/RD) from bank SMS.

**Investment parsing should capture:**
- buy/sell trades (security, quantity, price, exchange)
- funds added to and withdrawn from the broker
- mutual fund purchases, redemptions and SIP debits (scheme, units, NAV, amount, folio)
- dividends
- FD creation and maturity
- EPF and NPS contributions

Build **holdings and activities in a Wealthfolio-compatible model**: accounts, assets, and activities of type BUY / SELL / DIVIDEND / DEPOSIT / WITHDRAWAL / INTEREST / FEE. Compute invested value. Because SMS-only means no live prices, show "invested amount" and the last known price or NAV parsed from SMS.

### 6.6 Parser design
- Use a **rule engine**: per-institution regex templates plus a generic fallback parser. The fallback detects amounts in these forms: `Rs`, `Rs.`, `INR`, `₹`, numbers with Indian digit grouping like 1,23,456.78, and debit/credit keywords (debited, credited, spent, withdrawn, received, sent, paid, refund, reversed).
- Extract: amount, type (debit/credit), account or card last 4 digits, merchant/payee, date and time, reference/UTR, available balance (if present).
- Store the **raw SMS reference** with each parsed transaction so the user can tap it to see the original.
- Provide an **"Unrecognised SMS" review screen** for financial-looking messages that didn't parse. The user can mark one as ignore or map it, which helps with adding samples later.
- Rules should live in JSON/assets so new formats can be added **without code changes**.
- Include **unit tests** for the parser with synthetic sample messages for major banks, cards, UPI, brokers and RTAs.

---

## 7. Features and screens

1. **Onboarding:** privacy explanation → SMS permission → biometric setup → 12-month scan with a progress animation.
2. **Dashboard (home):**
   - This month's total spend vs income vs last month.
   - Animated **donut chart by bucket**; tap a slice to drill down.
   - **Bar chart** of daily or monthly spend trend.
   - Glass cards for net worth (bank balances + investments − card dues), upcoming card dues, and budget status.
3. **Transactions:** searchable, filterable list (month, bucket, account, type) with swipe to recategorise, a detail sheet showing the raw SMS, and add/edit/delete.
4. **Accounts:**
   - **Savings accounts:** last known balance (from SMS), monthly inflow and outflow.
   - **Credit cards:** current unbilled spend, statement amount, minimum due, due date countdown, limit and utilisation bar, payment history.
   - **Investments:** per broker / RTA / EPF / NPS / FD holdings, invested amount, and activity timeline.
5. **Insights:** month-over-month comparison per bucket, top merchants, and a **recurring and subscriptions list** (auto-detected by matching merchant + amount + cadence) with the next expected date.
6. **Budgets:** monthly budget per bucket and overall. Notifications at 80% and 100%.
7. **Reminders:** credit card due-date notifications (for example 5 days, 1 day and the due day), plus upcoming subscription and EMI alerts.
8. **Settings:**
   - Categories and rules.
   - Accounts: merge, rename, hide.
   - Rescan SMS.
   - **Encrypted backup and restore**, using a password-protected file through the Storage Access Framework.
   - **Wealthfolio CSV import and export** for accounts and activities.
   - App lock.
   - Clear data.
9. **Home-screen widget (Glance):** month-to-date spend, the top 3 buckets, and the next card due date.

---

## 8. Visual design: dark "liquid glass"

- **Dark mode only.** Near-black background (around #0B0B12) with **soft, slowly animated gradient blobs** (teal / violet / blue) behind the content.
- **How to get glass cheaply (this is mandatory for smoothness):**
  - Draw the background blobs as large **radial gradients**. They are already soft, so they need **no blur pass**.
  - Animate the blobs with `graphicsLayer` translation and scale only. This avoids recomposition and keeps them at 60/120 fps.
  - Cards are translucent fills over the blobs, which looks like frosted glass at almost no cost. **Do not apply real-time backdrop blur to cards or list items.**
  - Use real `RenderEffect` blur only on one or two static surfaces, if at all (for example the bottom sheet scrim). Respect the system "reduce animations" setting by freezing the blobs.
- **Glass cards:**
  - translucent fill (white at about 6–10% alpha)
  - 1px light border (white at about 15% alpha)
  - large corner radius (20–28dp)
  - a subtle inner highlight or gradient sheen
- **Accent colour:** default **teal / violet**, defined as a single theme token so it is easy to change.
- **Charts:** animated donut and bar charts with smooth enter and transition animations; tapping a part shows a tooltip.
- **Typography and layout:** clean, rounded type with large numbers for amounts. Format amounts as INR with Indian digit grouping, e.g. ₹1,23,456.
- **Interaction:**
  - Simple and interactive: bottom navigation (Home · Transactions · Accounts · Insights · Settings).
  - Haptic feedback, pull-to-refresh, shared-element and spring animations.
  - Keep it uncluttered.
- **Accessibility and compatibility:**
  - Sufficient contrast on the glass surfaces.
  - Supports font scaling.
  - Glass is built without blur, so there is no costly path to fall back from.

---

## 9. Deliverables

1. Full **source code** (Android Studio / Gradle project) handed over in the session (a zip or archive). **No GitHub push.**
2. A **signed release APK** ready to sideload, plus a debug APK.
3. A README covering: how to build, how to sideload on Android 16 (allowing installs from unknown sources, granting SMS permission, and Android's "restricted settings" step for sideloaded apps that request SMS access), how to add new SMS parsing rules, and how to rename the app.
4. Parser unit tests with synthetic SMS samples.

---

## 10. Open items (to be supplied by the user later)

> Everything else that was open has been decided in Section 11.

- Real samples for the formats marked "reconstructed" or missing in Section 14 (brokers, CAMS/KFintech, NPS, FD, SGB, NBFC EMIs, LIC). Until then they use the fallback parser.
- **Sender IDs** for the samples in Section 13. They weren't provided, so detect the institution from the message text as well (see 13.1).
- Optional: an icon or accent colour preference.

---

## 11. Decisions on previously open questions (defaults chosen; do not re-ask)

### 11.1 Money logic
| Question | Decision |
|---|---|
| Income | Use the income buckets in Section 5. Dashboard "income" = all credits except Transfers and Investments. |
| Refunds and reversals | **Net them against the original category** when they match the same merchant within 60 days. Otherwise put them in *Refunds & Cashback* income. |
| ATM cash | Goes in the **Cash** bucket and counts as spend. |
| Definition of a month | **Calendar month** by default. A setting lets the user pick a start day (1–28) for salary cycles. Credit cards always use their own statement cycle. |
| Self-transfers | **Automatic:** a debit from one tracked account and a credit to another tracked account with the same amount within 30 minutes, or with the same UTR, become *Transfers*. Credit card bill payments are always *Transfers*. The user can also mark VPAs and names as "mine". |
| Loans | Add a **Loan** account type with an outstanding amount (from SMS if present, otherwise one manual entry), reduced by each EMI. **Net worth = bank balances + investments − card dues − loan outstanding.** |
| Foreign-currency spends | Store the original currency and amount. Convert to INR using a user-editable rate per currency (default: the last known rate, which can be taken from the statement SMS). Mark the transaction "≈ approximate" until edited. |
| Card EMI conversion | The original purchase counts as spend **once**. The conversion creates a Loan entry, and the monthly card EMI postings go to *Transfers* to avoid counting it twice. |
| Split or shared expenses | Keep it simple: an optional **"My share"** field on a transaction. Totals use your share. No group or friends features. |

### 11.2 Accounts and data integrity
| Question | Decision |
|---|---|
| Automatic account creation | The account key is **institution + type + last 4 digits**. If there are no last 4 digits, use the institution's only account. If the institution has several, put the transaction in an **"Unassigned"** queue that the user resolves once. |
| Starting balances | Take the balance from the first SMS that includes one. Otherwise show a one-time "Set balance" prompt on that account. |
| Rescan vs user edits | Every user-edited field carries a `userEdited` flag. **Rescans never overwrite it.** A rescan only adds transactions that are missing. |
| Deduplication | It is a **strong duplicate** if the UTR or reference matches. It is a **probable duplicate** if the amount matches, the account is the same or linked, the time is within ±15 minutes and there is no conflicting reference. Different references mean they are **not** duplicates. Merge probable duplicates silently and keep both raw SMS. |
| Raw SMS | **Copy** the body, sender and timestamp into the encrypted database. Do not depend on the SMS provider ID. |
| Merchant names | Remove gateway prefixes (Razorpay, PayU, and so on), `*`, trailing city names and digits. Then map aliases to a single name through a JSON alias table. |
| Hidden accounts | Left out of totals and net worth. Their transactions are kept. |

### 11.3 Investments with SMS only
- **Opening holdings:** add a one-time **"Add opening holdings"** form, plus **Wealthfolio CSV import**, so the user can bring holdings over from desktop. Show a small banner: *"Holdings come from SMS and may be incomplete. Add opening holdings for accuracy."*
- **Symbols:** store the name exactly as it appears in the SMS, with an optional, user-editable symbol or ISIN field. Do not bundle large symbol databases. Wealthfolio export uses the symbol when it is set and otherwise uses the name, and flags those rows.
- **Wealthfolio CSV:** export and import **only investment accounts and their activities**. Verify the column format against the current Wealthfolio import documentation at build time. Spending transactions go to a separate **generic transactions CSV** export.
- Splits, bonus issues and corporate actions are **out of scope** for v1. Use manual edits instead.

### 11.4 Android and platform
| Question | Decision |
|---|---|
| minSdk | **31** |
| Reminders | A **daily WorkManager job at about 9:00** checks card dues, EMIs and subscriptions. Day-level timing is enough, so **no exact-alarm permission** is needed. WorkManager survives reboots. |
| Real-time SMS | The `RECEIVE_SMS` receiver only enqueues a small expedited WorkManager job. It does no parsing on the broadcast thread. |
| RCS and app notifications | **Not supported in v1.** No NotificationListener. Mention this limit in the README. |
| System backup | `allowBackup="false"`. Also exclude the data from device-to-device transfer. |
| Form factor | **Phone-first, portrait-optimised.** Layouts must not break on tablets and foldables, but no dedicated tablet UI. |
| Language | English UI. English SMS parsing. Hindi or regional SMS go to the "Unrecognised" screen. |

### 11.5 Security and privacy
- **App lock** comes back after **60 seconds** in the background. You can change this in Settings.
  - If no biometrics are enrolled, use the device PIN or pattern.
  - If the phone has no screen lock at all, show a warning and suggest setting one.
- **Widget:** **hide amounts by default** ("₹ •••"). Tapping the widget opens the app (behind the lock). Settings has an option to show amounts.
- **Notifications:** use `VISIBILITY_PRIVATE` with a redacted public version, so no amounts appear on the lock screen.
- **Encrypted backup:**
  - **Format:** a versioned file header, encrypted with AES-256-GCM using a key derived from the password with PBKDF2 or Argon2.
  - **Optional weekly auto-backup** to a folder the user picks (Storage Access Framework, run by WorkManager).
  - **Monthly reminder** if the user has never backed up.
- **Key loss:** if the database can't be opened (for example after a phone reset), show a **"Restore from backup"** screen. Clearly state in the app and in the README that a **forgotten backup password cannot be recovered**.

### 11.6 Rules, maintenance and delivery
- **Parsing rule updates:**
  - Built-in rules are JSON files in the app's assets.
  - Settings has **"Import rule pack"** (JSON). Imported rules override the built-in ones by rule ID.
  - On the "Unrecognised SMS" screen, **"Map it"** saves a simple rule: sender, keywords, type, category and account. No regex editor.
- **Database upgrades:** Room with exported schemas, plus auto-migrations and migration tests.
- **Updates:** install the new APK manually. Show the version in Settings.
- **Signing key:** generate a release keystore and hand it over with its passwords in a separate file. Make it clear in the README that **losing the keystore means future updates can't be installed over the existing app** (uninstalling it would lose the data, so back up first).
- **Building in the session:** install JDK 17 and the Android command-line tools. If the network blocks this, deliver the source code plus exact build commands instead.
- **Wealthfolio licence (AGPL):** reimplement its ideas only. Do not copy its code or assets.

### 11.7 Product details
- **Budgets:** no rollover. Each alert (80% and 100%) fires **once per bucket per month**.
- **Recurring and subscription detection:**
  - At least **3 occurrences** from the same normalised merchant.
  - The amount varies by no more than **±10%**.
  - The cadence is weekly, monthly or yearly, within ±3 days. The user can confirm or dismiss each one.
- **Exports:** a generic transactions CSV, plus the Wealthfolio CSV.
- **Every screen** has an empty state and an error state.
- **App icon:** a simple placeholder vector icon in the accent colour.

---

## 12. Performance budget and acceptance criteria

| Metric | Target |
|---|---|
| Release APK size (arm64) | **≤ 12 MB** |
| Cold start to dashboard (mid-range phone) | **≤ 600 ms** (with the Baseline Profile) |
| Scrolling and animations | 60 fps on a mid-range phone, 120 fps where supported. No jank in the transaction list. |
| Memory while idle | ≤ 150 MB |
| 12-month scan (≈20,000 SMS) | **≤ 60 s**, in the background, with progress shown and the UI still usable |
| Parser accuracy on the synthetic test set | ≥ 95% of fields correct |
| Background battery use | No wakelocks, no polling. Work runs only on new SMS and in the daily job. |

**Implementation rules for smoothness:**
- **Scanning:**
  - Filter SMS by sender ID core **before** running any regex.
  - Precompile the regexes once.
  - Scan in batches of about 500, with one database transaction per batch.
- **Lists:** stable `key`s, `contentType`, immutable UI models, and Compose strong skipping. Never format amounts or dates inside composition. Do that in the ViewModel.
- **Charts:** animate with `Animatable` and draw in `drawBehind` or `Canvas`, so animation does not recompose the parent.
- **Database:** compute dashboard aggregates in SQL (with indexed `date`, `accountId` and `categoryId`), not in Kotlin loops.
- **Widget:** refresh only when data changes, plus once at midnight.

---

## 13. Real SMS formats from the user (anonymised) and expected parsing

The user supplied about 90 real messages. They are reduced here to **one sample per format**. Personal names, VPAs, vehicle numbers and reference numbers are anonymised, but the **structure, spacing and punctuation are exactly as received**.
- Build one JSON rule per format.
- Put every sample below into `ParserTest` with the expected result shown.

### 13.1 General findings (apply to all parsers)
- **Identify the institution from the message text too, not only the sender ID.** Look for phrases like "ICICI Bank", "SBI Card" / "SBI Credit Card" and "HDFC Bank". Sender IDs were not supplied.
- **Amount formats seen:**
  - `Rs 91.00`, `Rs.1,628.12`, `Rs. 23,000.00`, `Rs.130` (no decimals), `INR 746.00`.
  - Indian grouping (`1,93,523.41`) **and** Western grouping (`196,000.00`).
  - Accept commas in any position.
- **Date formats seen:**
  - `30-Oct-26`, `07-NOV-26` (any letter case), `23/10/26`, `29/08/2026`, `21-10-2026 10:24:13`, `17/10/2026 17:07:41`, `Oct 06, 2026`.
  - **`28-10` (day and month, no year).** For this format, take the year from the SMS timestamp. If the resulting date is later than the SMS timestamp, use the previous year.
- **Use the SMS received time for the time of day** when the body has only a date.
- **Multi-line messages** (HDFC) must be parsed. Normalise newlines and repeated spaces to single spaces before matching.
- **Exact duplicate SMS happen.** The ICICI CRED Club debit of Rs 3105.00 arrived twice with the same UPI reference. That is a strong duplicate, so keep it once.
- **Same amount, same day, different UPI reference is not a duplicate.** For example, two debits of Rs 440 to the same payee with different references are two real transactions.
- **Masked or missing payee.** HDFC card UPI messages show payees like `*ymkc@ptys` or `NA`.
  - Store the VPA as the merchant.
  - Use the VPA handle only as a weak hint: `@ptys`/`@pty` = Paytm, `@ybl` = PhonePe, `@okbiz*` = Google Pay business.
  - Default category: **Others**.

### 13.2 ICICI Bank savings account
| # | Sample | Expected |
|---|---|---|
| I1 | `ICICI Bank Acct XX468 debited for Rs 91.00 on 30-Oct-26; PAYEE NAME credited. UPI:120807593798. Call 18002662 for dispute. SMS BLOCK 468 to 9215676766.` | DEBIT ₹91.00 · acct ICICI ••468 · payee "PAYEE NAME" · UPI ref 120807593798 · 2026-10-30 |
| I2 | `Dear Customer, Acct XX468 is credited with Rs 400.00 on 23-Oct-26 from PAYEE NAME. UPI:131720997577-ICICI Bank.` | CREDIT ₹400.00 · ICICI ••468 · from "PAYEE NAME" · ref 131720997577. Note the trailing space before "." in some messages, e.g. `from NAME .` |
| I3 | `ICICI Bank Account XX468 is credited with Rs 21,000.00 on 30-Sep-26 by Account linked to mobile number XXXXX12345. IMPS Ref. no. 627317559036.` | CREDIT ₹21,000 · IMPS · ref 627317559036 · payee "Mobile XXXXX12345" |
| I4 | `Your ICICI Bank Account XX468 credited:Rs. 23,000.00 on 07-Sep-26. Info CAM*13321ORY*CA. Available Balance is Rs. 1,93,523.41.` | CREDIT ₹23,000 · **cash deposit** (`CAM*` = cash deposit machine) · balance ₹1,93,523.41 · category **Transfers** (cash moved into the bank, not income) |
| I5 | `ICICI Bank Acc XX468 debited Rs. 27,534.82 on 04-Sep-26 InfoBIL*ONL*BIC03.Avl Bal Rs. 21,815.91.To dispute call 18002662 or SMS BLOCK 468 to 9215676766` | DEBIT ₹27,534.82 · **online bill payment** (`BIL*ONL*`) · balance ₹21,815.91 · try to match a credit card payment within ±2 days, which makes it a **Transfer**; otherwise **Bills & Utilities**. Note there is no space after `Info` or after `.` |
| I6 | `ICICI Bank Acc XX468 debited Rs. 1,000.00 on 20-Aug-26 NFS*CASH WDL*. Avb Bal Rs. 8,390.72. To dispute Call 18002662 or SMS BLOCK 468 to 9215676766 .` | DEBIT ₹1,000 · **ATM withdrawal** → **Cash** · balance ₹8,390.72. Note the `Avb Bal` spelling |
| I7 | `KYC updation for your ICICI Bank Savings Account XX468 will be due on Oct 06, 2026. ...` | **IGNORE** (service message). It may still be used to confirm that account ••468 is a savings account. |

### 13.3 ICICI Bank credit cards and FASTag
| # | Sample | Expected |
|---|---|---|
| C1 | `INR 746.00 spent using ICICI Bank Card XX2002 on 12-Aug-26 on RAZORPAY RETAIL. Avl Limit: INR 27,765.00. If not you, call 1800 2662/SMS BLOCK 2002 to 9215676766.` | CARD SPEND ₹746 · ICICI card ••2002 · merchant "RAZORPAY RETAIL". This is a gateway with no real merchant behind it, so the category is **Others** · available limit ₹27,765 |
| C2 | `ICICI Bank Credit Card XX5008 Statement is sent to pi************di@live.in. Total of Rs 1,640.00 or minimum of Rs 100.00 is due by 07-NOV-26.` | STATEMENT · ICICI card ••5008 · total ₹1,640 · minimum ₹100 · due 2026-11-07 → schedule reminders. Do **not** store the email address. |
| F1 | `Rs.130 paid at Basheerapur Toll Plaza for DL00XX0000 on 21-10-2026 10:24:13 with ICICI Bank FASTag. Bal Rs.325. Call 18002100104 for dispute` | DEBIT ₹130 · account **ICICI FASTag (DL00XX0000)** · merchant "Basheerapur Toll Plaza" · **Transport / Fuel** · balance ₹325 |
| F2 | `Recharge of Rs. 500.00 for ICICI Bank FASTag linked veh. no. DL00XX0000 is processed on 17/10/2026 17:07:41 . Balance is Rs. 730.00 .` | CREDIT to the FASTag account ₹500 · **Transfers** (a wallet top-up, not spend) · balance ₹730 |

➡ Add a **Wallet / FASTag** account type: it has a balance, counts towards net worth, and its top-ups are transfers.

### 13.4 SBI Card
| # | Sample | Expected |
|---|---|---|
| S1 | `Rs.240.00 spent on your SBI Credit Card ending 2383 at FASHNEARTECHNOLOGIESPRI on 23/10/26. Trxn. not done by you? Report at https://sbicard.com/Dispute` | CARD SPEND ₹240 · SBI card ••2383 · merchant "FASHNEARTECHNOLOGIESPRI" (this is Meesho's legal entity, so add an alias to **Meesho** → **Shopping**) · 2026-10-23 |
| S2 | `We have received payment of Rs.1,628.12 via BBPS & the same has been credited to your SBI Credit Card. Your available limit is Rs.196,000.00.` | CARD PAYMENT ₹1,628.12 → **Transfers** · available limit ₹1,96,000. **No card number in the SMS:** if the user has only one SBI card, assign it there; otherwise put it in the "Unassigned" queue. |
| S3 | `Dear Cardholder, your payment of INR 906.30 at Amazon is due on 29/08/2026 and will be processed through your credit card ending 2383 as per e-Mandate (SiHub ID: XXXXXXXX) registered by you. ...` | **UPCOMING AUTO-DEBIT, not a transaction.** Create or update a recurring item "Amazon ₹906.30" on card ••2383, next date 2026-08-29. Show it in Reminders and Subscriptions. |
| S4 | `Dear Cardholder, with the Balance Transfer on EMI facility on your credit card, you can avoid high interest rates! ...` | **IGNORE** (promotion) |
| S5 | `Dear Cardholder, we have received your request to increase the Credit Limit ...` / `... credit limit enhancement ... cannot be processed due to internal policy ...` | **IGNORE** (service request) |
| S6 | All `OTP ...` / `... is the OTP ...` / `The verification code to confirm ...` / `OTP to view your CKYC Number ...` | **IGNORE.** Detect `OTP`, `verification code`, `One Time Password` anywhere in the message, including when it starts with digits ("464112 is the OTP…"). **Never store OTP messages** (not even in the "Unrecognised" queue). |

### 13.5 HDFC Bank credit cards
| # | Sample | Expected |
|---|---|---|
| H1 | `Txn Rs.110.00\nOn HDFC Bank Card 0021\nAt *ymkc@ptys \nby UPI 222444231460\nOn 28-10\nNot You?\nCall 18002586161/SMS BLOCK CC 0021 to 7308080808` | CARD SPEND ₹110 · HDFC card ••0021 (a **RuPay credit card used on UPI**) · merchant `*ymkc@ptys` · UPI ref 222444231460 · date 28 Oct (year inferred) |
| H2 | Same as H1 but `At NA` | merchant **"Unknown (UPI)"**, category **Others** |
| H3 | Same as H1 but `At paytmqr72zcin@ptys` / `At Q696637768@ybl` / `At gpay-12194048688@okbizaxi` | merchant = the VPA. Never use QR merchant VPAs as names. Learned rules can still key on the exact VPA. |
| H4 | `Statement Generated:\nFor HDFC Bank Credit Card XX7674.\nView: https://...\nPay: https://...` | STATEMENT event for card ••7674 (a **different** card from ••0021), **with no amounts.** Mark the card "Statement ready". Ask the user to enter the total and due date with a one-tap prompt on the card. Do not store the links. |
| H5 | `090965 is your SECRET One Time Password (OTP) on your HDFC BANK Credit Card transaction ...` | **IGNORE** (OTP) |

### 13.6 Matching rules shown by these samples
- **CRED Club debits from ICICI count as credit card bill payments, so they are Transfers.** Example: ICICI debit ₹1046.00 to "CRED Club" on 23-Oct and SBI Card "payment of Rs.1,046.00 received". Link the pair when the amount matches within ±3 days. **If no card credit is found, it is still a Transfer** (CRED is mainly used for card bills).
- **Wallet loads are Transfers.** For example, "Amazon Pay Bala…" is an Amazon Pay balance top-up.
- **Default merchant rules** (all of them can be changed and learned):

  | Payee contains | Category |
  |---|---|
  | `Mother dairy` | Groceries |
  | `PHARMAC` / `PHARMACY` / `CHEMIST` / `MEDICAL` | Health |
  | `HARDWARE` | Shopping |
  | `Toll Plaza` | Transport / Fuel |
  | `FINLEASE`, `FINANCE`, `FINANCI`, `LOAN`, `LOANPAY`, `CREDIT` (for example "KHOSYA FINLEASE", "FINAGLE FINANCI", "INSTANTLOANPAY") | **Rent / EMI** |
  | `Invest` (for example "Weekline Invest") | **Investments** |

  Payee names are cut off at about 15 characters in ICICI messages, so match on **prefixes and partial words**.
- **Payments to individuals** (personal names with no business keyword) default to **Others**.
  - Mark them as *person-to-person* so the user can bulk-categorise them.
  - Learn rules per payee name.
- **Credits from individuals** default to **Other income**, unless they are matched as a transfer between the user's own accounts.

### 13.7 New card and account facts from these samples
The app should auto-create these accounts on the first scan:

| Account | Type |
|---|---|
| ICICI ••468 | Savings |
| ICICI cards ••2002 and ••5008 | Credit cards |
| ICICI FASTag | Wallet / FASTag |
| SBI Card ••2383 | Credit card (other SBI card numbers appear only in OTP messages, so ignore them) |
| HDFC ••0021 | Credit card, used on UPI |
| HDFC ••7674 | Credit card |

---

## 14. Public SMS formats researched online (supplements Section 13)

The user has no more samples, so formats were collected from public sources. The **full catalogue** is in three files next to this spec. Copy them into the project as `docs/sms-research/`.

| File | Covers | Real message text quoted? |
|---|---|---|
| `sms-research/01-banks-cards-wallets.md` | 74 formats (F01–F74), with over 100 sample messages: SBI, HDFC, Axis, Kotak, BoB, PNB, Canara, Union, IDFC, YES, IndusInd, AU, Federal, BOI, Indian Bank, Central, Bandhan; credit cards (HDFC, Axis, YES, Kotak, IDFC, IndusInd, Scapia, SBI Card, BOBCARD, PNB, AU, AmEx, OneCard); wallets (Amazon Pay, PhonePe, PayZapp, CRED, slice, Airtel PB); refunds, reversals, failed transactions; ignore list; generic fallback recipe; sender ID table | Yes, for ~69. Reconstructed for 5 |
| `sms-research/02-mutual-funds-epf-deposits-loans.md` | 41 formats: HDFC MF and Navi MF allotment, bank-side SIP and redemption, EPFO contribution, FD and savings interest, India Post, loan EMI recovery, insurance e-mandate | Yes, for 22. Partial for 2. Reconstructed for 17 |
| `sms-research/03-brokers-depositories-exchanges.md` | 25 formats: CDSL debit, NSE traded value, NSE funds/securities balance, Zerodha payout, ACH dividend credits, CDSL TPIN (ignore) | Yes, for ~10 (some partial). Reconstructed for ~15 |

### 14.1 How to use these
1. **Write rules and tests only for formats with real text quoted**: those labelled verbatim in the files, plus Section 13. Each one becomes a JSON rule plus a `ParserTest` case.
2. **Reconstructed formats get no institution-specific regex.** The generic fallback parser (14.2) handles them. If it fails, they go to the "Unrecognised SMS" screen. When the user later supplies real samples, add the rules and rebuild.
3. **Mark every rule with `source`** (`user` / `public-verbatim` / `public-reconstructed`) and a `verifiedOn` date, so rules from real user samples take priority.
4. **Licence.** Many samples come from the test files of open-source parsers:
   - PennyWise AI is **AGPL-3.0**.
   - indian-bank-sms-parser and Umber are MIT.
   Use the *message texts* as test data and reference. **Do not copy any parser source code.** Write all rules and code from scratch.

### 14.2 Generic fallback parser (cross-bank patterns)
Implement these exactly. The details are in `01-banks-cards-wallets.md` §6.

1. **Normalise:**
   - Apply **Unicode NFKC**. SBI Card sends some words in mathematical-sans lookalike letters (𝗌𝗉𝖾𝗇𝗍).
   - Collapse spaces but keep newlines as separators.
   - Treat `₹`, `Rs`, `Rs.`, `Rs:`, `Rs..`, `INR`, `INR.` as one currency token.
2. **Amount:**
   - Accept any comma grouping and 0–3 decimals (`305.0`, `5.000`).
   - SBI UPI debits have **no currency marker** (`debited by 305.0`), so handle that case.
   - Take the first amount that is **not** preceded by `Bal|Avl|Avbl|Available|limit|Lmt`.
3. **Check for negation and future tense before looking for the direction.** These phrases mean the message is not a transaction: `not debited`, `will be debited/deducted`, `auto-debited`, `due on/by`, `requested`, `declined`, `failed`, `could not`. (ICICI: "Your account is **not debited** with Rs 500…")
4. **Direction keywords:**
   - Debit: `debited|Dr|spent|Sent|paid|withdrawn|w/d|transferred from|used on`.
   - Credit: `credited|Cr|received|deposited|refund|reversed|cashback`.
   - BoB credits read `Cr. to <vpa>`.
5. **Account or card last digits:** use the patterns in §6.4 of `01-banks-cards-wallets.md`. As a fallback, read the digits from `SMS BLOCK … <digits>`, which is where HDFC and Axis sometimes put the card number.
6. **Counterparty:** the ordered pattern list in §6.5. Axis cuts merchant names to about 11 characters and ICICI to about 15, so merchant rules match on prefixes.
7. **Reference:** UPI RRN (12 digits), NEFT UTR, IMPS reference.
8. **Date:** all formats from Section 13.1 plus `ddMMMyy` (SBI), `yyyy-MM-dd:HH:mm:ss` (HDFC cards), `ddMMMyyyy HH:mm` (Federal), `MMMM d, yyyy`. **Use the received date when the date is missing, more than 2 days after the SMS arrived, or more than 45 days before it** (almost always a mis-parse).
9. **Channel tags:** UPI / IMPS / NEFT-RTGS / ATM / mandate (ACH, NACH, ECS, AutoPay) / card / salary / interest. They drive the default category, for example salary → *Salary* and interest → *Interest*.
10. **Sender check:** accept only DLT headers (`XX-ABCDEF`, optionally followed by `-S/-T/-P/-G`) whose core is on the institution list. **Reject bank-style messages from ordinary 10-digit mobile numbers**, because these are phishing. Put the sender table from `01-banks-cards-wallets.md` §6 into `senders.json`.

### 14.3 Additions to the ignore list (from the research)
None of these are transactions:
- Pre-debit and mandate notices ("will be debited", "E-Mandate! … will be deducted").
- Mandates being created or cancelled.
- UPI collect requests.
- OTPs that contain amounts ("123456 is your OTP for a Dr. INR 500.00…").
- Bill-due alerts from billers.
- Balance-only messages.
- EMI conversion and loan offers.
- **ASBA/IPO blocks.** The money is held, not debited. Optionally show them as a "blocked" amount.
- Vouchers and rewards.
- ATM free-transaction counts.
- SIP **registration**. Only "SIP Txn Successful" is a real debit.
- UPI Lite disabled.
- Standing-instruction setup.
- Telecom recharge confirmations from telecom senders.
- NSE/BSE traded-value and balance snapshots.

**Store, don't book (snapshots and schedules):**
- **NSE/BSE traded-value and funds/securities balance messages.** Store them as broker snapshots shown on the investment account. Never create cash transactions from them.
- **E-mandate and AutoPay pre-debit notices.** Keep using these to update the subscription list with the next date and amount, as in 13.4 S3.

### 14.4 Investment-specific notes
- **One SIP can produce up to four SMS:**
  1. the bank debit,
  2. the platform notice (for example Groww),
  3. the AMC "processed" message,
  4. the AMC allotment.

  Create **one** BUY activity from the allotment, which carries units, NAV and folio. Mark the bank debit as **Investments** (a transfer of money into the investment) and link it to that activity. Merge the other two into it.
- **EPFO:**
  - **Message:** "… passbook balance against <member ID> is Rs. X. Contribution of Rs. Y for due month MMM-yy has been received."
  - **What to book:** a DEPOSIT of Y into the EPF account, dated to the **received date**. Store the due month as a note. Set the account balance to X.
  - **Regex:** the balance amount comes first, so anchor the contribution amount on `Contribution of Rs`.
- **Dividends** arrive as bank credits with an `ACH*<COMPANY>*` narration (ICICI, also seen as `Info ACH*…`) or `ACH Credit:<COMPANY>` (other banks). These are DIVIDEND activities. The company name is the asset.
- **CDSL debit alerts** (`CDSL: DEBITED IN A/C … <qty> <security> TOWARDS <reason> ON dd/MM/yyyy`) are SELL or redemption activities with quantity but **no price**. Record the quantity and leave the price empty.
- **Zerodha "withdrawal request … processed"** is a WITHDRAWAL from the broker account. It also arrives for automatic quarterly settlements, which are still withdrawals.
- **No real samples for:** broker trades and fund additions, NSDL, CAMS/KFintech allotment, NPS, FD booking and maturity, SGB, NBFC EMIs, LIC. These use the fallback parser and the "Unrecognised" screen until the user supplies samples.

### 14.5 Rebuild plan when more samples arrive
1. The user exports messages from the "Unrecognised SMS" screen. Add an **"Export unrecognised (anonymised)"** action: it replaces names with `NAME`, keeps the last 4 digits of numbers, masks VPAs to their handle (for example `@ybl`), and writes a text file through the Storage Access Framework.
2. Add the samples to the tests, write the rules, run the parser tests, and rebuild the APK. Rules can also be shipped as an importable rule pack (11.6), so no rebuild is needed.
