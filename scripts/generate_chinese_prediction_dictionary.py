#!/usr/bin/env python3
#
# SPDX-License-Identifier: LGPL-2.1-or-later
# SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors

"""Generate the compact Chinese handwriting prediction dictionary."""

from __future__ import annotations

import argparse
import collections
import gzip
import hashlib
import io
import struct
import urllib.request
from pathlib import Path

try:
    from opencc import OpenCC
except ImportError as error:
    raise SystemExit(
        "Install opencc-python-reimplemented==0.1.7 before running this generator."
    ) from error


SOURCE_URL = (
    "https://github.com/rime/librime-predict/releases/download/data-1.0/predict.txt"
)
SOURCE_SHA256 = "df0f7a9ef96569da402d9ea2376aefad4d15382ebcccb05ec84a0acbc00c7f83"
MAGIC = b"CPD1"
FORMAT_VERSION = 1
HEADER_SIZE = 24
MAX_CANDIDATES = 10


def source_bytes(source: str) -> bytes:
    if source.startswith(("http://", "https://")):
        return urllib.request.urlopen(source, timeout=120).read()
    return Path(source).read_bytes()


def verify_source(data: bytes) -> None:
    digest = hashlib.sha256(data).hexdigest()
    if digest != SOURCE_SHA256:
        raise SystemExit(
            f"Unexpected predict.txt SHA-256: {digest}; expected {SOURCE_SHA256}"
        )


def parse_rows(data: bytes) -> dict[str, dict[str, int]]:
    rows: dict[str, dict[str, int]] = collections.defaultdict(dict)
    for line in io.TextIOWrapper(io.BytesIO(data), encoding="utf-8"):
        parts = line.rstrip("\n").split("\t")
        if len(parts) != 3:
            continue
        context, candidate, raw_weight = parts
        if not context or not candidate:
            continue
        weight = int(raw_weight)
        rows[context][candidate] = max(weight, rows[context].get(candidate, 0))
    return rows


def convert_rows(
    rows: dict[str, dict[str, int]],
    converter: OpenCC,
) -> dict[str, dict[str, int]]:
    converted: dict[str, dict[str, int]] = collections.defaultdict(dict)
    for context, candidates in rows.items():
        converted_context = converter.convert(context)
        target = converted[converted_context]
        for candidate, weight in candidates.items():
            converted_candidate = converter.convert(candidate)
            target[converted_candidate] = max(weight, target.get(converted_candidate, 0))
    return converted


def encode_section(rows: dict[str, dict[str, int]]) -> bytes:
    section = bytearray()
    for context in sorted(rows, key=lambda value: value.encode("utf-8")):
        context_bytes = context.encode("utf-8")
        ranked = sorted(
            rows[context].items(),
            key=lambda item: (-item[1], item[0].encode("utf-8")),
        )[:MAX_CANDIDATES]
        if len(context_bytes) > 0xFFFF:
            raise ValueError(f"Context is too long: {context!r}")
        section += struct.pack(">H", len(context_bytes))
        section += context_bytes
        section += struct.pack("B", len(ranked))
        for candidate, _ in ranked:
            candidate_bytes = candidate.encode("utf-8")
            if len(candidate_bytes) > 0xFFFF:
                raise ValueError(f"Candidate is too long: {candidate!r}")
            section += struct.pack(">H", len(candidate_bytes))
            section += candidate_bytes
    return bytes(section)


def write_asset(
    traditional_rows: dict[str, dict[str, int]],
    simplified_rows: dict[str, dict[str, int]],
    output: Path,
) -> None:
    traditional = encode_section(traditional_rows)
    simplified = encode_section(simplified_rows)
    simplified_offset = HEADER_SIZE + len(traditional)
    payload = b"".join(
        (
            MAGIC,
            struct.pack(
                ">IIIII",
                FORMAT_VERSION,
                HEADER_SIZE,
                len(traditional_rows),
                simplified_offset,
                len(simplified_rows),
            ),
            traditional,
            simplified,
        )
    )

    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as raw_output:
        with gzip.GzipFile(
            filename="",
            mode="wb",
            fileobj=raw_output,
            compresslevel=9,
            mtime=0,
        ) as compressed:
            compressed.write(payload)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", default=SOURCE_URL)
    parser.add_argument(
        "--output",
        default="app/src/main/assets/t9/chinese-predict-v1.cpz",
        type=Path,
    )
    args = parser.parse_args()

    data = source_bytes(args.source)
    verify_source(data)
    traditional_rows = parse_rows(data)
    simplified_rows = convert_rows(traditional_rows, OpenCC("t2s"))
    write_asset(traditional_rows, simplified_rows, args.output)
    print(f"Wrote {args.output} ({args.output.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
