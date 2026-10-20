#!/usr/bin/env python3
"""Live end-to-end smoke test: the real app on an emulator against a real Pinakes instance.

Drives the installed debug app with adb/uiautomator (``ui.py``) like a user, checks the
server state through the Mobile API after every step, and removes what it created.

Environment (nothing is hardcoded, secrets are never printed):
  PINAKES_E2E_URL       instance URL as the device reaches it (emulator: http://10.0.2.2:8081)
  PINAKES_E2E_HOST_URL  same instance as this machine reaches it (default: the device URL
                        with 10.0.2.2 replaced by 127.0.0.1)
  PINAKES_E2E_EMAIL     an admin or staff account
  PINAKES_E2E_PASSWORD  its password
  PINAKES_E2E_ISBN      ISBN to scan-add (default 9788845292613); must not be in the catalogue
  ANDROID_SERIAL        target device (adb's own variable)

Optional, for the "hidden profile fields" step (skipped without them): direct access to the
instance database, used to switch the gender / tax code visibility and restore it afterwards.
  PINAKES_E2E_DB_NAME, PINAKES_E2E_DB_USER, PINAKES_E2E_DB_PASSWORD,
  PINAKES_E2E_DB_SOCKET (or PINAKES_E2E_DB_HOST + PINAKES_E2E_DB_PORT)

Steps: onboarding URL, login, staff hub, manual ISBN add (scraped preview), duplicate add
copies, manual record without ISBN, shelf/level location on edit (skipped when the instance
has no shelf with a level), phase 2 (each skipped while its /health staff_* flag is off):
loan created by API with an Idempotency-Key and its replay, then returned in the app by
typing the copy code; a copy with an explicit code from the book editor; a series created in
the app; a two-row CSV import job followed to completion; a free Desiderata offer received into a picked record; an
unpublished archive unit created and checked absent from the public list; Tier 2: an unknown
code looked up at the periodicals desk (read-only), a book club and an FRBR work created in the
app; then airplane-mode
offline search and detail, calendar link + feed.
Cleanup (only what this run created): created books deleted, the calendar link revoked when
it did not exist before, device tokens issued during the run revoked, app data cleared,
airplane mode off. Exit code 0 only when every step passed.
"""
import json
import os
import random
import re
import subprocess
import sys
import time
import traceback
import urllib.error
import urllib.request
import uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ui  # noqa: E402

PKG = "com.pinakes.app"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def env(name, default=None, required=True):
    value = os.environ.get(name, default)
    if required and not value:
        sys.exit(f"missing environment variable {name}")
    return value


DEVICE_URL = env("PINAKES_E2E_URL").rstrip("/")
HOST_URL = env("PINAKES_E2E_HOST_URL", DEVICE_URL.replace("10.0.2.2", "127.0.0.1")).rstrip("/")
EMAIL = env("PINAKES_E2E_EMAIL")
PASSWORD = env("PINAKES_E2E_PASSWORD")
ISBN = env("PINAKES_E2E_ISBN", "9788845292613")
RUN = time.strftime("%Y%m%d%H%M%S")
MANUAL_TITLE = f"E2E smoke {RUN}"


# ---------------------------------------------------------------- strings (device language)

def load_strings():
    locale = ui.shell("getprop persist.sys.locale").strip() or ui.shell("getprop ro.product.locale").strip()
    lang = (locale or "en").split("-")[0].lower()
    path = os.path.join(ROOT, "i18n", f"{lang}.json")
    if not os.path.exists(path):
        path = os.path.join(ROOT, "i18n", "en.json")
    with open(os.path.join(ROOT, "i18n", "en.json"), encoding="utf-8") as fh:
        base = json.load(fh)
    with open(path, encoding="utf-8") as fh:
        base.update(json.load(fh))
    return base


S = {}


def s(key, *args):
    value = S[key]
    for i, a in enumerate(args, 1):
        value = re.sub(r"%" + str(i) + r"\$[sd]", str(a), value)
    return value


def prefix(key):
    """The text of a format string before its first placeholder (for substring matching)."""
    return re.split(r"%\d\$[sd]", S[key])[0].strip()


# ---------------------------------------------------------------- API

class Api:
    def __init__(self):
        self.token = None

    def call(self, method, path, body=None, raw=False, auth=True, base=None, headers=None, multipart=None):
        url = (base or HOST_URL) + "/api/v1/" + path.lstrip("/") if not path.startswith("http") else path
        data = json.dumps(body).encode() if body is not None else None
        content_type = "application/json"
        if multipart is not None:
            data, content_type = encode_multipart(multipart)
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Accept", "application/json")
        for k, v in (headers or {}).items():
            req.add_header(k, v)
        if data is not None:
            req.add_header("Content-Type", content_type)
        if auth and self.token:
            req.add_header("Authorization", "Bearer " + self.token)
        try:
            with urllib.request.urlopen(req, timeout=180) as res:
                payload = res.read()
                return res.status, (payload if raw else json.loads(payload or b"{}")), res.headers
        except urllib.error.HTTPError as e:
            payload = e.read()
            try:
                return e.code, (payload if raw else json.loads(payload or b"{}")), e.headers
            except ValueError:
                return e.code, payload, e.headers

    def login(self):
        status, body, _ = self.call("POST", "auth/login", {
            "email": EMAIL, "password": PASSWORD, "device_name": f"e2e-smoke-{RUN}",
            "device_id": f"e2e-{RUN}-{random.randint(1000, 9999)}", "platform": "other",
        }, auth=False)
        if status != 200 or not body.get("data", {}).get("token"):
            raise AssertionError(f"API login failed with HTTP {status}")
        self.token = body["data"]["token"]
        role = body["data"]["user"].get("tipo_utente")
        if role not in ("admin", "staff"):
            raise AssertionError(f"PINAKES_E2E_EMAIL must be admin or staff (is {role!r})")

    def find_book(self, q):
        status, body, _ = self.call("GET", f"staff/books?q={urllib.request.quote(q)}&limit=20")
        assert status == 200, f"GET staff/books HTTP {status}"
        return body.get("data") or []

    def book(self, book_id):
        status, body, _ = self.call("GET", f"staff/books/{book_id}")
        assert status == 200, f"GET staff/books/{book_id} HTTP {status}"
        return body["data"]


