/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText

class ChineseT9CompositionCoordinator(
    formatText: (String) -> FormattedText?,
    buildRawPreeditDisplay: (String) -> FormattedText?
) {
    private val session = ChineseT9CompositionSession()
    private val rawCodeSession = ChineseT9RawCodeSession()
    private val lifecycle = ChineseT9CompositionLifecycle(session)
    private val presentationSource = ChineseT9PresentationSource(
        formatText = formatText,
        buildPreeditDisplay = buildRawPreeditDisplay
    )
    private val zhuyinResolver = T9ZhuyinResolver()
    private val codePresentationSource = ChineseT9CodePresentationSource(
        formatText = formatText,
        zhuyinResolver = zhuyinResolver
    )
    private val codePresentationCache = ChineseT9PresentationSnapshotCache()
    private var scheme = ChineseT9Scheme.PINYIN

    fun activateScheme(next: ChineseT9Scheme, forceReset: Boolean = false) {
        if (scheme == next && !forceReset) return
        clear()
        scheme = next
    }

    fun clear() {
        lifecycle.clearCompositionState()
        rawCodeSession.clear()
        codePresentationCache.reset()
    }

    fun hasState(): Boolean = when (scheme) {
        ChineseT9Scheme.PINYIN -> lifecycle.hasCompositionState()
        ChineseT9Scheme.STROKE,
        ChineseT9Scheme.ZHUYIN -> !rawCodeSession.isEmpty()
    }

    fun inputState(hasComposingText: Boolean): ChineseT9CompositionLifecycle.InputState =
        when (scheme) {
            ChineseT9Scheme.PINYIN -> lifecycle.inputState(hasComposingText)
            ChineseT9Scheme.STROKE,
            ChineseT9Scheme.ZHUYIN -> if (hasComposingText || !rawCodeSession.isEmpty()) {
                ChineseT9CompositionLifecycle.InputState.COMPOSING
            } else {
                ChineseT9CompositionLifecycle.InputState.IDLE
            }
        }

    fun shouldClearFromEditorTap(
        isActive: Boolean,
        state: ChineseT9CompositionLifecycle.InputState
    ): Boolean = isActive && (
        state == ChineseT9CompositionLifecycle.InputState.COMPOSING || hasState()
    )

    fun shouldClearHiddenComposition(isActive: Boolean, hasPendingPunctuation: Boolean): Boolean =
        isActive && !hasPendingPunctuation && keyCount() <= 0

    fun shouldResetEngineForLiteralStar(
        isChineseMode: Boolean,
        state: ChineseT9CompositionLifecycle.InputState
    ): Boolean = lifecycle.shouldResetEngineForLiteralStar(isChineseMode, state)

    fun shouldReopenLastResolvedSegment(isActive: Boolean): Boolean =
        scheme == ChineseT9Scheme.PINYIN && lifecycle.shouldReopenLastResolvedSegment(isActive)

    fun appendSeparator() {
        if (scheme == ChineseT9Scheme.PINYIN) lifecycle.appendSeparatorForShortcut()
    }

    fun backspaceFromVirtualKey() = when (scheme) {
        ChineseT9Scheme.PINYIN -> lifecycle.backspaceFromVirtualKey()
        ChineseT9Scheme.STROKE -> rawCodeSession.backspace()
        ChineseT9Scheme.ZHUYIN -> rawCodeSession.backspace().also {
            codePresentationCache.reset()
        }
    }

    fun handleForwardedKeyDown(keyCode: Int): ChineseT9CompositionLifecycle.ForwardedKeyAction =
        if (scheme == ChineseT9Scheme.PINYIN) {
            lifecycle.handleForwardedKeyDown(keyCode)
        } else {
            handleRawCodeKeyDown(keyCode)
        }

    suspend fun popLastResolvedSegment(api: FcitxAPI, candidatePagingMode: Int): Boolean {
        if (scheme != ChineseT9Scheme.PINYIN) return false
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
        if (scheme != ChineseT9Scheme.PINYIN) return rawCodeSnapshot()
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
            sessionRevision = session.revision,
            scheme = scheme
        )
    }

    fun compositionTicket(): ChineseT9CompositionTicket = when (scheme) {
        ChineseT9Scheme.PINYIN -> ChineseT9CompositionTicket(
            scheme = scheme,
            rawSequence = session.rawSequence(),
            digitSequence = session.digitSequence(),
            sessionRevision = session.revision
        )
        ChineseT9Scheme.STROKE,
        ChineseT9Scheme.ZHUYIN -> ChineseT9CompositionTicket(
            scheme = scheme,
            rawSequence = rawCodeSession.rawCode,
            digitSequence = rawCodeSession.digitSequence,
            sessionRevision = rawCodeSession.revision
        )
    }

    fun keyCount(): Int = when (scheme) {
        ChineseT9Scheme.PINYIN -> session.keyCount()
        ChineseT9Scheme.STROKE,
        ChineseT9Scheme.ZHUYIN -> rawCodeSession.keyCount
    }

    fun digitSequence(): String = when (scheme) {
        ChineseT9Scheme.PINYIN -> session.digitSequence()
        ChineseT9Scheme.STROKE,
        ChineseT9Scheme.ZHUYIN -> rawCodeSession.digitSequence
    }

    fun currentSegment(): String = when (scheme) {
        ChineseT9Scheme.PINYIN -> session.currentSegment(::firstUnresolvedRawSegment)
        ChineseT9Scheme.STROKE,
        ChineseT9Scheme.ZHUYIN -> rawCodeSession.currentSegment
    }

    fun readingCandidates(): List<String> = when (scheme) {
        ChineseT9Scheme.PINYIN -> T9PinyinUtils.t9KeyToPinyin(currentSegment())
        ChineseT9Scheme.STROKE,
        ChineseT9Scheme.ZHUYIN -> emptyList()
    }

    fun preeditDisplay(rawComposition: String? = null): FormattedText? = when (scheme) {
        ChineseT9Scheme.PINYIN -> presentationSource.preeditDisplay(
            model = session.model,
            fullComposition = session.fullComposition(),
            rawComposition = rawComposition
        )
        ChineseT9Scheme.STROKE,
        ChineseT9Scheme.ZHUYIN -> codePresentationSource.formattedRawDisplay(
            scheme,
            rawComposition ?: rawCodeSession.rawCode
        )
    }

    fun presentation(key: ChineseT9PresentationSnapshotKey): T9PresentationState =
        if (key.scheme == ChineseT9Scheme.PINYIN) {
            lifecycle.getOrBuildPresentation(key) {
                presentationSource.build(key)
            }
        } else {
            codePresentationCache.getOrBuild(key) {
                codePresentationSource.build(key)
            }
        }

    fun literalCommitText(preview: String): String? = when (scheme) {
        ChineseT9Scheme.PINYIN -> preview
            .filter { char -> char in 'a'..'z' || char in 'A'..'Z' }
            .takeIf(String::isNotEmpty)
        ChineseT9Scheme.STROKE -> T9StrokeCodec.literalCommitText(
            rawCode = rawCodeSession.rawCode,
            preview = preview
        )
        ChineseT9Scheme.ZHUYIN -> preview
            .filter(ChineseT9CodePresentationSource::isZhuyinSymbol)
            // A fallback key group contains several alternatives per digit and is not a reading.
            .takeIf { text -> text.length == rawCodeSession.keyCount }
    }

    fun candidateMatchesResolvedPrefix(
        candidate: FcitxEvent.Candidate,
        expected: String
    ): Boolean {
        if (scheme != ChineseT9Scheme.PINYIN) return false
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
        if (scheme != ChineseT9Scheme.PINYIN) return false
        val fullPrefix = resolvedPinyinPrefix() ?: return false
        if (prefix.isNotEmpty() && prefix != fullPrefix) return true
        if (prefix != fullPrefix || session.unresolvedDigits.isEmpty()) return false
        return ChineseT9PresentationSource.normalizeCandidateComment(candidate.comment) == prefix
    }

    fun consumeResolvedPrefix(prefix: String): String? =
        if (scheme != ChineseT9Scheme.PINYIN) null else session.consumeResolvedPrefix(
            prefix = prefix,
            removeResolvedPrefixFromRawSource = ::removeResolvedPrefixFromRawSource,
            firstUnresolvedRawSegment = ::firstUnresolvedRawSegment
        )

    fun prepareReplay(rawPreedit: String) {
        if (scheme == ChineseT9Scheme.PINYIN) session.prepareReplay(rawPreedit)
    }

    fun selectPinyin(pinyin: String): ChineseT9CompositionSession.PinyinSelectionRequest? =
        if (scheme == ChineseT9Scheme.PINYIN) session.selectPinyin(pinyin) else null

    suspend fun mirrorPinyinSelection(
        api: FcitxAPI,
        request: ChineseT9CompositionSession.PinyinSelectionRequest
    ): Boolean = ChineseT9RimeBridge.from(session, api).mirrorPinyinSelection(request)

    fun consumeSelectedCandidateReading(candidate: FcitxEvent.Candidate): Boolean {
        if (scheme != ChineseT9Scheme.PINYIN) {
            val hadCode = !rawCodeSession.isEmpty()
            rawCodeSession.clear()
            codePresentationCache.reset()
            return hadCode
        }
        val commentSegments = ChineseT9PresentationSource.normalizeCandidateComment(candidate.comment)
            .split(' ')
            .filter { it.isNotEmpty() }
        return session.consumeSelectedCandidateReading(commentSegments)
    }

    private fun handleRawCodeKeyDown(
        keyCode: Int
    ): ChineseT9CompositionLifecycle.ForwardedKeyAction {
        val hadComposition = !rawCodeSession.isEmpty()
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            rawCodeSession.backspace()
            codePresentationCache.reset()
            return when {
                !hadComposition -> ChineseT9CompositionLifecycle.ForwardedKeyAction.NONE
                rawCodeSession.isEmpty() ->
                    ChineseT9CompositionLifecycle.ForwardedKeyAction.HIDE_CANDIDATE_UI_IMMEDIATELY
                else ->
                    ChineseT9CompositionLifecycle.ForwardedKeyAction.REFRESH_AFTER_ENGINE_CANDIDATES
            }
        }
        val digit = PhysicalT9KeyPolicy.t9Digit(keyCode)
            ?.takeIf(scheme::acceptsCompositionDigit)
            ?: return ChineseT9CompositionLifecycle.ForwardedKeyAction.NONE
        rawCodeSession.append(digit)
        codePresentationCache.reset()
        return ChineseT9CompositionLifecycle.ForwardedKeyAction.REFRESH_AFTER_ENGINE_CANDIDATES
    }

    private fun rawCodeSnapshot(): ChineseT9InputSnapshot {
        val rawCode = rawCodeSession.rawCode
        val digitSequence = rawCodeSession.digitSequence
        val zhuyinResolution = if (scheme == ChineseT9Scheme.ZHUYIN) {
            zhuyinResolver.resolve(rawCode)
        } else {
            null
        }
        return ChineseT9InputSnapshot(
            rawSequence = rawCode,
            digitSequence = digitSequence,
            currentSegment = rawCodeSession.currentSegment,
            fullComposition = rawCode,
            model = T9CompositionModel(
                unresolvedDigits = digitSequence,
                rawPreedit = rawCode
            ),
            keyCount = rawCodeSession.keyCount,
            filterPrefixes = emptyList(),
            hasPendingPinyinSelection = false,
            sessionRevision = rawCodeSession.revision,
            scheme = scheme,
            hasInvalidReading = zhuyinResolution is T9ZhuyinResolver.Result.Invalid
        )
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
