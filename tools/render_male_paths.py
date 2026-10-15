"""Rasterize muscle paths over the anatomical background for visual identification."""
import base64
import re
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw

SRC = Path(r"D:\TrackMyStuff\male_msucle_map.svg")
OUT = Path(r"D:\TrackMyStuff\tools\male_svg_preview")
SVG = "{http://www.w3.org/2000/svg}"
INK = "{http://www.inkscape.org/namespaces/inkscape}"
TOKEN = re.compile(r"[MmZzLlHhVvCcSsQqTtAa]|[-+]?(?:\d*\.\d+|\d+)(?:[eE][-+]?\d+)?")


def flatten(d: str, steps: int = 12):
    tokens = TOKEN.findall(d)
    i = 0
    cx = cy = sx = sy = 0.0
    prev_ctrl = None
    cmd = None
    contours = []
    current = []

    def num():
        nonlocal i
        value = float(tokens[i])
        i += 1
        return value

    def start(x, y):
        nonlocal current
        if current:
            contours.append(current)
        current = [(x, y)]

    def line(x, y):
        current.append((x, y))

    def cubic(x1, y1, x2, y2, x, y):
        x0, y0 = current[-1]
        for s in range(1, steps + 1):
            t = s / steps
            u = 1 - t
            px = u**3 * x0 + 3 * u**2 * t * x1 + 3 * u * t**2 * x2 + t**3 * x
            py = u**3 * y0 + 3 * u**2 * t * y1 + 3 * u * t**2 * y2 + t**3 * y
            current.append((px, py))

    while i < len(tokens):
        if re.match(r"[A-Za-z]", tokens[i]):
            cmd = tokens[i]
            i += 1
            if cmd.upper() not in {"S", "C"}:
                prev_ctrl = None
        rel = cmd.islower()
        c = cmd.upper()
        if c == "M":
            x, y = num(), num()
            if rel:
                x += cx
                y += cy
            cx, cy = sx, sy = x, y
            start(cx, cy)
            prev_ctrl = None
            # subsequent pairs are implicit lineto
            while i < len(tokens) and not re.match(r"[A-Za-z]", tokens[i]):
                x, y = num(), num()
                if rel:
                    x += cx
                    y += cy
                cx, cy = x, y
                line(cx, cy)
        elif c == "Z":
            if current:
                current.append((sx, sy))
            cx, cy = sx, sy
        elif c == "L":
            x, y = num(), num()
            if rel:
                x += cx
                y += cy
            cx, cy = x, y
            line(cx, cy)
        elif c == "H":
            x = num()
            if rel:
                x += cx
            cx = x
            line(cx, cy)
        elif c == "V":
            y = num()
            if rel:
                y += cy
            cy = y
            line(cx, cy)
        elif c == "C":
            x1, y1 = num(), num()
            x2, y2 = num(), num()
            x, y = num(), num()
            if rel:
                x1 += cx
                y1 += cy
                x2 += cx
                y2 += cy
                x += cx
                y += cy
            cubic(x1, y1, x2, y2, x, y)
            prev_ctrl = (x2, y2)
            cx, cy = x, y
        elif c == "S":
            x2, y2 = num(), num()
            x, y = num(), num()
            if rel:
                x2 += cx
                y2 += cy
                x += cx
                y += cy
            if prev_ctrl is None:
                x1, y1 = cx, cy
            else:
                x1, y1 = 2 * cx - prev_ctrl[0], 2 * cy - prev_ctrl[1]
            cubic(x1, y1, x2, y2, x, y)
            prev_ctrl = (x2, y2)
            cx, cy = x, y
        elif c == "Q":
            x1, y1 = num(), num()
            x, y = num(), num()
            if rel:
                x1 += cx
                y1 += cy
                x += cx
                y += cy
            # quadratic as cubic
            x0, y0 = current[-1]
            c1 = (x0 + 2 / 3 * (x1 - x0), y0 + 2 / 3 * (y1 - y0))
            c2 = (x + 2 / 3 * (x1 - x), y + 2 / 3 * (y1 - y))
            cubic(c1[0], c1[1], c2[0], c2[1], x, y)
            cx, cy = x, y
        else:
            raise SystemExit(f"unsupported {cmd} at {i}")
    if current:
        contours.append(current)
    return contours


def main():
    root = ET.parse(SRC).getroot()
    bg = Image.open(OUT / "background.png").convert("RGBA")
    scale = 0.5
    base = bg.resize((int(bg.width * scale), int(bg.height * scale)), Image.Resampling.LANCZOS)

    colors = {
        "path27": (220, 30, 30, 140),
        "sbd": (30, 90, 220, 110),
        "serratus": (20, 160, 60, 120),
        "obliques_front": (200, 120, 20, 120),
        "upper_back": (140, 40, 180, 100),
        "lower_back": (220, 200, 20, 120),
        "lats": (200, 80, 40, 90),
        "tigh_extensors_front": (200, 140, 40, 110),
        "tigh_extensors_back": (180, 90, 20, 110),
        "tigh_flexors": (40, 160, 80, 110),
        "tigh_adductors_front": (20, 150, 170, 110),
        "tigh_adductors_back": (20, 100, 140, 110),
    }

    overlay = base.copy()
    draw = ImageDraw.Draw(overlay, "RGBA")
    for el in root.findall(f".//{SVG}path"):
        pid = el.attrib["id"]
        color = colors.get(pid, (80, 80, 80, 40))
        for contour in flatten(el.attrib["d"]):
            pts = [(x * scale, y * scale) for x, y in contour]
            if len(pts) >= 3:
                draw.polygon(pts, fill=color, outline=color[:3] + (220,))
    overlay.save(OUT / "colored_overlay.png")

    # isolated crops for ambiguous regions
    crops = {
        "path27": (680, 340, 900, 500),
        "torso_front": (140, 250, 400, 520),
        "thighs_front": (140, 430, 400, 760),
        "thighs_back": (650, 440, 930, 780),
        "back_torso": (650, 90, 950, 520),
    }
    full = Image.new("RGBA", bg.size, (255, 255, 255, 255))
    full.alpha_composite(bg)
    isolated = ImageDraw.Draw(full, "RGBA")
    for el in root.findall(f".//{SVG}path"):
        pid = el.attrib["id"]
        if pid not in {"path27", "lower_back", "lats", "upper_back", "obliques_front", "serratus", "sbd"}:
            continue
        color = colors[pid]
        for contour in flatten(el.attrib["d"]):
            if len(contour) >= 3:
                isolated.polygon(contour, fill=color)
    for name, box in crops.items():
        full.crop(box).resize(
            ((box[2] - box[0]) * 2, (box[3] - box[1]) * 2),
            Image.Resampling.NEAREST,
        ).save(OUT / f"crop_{name}.png")
    print("done", OUT)


if __name__ == "__main__":
    main()
