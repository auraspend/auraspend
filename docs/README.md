# `docs/` — the project's engineering record

This folder is **not published**. The public website lives in [`/site`](../site),
and GitHub Pages deploys that folder (see `.github/workflows/pages.yml`).

Everything here is internal: the handoff log, the architecture decision
records, the evaluation reports, the design work, the release rehearsal
records and the store-listing copy. It is committed to the repository and
readable on GitHub, but it is not part of the site.

## What used to be here

Until 19 October 2026 the website was served straight out of this folder, so
`docs/index.html`, `docs/privacy.html`, `docs/terms.html`, `docs/changelog.html`,
`docs/css/` and `docs/fonts/` were the site. They moved to [`/site`](../site)
when the site was rebuilt around the app's current interface, and the Pages
source changed from *deploy from a branch* to *GitHub Actions* so that this
folder is no longer part of the published surface.

Two things moved with them and are worth knowing about:

- The self-hosted **Plus Jakarta Sans** that the site used to load from
  `docs/fonts/` is now at `site/fonts/`. The screenshot editor under
  `docs/store/` has its own byte-identical copy in
  `docs/store/src/app/fonts/`, and the font's OFL text is in
  `docs/licenses/OFL-PlusJakartaSans.txt`.
- The site's `robots.txt` and `sitemap.xml` are now `site/robots.txt` and
  `site/sitemap.xml`, and list the seven published pages rather than four.

The handoff records in `docs/handoffs/` still refer to the old paths. That is
correct: they are a historical record of what was true at the time, and
rewriting them to match today's layout would be the opposite of honest.
