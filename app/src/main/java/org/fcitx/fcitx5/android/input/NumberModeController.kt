/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.view.KeyEvent
import java.util.Locale

class NumberModeController(
    private val commitText: (String) -> Unit,
    private val getTextBeforeCursor: () -> String?,
    private val showOperatorHints: () -> Unit,
    private val hideOperatorHints: () -> Unit,
    private val showEqualsChoice: (prefix: String, result: String) -> Unit,
    private val hideEqualsChoice: () -> Unit
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

    val hasTransientPanel: Boolean
        get() = transientPanel != TransientPanel.NONE

    fun operatorForKey(keyCode: Int): String? = operatorMap[keyCode]

    fun showOperatorHintPanel() {
        dismissTransientPanel()
        transientPanel = TransientPanel.OPERATOR_HINT
        showOperatorHints()
    }

    fun dismissTransientPanel() {
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
        if (operator != "=" && operator != "≈") {
            commitText(operator)
            return true
        }
        val result = evaluateExpressionBeforeCursor(approximate = operator == "≈")
        commitText(operator)
        if (result != null) {
            pendingEqualsResult = result
            transientPanel = TransientPanel.RESULT_CHOICE
            showEqualsChoice(operator, result)
        }
        return true
    }

    fun handleTransientPanelKeyDown(keyCode: Int, event: KeyEvent): KeyResult {
        if (transientPanel == TransientPanel.NONE || event.action != KeyEvent.ACTION_DOWN) {
            return KeyResult(handled = false)
        }
        if (event.repeatCount > 0) return KeyResult(handled = true)
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
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            val operator = operatorMap[keyCode] ?: return KeyResult(handled = true)
            commitOperator(operator)
            return KeyResult(handled = true, consumedKeyUp = keyCode)
        }
        if (keyCode == KeyEvent.KEYCODE_STAR) {
            commitOperator("*")
            return KeyResult(handled = true, consumedKeyUp = keyCode)
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_POUND,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_BACK -> {
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
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                commitEqualsResult()
                KeyResult(handled = true, consumedKeyUp = keyCode)
            }
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_BACK -> {
                dismissTransientPanel()
                KeyResult(handled = true, consumedKeyUp = keyCode)
            }
            else -> {
                dismissTransientPanel()
                KeyResult(handled = false)
            }
        }
    }

    private fun evaluateExpressionBeforeCursor(approximate: Boolean = false): String? {
        val before = getTextBeforeCursor() ?: return null
        val expression = before.takeLastWhile {
            it.isDigit() || it in setOf('.', '+', '-', '*', '/', '%', '(', ')', ' ', 'π')
        }.trim()
        if (expression.isBlank() || expression.none { it.isDigit() || it == 'π' }) return null
        return runCatching {
            val value = NumberExpressionParser(expression).parse()
            if (approximate) formatApproximateNumberResult(value) else formatNumberResult(value)
        }.getOrNull()
    }

    private fun formatNumberResult(value: Double): String {
        if (!value.isFinite()) return value.toString()
        val long = value.toLong()
        return if (value == long.toDouble()) long.toString() else {
            String.format(Locale.US, "%.10f", value)
                .trimEnd('0')
                .trimEnd('.')
        }
    }

    private fun formatApproximateNumberResult(value: Double): String {
        if (!value.isFinite()) return value.toString()
        val long = value.toLong()
        return if (value == long.toDouble()) long.toString() else {
            String.format(Locale.US, "%.2f", value)
                .trimEnd('0')
                .trimEnd('.')
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
                skipSpaces()
                value = when {
                    match('*') -> value * parseFactor()
                    match('/') -> value / parseFactor()
                    match('%') -> value % parseFactor()
                    startsFactor() -> value * parseFactor()
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
            skipSpaces()
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
