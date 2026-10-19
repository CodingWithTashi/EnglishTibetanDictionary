"""Part-of-speech headers and domain/register labels of the English → Tibetan table (plan §5.3 "entotb").

Two whitelists: `POS_CODES` maps a header token as written ("v.t.", "n", "combining form") to the
`senses.part_of_speech` code, and `LABEL_KEYS` maps a label abbreviation (lower-cased, without its
dot) to the canonical key stored in `senses.label`. Anything not listed is ordinary text.
"""

from __future__ import annotations

import re

from dictbuild.parsers.common import starts_with_particle

# Rule "entotb: POS header tokens map n./n→n, adj.→adj, … v.t./v.t/vt.→vt, …, combining form→comb".
POS_CODES: dict[str, str] = {
    "n.": "n",
    "n": "n",
    "adj.": "adj",
    "adj": "adj",
    "adv.": "adv",
    "adv": "adv",
    "v.": "v",
    "v.t.": "vt",
    "v.t": "vt",
    "vt.": "vt",
    "vt": "vt",
    "v.i.": "vi",
    "v.i": "vi",
    "vi.": "vi",
    "vi": "vi",
    "interj.": "interj",
    "int.": "interj",
    "prep.": "prep",
    "prep": "prep",
    "pron.": "pron",
    "conj.": "conj",
    "pl.": "pl",
    "prefix": "prefix",
    "prefix.": "prefix",
    "suffix": "suffix",
    "suffix.": "suffix",
    "abbr.": "abbr",
    "combining form": "comb",
    "pred.": "pred",
    "aux.": "aux",
    "num.": "num",
}
VERB_CODES = frozenset({"v", "vt", "vi"})

# Tokens that are headers wherever a block may start. Longer spellings come first ("v.t." before "v.").
_STRONG_POS = (
    r"(?:v\.t\.|v\.i\.|vt\.|vi\.|v\.|n\.|adj\.|adv\.|interj\.|int\.|prep\.|pron\.|conj\.|pl\.|abbr\.|pred\.|aux\.|num\.)"
)
# Undotted spellings ("n 1.", "v.t 1.", "prefix") are headers only at the start of a definition.
_WEAK_POS = r"(?:v\.t|v\.i|vt|vi|adj|adv|prep|n|prefix\.?|suffix\.?|combining\s+form)"
# Rule "`adj. & n.` → `adj,n`": tokens chain with optional spaces and "&" ("n. pl.", "n.pl.", "adj. n.").
_HEADER_END = r"(?=\s|$|[\u0f00-\u0fff(*\[0-9])"
START_HEADER = re.compile(
    rf"^(?P<header>(?:{_STRONG_POS}|{_WEAK_POS})(?:\s*&?\s*(?:{_STRONG_POS}|{_WEAK_POS}))*){_HEADER_END}"
)
MID_HEADER = re.compile(rf"(?<=\s)(?P<header>{_STRONG_POS}(?:\s*&?\s*{_STRONG_POS})*){_HEADER_END}")
# Right after a sense number a header names that sense's part of speech only ("2. pl. …", "3. v.i. …").
SENSE_HEADER = re.compile(rf"^(?P<header>{_STRONG_POS})(?=\s|[\u0f00-\u0fff])\s*")
# One header token, longest spelling first, so "v.t" is never read as "v." followed by "t".
_HEADER_TOKEN = re.compile(
    "|".join(re.escape(token).replace(r"\ ", r"\s+") for token in sorted(POS_CODES, key=len, reverse=True))
)

