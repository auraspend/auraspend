#!/usr/bin/env python3
"""One-off: stamp the shared header and footer into the site pages.

The site is plain static HTML with no build step, so the header and footer are
duplicated per page. This script copies them verbatim out of index.html so they
cannot drift, and marks the current page for assistive technology.

Run from the repository root:  python3 tools/stamp_site_chrome.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SITE = ROOT / "site"

# Header nav entries, in order. The first element of each pair is the href.
NAV = [
    ("index.html#how", "How it works"),
    ("index.html#features", "Features"),
    ("screenshots.html", "Screenshots"),
    ("download.html", "Download"),
    ("faq.html", "FAQ"),
    ("index.html#open-source", "Open source"),
]

# Which page is current, per file. A file not listed has no current nav item.
CURRENT_NAV = {
    "screenshots.html": "screenshots.html",
    "download.html": "download.html",
    "faq.html": "faq.html",
}

# Footer links, per file, that should be marked as the current page.
CURRENT_FOOTER = {
    "privacy.html": "privacy.html",
    "terms.html": "terms.html",
    "changelog.html": "changelog.html",
    "download.html": "download.html",
    "faq.html": "faq.html",
    "screenshots.html": "screenshots.html",
}


def extract(text, tag):
    match = re.search(rf"    <{tag} .*?</{tag}>", text, re.S)
    if not match:
        raise SystemExit(f"could not find a <{tag}> block in index.html")
    return match.group(0)


def mark(block, href, current, tag):
    """Add aria-current=\"page\" to the link whose href matches."""
    if not current:
        return block
    needle = f'href="{href}"'
    if needle not in block:
        raise SystemExit(f"no link with {needle} in the {tag} block")
    return block.replace(needle, f'{needle} aria-current="page"', 1)


def main():
    index = (SITE / "index.html").read_text(encoding="utf-8")
    header = extract(index, "header")
    footer = extract(index, "footer")

    stamped = 0
    for page in sorted(SITE.glob("*.html")):
        if page.name == "index.html":
            continue
        text = page.read_text(encoding="utf-8")
        if "<!--@HEADER-->" not in text or "<!--@FOOTER-->" not in text:
            print(f"  skip  {page.name} (no placeholders)")
            continue

        page_header = header
        page_footer = footer

        nav_current = CURRENT_NAV.get(page.name)
        if nav_current:
            page_header = mark(page_header, nav_current, True, "header")

        footer_current = CURRENT_FOOTER.get(page.name)
        if footer_current:
            page_footer = mark(page_footer, footer_current, True, "footer")

        text = text.replace("<!--@HEADER-->", page_header)
        text = text.replace("<!--@FOOTER-->", page_footer)
        page.write_text(text, encoding="utf-8")
        print(f"  ok    {page.name}")
        stamped += 1

    print(f"\nStamped {stamped} page(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