def encode_multipart(fields):
    """fields: {name: str | (filename, bytes, mime)} → (body, content type)."""
    boundary = "e2e" + RUN + str(random.randint(1000, 9999))
    out = b""
    for name, value in fields.items():
        out += f"--{boundary}\r\n".encode()
        if isinstance(value, tuple):
            filename, payload, mime = value
            out += f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\nContent-Type: {mime}\r\n\r\n'.encode()
            out += payload + b"\r\n"
        else:
            out += f'Content-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode()
    out += f"--{boundary}--\r\n".encode()
    return out, f"multipart/form-data; boundary={boundary}"


API = Api()
CREATED = []          # book ids to delete
CREATED_SERIES = []   # series ids to delete
CREATED_ARCHIVES = [] # archive unit ids to delete
CREATED_OFFERS = []   # desiderata offer ids to delete
CREATED_CLUBS = []    # book club ids to delete
CREATED_WORKS = []    # FRBR work ids to delete
CLUB_NAME = f"E2E club {RUN}"
WORK_TITLE = f"E2E work {RUN}"
CIRC_TITLE = f"E2E circulation {RUN}"
SERIES_NAME = f"E2E series {RUN}"
IMPORT_TITLES = (f"E2E import A {RUN}", f"E2E import B {RUN}")
ARCHIVE_REF = f"E2E-{RUN}"
RESULTS = []          # (step, ok, detail)
DEVICES_BEFORE = set()
STATE = {"devices_known": False, "calendar_was_enabled": None}


def step(name):
    def wrap(fn):
        def run(*a, **kw):
            print(f"--> {name}", flush=True)
            try:
                detail = fn(*a, **kw)
                RESULTS.append((name, "PASS", detail or ""))
                print(f"    PASS {detail or ''}", flush=True)
                return True
            except SkipStep as e:
                RESULTS.append((name, "SKIP", str(e)))
                print(f"    SKIP {e}", flush=True)
                return True
            except Exception as e:  # noqa: BLE001 - report and continue with the next step
                RESULTS.append((name, "FAIL", str(e)))
                print(f"    FAIL {e}", flush=True)
                traceback.print_exc()
                try:
                    ui.screenshot(f"/tmp/pinakes-e2e-{RUN}-{len(RESULTS)}.png")
                except Exception:  # noqa: BLE001
                    pass
                return False
        return run
    return wrap


class SkipStep(Exception):
    pass


# ---------------------------------------------------------------- navigation helpers

def open_tab(key):
    """Go back to the bottom-navigation screen if needed, then open the tab [key]."""
    w, h = ui.screen_size()
    for _ in range(6):
        ui.hide_keyboard()
        for n in ui.dump():
            # Bottom navigation labels sit in the lowest part of the screen.
            if n.text == S[key] and n.center[1] > h * 0.8:
                ui.tap_xy(*n.center)
                return
        ui.back()
    raise AssertionError(f"bottom navigation not reachable for {S[key]!r}")


def open_staff_hub():
    open_tab("nav_profile")
    ui.wait(S["profile_action_edit"], 90)
    ui.scroll_to(S["profile_action_staff"])
    ui.tap(S["profile_action_staff"])
    ui.wait(S["staff_scan"])


def deny_camera_prompt():
    for res in ("com.android.permissioncontroller:id/permission_deny_button",
                "com.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button"):
        deadline = time.time() + 4
        while time.time() < deadline:
            n = ui.find_res(res)
            if n:
                ui.tap_xy(*n.center)
                return
            time.sleep(0.5)


def scanner_lookup(code):
    ui.tap(S["staff_scan_manual_label"])
    ui.type_text(code)
    ui.hide_keyboard()
    ui.tap(S["staff_scan_lookup"])


# ---------------------------------------------------------------- steps

@step("preflight: API login and clean ISBN")
def preflight():
    API.login()
    # Snapshot what already exists, so cleanup only removes what this run created.
    _, devs, _ = API.call("GET", "me/devices")
    DEVICES_BEFORE.update(d["id"] for d in devs.get("data") or [])
    STATE["devices_known"] = True
    status, cal, _ = API.call("GET", "me/calendar")
    if status == 200:
        STATE["calendar_was_enabled"] = bool((cal.get("data") or {}).get("enabled"))
    status, body, _ = API.call("GET", "health", auth=False)
    feats = body["data"]["features"]
    assert feats.get("staff_catalog") and feats.get("catalog_sync"), f"server lacks Mobile API 1.6.0 features: {feats}"
    if any(r.get("isbn13") == ISBN or r.get("ean") == ISBN for r in API.find_book(ISBN)):
        raise AssertionError(f"ISBN {ISBN} is already in the catalogue: set PINAKES_E2E_ISBN to an unused one")
    return f"calendar_feed={feats.get('calendar_feed')}"


