"""Dictionary packs: everything that differs between the databases the app ships.

Each pack is one module defining its `Pack` (`monlam.MONLAM` is the Tibetan pack, `bo.db` from
`MLDic.ml`; `ddc.DDC` the Dzongkha one, `dz.db` from the DDC StarDict files). `PACKS` maps the
`--pack` names to them; a new pack adds its module and one entry here.
"""

from __future__ import annotations

from dictbuild.packs.base import Expected, Pack
from dictbuild.packs.ddc import DDC
from dictbuild.packs.monlam import MONLAM

PACKS: dict[str, Pack] = {pack.id: pack for pack in (MONLAM, DDC)}

__all__ = ["DDC", "MONLAM", "PACKS", "Expected", "Pack"]
