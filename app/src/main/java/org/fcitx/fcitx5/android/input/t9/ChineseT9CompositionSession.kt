/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

class ChineseT9CompositionSession {

    data class PopResolvedSegmentResult(
        val segment: T9ResolvedSegment,
        val previousUnresolved: String,
        val fallbackRawPreedit: String
    )

    data class PinyinSelectionRequest(
        val selectedSegment: T9ResolvedSegment,
        val originalSegment: String,
        val remainingDigits: String,
        val consumeExplicitSeparator: Boolean,
        val replaceFromStart: Boolean
    )

    private val tracker = T9CompositionTracker()

    var model = T9CompositionModel()
        private set

    var revision: Long = 0
        private set

    private var observedNonEmptyPreedit = false
    private var ignoreEmptyPreeditUntilReplay = false

    val hasResolvedSegments: Boolean
        get() = model.hasResolvedSegments

    val pendingSelection: T9PendingSelection?
        get() = model.pendingSelection

    val resolvedSegments: List<T9ResolvedSegment>
        get() = model.resolvedSegments

    val unresolvedDigits: String
        get() = model.unresolvedDigits

    val rawPreedit: String
        get() = model.rawPreedit

    fun clear() {
        tracker.clear()
        setModel(T9CompositionModel())
        observedNonEmptyPreedit = false
        ignoreEmptyPreeditUntilReplay = false
    }

    fun hasState(): Boolean = !tracker.isEmpty() || model != T9CompositionModel()

    fun appendDigit(digit: Char) {
        tracker.appendDigit(digit)
        updateFromTracker()
    }

    fun appendSeparator() {
        tracker.appendApostrophe()
        updateFromTracker()
    }

    fun backspace() {
        tracker.backspace()
        updateFromTracker()
    }

    fun replace(rawComposition: String) {
        tracker.replace(rawComposition)
        updateFromTracker(preserveResolved = false)
    }

    fun updateFromTracker(preserveResolved: Boolean = model.hasResolvedSegments) {
        val unresolvedDigits = tracker.getCurrentSegment()
        val nextModel = if (preserveResolved) {
            val resolvedSource = model.resolvedSegments.joinToString("") { it.sourceDigits }
            val rawPreedit = if (
                resolvedSource.isNotEmpty() &&
                model.rawPreedit.startsWith("$resolvedSource'")
            ) {
                "$resolvedSource'${tracker.getFullComposition()}"
            } else {
                resolvedSource + tracker.getFullComposition()
            }
            model.copy(
                unresolvedDigits = unresolvedDigits,
                rawPreedit = rawPreedit,
                pendingSelection = model.pendingSelection?.takeIf {
                    it.remainingDigits == unresolvedDigits
                }
            )
        } else {
            T9CompositionModel(
                unresolvedDigits = unresolvedDigits,
                rawPreedit = tracker.getFullComposition()
            )
        }
        setModel(nextModel)
    }

    fun syncFromPreedit(rawPreedit: String) {
        if (rawPreedit.isEmpty()) {
            if (ignoreEmptyPreeditUntilReplay) return
            if (!tracker.isEmpty()) return
            if (observedNonEmptyPreedit && hasState()) {
                clear()
            }
            return
        }
        ignoreEmptyPreeditUntilReplay = false
        observedNonEmptyPreedit = true
        if (tracker.isEmpty() && !model.hasResolvedSegments && model.rawPreedit.isEmpty()) {
            setModel(model.copy(rawPreedit = rawPreedit))
        }
    }

    fun prepareReplay(rawPreedit: String) {
        ignoreEmptyPreeditUntilReplay = rawPreedit.isNotEmpty()
    }

    fun rawSequence(): String =
        if (model.rawPreedit.any { it in '2'..'9' || it == '\'' }) {
            model.rawPreedit.filter { it in '2'..'9' || it == '\'' }
        } else if (!tracker.isEmpty()) {
            tracker.getFullComposition()
        } else {
            model.resolvedSegments.joinToString("") { it.sourceDigits } + model.unresolvedDigits
        }

