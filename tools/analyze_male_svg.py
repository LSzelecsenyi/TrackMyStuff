"""One-off inspection of male_msucle_map.svg. Does not modify the source."""
import re
import xml.etree.ElementTree as ET
from collections import Counter

SRC = r"D:\TrackMyStuff\male_msucle_map.svg"
INK = "{http://www.inkscape.org/namespaces/inkscape}"
XLINK = "{http://www.w3.org/1999/xlink}"
SVG = "{http://www.w3.org/2000/svg}"

tree = ET.parse(SRC)
root = tree.getroot()


def local(tag: str) -> str:
    return tag.split("}")[-1]


print("ROOT", local(root.tag))
for key, value in root.attrib.items():
    if key.startswith("xmlns") or len(value) > 180:
        print(f"  attr {key} = <{len(value)} chars>")
    else:
        print(f"  attr {key} = {value}")

ids = []


def walk(el, depth, parent_id):
    tag = local(el.tag)
    eid = el.attrib.get("id")
    if eid:
        ids.append((eid, tag, parent_id))
    transform = el.attrib.get("transform")
    label = el.attrib.get(f"{INK}label")
    groupmode = el.attrib.get(f"{INK}groupmode")
    interesting = tag in {"g", "path", "image", "use", "rect", "svg", "defs", "clipPath", "mask"}
    if interesting or transform or label:
        extra = []
        if label:
            extra.append(f"label={label}")
        if groupmode:
            extra.append(f"groupmode={groupmode}")
        if transform:
            extra.append(f"transform={transform}")
        style = el.attrib.get("style")
        if style:
            extra.append(f"style={style[:160]}")
        if tag == "image":
            href = el.attrib.get("href") or el.attrib.get(f"{XLINK}href") or ""
            extra.append(f"href_len={len(href)} prefix={href[:40]!r}")
            for k in ("x", "y", "width", "height", "preserveAspectRatio"):
                if k in el.attrib:
                    extra.append(f"{k}={el.attrib[k]}")
        if tag == "path":
            d = el.attrib.get("d", "")
            extra.append(f"d_len={len(d)} subpaths={len(re.findall(r'[Mm]', d))}")
            for k, v in el.attrib.items():
                lk = local(k) if k.startswith("{") else k
                if lk in {"d", "id", "style"}:
                    continue
                shown = v if len(v) < 80 else f"<{len(v)} chars>"
                extra.append(f"{lk}={shown}")
        if depth <= 2 or tag in {"path", "image", "g"}:
            print(f"{'  '*depth}{tag} id={eid} parent={parent_id} {' '.join(extra)}")
    for child in list(el):
        walk(child, depth + 1, eid or parent_id)


walk(root, 0, None)

print("\nID COUNTS")
counts = Counter(i for i, _, _ in ids)
dups = [i for i, n in counts.items() if n > 1]
print("total ids", len(ids), "unique", len(counts), "dups", dups)

print("\nPATH IDS IN ORDER")
for eid, tag, parent in ids:
    if tag == "path":
        print(f"  {eid} parent={parent}")
