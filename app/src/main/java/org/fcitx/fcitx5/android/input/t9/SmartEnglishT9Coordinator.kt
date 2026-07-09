/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText

class SmartEnglishT9Coordinator(
    private val controller: SmartEnglishT9Controller,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val isEnabled: () -> Boolean,
    private val isActive: () -> Boolean,
    private val resetPendingDigit: () -> Unit,
    private val refreshUi: () -> Unit
) {
    constructor(
        candidateLimit: Int,
        noMatchText: String,
        scope: CoroutineScope,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
        isEnabled: () -> Boolean,
        isActive: () -> Boolean,
        shouldLearnWords: () -> Boolean,
        commitText: (String) -> Unit,
        refreshUi: () -> Unit,
        resetPendingDigit: () -> Unit
    ) : this(
        controller = SmartEnglishT9Controller(
            candidateLimit = candidateLimit,
            noMatchText = noMatchText,
            isActive = isActive,
            shouldLearnWords = shouldLearnWords,
            commitText = commitText,
            refreshUi = refreshUi
        ),
        scope = scope,
        ioDispatcher = ioDispatcher,
        mainDispatcher = mainDispatcher,
        isEnabled = isEnabled,
        isActive = isActive,
        resetPendingDigit = resetPendingDigit,
        refreshUi = refreshUi
    )

    private var warmupJob: Job? = null

    val hasDigits: Boolean
        get() = controller.hasDigits

    val hasCandidates: Boolean
        get() = controller.hasCandidates

    val caseLabel: String
        get() = controller.caseLabel

    val isDictionaryReady: Boolean
        get() = controller.isDictionaryReady

    fun warmupIfEnabled() {
        if (isEnabled()) warmup()
    }

    fun onEnabledChanged(enabled: Boolean) {
        reset()
        if (enabled) warmup()
    }

    fun onModeEntered() {
        warmupIfEnabled()
    }

    fun warmup() {
        if (controller.isDictionaryReady) return
        if (warmupJob?.isActive == true) return
        warmupJob = scope.launch(ioDispatcher) {
            controller.preloadDictionary()
            withContext(mainDispatcher) {
                warmupJob = null
                if (isActive() && controller.hasDigits) {
                    refreshUi()
                }
            }
        }
    }

    fun appendDigit(digit: Int) {
        warmup()
        controller.appendDigit(digit)
    }

    fun reset() {
        resetPendingDigit()
        controller.reset()
    }

    fun paged(): FcitxEvent.PagedCandidateEvent.Data? =
        controller.paged()

    fun presentation(formatText: (String) -> FormattedText?): T9PresentationState? =
        controller.presentation(formatText)

    fun moveCandidate(delta: Int): Boolean =
        controller.moveCandidate(delta)

    fun setCandidateIndex(index: Int): Boolean =
        controller.setCandidateIndex(index)

    fun commitCandidate(
        index: Int? = null,
        appendSpace: Boolean = true,
        continuePrediction: Boolean = appendSpace
    ): Boolean {
        val committed = controller.commitCandidate(index, appendSpace, continuePrediction)
        if (committed) {
            resetPendingDigit()
        }
        return committed
    }

    fun backspace(): Boolean =
        controller.backspace()

    fun applyCase(char: Char): Char =
        controller.applyCase(char)

    fun consumeShiftOnce() =
        controller.consumeShiftOnce()

    fun cycleCase(): String =
        controller.cycleCase()

    fun recordLearningChar(char: Char) =
        controller.recordLearningChar(char)

    fun flushLearningWord() =
        controller.flushLearningWord()
}