    fun digitSequence(): String = rawSequence().filter { it in '2'..'9' }

    fun keyCount(): Int = rawSequence().count { it in '2'..'9' }

    fun fullComposition(): String = tracker.getFullComposition()

    fun selectableSegment(): String = tracker.getSelectableSegment()

    fun currentSegment(
        firstUnresolvedRawSegment: (raw: String, resolved: List<T9ResolvedSegment>) -> String
    ): String =
        if (model.rawPreedit.contains('\'')) {
            firstUnresolvedRawSegment(rawSequence(), model.resolvedSegments)
        } else if (model.hasResolvedSegments) {
            firstUnresolvedRawSegment(rawSequence(), model.resolvedSegments)
                .ifEmpty { model.unresolvedDigits }
        } else {
            tracker.getSelectableSegment()
        }

    fun shouldReopenLastResolvedSegment(): Boolean = model.resolvedSegments.isNotEmpty()

    fun popLastResolvedSegment(): PopResolvedSegmentResult? {
        val last = model.resolvedSegments.lastOrNull() ?: return null
        val newResolved = model.resolvedSegments.dropLast(1)
        val previousUnresolved = model.unresolvedDigits
        val restoredUnresolved = last.sourceDigits + model.unresolvedDigits
        val restoredRawPreedit = model.rawPreedit
            .takeIf { it.contains('\'') }
            ?: newResolved.joinToString("") { it.sourceDigits } + restoredUnresolved
        tracker.replace(restoredRawPreedit)
        setModel(
            T9CompositionModel(
                resolvedSegments = newResolved,
                unresolvedDigits = restoredUnresolved,
                rawPreedit = restoredRawPreedit,
                pendingSelection = null,
            )
        )
        return PopResolvedSegmentResult(
            segment = last,
            previousUnresolved = previousUnresolved,
            fallbackRawPreedit = restoredRawPreedit
        )
    }

    fun markAllResolvedSegmentsEngineUnbacked(rawPreedit: String) {
        setModel(
            model.copy(
                resolvedSegments = model.resolvedSegments.map { it.copy(engineBacked = false) },
                rawPreedit = rawPreedit
            )
        )
    }

    fun selectPinyin(pinyin: String): PinyinSelectionRequest? {
        val segment = currentSegment(::defaultFirstUnresolvedRawSegment)
        if (segment.isEmpty() || pinyin.isEmpty()) return null
        val matchedPrefixLength = T9PinyinUtils.matchedPrefixLength(segment, pinyin)
        if (matchedPrefixLength <= 0) return null
        val selectedDigits = segment.take(matchedPrefixLength)
        val remainingDigits = segment.drop(matchedPrefixLength)
        val selectedSegment = T9ResolvedSegment(pinyin, selectedDigits)
        val rawBeforeSelection = rawSequence()
        val hadManualSeparator = !model.hasResolvedSegments && tracker.hasManualSeparator()
        if (hadManualSeparator) {
            tracker.replaceSelectableSegmentThroughFirstSeparator(remainingDigits)
        } else {
            tracker.replaceCurrentSegment(remainingDigits)
        }
        val unresolvedDigits = tracker.getCurrentSegment()
        val newResolved = model.resolvedSegments + selectedSegment
        setModel(
            model.copy(
                resolvedSegments = newResolved,
                unresolvedDigits = unresolvedDigits,
                rawPreedit = rawBeforeSelection.takeIf { hadManualSeparator }
                    ?: newResolved.joinToString("") { it.sourceDigits } + unresolvedDigits,
                pendingSelection = T9PendingSelection(selectedSegment, unresolvedDigits)
            )
        )
        return PinyinSelectionRequest(
            selectedSegment = selectedSegment,
            originalSegment = segment,
            remainingDigits = unresolvedDigits,
            consumeExplicitSeparator = hadManualSeparator,
            replaceFromStart = hadManualSeparator
        )
    }

