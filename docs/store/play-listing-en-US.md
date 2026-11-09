# Google Play listing — en-US (canonical copy)

**Status:** draft for the Play **prod** build (`com.awbuilds.auraspend`), not the
`fdroid` build. The copy in `fastlane/metadata/android/en-US/` describes the
**no-Google-Play-Services** build (no Drive backup) and must not be pasted into
the Play listing.

**Last updated:** 2026-10-17

**Release status:** `v0.1.6` (versionCode 8) is uploaded to Play closed testing
(`alpha`) and sent for review. The en-US **title is live** as
`AuraSpend: SMS Expense Tracker` (30/30); the store **tags** remain a Play
Console-only field. The first upload (versionCode 7) included the
terracotta UI and was superseded before production. The tag workflow uploads the
signed AAB and the `docs/store/whatsnew/` notes, but it does **not** upload the
listing text below — paste the title, short and full description into the Play
Console. The pre-terracotta store visuals match the app.

---

## Title (30 max)

```
AuraSpend: SMS Expense Tracker
```

30/30 characters. The current Play title is `AuraSpend: Expense Tracker`
(26/30); adding `SMS` targets the high-intent "bank SMS expense tracker"
queries while keeping `Expense Tracker`. Brand-only `AuraSpend` is not
recommended: the title is the strongest ASO field.

## Short description (80 max)

```
Automatic SMS expense tracker, budget planner & money manager. Private, offline.
```

80/80 characters. It carries the primary keywords (automatic SMS expense tracker, budget planner, money manager) and the privacy/offline differentiators without violating Play Store promotional guidelines.

## Full description (4000 max)

```
Turn your bank SMS into an effortless, private money manager.

AuraSpend is an automatic SMS expense tracker, budget planner, and daily money manager built for India. It reads your transactional bank SMS directly on your phone, converting them into organised expenses, monthly budgets, and spending insights — 100% offline, with no account registration and no bank logins required.

Tired of typing every ₹20 chai or UPI payment? AuraSpend automates expense tracking so you always know where your money goes.

KEY FEATURES

1. AUTOMATIC SMS EXPENSE TRACKER
• Instant Expense Logging: Automatically turns bank SMS alerts into organised, categorised expenses.
• Multi-Account & Card Tracker: Tracks credit card spends, debit card purchases, ATM cash withdrawals, net banking, refunds, and bank transfers.
• UPI Payment Tracker: Automatically logs UPI payments made via Google Pay, PhonePe, Paytm, CRED, and BHIM.
• 50+ Indian Financial Institutions: Supports major banks including HDFC Bank, SBI, ICICI Bank, Axis Bank, Kotak Mahindra, PNB, and Bank of Baroda.
• Smart Merchant Detection: Accurately identifies merchant names, UPI IDs, and transaction types.

2. SMART BUDGET PLANNER & BUDGET APP
• Monthly Budget Planner: Set monthly budgets for overall spending or specific expense categories.
• Visual Budget Rings: Monitor budget health and category limits at a single glance before you spend.
• Prevent Overspending: Know exactly how much safe-to-spend money you have left each month.

3. SUBSCRIPTION MANAGER & BILL TRACKER
• Recurring Subscriptions: Automatically detects repeat payments like Netflix, Spotify, YouTube, gym, and cloud storage.
• Bill Management: Track repeat household bills, rent, electricity, Wi-Fi, insurance premiums, and SIP investments in one unified Plan hub.

4. DAILY EXPENSE MANAGER & SPENDING TRACKER
• Daily Expense Tracking: Clean visual timeline of your daily income and expense transactions.
• Spending Analytics & Reports: Clear category breakdowns, 7-day cash flow charts, and month-over-month spending trends.
• Quick Cash Logging: Easily record cash expenses in seconds with smart category and merchant suggestions.
• Powerful Search & Filters: Filter past transactions by merchant, category, account, card, or date range.

5. PRIVATE, OFFLINE & SECURE BY DESIGN
• 100% On-Device Processing: Your bank SMS messages are parsed and analysed entirely on your phone.
• No Bank Login Required: AuraSpend never asks for net banking credentials, bank account numbers, or OTPs.
• Strictly Read-Only SMS: Reads only transactional bank messages. Never reads personal chats, never sends SMS, and never touches OTPs.
• Data Ownership: Your financial data stays on your phone unless you choose to back it up to your personal Google Drive.
• Encrypted & Protected: Encrypted local database with PIN and biometric app lock support.
• Ad-Free Money Manager: No ads, no analytics trackers, and no third-party data broker SDKs.
• Open Source: Fully open source under AGPL-3.0 for verified transparency.

6. BUILT FOR INDIA
• Indian Number Formatting: Native Lakh and Crore (₹) formats with INR and 30+ currencies.
• Bilingual: Available in English and Hindi (हिन्दी).
• Clean Design: Distraction-free design with Light, Dark, and AMOLED themes.
• Complete Data Portability: Export your complete transaction history to CSV anytime.

WHO IS THIS EXPENSE TRACKER FOR?
• Salaried professionals managing monthly salary, credit card bills, and household expenses.
• Students and young professionals building healthy budgeting habits without spreadsheets.
• Families tracking daily household hisab-kitab, groceries, school fees, and rent.
• Anyone looking for a private, ad-free money manager and spending tracker.

Take control of your personal finances today. Download AuraSpend and experience automatic, private SMS expense tracking!
```

