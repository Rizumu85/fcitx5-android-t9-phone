/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxAPI

class ChineseT9RimeBridge(
    private val session: ChineseT9CompositionSession,
    private val io: RimeIo
) {
    interface RimeIo {
        suspend fun getInput(): String
        suspend fun replaceInput(start: Int, length: Int, text: String, caretPos: Int): Boolean
        suspend fun setCandidatePagingMode(mode: Int)
        suspend fun reset()
        suspend fun sendKey(char: Char)
    }

    suspend fun mirrorPinyinSelection(
        request: ChineseT9CompositionSession.PinyinSelectionRequest
    ): Boolean {
        val selectedSegment = request.selectedSegment
        val replacement = engineReplacementText(selectedSegment.pinyin)
        val input = io.getInput()
        val separatorLength = if (request.consumeExplicitSeparator) 1 else 0
        val originalLength = request.originalSegment.length + separatorLength
        val start = if (request.replaceFromStart) 0 else input.length - originalLength
        val replacementLength = selectedSegment.sourceDigits.length + separatorLength
        val replacementCaret = input.length - replacementLength + replacement.length
        val canReplace = start >= 0 &&
            input.regionMatches(start, selectedSegment.sourceDigits, 0, selectedSegment.sourceDigits.length)
        val replaced = canReplace &&
            io.replaceInput(start, replacementLength, replacement, replacementCaret)
        if (replaced) {
            session.markSelectionEngineBacked(selectedSegment, request.remainingDigits)
        } else {
            session.clearPendingSelection(selectedSegment, request.remainingDigits)
        }
        return replaced
    }

    suspend fun restoreResolvedSegment(
        segment: T9ResolvedSegment,
        previousUnresolved: String,
        fallbackRawPreedit: String,
        candidatePagingMode: Int
    ): Boolean {
        val selectedInput = engineReplacementText(segment.pinyin)
        val restoreReplacement = if (fallbackRawPreedit.contains("${segment.sourceDigits}'")) {
            "${segment.sourceDigits}'"
        } else {
            segment.sourceDigits
        }
        val input = io.getInput()
        val expectedStart = input.length - previousUnresolved.length - selectedInput.length
        val start = expectedStart.takeIf {
            it >= 0 && input.regionMatches(it, selectedInput, 0, selectedInput.length)
        } ?: input.lastIndexOf(selectedInput).takeIf { it >= 0 }
        val caretPos = input.length - selectedInput.length + restoreReplacement.length
        val restored = start != null &&
            io.replaceInput(start, selectedInput.length, restoreReplacement, caretPos)
        if (!restored) {
            io.setCandidatePagingMode(candidatePagingMode)
            io.reset()
            fallbackRawPreedit.forEach { ch ->
                if (ch in '2'..'9' || ch == '\'') {
                    io.sendKey(ch)
                }
            }
            session.markAllResolvedSegmentsEngineUnbacked(fallbackRawPreedit)
        }
        return restored
    }

    companion object {
        fun engineReplacementText(pinyin: String): String = "${pinyin.lowercase()}'"

        fun from(session: ChineseT9CompositionSession, api: FcitxAPI): ChineseT9RimeBridge =
            ChineseT9RimeBridge(
                session = session,
                io = object : RimeIo {
                    override suspend fun getInput(): String = api.getRimeInput()

                    override suspend fun replaceInput(
                        start: Int,
                        length: Int,
                        text: String,
                        caretPos: Int
                    ): Boolean = api.replaceRimeInput(start, length, text, caretPos)

                    override suspend fun setCandidatePagingMode(mode: Int) =
                        api.setCandidatePagingMode(mode)

                    override suspend fun reset() = api.reset()

                    override suspend fun sendKey(char: Char) = api.sendKey(char)
                }
            )
    }
}
