# Google Play listing — en-US (canonical copy)

**Status:** draft for the Play **prod** build (`com.awbuilds.auraspend`), not the
`fdroid` build. The copy in `fastlane/metadata/android/en-US/` describes the
**no-Google-Play-Services** build (no Drive backup) and must not be pasted into
the Play listing.

**Last updated:** 2026-10-17

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
Offline expense tracker: budgets, subscriptions, insights. No ads, no tracking.
```

79/80 characters. Keep this one: it carries the primary keyword, the offline
differentiator, three feature nouns and the privacy promise. If the title takes
`SMS`, the short description does not need to repeat it.

## Full description (4000 max)

```
Turn your bank SMS into a clear picture of your money — on your phone, not in someone else's cloud.

AuraSpend is an offline bank SMS expense tracker, budget app and money manager built for India. It reads your bank and payment SMS on your phone, turns them into transactions, and helps you see where your money goes — without an AuraSpend account, without ads, and without sending your messages to AuraSpend.

WHY AURASPEND

• Automatic SMS tracking: 50+ Indian banks, cards and wallets are recognised, turning messages like "Rs 1,499 debited..." into categorised expenses.
• Works offline: parsing, budgets and insights run on your phone. Day-to-day use needs no internet.
• Private by design: no AuraSpend account, no AuraSpend servers, no ads, no analytics, no tracking SDKs.
• You stay in control: correct any category, edit any transaction, and export everything as CSV whenever you want.

EVERYDAY MONEY, ORGANISED

• Expense tracker with categories, notes, search and filters.
• Monthly budgets and budget health, so you know what is left before you spend.
• Recurring-payment and subscription detection: Netflix, Spotify, rent, insurance and other repeats.
• Savings goals, plus a Plan hub that brings budgets, subscriptions and goals together.
• Insights: category breakdowns, daily spending and month-by-month trends.
• UPI, NEFT, IMPS, ATM, card and wallet messages are recognised by sender, not just by keywords.
• Indian number formatting (lakh and crore) with INR by default; 30 currencies are available for display.

BUILT FOR PRIVACY

Your bank SMS is processed on your phone. AuraSpend does not upload your messages to its own servers and does not include ads, analytics or third-party tracking.

If you choose Google Drive backup (Play build), the backup is written to your own Google Drive account so you can restore it on another device. You can use the app entirely without it. The database is encrypted at rest, and you can add a 4-digit app lock with biometric unlock.

AuraSpend is free and open source under AGPL-3.0-or-later, so the privacy claims can be checked, not just trusted.

MADE FOR INDIAN BANK SMS

Bank messages are messy: multiple amounts, balance tails, card limits, UPI references, reward points and fraud warnings. AuraSpend routes by sender, uses per-bank parsers, and keeps a Skipped list so messages it cannot read stay visible instead of disappearing.

An optional on-device AI model (about 22 MB) can refine categorisation. It downloads only after you tap to consent, is SHA-256 verified, and the app works without it.

WHO IT IS FOR

• Salaried professionals who want their salary, bills and card spends in one place.
• Students and first-jobbers building a budget without a spreadsheet.
• Families tracking shared expenses, rent and school fees.
• Anyone who wants a private spending tracker without linking a bank account.

ALSO INCLUDED

• English and Hindi.
• Light, dark and AMOLED themes.
• CSV export and import.
• No sign-up, no paywall, no ads.
• Read-only SMS access: AuraSpend never sends SMS and never asks for bank credentials.

AuraSpend is a passive tracker: it never moves money and never connects to your bank account.

Download AuraSpend and turn your bank SMS into a money timeline you can actually use.
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
2. **Visuals are pre-terracotta — accepted for 0.1.6.** The icon, feature
   graphic and seven screenshots were captured on 2026-10-10, before the 0048
   UI revamp. They still show the purple Aurora brand and Plus Jakarta Sans. The
   owner chose to ship 0.1.6 with them anyway; regenerate from the terracotta
   build in a later release.
3. **Hindi copy is F-Droid copy.** `fastlane/metadata/android/hi-IN/` also says
   Drive backup is unavailable. The Play `hi-IN` listing needs a Play-specific
   translation of the copy above; keep the F-Droid copy separate.
4. **The `fastlane/` tree is not the Play listing.** It describes the no-GMS
   build. Either mark it explicitly as F-Droid metadata or move Play copy under
   `docs/store/play/` and keep `fastlane/` for the F-Droid build.
5. **Release notes are refreshed for 0.1.6.** The tag workflow reads
   `docs/store/whatsnew/`; the old 0.1.3 notes file was removed.
6. **No ratings/social proof yet.** The long description cannot claim ratings
   before the closed test converts. Revisit after production.
