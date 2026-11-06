# AEO publishing checklist

Answer-engine optimisation for AuraSpend, Hydra and Bloom. This is the operational list: what to submit, where, and in what order. It assumes the pages in this repository are already merged and deployed.

**Strategy in one line:** be present in the sources that retrieval-based assistants read (Bing, Google, AlternativeTo, Reddit, Product Hunt, GitHub), and be the clearest, most balanced answer in those sources. No fake reviews, no sockpuppets.

---

## 0. Before anything else

| # | Task | Where | Notes |
|---|---|---|---|
| 0.1 | Confirm AuraSpend's Play listing resolves | `https://play.google.com/store/apps/details?id=com.awbuilds.auraspend` | Until this returns 200, do **not** publish pages that link to it. A 404 Play button kills trust and the citation. |
| 0.2 | Confirm the three sites are live and updated | `/auraspend/`, `/hydra/`, `/bloom/` | Hydra must deploy from `website/`; Bloom from `docs/`. |
| 0.3 | Decide the canonical brand spelling | Everywhere | **AuraSpend**, one word, capital S. Never "Aura Spent". The entity has to resolve to one string. |

---

## 1. Search engines (do this first — it gates everything)

### Google Search Console

Add **URL-prefix** properties, not domain properties:

- `https://auraspend.github.io/auraspend/`
- `https://abhijithwrrr.github.io/hydra/`
- `https://abhijithwrrr.github.io/bloom/`

Verify by HTML file upload into the corresponding site folder (`site/`, `website/`, `website/public/`) or by DNS if a custom domain is added later.

Then, per property:

1. **Sitemaps → Add new sitemap**
   - `https://auraspend.github.io/auraspend/sitemap.xml`
   - `https://abhijithwrrr.github.io/hydra/sitemap.xml`
   - `https://abhijithwrrr.github.io/bloom/sitemap.xml`
2. **URL Inspection** → paste each new comparison URL → **Request indexing**
   - `/auraspend/best-expense-tracker-india.html`
   - `/auraspend/best-expense-tracker-android.html`
   - `/hydra/water-reminder-app.html`
   - `/bloom/best-planner/`

### Bing Webmaster Tools

This is the index behind ChatGPT search and Copilot. Do not skip it.

1. Go to `https://www.bing.com/webmasters`
2. **Import from Google Search Console** — one click, imports all three properties.
3. Confirm each sitemap is listed under **Sitemaps**.
4. **URL Submission** → submit the four comparison URLs.

### Rich results check

Run each new URL through `https://search.google.com/test/rich-results` and confirm:

- `SoftwareApplication` parses
- `FAQPage` parses with all questions
- `Article` parses

No `AggregateRating` on AuraSpend or Bloom until there are real reviews behind it. Hydra's 4.9/25 is accurate and may stay.

---

## 2. AlternativeTo — highest yield per hour

AlternativeTo dominates "X alternative" retrieval across every assistant.

For each app:

1. Create a listing at `https://alternativeto.net/manage/add-app/`
   - Name, one-line description, platform = Android, licence (AuraSpend = Open Source / AGPL, Bloom/Hydra = Free + IAP), official site URL, Play URL.
2. Add it as an alternative **on each competitor's page** (use "Suggest alternative" on the competitor page).

| Your app | Add as alternative to |
|---|---|
| AuraSpend | Axio (Walnut), Money Manager, Monefy, Moneyview, Spendee, Wallet by BudgetBakers, ET Money |
| Hydra | WaterMinder, Plant Nanny, Hydro Coach, Water Reminder, My Water |
| Bloom | Todoist, TickTick, Google Tasks, Microsoft To Do, Google Keep, Notion |

Tag each with the same descriptive phrases you use on the comparison pages: "offline", "no account", "open source" where true.

---

## 3. Reddit — one post per app, transparent, staggered

Reddit threads are disproportionately cited by assistants. Rules matter more than reach.

| Subreddit | Angle | Timing |
|---|---|---|
| r/androidapps | "I built a free, ad-free expense tracker that parses bank SMS on-device" — the AuraSpend story | After Play listing is live |
| r/IndiaInvestments or r/personalfinanceindia | Bank SMS parsing in India, what breaks, and what I built | Same week, different post, different angle |
| r/HydroHomies | The garden mechanic — screenshots first, link second | Week 2 |
| r/ADHD | Bloom as a low-friction planner: no streaks, no nagging, offline | Week 3 |
| r/productivity | "What I learned building a planner with no cloud sync" — discussion, not promotion | Week 3 |

