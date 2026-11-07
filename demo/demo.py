import io, subprocess, sys, time
from PIL import Image

PKG = "com.legend.keyboard"
DBG = open("demo_debug.txt", "w")

def log(*a):
    line = " ".join(str(x) for x in a)
    print(line, flush=True)
    DBG.write(line + "\n"); DBG.flush()

TEXT = "كلود اسطورة و عم كل ذكاء اصطناعي"

def shell(cmd):
    return subprocess.run(["adb", "shell", cmd], capture_output=True, text=True).stdout.strip()

def tap(x, y):
    subprocess.run(["adb", "shell", "input", "tap", str(int(x)), str(int(y))])

def shot(name):
    png = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout
    open(name, "wb").write(png)
    return Image.open(io.BytesIO(png)).convert("RGB")

def find_keyboard(img):
    """Find vertical run of the Neon gradient background at the far-left edge."""
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
    return best  # length, top, bottom

ROWS = {
    "tool": (0, 0.9, [("THEME", 1), ("CLIPS", 1), ("S0", 2.4), ("S1", 2.4), ("S2", 2.4), ("EMOJI", 1)]),
    "r1": (0.9, 1, [(c, 1) for c in "ضصثقفغعهخحجد"]),
    "r2": (1.9, 1, [(c, 1) for c in "شسيبلاتنمكط"]),
    "r3": (2.9, 1, [(c, 1) for c in "ذئءؤرىةوزظ"] + [("DEL", 1.5)]),
    "bot": (3.9, 1, [("123", 1.4), ("LANG", 1.3), ("،", 1), ("SPACE", 4), (".", 1), ("ENTER", 1.5)]),
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

def main():
    log(subprocess.run(["adb", "install", "-r", "app/build/outputs/apk/debug/app-debug.apk"], capture_output=True, text=True))
    ime = f"{PKG}/.LegendIME"
    for _ in range(15):
        lst = shell("ime list -a -s")
        log("ime list:", lst.replace("\n", " | "))
        if ime in lst:
            break
        time.sleep(2)
    log("enable:", shell(f"ime enable {ime}"))
    log("set:", shell(f"ime set {ime}"))
    shell(f"settings put secure enabled_input_methods {ime}")
    shell(f"settings put secure default_input_method {ime}")
    shell("settings put secure show_ime_with_hard_keyboard 1")
    log("default IME:", shell("settings get secure default_input_method"))
    shell("input keyevent KEYCODE_WAKEUP")
    shell("wm dismiss-keyguard")
    time.sleep(1)

    rec = subprocess.Popen(["adb", "shell", "screenrecord", "--size", "720x1600",
                            "--bit-rate", "5000000", "--time-limit", "170", "/sdcard/demo.mp4"])
    time.sleep(2)
    ok = False
    try:
        shell(f"am start -n {PKG}/.DemoActivity")
        time.sleep(5)
        for attempt in range(3):
            img = shot("shot0.png")
            W, H = img.size
            length, top, bottom = find_keyboard(img)
            log("attempt", attempt, "screen", W, H, "keyboard run", length, top, bottom)
            log("IME in use:", shell("dumpsys input_method | grep -E 'mCurMethodId|mCurId' | head -3"))
            if length >= H * 0.15:
                break
            time.sleep(3)
        else:
            log("keyboard not detected")
            return
        kbh = length
        time.sleep(1.5)

        def t(label):
            x, y = pos(label, top, kbh, W)
            tap(x, y)

        t("LANG"); time.sleep(1.2)
        shot("shot1.png")
        for ch in TEXT:
            t("SPACE" if ch == " " else ch)
            time.sleep(0.5)
        time.sleep(2)
        shot("shot2.png")
        for _ in range(4):          # cycle all themes, back to Neon
            t("THEME"); time.sleep(1.1)
        t("EMOJI"); time.sleep(1.2)
        shot("shot3.png")
        x = W * 4.5 / 8
        y = top + kbh / 4.9 * 1.4
        tap(x, y); time.sleep(1.2)  # fire emoji
        t("EMOJI"); time.sleep(2.5)
        shot("shot4.png")
        ok = True
    finally:
        shell("kill -2 $(pidof screenrecord)")
        try:
            rec.wait(timeout=25)
        except Exception:
            pass
        time.sleep(2)
        subprocess.run(["adb", "pull", "/sdcard/demo.mp4", "demo_raw.mp4"])
    sys.exit(0 if ok else 1)

main()
