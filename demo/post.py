import glob, json, re, subprocess

segs = sorted(glob.glob("seg_*.mp4"), key=lambda p: int(re.findall(r"\d+", p)[0]))
print("segments:", segs)
if not segs:
    raise SystemExit("no segments")
with open("list.txt", "w") as f:
    for s in segs:
        f.write(f"file '{s}'\n")
subprocess.run(["ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", "list.txt", "-vf", "fps=30,scale=540:-2",
                "-c:v", "libx264", "-crf", "24", "-preset", "veryfast", "-pix_fmt", "yuv420p", "full.mp4"], check=True)
dur = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", "full.mp4"],
                           capture_output=True, text=True).stdout.strip())
try:
    waits = sorted(json.load(open("timeline.json"))["waits"])
except Exception:
    waits = []
parts, pts = [], 0.0
for s, e in waits:
    s, e = max(s, pts), min(e, dur)
    if e - s < 4:
        continue
    if s > pts:
        parts.append((pts, s, 1))
    parts.append((s, e, 8))
    pts = e
if pts < dur:
    parts.append((pts, dur, 1))
print("duration", dur, "parts", parts)
fc = ";".join(f"[0:v]trim=start={a:.2f}:end={b:.2f},setpts=(PTS-STARTPTS)/{f}[v{i}]" for i, (a, b, f) in enumerate(parts))
fc += ";" + "".join(f"[v{i}]" for i in range(len(parts))) + f"concat=n={len(parts)}:v=1:a=0[out]"
subprocess.run(["ffmpeg", "-y", "-i", "full.mp4", "-filter_complex", fc, "-map", "[out]", "-c:v", "libx264", "-crf", "27",
                "-preset", "veryfast", "-pix_fmt", "yuv420p", "-movflags", "+faststart", "demo_termux.mp4"], check=True)
