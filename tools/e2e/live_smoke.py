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

Steps: onboarding URL, login, staff hub, manual ISBN add (scraped preview), duplicate add
copies, manual record without ISBN, shelf/level location on edit (skipped when the instance
has no shelf with a level), airplane-mode offline search and detail, calendar link + feed.
Cleanup (only what this run created): created books deleted, the calendar link revoked when
it did not exist before, device tokens issued during the run revoked, app data cleared,
airplane mode off. Exit code 0 only when every step passed.
"""
import json
import os
import random
import re
import sys
import time
import traceback
import urllib.error
import urllib.request

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

    def call(self, method, path, body=None, raw=False, auth=True, base=None):
        url = (base or HOST_URL) + "/api/v1/" + path.lstrip("/") if not path.startswith("http") else path
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Accept", "application/json")
        if data is not None:
            req.add_header("Content-Type", "application/json")
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


API = Api()
CREATED = []          # book ids to delete
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
    ui.wait(S["action_continue"], 30)
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
    ui.tap(S["staff_scan"])
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
    ui.tap(S["staff_add_manual"])
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
    ui.tap(S["staff_books"])
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
    ui.gone(S["staff_book_save"], 120)       # the edit screen closes after the save
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
            for fn in (staff_hub, scan_add, scan_duplicate, manual_record, edit_location, offline, calendar):
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
