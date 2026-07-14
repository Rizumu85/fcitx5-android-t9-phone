# Offline Handwriting Recognition Notice

The bundled offline handwriting repository contains substroke descriptors for
9,507 Chinese characters. It was converted on 2026-07-14 from `mmah.json` in
[`gugray/hanzi_lookup`](https://github.com/gugray/hanzi_lookup) into the app's
own compact binary layout. The converted file is
`app/src/main/assets/handwriting/mmah-substrokes-v1.bin`.

The descriptors ultimately derive from Make Me a Hanzi and Arphic PL fonts.
They remain available under the Arphic Public License. A verbatim copy is in
`docs/third-party/licenses/ARPHIC-PUBLIC-LICENSE.txt`.

The Kotlin substroke analysis and matching implementation is an independent
adaptation of the algorithm documented by HanziLookup and `hanzi_lookup`; those
source files are licensed under LGPL-3.0-or-later. A verbatim copy is in
`docs/third-party/licenses/LGPL-3.0.txt`.

Google ML Kit Digital Ink Recognition is a separately distributed optional
enhanced backend. Its language model is downloaded by ML Kit at runtime and is
not bundled, mirrored, or repackaged by this project.

AndroidX Ink provides the low-latency handwriting renderer and stock pressure
pen brush. AndroidX is licensed under the Apache License 2.0; its dependency
license is included in the app's generated third-party library notices.
