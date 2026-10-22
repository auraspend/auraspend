#!/usr/bin/env python3
"""Regenerate the site's two legal pages from the factory's site profile.

Why this script exists
----------------------
The site's *content* comes from a profile, never from a hand-written page. The
factory's `make_site.py` renders `index.html`, `privacy-policy.html`,
`terms-of-service.html`, `styles.css` and `script.js` from that profile into a
`website/` directory. AuraSpend already has a real site at `site/` — its own
Quiet Ledger chrome, its own stylesheet, its own theme toggle — so the
generated pages cannot simply be dropped in: their chrome is the factory
template's, and their stylesheet does not exist there.

So this script runs the generator and then re-skins the two legal pages into
the site's chrome. The *words* are the generator's output; only the furniture
(the `<head>`, the header and footer blocks, the page furniture) is the site's.

The header and footer are left as `<!--@HEADER-->` / `<!--@FOOTER-->`
placeholders, exactly as the site's own pages carry them, and
`tools/stamp_site_chrome.py` fills them in — one chrome for the whole site.

Usage
-----
    python3 tools/generate_legal_pages.py
    python3 tools/generate_legal_pages.py --profile path/to/auraspend.json

The profile defaults to `$AURASPEND_SITE_PROFILE`, then to
`~/RealWorldProjects/_factory/site-profiles/auraspend.json`. The factory
defaults to `$AURASPEND_FACTORY_DIR`, then to `~/RealWorldProjects/_factory`.

Exit code 0 when both pages were written, 1 otherwise.
"""
import argparse
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SITE = ROOT / "site"
FACTORY = Path(
    os.environ.get("AURASPEND_FACTORY_DIR", os.path.expanduser("~/RealWorldProjects/_factory"))
)
DEFAULT_PROFILE = Path(
    os.environ.get(
        "AURASPEND_SITE_PROFILE", FACTORY / "site-profiles" / "auraspend.json"
    )
)

# The factory template renders its pages with this wrapper; everything inside
# it is the content.
CONTENT_RE = re.compile(
    r'<div class="wrap" style="max-width:760px">\s*(.*?)\s*</div>\s*</section>\s*</main>',
    re.S,
)


def fail(message):
    print(f"  FAIL  {message}", file=sys.stderr)
    return 1


