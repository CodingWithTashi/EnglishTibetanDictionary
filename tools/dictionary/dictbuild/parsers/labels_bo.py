"""Leading `(label)` whitelist of the Tibetan → Tibetan table (plan §5.3 "tbtb").

The source writes grammatical classes and usage labels as Tibetan parentheticals at the start of a
definition, a numbered sense or a sub-sense: `(བྱེད་འབྲེལ་ལས་ཚིག)` (transitive verb), `(མངོན་བརྗོད།)`
(poetic synonym). `LABELS_BO` maps every spelling seen in the source, typos included, to a
part-of-speech code or a canonical label key; spellings are compared without tsheg, shad or spaces.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

POS = "pos"
LABEL = "label"


@dataclass(frozen=True, slots=True)
class BoLabel:
    kind: str  # POS → `senses.part_of_speech`; LABEL → `senses.label`
    key: str


_VT = (BoLabel(POS, "vt"),)
_VI = (BoLabel(POS, "vi"),)
_AUX = (BoLabel(POS, "aux"),)
_CONJ = (BoLabel(POS, "conj"),)
_ADV = (BoLabel(POS, "adv"),)
_INTERJ = (BoLabel(POS, "interj"),)
_ARCHAIC = (BoLabel(LABEL, "archaic"),)
_DIALECT = (BoLabel(LABEL, "dialect"),)

# Rule "leading (label) from the whitelist with aliases": spelling as written → labels.
_WHITELIST: dict[str, tuple[BoLabel, ...]] = {
    # Transitive verb, its tense/mood variants and the source's misspellings.
    "བྱེད་འབྲེལ་ལས་ཚིག": _VT,
    "བྱེད་འབྲེལ་ལས་ཚིག་ད་ལྟ་བ": _VT,
    "བྱེད་འབྲེལ་ལས་ཚིག་འདས་པ": _VT,
    "བྱེད་འབྲེལ་སྐུལ་ཚིག": _VT,
    "བྱེད་འབྲེལ་ཚིག": _VT,
    "བྱེད་འབྲེལ་ལས་ཆིག": _VT,
    "བྱེས་འབྲེལ་ལས་ཚིག": _VT,
    "བྲེད་འབྲེལ་ལས་ཚིག": _VT,
    "དབྱེ་འབྲེལ་ལས་ཚིག": _VT,
    # Intransitive verb.
    "བྱེད་མེད་ལས་ཚིག": _VI,
    "བྱེད་མེད་ལས་ཚིག་འདས་པ": _VI,
    "བྱེད་མེད་སྐུལ་ཚིག": _VI,
    "བྱེད་མེད་ལས་ཆིག": _VI,
    "བྱེད་མེད་དལ་ཚིག": _VI,
    # Other word classes.
    "ཚབ་ཚིག": (BoLabel(POS, "pron"),),
    "སྦྲེལ་ཚིག": _CONJ,
    "སྦྱོར་ཚིག": _CONJ,
    "རྒྱན་ཚིག": (BoLabel(POS, "adj"),),
    "བསྣན་ཚིག": _ADV,
    "བསྣ་ཚིག": _ADV,
    "གྲངས་ཚིག": (BoLabel(POS, "num"),),
    "གྲོགས་ཚིག": _AUX,
    "ལས་ཚིག་རྗེས་མཐུན་པ": _AUX,
    "ལས་ཚིག་རྒྱས་མཐུན་པ": _AUX,
    "ཚིག་ཕྲད": (BoLabel(POS, "part"),),
    "འབོད་ཚིག": _INTERJ,
    "འབོད་སྒྲ": _INTERJ,
    # Usage labels.
    "མངོན་བརྗོད": (BoLabel(LABEL, "poetic"),),
    "བརྡ་རྙིང": _ARCHAIC,
    "ཡུལ་སྐད": _DIALECT,
    "ཡུལ་སྐལ": _DIALECT,
    "ཡུལ་སྐད་དམ་བརྡ་རྙིང": _DIALECT + _ARCHAIC,
    "ལེགས་སྦྱར": (BoLabel(LABEL, "sanskrit"),),
    "རྒྱ་སྐད": (BoLabel(LABEL, "chinese"),),
    "སོག་སྐད": (BoLabel(LABEL, "mongolian"),),
}

# Spellings are compared without tsheg, shad marks or spaces: "བརྡ་རྙིང་།", "བརྡ་རྙིང།" and "བརྡརྙིང་།" agree.
_IGNORED_MARKS = re.compile(r"[\u0f0b-\u0f0e\u0f14\s]")


def _key(written: str) -> str:
    return _IGNORED_MARKS.sub("", written)


LABELS_BO: dict[str, tuple[BoLabel, ...]] = {_key(written): labels for written, labels in _WHITELIST.items()}


def lookup_labels(written: str) -> tuple[BoLabel, ...]:
    """Labels of a parenthetical's inner text, or () when it is not a whitelisted label."""
    return LABELS_BO.get(_key(written), ()) if written.strip() else ()


def lookup_label(written: str) -> BoLabel | None:
    """The first label of a parenthetical's inner text, or None when it is not whitelisted."""
    labels = lookup_labels(written)
    return labels[0] if labels else None