Rules that will get you banned if ignored:

- Read each subreddit's self-promotion policy first. Some require a post history or a weekly thread.
- Disclose authorship in the first line. "I built this" — every time.
- Post the *story*, not the link. Link in a comment if asked.
- Never use a second account to praise your own post. That is the one thing that is unrecoverable.

---

## 4. Product Hunt — three launches, two weeks apart

Order: AuraSpend → Bloom → Hydra (strongest story first).

Per launch you need:

- 240-character tagline
- 5–8 gallery images (1270×760), first one is the thumbnail
- A maker comment that explains *why* you built it, in first person
- A "first comment" that answers the obvious objection before anyone asks

AuraSpend's hook: **"Free, open source, and it never touches the network."** The 22 MB on-device model is the novelty — lead with the constraint, not the AI.
Hydra's hook: **"A water tracker that grows a garden instead of nagging you."**
Bloom's hook: **"A planner for people who hate planners."**

---

## 5. Open source and repositories (AuraSpend)

1. **F-Droid** — the inclusion request is already tracked. Confirm the metadata MR is still open, and that the GMS-free build is the one submitted.
2. **awesome-android** — PR adding AuraSpend under "Apps → Finance". One line, matching the list's format.
3. **awesome-privacy** (optional, only if the listing criteria fit) — the offline/no-analytics angle is a genuine match.
4. **GitHub topics** on each repo: `android`, `expense-tracker`, `offline-first`, `privacy`, `kotlin`, `compose`. Topics are crawled and used by GitHub search.
5. Every repo README should open with one sentence that names the category. Currently Hydra's does; AuraSpend's and Bloom's do too — keep it that way.

---

## 6. Store and directory breadth

| Destination | Apps | Effort |
|---|---|---|
| APKPure | AuraSpend (when live) | Upload APK, fill listing |
| Uptodown | AuraSpend | Same |
| Amazon Appstore | All three | Manual submission, ~1 week review |
| Xiaomi GetApps | All three | High India relevance for AuraSpend |
| Samsung Galaxy Store | All three | Optional |
| Slant / Product Hunt alternatives lists | All three | Low effort, low yield |

Skip anything that charges for a "review" or guarantees placement. Those pages are being devalued and they cost credibility.

---

## 7. Outreach — journalists and creators

Pitch a *story*, never a review request.

**AuraSpend:**
> A free Android expense tracker that runs a 22 MB sentence encoder on-device instead of sending bank SMS to a server — and it's AGPL, so you can check.

**Hydra:**
> A hydration app whose retention mechanic is a hand-drawn garden, not a streak counter.

**Bloom:**
> A planner with no account, no sync and no streaks, built for people who find productivity apps stressful.

Targets: Android Authority tips, XDA, MakeUseOf, Android Police (tips@), plus 5–10 YouTube channels per niche that already publish roundups. For YouTube, link the exact roundup video and say what you'd add — generic "please review my app" mail is ignored.

---

## 8. Cadence after launch

- **Two pages per month, maximum.** One comparison refresh, one original piece.
- Refresh every comparison page quarterly: update prices, add "last updated", fix anything a competitor changed.
- Original-data ideas that get cited:
  - "What 10,000 Indian bank SMS actually look like" (AuraSpend, aggregates only, no personal data)
  - "How often do people actually forget to drink water?" (Hydra, anonymised log data if you have consent)
  - "Why planner apps get abandoned in week two" (Bloom, based on your own retention data)

Google's scaled-content-abuse policy will deindex a site that publishes 50 generated listicles. Volume is not the strategy; being the best answer on ten queries is.

---

## 9. What not to do

- No paid reviews, no review swaps, no "5 stars and we'll feature you".
- No sockpuppet accounts on Reddit, Quora, Product Hunt or Play.
- No `AggregateRating` schema without real reviews.
- No claiming install numbers you do not have. Hydra's site correctly says 1K+ and 4.9★/25 reviews; keep every page consistent with Play.
- No cross-comparing your own apps to each other. They are in three different categories; a page that lists all three as "the best" reads as an ad and will not be quoted.