def generate(profile: Path, workdir: Path) -> Path:
    """Run the factory generator and return the directory it wrote."""
    script = FACTORY / "scripts" / "make_site.py"
    if not script.exists():
        raise SystemExit(f"the factory generator is not at {script}")
    if not profile.exists():
        raise SystemExit(f"the site profile is not at {profile}")
    result = subprocess.run(
        [sys.executable, str(script), str(workdir), str(profile)],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise SystemExit(f"make_site.py failed:\n{result.stdout}{result.stderr}")
    return workdir / "website"


def site_head(title: str, description: str, slug: str) -> str:
    """The site's own <head>, with this page's title and description."""
    source = (SITE / "index.html").read_text(encoding="utf-8")
    head = re.search(r"<head>(.*?)</head>", source, re.S).group(1)
    # The app's structured data belongs on the front page; a legal page that
    # repeats it would describe the same app twice.
    head = re.sub(
        r"\s*<script type=\"application/ld\+json\">.*?</script>",
        "",
        head,
        flags=re.S,
    )
    canonical = f"https://auraspend.github.io/auraspend/{slug}"
    full_title = f"{title} — AuraSpend"
    head = re.sub(r"<title>.*?</title>", f"<title>{full_title}</title>", head, flags=re.S)
    head = re.sub(
        r'<meta name="description" content=".*?">',
        f'<meta name="description" content="{description}">',
        head,
        flags=re.S,
    )
    head = re.sub(
        r'<link rel="canonical" href=".*?">',
        f'<link rel="canonical" href="{canonical}">',
        head,
        flags=re.S,
    )
    head = re.sub(
        r'<meta property="og:title" content=".*?">',
        f'<meta property="og:title" content="{full_title}">',
        head,
        flags=re.S,
    )
    head = re.sub(
        r'<meta property="og:description" content=".*?">',
        f'<meta property="og:description" content="{description}">',
        head,
        flags=re.S,
    )
    head = re.sub(
        r'<meta property="og:url" content=".*?">',
        f'<meta property="og:url" content="{canonical}">',
        head,
        flags=re.S,
    )
    head = re.sub(
        r'<meta name="twitter:title" content=".*?">',
        f'<meta name="twitter:title" content="{full_title}">',
        head,
        flags=re.S,
    )
    head = re.sub(
        r'<meta name="twitter:description" content=".*?">',
        f'<meta name="twitter:description" content="{description}">',
        head,
        flags=re.S,
    )
    return head.strip()


def wrap_tables(block: str) -> str:
    """Put a bare <table> in the site's .table-wrap, so it scrolls on a phone."""
    out = []
    position = 0
    while True:
        match = re.search(r"<table\b", block[position:])
        if not match:
            break
        start = position + match.start()
        end = position + match.end()
        out.append(block[position:start])
        if "table-wrap" in block[max(0, start - 60) : start]:
            out.append("<table")
            position = end
            continue
        close = block.find("</table>", end)
        if close == -1:
            out.append("<table")
            position = end
            continue
        out.append('<div class="table-wrap"><table')
        out.append(block[end : close + len("</table>")])
        out.append("</div>")
        position = close + len("</table>")
    out.append(block[position:])
    return "".join(out)


def split_sections(content: str):
    """Split the generated body into (title, html) blocks, one per <h2>."""
    blocks = []
    parts = re.split(r"(<h2>.*?</h2>)", content, flags=re.S)
    heading = None
    body = []
    for part in parts:
        if part.startswith("<h2>"):
            if heading is not None or body:
                blocks.append((heading, "\n".join(body).strip()))
            heading = re.sub(r"<[^>]+>", "", part).strip()
            body = []
        elif part.strip():
            body.append(part.strip())
    if heading is not None or body:
        blocks.append((heading, "\n".join(body).strip()))
    return blocks


def build_page(generated: str, title: str, description: str, slug: str, eyebrow: str,
               updated: str) -> str:
    match = CONTENT_RE.search(generated)
    if not match:
        raise SystemExit("could not find the generated content block")
    content = match.group(1)

    lead = ""
    lead_match = re.search(r'<p class="lede">(.*?)</p>', content, re.S)
    if lead_match:
        lead = lead_match.group(1).strip()
        content = content[: lead_match.start()] + content[lead_match.end() :]

    blocks = split_sections(content)
    sections = []
    for heading, body in blocks:
        if heading is None:
            continue
        sections.append(
            f'                <section class="lsec">\n'
            f"                    <h2>{heading}</h2>\n"
            f"{wrap_tables(body)}\n"
            f"                </section>"
        )

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
{site_head(title, description, slug)}
</head>
<body>
    <a class="skip-link" href="#main">Skip to content</a>

<!--@HEADER-->

    <main id="main">
        <section class="page-head">
            <div class="container container--prose">
                <span class="eyebrow">{eyebrow}</span>
                <h1>{title}</h1>
                <p class="lead">{lead}</p>
                <p class="updated">{updated}</p>
            </div>
        </section>

        <section class="section section--tight" style="padding-top:0">
            <div class="container container--prose">
{chr(10).join(sections)}
            </div>
        </section>
    </main>

<!--@FOOTER-->

    <script src="js/site.js" defer></script>
</body>
</html>
"""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", default=str(DEFAULT_PROFILE))
    args = parser.parse_args()
    profile = Path(args.profile)

    with tempfile.TemporaryDirectory() as tmp:
        website = generate(profile, Path(tmp))

        privacy = build_page(
            (website / "privacy-policy.html").read_text(encoding="utf-8"),
            title="Privacy Policy",
            description=(
                "What AuraSpend keeps on your phone, what can leave it and when, every "
                "permission the app ships, and the rights you have over it."
            ),
            slug="privacy-policy.html",
            eyebrow="Legal",
            updated=(
                "Applies to the Google Play build, the F-Droid build and to APKs built from "
                "the published source."
            ),
        )
        terms = build_page(
            (website / "terms-of-service.html").read_text(encoding="utf-8"),
            title="Terms of Service",
            description=(
                "What you get, what you owe, and the limits of what a piece of software can "
                "promise."
            ),
            slug="terms-of-service.html",
            eyebrow="Legal",
            updated=(
                "Applies to the Google Play build, the F-Droid build, the GitHub Releases APK "
                "and to APKs built from the published source."
            ),
        )

    (SITE / "privacy-policy.html").write_text(privacy, encoding="utf-8")
    print("  ok    site/privacy-policy.html")
    (SITE / "terms-of-service.html").write_text(terms, encoding="utf-8")
    print("  ok    site/terms-of-service.html")
    print(f"\nGenerated from {profile}.")
    print("Run tools/stamp_site_chrome.py to stamp the shared header and footer.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
