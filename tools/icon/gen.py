"""Generate the launcher and splash drawables from the design's app-icon geometry.

Usage: python -I gen.py [res-dir] [font-dir] [decimals]
(defaults: app/src/main/res, app/src/main/res/font and 1; see README.md)

Writes drawable/ic_launcher_{background,foreground,monochrome}.xml, mipmap-anydpi-v26/ic_launcher{,_round}.xml,
drawable/ic_splash_tile.xml (84dp tile + shadow in a 136dp frame, also drawn by the installing screen) and
ic_splash_icon.xml (that tile inset to the 288dp splash frame: dp insets in drawable/, fractions in drawable-v26/).

All book geometry lives in the 160-unit tile space of the design (5a). Glyph runs are shaped with HarfBuzz
(uharfbuzz) and outlined with fontTools, then placed exactly as the SVG <text text-anchor="middle"> runs:
"བོད" Jomolhari 30 centred on x=54, baseline 92; "En" Noto Sans 600 26 centred on x=106, baseline 90.
"""
import math
import os
import sys

import uharfbuzz as hb
from fontTools.pens.qu2cuPen import Qu2CuPen
from fontTools.pens.recordingPen import DecomposingRecordingPen, RecordingPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # relpen.py sits next to this script
from relpen import RelativePathPen  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
RES = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "..", "app", "src", "main", "res")
FONTS = sys.argv[2] if len(sys.argv) > 2 else os.path.join(RES, "font")
DEC = int(sys.argv[3]) if len(sys.argv) > 3 else 1


def num(v, dec=DEC):
    s = f"{round(v, dec):.{dec}f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


# ---------------------------------------------------------------- glyph outlines (tile space)

QU2CU_ERR = 0.05  # tile units (= 0.026dp at the 84dp splash size): merge TrueType quad splines into cubics


def glyph_parts(tt, glyph_set, name):
    """One recording per drawn shape: a composite glyph (e.g. the བོ ligature) yields one per component."""
    rec = RecordingPen()
    glyph_set[name].draw(rec)
    parts = []
    if rec.value and all(op == "addComponent" for op, _ in rec.value):
        for _, (comp, transform) in rec.value:
            sub = DecomposingRecordingPen(glyph_set)
            glyph_set[comp].draw(TransformPen(sub, transform))
            parts.append((comp, sub))
    else:
        sub = DecomposingRecordingPen(glyph_set)
        glyph_set[name].draw(sub)
        parts.append((name, sub))
    return parts


def text_paths(font_file, text, size, cx, baseline):
    """Shapes `text` like a browser (HarfBuzz) and returns [(glyph name, pathData, reference pathData)]."""
    tt = TTFont(os.path.join(FONTS, font_file))
    upem = tt["head"].unitsPerEm
    face = hb.Face(hb.Blob.from_file_path(os.path.join(FONTS, font_file)))
    font = hb.Font(face)
    buf = hb.Buffer()
    buf.add_str(text)
    buf.guess_segment_properties()
    hb.shape(font, buf, {"kern": True, "liga": True})
    s = size / upem
    total = sum(p.x_advance for p in buf.glyph_positions)
    x0 = cx - total * s / 2  # text-anchor="middle": centre of the run's advance
    glyph_set = tt.getGlyphSet()
    out = []
    pen_x = 0
    for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
        name = tt.getGlyphName(info.codepoint)
        transform = (s, 0, 0, -s, x0 + (pen_x + pos.x_offset) * s, baseline - pos.y_offset * s)
        for part, rec in glyph_parts(tt, glyph_set, name):
            compact = RelativePathPen(None, DEC)
            rec.replay(TransformPen(Qu2CuPen(compact, QU2CU_ERR), transform))
            ref = SVGPathPen(None, ntos=lambda v: num(v, 4))
            rec.replay(TransformPen(ref, transform))
            out.append((part, compact.commands(), ref.getCommands()))
        pen_x += pos.x_advance
    return out, (x0, x0 + total * s)


BOD, BOD_SPAN = text_paths("jomolhari_regular.ttf", "བོད", 30, 54, 92)
EN, EN_SPAN = text_paths("noto_sans_semibold.ttf", "En", 26, 106, 90)

