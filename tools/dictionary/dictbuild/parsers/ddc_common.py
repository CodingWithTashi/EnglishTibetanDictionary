"""Pieces shared by the parsers of the three DDC Dzongkha StarDict dictionaries (dz-en, en-dz, dz-dz).

The sources are StarDict `x` records: `<k>key</k> <def>…</def>` blocks with inline markup (`<pos>`,
`<abbr><i>…</i></abbr>`, `<b>N</b>`, `<kref type="syn">…</kref>`) that is often glued to the text
around it.
"""

from __future__ import annotations

import html
import re

# Part-of-speech labels as the DDC files write them → `senses.part_of_speech` codes. `IDM` (idiom)
# is a known label without a code: the entry is a phrase.
DDC_POS_CODES: dict[str, str | None] = {
    "noun": "n",
    "adj.": "adj",
    "verb": "v",
    "adv.": "adv",
    "pron.": "pron",
    "prep.": "prep",
    "excl.": "interj",
    "conj.": "conj",
    "pron:": "pron",
    "conj:": "conj",
    "n:": "n",
    "v:": "v",
    "n.": "n",
    "v.": "v",
    "adv:": "adv",
    "modal v.": "aux",
    "combining form": "comb",
    "combining form n.": "comb",
    "PHR V.": "v",
    "IDM": None,
    "prefix": "prefix",
    "suffix": "suffix",
}

_TAG = re.compile(r"<[^>]*>")
_WHITESPACE_RUN = re.compile(r"\s+")
_K_BLOCK = re.compile(r"<k>(?P<key>.*?)</k>", re.DOTALL)


def pos_code(text: str) -> tuple[str | None, bool]:
    """Return (code, known) for a part-of-speech label.

    An empty label is `(None, True)`, `IDM` is `(None, True)` as well; any other label that is not in
    `DDC_POS_CODES` (compared as written, then lower-cased) is `(None, False)`.
    """
    label = _WHITESPACE_RUN.sub(" ", text).strip()
    if not label:
        return None, True
    for candidate in (label, label.lower()):
        if candidate in DDC_POS_CODES:
            return DDC_POS_CODES[candidate], True
    return None, False


def strip_tags(markup: str) -> str:
    """Replace each tag with a space, unescape HTML entities and collapse whitespace."""
    return _WHITESPACE_RUN.sub(" ", html.unescape(_TAG.sub(" ", markup))).strip()


def split_k_blocks(body: str) -> list[tuple[str, str]]:
    """Split a record into `(key text, rest)` per `<k>…</k>` block, in order.

    `rest` is the markup from the end of the block's `</k>` up to the next `<k>` (or the end).
    """
    matches = list(_K_BLOCK.finditer(body))
    blocks: list[tuple[str, str]] = []
    for position, match in enumerate(matches):
        end = matches[position + 1].start() if position + 1 < len(matches) else len(body)
        blocks.append((strip_tags(match.group("key")), body[match.end() : end]))
    return blocks