# Rule "domain/register labels from a whitelist (adm med bot phys astrol pol law com chem edu econ …
# sl colloq fig archaic obs …)": abbreviation → canonical key. Variant spellings share one key.
LABEL_KEYS: dict[str, str] = {
    **{
        key: key
        for key in (
            "acc", "adm", "aeron", "agric", "anat", "anthrop", "archaic", "archit", "arith", "astrol", "astron",
            "aviat", "bacteriol", "biochem", "biol", "bot", "brit", "chem", "cine", "colloq", "com", "comput",
            "cricket", "dent", "derm", "dial", "eccl", "ecol", "econ", "edu", "electr", "engin", "entomol",
            "environ", "fig", "geog", "geol", "geom", "genet", "gk", "golf", "gram", "hist", "joc", "jrn",
            "lat", "law", "lib", "ling", "lit", "logic", "math", "mech", "med", "metall", "meteorol", "mil",
            "mining", "mus", "myth", "naut", "numis", "obs", "optics", "ornithol", "path", "phil", "phonet",
            "photo", "phys", "physiol", "poet", "pol", "printing", "psych", "psychoanal", "pub", "relig",
            "scot", "skt", "sl", "soc", "sports", "stat", "surg", "technol", "telecom", "theatr", "theol",
            "trans", "us", "zool",
        )
    },  # fmt: skip
    "alg": "math",
    "anthropol": "anthrop",
    "archaeol": "archaeol",
    "archeol": "archaeol",
    "bibl": "bibl",
    "billiard": "billiards",
    "boxing": "boxing",
    "falconary": "falconry",
    "fem": "fem",
    "journalism": "jrn",
    "metereol": "meteorol",
    "scottish": "scot",
    "tennis": "tennis",
    "theatre": "theatr",
    "tv": "tv",
    "arch": "archaic",
    "astro": "astron",
    "compt": "comput",
    "comptr": "comput",
    "comptr.sc": "comput",
    "eco": "econ",
    "electronics": "electr",
    "engn": "engin",
    "entimol": "entomol",
    "fr": "fr",
    "gene": "genet",
    "her": "herald",
    "his": "hist",
    "mine": "mining",
    "opt": "optics",
    "pathol": "path",
    "phot": "photo",
    "physio": "physiol",
    "psycho": "psych",
    "psychiatry": "psych",
    "psychol": "psych",
    "rel": "relig",
    "scotish": "scot",
    "theater": "theatr",
}
# Single capital letters are labels only in these spellings ("L." = Latin, "Gk." = Greek, "Fr." = French).
_CASED_LABEL_KEYS: dict[str, str] = {"L": "lat", "Gk": "gk", "Fr": "fr"}

# A label as written: letters with inner dots or commas ("adm,law", "comptr.sc"), an optional final
# dot or comma, then a space, Tibetan text, a bracket, an asterisk or a digit.
_LABEL_TOKEN = re.compile(r"(?P<token>[A-Za-z]+(?:[.,][A-Za-z]+)*[.,]?)(?=\s|$|[\u0f00-\u0fff(*0-9])")
_LABEL_PART_SEPARATOR = re.compile(r"[.,]")


def pos_codes(header: str) -> tuple[str, ...]:
    """`"adj. & n."` → `("adj", "n")`; codes keep their order, duplicates removed."""
    codes: list[str] = []
    for token in _HEADER_TOKEN.findall(header):
        code = POS_CODES[re.sub(r"\s+", " ", token)]
        if code not in codes:
            codes.append(code)
    return tuple(codes)


def label_keys(token: str) -> list[str] | None:
    """Canonical keys of a label token (`"adm,law"` → `["adm", "law"]`), or None if any part is unknown."""
    stripped = token.rstrip(".,")
    if stripped in _CASED_LABEL_KEYS:
        return [_CASED_LABEL_KEYS[stripped]]
    whole = LABEL_KEYS.get(stripped.lower())
    if whole is not None:
        return [whole]
    keys: list[str] = []
    for part in _LABEL_PART_SEPARATOR.split(stripped):
        key = LABEL_KEYS.get(part.lower())
        if key is None:
            return None
        if key not in keys:
            keys.append(key)
    return keys or None


def take_labels(text: str) -> tuple[list[str], str]:
    """Labels at the start of `text` and the text after them.

    A whitelisted token followed by a non-label Latin word is text, not a label ("law and order"),
    and so is one bound to the Tibetan that follows ("auction ལ་ལྟོས།" = "see auction").
    """
    labels: list[str] = []
    while (match := _LABEL_TOKEN.match(text)) and (keys := label_keys(match.group("token"))) is not None:
        rest = text[match.end() :].lstrip()
        if starts_with_particle(rest):
            break
        if rest[:1].isascii() and rest[:1].isalpha():
            following = _LABEL_TOKEN.match(rest)
            if following is None or label_keys(following.group("token")) is None:
                break
        labels.extend(key for key in keys if key not in labels)
        text = rest
    return labels, text
