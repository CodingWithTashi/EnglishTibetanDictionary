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
    # Misspellings that occur only at the start of a definition ("adv. & phep.", "v. t.", "n.t.").
    "phep.": "prep",
    "v. t.": "vt",
    "v. i.": "vi",
    "n.t.": "vt",
    "b.t.": "vt",
}
VERB_CODES = frozenset({"v", "vt", "vi"})

# Tokens that are headers wherever a block may start. Longer spellings come first ("v.t." before "v.").
_STRONG_POS = (
    r"(?:v\.t\.|v\.i\.|vt\.|vi\.|v\.|n\.|adj\.|adv\.|interj\.|int\.|prep\.|pron\.|conj\.|pl\.|abbr\.|pred\.|aux\.|num\.)"
)
# Undotted spellings ("n 1.", "v.t 1.", "prefix") and misspellings ("v. t.", "n.t.", "phep.") are
# headers only at the start of a definition.
_WEAK_POS = (
    r"(?:v\.\s+t\.|v\.\s+i\.|n\.t\.|b\.t\.|phep\.|v\.t|v\.i|vt|vi|adj|adv|prep|n|prefix\.?|suffix\.?"
    r"|combining\s+form)"
)
# Rule "`adj. & n.` → `adj,n`": tokens chain with spaces, "&", "and" or a comma, and the source's stray
# dots ("n. pl.", "n.pl.", "adj. and adv.", "n. &. v.i.", "n. & . v.", "adv, prep. & . conj.").
_JOIN = r"(?:\s*[&,]\s*\.?\s*|\s+and\s+|\s*)"
_HEADER_END = r"(?=\s|$|[\u0f00-\u0fff(*\[0-9])"
START_HEADER = re.compile(
    rf"^(?P<header>(?:{_WEAK_POS}|{_STRONG_POS})(?:{_JOIN}(?:{_WEAK_POS}|{_STRONG_POS}))*){_HEADER_END}"
)
MID_HEADER = re.compile(rf"(?<=\s)(?P<header>{_STRONG_POS}(?:{_JOIN}{_STRONG_POS})*){_HEADER_END}")
# Right after a sense number a header names that sense's part of speech only ("2. pl. …", "3. v.i. …").
SENSE_HEADER = re.compile(rf"^(?P<header>{_STRONG_POS})(?=\s|[\u0f00-\u0fff])\s*")
# One header token, longest spelling first, so "v.t" is never read as "v." followed by "t"; a token is
# never part of a longer word (the "n" of "and").
_HEADER_TOKEN = re.compile(
    "(?<![A-Za-z])(?:"
    + "|".join(re.escape(token).replace(r"\ ", r"\s+") for token in sorted(POS_CODES, key=len, reverse=True))
    + ")(?![A-Za-z])"
)