# ---------------------------------------------------------------- book geometry (tile space, verbatim from 6.5)

BASE = "M26 118Q54 108 80 122Q106 108 134 118L134 126Q106 116 80 130Q54 116 26 126Z"
LEFT = "M30 44Q56 36 80 50L80 122Q56 108 30 116Z"
RIGHT = "M130 44Q104 36 80 50L80 122Q104 108 130 116Z"
GUTTER = "M72 46H88V124H72Z"
TILE = "M40 0H120A40 40 0 0 1 160 40V120A40 40 0 0 1 120 160H40A40 40 0 0 1 0 120V40A40 40 0 0 1 40 0Z"

INK = "#FF8A2E1C"
BASE_COLOR = "#FF3E1109"


def quad_point(p0, p1, p2, t):
    return tuple((1 - t) ** 2 * a + 2 * (1 - t) * t * b + t * t * c for a, b, c in zip(p0, p1, p2))


def split_quad(p0, p1, p2, t):
    """de Casteljau: returns the control points of [0,t] and [t,1]."""
    a = tuple(x + (y - x) * t for x, y in zip(p0, p1))
    b = tuple(x + (y - x) * t for x, y in zip(p1, p2))
    m = tuple(x + (y - x) * t for x, y in zip(a, b))
    return (p0, a, m), (m, b, p2)


def t_at_x(p0, p1, p2, x):
    # x(t) = A t^2 + B t + C on the x components
    A = p0[0] - 2 * p1[0] + p2[0]
    B = 2 * (p1[0] - p0[0])
    C = p0[0] - x
    if abs(A) < 1e-12:
        return -C / B
    d = math.sqrt(B * B - 4 * A * C)
    for t in ((-B + d) / (2 * A), (-B - d) / (2 * A)):
        if -1e-9 <= t <= 1 + 1e-9:
            return t
    raise ValueError("no root")


def mono_pages(gap):
    """Left + right page as one path, the gutter left as a gap of `gap` units centred on the spine."""
    edge = 80 - gap / 2
    top = ((30, 44), (56, 36), (80, 50))
    bottom = ((80, 122), (56, 108), (30, 116))
    t_top = t_at_x(*top, edge)
    (_, c1, m1), _ = split_quad(*top, t_top)
    t_bot = t_at_x(*bottom, edge)
    _, (m2, c2, end2) = split_quad(*bottom, t_bot)

    def page(mirror):
        f = (lambda p: (160 - p[0], p[1])) if mirror else (lambda p: p)
        pts = [f(p) for p in ((30, 44), c1, m1, m2, c2, end2)]
        (sx, sy), (ax, ay), (bx, by), (cx_, cy_), (dx, dy), (ex, ey) = pts
        n = lambda v: num(v, 2)  # noqa: E731
        return f"M{n(sx)} {n(sy)}Q{n(ax)} {n(ay)} {n(bx)} {n(by)}L{n(cx_)} {n(cy_)}Q{n(dx)} {n(dy)} {n(ex)} {n(ey)}Z"

    return page(False) + page(True)


# ---------------------------------------------------------------- XML writers

HEADER = '<?xml version="1.0" encoding="utf-8"?>\n'
GENERATED = "<!-- Generated by tools/icon/gen.py: do not hand-edit (see tools/icon/README.md). -->\n"
GENERATED_OUTLINED = ("<!-- Generated by tools/icon/gen.py (glyphs outlined with fontTools): do not hand-edit\n"
                      "     (see tools/icon/README.md). -->\n")


def gradient(kind, indent, items, **coords):
    pad = " " * indent
    lines = [f'{pad}<aapt:attr name="android:fillColor">', f'{pad}    <gradient', f'{pad}        android:type="{kind}"']
    lines += [f'{pad}        android:{k}="{num(v, 4)}"' for k, v in coords.items()]
    lines[-1] += ">"
    lines += [f'{pad}        <item android:offset="{num(o, 4)}" android:color="{c}" />' for o, c in items]
    lines += [f'{pad}    </gradient>', f'{pad}</aapt:attr>']
    return "\n".join(lines) + "\n"


