import contextlib, io, json, os, re, subprocess, sys, time, urllib.request, urllib.parse
import xml.etree.ElementTree as ET
from PIL import Image

PKG = "com.legend.keyboard"
IME = f"{PKG}/.LegendIME"
DBG = open("insta_debug.txt", "w")
WAITS = []
T0 = None
rec = None

def log(*a):
    line = " ".join(str(x) for x in a)
    print(line, flush=True)
    DBG.write(line + "\n"); DBG.flush()

def sh(cmd, timeout=90):
    try:
        r = subprocess.run(["adb", "shell", cmd], capture_output=True, text=True, timeout=timeout)
        return (r.stdout + r.stderr).strip()
    except subprocess.TimeoutExpired:
        return ""

def tap(x, y):
    subprocess.run(["adb", "shell", "input", "tap", str(int(x)), str(int(y))])

def shot(name):
    png = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout
    open(name, "wb").write(png)
    return Image.open(io.BytesIO(png)).convert("RGB")

def ui_nodes():
    sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1")
    xml = sh("cat /sdcard/ui.xml")
    try:
        root = ET.fromstring(xml)
    except Exception:
        return []
    out = []
    for n in root.iter("node"):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        out.append(dict(text=n.get("text", ""), desc=n.get("content-desc", ""),
                        cx=(x1 + x2) // 2, cy=(y1 + y2) // 2))
    return out

def norm(s):
    return s.replace("\u2019", "'").strip().lower()

def click_any(names):
    nodes = ui_nodes()
    for want in names:
        w = norm(want)
        for n in nodes:
            if norm(n["text"]) == w or norm(n["desc"]) == w:
                tap(n["cx"], n["cy"])
                log("clicked:", want)
                return want
    return None

def focus():
    return sh("dumpsys window | grep -E 'mCurrentFocus' | head -2")

def start_rec():
    global rec, T0
    sh("touch /sdcard/rec_on; rm -f /sdcard/seg_*.mp4")
    rec = subprocess.Popen(["adb", "shell",
        "i=0; while [ -f /sdcard/rec_on ]; do screenrecord --size 720x1600 --bit-rate 3000000 --time-limit 170 /sdcard/seg_$i.mp4; i=$((i+1)); done"])
    T0 = time.time()

def stop_rec():
    sh("rm -f /sdcard/rec_on; kill -2 $(pidof screenrecord)")
    try:
        rec.wait(timeout=40)
    except Exception:
        pass
    time.sleep(2)
    for f in sh("ls /sdcard/seg_*.mp4").split():
        subprocess.run(["adb", "pull", f, "."])
    json.dump({"waits": WAITS}, open("timeline.json", "w"))

@contextlib.contextmanager
def waiting():
    s = time.time() - T0
    try:
        yield
    finally:
        e = time.time() - T0
        if e - s > 6:
            WAITS.append([s, e])

def find_keyboard(img):
    w, h = img.size
    px = img.load()
    best = (0, 0, 0)
    start = None
    for y in range(h + 1):
        ok = False
        if y < h:
            r, g, b = px[2, y]
            ok = (b - g >= 25) and r < 50 and g < 50 and 30 <= b < 110
        if ok and start is None:
            start = y
        if not ok and start is not None:
            if y - start > best[0]:
                best = (y - start, start, y)
            start = None
    return best

ROWS = {
    "tool": (0, 0.9, [("THEME", 1), ("CLIPS", 1), ("S0", 2.4), ("S1", 2.4), ("S2", 2.4), ("EMOJI", 1)]),
    "r1": (0.9, 1, [(c, 1) for c in "qwertyuiop"]),
    "r2": (1.9, 1, [(c, 1) for c in "asdfghjkl"]),
    "r3": (2.9, 1, [("SHIFT", 1.5)] + [(c, 1) for c in "zxcvbnm"] + [("DEL", 1.5)]),
    "bot": (3.9, 1, [("123", 1.4), ("LANG", 1.3), (",", 1), ("SPACE", 4), (".", 1), ("ENTER", 1.5)]),
}

def pos(label, top, kbh, W):
    unit = kbh / 4.9
    for start, h, keys in ROWS.values():
        total = sum(w for _, w in keys)
        cum = 0
        for l, w in keys:
            if l == label:
                return W * (cum + w / 2) / total, top + unit * (start + h / 2)
            cum += w
    raise KeyError(label)

DISMISS = ["Don't allow", "Not now", "No thanks", "Got it", "Skip", "No, thanks"]
DL_BTNS = ["Wait", "Download anyway", "Keep", "Download", "Continue", "Allow"]

def apk_url():
    req = urllib.request.Request("https://api.github.com/repos/termux/termux-app/releases/latest",
                                 headers={"User-Agent": "demo", "Accept": "application/vnd.github+json"})
    tok = os.environ.get("GH_TOKEN")
    if tok:
        req.add_header("Authorization", f"Bearer {tok}")
    d = json.load(urllib.request.urlopen(req, timeout=30))
    assets = d["assets"]
    log("release:", d["tag_name"], [a["name"] for a in assets])
    for a in assets:
        if "x86_64" in a["name"] and "github-debug" in a["name"]:
            return a["name"], a["browser_download_url"]
    for a in assets:
        if "x86_64" in a["name"]:
            return a["name"], a["browser_download_url"]
    raise RuntimeError("no x86_64 apk")

def install_bundle(name):
    import glob, zipfile
    subprocess.run(["adb", "pull", f"/sdcard/Download/{name}", "bundle.zip"])
    zipfile.ZipFile("bundle.zip").extractall("bundle")
    apks = sorted(glob.glob("bundle/**/*.apk", recursive=True))
    log("bundle apks:", apks)
    r = subprocess.run(["adb", "install-multiple", "-r", "-g"] + apks, capture_output=True, text=True)
    log("install-multiple:", r.stdout.strip(), r.stderr.strip()[:500])

def install_local():
    import os, glob, zipfile, shutil
    if not os.path.exists("ig_file.txt"):
        return False
    f = open("ig_file.txt").read().strip()
    if not os.path.exists(f):
        return False
    log("local file:", f, os.path.getsize(f))
    if f.endswith(".apk"):
        r = subprocess.run(["adb", "install", "-r", "-g", f], capture_output=True, text=True, timeout=300)
        log("install:", r.stdout.strip(), r.stderr.strip()[:500])
    else:
        shutil.rmtree("igx", ignore_errors=True)
        zipfile.ZipFile(f).extractall("igx")
        apks = sorted(glob.glob("igx/**/*.apk", recursive=True))
        log("bundle apks:", [os.path.basename(a) for a in apks])
        r = subprocess.run(["adb", "install-multiple", "-r", "-g"] + apks, capture_output=True, text=True, timeout=300)
        log("install-multiple:", r.stdout.strip(), r.stderr.strip()[:500])
        if "Success" not in r.stdout:
            base = [a for a in apks if os.path.basename(a) in ("com.instagram.android.apk", "base.apk")]
            keep = base + [a for a in apks if re.search(r"arm64|x86_64|xxhdpi|xhdpi|en\.apk|nodpi", os.path.basename(a)) and a not in base]
            if keep:
                r = subprocess.run(["adb", "install-multiple", "-r", "-g"] + keep, capture_output=True, text=True, timeout=300)
                log("retry install-multiple:", r.stdout.strip(), r.stderr.strip()[:500])
    return "package:com.instagram.android" in sh("pm list packages com.instagram.android")

def clear_field():
    sh("input keyevent KEYCODE_MOVE_END")
    sh("input keyevent " + " ".join(["KEYCODE_DEL"] * 40))

def type_text(txt):
    import shlex
    clear_field()
    sh("input text " + shlex.quote(txt))

def login_flow():
    user, pw = os.environ.get("IG_USER", ""), os.environ.get("IG_PASS", "")
    if not user or not pw:
        log("login: no credentials in env"); return
    log("login: start")
    for _ in range(5):
        if click_any(["I already have a profile", "Log in", "Log In"]):
            break
        time.sleep(2)
    time.sleep(4)
    shot("i7.png")
    if not click_any(["Username, email or mobile number", "Username, email or mobile number, "]):
        nodes = [n for n in ui_nodes() if "username" in norm(n["text"] + " " + n["desc"])]
        if nodes: tap(nodes[0]["cx"], nodes[0]["cy"])
    time.sleep(1); type_text(user); time.sleep(1)
    if not click_any(["Password"]):
        nodes = [n for n in ui_nodes() if "password" in norm(n["text"] + " " + n["desc"])]
        if nodes: tap(nodes[0]["cx"], nodes[0]["cy"])
    time.sleep(1); type_text(pw); time.sleep(1)
    sh("input keyevent KEYCODE_BACK")
    time.sleep(1)
    shot("i8.png")
    log("login: clicked:", click_any(["Log in", "Log In"]))
    time.sleep(15)
    shot("i9.png")

    def screen_text():
        return " | ".join(sorted({n["text"] or n["desc"] for n in ui_nodes() if (n["text"] or n["desc"])}))

    txt = screen_text()
    log("login: screen after submit:", txt[:600])
    if "approval" in txt.lower() or "other device" in txt.lower():
        log("APPROVAL_PROMPT: shown -- open Instagram on your phone now and approve the login request")
        end = time.time() + 170
        n = 0
        while time.time() < end:
            t2 = screen_text()
            if "approval" not in t2.lower() and "other device" not in t2.lower():
                log("login: approval cleared, screen now:", t2[:300])
                break
            n += 1
            if n % 4 == 0:
                shot(f"i9w{n}.png")
                log("login: still waiting for approval...", int(end - time.time()), "s left")
            time.sleep(5)
        else:
            log("login: approval timed out")

    for i in range(4):
        c = click_any(["Not now", "Not Now", "Skip", "Allow", "OK", "Wait"])
        log("login: post-click", c)
        time.sleep(4)
    shot("i10.png")
    time.sleep(4)
    sh("input swipe 540 1700 540 900 500"); time.sleep(3)
    shot("i11.png")
    log("login: focus:", focus())
    log("login: screen text:", screen_text()[:600])

def main():
    for _f in ("ig_fetch_out.txt",):
        if os.path.exists(_f):
            log("--- " + _f); log(open(_f).read()[-2500:]); log("--- end")
    log(subprocess.run(["adb", "root"], capture_output=True, text=True).stdout.strip())
    time.sleep(3)
    subprocess.run(["adb", "wait-for-device"])
    log("abilist:", sh("getprop ro.product.cpu.abilist"))
    pk = sh("pm list packages | grep -iE 'chrome|instagram'")
    log("packages:", pk.replace("\n", " | "))
    chrome = "package:com.android.chrome" in pk
    cr = "-p com.android.chrome" if chrome else ""
    if chrome:
        sh("echo '_ --disable-fre --no-first-run --no-default-browser-check' > /data/local/tmp/chrome-command-line")
        sh("chmod 755 /data/local/tmp/chrome-command-line")
        sh("am set-debug-app --persistent com.android.chrome")
    sh("input keyevent KEYCODE_WAKEUP"); sh("wm dismiss-keyguard")
    time.sleep(1)

    start_rec()
    ok = False
    try:
        sh("input keyevent KEYCODE_HOME"); time.sleep(3)
        shot("i0.png")
        sh("cmd appops set com.android.chrome REQUEST_INSTALL_PACKAGES allow")
        sh("rm -f /sdcard/Download/*")
        if install_local():
            log("installed via adb: True")
            shot("i4.png")
            sh("monkey -p com.instagram.android -c android.intent.category.LAUNCHER 1")
            time.sleep(12)
            for _ in range(4):
                click_any(["Wait", "Don't allow", "Allow", "Not now", "OK"])
                time.sleep(2)
            log("focus:", focus(), "| pid:", sh("pidof com.instagram.android"))
            shot("i5.png")
            login_flow()
            ok = True
            return
        sh(f"am start -a android.intent.action.VIEW -d https://instagram.en.uptodown.com/android/download {cr}")
        time.sleep(8)
        for _ in range(3):
            if not click_any(DISMISS):
                break
            time.sleep(1.5)
        shot("i1.png")
        name = None
        started = time.time()
        with waiting():
            end = time.time() + 300
            while time.time() < end:
                files = [f for f in sh("ls /sdcard/Download").split("\n")
                         if f.lower().endswith((".apk", ".xapk", ".apkm", ".apks"))]
                if files:
                    name = files[0]
                    break
                click_any(DL_BTNS + DISMISS)
                if time.time() - started > 25:
                    click_any(["Download", "DOWNLOAD", "Download APK", "Download Instagram"])
                time.sleep(4)
        log("downloaded:", name, "| files:", sh("ls -la /sdcard/Download").replace("\n", " | "), "| focus:", focus())
        shot("i2.png")
        if not name:
            log("no download happened")
            return
        time.sleep(3)
        installed = lambda: "package:com.instagram.android" in sh("pm list packages com.instagram.android")
        if name.lower().endswith(".apk"):
            for _ in range(6):
                if "instagram" in focus() or "packageinstaller" in focus().lower():
                    break
                if click_any(["Open"]):
                    break
                time.sleep(1)
            time.sleep(3)
            shot("i3.png")
            end = time.time() + 120
            while time.time() < end:
                if "instagram" in focus().lower():
                    break
                click_any(["Install", "Install anyway", "Update", "Open"])
                time.sleep(3)
            if not installed():
                log("UI install failed; focus:", focus())
                sh(f'cp "/sdcard/Download/{name}" /data/local/tmp/ig.apk')
                log(sh("pm install -r -g /data/local/tmp/ig.apk")[:500])
        else:
            install_bundle(name)
        log("installed:", installed())
        if not installed():
            return
        shot("i4.png")
        sh("monkey -p com.instagram.android -c android.intent.category.LAUNCHER 1")
        time.sleep(12)
        for _ in range(3):
            click_any(["Don't allow", "Allow", "Not now", "OK"])
            time.sleep(2)
        log("focus:", focus(), "| pid:", sh("pidof com.instagram.android"))
        shot("i5.png")
        time.sleep(6)
        sh("input swipe 540 1700 540 900 500"); time.sleep(3)
        shot("i6.png")
        time.sleep(4)
        ok = True
    finally:
        stop_rec()
    sys.exit(0 if ok else 1)

main()
