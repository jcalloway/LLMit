"""Clawd checking the time: launcher icon PNGs + the in-app vector, from one sprite definition.

Regenerates every Clawd asset in the app. Edit the CLAWD / SCENE grids below and run
`python art/clawd_icon.py` (needs Pillow); it rewrites the mipmap PNGs and ic_lobster.xml
in place. Hand-authored on an explicit cell grid rather than generated as an image, so the
pixels land exactly on the grid and stay crisp at every launcher size.
"""
from PIL import Image
import os

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(PROJECT, "app", "src", "main", "res")

BG      = (43, 39, 36)       # #2B2724
CLAWD_C = (206, 122, 92)     # #CE7A5C
EYE     = (20, 18, 17)
CREAM   = (250, 249, 245)

# 8-wide body. Legs read leg-gap-leg-GAP-leg-gap-leg: the middle gap is double width,
# splitting them into a left and a right pair.
CLAWD = [
    "..########..",
    "..#o####o#..",
    "############",
    "############",
    "..########..",
    "..########..",
    "..#.#..#.#..",
    "..#.#..#.#..",
]

SCENE = [
    ".........."  + "·ccc·",
    ".........."  + "ccdcc",
    ".........."  + "ccddc",
    ".........."  + "ccccc",
    ".........."  + "·ccc·",
    "..########.."  + "...",
    "..#o####o#.."  + "...",
    "############"  + "...",
    "############"  + "...",
    "..########.."  + "...",
    "..########.."  + "...",
    "..#.#..#.#.."  + "...",
    "..#.#..#.#.."  + "...",
]

COLORS = {'#': CLAWD_C, 'o': EYE, 'c': CREAM, 'd': EYE}


def sprite(rows):
    h, w = len(rows), max(len(r) for r in rows)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in COLORS:
                px[x, y] = COLORS[ch] + (255,)
    return img


# --- launcher icon: 54-cell grid (2dp/cell), subject kept inside the 72dp safe zone ---
GRID, SUBJECT, OUT = 54, 34, 1080
sp = sprite(SCENE)
scale = max(1, SUBJECT // max(sp.size))
cells = sp.resize((sp.width * scale, sp.height * scale), Image.NEAREST)
canvas = Image.new("RGBA", (GRID, GRID), BG + (255,))
canvas.alpha_composite(cells, ((GRID - cells.width) // 2, (GRID - cells.height) // 2))
icon = canvas.resize((OUT, OUT), Image.NEAREST)
# Full-size master, kept beside this script for reference rather than dropped in the CWD.
icon.save(os.path.join(os.path.dirname(os.path.abspath(__file__)), "clawd_icon_master.png"))

for name, px_ in {"mdpi":108, "hdpi":162, "xhdpi":216, "xxhdpi":324, "xxxhdpi":432}.items():
    d = os.path.join(RES, f"mipmap-{name}")
    os.makedirs(d, exist_ok=True)
    icon.convert("RGB").resize((px_, px_), Image.NEAREST).save(os.path.join(d, "ic_launcher_art.png"), "PNG")

# --- in-app vector: merged horizontal runs; eyes left unfilled so the notification
#     silhouette (flattened by Android to one colour) still reads as a face ---
CELL, OX, OY = 4, 0, 8           # 12x8 cells -> 48x32, centred in a 48x48 viewport
runs = []
for y, row in enumerate(CLAWD):
    x = 0
    while x < len(row):
        if row[x] == '#':
            s = x
            while x < len(row) and row[x] == '#':
                x += 1
            runs.append((s, y, x - s))
        else:
            x += 1

hexc = "#%02X%02X%02X" % CLAWD_C
paths = "\n".join(
    f'    <path android:fillColor="{hexc}" '
    f'android:pathData="M{OX+sx*CELL},{OY+sy*CELL} h{w*CELL} v{CELL} h-{w*CELL} z" />'
    for sx, sy, w in runs
)
xml = f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="48"
    android:viewportHeight="48">

    <!-- Clawd, the Claude Code pixel mascot. Each path is one merged horizontal run of
         pixels; the eyes are deliberately left unfilled so they stay visible when Android
         flattens this to a single-colour notification silhouette. -->
{paths}
</vector>
'''
with open(os.path.join(RES, "drawable", "ic_lobster.xml"), "w", encoding="utf-8") as f:
    f.write(xml)
print(f"icon + {len(runs)} vector runs written")
