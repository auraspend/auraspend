#!/usr/bin/env python3
"""Update the Google Play store listing (title, short description, full description).

Reads the canonical metadata from fastlane/metadata/android/en-US/.
Uses the PLAY_SERVICE_ACCOUNT_JSON secret from the release environment.
No credentials or account identities are stored in this repository.
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

PACKAGE = "com.awbuilds.auraspend"
LANGUAGE = "en-US"
BASE = (
    "https://androidpublisher.googleapis.com/androidpublisher/v3/"
    f"applications/{PACKAGE}"
)
ROOT = Path(__file__).resolve().parent.parent
METADATA_DIR = ROOT / "fastlane" / "metadata" / "android" / LANGUAGE


def call(method: str, url: str, body: dict | None = None, token: str = "") -> dict:
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as error:
        detail = error.read().decode(errors="replace")
        print(f"{method} {url} failed: HTTP {error.code}: {detail}", file=sys.stderr)
        raise


def main() -> int:
    title_file = METADATA_DIR / "title.txt"
    short_file = METADATA_DIR / "short_description.txt"
    full_file = METADATA_DIR / "full_description.txt"

    if not title_file.is_file() or not short_file.is_file() or not full_file.is_file():
        print(f"Metadata files missing in {METADATA_DIR}", file=sys.stderr)
        return 1

    title = title_file.read_text(encoding="utf-8").strip()
    short_desc = short_file.read_text(encoding="utf-8").strip()
    full_desc = full_file.read_text(encoding="utf-8").strip()

    print(f"Title ({len(title)}/30 chars): {title!r}")
    print(f"Short Description ({len(short_desc)}/80 chars): {short_desc!r}")
    print(f"Full Description ({len(full_desc)}/4000 chars): {len(full_desc)} characters")

    if len(title) > 30:
        print(f"Error: title exceeds 30 chars ({len(title)})", file=sys.stderr)
        return 1
    if len(short_desc) > 80:
        print(f"Error: short description exceeds 80 chars ({len(short_desc)})", file=sys.stderr)
        return 1
    if len(full_desc) > 4000:
        print(f"Error: full description exceeds 4000 chars ({len(full_desc)})", file=sys.stderr)
        return 1

    raw = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON")
    if not raw:
        print("PLAY_SERVICE_ACCOUNT_JSON is not set; verified metadata files only.", file=sys.stderr)
        return 1

    from google.auth.transport.requests import Request
    from google.oauth2 import service_account

    credentials = service_account.Credentials.from_service_account_info(
        json.loads(raw),
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    credentials.refresh(Request())
    token = credentials.token

    edit = call("POST", f"{BASE}/edits", {}, token=token)
    edit_id = edit["id"]
    print(f"Created edit session: {edit_id}")

    try:
        before = call("GET", f"{BASE}/edits/{edit_id}/listings/{LANGUAGE}", token=token)
        print("--- CURRENT LIVE LISTING ---")
        print(f"Title: {before.get('title')!r}")
        print(f"Short: {before.get('shortDescription')!r}")
        print(f"Full length: {len(before.get('fullDescription', ''))} chars")

        patch_body = {
            "title": title,
            "shortDescription": short_desc,
            "fullDescription": full_desc,
        }
        after = call("PATCH", f"{BASE}/edits/{edit_id}/listings/{LANGUAGE}", patch_body, token=token)
        print("--- PATCHED LISTING ---")
        print(f"Updated Title: {after.get('title')!r}")
        print(f"Updated Short: {after.get('shortDescription')!r}")
        print(f"Updated Full: {len(after.get('fullDescription', ''))} chars")

        call("POST", f"{BASE}/edits/{edit_id}:commit", {}, token=token)
        print(f"Successfully committed edit {edit_id} to Google Play!")
    except Exception as e:
        print(f"Error updating listing: {e}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
