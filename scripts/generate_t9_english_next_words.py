#!/usr/bin/env python3
#
# SPDX-License-Identifier: LGPL-2.1-or-later
# SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors

"""Generate the Smart English next-word asset from TT9 ngram data."""

from __future__ import annotations

import argparse
import collections
import io
import re
import urllib.request
import zipfile
from pathlib import Path


TT9_NGRAMS_URL = (
    "https://raw.githubusercontent.com/sspanak/tt9/master/app/languages/ngrams/en-ngrams.zip"
)
WORD_RE = re.compile(r"[A-Za-z]+(?:'[A-Za-z]+)?")


def normalize_word(raw: str) -> str | None:
    word = raw.lower().replace("'", "")
    if len(word) < 2:
        return None
    if not word.isalpha():
        return None
    if len(set(word)) == 1:
        return None
    return word


def ngram_lines(source: str) -> list[str]:
    if source.startswith("http://") or source.startswith("https://"):
        data = urllib.request.urlopen(source, timeout=60).read()
    else:
        data = Path(source).read_bytes()
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        name = archive.namelist()[0]
        with archive.open(name) as file:
            return file.read().decode("utf-8", errors="replace").splitlines()


def build_pairs(lines: list[str]) -> dict[str, list[tuple[str, int]]]:
    weighted_pairs: dict[str, collections.Counter[str]] = collections.defaultdict(collections.Counter)
    for rank, line in enumerate(lines):
        words = [word for word in (normalize_word(match.group(0)) for match in WORD_RE.finditer(line)) if word]
        if len(words) < 2:
            continue
        score = max(1, len(lines) - rank)
        for previous, next_word in zip(words, words[1:]):
            if previous == next_word:
                continue
            weighted_pairs[previous][next_word] += score
    return {
        previous: sorted(counter.items(), key=lambda item: (-item[1], item[0]))
        for previous, counter in weighted_pairs.items()
    }


def write_asset(pairs: dict[str, list[tuple[str, int]]], output: Path, per_previous_limit: int) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="\n") as file:
        for previous in sorted(pairs):
            next_words = pairs[previous][:per_previous_limit]
            if not next_words:
                continue
            file.write(previous)
            for word, score in next_words:
                file.write(f"\t{word}:{score}")
            file.write("\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", default=TT9_NGRAMS_URL)
    parser.add_argument(
        "--output",
        default="app/src/main/assets/t9/english-next.tsv",
        type=Path,
    )
    parser.add_argument("--per-previous-limit", default=24, type=int)
    args = parser.parse_args()

    write_asset(
        build_pairs(ngram_lines(args.source)),
        args.output,
        args.per_previous_limit,
    )


if __name__ == "__main__":
    main()
