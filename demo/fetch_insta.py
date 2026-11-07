"""Download the Instagram APK/XAPK on the CI host (not inside the emulator).
Tries apkmirror first, then apkpure, then uptodown. Writes ig_file.txt with the
saved file name on success. Log goes to ig_fetch_log.txt."""
import re, sys, zipfile, urllib.request, urllib.error, urllib.parse, http.cookiejar

UA = ("Mozilla/5.0 (Linux; Android 14; Pixel 6) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
LOG = open("ig_fetch_log.txt", "w")
cj = http.cookiejar.CookieJar()
op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))

def log(*a):
    s = " ".join(str(x) for x in a)
    print(s, flush=True); LOG.write(s + "\n"); LOG.flush()

def get(url, referer=None, binary=False, timeout=60):
    h = {"User-Agent": UA, "Accept": "text/html,application/xhtml+xml,*/*;q=0.8",
         "Accept-Language": "en-US,en;q=0.9"}
    if referer:
        h["Referer"] = referer
    r = op.open(urllib.request.Request(url, headers=h), timeout=timeout)
    return r if binary else r.read().decode("utf-8", "replace")

def save(resp, path):
    n = 0
    with open(path, "wb") as f:
        while True:
            b = resp.read(1 << 20)
            if not b:
                break
            f.write(b); n += len(b)
    return n

def finish(path):
    """Validate the download is a real zip-based APK/XAPK and pick the final name."""
    try:
        z = zipfile.ZipFile(path)
        names = z.namelist()
    except Exception as e:
        log("  not a zip:", e); return None
    is_apk = "AndroidManifest.xml" in names and "classes.dex" in names
    final = "ig.apk" if is_apk else "ig.xapk"
    import os
    os.replace(path, final)
    log("  OK ->", final, os.path.getsize(final), "bytes")
    open("ig_file.txt", "w").write(final)
    return final

def apkmirror():
    base = "https://www.apkmirror.com"
    html = get(base + "/apk/instagram/instagram-instagram/")
    rel = re.findall(r'href="(/apk/instagram/instagram-instagram/instagram-instagram-[\d-]+-release/)"', html)
    if not rel:
        raise RuntimeError("no release link (blocked?)")
    log("  release:", rel[0])
    html = get(base + rel[0], referer=base)
    variants = re.findall(r'href="(/apk/instagram/instagram-instagram/[^"]+-android-apk-download/)"', html)
    if not variants:
        raise RuntimeError("no variants")
    pick = variants[0]
    log("  variant:", pick)
    html = get(base + pick, referer=base + rel[0])
    m = re.search(r'href="([^"]*/download/\?key=[^"]+)"', html)
    if not m:
        raise RuntimeError("no download page link")
    dl = base + m.group(1).replace("&amp;", "&")
    html = get(dl, referer=base + pick)
    m = re.search(r'href="([^"]*download\.php\?id=[^"]+)"', html)
    if not m:
        raise RuntimeError("no final link")
    final = base + m.group(1).replace("&amp;", "&")
    n = save(get(final, referer=dl, binary=True, timeout=300), "ig_dl.bin")
    log("  downloaded", n, "bytes")

def apkpure():
    for kind in ("XAPK", "APK"):
        url = f"https://d.apkpure.com/b/{kind}/com.instagram.android?version=latest"
        log("  trying", url)
        try:
            n = save(get(url, referer="https://apkpure.com/", binary=True, timeout=300), "ig_dl.bin")
            log("  downloaded", n, "bytes")
            if finish("ig_dl.bin"):
                return True
        except Exception as e:
            log("  failed:", repr(e)[:200])
    return False

def uptodown():
    page = "https://instagram.en.uptodown.com/android/download"
    html = get(page)
    m = re.search(r'data-url="([^"]+)"', html) or re.search(r'href="(https://dw\.uptodown\.com/[^"]+)"', html)
    if not m:
        raise RuntimeError("no data-url")
    url = m.group(1).replace("&amp;", "&")
    if url.startswith("/"):
        url = "https://instagram.en.uptodown.com" + url
    log("  url:", url[:120])
    n = save(get(url, referer=page, binary=True, timeout=300), "ig_dl.bin")
    log("  downloaded", n, "bytes")

def main():
    for name, fn in (("apkmirror", apkmirror), ("apkpure", apkpure), ("uptodown", uptodown)):
        log("==", name)
        try:
            r = fn()
            if r is True:
                return 0
            if finish("ig_dl.bin"):
                return 0
        except Exception as e:
            log("  failed:", repr(e)[:300])
    log("all sources failed")
    return 1

sys.exit(main())
