#!/usr/bin/env python3
"""Update the en-US Google Play store listing title.

Uses the same Play service-account secret as the release workflow. No
credentials are stored in this repository and the account is never printed.
Run from the `Play listing` workflow, not from a developer machine.
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request

from google.auth.transport.requests import Request
from google.oauth2 import service_account

PACKAGE = "com.awbuilds.auraspend"
LANGUAGE = "en-US"
NEW_TITLE = "AuraSpend: SMS Expense Tracker"
BASE = (
    "https://androidpublisher.googleapis.com/androidpublisher/v3/"
    f"applications/{PACKAGE}"
)


def call(method: str, url: str, body: dict | None = None) -> dict:
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {TOKEN}",
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
    raw = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON")
    if not raw:
        print("PLAY_SERVICE_ACCOUNT_JSON is not set", file=sys.stderr)
        return 1
    if len(NEW_TITLE) > 30:
        print("title exceeds Play's 30-character limit", file=sys.stderr)
        return 1

    credentials = service_account.Credentials.from_service_account_info(
        json.loads(raw),
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    credentials.refresh(Request())

    global TOKEN
    TOKEN = credentials.token

    edit = call("POST", f"{BASE}/edits", {})
    edit_id = edit["id"]
    before = call("GET", f"{BASE}/edits/{edit_id}/listings/{LANGUAGE}")
    print(f"current title: {before.get('title')!r}")
    after = call(
        "PATCH",
        f"{BASE}/edits/{edit_id}/listings/{LANGUAGE}",
        {"title": NEW_TITLE},
    )
    call("POST", f"{BASE}/edits/{edit_id}:commit", {})
    print(f"updated title: {after.get('title')!r}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
