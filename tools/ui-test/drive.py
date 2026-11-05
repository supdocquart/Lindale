"""Drives the running Lindale dev build on the Xvfb screen and checks what happened.

Started by tools/ui-test/run.sh. Assertions use the app log (logs/latest.log), PulseAudio, the
session D-Bus (MPRIS), the fake Discord socket log and X11 window state. Exit code 0 = all passed.
"""
import os, re, subprocess, sys, time
from Xlib import X, XK, display
from Xlib.ext import xtest

WORK = sys.argv[1]
LOG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "logs", "latest.log")
MUSIC = os.path.expanduser("~/Music/Lindale")
d = display.Display(os.environ.get("DISPLAY", ":97"))
results = []


def log_text():
    try:
        return open(LOG, encoding="utf-8", errors="replace").read()
    except FileNotFoundError:
        return ""


def wait_log(pattern, timeout=60, since=0):
    end = time.time() + timeout
    while time.time() < end:
        m = re.search(pattern, log_text()[since:])
        if m:
            return m
        time.sleep(0.5)
    return None


def check(name, ok, detail=""):
    results.append((name, bool(ok)))
    print(("PASS " if ok else "FAIL ") + name + (f"  ({detail})" if detail and not ok else ""), flush=True)


def windows(name="Lindal"):
    found = []

    def walk(w):
        try:
            n = w.get_wm_name()
            if n and name in str(n):
                g = w.get_geometry()
                pos = w.translate_coords(d.screen().root, 0, 0)
                found.append((w, -pos.x, -pos.y, g.width, g.height, w.get_attributes().map_state))
        except Exception:
            pass
        for c in w.query_tree().children:
            walk(c)

    walk(d.screen().root)
    return found


def main_window():
    vis = [w for w in windows() if w[5] == X.IsViewable and w[3] > 600]
    return max(vis, key=lambda w: w[3] * w[4]) if vis else None


def click(x, y, button=1):
    xtest.fake_input(d, X.MotionNotify, x=x, y=y); d.sync(); time.sleep(0.1)
    xtest.fake_input(d, X.ButtonPress, button); d.sync(); time.sleep(0.05)
    xtest.fake_input(d, X.ButtonRelease, button); d.sync(); time.sleep(0.4)


def key(name, *mods):
    codes = [d.keysym_to_keycode(XK.string_to_keysym(m)) for m in mods]
    for c in codes: xtest.fake_input(d, X.KeyPress, c)
    k = d.keysym_to_keycode(XK.string_to_keysym(name))
    xtest.fake_input(d, X.KeyPress, k); xtest.fake_input(d, X.KeyRelease, k)
    for c in codes: xtest.fake_input(d, X.KeyRelease, c)
    d.sync(); time.sleep(0.08)


def type_text(text):
    for ch in text: key(ch)


def rel(x, y):
    w = main_window()
    return (w[1] + x, w[2] + y) if w else (x, y)


def dbus(*args):
    return subprocess.run(["dbus-send", "--session", "--print-reply", *args], capture_output=True, text=True).stdout


try:
    # 1. Start-up
    started = wait_log(r"\[MEM\]", timeout=900)
    check("app starts", started)
    if not started:
        raise SystemExit
    time.sleep(3)
    engine = os.environ.get("LINDALE_ENGINE", "vlc").lower()
    check(f"{engine} engine loads", re.search(rf"Audio engine: {engine}", log_text()))
    check("media controls (MPRIS) ready", re.search(r"media session: system media controls ready", log_text()))
    check("window is shown", main_window())

    # 2. Welcome guide: skip it
    click(*rel(436, 544)); time.sleep(1)

    # 3. Search
    mark = len(log_text())
    key("k", "Control_L"); time.sleep(0.4)
    type_text("mockingbird"); key("Return")
    check("search opens the results page", wait_log(r"\[UI:NAV\] route=search", 20, mark))
    time.sleep(6)

    # 4. Play: the first result, then again from a local file named after its id (streams from data
    #    centres are often refused by YouTube, a local file always plays).
    mark = len(log_text())
    click(*rel(272, 196))
    m = wait_log(r"\[UI:NOW_PLAYING\] id=([A-Za-z0-9_-]{11})", 20, mark)
    check("a search result starts playing", m)
    if m:
        vid = m.group(1)
        os.makedirs(MUSIC, exist_ok=True)
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=90",
                        "-c:a", "libvorbis", os.path.join(MUSIC, f"Lindale UI test [{vid}].ogg")], check=True)
        mark = len(log_text())
        click(*rel(272, 196)); time.sleep(0.5)
        if not wait_log(r"Playing downloaded file", 5, mark):
            click(*rel(272, 196))
        check("local file plays", wait_log(r"Playing downloaded file", 20, mark))
        time.sleep(4)
        sinks = subprocess.run(["pactl", "list", "short", "sink-inputs"], capture_output=True, text=True).stdout
        check("audio reaches PulseAudio", sinks.strip())
        # The MPRIS name is registered with the first song's metadata.
        check("MPRIS name on the bus", "org.mpris.MediaPlayer2.lindale" in dbus("--dest=org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus.ListNames"))
        status = dbus("--dest=org.mpris.MediaPlayer2.lindale", "/org/mpris/MediaPlayer2",
                      "org.freedesktop.DBus.Properties.Get", "string:org.mpris.MediaPlayer2.Player", "string:PlaybackStatus")
        check("MPRIS says Playing", "Playing" in status, status.strip()[-40:])
        dbus("--dest=org.mpris.MediaPlayer2.lindale", "/org/mpris/MediaPlayer2", "org.mpris.MediaPlayer2.Player.Pause")
        check("MPRIS Pause reaches the player", wait_log(r"media session: Pause", 10, mark))
        dbus("--dest=org.mpris.MediaPlayer2.lindale", "/org/mpris/MediaPlayer2", "org.mpris.MediaPlayer2.Player.Play")
        time.sleep(2)
        discord = open(os.path.join(WORK, "discord.log"), encoding="utf-8").read() if os.path.exists(os.path.join(WORK, "discord.log")) else ""
        check("Discord Rich Presence sent", "SET_ACTIVITY" in discord)

    # 5. Full-screen player and back
    w = main_window()
    click(*rel(202, 752)); time.sleep(2)
    key("Escape"); time.sleep(1)

    # 6. Mini player
    click(*rel(1063, 760)); time.sleep(2)
    mini = [x for x in windows() if x[5] == X.IsViewable and x[3] < 600]
    check("mini player opens", mini and main_window() is None)
    if mini:
        _, mx, my, mw, mh, _ = mini[0]
        click(mx + mw - 18, my + mh // 2); time.sleep(2)
        check("mini player returns to the main window", main_window())

    # 7. Tray: menu > Quit
    click(1412, 912, button=3); time.sleep(1.5)
    click(1440, 988); time.sleep(5)
    check("tray Quit closes the app", not re.search(r"compose\.application\.configure", subprocess.run(["ps", "ax"], capture_output=True, text=True).stdout))
finally:
    check("no crash in the log", "[CRASH]" not in log_text())
    failed = [n for n, ok in results if not ok]
    print(f"\n{len(results) - len(failed)}/{len(results)} passed" + (f"; failed: {', '.join(failed)}" if failed else ""))
    sys.exit(1 if failed else 0)
