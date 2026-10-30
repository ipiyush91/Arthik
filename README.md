# Arthik

Arthik is a private, offline personal-finance app for Android, built for India. It reads your bank, credit card, UPI and investment SMS and shows where your money goes: monthly spend by bucket, card dues, budgets, subscriptions and net worth. It has a dark "liquid glass" look.

- **100% on-device.** There is no `INTERNET` permission. The database is encrypted with SQLCipher, and its key is held by the Android Keystore. A fingerprint, face or PIN lock protects the app. `FLAG_SECURE` keeps it out of screenshots, and Android's own backup is turned off.
- **SMS parsing.** A rule engine reads the formats in JSON rule packs, with a generic fallback for anything without its own rule. It deduplicates messages, links self-transfers, credit card bill payments and refunds, and learns your merchant → bucket choices.
- **Kept light on purpose.** It has no Hilt and no chart, icon or blur libraries. The glass effect needs no real blur, and the charts are drawn on a Compose Canvas. The release APK is arm64-only and shrunk with R8.

The full product spec is in [`docs/APP_BUILD_SPEC.md`](docs/APP_BUILD_SPEC.md). The SMS research behind the rules is in [`docs/sms-research/`](docs/sms-research).

## Project layout

| Module | What it contains |
|---|---|
| `core/` | Pure Kotlin/JVM with no Android dependency: SMS normaliser, rule engine and generic parser (`parse/`), rule packs (`src/main/resources/arthik/rules/*.json`), sender table, merchant dictionary and categoriser, deduplication and transfer matching, recurring detection, Wealthfolio and transactions CSV. All of it is unit-tested. |
| `app/` | Android app: Room + SQLCipher (`data/`), ingest pipeline, WorkManager jobs (`work/`), SMS receiver and reader (`sms/`), encrypted backup and CSV (`backup/`), Glance widget, and the Compose UI (`ui/`). |

## Build

Requirements: JDK 17 or newer, and the Android SDK with platform 36 and build-tools installed. Use Android Studio, or set `ANDROID_HOME`.

Run the parser unit tests (they don't need the Android SDK):

```bash
./gradlew :core:test
```

Build a debug APK:

```bash
./gradlew :app:assembleDebug
```

### Signed release APK

The script below creates the signing key and a `keystore.properties` file. Both are git-ignored, and you only do this once:

```bash
scripts/make-keystore.sh
```

Then build the release APK:

```bash
./gradlew :app:assembleRelease
```

The build writes two release APKs to `app/build/outputs/apk/release/`:
- `app-arm64-v8a-release.apk`, the small one for modern phones;
- `app-universal-release.apk`, which works on any phone.

> **Keep `arthik-release.jks` and its passwords safe.** Android only installs an update over the existing app if it is signed with the same key. Without the key you would have to uninstall the app first, which deletes Arthik's data. Make a backup before you do that.

## Install on Android 16 (sideloading)

Arthik reads SMS, which the Play Store only allows for default SMS apps. So you install the APK yourself:

1. **Allow the installer.** Copy the APK to the phone and open it. If asked, allow your file manager or browser to install unknown apps (Settings → Apps → Special app access → Install unknown apps).
2. **Allow restricted settings.** Android blocks SMS permissions for sideloaded apps at first:
   1. Open Settings → Apps → Arthik.
   2. Tap **⋮** (top right) → **Allow restricted settings**, and confirm with your PIN.
   3. Go back to Arthik and tap **Allow SMS access** again.
3. **Finish onboarding.** Allow notifications if you want due-date and budget alerts, and turn on the app lock. Arthik then scans the last 12 months in the background.

## Add new SMS formats (no code changes)

Rules live in `core/src/main/resources/arthik/rules/*.json`. Each rule is a template with placeholders:

```json
{ "id": "icici.sb.upi.debit", "institution": "ICICI Bank", "source": "user", "priority": 200,
  "accountType": "SAVINGS", "direction": "DEBIT", "channel": "UPI",
  "patterns": ["ICICI Bank Acct {acct} debited for {amount} on {date}; {merchant} credited. UPI:{ref}\\."],
  "sample": "ICICI Bank Acct XX468 debited for Rs 91.00 on 30-Oct-26; NAME credited. UPI:120807593798. ..." }
```

The placeholders are:

| Placeholder | Matches |
|---|---|
| `{amount}`, `{balance}`, `{limit}`, `{total}`, `{min}` | An amount. Every `Rs` / `Rs.` / `INR` / `₹` form is normalised to `Rs ` first. |
| `{acct}` | Masked account or card digits, for example `XX468`. |
| `{date}` | Any date format seen in Indian bank SMS. |
| `{merchant}`, `{ref}`, `{any}` | The payee, a reference number, and any text to skip. |
| `{text:name}`, `{num:name}`, `{date:due}` | The same kinds of value, captured under a custom name. |

A space in a template matches any amount of whitespace. The rule types are:
- `TXN`: a transaction;
- `STATEMENT`: a card statement (total, minimum due, due date);
- `UPCOMING`: an auto-debit or e-mandate notice;
- `SNAPSHOT`: a balance only;
- `IGNORE`: a message to skip.

Rule order: rules from your own real samples (`"source": "user"`) win over public ones, then higher `priority` wins.

There are two ways to add rules:
- **In the code:** add a rule and a test case in `core/src/test/...`, run `./gradlew :core:test`, and rebuild.
- **On the phone:** put rules in a `.json` file shaped `{ "version": 1, "rules": [ ... ] }` and load it with **Settings → Import rule pack**. Imported rules replace built-in ones with the same `id`. Rescan afterwards.

To collect samples, use **Settings → Export unrecognised SMS (anonymised)**. It masks names, numbers and UPI IDs.

## Rename the app

- **The name shown on the phone** is `app_name` in `app/src/main/res/values/strings.xml`.
- **The package ID** is `applicationId` in `app/build.gradle.kts`. Changing it makes a different app on the phone, so back up first.

## Known limits (v1)

- Formats that were only described in public sources (not quoted) have no dedicated rule. The generic parser or the "Unrecognised SMS" screen handles them. See `docs/sms-research/`.
- RCS messages and app notifications are not read.
- Hindi and regional-language SMS are not parsed.
- Holdings built from SMS alone can be incomplete. Add opening holdings, or import a Wealthfolio CSV.