@step("onboarding and login in the app")
def onboarding_and_login():
    ui.adb("shell", "pm", "clear", PKG)
    ui.airplane(False)
    ui.shell(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1")
    ui.wait(S["onboarding_url_label"], 30)
    ui.tap(S["onboarding_url_label"])
    ui.type_text(DEVICE_URL)
    ui.hide_keyboard()
    ui.tap(S["action_discover_library"])
    ui.wait(S["action_continue"], 90)       # discovery on a loaded dev server
    ui.tap(S["action_continue"])
    ui.wait(S["login_email_label"], 30)
    ui.tap(S["login_email_label"], exact=True)
    ui.type_text(EMAIL)
    ui.hide_keyboard()
    ui.tap(S["field_password"], exact=True)
    ui.type_text(PASSWORD, secret=True)
    ui.hide_keyboard()
    # A loaded dev server can time out the first request: retry the sign-in like a user would.
    for attempt in range(3):
        ui.tap(S["login_action_sign_in"])
        which, _ = ui.wait_any([S["nav_profile"], S["login_error_network"]], 120)
        if which == S["nav_profile"]:
            return f"signed in (attempt {attempt + 1})"
        time.sleep(5)
    raise AssertionError("sign-in kept failing with a network error")


@step("staff hub is reachable")
def staff_hub():
    open_staff_hub()
    for key in ("staff_add_manual", "staff_books", "staff_authors", "staff_shelves"):
        ui.scroll_to(S[key])


@step("scan: manual ISBN, scraped preview, add with 2 copies")
def scan_add():
    ui.scroll_to(S["staff_scan"], exact=True)
    ui.tap(S["staff_scan"], exact=True)
    deny_camera_prompt()
    ui.wait(S["staff_scan_manual_label"])
    scanner_lookup(ISBN)
    found, _ = ui.wait_any([S["staff_scan_confirm"], S["staff_scan_not_found"], S["staff_scan_duplicate"]], 90)
    assert found == S["staff_scan_confirm"], f"expected a preview for {ISBN}, got {found!r}"
    time.sleep(1.5)                           # let the sheet finish expanding
    ui.tap(S["staff_cd_increase"])          # 1 → 2 copies
    ui.tap(S["staff_scan_confirm"])
    which, _ = ui.wait_any([s("staff_scan_session_count", 1), S["staff_error_save"], S["staff_error_offline"]], 150)
    rows = [r for r in API.find_book(ISBN) if r.get("isbn13") == ISBN or r.get("ean") == ISBN]
    CREATED.extend(r["id"] for r in rows if r["id"] not in CREATED)
    assert which == s("staff_scan_session_count", 1), f"the app reported {which!r} (server rows: {len(rows)})"
    assert len(rows) == 1, f"expected one record with {ISBN}, found {len(rows)}"
    book = API.book(rows[0]["id"])
    assert book["copies_total"] == 2, f"copies_total {book['copies_total']} != 2"
    return f"book #{book['id']} {book['title']!r}"


@step("scan the same ISBN: duplicate sheet adds a copy")
def scan_duplicate():
    if not CREATED:
        raise SkipStep("no scanned book")
    scanner_lookup(ISBN)
    ui.wait(S["staff_scan_duplicate"], 60)
    ui.tap(S["staff_scan_add_copy"])
    ui.wait(s("staff_scan_added_copies", 1), 30)
    book = API.book(CREATED[0])
    assert book["copies_total"] == 3, f"copies_total {book['copies_total']} != 3"
    ui.back()


@step("manual record without ISBN")
def manual_record():
    ui.scroll_to(S["staff_add_manual"], exact=True)
    ui.tap(S["staff_add_manual"], exact=True)
    ui.wait(S["staff_field_title"])
    ui.tap(S["staff_field_title"])
    ui.type_text(MANUAL_TITLE)
    ui.hide_keyboard()
    ui.tap(S["staff_book_save"])
    ui.wait(S["staff_add_manual"], 120)     # back on the hub after the save
    rows = [r for r in API.find_book(MANUAL_TITLE) if r["title"] == MANUAL_TITLE]
    CREATED.extend(r["id"] for r in rows if r["id"] not in CREATED)
    assert len(rows) == 1, f"expected one {MANUAL_TITLE!r}, found {len(rows)}"
    book = API.book(rows[0]["id"])
    assert not book.get("isbn13") and not book.get("ean"), "manual record got an identifier"
    return f"book #{book['id']}"


@step("edit location: shelf + level, everything else unchanged")
def edit_location():
    status, body, _ = API.call("GET", "staff/shelves")
    shelves = [sh for sh in body.get("data") or [] if sh.get("levels")]
    if not shelves:
        raise SkipStep("the instance has no shelf with a level")
    if len(CREATED) < 2:
        raise SkipStep("no manual record")
    shelf, level = shelves[0], shelves[0]["levels"][0]
    before = API.book(CREATED[1])
    ui.scroll_to(S["staff_books"], exact=True)
    ui.tap(S["staff_books"], exact=True)
    ui.tap(S["staff_books_search"])
    ui.type_text(MANUAL_TITLE)
    ui.hide_keyboard()
    ui.tap(MANUAL_TITLE, timeout=30)
    ui.wait(S["staff_book_save"], 30)
    ui.scroll_to(S["staff_shelf"])
    ui.tap(S["staff_shelf"])
    display = " · ".join(x for x in (shelf.get("code"), shelf.get("name")) if x)
    ui.tap(display)
    ui.scroll_to(S["staff_level"])
    ui.tap(S["staff_level"])
    ui.tap(s("staff_level_number", level["number"]))
    ui.tap(S["staff_book_save"])
    # The save button shows a spinner (no text) while saving: wait for the editor itself to close.
    ui.gone(S["staff_book_edit"], 120)
    after = API.book(CREATED[1])
    loc = after.get("location") or {}
    assert loc.get("shelf_id") == shelf["id"] and loc.get("level_id") == level["id"], f"location {loc}"
    for key in ("title", "subtitle", "isbn13", "ean", "language", "media_type", "copies_total", "authors", "publishers", "genre"):
        assert before.get(key) == after.get(key), f"{key} changed: {before.get(key)!r} → {after.get(key)!r}"
    ui.back()
    return f"{display} / level {level['number']}"


@step("offline: airplane mode, cached search and detail")
def offline():
    # Foreground again so the app syncs the catalogue with the records created above.
    ui.shell("input keyevent KEYCODE_HOME")
    time.sleep(1)
    ui.shell(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1")
    time.sleep(25)                           # the foreground sync (slow dev servers need time)
    ui.airplane(True)
    try:
        open_tab("nav_catalog")
        ui.tap(S["catalog_field_placeholder"])
        ui.type_text(MANUAL_TITLE)
        ui.hide_keyboard()
        ui.wait(S["offline_results"], 30)
        ui.tap(MANUAL_TITLE, timeout=30)
        ui.wait(S["offline_actions_unavailable"], 30)
        # The availability box is below the cover: scroll to it, checking each screen on the way.
        last_known = prefix("offline_availability_last_known")
        for _ in range(8):
            nodes = ui.dump()
            for bad in (S["book_request_loan"], S["book_reserve"], "Failed to connect"):
                assert not ui.find(bad, nodes=nodes), f"{bad!r} shown offline"
            if ui.find(last_known, nodes=nodes) or ui.find(S["offline_availability_unknown"], nodes=nodes):
                break
            ui.swipe_up()
        else:
            raise AssertionError("no 'last known' availability wording on the offline page")
        ui.back()
    finally:
        ui.airplane(False)


@step("calendar: create link, fetch the feed")
def calendar():
    status, body, _ = API.call("GET", "health", auth=False)
    if not body["data"]["features"].get("calendar_feed"):
        raise SkipStep("calendar_feed is off on this instance")
    open_tab("nav_profile")
    ui.wait(S["profile_action_edit"], 90)    # the profile has loaded
    ui.scroll_to(S["profile_action_calendar"])
    ui.tap(S["profile_action_calendar"])
    which, _ = ui.wait_any([S["calendar_enable"], S["calendar_copy_link"]], 30)
    if which == S["calendar_enable"]:
        ui.tap(S["calendar_enable"])
        STATE["calendar_created"] = True
    ui.wait(S["calendar_copy_link"], 30)
    status, body, _ = API.call("GET", "me/calendar")
    data = body["data"]
    assert data["enabled"] and data["feed_url"], f"calendar status {data}"
    feed = data["feed_url"].replace("10.0.2.2", "127.0.0.1") if "127.0.0.1" in HOST_URL else data["feed_url"]
    status, payload, headers = API.call("GET", feed, raw=True, auth=False)
    assert status == 200, f"feed HTTP {status}"
    assert b"BEGIN:VCALENDAR" in payload, "feed is not iCalendar"
    assert "text/calendar" in (headers.get("Content-Type") or ""), "feed content type"
    ui.back()


# ---------------------------------------------------------------- phase 2 (Mobile API 1.7.0)

def staff_flag(name):
    _, body, _ = API.call("GET", "health", auth=False)
    return bool((body.get("data") or {}).get("features", {}).get(name))


def on_hub(nodes=None):
    """The hub: its title is on screen and the bottom navigation is not (Profile shows both)."""
    nodes = nodes if nodes is not None else ui.dump()
    return ui.find(S["staff_title"], exact=True, nodes=nodes) and not ui.find(S["nav_profile"], exact=True, nodes=nodes)


def back_to_hub():
    for _ in range(6):
        if on_hub():
            return
        if ui.find(S["nav_profile"], exact=True):
            break
        ui.hide_keyboard()
        ui.back()
    open_staff_hub()


@step("circulation: loan by API, return by typed copy code in the app")
def circulation_return():
    if not staff_flag("staff_circulation"):
        raise SkipStep("staff_circulation is off on this instance")
    _, me, _ = API.call("GET", "me")
    user_id = me["data"]["id"]
    status, book, _ = API.call("POST", "staff/books", {"title": CIRC_TITLE, "copies": 1})
    assert status in (200, 201), f"create book HTTP {status}"
    CREATED.append(book["data"]["id"])
    code = API.book(book["data"]["id"])["copies"][0]["inventory_number"]
    key = f"e2e-{RUN}-loan"
    status, loan, _ = API.call("POST", "staff/loans", {"user_id": user_id, "book_id": book["data"]["id"], "immediate": True,
                                                       "copy_code": code}, headers={"Idempotency-Key": key})
    assert status in (200, 201), f"create loan HTTP {status}: {loan.get('error')}"
    loan_id = loan["data"]["id"]
    # The same submission again is a replay, not a second loan.
    status, again, _ = API.call("POST", "staff/loans", {"user_id": user_id, "book_id": book["data"]["id"], "immediate": True,
                                                        "copy_code": code}, headers={"Idempotency-Key": key})
    assert status == 200 and again["data"]["id"] == loan_id, f"replay HTTP {status}"
    back_to_hub()
    ui.scroll_to(S["staff_scan_copy"])
    ui.tap(S["staff_scan_copy"])
    deny_camera_prompt()
    ui.tap(S["staff_copy_code"])
    ui.type_text(code)
    ui.hide_keyboard()
    ui.tap(S["staff_scan_lookup"])
    ui.wait(CIRC_TITLE, 30)
    ui.tap(S["staff_loan_action_return"], exact=True)
    ui.wait(S["staff_return_title"], 15)
    time.sleep(1.5)
    nodes = ui.dump()
    buttons = [n for n in nodes if n.text == S["staff_loan_action_return"]]
    ui.tap_xy(*buttons[-1].center)
    ui.wait(s("staff_copy_returned_count", 1), 30)
    _, after, _ = API.call("GET", f"staff/loans/{loan_id}")
    assert after["data"]["status"] == "restituito", f"loan status {after['data']['status']}"
    back_to_hub()
    return f"loan #{loan_id} returned by code {code}"


@step("copies: add a copy with an explicit code from the book editor")
def copy_with_code():
    if not staff_flag("staff_copies"):
        raise SkipStep("staff_copies is off on this instance")
    if not CREATED:
        raise SkipStep("no book created by this run")
    book_id = CREATED[-1]
    title = API.book(book_id)["title"]
    code = f"E2E-{RUN}-2"
    back_to_hub()
    ui.scroll_to(S["staff_books"], exact=True)
    ui.tap(S["staff_books"], exact=True)
    ui.tap(S["staff_books_search"])
    ui.type_text(title)
    ui.hide_keyboard()
    ui.tap(title, timeout=30)
    ui.wait(S["staff_book_save"], 30)
    ui.scroll_to(S["staff_copy_add_with_code"])
    ui.tap(S["staff_copy_add_with_code"])
    ui.tap(S["staff_copy_code"])
    ui.type_text(code)
    ui.hide_keyboard()
    ui.tap(S["action_save"], exact=True)
    ui.wait(S["staff_copy_added"], 120)        # the copy save runs the full admin pipeline
    codes = [c.get("inventory_number") for c in API.book(book_id)["copies"]]
    assert code in codes, f"{code} not in {codes}"
    back_to_hub()
    return f"{code} on book #{book_id}"


@step("series: create a series in the app")
def series_roundtrip():
    if not staff_flag("staff_series"):
        raise SkipStep("staff_series is off on this instance")
    back_to_hub()
    ui.scroll_to(S["staff_series"])
    ui.tap(S["staff_series"], exact=True)
    ui.tap(S["staff_series_new"])
    ui.tap(S["staff_field_name"])
    ui.type_text(SERIES_NAME)
    ui.hide_keyboard()
    ui.tap(S["action_save"], exact=True)
    ui.wait(S["staff_series_add_books"], 30)
    _, rows, _ = API.call("GET", "staff/series?flat=1&q=" + urllib.request.quote(SERIES_NAME))
    found = [r for r in rows.get("data") or [] if r.get("name") == SERIES_NAME]
    CREATED_SERIES.extend(r["id"] for r in found)
    assert len(found) == 1, f"expected the series once, found {len(found)}"
    back_to_hub()
    return f"series #{found[0]['id']}"


@step("imports: a two-row CSV job runs to completion in the app")
def csv_import_two_rows():
    if not staff_flag("staff_imports"):
        raise SkipStep("staff_imports is off on this instance (backend pending)")
    csv = "titolo;autore\n" + "\n".join(f"{t};E2E Author" for t in IMPORT_TITLES) + "\n"
    status, job, _ = API.call("POST", "staff/imports", multipart={
        "file": (f"e2e-{RUN}.csv", csv.encode(), "text/csv"), "kind": "csv", "enable_scraping": "0"})
    assert status == 201, f"create import HTTP {status}: {job.get('error')}"
    back_to_hub()
    ui.scroll_to(S["staff_imports"])
    ui.tap(S["staff_imports"])
    ui.scroll_to(f"e2e-{RUN}.csv")
    ui.tap(f"e2e-{RUN}.csv")
    ui.wait(S["staff_import_status_done"], 120)
    for t in IMPORT_TITLES:
        rows = [r for r in API.find_book(t) if r["title"] == t]
        CREATED.extend(r["id"] for r in rows if r["id"] not in CREATED)
        assert len(rows) == 1, f"{t!r} imported {len(rows)} times"
    back_to_hub()
    return f"job {job['data']['id']}"


@step("desiderata: a free offer is received into a picked record from the app")
def desiderata_receive():
    if not staff_flag("staff_desiderata"):
        raise SkipStep("staff_desiderata is off on this instance (backend pending)")
    target = next((r for r in API.find_book(CIRC_TITLE) if r.get("title") == CIRC_TITLE), None)
    if target is None:
        raise SkipStep("no record created by this run to receive into")
    title = f"E2E offer {RUN}"
    # A free offer (no wanted title): the run never touches someone else's wanted list.
    status, offer, _ = API.call("POST", "desiderata/offers", {"submission_id": str(uuid.uuid4()), "title": title, "author": "E2E",
                                                              "notes": f"E2E {RUN}", "consent": True})
    assert status in (200, 201), f"offer HTTP {status}: {offer.get('error')}"
    offer_id = offer["data"]["id"]
    CREATED_OFFERS.append(offer_id)
    back_to_hub()
    ui.scroll_to(S["staff_desiderata"])
    ui.tap(S["staff_desiderata"])
    ui.scroll_to(title)
    ui.tap(title)
    time.sleep(1.5)                           # let the sheet finish expanding
    ui.tap(S["staff_offer_receive"])
    ui.wait(S["staff_offer_receive_into"], 30)
    ui.tap(S["cd_clear"])
    ui.type_text(CIRC_TITLE)
    ui.hide_keyboard()
    ui.tap(CIRC_TITLE, timeout=60)
    ui.wait(S["staff_offer_received"], 120)
    _, after, _ = API.call("GET", "staff/desiderata/offers?status=received&q=" + urllib.request.quote(title))
    got = [o for o in after.get("data") or [] if o["id"] == offer_id]
    assert got and (got[0].get("received_book") or {}).get("id") == target["id"], f"offer after receive: {got}"
    back_to_hub()
    return f"offer #{offer_id} received into book #{target['id']}"


@step("archives: create an unpublished unit in the app")
def archive_create_delete():
    if not staff_flag("staff_archives"):
        raise SkipStep("staff_archives is off on this instance (backend pending)")
    back_to_hub()
    ui.scroll_to(S["staff_archives"])
    ui.tap(S["staff_archives"], exact=True)
    ui.tap(S["staff_archive_new"])
    ui.tap(S["staff_archive_published"])          # new units start published: turn it off
    ui.tap(S["staff_archive_reference_code"])
    ui.type_text(ARCHIVE_REF)
    ui.hide_keyboard()
    ui.tap(S["staff_archive_constructed_title"])
    ui.type_text(f"E2E unit {RUN}")
    ui.hide_keyboard()
    ui.tap(S["action_save"], exact=True)
    ui.wait(S["staff_archive_created"], 30)
    _, rows, _ = API.call("GET", "staff/archives?published=all&q=" + urllib.request.quote(ARCHIVE_REF))
    found = [r for r in rows.get("data") or [] if r.get("reference_code") == ARCHIVE_REF]
    CREATED_ARCHIVES.extend(r["id"] for r in found)
    assert len(found) == 1 and not found[0].get("published"), f"archive rows {found}"
    _, public, _ = API.call("GET", "archives?q=" + urllib.request.quote(ARCHIVE_REF))
    assert not any(r.get("reference_code") == ARCHIVE_REF for r in public.get("data") or []), "unpublished unit is public"
    back_to_hub()
    return f"archive #{found[0]['id']}"


@step("periodicals desk: an unknown code is looked up, nothing is changed")
def periodicals_desk():
    if not staff_flag("staff_periodicals"):
        raise SkipStep("staff_periodicals is off on this instance")
    code = "0000000000017"
    _, expected, _ = API.call("GET", "periodicals/staff/scan-lookup?code=" + code)
    assert (expected.get("data") or {}).get("match") == "none", f"code {code} unexpectedly matches: {expected.get('data')}"
    back_to_hub()
    ui.scroll_to(S["staff_periodicals"])
    ui.tap(S["staff_periodicals"], exact=True)
    deny_camera_prompt()
    ui.tap(S["staff_periodicals_code"])
    ui.type_text(code)
    ui.hide_keyboard()
    ui.tap(S["staff_scan_lookup"])
    ui.wait(S["staff_periodicals_no_match"], 60)
    ui.back()
    back_to_hub()
    return f"no match for {code}"


@step("book clubs: create a club in the app")
def club_create():
    if not staff_flag("staff_bookclub"):
        raise SkipStep("staff_bookclub is off on this instance")
    back_to_hub()
    ui.scroll_to(S["staff_clubs"])
    ui.tap(S["staff_clubs"], exact=True)
    ui.tap(S["staff_club_new"])
    ui.tap(S["staff_field_name"])
    ui.type_text(CLUB_NAME)
    ui.hide_keyboard()
    ui.scroll_to(S["action_save"], exact=True)
    ui.tap(S["action_save"], exact=True)
    ui.wait(S["staff_club_workflow_edit"], 60)      # the new club's page
    _, clubs, _ = API.call("GET", "bookclub/staff/clubs")
    found = [c for c in clubs.get("data") or [] if c.get("name") == CLUB_NAME]
    CREATED_CLUBS.extend(c["id"] for c in found)
    assert len(found) == 1 and found[0].get("privacy") == "public", f"clubs named {CLUB_NAME!r}: {found}"
    back_to_hub()
    return f"club #{found[0]['id']}"


@step("FRBR: create a work in the app")
def work_create():
    if not staff_flag("staff_frbr"):
        raise SkipStep("staff_frbr is off on this instance")
    back_to_hub()
    ui.scroll_to(S["staff_works"])
    ui.tap(S["staff_works"], exact=True)
    ui.tap(S["staff_work_new"])
    ui.tap(S["staff_work_uniform_title"])
    ui.type_text(WORK_TITLE)
    ui.hide_keyboard()
    ui.scroll_to(S["action_save"], exact=True)    # the work form is taller than the sheet
    ui.tap(S["action_save"], exact=True)
    ui.wait(S["staff_work_attach"], 60)             # the new work's page
    _, works, _ = API.call("GET", "staff/works?q=" + urllib.request.quote(WORK_TITLE))
    found = [w for w in works.get("data") or [] if w.get("uniform_title") == WORK_TITLE]
    CREATED_WORKS.extend(w["id"] for w in found)
    assert len(found) == 1, f"works titled {WORK_TITLE!r}: {found}"
    back_to_hub()
    return f"work #{found[0]['id']}"


# ---------------------------------------------------------------- database (optional)

DB_FLAGS = ("show_sesso", "show_cod_fiscale")
DB_BEFORE = {}


def db_configured():
    return bool(os.environ.get("PINAKES_E2E_DB_NAME") and os.environ.get("PINAKES_E2E_DB_USER"))


def sql(query):
    """Run one statement with the mysql client; the password goes through MYSQL_PWD, never argv."""
    cmd = ["mysql", "-N", "-B", "-u", os.environ["PINAKES_E2E_DB_USER"]]
    if os.environ.get("PINAKES_E2E_DB_SOCKET"):
        cmd += ["--socket", os.environ["PINAKES_E2E_DB_SOCKET"]]
    else:
        cmd += ["-h", os.environ.get("PINAKES_E2E_DB_HOST", "127.0.0.1"), "-P", os.environ.get("PINAKES_E2E_DB_PORT", "3306")]
    cmd += [os.environ["PINAKES_E2E_DB_NAME"], "-e", query]
    envp = dict(os.environ, MYSQL_PWD=os.environ.get("PINAKES_E2E_DB_PASSWORD", ""))
    res = subprocess.run(cmd, capture_output=True, text=True, env=envp)
    if res.returncode != 0:
        raise RuntimeError("mysql failed: " + res.stderr.strip().splitlines()[-1] if res.stderr.strip() else "mysql failed")
    return res.stdout


def set_visibility(values):
    """values: {flag: "0"/"1"/None}; None deletes the row (back to the default: shown)."""
    for flag, value in values.items():
        if value is None:
            sql(f"DELETE FROM system_settings WHERE category='registration' AND setting_key='{flag}'")
        else:
            sql("INSERT INTO system_settings (category, setting_key, setting_value, updated_at) "
                f"VALUES ('registration', '{flag}', '{value}', NOW()) "
                "ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value), updated_at=NOW()")


def wait_health_visibility(expected, timeout=120):
    """The server caches settings for up to 60 s: wait until /health reports [expected]
    ({flag: bool})."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        _, body, _ = API.call("GET", "health", auth=False)
        reg = (body.get("data") or {}).get("registration") or {}
        if all(reg.get(flag) is shown for flag, shown in expected.items()):
            return
        time.sleep(5)
    raise AssertionError(f"/health never reported {expected}")


def open_profile_editor():
    open_tab("nav_profile")
    ui.wait(S["profile_action_edit"], 90)
    ui.tap(S["profile_action_edit"])
    ui.wait(S["profile_first_name"], 30)


def editor_has(label):
    for _ in range(4):
        if ui.find(label, exact=True):
            return True
        ui.swipe_up()
    return False


@step("profile: hidden gender / tax code are not shown, then shown again")
def hidden_profile_fields():
    if not db_configured():
        raise SkipStep("PINAKES_E2E_DB_* not set")
    rows = sql("SELECT setting_key, setting_value FROM system_settings WHERE category='registration' "
               "AND setting_key IN ('show_sesso','show_cod_fiscale')")
    DB_BEFORE.update({k: None for k in DB_FLAGS})
    for line in rows.splitlines():
        key, _, value = line.partition("\t")
        DB_BEFORE[key] = value
    try:
        set_visibility({k: "0" for k in DB_FLAGS})
        wait_health_visibility({k: False for k in DB_FLAGS})
        _, me, _ = API.call("GET", "me")
        assert set(me["data"].get("hidden_fields") or []) == {"sesso", "cod_fiscale"}, me["data"].get("hidden_fields")
        assert "sesso" not in me["data"] and "cod_fiscale" not in me["data"], "hidden keys still in /me"
        # Background and foreground the app: Profile re-reads /me and the field schema on resume.
        ui.shell("input keyevent KEYCODE_HOME")
        time.sleep(1)
        ui.shell(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1")
        time.sleep(4)
        open_profile_editor()
        assert not editor_has(S["profile_tax_code"]), "tax code shown while hidden"
        assert not editor_has(S["profile_gender"]), "gender shown while hidden"
        ui.back()
    finally:
        set_visibility(DB_BEFORE)
    wait_health_visibility({k: DB_BEFORE.get(k) not in ("0", "false") for k in DB_FLAGS})
    return "hidden, checked, restored"


def cleanup():
    print("--> cleanup", flush=True)
    try:
        _cleanup_server()
    except Exception as e:  # noqa: BLE001 - the device must be reset even if the server is slow
        print(f"    server cleanup incomplete: {e} (left: books {CREATED})")
    ui.adb("shell", "pm", "clear", PKG, check=False)


def _cleanup_server():
    # Records this run created even when a step failed half-way (the ISBN was absent at
    # preflight and the manual title is unique to the run).
    if API.token:
        for q, match in ((ISBN, lambda r: r.get("isbn13") == ISBN or r.get("ean") == ISBN),
                         (MANUAL_TITLE, lambda r: r.get("title") == MANUAL_TITLE)):
            for r in API.find_book(q):
                if match(r) and r["id"] not in CREATED:
                    CREATED.append(r["id"])
    try:
        ui.airplane(False)
    except Exception:  # noqa: BLE001
        pass
    if API.token:
        # Phase 2 records found by their run-unique names, in case a step failed half-way.
        for t in (CIRC_TITLE,) + IMPORT_TITLES:
            for r in API.find_book(t):
                if r.get("title") == t and r["id"] not in CREATED:
                    CREATED.append(r["id"])
        for book_id in list(CREATED):
            # A loan this run left open keeps the book: return it first.
            _, loans, _ = API.call("GET", f"staff/loans?bucket=all&book_id={book_id}")
            for loan in loans.get("data") or []:
                if "return" in (loan.get("actions") or []):
                    status, _, _ = API.call("POST", f"staff/loans/{loan['id']}/return", {"outcome": "restituito"})
                    print(f"    return loan #{loan['id']}: HTTP {status}")
        for series_id in CREATED_SERIES:
            status, _, _ = API.call("DELETE", f"staff/series/{series_id}")
            print(f"    delete series #{series_id}: HTTP {status}")
        for archive_id in CREATED_ARCHIVES:
            status, _, _ = API.call("DELETE", f"staff/archives/{archive_id}")
            print(f"    delete archive unit #{archive_id}: HTTP {status}")
        # Tier 2 records found by their run-unique names, in case a step failed half-way.
        _, clubs, _ = API.call("GET", "bookclub/staff/clubs")
        CREATED_CLUBS.extend(c["id"] for c in clubs.get("data") or [] if c.get("name") == CLUB_NAME and c["id"] not in CREATED_CLUBS)
        _, works, _ = API.call("GET", "staff/works?q=" + urllib.request.quote(WORK_TITLE))
        CREATED_WORKS.extend(w["id"] for w in works.get("data") or [] if w.get("uniform_title") == WORK_TITLE and w["id"] not in CREATED_WORKS)
        for club_id in CREATED_CLUBS:
            status, _, _ = API.call("DELETE", f"bookclub/staff/clubs/{club_id}")
            print(f"    delete book club #{club_id}: HTTP {status}")
        for work_id in CREATED_WORKS:
            status, _, _ = API.call("DELETE", f"staff/works/{work_id}")
            print(f"    delete work #{work_id}: HTTP {status}")
        for offer_id in CREATED_OFFERS:
            status, _, _ = API.call("DELETE", f"staff/desiderata/offers/{offer_id}")
            print(f"    delete offer #{offer_id}: HTTP {status}")
    for book_id in CREATED:
        status, _, _ = API.call("DELETE", f"staff/books/{book_id}")
        print(f"    delete book #{book_id}: HTTP {status}")
    if API.token:
        # Only undo what this run did: a link that existed before the run is left alone.
        if STATE["calendar_was_enabled"] is False and STATE.get("calendar_created"):
            status, _, _ = API.call("DELETE", "me/calendar/token")
            print(f"    revoke calendar link created by this run: HTTP {status}")
        # Only tokens issued during this run (the app's login); never pre-existing sessions.
        if STATE["devices_known"]:
            _, devs, _ = API.call("GET", "me/devices")
            for d in devs.get("data") or []:
                if d["id"] not in DEVICES_BEFORE and not d.get("is_current"):
                    status, _, _ = API.call("DELETE", f"me/devices/{d['id']}")
                    print(f"    revoke app device token #{d['id']} created by this run: HTTP {status}")
        API.call("POST", "auth/logout")


def main():
    S.update(load_strings())
    ok = preflight()
    try:
        # Without a signed-in app the remaining steps cannot run: stop there.
        if ok and onboarding_and_login():
            for fn in (staff_hub, scan_add, scan_duplicate, manual_record, edit_location,
                       circulation_return, copy_with_code, series_roundtrip, csv_import_two_rows, desiderata_receive, archive_create_delete,
                       periodicals_desk, club_create, work_create,
                       offline, calendar, hidden_profile_fields):
                fn()
    finally:
        cleanup()
    passed = sum(1 for r in RESULTS if r[1] == "PASS")
    skipped = sum(1 for r in RESULTS if r[1] == "SKIP")
    failed = sum(1 for r in RESULTS if r[1] == "FAIL")
    print(f"\nlive smoke: {passed} passed, {skipped} skipped, {failed} failed")
    for name, result, detail in RESULTS:
        print(f"  {result:4} {name} {detail}")
    return 1 if failed or not passed else 0


if __name__ == "__main__":
    sys.exit(main())
