/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesMode
import org.fcitx.fcitx5.android.utils.isTypeNull

class InputDeviceManager(private val onChange: (Boolean) -> Unit) {

    private var inputView: InputView? = null
    private var candidatesView: CandidatesView? = null

    private val prefs = AppPrefs.getInstance()

    @Volatile
    private var t9InputModeEnabled = prefs.keyboard.useT9KeyboardLayout.getValue()

    @Volatile
    private var candidatesViewMode = prefs.candidates.mode.getValue()

    private val t9InputModeEnabledChangeListener =
        ManagedPreference.OnChangeListener<Boolean> { _, value ->
            t9InputModeEnabled = value
        }

    private val candidatesViewModeChangeListener =
        ManagedPreference.OnChangeListener<FloatingCandidatesMode> { _, value ->
            candidatesViewMode = value
        }

    init {
        prefs.keyboard.useT9KeyboardLayout.registerOnChangeListener(
            t9InputModeEnabledChangeListener
        )
        prefs.candidates.mode.registerOnChangeListener(candidatesViewModeChangeListener)
    }

    private fun setupInputViewEvents(isVirtual: Boolean) {
        val iv = inputView ?: return
        if (isDialerField) {
            iv.handleEvents = false
            iv.visibility = View.GONE
            return
        }
        // Enable InputView interaction when using virtual keyboard
        // OR when T9 input mode is enabled for physical keyboard users.
        val enableInputView = isVirtual || t9InputModeEnabled
        iv.handleEvents = enableInputView
        iv.visibility = if (enableInputView) View.VISIBLE else View.GONE
    }

    private fun setupCandidatesViewEvents(isVirtual: Boolean) {
        val cv = candidatesView ?: return
        if (isDialerField) {
            cv.handleEvents = false
            cv.visibility = View.GONE
            return
        }
        val useFloatingCandidates = !isVirtual || t9InputModeEnabled
        cv.handleEvents = useFloatingCandidates
        // hide CandidatesView when entering virtual keyboard mode,
        // but preserve the visibility when entering physical keyboard mode (in case it's empty)
        if (!useFloatingCandidates) {
            cv.visibility = View.GONE
        }
    }

    private fun setupViewEvents(isVirtual: Boolean) {
        setupInputViewEvents(isVirtual)
        setupCandidatesViewEvents(isVirtual)
    }

    var isVirtualKeyboard = true
        private set(value) {
            if (field == value) {
                return
            }
            field = value
            setupViewEvents(value)
            // fire change AFTER updating InputView(s),
            // make the view(s) ready for incoming events during `onChange`
            onChange(value)
        }

    fun setInputView(inputView: InputView) {
        this.inputView = inputView
        setupInputViewEvents(this.isVirtualKeyboard)
    }

    fun setCandidatesView(candidatesView: CandidatesView) {
        this.candidatesView = candidatesView
        setupCandidatesViewEvents(this.isVirtualKeyboard)
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
        setupViewEvents(isVirtualKeyboard)
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
            setupViewEvents(isVirtualKeyboard)
            service.requestHideSelf(0)
            return false
        }
        startedInputView = true
        startedInputSession = true
        isNullInputType = info.isTypeNull()
        isVirtualKeyboard = if (t9InputModeEnabled) false else when (candidatesViewMode) {
            FloatingCandidatesMode.SystemDefault -> service.superEvaluateInputViewShown()
            FloatingCandidatesMode.InputDevice -> isVirtualKeyboard
            FloatingCandidatesMode.Disabled -> true
        }
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
        isVirtualKeyboard = if (t9InputModeEnabled) false else when (candidatesViewMode) {
            FloatingCandidatesMode.SystemDefault -> service.superEvaluateInputViewShown()
            FloatingCandidatesMode.InputDevice -> false
            FloatingCandidatesMode.Disabled -> true
        }
    }

    fun evaluateOnViewClicked(service: FcitxInputMethodService) {
        if (!startedInputView) return
        if (t9InputModeEnabled) return
        isVirtualKeyboard = when (candidatesViewMode) {
            FloatingCandidatesMode.SystemDefault -> service.superEvaluateInputViewShown()
            else -> true
        }
    }

    fun evaluateOnUpdateEditorToolType(toolType: Int, service: FcitxInputMethodService) {
        if (!startedInputView) return
        if (t9InputModeEnabled) return
        isVirtualKeyboard = when (candidatesViewMode) {
            FloatingCandidatesMode.SystemDefault -> service.superEvaluateInputViewShown()
            FloatingCandidatesMode.InputDevice ->
                // switch to virtual keyboard on touch screen events, otherwise preserve current mode
                if (toolType == MotionEvent.TOOL_TYPE_FINGER || toolType == MotionEvent.TOOL_TYPE_STYLUS) true else isVirtualKeyboard
            FloatingCandidatesMode.Disabled -> true
        }
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
        setupViewEvents(isVirtualKeyboard)
    }

    fun onFinishInput() {
        startedInputSession = false
        isDialerField = false
        setupViewEvents(isVirtualKeyboard)
    }
}
