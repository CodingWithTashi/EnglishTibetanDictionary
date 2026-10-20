"""A pen that writes compact SVG/VectorDrawable pathData with relative commands.

Coordinates are rounded to absolute values first and every relative delta is taken between rounded points, so
rounding never accumulates along a contour.
"""
from fontTools.pens.basePen import BasePen


class RelativePathPen(BasePen):
    def __init__(self, glyph_set, dec):
        super().__init__(glyph_set)
        self.dec = dec
        self.out = []
        self.cur = (0.0, 0.0)  # rounded current point
        self.start = (0.0, 0.0)
        self.last_cmd = None

    def _r(self, p):
        return (round(p[0], self.dec), round(p[1], self.dec))

    def _n(self, v):
        s = f"{v:.{self.dec}f}".rstrip("0").rstrip(".")
        return "0" if s in ("-0", "") else s

    def _emit(self, cmd, nums):
        parts = []
        for v in nums:
            t = self._n(v)
            if parts and not t.startswith("-"):
                parts.append(" ")
            parts.append(t)
        body = "".join(parts)
        if cmd == self.last_cmd and cmd not in ("m", "z"):
            self.out.append(("" if body.startswith("-") else " ") + body)
        else:
            self.out.append(cmd + body)
        self.last_cmd = cmd

    def _rel(self, *pts):
        res = []
        for p in pts:
            q = self._r(p)
            res += [q[0] - self.cur[0], q[1] - self.cur[1]]
        return res

    def _moveTo(self, pt):
        q = self._r(pt)
        self._emit("m" if self.out else "M", [q[0] - self.cur[0], q[1] - self.cur[1]] if self.out else list(q))
        self.cur = self.start = q

    def _lineTo(self, pt):
        q = self._r(pt)
        dx, dy = q[0] - self.cur[0], q[1] - self.cur[1]
        if abs(dy) < 1e-9 and abs(dx) < 1e-9:
            return
        if abs(dy) < 1e-9:
            self._emit("h", [dx])
        elif abs(dx) < 1e-9:
            self._emit("v", [dy])
        else:
            self._emit("l", [dx, dy])
        self.cur = q

    def _qCurveToOne(self, p1, p2):
        self._emit("q", self._rel(p1, p2))
        self.cur = self._r(p2)

    def _curveToOne(self, p1, p2, p3):
        self._emit("c", self._rel(p1, p2, p3))
        self.cur = self._r(p3)

    def _closePath(self):
        self.out.append("z")
        self.last_cmd = "z"
        self.cur = self.start

    def _endPath(self):
        self.cur = self.start

    def commands(self):
        return "".join(self.out)
