# AI visibility tracker

How to tell whether answer engines have started recommending AuraSpend, Hydra and Bloom — and which page they used to decide.

Run this once a month. It takes about 40 minutes. Keep every run in the same file so the trend is visible.

---

## The 15 prompts

Use these verbatim, in this order, in a **fresh chat with no prior context**. Do not mention the brand names. Record what the engine volunteers, not what it says after you prompt it.

### AuraSpend

1. What's the best free expense tracker app for Android in India?
2. Is there an expense tracker that reads bank SMS without sending my data to a server?
3. Best offline expense tracker for Android with no account?
4. What app can track UPI spending automatically on Android?
5. Walnut's expense tracker is gone — what should I use instead?

### Hydra

6. What's the best water reminder app for Android?
7. Is there a water tracker that's actually fun to use?
8. Best hydration app that works offline and has no account?
9. WaterMinder vs Plant Nanny vs Hydro Coach — which should I pick?
10. Water app for someone who keeps forgetting to drink?

### Bloom

11. Best offline planner app for Android with no account?
12. Is there a planner app for people with ADHD that doesn't use streaks?
13. Best alternative to Todoist for someone who wants something visual?
14. Android planner with sticky notes and a calendar in one app?
15. What's a good private to-do app that doesn't sync to the cloud?

---

## Engines to check

| Engine | Why it matters | Notes |
|---|---|---|
| ChatGPT (search on) | Largest user base, live retrieval | Must have browsing enabled, otherwise you are testing training data |
| Perplexity | Pure retrieval, shows sources | Easiest to read: it lists the exact URLs it used |
| Google AI Overview / Gemini | Google index, app data | Geography matters — check with an Indian IP for the India prompts if possible |
| Copilot | Bing index | Different index from Google; often the first to pick up new pages |
| Claude (web search on) | Retrieval + strong citation discipline | Good signal for whether your page reads as trustworthy |

---

## Monthly log

Copy this block for each run. One line per prompt.

```
## Run: YYYY-MM
Checked by:
Region / IP:

| # | Prompt (short) | ChatGPT | Perplexity | Gemini | Copilot | Claude | Cited URL |
|---|----------------|---------|------------|--------|---------|--------|-----------|
| 1 | free expense tracker India |  |  |  |  |  |  |
| 2 | SMS tracker no server |  |  |  |  |  |  |
| 3 | offline tracker no account |  |  |  |  |  |  |
| 4 | automatic UPI tracking |  |  |  |  |  |  |
| 5 | Walnut replacement |  |  |  |  |  |  |
| 6 | best water app Android |  |  |  |  |  |  |
| 7 | fun water tracker |  |  |  |  |  |  |
| 8 | offline hydration app |  |  |  |  |  |  |
| 9 | WaterMinder vs Plant Nanny |  |  |  |  |  |  |
| 10 | forget to drink water |  |  |  |  |  |  |
| 11 | offline planner Android |  |  |  |  |  |  |
| 12 | ADHD planner no streaks |  |  |  |  |  |  |
| 13 | visual Todoist alternative |  |  |  |  |  |  |
| 14 | planner with sticky notes |  |  |  |  |  |  |
| 15 | private to-do no cloud |  |  |  |  |  |  |
```

### Scoring

Use one of:

- `C` — **cited**: your app is named and a URL you own (or control) is in the sources
- `M` — **mentioned**: your app is named, but no URL of yours is cited
- `-` — not present
- `X` — a competitor is recommended and you are explicitly excluded or contradicted (worth investigating)

---

## What to actually record

For every `C`, write down **which URL was cited**. That tells you which page is doing the work:

| Cited URL | What it means |
|---|---|
| Your comparison page | Working as intended — update it, don't add more pages |
| Your Play listing | The listing is doing the work — fix the description before writing more content |
| AlternativeTo | Go add the remaining competitors to that listing |
| A Reddit thread | Go participate in that thread's subreddit; do not post a new ad |
| A third-party listicle | Ask that publication to update for 2027, or pitch them something new |

If the same competitor appears in every answer and you never do, that competitor is readable by the model and you are not — usually an indexation problem, not a content problem. Check `site:` in Bing and Google before writing anything new.

