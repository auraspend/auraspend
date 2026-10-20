#!/usr/bin/env python3
"""Render the README's screenshots inside the iPhone frame.

GitHub renders Markdown with no CSS and strips inline style, so the frame
cannot be layered onto the page — it has to be baked into the image. This
produces the composites the README links to, from the same captures the
website and the Play Store deck use.

    python3 tools/render_readme_frames.py

The geometry is the store editor's, not this script's invention
(`docs/store/src/lib/constants.ts`):

  PHONE_SCREEN   the screen rect the mockup was measured for: 918×1990 at
                 52,46, inside a 1022×2082 bezel
  IPHONE_CHROME  the drawn iOS status bar (390×46, with 9:41, cellular,
                 wifi and battery) and the 104×26 island at 11px

The capture is nudged down by (46 − 24)px — 46 is the height of the drawn
bar, 24 the height of the Android one the captures carry — so the Android
row lands exactly behind the drawn band and the gesture pill falls off the
bottom. The band is painted in the app's own background colour, so the strip
above the shifted capture reads as the device rather than as a gap.

The island is a stadium, not a circle: `border-radius: 50%` on a 4:1 box
draws an ellipse.

Requires Google Chrome (or Chromium/Edge). The output is PNG with a
transparent background, so it sits correctly on either GitHub theme.
"""
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MOCKUP = ROOT / "docs" / "store" / "public" / "mockup.png"
CAPTURES = ROOT / "site" / "img" / "screens"
OUT = ROOT / "docs" / "screenshots" / "framed"

# The README's Preview grid: three rows of three, in the order they read.
FRAMES = [
    ("home-light", "home"),
    ("activity-light", "activity"),
    ("plan-light", "plan"),
    ("quick-add-light", "quick-add"),
    ("insights-light", "insights"),
    ("subscriptions-light", "subscriptions"),
    ("settings-light", "settings"),
    ("home-dark", "home-dark"),
    ("detail-light", "transaction-detail"),
]

# Rendered width in pixels. The README cell is ~320 CSS px wide, so this is
# roughly 2x — crisp on a retina screen without a large file.
WIDTH = 640
HEIGHT = round(WIDTH * 2082 / 1022)

# PHONE_SCREEN, as percentages of the bezel.
SCREEN = {"l": 5.088, "t": 2.209, "w": 89.824, "h": 95.581, "rx": 13.725, "ry": 6.332}
# IPHONE_CHROME, in the 390×844 design space.
STATUS_BAR, SCREEN_H, ISLAND_TOP, ISLAND_W, ISLAND_H = 46, 844, 11, 104, 26
CAPTURE_INSET_DP = 24
SHIFT = (STATUS_BAR - CAPTURE_INSET_DP) / SCREEN_H * 100

# The app's own paper, for the light captures; the dark ones get #0F0F0D.
PAPER = {"light": "#F7F5F0", "dark": "#0F0F0D"}
INK = {"light": "#17160F", "dark": "#F4F1E8"}

CHROME_CANDIDATES = [
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
    "google-chrome",
    "google-chrome-stable",
    "chromium",
    "chromium-browser",
    "microsoft-edge",
]

