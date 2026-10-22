# 0080 — the legal pages are generated, and the chrome tool works again

**Date:** 2026-10-22 · **Branch:** `dev` · **Status: committed and pushed.**

## What this session did

The owner asked for everything in the working tree to be committed and pushed.
The tree held another session's **in-flight** work on the site's legal pages,
which had never been committed. This record is about what that work contains
and what is still open in it — it is not a claim of authorship, and the
session that wrote it is still at work on it.

## What was in the tree

- **`tools/generate_legal_pages.py`** (new) — generates the site's two legal
  pages from the factory's site profile and re-skins them into this site's
  chrome. The *words* are the generator's output; only the furniture is the
  site's. The header and footer are left as `<!--@HEADER-->` /
  `<!--@FOOTER-->` for `tools/stamp_site_chrome.py` to fill in.
- **`tools/stamp_site_chrome.py`** (changed) — **it was a no-op.** It looked
  for the `<!--@HEADER-->` / `<!--@FOOTER-->` placeholders, but those were
  filled in once and then edited by hand, so every page was skipped and the
  script changed nothing. AGENTS.md requirement 10 says to re-run it after
  changing the chrome in `index.html`; before this, that was untrue. It now
  replaces the chrome *block* on a page.
- **`site/privacy-policy.html`, `site/terms-of-service.html`** (new) — the
  generated pages.
- **`site/index.html`, `site/faq.html`** (changed) — the legal links now
  point at the new slugs.

The generated privacy policy is a **rewrite, not a copy**: it adds a
"Permissions, and why each is needed" section the hand-written page does not
have, which is what Android 11+ asks a store listing to explain.

## Three things still open in that work

1. **The new pages carry the raw `<!--@HEADER-->` placeholder**, so they
   would publish with the stylesheet but **no header and no footer**.
2. **`tools/stamp_site_chrome.py` cannot currently be run**: its
   `CURRENT_FOOTER` map still names `privacy.html` and `terms.html`, and the
   footer's legal links are now the new slugs — so `mark()` would raise
   `no link with href="privacy.html" in the footer block` and the script
   would exit. The map needs the new slugs (and the old pages need to be
   retired, or excluded, before it will pass).
3. **`site/sitemap.xml` still lists the old URLs**, and the footers in
   `changelog.html` and `404.html` still point at the old pages — the chrome
   in `index.html` was changed, but the other pages have not been re-stamped.
   So the old `privacy.html` and `terms.html` remain published **alongside**
   the new ones, with **different text**, and the footer on most pages links
   to the old policy while the home page's inline link goes to the new one.

Nothing here was decided in this session: retiring the old URLs is a
judgement about what should stay live, and it belongs to whoever owns the
legal pages.

## Verification

- `./tools/check_site_version.sh` — ok, the changelog ends at 0.1.97 across 18
  releases.
- `./tools/check_privacy_claims.sh` — every absolute claim states its
  exception, across the tree including the new pages.
- `./tools/check_license.sh` — licence metadata is consistent.
- The support address on the new pages (`awbuilds.support@gmail.com`) is the
  same project role address the live `privacy.html` and `terms.html` already
  publish — no new disclosure.

## Note

`tools/generate_legal_pages.py` defaults to
`~/RealWorldProjects/_factory`, which names the owner's local folder. It
carries no username and the `~` keeps it portable, but it is a machine path
in a public repository, and requirement 11's history (handoff 0020) scrubbed
those. Worth making generic before more people see it.
