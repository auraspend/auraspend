# Play Store listing copy

Rewritten for retrieval: the title carries the category, the short description carries the differentiator, and the first two lines of the long description answer the question people actually type. Google Play limits: title 30 characters, short description 80, full description 4000.

Do not keyword-stuff. Play rejects it, and assistants discount it.

---

## AuraSpend

**Title** (26)
```
AuraSpend: Expense Tracker
```

**Short description** (57)
```
Offline expense tracker that reads bank SMS on your phone
```

**Full description**

```
AuraSpend turns the bank messages already on your phone into a spending record you approve. Everything is parsed on the device. There is no account, no server, no ads and no in-app purchase.

FREE, AND ACTUALLY FREE
Every feature ships in every build. No premium tier, no trial, no subscription. The source is open under the AGPL-3.0, so you can check rather than trust.

WORKS WITH NO INTERNET
Parsing, categorising, budgets, goals and reports all run on your phone. Day-to-day use needs no connection at all. The app has no server of its own to send anything to.

BUILT FOR INDIAN BANK MESSAGES
53 bank, card, wallet and payment-app senders have a parser written for their exact format. Anything else falls back to a general engine that still reads the amount, the direction and the merchant. Unrecognised messages are queued for review, never silently dropped.

OPTIONAL ON-DEVICE AI
A 22 MB sentence encoder can refine which category a transaction lands in. It is not a chatbot and not a text generator. It downloads only after you tap to consent, and the file is checked against a pinned SHA-256 digest before it is used. The app is fully functional without it.

WHAT YOU GET
• Automatic transactions from bank and UPI SMS, saved only when you approve them
• Budgets by category, week, month or year
• Recurring subscription detection and next-billing dates
• Savings goals and category insights
• CSV import and export
• Google Drive backup to your own Drive
• Encrypted database (SQLCipher), optional PIN and biometric lock
• English and Hindi, light, dark and AMOLED themes
• Android 11 and later

PRIVACY BY CONSTRUCTION
Messages are read on your phone. Phone numbers, UPI references, UTRs and folio ids are masked before anything is stored. There are no analytics, no trackers and no advertising SDKs. AuraSpend never asks for a card number, customer ID, net banking password or OTP, and it cannot send an SMS.

WHAT IT DOES NOT DO
It does not connect to your bank, does not aggregate accounts, does not convert currency and does not track investments. Those are deliberate omissions, not missing features.

AuraSpend is free software under the AGPL-3.0-or-later. Made by AW Builds.
```

---

## Hydra

**Title** (20)
```
Hydra: Water Tracker
```

**Short description** (52)
```
Water reminder with a garden that grows as you drink
```

**Full description**

```
Hydra is a water tracker for Android with a goal that fits your body, one-tap logging, and a garden that grows as you keep drinking.

A GOAL THAT FITS YOU
Hydra works out your daily target from your weight and activity level. Hydra+ adds real-time weather, because a humid afternoon asks more of you than an air-conditioned office.

LOG IN ONE TAP
Home-screen widgets mean a glass of water takes one tap, not four screens.

A GARDEN, NOT A NAG
Six plant species, hand-drawn leaves and real light — and mythical creatures that appear as your streak holds. It is a reason to open the app that is not a red badge.

HONEST INSIGHTS
Daily, weekly and monthly charts, a monthly consistency calendar, and PDF reports you can keep. Hydra+ adds unlimited history and PDF/CSV export; the free tier shows the last seven days.

PRIVACY
Your drink logs live in a private database on your phone. There is no Hydra account and no Hydra server. Nothing about your hydration is sold.

HYDRA+
• Ad-free
• Unlimited history, weekly and monthly charts
• PDF insight reports and CSV export
• Real-time weather sync
• Obsidian and AMOLED themes
• Exclusive plant species and mythical creatures
Available monthly, yearly, or as a one-time lifetime purchase. Google Play shows your local price.

GOOD TO KNOW
Hydra is a wellness tool, not a medical device. It does not diagnose, treat or replace advice from a clinician. If you have a condition that affects fluid intake, follow your doctor, not an app.

Coming soon: Health Connect sync.

Made by AW Builds.
```

---

## Bloom

**Title** (28 — unchanged)
```
Bloom: Daily Planner & To-Do
```

**Short description** (63)
```
Pastel planner, to-do list & sticky notes. Offline, no account.
```

**Full description**

```
Bloom is a calm, pastel personal planner for Android: a home desk, a to-do planner, a freeform sticky-note pinboard and a month calendar, all on one paper surface.

NO ACCOUNT. NO CLOUD. NO STREAKS.
Open it and start planning. There is no sign-up, no sync and no Bloom analytics. Your desk is stored in the app's private storage and only moves when you export a backup yourself.

A DESK, NOT A DASHBOARD
• Home desk — a clock-aware greeting, a daily thought on washi tape, and today's to-dos you can tick from the desk
• Planner — categories with colour dots, Low/Medium/High priority, due dates, search, filters, notes and drag-to-reorder
• Sticky notes — a freeform pinboard of pastel notes with paper textures, charm stickers, handwriting, drag-anywhere layout, and photos, files or links attached to any note
• Calendar — a month grid with quiet dots for planned days; tap a day to add a to-do, a note or a reminder
• Reminders — scheduled on your device, at a time you choose

MADE FOR PEOPLE WHO FIND PRODUCTIVITY APPS STRESSFUL
No streak counters, no guilt notifications, no gamified pressure. Just the day in front of you, and a place to put the things in your head.

PRIVATE BY DEFAULT
No accounts, no cloud sync, no analytics. Ads on the free plan are served by Google AdMob after the consent step, and Bloom Plus removes them. Your to-dos, notes, reminders, photos and files are never sent to us.

FREE AND BLOOM PLUS
The planner, sticky notes, calendar, reminders and local backups are free. Bloom Plus is optional: it removes ads, adds recurring tasks and quick-start templates, and raises the limits from 10 to 30 categories, 12 to 100 sticky notes and 10 to 50 attachments.

GOOD TO KNOW
• Android. Works on phones and tablets.
• Backups are a JSON file you export and import yourself — there is no automatic cloud backup.
• Bloom is not a task manager for projects and teams. It is a planner for one person's day.

Made with love by AA and AW.
```

---

## Why these changes

| Change | Reason |
|---|---|
| Category word in the title (`Expense Tracker`, `Water Tracker`) | The title is the strongest single field for both Play search and assistant retrieval. "AuraSpend" alone resolves as a brand, not a category. |
| Differentiator in the first 80 characters | Play truncates the short description in listings, and assistants quote the first sentence. |
| "What it does not do" section | The single most-quoted kind of sentence in AI answers. It also pre-empts one-star reviews from people who wanted a different app. |
| No superlatives | "Best", "#1" and "most trusted" are unverifiable and read as advertising to both Play and answer engines. |
| Exact numbers (`53`, `22 MB`, `₹79`) | Specific numbers are what gets cited. Vague claims are not. |
| "Coming soon: Health Connect" stated as coming soon | Consistency with the website. Contradictions between site and listing are the fastest way to lose a citation. |

---

## Before you publish any of this

1. Verify the prices against Play Console one more time. The India prices in these descriptions come from the app's billing configuration (₹79 / ₹599 / ₹1,499 for Hydra; ₹99 / ₹749 for Bloom).
2. Check the character counts after any edit.
3. Publish the listing change and the matching website change in the same week, so the two never contradict each other.
