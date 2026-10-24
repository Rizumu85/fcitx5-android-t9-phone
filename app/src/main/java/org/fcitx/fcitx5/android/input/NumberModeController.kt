/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.view.KeyEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.fcitx.fcitx5.android.input.t9.PhysicalT9KeyPolicy
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class NumberModeController(
    private val scope: CoroutineScope,
    private val commitText: (String) -> Unit,
    private val getTextBeforeCursor: () -> String?,
    private val showOperatorHints: () -> Unit,
    private val hideOperatorHints: () -> Unit,
    private val showEqualsChoice: (prefix: String, result: String) -> Unit,
    private val hideEqualsChoice: () -> Unit,
    private val editorDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val evaluationDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    private enum class TransientPanel {
        NONE,
        OPERATOR_HINT,
        RESULT_CHOICE
    }

    data class KeyResult(
        val handled: Boolean,
        val consumedKeyUp: Int? = null
    )

    private companion object {
        const val MaxPlainIntegerDigits = 20
    }

    private val operatorMap = mapOf(
        KeyEvent.KEYCODE_1 to "-",
        KeyEvent.KEYCODE_2 to "+",
        KeyEvent.KEYCODE_3 to "=",
        KeyEvent.KEYCODE_4 to "π",
        KeyEvent.KEYCODE_5 to "/",
        KeyEvent.KEYCODE_6 to "≈",
        KeyEvent.KEYCODE_7 to "(",
        KeyEvent.KEYCODE_8 to "%",
        KeyEvent.KEYCODE_9 to ")",
        KeyEvent.KEYCODE_0 to "."
    )

    private var transientPanel = TransientPanel.NONE
    private var pendingEqualsResult: String? = null
    private var evaluationGeneration = 0L
    private var evaluationJob: Job? = null

    val hasTransientPanel: Boolean
        get() = transientPanel != TransientPanel.NONE

    fun operatorForKey(keyCode: Int): String? = operatorMap[keyCode]

    fun showOperatorHintPanel() {
        dismissTransientPanel()
        transientPanel = TransientPanel.OPERATOR_HINT
        showOperatorHints()
    }

    fun dismissTransientPanel() {
        invalidatePendingEvaluation()
        val previousPanel = transientPanel
        transientPanel = TransientPanel.NONE
        pendingEqualsResult = null
        when (previousPanel) {
            TransientPanel.OPERATOR_HINT -> hideOperatorHints()
            TransientPanel.RESULT_CHOICE -> hideEqualsChoice()
            TransientPanel.NONE -> Unit
        }
    }

    fun commitOperator(operator: String): Boolean {
        dismissTransientPanel()
        commitText(operator)
        if (operator == "=" || operator == "≈") {
            requestExpressionEvaluation(operator)
        }
        return true
    }

    fun invalidatePendingEvaluation() {
        evaluationGeneration += 1
        evaluationJob?.cancel()
        evaluationJob = null
    }

    fun handleTransientPanelKeyDown(
        keyCode: Int,
        action: Int,
        repeatCount: Int
    ): KeyResult {
        if (transientPanel == TransientPanel.NONE || action != KeyEvent.ACTION_DOWN) {
            return KeyResult(handled = false)
        }
        if (repeatCount > 0) return KeyResult(handled = true)
        return when (transientPanel) {
            TransientPanel.OPERATOR_HINT -> handleOperatorHintPanelKeyDown(keyCode)
            TransientPanel.RESULT_CHOICE -> handleResultChoiceKeyDown(keyCode)
            TransientPanel.NONE -> KeyResult(handled = false)
        }
    }

    private fun commitEqualsResult() {
        val result = pendingEqualsResult ?: return dismissTransientPanel()
        dismissTransientPanel()
        commitText(result)
    }

    private fun handleOperatorHintPanelKeyDown(keyCode: Int): KeyResult {
        if (PhysicalT9KeyPolicy.t9Digit(keyCode) != null) {
            val operator = operatorMap[keyCode] ?: return KeyResult(handled = true)
            commitOperator(operator)
            return KeyResult(handled = true, consumedKeyUp = keyCode)
        }
        if (keyCode == KeyEvent.KEYCODE_STAR) {
            commitOperator("*")
            return KeyResult(handled = true, consumedKeyUp = keyCode)
        }
        return when {
            keyCode == KeyEvent.KEYCODE_POUND ||
                PhysicalT9KeyPolicy.isConfirmKey(keyCode) ||
                PhysicalT9KeyPolicy.isDeleteKey(keyCode) -> {
                dismissTransientPanel()
                KeyResult(handled = true, consumedKeyUp = keyCode)
            }
            else -> {
                dismissTransientPanel()
                KeyResult(handled = false)
            }
        }
    }

    private fun handleResultChoiceKeyDown(keyCode: Int): KeyResult {
        return when {
            PhysicalT9KeyPolicy.isConfirmKey(keyCode) -> {
                commitEqualsResult()
                KeyResult(handled = true, consumedKeyUp = keyCode)
            }
            PhysicalT9KeyPolicy.isDeleteKey(keyCode) -> {
                dismissTransientPanel()
                KeyResult(handled = true, consumedKeyUp = keyCode)
            }
            else -> {
                dismissTransientPanel()
                KeyResult(handled = false)
            }
        }
    }

    private fun requestExpressionEvaluation(operator: String) {
        val ticket = ++evaluationGeneration
        evaluationJob = scope.launch(editorDispatcher) {
            try {
                // The operator must finish its editor effect before any read/parser work can delay input.
                yield()
                if (ticket != evaluationGeneration) return@launch
                val before = getTextBeforeCursor() ?: return@launch
                val result = withContext(evaluationDispatcher) {
                    evaluateExpressionBeforeCursor(
                        before = before,
                        committedOperator = operator,
                        approximate = operator == "≈"
                    )
                }
                if (ticket != evaluationGeneration || result == null) return@launch
                pendingEqualsResult = result
                transientPanel = TransientPanel.RESULT_CHOICE
                showEqualsChoice(operator, result)
            } finally {
                if (ticket == evaluationGeneration) evaluationJob = null
            }
        }
    }

    private fun evaluateExpressionBeforeCursor(
        before: String,
        committedOperator: String,
        approximate: Boolean
    ): String? {
        val textBeforeOperator = if (before.endsWith(committedOperator)) {
            before.dropLast(committedOperator.length)
        } else {
            before
        }
        val expression = textBeforeOperator.takeLastWhile {
            isExpressionChar(it)
        }.trim()
        if (expression.isBlank() || expression.none { it.isDigit() || it == 'π' }) return null
        // Text such as "Tel 138 1234+1" ends in an expression preceded by unrelated numbers. Try the
        // longest whitespace-delimited suffix that parses instead of multiplying across the gap.
        return expressionSuffixes(expression).firstNotNullOfOrNull { candidate ->
            runCatching {
                val value = NumberExpressionParser(candidate).parse()
                // Division by zero must not offer "Infinity" or "NaN" as a committable result.
                check(value.isFinite())
                if (approximate) formatApproximateNumberResult(value) else formatNumberResult(value)
            }.getOrNull()
        }
    }

    private fun expressionSuffixes(expression: String): Sequence<String> =
        sequenceOf(expression) + expression.indices.asSequence()
            .filter { expression[it].isWhitespace() }
            .map { expression.substring(it + 1).trim() }
            .filter { suffix -> suffix.any { it.isDigit() || it == 'π' } }

    private fun isExpressionChar(char: Char): Boolean = char.isDigit() || when (char) {
        '.', '+', '-', '*', '/', '%', '(', ')', ' ', 'π' -> true
        else -> false
    }

    // Twelve significant digits hide binary noise (0.1+0.2, 1e23) without rounding small results
    // such as 0.000000000012 down to zero the way a fixed decimal count would.
    private fun formatNumberResult(value: Double): String =
        plainDecimal(BigDecimal.valueOf(value).round(MathContext(12, RoundingMode.HALF_UP)))

    private fun formatApproximateNumberResult(value: Double): String =
        plainDecimal(BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP))

    private fun plainDecimal(value: BigDecimal): String {
        if (value.signum() == 0) return "0"
        val stripped = value.stripTrailingZeros()
        // Plain notation would print hundreds of digits for huge results.
        return if (stripped.precision() - stripped.scale() > MaxPlainIntegerDigits) {
            stripped.toString()
        } else {
            stripped.toPlainString()
        }
    }

    private class NumberExpressionParser(private val text: String) {
        private var index = 0

        fun parse(): Double {
            val value = parseExpression()
            skipSpaces()
            check(index == text.length)
            return value
        }

        private fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipSpaces()
                value = when {
                    match('+') -> value + parseTerm()
                    match('-') -> value - parseTerm()
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                // Implicit multiplication such as 2(3) or 2π needs adjacency; "138 1234" is two
                // separate numbers, not a product.
                val adjacent = startsFactor()
                skipSpaces()
                value = when {
                    match('*') -> value * parseFactor()
                    match('/') -> value / parseFactor()
                    match('%') -> value % parseFactor()
                    adjacent -> value * parseFactor()
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            skipSpaces()
            if (match('+')) return parseFactor()
            if (match('-')) return -parseFactor()
            if (match('(')) {
                val value = parseExpression()
                check(match(')'))
                return value
            }
            if (match('π')) return Math.PI
            return parseNumber()
        }

        private fun startsFactor(): Boolean {
            if (index >= text.length) return false
            return text[index].isDigit() || text[index] == '.' || text[index] == '(' || text[index] == 'π'
        }

        private fun parseNumber(): Double {
            skipSpaces()
            val start = index
            while (index < text.length && (text[index].isDigit() || text[index] == '.')) {
                index += 1
            }
            check(index > start)
            return text.substring(start, index).toDouble()
        }

        private fun match(char: Char): Boolean {
            skipSpaces()
            if (index >= text.length || text[index] != char) return false
            index += 1
            return true
        }

        private fun skipSpaces() {
            while (index < text.length && text[index].isWhitespace()) {
                index += 1
            }
        }
    }
}
