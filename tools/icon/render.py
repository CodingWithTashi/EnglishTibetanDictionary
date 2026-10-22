"""Minimal VectorDrawable renderer (skia) for the app icon: groups (scale/translate), paths, solid and
linear/radial gradient fills, fillAlpha. Used to rasterise the legacy mipmaps and preview images straight from the
generated XML, so the PNGs and the vectors share one geometry. Run gen.py first: the glyph check reads its
glyph_reference.json.

Usage: python -I render.py [res-dir] [preview-dir]
(defaults: app/src/main/res and tools/icon/previews, which is git-ignored; see README.md)
"""
import json
import math
import os
import re
import sys
import xml.etree.ElementTree as ET

import numpy as np
import skia
from PIL import Image

A = "{http://schemas.android.com/apk/res/android}"
TOKEN = re.compile(r"[MmLlHhVvQqCcAaZz]|-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
ARGS = {"m": 2, "l": 2, "h": 1, "v": 1, "q": 4, "c": 6, "a": 7, "z": 0}


def parse_path(d):
    toks = TOKEN.findall(d)
    p = skia.Path()
    i, cmd = 0, None
    cx = cy = sx = sy = 0.0
    while i < len(toks):
        t = toks[i]
        if t.isalpha():
            cmd = t
            i += 1
            if cmd in "zZ":
                p.close()
                cx, cy = sx, sy
                continue
        n = ARGS[cmd.lower()]
        a = [float(x) for x in toks[i:i + n]]
        i += n
        rel = cmd.islower()
        c = cmd.lower()
        ox, oy = (cx, cy) if rel else (0.0, 0.0)
        if c == "m":
            cx, cy = ox + a[0], oy + a[1]
            p.moveTo(cx, cy)
            sx, sy = cx, cy
            cmd = "l" if rel else "L"  # implicit repeats after a moveto are linetos
        elif c == "l":
            cx, cy = ox + a[0], oy + a[1]
            p.lineTo(cx, cy)
        elif c == "h":
            cx = (cx if rel else 0) + a[0]
            p.lineTo(cx, cy)
        elif c == "v":
            cy = (cy if rel else 0) + a[0]
            p.lineTo(cx, cy)
        elif c == "q":
            p.quadTo(ox + a[0], oy + a[1], ox + a[2], oy + a[3])
            cx, cy = ox + a[2], oy + a[3]
        elif c == "c":
            p.cubicTo(ox + a[0], oy + a[1], ox + a[2], oy + a[3], ox + a[4], oy + a[5])
            cx, cy = ox + a[4], oy + a[5]
        elif c == "a":
            ex, ey = ox + a[5], oy + a[6]
            p.arcTo(a[0], a[1], a[2],
                    skia.Path.ArcSize.kLarge_ArcSize if a[3] else skia.Path.ArcSize.kSmall_ArcSize,
                    skia.PathDirection.kCW if a[4] else skia.PathDirection.kCCW, ex, ey)
            cx, cy = ex, ey
    return p


def color(s):
    s = s.lstrip("#")
    if len(s) == 6:
        s = "FF" + s
    return int(s, 16)


def shader_for(path_el):
    for attr in path_el:
        if attr.tag == "{http://schemas.android.com/aapt}attr" or attr.tag == "aapt:attr" or attr.tag.endswith("attr"):
            g = attr.find("gradient")
            kind = g.get(A + "type")
            stops = [(float(it.get(A + "offset")), color(it.get(A + "color"))) for it in g.findall("item")]
            pos = [s[0] for s in stops]
            cols = [s[1] for s in stops]
            if kind == "linear":
                pts = [(float(g.get(A + "startX")), float(g.get(A + "startY"))),
                       (float(g.get(A + "endX")), float(g.get(A + "endY")))]
                return skia.GradientShader.MakeLinear(pts, cols, pos)
            if kind == "radial":
                return skia.GradientShader.MakeRadial((float(g.get(A + "centerX")), float(g.get(A + "centerY"))),
                                                      float(g.get(A + "gradientRadius")), cols, pos)
    return None


def draw_children(canvas, el, tint=None):
    for child in el:
        if child.tag == "group":
            canvas.save()
            canvas.translate(float(child.get(A + "translateX", 0)), float(child.get(A + "translateY", 0)))
            canvas.scale(float(child.get(A + "scaleX", 1)), float(child.get(A + "scaleY", 1)))
            draw_children(canvas, child, tint)
            canvas.restore()
        elif child.tag == "path":
            paint = skia.Paint(AntiAlias=True)
            sh = shader_for(child)
            if sh is not None:
                paint.setShader(sh)
                paint.setColor(0xFF000000)
            else:
                paint.setColor(color(child.get(A + "fillColor")))
            if tint is not None:  # themed icons only use the layer's alpha (monochrome has solid fills)
                paint.setColor((paint.getColor() & 0xFF000000) | (tint & 0x00FFFFFF))
            alpha = float(child.get(A + "fillAlpha", 1))
            paint.setAlphaf(paint.getAlphaf() * alpha)
            canvas.drawPath(parse_path(child.get(A + "pathData")), paint)


def draw_vector(canvas, xml_file, size_px, tint=None):
    root = ET.parse(xml_file).getroot()
    vp = float(root.get(A + "viewportWidth"))
    canvas.save()
    canvas.scale(size_px / vp, size_px / vp)
    draw_children(canvas, root, tint)
    canvas.restore()


def surface(w, h, bg=0x00000000):
    s = skia.Surface(w, h)
    s.getCanvas().clear(bg)
    return s


def save(s, file, optimize=True):
    img = Image.fromarray(s.makeImageSnapshot().toarray(colorType=skia.ColorType.kRGBA_8888_ColorType), "RGBA")
    img.save(file, optimize=optimize)


DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


# Launcher variants: (mipmap qualifier, folder of the foreground layer). Bhutan SIMs get the "རྫོང" foreground.
VARIANTS = (("", "drawable"), ("-mcc402", "drawable-mcc402"))


def legacy_icon(res, size_px, tile_px, round_, foreground_dir="drawable"):
    """The 72dp visible tile of the adaptive icon (background + foreground), masked like a legacy icon."""
    s = surface(size_px, size_px)
    c = s.getCanvas()
    off = (size_px - tile_px) / 2
    rect = skia.Rect.MakeXYWH(off, off, tile_px, tile_px)
    if round_:
        c.clipRRect(skia.RRect.MakeOval(rect), doAntiAlias=True)
    else:
        c.clipRRect(skia.RRect.MakeRectXY(rect, tile_px / 4, tile_px / 4), doAntiAlias=True)  # rx 40 of 160
    k = tile_px / 72
    c.translate(off - 18 * k, off - 18 * k)
    draw_vector(c, os.path.join(res, "drawable", "ic_launcher_background.xml"), 108 * k)
    draw_vector(c, os.path.join(res, foreground_dir, "ic_launcher_foreground.xml"), 108 * k)
    return s


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    res = sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "..", "..", "app", "src", "main", "res")
    prev = sys.argv[2] if len(sys.argv) > 2 else os.path.join(here, "previews")
    os.makedirs(prev, exist_ok=True)
    # Legacy mipmaps (API 24-25): rounded tile 40dp / circle 44dp in the 48dp launcher square.
    for qualifier, foreground_dir in VARIANTS:
        for name, d in DENSITIES.items():
            folder = os.path.join(res, f"mipmap{qualifier}-{name}")
            os.makedirs(folder, exist_ok=True)
            save(legacy_icon(res, round(48 * d), round(40 * d), False, foreground_dir),
                 os.path.join(folder, "ic_launcher.png"))
            save(legacy_icon(res, round(48 * d), round(44 * d), True, foreground_dir),
                 os.path.join(folder, "ic_launcher_round.png"))

    # Previews (not shipped; the default preview directory is git-ignored).
    big = 108 * 8
    for qualifier, foreground_dir in VARIANTS:
        for shape in ("circle", "squircle"):
            s = surface(big, big, 0xFFEFEBE4)
            c = s.getCanvas()
            r = skia.Rect.MakeXYWH(18 * 8, 18 * 8, 72 * 8, 72 * 8)
            c.clipRRect(skia.RRect.MakeOval(r) if shape == "circle"
                        else skia.RRect.MakeRectXY(r, 72 * 8 * .3, 72 * 8 * .3), doAntiAlias=True)
            draw_vector(c, os.path.join(res, "drawable", "ic_launcher_background.xml"), big)
            draw_vector(c, os.path.join(res, foreground_dir, "ic_launcher_foreground.xml"), big)
            save(s, os.path.join(prev, f"adaptive{qualifier}-{shape}.png"))
    # Full 108 canvas with the safe-zone circle (33dp) and visible tile outlined.
    s = surface(big, big)
    c = s.getCanvas()
    for layer in ("ic_launcher_background", "ic_launcher_foreground"):
        draw_vector(c, os.path.join(res, "drawable", layer + ".xml"), big)
    p = skia.Paint(AntiAlias=True, Style=skia.Paint.kStroke_Style, StrokeWidth=3, Color=0xFF00FFFF)
    c.drawCircle(54 * 8, 54 * 8, 33 * 8, p)
    c.drawRect(skia.Rect.MakeXYWH(18 * 8, 18 * 8, 72 * 8, 72 * 8), p)
    save(s, os.path.join(prev, "adaptive-canvas-safezone.png"))
    # Monochrome, tinted the way a themed launcher does (preview colours only).
    for bg, fg, tag in ((0xFF2B2930, 0xFFE6E0E9, "dark"), (0xFFE8DEF8, 0xFF4F378B, "light")):
        s = surface(big, big, 0xFFEFEBE4)
        c = s.getCanvas()
        r = skia.Rect.MakeXYWH(18 * 8, 18 * 8, 72 * 8, 72 * 8)
        c.clipRRect(skia.RRect.MakeOval(r), doAntiAlias=True)
        c.drawColor(bg)
        draw_vector(c, os.path.join(res, "drawable", "ic_launcher_monochrome.xml"), big, tint=fg)
        save(s, os.path.join(prev, f"monochrome-{tag}.png"))
    # Splash at Pixel 8 density (2.625) on both window backgrounds: ic_splash_icon is ic_splash_tile inset to the
    # 288dp frame, drawn the way InsetDrawable does it (inset = (int)(bounds * fraction)).
    dens = 2.625
    px = round(288 * dens)
    tile = os.path.join(res, "drawable", "ic_splash_tile.xml")
    tile_root = ET.parse(tile).getroot()
    tile_dp = float(tile_root.get(A + "viewportWidth"))
    inset = int(px * (288 - tile_dp) / 2 / 288)
    for bg, tag in ((0xFFFBF8F3, "light"), (0xFF1B1715, "dark")):
        s = surface(px, px, bg)
        c = s.getCanvas()
        c.translate(inset, inset)
        draw_vector(c, tile, px - 2 * inset)
        save(s, os.path.join(prev, f"splash-{tag}.png"))
        # Reference: the CSS drop-shadow as a true Gaussian (sigma = 9dp) under the same tile.
        s = surface(px, px, bg)
        c = s.getCanvas()
        sp = skia.Paint(AntiAlias=True, Color=0x334A1409,
                        MaskFilter=skia.MaskFilter.MakeBlur(skia.kNormal_BlurStyle, 9 * dens))
        c.drawRRect(skia.RRect.MakeRectXY(skia.Rect.MakeXYWH(102 * dens, 110 * dens, 84 * dens, 84 * dens),
                                          21 * dens, 21 * dens), sp)
        c.save()
        c.translate((288 - tile_dp) / 2 * dens, (288 - tile_dp) / 2 * dens)
        c.scale(dens, dens)
        holder = ET.Element("holder")
        holder.append(tile_root.find("group"))
        draw_children(c, holder)
        c.restore()
        save(s, os.path.join(prev, f"splash-{tag}-gaussian-reference.png"))
        tile_root = ET.parse(tile).getroot()
    # The tiles as the installing screen draws them (84dp slot, 136dp drawable centred on it) at the same density.
    for tile_name in ("ic_splash_tile", "ic_splash_tile_dz"):
        suffix = tile_name.removeprefix("ic_splash_tile").replace("_", "-")
        for bg, tag in ((0xFFFBF8F3, "light"), (0xFF1B1715, "dark")):
            size = round(tile_dp * dens)
            s = surface(size, size, bg)
            draw_vector(s.getCanvas(), os.path.join(res, "drawable", tile_name + ".xml"), size)
            save(s, os.path.join(prev, f"installing-tile{suffix}-{tag}.png"))
    # Glyph fidelity: compact (qu2cu, 1 decimal, relative) vs exact fontTools outlines, at 16x the tile.
    references = json.load(open(os.path.join(here, "glyph_reference.json")))
    scale = 16
    report = {}
    for foreground_dir, ref in references.items():
        root = ET.parse(os.path.join(res, foreground_dir, "ic_launcher_foreground.xml")).getroot()
        paths = [el for el in root.find("group") if el.tag == "path"]
        compact = [el.get(A + "pathData") for el in paths[-len(ref):]]
        report[foreground_dir] = glyph_report(ref, compact, scale)
    print(json.dumps(report, indent=1))


def glyph_report(ref, compact, scale):
    """Pixels where each compact glyph path differs from its exact outline, drawn at `scale` times the tile."""
    report = {}
    for (name, rd), cd in zip(ref, compact):
        imgs = []
        for d in (rd, cd):
            s = surface(160 * scale, 160 * scale, 0xFFFFFFFF)
            c = s.getCanvas()
            c.scale(scale, scale)
            c.drawPath(parse_path(d), skia.Paint(AntiAlias=True, Color=0xFF000000))
            imgs.append(s.makeImageSnapshot().toarray()[..., 0].astype(int))
        diff = np.abs(imgs[0] - imgs[1])
        ink = (imgs[0] < 128).sum()
        report[name] = {"ink_px": int(ink), "px_differing_gt_half": int((diff > 128).sum()),
                        "max_diff": int(diff.max())}
    return report


if __name__ == "__main__":
    main()
