/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import org.fcitx.fcitx5.android.utils.isTypeNull

class InputDeviceManager(private val onChange: (Boolean) -> Unit) {

    private var inputView: InputView? = null
    private var candidatesView: CandidatesView? = null

    private fun setupInputViewEvents() {
        val iv = inputView ?: return
        if (isDialerField) {
            iv.handleEvents = false
            iv.visibility = View.GONE
            return
        }
        iv.handleEvents = true
        iv.visibility = View.VISIBLE
    }

    private fun setupCandidatesViewEvents() {
        val cv = candidatesView ?: return
        if (isDialerField) {
            cv.handleEvents = false
            cv.visibility = View.GONE
            return
        }
        cv.handleEvents = true
    }

    private fun setupViewEvents() {
        setupInputViewEvents()
        setupCandidatesViewEvents()
    }

    var isVirtualKeyboard = true
        private set(value) {
            if (field == value) {
                return
            }
            field = value
            setupViewEvents()
            // fire change AFTER updating InputView(s),
            // make the view(s) ready for incoming events during `onChange`
            onChange(value)
        }

    fun setInputView(inputView: InputView) {
        this.inputView = inputView
        setupInputViewEvents()
    }

    fun setCandidatesView(candidatesView: CandidatesView) {
        this.candidatesView = candidatesView
        setupCandidatesViewEvents()
    }

    private var startedInputView = false
    private var startedInputSession = false
    private var isNullInputType = true
    private var isDialerField = false

    /** True when user is in an input field. Used for T9 key remapping. */
    val isInInputMode: Boolean get() = startedInputSession || startedInputView
    val isPassthroughInput: Boolean get() = isDialerField
    val shouldPassThroughHardwareKeys: Boolean
        get() = isDialerField || (!isInInputMode && isNullInputType)

    fun notifyOnStartInput(attribute: EditorInfo) {
        isNullInputType = attribute.isTypeNull()
        isDialerField = isPhoneDialer(attribute)
        startedInputSession = !isDialerField && !isNullInputType
        setupViewEvents()
    }

    /**
     * @return should use virtual keyboard
     */
    fun evaluateOnStartInputView(info: EditorInfo, service: FcitxInputMethodService): Boolean {
        // Dialer fields handle input themselves (numeric keys, *, #), so
        // pass through all key events without intercepting them. The inputType
        // is not always TYPE_CLASS_PHONE on all devices, so match by package name.
        isDialerField = isPhoneDialer(info)
        if (isDialerField) {
            startedInputView = false
            startedInputSession = false
            isNullInputType = info.isTypeNull()
            setupViewEvents()
            service.requestHideSelf(0)
            return false
        }
        startedInputView = true
        startedInputSession = true
        isNullInputType = info.isTypeNull()
        isVirtualKeyboard = false
        return isVirtualKeyboard
    }

    private fun isPhoneDialer(info: EditorInfo): Boolean {
        return info.packageName.endsWith(".dialer") &&
            (info.inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT
    }

    /**
     * @return should force show input views on hardware key down
     */
    fun evaluateOnKeyDown(e: KeyEvent, service: FcitxInputMethodService): Boolean {
        // Never force-show the input view for dialer fields; they handle
        // all key events themselves.
        if (isDialerField) return false
        if (startedInputView) {
            // filter out back/home/volume buttons and combination keys
            if (e.unicodeChar != 0) {
                // evaluate virtual keyboard visibility when pressing physical keyboard while InputView visible
                evaluateOnKeyDownInner(service)
            }
            // no need to force show InputView since it's already visible
            return false
        } else {
            // force show InputView when focusing on text input (likely inputType is not TYPE_NULL)
            // and pressing any digit/letter/punctuation key on physical keyboard
            val showInputView = !isNullInputType && e.unicodeChar != 0
            if (showInputView) {
                evaluateOnKeyDownInner(service)
            }
            return showInputView
        }
    }

    private fun evaluateOnKeyDownInner(service: FcitxInputMethodService) {
        isVirtualKeyboard = false
    }

    fun evaluateOnViewClicked(service: FcitxInputMethodService) {
        if (!startedInputView) return
    }

    fun evaluateOnUpdateEditorToolType(toolType: Int, service: FcitxInputMethodService) {
        if (!startedInputView) return
    }

    /**
     * Should be called when input method switched **by user**
     * @return should force show inputView for [CandidatesView] when input method switched by user
     */
    fun evaluateOnInputMethodSwitch(): Boolean {
        return !isVirtualKeyboard && !startedInputView
    }

    fun onFinishInputView() {
        startedInputView = false
        setupViewEvents()
    }

    fun onFinishInput() {
        startedInputSession = false
        isDialerField = false
        setupViewEvents()
    }
}