    fun markSelectionEngineBacked(
        selectedSegment: T9ResolvedSegment,
        remainingDigits: String
    ) {
        val resolved = model.resolvedSegments.toMutableList()
        val index = resolved.indexOfFirst {
            it.pinyin == selectedSegment.pinyin &&
                it.sourceDigits == selectedSegment.sourceDigits &&
                !it.engineBacked
        }
        if (index < 0) return
        resolved[index] = resolved[index].copy(engineBacked = true)
        val nextPending = model.pendingSelection?.takeUnless {
            it.segment.pinyin == selectedSegment.pinyin &&
                it.segment.sourceDigits == selectedSegment.sourceDigits &&
                it.remainingDigits == remainingDigits
        }
        setModel(
            model.copy(
                resolvedSegments = resolved,
                pendingSelection = nextPending
            )
        )
    }

    fun clearPendingSelection(
        selectedSegment: T9ResolvedSegment,
        remainingDigits: String
    ) {
        val pending = model.pendingSelection ?: return
        if (pending.segment.pinyin == selectedSegment.pinyin &&
            pending.segment.sourceDigits == selectedSegment.sourceDigits &&
            pending.remainingDigits == remainingDigits
        ) {
            setModel(model.copy(pendingSelection = null))
        }
    }

    fun consumeResolvedPrefix(
        prefix: String,
        removeResolvedPrefixFromRawSource: (raw: String, resolved: List<T9ResolvedSegment>) -> String,
        firstUnresolvedRawSegment: (raw: String, resolved: List<T9ResolvedSegment>) -> String
    ): String? {
        val resolved = model.resolvedSegments
        if (resolved.isEmpty()) return null
        var matchedCount = 0
        for (count in 1..resolved.size) {
            if (resolved.take(count).joinToString(" ") { it.pinyin } == prefix) {
                matchedCount = count
                break
            }
        }
        if (matchedCount <= 0) return null
        val consumedResolved = resolved.take(matchedCount)
        val remainingResolved = resolved.drop(matchedCount)
        val rawPreedit = removeResolvedPrefixFromRawSource(rawSequence(), consumedResolved)
        val remainingUnresolvedDigits = firstUnresolvedRawSegment(rawPreedit, remainingResolved)
        tracker.replace(rawPreedit)
        setModel(
            model.copy(
                resolvedSegments = remainingResolved,
                unresolvedDigits = remainingUnresolvedDigits,
                rawPreedit = rawPreedit,
                pendingSelection = null
            )
        )
        return rawPreedit
    }

    fun consumeSelectedCandidateReading(
        commentSegments: List<String>
    ): Boolean {
        if (model.hasResolvedSegments) return false
        val rawDigits = tracker.getFullComposition().filter { it in '2'..'9' }
        if (rawDigits.isEmpty() || commentSegments.isEmpty()) return false
        var consumedDigits = ""
        for (segment in commentSegments) {
            val segmentDigits = T9PinyinUtils.pinyinToT9Keys(segment)
            if (segmentDigits.isEmpty()) break
            val nextConsumed = consumedDigits + segmentDigits
            if (!rawDigits.startsWith(nextConsumed)) break
            consumedDigits = nextConsumed
        }
        if (consumedDigits.isEmpty()) return false
        val remainingDigits = rawDigits.drop(consumedDigits.length)
        if (remainingDigits.isEmpty()) {
            clear()
        } else {
            tracker.replace(remainingDigits)
            setModel(
                T9CompositionModel(
                    unresolvedDigits = remainingDigits,
                    rawPreedit = remainingDigits
                )
            )
        }
        return true
    }

    private fun setModel(next: T9CompositionModel) {
        if (model == next) return
        model = next
        revision += 1
    }

    private fun defaultFirstUnresolvedRawSegment(
        raw: String,
        resolved: List<T9ResolvedSegment>
    ): String {
        var remaining = raw
        resolved.forEach { segment ->
            if (remaining.startsWith(segment.sourceDigits)) {
                remaining = remaining.drop(segment.sourceDigits.length)
            }
        }
        return remaining.split('\'').firstOrNull { part ->
            part.any { it in '2'..'9' }
        }.orEmpty().filter { it in '2'..'9' }
    }
}
