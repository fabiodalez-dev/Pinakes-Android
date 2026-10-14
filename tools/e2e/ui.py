#!/usr/bin/env python3
"""Small adb + uiautomator driver for the live smoke test (no extra dependencies).

Usable as a module (``import ui``) or from the shell for poking at a screen:

    ANDROID_SERIAL=emulator-5554 python3 tools/e2e/ui.py dump
    python3 tools/e2e/ui.py tap "Admin area"
    python3 tools/e2e/ui.py shot /tmp/screen.png

Texts are matched case-insensitively against a node's text or content description. The
device is chosen by ANDROID_SERIAL (adb's own variable).
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

SERIAL = os.environ.get("ANDROID_SERIAL", "")
ADB = ["adb"] + (["-s", SERIAL] if SERIAL else [])


def adb(*args, check=True, capture=True):
    res = subprocess.run(ADB + list(args), capture_output=capture)
    if check and res.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args[:3])} failed: {res.stderr.decode(errors='replace').strip()}")
    return res.stdout


def shell(cmd):
    return adb("shell", cmd).decode(errors="replace")


class Node:
    def __init__(self, el):
        self.text = el.get("text") or ""
        self.desc = el.get("content-desc") or ""
        self.res = el.get("resource-id") or ""
        self.cls = el.get("class") or ""
        self.clickable = el.get("clickable") == "true"
        self.checked = el.get("checked") == "true"
        b = [int(v) for v in re.findall(r"\d+", el.get("bounds", "[0,0][0,0]"))]
        self.bounds = b
        self.center = ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)

    def label(self):
        return (self.text + " " + self.desc).strip()

    def __repr__(self):
        return f"Node({self.label()!r} {self.bounds})"


def dump():
    """All nodes of the current screen (retries: uiautomator dump fails while animating)."""
    for _ in range(5):
        out = adb("exec-out", "uiautomator", "dump", "/dev/tty", check=False).decode(errors="replace")
        start, end = out.find("<?xml"), out.rfind("</hierarchy>")
        if start >= 0 and end > start:
            root = ET.fromstring(out[start:end + len("</hierarchy>")])
            return [Node(el) for el in root.iter("node")]
        time.sleep(0.5)
    raise RuntimeError("uiautomator dump failed")


def find(text, exact=False, nodes=None, include_edit=False):
    """First node showing [text]. Editable fields are skipped unless [include_edit]: a field
    that contains what was just typed must not shadow the list row with the same text."""
    want = text.lower()
    for n in nodes if nodes is not None else dump():
        if not include_edit and n.cls == "android.widget.EditText" and n.text:
            continue
        hay = [n.text.lower(), n.desc.lower()]
        if (want in hay) if exact else any(want in h for h in hay if h):
            return n
    return None


def find_res(res_id, nodes=None):
    for n in nodes if nodes is not None else dump():
        if n.res == res_id:
            return n
    return None


def wait(text, timeout=20, exact=False):
    """Wait until [text] is on screen; returns its node or raises."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        n = find(text, exact)
        if n:
            return n
        time.sleep(0.7)
    raise AssertionError(f"not on screen after {timeout}s: {text!r}")


def wait_any(texts, timeout=20):
    deadline = time.time() + timeout
    while time.time() < deadline:
        nodes = dump()
        for t in texts:
            n = find(t, nodes=nodes)
            if n:
                return t, n
        time.sleep(0.7)
    raise AssertionError(f"none on screen after {timeout}s: {texts!r}")


def gone(text, timeout=15):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if not find(text):
            return True
        time.sleep(0.7)
    raise AssertionError(f"still on screen after {timeout}s: {text!r}")


def tap_xy(x, y):
    shell(f"input tap {x} {y}")
    time.sleep(0.6)


def tap(text, timeout=20, exact=False):
    n = wait(text, timeout, exact)
    tap_xy(*n.center)
    return n


_SIZE = None


def screen_size():
    global _SIZE
    if _SIZE is None:
        m = re.search(r"(\d+)x(\d+)", shell("wm size").splitlines()[-1])
        _SIZE = (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)
    return _SIZE


def swipe_up():
    w, h = screen_size()
    shell(f"input swipe {w // 2} {int(h * 0.75)} {w // 2} {int(h * 0.30)} 300")
    time.sleep(0.6)


def swipe_down():
    w, h = screen_size()
    shell(f"input swipe {w // 2} {int(h * 0.30)} {w // 2} {int(h * 0.75)} 300")
    time.sleep(0.6)


def scroll_to(text, max_swipes=8, exact=False, passes=2):
    """Scroll down looking for [text], then back up (content can appear late), [passes] times."""
    for p in range(passes):
        for _ in range(max_swipes):
            n = find(text, exact)
            if n:
                return n
            (swipe_up if p % 2 == 0 else swipe_down)()
    raise AssertionError(f"not found while scrolling: {text!r}")


def _escape(value):
    # `input text` goes through the device shell: escape every non-alphanumeric character.
    out = []
    for ch in value:
        if ch == " ":
            out.append("%s")
        elif ch.isalnum():
            out.append(ch)
        else:
            out.append("\\" + ch)
    return "".join(out)


def type_text(value, secret=False):
    """Type into the focused field. With secret=True nothing is ever echoed."""
    adb("shell", "input text " + _escape(value), check=True)
    time.sleep(0.4)


def fill(label, value, secret=False):
    tap(label)
    shell("input keyevent KEYCODE_MOVE_END")
    type_text(value, secret=secret)
    hide_keyboard()


def keyboard_shown():
    """The IME insets source is visible (more reliable than input_method's mInputShown)."""
    for line in shell("dumpsys window").splitlines():
        if "InsetsSource" in line and "type=ime" in line:
            return "visible=true" in line
    return "mInputShown=true" in shell("dumpsys input_method")


def hide_keyboard():
    """Close the soft keyboard (BACK only when it is really shown, or BACK would navigate)."""
    if keyboard_shown():
        shell("input keyevent KEYCODE_BACK")
        time.sleep(0.6)


def back():
    shell("input keyevent KEYCODE_BACK")
    time.sleep(0.6)


def airplane(on):
    shell(f"cmd connectivity airplane-mode {'enable' if on else 'disable'}")
    time.sleep(3)


def screenshot(path):
    with open(path, "wb") as fh:
        fh.write(adb("exec-out", "screencap", "-p"))
    return path


def main(argv):
    cmd = argv[1] if len(argv) > 1 else "dump"
    if cmd == "dump":
        for n in dump():
            if n.label():
                print(n.label(), n.bounds, "click" if n.clickable else "")
    elif cmd in ("tap", "find"):
        n = (tap if cmd == "tap" else wait)(argv[2])
        print("match", n)
    elif cmd == "shot":
        print(screenshot(argv[2]))
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