# Rule "domain/register labels from a whitelist (adm med bot phys astrol pol law com chem edu econ …
# sl colloq fig archaic obs …)": abbreviation → canonical key. Variant spellings share one key.
LABEL_KEYS: dict[str, str] = {
    **{
        key: key
        for key in (
            "acc", "adm", "aeron", "agric", "alchem", "anat", "anthrop", "archaic", "archit", "arith", "astrol", "astron",
            "aviat", "bacteriol", "biochem", "biol", "bot", "brit", "chem", "cine", "colloq", "com", "comput",
            "cricket", "dent", "derm", "dial", "eccl", "ecol", "econ", "edu", "electr", "engin", "entomol",
            "environ", "fig", "geog", "geol", "geom", "genet", "gk", "golf", "gram", "hist", "hort", "joc", "jrn",
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
    "vulg": "vulg",
    "arch": "archaic",
    "astro": "astron",
    "compt": "comput",
    "comptr": "comput",
    "comptr.sc": "comput",
    "eccles": "eccl",
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
    "orithol": "ornithol",
    "pathol": "path",
    "phot": "photo",
    "phon": "phonet",
    "physio": "physiol",
    "physol": "physiol",
    "print": "printing",
    "psycho": "psych",
    "psycol": "psych",
    "psychiatry": "psych",
    "psychol": "psych",
    "rel": "relig",
    "scotish": "scot",
    "theater": "theatr",
    "rhet": "rhet",
    "sc": "sci",
    "int'l": "intl",
    "int’l": "intl",
    # Multi-part labels written with spaces are looked up without them ("compt. sc." → "compt.sc").
    "compt.sc": "comput",
    "comp.sc": "comput",
}
# Capitalised spellings that are labels ("L." = Latin, "Gk." = Greek, "F."/"Fr." = French).
_CASED_LABEL_KEYS: dict[str, str] = {"L": "lat", "Gk": "gk", "Fr": "fr", "F": "fr"}
# "tennis etc.", "surg. etc.": a trailing "etc." widens the label and is not a key of its own.
_ETC = "etc"

# A label as written: letters with inner dots or commas ("adm,law", "comptr.sc"), an optional final
# dot or comma, then a space, Tibetan text, a bracket, an asterisk or a digit.
_LABEL_TOKEN = re.compile(r"(?P<token>[A-Za-z’']+(?:[.,][A-Za-z’']+)*[.,]?)(?=\s|$|[\u0f00-\u0fff(*0-9])")
# A multi-part label whose parts are separated by spaces ("compt. sc.", "poet., rhet.", "tennis etc.",
# "Int’l law"). It is tried first; when it is not a label as a whole, the single token is tried.
_MULTI_LABEL_TOKEN = re.compile(
    r"(?P<token>[A-Za-z’']+\.?(?:,?\s+[A-Za-z’']+\.?)+,?)(?=\s|$|[\u0f00-\u0fff(*0-9])"
)
_LABEL_PART_SEPARATOR = re.compile(r"[.,\s]+")


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
    stripped = re.sub(r"\s+", "", token.rstrip(".,"))
    if stripped in _CASED_LABEL_KEYS:
        if len(stripped) == 1 and not token.rstrip(",").endswith("."):
            return None  # "(F)", "(L)": the letter itself, not "F." (French) or "L." (Latin)
        return [_CASED_LABEL_KEYS[stripped]]
    whole = LABEL_KEYS.get(stripped.lower())
    if whole is not None:
        return [whole]
    parts = [part for part in _LABEL_PART_SEPARATOR.split(token.rstrip(".,")) if part]
    if len(parts) > 1 and parts[-1].lower() == _ETC:
        parts.pop()
    keys: list[str] = []
    for part in parts:
        key = LABEL_KEYS.get(part.lower()) or _CASED_LABEL_KEYS.get(part)
        if key is None:
            return None
        if key not in keys:
            keys.append(key)
    return keys or None


def take_labels(text: str) -> tuple[list[str], str]:
    """Labels at the start of `text` and the text after them.

    An undotted whitelisted token followed by a non-label Latin word is text, not a label ("law and
    order"); a dotted one is a label before a phrase ("sl. chuck out …", "anat. head ཀཿ …"). A token
    bound to the Tibetan that follows is text ("auction ལ་ལྟོས།" = "see auction").
    """
    labels: list[str] = []
    while (found := _take_label(text)) is not None:
        keys, text = found
        labels.extend(key for key in keys if key not in labels)
    return labels, text


def _take_label(text: str) -> tuple[list[str], str] | None:
    for pattern in (_MULTI_LABEL_TOKEN, _LABEL_TOKEN):
        match = pattern.match(text)
        if match is None or (keys := label_keys(match.group("token"))) is None:
            continue
        rest = text[match.end() :].lstrip()
        if starts_with_particle(rest):
            return None
        if rest[:1].isascii() and rest[:1].isalpha() and not match.group("token").endswith("."):
            following = _LABEL_TOKEN.match(rest)
            if following is None or label_keys(following.group("token")) is None:
                return None
        return keys, rest
    return None