def path(indent, data, fill=None, alpha=None, grad=None, comment=None):
    pad = " " * indent
    out = f"{pad}<!-- {comment} -->\n" if comment else ""
    attrs = [f'android:pathData="{data}"']
    if fill:
        attrs.append(f'android:fillColor="{fill}"')
    if alpha is not None:
        attrs.append(f'android:fillAlpha="{num(alpha, 4)}"')
    joined = f"\n{pad}    ".join(attrs)
    if grad:
        return out + f"{pad}<path\n{pad}    {joined}>\n{grad}{pad}</path>\n"
    return out + f"{pad}<path\n{pad}    {joined} />\n"


PAGE_L = [(0, "#FFFBF8F3"), (0.85, "#FFF3EDE3"), (1, "#FFD9CDB9")]
PAGE_R = [(0, "#FFD9CDB9"), (0.15, "#FFF3EDE3"), (1, "#FFFBF8F3")]
GUTTER_STOPS = [(0, "#004A1409"), (0.5, "#734A1409"), (1, "#004A1409")]  # 0x73 = 0.45 alpha


def book(indent, glyphs=True):
    """Base, pages, gutter and (optionally) the two glyph runs, in tile space, drawing order of the design."""
    g = indent + 4
    out = path(indent, BASE, fill=BASE_COLOR, alpha=0.8, comment="Cover under the pages")
    out += path(indent, LEFT, grad=gradient("linear", g, PAGE_L, startX=30, startY=0, endX=80, endY=0),
                comment="Left page")
    out += path(indent, RIGHT, grad=gradient("linear", g, PAGE_R, startX=80, startY=0, endX=130, endY=0),
                comment="Right page")
    out += path(indent, GUTTER, grad=gradient("linear", g, GUTTER_STOPS, startX=72, startY=0, endX=88, endY=0),
                comment="Gutter shadow")
    if glyphs:
        out += f"{' ' * indent}<!-- \"བོད\" Jomolhari 30 centred on 54, baseline 92: outlined, one path per glyph -->\n"
        out += "".join(path(indent, d, fill=INK, comment=name) for name, d, _ in BOD)
        out += f"{' ' * indent}<!-- \"En\" Noto Sans SemiBold 26 centred on 106, baseline 90: outlined -->\n"
        out += "".join(path(indent, d, fill=INK, comment=name) for name, d, _ in EN)
    return out


def vector(size_dp, viewport, body, aapt=True, comment=None, ignore=None, outlined=False):
    ns = '\n    xmlns:aapt="http://schemas.android.com/aapt"' if aapt else ""
    ns += '\n    xmlns:tools="http://schemas.android.com/tools"' if ignore else ""
    top = (GENERATED_OUTLINED if outlined else GENERATED) + (f"<!-- {comment} -->\n" if comment else "")
    extra = f'\n    tools:ignore="{ignore}"' if ignore else ""
    return (HEADER + top + f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{ns}\n'
            f'    android:width="{size_dp}dp"\n    android:height="{size_dp}dp"\n'
            f'    android:viewportWidth="{viewport}"\n    android:viewportHeight="{viewport}"{extra}>\n'
            + body + "</vector>\n")


def group(indent, scale, offset, body):
    pad = " " * indent
    return (f'{pad}<!-- 160-unit tile mapped by scale {num(scale, 4)}, offset {num(offset, 4)} -->\n'
            f'{pad}<group\n{pad}    android:scaleX="{num(scale, 4)}"\n{pad}    android:scaleY="{num(scale, 4)}"\n'
            f'{pad}    android:translateX="{num(offset, 4)}"\n{pad}    android:translateY="{num(offset, 4)}">\n'
            + body + f"{pad}</group>\n")


def write(rel, text):
    p = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"{rel}: {len(text.encode())} bytes")


# ---------------------------------------------------------------- launcher layers (108dp canvas)

S, O = 0.45, 18  # tile 160 -> 72dp, centred: the visible area of an adaptive icon

write("drawable/ic_launcher_background.xml", vector(108, 108, path(
    4, "M0 0H108V108H0Z",
    grad=gradient("radial", 8, [(0, "#FFA63D27"), (1, "#FF6A1F12")],
                  centerX=O + 0.25 * 160 * S, centerY=O + 0.15 * 160 * S, gradientRadius=1.10 * 160 * S)),
    comment="App icon tile (6.5): radial gradient cx 25% cy 15% r 110% of the 72dp visible tile"))

