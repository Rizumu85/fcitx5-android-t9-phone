/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText

class ChineseT9CompositionCoordinator(
    formatText: (String) -> FormattedText?,
    buildRawPreeditDisplay: (String) -> FormattedText?
) {
    private val session = ChineseT9CompositionSession()
    private val lifecycle = ChineseT9CompositionLifecycle(session)
    private val presentationSource = ChineseT9PresentationSource(
        formatText = formatText,
        buildPreeditDisplay = buildRawPreeditDisplay
    )

    fun clear() = lifecycle.clearCompositionState()

    fun hasState(): Boolean = lifecycle.hasCompositionState()

    fun inputState(hasComposingText: Boolean): ChineseT9CompositionLifecycle.InputState =
        lifecycle.inputState(hasComposingText)

    fun shouldClearFromEditorTap(
        isActive: Boolean,
        state: ChineseT9CompositionLifecycle.InputState
    ): Boolean = lifecycle.shouldClearFromEditorTap(isActive, state)

    fun shouldClearHiddenComposition(isActive: Boolean, hasPendingPunctuation: Boolean): Boolean =
        lifecycle.shouldClearHiddenComposition(isActive, hasPendingPunctuation)

    fun shouldResetEngineForLiteralStar(
        isChineseMode: Boolean,
        state: ChineseT9CompositionLifecycle.InputState
    ): Boolean = lifecycle.shouldResetEngineForLiteralStar(isChineseMode, state)

    fun shouldReopenLastResolvedSegment(isActive: Boolean): Boolean =
        lifecycle.shouldReopenLastResolvedSegment(isActive)

    fun appendSeparator() = lifecycle.appendSeparatorForShortcut()

    fun backspaceFromVirtualKey() = lifecycle.backspaceFromVirtualKey()

    fun handleForwardedKeyDown(keyCode: Int): ChineseT9CompositionLifecycle.ForwardedKeyAction =
        lifecycle.handleForwardedKeyDown(keyCode)

    suspend fun popLastResolvedSegment(api: FcitxAPI, candidatePagingMode: Int): Boolean {
        val popped = session.popLastResolvedSegment() ?: return false
        if (popped.segment.engineBacked) {
            ChineseT9RimeBridge.from(session, api).restoreResolvedSegment(
                segment = popped.segment,
                previousUnresolved = popped.previousUnresolved,
                fallbackRawPreedit = popped.fallbackRawPreedit,
                candidatePagingMode = candidatePagingMode
            )
        }
        return true
    }

    fun snapshot(inputPreedit: String): ChineseT9InputSnapshot {
        session.syncFromPreedit(inputPreedit)
        val rawSequence = session.rawSequence()
        return ChineseT9InputSnapshot(
            rawSequence = rawSequence,
            digitSequence = rawSequence.filter { it in '2'..'9' },
            currentSegment = currentSegment(),
            fullComposition = session.fullComposition(),
            model = session.model,
            keyCount = rawSequence.count { it in '2'..'9' },
            filterPrefixes = resolvedPinyinFilterPrefixes(session.model),
            hasPendingPinyinSelection = session.pendingSelection != null,
            sessionRevision = session.revision
        )
    }

    fun keyCount(): Int = session.keyCount()

    fun digitSequence(): String = session.digitSequence()

    fun currentSegment(): String = session.currentSegment(::firstUnresolvedRawSegment)

    fun pinyinCandidates(): List<String> =
        T9PinyinUtils.t9KeyToPinyin(currentSegment())

    fun preeditDisplay(rawComposition: String? = null): FormattedText? =
        presentationSource.preeditDisplay(
            model = session.model,
            fullComposition = session.fullComposition(),
            rawComposition = rawComposition
        )

    fun presentation(key: ChineseT9PresentationSnapshotKey): T9PresentationState =
        lifecycle.getOrBuildPresentation(key) {
            presentationSource.build(key)
        }

    fun candidateMatchesResolvedPrefix(
        candidate: FcitxEvent.Candidate,
        expected: String
    ): Boolean {
        val normalized = ChineseT9PresentationSource.normalizeCandidateComment(candidate.comment)
        val expectedSegments = resolvedSegmentsForFilterPrefix(expected)
        if (!expectedSegments.isNullOrEmpty()) {
            val commentSegments = normalized
                .split(' ')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
            if (commentSegments.size >= expectedSegments.size &&
                expectedSegments.indices.all { index ->
                    ChineseT9PresentationSource.commentSegmentMatchesResolvedSegment(
                        commentSegments[index],
                        expectedSegments[index]
                    )
                }
            ) {
                return true
            }
        }
        return normalized == expected || normalized.startsWith("$expected ")
    }

    fun shouldConsumeResolvedPrefixAfterCandidate(
        prefix: String,
        candidate: FcitxEvent.Candidate
    ): Boolean {
        val fullPrefix = resolvedPinyinPrefix() ?: return false
        if (prefix.isNotEmpty() && prefix != fullPrefix) return true
        if (prefix != fullPrefix || session.unresolvedDigits.isEmpty()) return false
        return ChineseT9PresentationSource.normalizeCandidateComment(candidate.comment) == prefix
    }

    fun consumeResolvedPrefix(prefix: String): String? =
        session.consumeResolvedPrefix(
            prefix = prefix,
            removeResolvedPrefixFromRawSource = ::removeResolvedPrefixFromRawSource,
            firstUnresolvedRawSegment = ::firstUnresolvedRawSegment
        )

    fun prepareReplay(rawPreedit: String) = session.prepareReplay(rawPreedit)

    fun selectPinyin(pinyin: String): ChineseT9CompositionSession.PinyinSelectionRequest? =
        session.selectPinyin(pinyin)

    suspend fun mirrorPinyinSelection(
        api: FcitxAPI,
        request: ChineseT9CompositionSession.PinyinSelectionRequest
    ): Boolean = ChineseT9RimeBridge.from(session, api).mirrorPinyinSelection(request)

    fun consumeSelectedCandidateReading(candidate: FcitxEvent.Candidate): Boolean {
        val commentSegments = ChineseT9PresentationSource.normalizeCandidateComment(candidate.comment)
            .split(' ')
            .filter { it.isNotEmpty() }
        return session.consumeSelectedCandidateReading(commentSegments)
    }

    private fun resolvedPinyinPrefix(): String? =
        session.resolvedSegments
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ") { it.pinyin }

    private fun resolvedPinyinFilterPrefixes(model: T9CompositionModel): List<String> {
        val resolvedSegments = model.resolvedSegments
        val resolved = resolvedSegments.map { it.pinyin }
        if (resolved.isEmpty()) return emptyList()
        if (model.pendingSelection != null || resolvedSegments.all { it.engineBacked }) {
            return emptyList()
        }
        return (resolved.size downTo 1)
            .map { count -> resolved.take(count).joinToString(" ") }
            .distinct()
    }

    private fun resolvedSegmentsForFilterPrefix(prefix: String): List<T9ResolvedSegment>? {
        val resolved = session.resolvedSegments
        for (count in 1..resolved.size) {
            val segments = resolved.take(count)
            if (segments.joinToString(" ") { it.pinyin } == prefix) return segments
        }
        return null
    }

    private fun firstUnresolvedRawSegment(
        raw: String,
        resolved: List<T9ResolvedSegment>
    ): String = removeResolvedPrefixFromRawSource(raw, resolved)
        .split('\'')
        .firstOrNull { segment -> segment.any { it in '2'..'9' } }
        ?.filter { it in '2'..'9' }
        .orEmpty()

    private fun removeResolvedPrefixFromRawSource(
        raw: String,
        resolved: List<T9ResolvedSegment>
    ): String {
        var rest = raw.filter { it in '2'..'9' || it == '\'' }
        resolved.forEach { segment ->
            if (rest.startsWith(segment.sourceDigits)) {
                rest = rest.drop(segment.sourceDigits.length)
                if (rest.startsWith('\'')) rest = rest.drop(1)
            }
        }
        return rest
    }
}
