# Screenshots

`framed/` holds the README's preview images: the app's captures inside the
same iPhone the Play Store deck uses. Regenerate them with

```bash
python3 tools/render_readme_frames.py
```

which reads the captures from [`site/img/screens/`](../../site/img/screens) —
the single copy in the repository — and writes the composites here. The
geometry is the store editor's (`PHONE_SCREEN` and `IPHONE_CHROME` in
`docs/store/src/lib/constants.ts`), so the repository, the website and the
store listing all show one device.

The raw captures used to live here as well. They were byte-identical copies
of `site/img/screens/`, so they were removed rather than kept in two places;
the `subscriptions.png` one was not even the same variant as its name
suggested, which is how the README ended up showing a dark capture in a row
of light ones.

Two older artefacts are still here and are no longer linked from anywhere:

- `quick-add.gif` — a 340×756 recording from September 2026, before the
  Quiet Ledger redesign.
- `device-2026-10-11/` — two captures of the terracotta-era onboarding, kept
  for the record.
