#!/usr/bin/env python3
"""Check Tacklebox-owned shipping source and identifiers, not dependency provenance.
Historical QA reports and third-party licences intentionally retain their original names.
This is a naming regression gate, not an App Review approval or originality detector.
"""
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
ERRORS = []
BANNED = re.compile(r"caddro|golf[ _.-]?vault|com[.]example|yourcompany|yourapp|MyApplication|HelloWorld|Following_Pixel|Season[ _-]*Wrapped|Features/Wrapped", re.I)

def require(ok, message):
    if not ok:
        ERRORS.append(message)

def contains(path, expected):
    require(expected in (ROOT / path).read_text(), f"{path}: missing expected identity {expected!r}")

def scan(paths):
    count = 0
    for relative in paths:
        entry = ROOT / relative
        files = sorted(entry.rglob("*")) if entry.is_dir() else [entry]
        for path in files:
            if not path.is_file():
                continue
            count += 1
            require(not BANNED.search(str(path.relative_to(ROOT))), f"{path.relative_to(ROOT)}: unrelated product in filename")
            try:
                content = path.read_text()
            except UnicodeError:
                continue
            for line, text in enumerate(content.splitlines(), 1):
                if BANNED.search(text):
                    ERRORS.append(f"{path.relative_to(ROOT)}:{line}: unrelated product or template identity")
    return count

import xml.etree.ElementTree as ET

count = scan(["app/src/main", "app/src/test", "app/src/androidTest", "app/build.gradle.kts", "settings.gradle.kts", "marketing/play-store-listing.md"])
contains("settings.gradle.kts", 'rootProject.name = "Tacklebox"')
for key in ("namespace", "applicationId"):
    contains("app/build.gradle.kts", f'{key} = "uk.co.tacklebox.app"')
strings = ET.parse(ROOT / "app/src/main/res/values/strings.xml").getroot()
require(next(e.text for e in strings if e.get("name") == "app_name") == "Tacklebox", "Android app name must be Tacklebox")
ns = "{http://schemas.android.com/apk/res/android}"
app = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot().find("application")
require(app.get(ns + "name") == ".TackleboxApp", "Unexpected Application class")
require(app.get(ns + "label") == "@string/app_name", "Unexpected launcher label")
require(app.find("provider").get(ns + "authorities") == "${applicationId}.fileprovider", "Unexpected provider authority")
for path in (ROOT / "app/src").rglob("*.kt"):
    match = re.search(r"^package (\S+)", path.read_text(), re.M)
    require(match is not None and (match[1] == "uk.co.tacklebox.app" or match[1].startswith("uk.co.tacklebox.app.")), f"{path.relative_to(ROOT)}: package outside Tacklebox")
contains("app/src/main/java/uk/co/tacklebox/app/JournalExport.kt", '"app" to "Tacklebox"')
print(json.dumps({"product": "Tacklebox", "filesChecked": count, "errors": ERRORS}, indent=2))
sys.exit(bool(ERRORS))
