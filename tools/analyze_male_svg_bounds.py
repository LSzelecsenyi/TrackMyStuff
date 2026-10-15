"""Compute path bounds and a low-res overlay preview. Does not modify the source SVG."""
import base64
import re
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw

SRC = Path(r"D:\TrackMyStuff\male_msucle_map.svg")
OUT = Path(r"D:\TrackMyStuff\tools\male_svg_preview")
OUT.mkdir(parents=True, exist_ok=True)

INK = "{http://www.inkscape.org/namespaces/inkscape}"
SVG = "{http://www.w3.org/2000/svg}"
TOKEN = re.compile(r"[MmZzLlHhVvCcSsQqTtAa]|[-+]?(?:\d*\.\d+|\d+)(?:[eE][-+]?\d+)?")


def local(tag: str) -> str:
    return tag.split("}")[-1]


def tokenize(d: str):
    return TOKEN.findall(d)


def bounds_of(d: str):
    tokens = tokenize(d)
    i = 0
    cx = cy = 0.0
    sx = sy = 0.0
    cmd = None
    xs = []
    ys = []

    def num():
        nonlocal i
        value = float(tokens[i])
        i += 1
        return value

    def add(x, y):
        xs.append(x)
        ys.append(y)

    while i < len(tokens):
        if re.match(r"[A-Za-z]", tokens[i]):
            cmd = tokens[i]
            i += 1
        if cmd is None:
            break
        rel = cmd.islower()
        c = cmd.upper()
        if c == "M":
            x, y = num(), num()
            if rel:
                x += cx
                y += cy
            cx, cy = x, y
            sx, sy = x, y
            add(cx, cy)
            cmd = "l" if rel else "L"
        elif c == "Z":
            cx, cy = sx, sy
        elif c == "L":
            x, y = num(), num()
            if rel:
                x += cx
                y += cy
            cx, cy = x, y
            add(cx, cy)
        elif c == "H":
            x = num()
            if rel:
                x += cx
            cx = x
            add(cx, cy)
        elif c == "V":
            y = num()
            if rel:
                y += cy
            cy = y
            add(cx, cy)
        elif c == "C":
            pts = []
            for _ in range(3):
                x, y = num(), num()
                if rel:
                    x += cx
                    y += cy
                pts.append((x, y))
            for x, y in pts:
                add(x, y)
            cx, cy = pts[-1]
        elif c == "S":
            pts = []
            for _ in range(2):
                x, y = num(), num()
                if rel:
                    x += cx
                    y += cy
                pts.append((x, y))
            for x, y in pts:
                add(x, y)
            cx, cy = pts[-1]
        elif c == "Q":
            pts = []
            for _ in range(2):
                x, y = num(), num()
                if rel:
                    x += cx
                    y += cy
                pts.append((x, y))
            for x, y in pts:
                add(x, y)
            cx, cy = pts[-1]
        elif c == "T":
            x, y = num(), num()
            if rel:
                x += cx
                y += cy
            cx, cy = x, y
            add(cx, cy)
        elif c == "A":
            num(); num(); num(); num(); num()
            x, y = num(), num()
            if rel:
                x += cx
                y += cy
            cx, cy = x, y
            add(cx, cy)
        else:
            raise SystemExit(f"unknown cmd {cmd}")
    return min(xs), min(ys), max(xs), max(ys)


root = ET.parse(SRC).getroot()
image_el = root.find(f".//{SVG}image")
href = image_el.attrib.get("href") or image_el.attrib["{http://www.w3.org/1999/xlink}href"]
png = base64.b64decode(href.split(",", 1)[1])
(OUT / "background.png").write_bytes(png)
bg = Image.open(OUT / "background.png").convert("RGBA")
print("image", bg.size, bg.mode)

scale = 0.45
preview = bg.resize((int(bg.width * scale), int(bg.height * scale)), Image.Resampling.LANCZOS)
draw = ImageDraw.Draw(preview, "RGBA")

print(f"{'id':24} {'label':22} {'bbox':40} {'cx':8} {'cy':8} side")
for el in root.findall(f".//{SVG}path"):
    pid = el.attrib["id"]
    label = el.attrib.get(f"{INK}label", "")
    d = el.attrib["d"]
    x0, y0, x1, y1 = bounds_of(d)
    cx = (x0 + x1) / 2
    cy = (y0 + y1) / 2
    side = "LEFT" if cx < 526 else "RIGHT"
    print(f"{pid:24} {label:22} {x0:7.1f},{y0:7.1f} {x1:7.1f},{y1:7.1f} {cx:8.1f} {cy:8.1f} {side} subs={len(re.findall('[Mm]', d))}")
    color = (255, 40, 40, 90) if pid == "path27" else (40, 90, 255, 50)
    # rough: draw bbox
    draw.rectangle(
        [x0 * scale, y0 * scale, x1 * scale, y1 * scale],
        outline=(255, 0, 0, 180) if pid in {"path27", "sbd"} else (0, 180, 80, 160),
        width=2,
    )

preview.save(OUT / "bbox_overlay.png")
bg.resize((526, 524), Image.Resampling.LANCZOS).save(OUT / "background_small.png")
print("wrote", OUT)
