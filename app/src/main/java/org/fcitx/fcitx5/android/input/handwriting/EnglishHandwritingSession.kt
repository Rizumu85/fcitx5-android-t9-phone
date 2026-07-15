/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import org.fcitx.fcitx5.android.input.t9.EnglishSuggestionEngine

internal class EnglishHandwritingSession(
    private val candidateLimit: Int,
    private val rerankRecognitions: (List<String>, List<String>, Int) -> List<String>,
    private val predictionsAfter: (List<String>, Int) -> List<String>,
    private val learnWord: (String) -> Unit,
    private val learnPair: (String, String) -> Unit
) {
    constructor(
        suggestionEngine: EnglishSuggestionEngine,
        candidateLimit: Int
    ) : this(
        candidateLimit = candidateLimit,
        rerankRecognitions = suggestionEngine::rerankRecognitions,
        predictionsAfter = suggestionEngine::predictionsAfter,
        learnWord = suggestionEngine::learnWord,
        learnPair = suggestionEngine::learnPair
    )

    private val recentWords = ArrayDeque<String>()
    private var preContext = ""

    fun begin(editorPreContext: String) {
        preContext = editorPreContext.takeLast(MaximumPreContextLength)
        rebuildRecentWords()
    }

    fun clear() {
        recentWords.clear()
        preContext = ""
    }

    fun recognitionPreContext(): String = preContext

    fun rerank(candidates: List<String>, suggestionsEnabled: Boolean): List<String> =
        if (suggestionsEnabled) {
            rerankRecognitions(candidates, recentWords.toList(), candidateLimit)
        } else {
            candidates.take(candidateLimit)
        }

    fun commitWord(
        rawWord: String,
        emittedText: String,
        suggestionsEnabled: Boolean,
        shouldLearn: Boolean,
        continuePrediction: Boolean,
        learnWord: Boolean
    ): List<String> {
        val word = EnglishSuggestionEngine.normalizeContextWord(rawWord)
        val previous = recentWords.lastOrNull()
        if (suggestionsEnabled && shouldLearn) {
            // Recognition teaches new spellings; selecting an existing prediction only teaches
            // the pair so dictionary management does not fill with already-known suggestions.
            if (learnWord) this.learnWord(rawWord)
            if (previous != null && word != null) learnPair(previous, word)
        }
        appendCommittedText(emittedText)
        return if (suggestionsEnabled && continuePrediction && word != null) {
            predictionsAfter(recentWords.toList(), candidateLimit)
        } else {
            emptyList()
        }
    }

    fun commitLiteral(text: String) {
        appendCommittedText(text)
    }

    fun breakContext() {
        recentWords.clear()
        preContext = ""
    }

    private fun appendCommittedText(text: String) {
        preContext = (preContext + text).takeLast(MaximumPreContextLength)
        rebuildRecentWords()
    }

    private fun rebuildRecentWords() {
        recentWords.clear()
        EnglishToken.findAll(preContext)
            .mapNotNull { match -> EnglishSuggestionEngine.normalizeContextWord(match.value) }
            .toList()
            .takeLast(ContextWordLimit)
            .forEach(recentWords::addLast)
    }

    private companion object {
        val EnglishToken = Regex("[A-Za-z]+")
        const val ContextWordLimit = 3
        const val MaximumPreContextLength = 20
    }
}
