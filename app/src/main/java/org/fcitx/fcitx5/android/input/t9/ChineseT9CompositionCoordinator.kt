/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import android.view.KeyEvent
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
    private val zhuyinReadingFilter = T9ZhuyinReadingFilterSession(zhuyinResolver)
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
        zhuyinReadingFilter.reset()
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

    fun shouldPreserveDeferredComposition(
        isActive: Boolean,
        engineWaiting: Boolean
    ): Boolean =
        isActive && engineWaiting && hasState()

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
            zhuyinReadingFilter.updateRawCode(rawCodeSession.rawCode)
            codePresentationCache.reset()
        }
    }

    fun handleForwardedKeyDown(keyCode: Int): ChineseT9CompositionLifecycle.ForwardedKeyAction =
        if (scheme == ChineseT9Scheme.PINYIN) {
            lifecycle.handleForwardedKeyDown(keyCode)
        } else {
            handleRawCodeKeyDown(keyCode)
        }

    fun popLastResolvedSegment(): ChineseT9CompositionSession.EngineProjection? =
        if (scheme == ChineseT9Scheme.PINYIN) session.popLastResolvedSegment() else null

    fun pinyinProjection(): ChineseT9CompositionSession.EngineProjection = session.projection()

    fun acceptsPinyinProjection(projection: ChineseT9CompositionSession.EngineProjection): Boolean =
        scheme == ChineseT9Scheme.PINYIN && session.accepts(projection)

    fun snapshot(): ChineseT9InputSnapshot {
        if (scheme != ChineseT9Scheme.PINYIN) return rawCodeSnapshot()
        val rawSequence = session.rawSequence()
        return ChineseT9InputSnapshot(
            rawSequence = rawSequence,
            digitSequence = rawSequence.filter { it in '2'..'9' },
            currentSegment = currentSegment(),
            fullComposition = session.fullComposition(),
            model = session.model,
            keyCount = rawSequence.count { it in '2'..'9' },
            filterPrefixes = emptyList(),
            sessionRevision = session.revision,
            sessionEpoch = session.epoch,
            scheme = scheme
        )
    }

    fun compositionTicket(): ChineseT9CompositionTicket = when (scheme) {
        ChineseT9Scheme.PINYIN -> ChineseT9CompositionTicket(
            scheme = scheme,
            rawSequence = session.rawSequence(),
            digitSequence = session.digitSequence(),
            sessionRevision = session.revision,
            sessionEpoch = session.epoch
        )
        ChineseT9Scheme.STROKE,
        ChineseT9Scheme.ZHUYIN -> ChineseT9CompositionTicket(
            scheme = scheme,
            rawSequence = rawCodeSession.rawCode,
            digitSequence = rawCodeSession.digitSequence,
            sessionRevision = rawCodeSession.revision,
            sessionEpoch = session.epoch
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
        ChineseT9Scheme.PINYIN -> session.currentSegment()
        ChineseT9Scheme.STROKE,
        ChineseT9Scheme.ZHUYIN -> rawCodeSession.currentSegment
    }

    fun readingCandidates(): List<String> = when (scheme) {
        ChineseT9Scheme.PINYIN -> T9PinyinUtils.t9KeyToPinyin(currentSegment())
        ChineseT9Scheme.STROKE -> emptyList()
        ChineseT9Scheme.ZHUYIN -> zhuyinReadingFilter.visibleOptions(rawCodeSession.rawCode)
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
        if (scheme == ChineseT9Scheme.ZHUYIN) {
            return zhuyinResolver.candidateMatchesReadingOption(candidate.comment, expected)
        }
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

    fun selectPinyin(pinyin: String): ChineseT9CompositionSession.EngineProjection? =
        if (scheme == ChineseT9Scheme.PINYIN) session.selectPinyin(pinyin) else null

    fun selectZhuyinReading(reading: String): Boolean {
        if (scheme != ChineseT9Scheme.ZHUYIN ||
            !zhuyinReadingFilter.select(rawCodeSession.rawCode, reading)
        ) {
            return false
        }
        codePresentationCache.reset()
        return true
    }

    fun consumeSelectedCandidateReading(
        candidate: FcitxEvent.Candidate
    ): String? {
        if (scheme == ChineseT9Scheme.ZHUYIN) {
            val reading = T9ZhuyinResolver.normalizeCandidateReading(candidate.comment)
            val readingDigits = T9ZhuyinResolver.digitsForReading(reading)
            rawCodeSession.consumePrefix(readingDigits)?.let { remaining ->
                zhuyinReadingFilter.consumePrefix(readingDigits.length)
                codePresentationCache.reset()
                return remaining
            }
        }
        if (scheme == ChineseT9Scheme.STROKE || scheme == ChineseT9Scheme.ZHUYIN) {
            if (rawCodeSession.isEmpty()) return null
            rawCodeSession.clear()
            zhuyinReadingFilter.reset()
            codePresentationCache.reset()
            return ""
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
            if (scheme == ChineseT9Scheme.ZHUYIN) {
                zhuyinReadingFilter.updateRawCode(rawCodeSession.rawCode)
            }
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
        if (scheme == ChineseT9Scheme.ZHUYIN) {
            zhuyinReadingFilter.updateRawCode(rawCodeSession.rawCode)
        }
        codePresentationCache.reset()
        return ChineseT9CompositionLifecycle.ForwardedKeyAction.REFRESH_AFTER_ENGINE_CANDIDATES
    }

    private fun rawCodeSnapshot(): ChineseT9InputSnapshot {
        val rawCode = rawCodeSession.rawCode
        val digitSequence = rawCodeSession.digitSequence
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
            filterPrefixes = if (scheme == ChineseT9Scheme.ZHUYIN) {
                zhuyinReadingFilter.filterPrefixes()
            } else {
                emptyList()
            },
            sessionRevision = rawCodeSession.revision,
            sessionEpoch = session.epoch,
            scheme = scheme,
            hasInvalidReading = scheme == ChineseT9Scheme.ZHUYIN && zhuyinReadingFilter.hasInvalidReading,
            explicitReadingOptions = if (scheme == ChineseT9Scheme.ZHUYIN) {
                zhuyinReadingFilter.visibleOptions(rawCode)
            } else {
                emptyList()
            },
            selectedReading = zhuyinReadingFilter.selectedReading
        )
    }

    private fun resolvedSegmentsForFilterPrefix(prefix: String): List<T9ResolvedSegment>? {
        val resolved = session.resolvedSegments
        for (count in 1..resolved.size) {
            val segments = resolved.take(count)
            if (segments.joinToString(" ") { it.pinyin } == prefix) return segments
        }
        return null
    }

}