write("drawable/ic_launcher_foreground.xml", vector(108, 108, group(4, S, O, book(8)), outlined=True,
                                                    comment="Open bilingual book (6.5 / 5a); inside the 33dp safe radius"))

MONO_GAP = 6
write("drawable/ic_launcher_monochrome.xml", vector(108, 108, group(4, S, O, path(
    8, mono_pages(MONO_GAP), fill="#FFFBF8F3", comment=f"Pages silhouette, gutter as a {MONO_GAP}-unit gap")),
    aapt=False, comment="Themed-icon layer: only the alpha is used"))

ADAPTIVE = (HEADER + '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_background" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
            '</adaptive-icon>\n')
write("mipmap-anydpi-v26/ic_launcher.xml", ADAPTIVE)
write("mipmap-anydpi-v26/ic_launcher_round.xml", ADAPTIVE)

# ---------------------------------------------------------------- splash tile (84dp tile + shadow) and splash icon

SPLASH_FRAME, TILE_DP = 288, 84  # the splash draws its icon in a 288dp frame; the design's tile is 84dp
SS = TILE_DP / 160
SHADOW_DY, SHADOW_SIGMA, SHADOW_ALPHA = 8, 9, 0.2  # drop-shadow(0 8px 18px rgba(74,20,9,.2)); sigma = blur/2
SPREAD = 2 * SHADOW_SIGMA  # the profile is drawn from -2 sigma (under the tile) to +2 sigma (faded out)
# The smallest square centred on the tile that holds the shadow: it reaches SHADOW_DY + SPREAD below the tile.
FRAME = TILE_DP + 2 * (SHADOW_DY + SPREAD)  # 136
SO = (FRAME - TILE_DP) / 2


def phi(z):
    return 0.5 * (1 + math.erf(z / math.sqrt(2)))


def shadow_stops(d_from, d_to, steps=4):
    """Gradient stops for alpha(d) = A * Phi(-d / sigma), d = signed distance outside the shadow shape's edge."""
    out = []
    for i in range(steps + 1):
        d = d_from + (d_to - d_from) * i / steps
        a = 0.0 if abs(d) >= SPREAD - 1e-9 and d > 0 else SHADOW_ALPHA * phi(-d / SHADOW_SIGMA)
        out.append((i / steps, f"#{round(a * 255):02X}4A1409"))
    return out


def shadow_pieces():
    """The blurred rounded rect as four linear-gradient edge strips and four radial-gradient corner sectors.

    Vector drawables have no blur; along each edge the CSS Gaussian shadow is a function of the distance to the
    edge, and around each corner of the distance to the corner's arc centre, so these eight gradient fills
    reproduce it without banding. The shadow's interior (inside -2 sigma) is hidden under the opaque tile.
    """
    x0, y0 = SO, SO + SHADOW_DY
    x1, y1 = x0 + TILE_DP, y0 + TILE_DP
    r = TILE_DP * 40 / 160
    d = SPREAD
    out = []
    edges = [  # rect (l, t, r, b), gradient from outside (start) to inside (end)
        ("top", (x0 + r, y0 - d, x1 - r, y0 + d), dict(startX=0, startY=y0 - d, endX=0, endY=y0 + d)),
        ("right", (x1 - d, y0 + r, x1 + d, y1 - r), dict(startX=x1 + d, startY=0, endX=x1 - d, endY=0)),
        ("bottom", (x0 + r, y1 - d, x1 - r, y1 + d), dict(startX=0, startY=y1 + d, endX=0, endY=y1 - d)),
        ("left", (x0 - d, y0 + r, x0 + d, y1 - r), dict(startX=x0 - d, startY=0, endX=x0 + d, endY=0)),
    ]
    for name, (l, t, rr, b), coords in edges:
        data = f"M{num(l)} {num(t)}H{num(rr)}V{num(b)}H{num(l)}Z"
        out.append(path(4, data, grad=gradient("linear", 8, shadow_stops(d, -d), **coords), comment=f"Shadow {name} edge"))
    r_in, r_out = r - d, r + d
    corners = [  # arc centre, start angle (degrees, y down) of the 90 degree sector
        ("top-left", (x0 + r, y0 + r), 180),
        ("top-right", (x1 - r, y0 + r), 270),
        ("bottom-right", (x1 - r, y1 - r), 0),
        ("bottom-left", (x0 + r, y1 - r), 90),
    ]
    for name, (cx, cy), a0 in corners:
        pt = lambda rad, ang: (cx + rad * math.cos(math.radians(ang)), cy + rad * math.sin(math.radians(ang)))  # noqa: E731
        (ax, ay), (bx, by) = pt(r_out, a0), pt(r_out, a0 + 90)
        (cx2, cy2), (dx, dy) = pt(r_in, a0 + 90), pt(r_in, a0)
        data = (f"M{num(ax)} {num(ay)}A{num(r_out)} {num(r_out)} 0 0 1 {num(bx)} {num(by)}"
                f"L{num(cx2)} {num(cy2)}A{num(r_in)} {num(r_in)} 0 0 0 {num(dx)} {num(dy)}Z")
        stops = [(r_in / r_out + (1 - r_in / r_out) * o, c) for o, c in shadow_stops(-d, d)]
        out.append(path(4, data, grad=gradient("radial", 8, stops, centerX=cx, centerY=cy, gradientRadius=r_out),
                        comment=f"Shadow {name} corner"))
    return out