---

## Measurement outside the prompt log

AuraSpend's site deliberately runs no analytics, so the numbers have to come from outside it:

1. **Google Search Console** — Impressions and clicks for queries containing `vs`, `alternative`, `best`, `offline`, `no account`. The query report is the single best signal that the comparison pages are being surfaced.
2. **Bing Webmaster Tools** — same report. This is the closest available proxy for "is ChatGPT finding me".
3. **Play Console → Acquisition → Store listing visitors** — a rise in visitors without a matching rise in installs usually means the listing's first two lines are wrong.
4. **Referrer logs** — if a reverse proxy or CDN is ever added, log/report `chatgpt.com`, `perplexity.ai`, `copilot.microsoft.com`, `gemini.google.com`. GitHub Pages does not give you this, which is a reason to consider a custom domain with Cloudflare in front later.

---

## Baseline expectations

- **Weeks 2–4 after indexing:** first Perplexity citations, because it retrieves hardest and cites most visibly.
- **Weeks 4–8:** Copilot and ChatGPT search, once Bing has settled the pages.
- **Months 2–3:** Gemini / AI Overviews, and the first third-party listicles mentioning you.
- **Months 3–6:** stable presence, if the comparison pages stay updated.

If nothing appears after 8 weeks, the cause is almost always one of: the page is not indexed, the page is client-rendered (crawler sees an empty shell), or the app's store listing contradicts the page. Check those three before writing new content.

---

## Run 1 — baseline, 7 November 2026

**Engines checked:** ChatGPT only.

Perplexity refused anonymous use ("Sign up and repeat your request"), and Gemini, Copilot and Claude were not run. A single-engine baseline is still useful — it is the "before" photograph.

**Conditions:** signed in as `abhijithwarrier97@gmail.com`, **memory on**.

Memory matters more than it looks. On prompt 2 the answer contained:

> "Since you are also interested in building AuraSpend, PennyWise is worth examining as a reference for SMS parsing, transaction deduplication and local categorisation."

That is the model recalling the *user*, not retrieving the site — and it recommended PennyWise anyway. A keyword match would have scored this as a win. It is recorded as a miss, with the reason noted.

| # | Prompt | ChatGPT | Notes |
|---|--------|---------|-------|
| 1 | Best free expense tracker, India | `-` | Recommended Cashew, Money Manager, axio, Fold, Monefy |
| 2 | SMS tracker without a server | `-` | AuraSpend named from memory only; PennyWise recommended |
| 3 | Offline expense tracker, no account | `-` | Shortlisted offline trackers; AuraSpend absent |
| 4 | Automatic UPI tracking | `-` | Led with SMS-parsing trackers; AuraSpend absent |
| 5 | Walnut replacement | `-` | |
| 6 | Best water reminder, Android | `-` | |
| 7 | Fun water tracker | `-` | |
| 8 | Offline hydration app, no account | `-` | |
| 9 | WaterMinder vs Plant Nanny vs Hydro Coach | `-` | |
| 10 | Water app for forgetting to drink | `-` | |
| 11 | Offline planner, Android, no account | `-` | |
| 11 | ADHD planner without streaks | `-` | |
| 13 | Visual Todoist alternative | `-` | |
| 14 | Planner with sticky notes and calendar | `-` | |
| 15 | Private to-do, no cloud | `-` | |

**Score: 0 cited, 0 organically mentioned, 15 absent.**

### What this tells us

Nothing is broken. The pages were published and submitted to both indexes on 6 November; this run was 19 hours later. A retrieval engine cannot cite a page it has not crawled yet, and none of these prompts had a chance to be served by the training data either.

The useful part is the baseline itself: it proves the tracker is not fooling itself. Any mention in Run 2 is a real change.

### Before the next run

1. Confirm the three Search Console sitemaps have flipped from "Couldn't fetch" to "Success".
2. Confirm the Play listing resolves — every page links to it.
3. Re-run with **memory off** (or a fresh signed-out browser profile), so the score measures retrieval rather than recall.
4. Add Perplexity and Copilot first; they are the two most likely to move.