**Length:** 3,270 / 4,000 characters (counted after paste; re-count in the Play
Console before saving).

## Why this is ASO-shaped

| ASO lever | How the copy uses it |
|---|---|
| Primary keyword | “expense tracker” in the title, short description, first paragraph and feature headings |
| Secondary keywords | “money manager”, “bank SMS”, “SMS expense tracker”, “budget app”, “spending tracker”, “offline”, “UPI”, “India”, “INR” |
| First two lines | visible before “Read more”; contains the hook, “bank SMS” and “expense tracker” |
| Differentiation | offline, no AuraSpend account, no ads/tracking, open source, Indian bank formats, optional on-device AI |
| Trust | privacy section with the Drive exception, read-only SMS, no bank credentials, app lock + encrypted database |
| Long-tail | “salary”, “students”, “families”, “rent”, “school fees”, “UPI”, “NEFT”, “IMPS”, “ATM” |
| Conversion | benefit-led bullets, “who it is for”, clear CTA |
| Policy fit | no “best/#1” claims, no misleading privacy absolute, no competitor names |

## Gaps to fix before calling the whole listing ASO-ready

1. **The Play Console copy is not versioned.** Handoff 0017 says the Play full
   description is 2,355/4,000 chars and lives only in the Console. The repo
   cannot review or diff it. Paste the text above into the Console (or copy the
   Console text back here) so there is one source of truth.
2. **Visuals are pre-terracotta — and now match the app.** The icon, feature
   graphic and seven screenshots show the purple Aurora brand and Plus Jakarta
   Sans, which is the design shipped in 0.1.7. Regenerate them only when the
   terracotta design ships.
3. **Hindi copy is F-Droid copy.** `fastlane/metadata/android/hi-IN/` also says
   Drive backup is unavailable. The Play `hi-IN` listing needs a Play-specific
   translation of the copy above; keep the F-Droid copy separate.
4. **The `fastlane/` tree is not the Play listing.** It describes the no-GMS
   build. Either mark it explicitly as F-Droid metadata or move Play copy under
   `docs/store/play/` and keep `fastlane/` for the F-Droid build.
5. **Release notes are refreshed for 0.1.7.** The tag workflow reads
   `docs/store/whatsnew/`; the old 0.1.3 notes file was removed.
6. **No ratings/social proof yet.** The long description cannot claim ratings
   before the closed test converts. Revisit after production.
