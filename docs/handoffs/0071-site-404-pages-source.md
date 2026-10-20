# 0071 — the site was serving a 404, because Pages was still on the old source

**Date:** 2026-10-20 · **Branch:** `main` + `dev` · **Status: committed.**

## What was wrong

`https://auraspend.github.io/auraspend/` returned **404**. The site was not
broken — it was never being served from where it lives.

The repository's Pages configuration was still:

```json
{"build_type": "legacy", "source": {"branch": "main", "path": "/docs"}}
```

i.e. *deploy from a branch*, publishing the `/docs` folder. Handoff 0067 had
moved the site to `/site` and added `.github/workflows/pages.yml` to publish it
from there, and it flagged one manual step — "Pages → Build and deployment →
Source → **GitHub Actions**" — that never happened. So:

- the branch deployment kept serving `/docs` ✗ — and `/docs` has no
  `index.html` since the site moved ✗ — hence the 404 ✗;
- the `pages.yml` workflow had been running **successfully** on every
  `site/**` push (it uploads an artifact and requests a deployment ✓) while
  the site itself was served by the legacy branch build ✗ — a workflow that
  reports success and changes nothing is the most expensive kind of silence ✗.

## The fix, and a correction to 0067

0067 said the switch was "one manual step, and only the owner can do it …
`GITHUB_TOKEN` cannot change a repository's Pages source". **That is wrong.**
The Pages API takes it:

```bash
gh api --method PUT repos/auraspend/auraspend/pages -F build_type=workflow
```

(`PATCH` on that path returns 404; `PUT` — "create a GitHub Pages site" — is
the one that works on an existing site ✗ and sets `build_type` to `workflow` ✓.)
The `source` field in the response still reads `/docs` afterwards ✗ and is
ignored ✗ — with `build_type: workflow` the Actions deployment is what serves ✗
✓ — so it was left alone rather than poking a working deployment ✗.

Then `gh workflow run pages.yml` published the site ✓.

## Verification

- `https://auraspend.github.io/auraspend/` → **200**, 52 KB, and the title is
  the new one ("AuraSpend — your bank SMS becomes a ledger" ✓).
- Every page: `download` ✓ `changelog` ✓ `faq` ✓ `screenshots` ✓ `privacy` ✓
  `terms` ✓ all **200** (there is no `licenses.html` ✗ — the site's legal pages
  are `privacy` and `terms` ✓).
- The assets: `css/style.css` ✓ 44 KB, and the Home captures ✓ — the
  `home-light.png` being served is **byte-identical** to the repository's
  (SHA-1 `1bde749e611e991b` ✓ ✓), i.e. the live site shows the home screen
  without the quick-action row ✓ ✓.

## Also in this commit

- The site's **changelog page** gained the 0.1.92 entry ✗ (it stopped at
  0.1.91 ✗ — the open item 0070 listed ✗).
- `pages.yml`'s comment no longer describes the source switch as pending ✓.
- Handoff 0067's claim about the API is corrected in place, pointing here ✗.

## Open

- The F-Droid metadata version for 0.1.92 is the owner's next step ✗.
- 0.1.92 sits `IN_REVIEW` on the Play closed-testing track ✗ ✓.