shadow = ("    <!-- Drop shadow 0 8 18 rgba(74,20,9,.2) of the tile. Vector drawables cannot blur, so the Gaussian\n"
          "         falloff is drawn as gradients: four edge strips and four corner sectors. -->\n"
          + "".join(shadow_pieces()))

tile = path(8, TILE, grad=gradient("radial", 12, [(0, "#FFA63D27"), (1, "#FF6A1F12")],
                                   centerX=0.25 * 160, centerY=0.15 * 160, gradientRadius=1.10 * 160),
            comment="Tile: radial gradient cx 25% cy 15% r 110%")
write("drawable/ic_splash_tile.xml", vector(
    num(FRAME), num(FRAME), shadow + group(4, SS, SO, tile + book(8)), outlined=True,
    comment=f"Splash tile: the app icon at {TILE_DP}dp with its drop shadow, centred in a {num(FRAME)}dp frame (the\n"
            f"     smallest that holds the shadow). ic_splash_icon insets it to the {SPLASH_FRAME}dp splash frame; the\n"
            "     installing screen draws it as is."))

INSET = (SPLASH_FRAME - FRAME) / 2  # 76dp a side


def inset(value, comment):
    return (HEADER + GENERATED + f"<!-- {comment} -->\n"
            '<inset xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:drawable="@drawable/ic_splash_tile"\n'
            f'    android:inset="{value}" />\n')


write("drawable/ic_splash_icon.xml", inset(
    f"{num(INSET)}dp",
    f"Splash icon (API 24-25): ic_splash_tile centred in the {SPLASH_FRAME}dp frame the splash draws its icon in.\n"
    f"     core-splashscreen draws it at exactly {SPLASH_FRAME}dp, so dp insets are exact here; drawable-v26 has the\n"
    "     fraction insets (API 26+) that also hold when the API 31+ splash draws the icon at another size."))
write("drawable-v26/ic_splash_icon.xml", inset(
    f"{num(100 * INSET / SPLASH_FRAME, 4)}%",
    f"Splash icon: ic_splash_tile centred in the {SPLASH_FRAME}dp frame the splash draws its icon in (intrinsic size\n"
    f"     {SPLASH_FRAME}dp). The insets are fractions of the bounds: the API 31+ splash may give the icon bounds of\n"
    "     another size and scale the result, and fixed dp insets would not scale with the tile."))

for name, d, ref in BOD + EN:
    print(f"glyph {name}: {len(d)} chars (reference {len(ref)})")
print("spans", [round(v, 2) for v in BOD_SPAN], [round(v, 2) for v in EN_SPAN])
import json  # noqa: E402
# Exact (unrounded) glyph outlines for render.py's fidelity check; git-ignored.
with open(os.path.join(HERE, "glyph_reference.json"), "w") as f:
    json.dump({name: ref for name, _, ref in BOD + EN}, f)
print("shadow stops", shadow_stops(SPREAD, -SPREAD))