TEMPLATE = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><style>
  html,body {{ margin:0; padding:0; width:{w}px; height:{h}px; overflow:hidden;
               background:transparent; }}
  .phone {{ position:relative; width:{w}px; height:{h}px; }}
  .bezel {{ position:absolute; inset:0; width:100%; height:100%; display:block; }}
  .screen {{ position:absolute; left:{sl}%; top:{st}%; width:{sw}%; height:{sh}%;
            overflow:hidden; border-radius:{srx}% / {sry}%; background:{paper}; }}
  .capture {{ position:absolute; left:0; top:{shift}%; width:100%; height:auto;
              display:block; }}
  .band {{ position:absolute; inset:0 0 auto 0; height:{band}%; background:{paper}; }}
  .chrome {{ position:absolute; top:0; left:0; width:100%; height:auto;
             display:block; font-family:'Plus Jakarta Sans',sans-serif; color:{ink}; }}
  .island {{ position:absolute; top:{itop}%; left:50%; transform:translateX(-50%);
             width:{iw}%; aspect-ratio:{ar}; border-radius:999px; background:#000; }}
</style></head><body>
  <div class="phone">
    <img class="bezel" src="{mockup}">
    <div class="screen">
      <img class="capture" src="{capture}">
      <span class="band"></span>
      <svg class="chrome" viewBox="0 0 390 46">
        <text x="30" y="27.68" font-size="13" font-weight="700" letter-spacing="0.13"
              fill="currentColor">9:41</text>
        <g transform="translate(293,17.5)" fill="currentColor">
          <path d="M0 7h3v4H0zM4.5 5h3v6h-3zM9 2.6h3V11H9zM13.5 0h3v11h-3z"/>
          <g transform="translate(22,0)">
            <path d="M8 10.4 5.6 8a3.4 3.4 0 0 1 4.8 0zM8 6.2a6.6 6.6 0 0 0-4.6 1.9L1.9 6.6a9.4 9.4 0 0 1 12.2 0l-1.5 1.5A6.6 6.6 0 0 0 8 6.2z"/>
          </g>
          <g transform="translate(43,0)">
            <rect x="0.6" y="0.6" width="19" height="9.8" rx="3" fill="none"
                  stroke="currentColor" stroke-opacity="0.45"/>
            <rect x="2.4" y="2.4" width="14.4" height="6.2" rx="1.7"/>
            <path d="M21.6 4v3" fill="none" stroke="currentColor" stroke-opacity="0.45"
                  stroke-width="1.4" stroke-linecap="round"/>
          </g>
        </g>
      </svg>
      <span class="island"></span>
    </div>
  </div>
</body></html>
"""


def find_chrome():
    for candidate in CHROME_CANDIDATES:
        found = shutil.which(candidate) if "/" not in candidate else (
            candidate if Path(candidate).exists() else None)
        if found:
            return found
    raise SystemExit("no Chrome found — install Google Chrome, Chromium or Edge")


def main():
    if not MOCKUP.exists():
        raise SystemExit(f"missing {MOCKUP}")
    chrome = find_chrome()
    OUT.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory() as profile:
        for capture, name in FRAMES:
            src = CAPTURES / f"{capture}.png"
            if not src.exists():
                raise SystemExit(f"missing capture {src}")
            theme = "dark" if "dark" in capture else "light"
            html = TEMPLATE.format(
                w=WIDTH, h=HEIGHT,
                mockup=MOCKUP.as_uri(), capture=src.as_uri(),
                sl=SCREEN["l"], st=SCREEN["t"], sw=SCREEN["w"], sh=SCREEN["h"],
                srx=SCREEN["rx"], sry=SCREEN["ry"],
                shift=round(SHIFT, 4), band=round(STATUS_BAR / SCREEN_H * 100, 4),
                itop=round(ISLAND_TOP / SCREEN_H * 100, 4),
                iw=round(ISLAND_W / 390 * 100, 4), ar=f"{ISLAND_W} / {ISLAND_H}",
                paper=PAPER[theme], ink=INK[theme],
            )
            with tempfile.NamedTemporaryFile("w", suffix=".html", delete=False) as f:
                f.write(html)
                page = Path(f.name)
            out = OUT / f"{name}.png"
            subprocess.run(
                [
                    chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                    "--no-first-run", "--no-default-browser-check",
                    "--default-background-color=00000000",
                    f"--user-data-dir={profile}",
                    f"--screenshot={out}",
                    f"--window-size={WIDTH},{HEIGHT}",
                    page.as_uri(),
                ],
                check=True, capture_output=True,
            )
            page.unlink(missing_ok=True)
            print(f"  {out.relative_to(ROOT)}  ({out.stat().st_size // 1024} KB)")

    print(f"\n{len(FRAMES)} frames written to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    sys.exit(main())
