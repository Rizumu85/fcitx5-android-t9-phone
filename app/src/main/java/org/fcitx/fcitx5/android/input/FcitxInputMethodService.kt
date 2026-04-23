/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.util.LruCache
import android.util.Size
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.ImageViewStyle
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.TextFormatFlag
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.core.ScancodeMapping
import org.fcitx.fcitx5.android.core.SubtypeManager
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.cursor.CursorRange
import org.fcitx.fcitx5.android.input.cursor.CursorTracker
import org.fcitx.fcitx5.android.input.t9.T9CompositionTracker
import org.fcitx.fcitx5.android.input.t9.T9PinyinUtils
import org.fcitx.fcitx5.android.utils.InputMethodUtil
import org.fcitx.fcitx5.android.utils.alpha
import org.fcitx.fcitx5.android.utils.forceShowSelf
import org.fcitx.fcitx5.android.utils.inputMethodManager
import org.fcitx.fcitx5.android.utils.isTypeNull
import org.fcitx.fcitx5.android.utils.monitorCursorAnchor
import org.fcitx.fcitx5.android.utils.styledFloat
import org.fcitx.fcitx5.android.utils.withBatchEdit
import splitties.bitflags.hasFlag
import splitties.dimensions.dp
import splitties.resources.styledColor
import timber.log.Timber
import kotlin.math.max

class FcitxInputMethodService : LifecycleInputMethodService() {

    private lateinit var fcitx: FcitxConnection

    private var jobs = Channel<Job>(capacity = Channel.UNLIMITED)

    private val cachedKeyEvents = LruCache<Int, KeyEvent>(78)
    private var cachedKeyEventIndex = 0

    /**
     * Saves MetaState produced by hardware keyboard with "sticky" modifier keys, to clear them in order.
     * See also [InputConnection#clearMetaKeyStates(int)](https://developer.android.com/reference/android/view/inputmethod/InputConnection#clearMetaKeyStates(int))
     */
    private var lastMetaState: Int = 0

    private lateinit var pkgNameCache: PackageNameCache

    private lateinit var decorView: View
    private lateinit var contentView: FrameLayout
    private var inputView: InputView? = null
    private var candidatesView: CandidatesView? = null

    private val navbarMgr = NavigationBarManager()
    private val inputDeviceMgr = InputDeviceManager { isVirtualKeyboard ->
        postFcitxJob {
            setCandidatePagingMode(if (isVirtualKeyboard) 0 else 1)
        }
        currentInputConnection?.monitorCursorAnchor(!isVirtualKeyboard)
        window.window?.let {
            navbarMgr.evaluate(it, isVirtualKeyboard)
        }
    }

    private var capabilityFlags = CapabilityFlags.DefaultFlags

    private val selection = CursorTracker()

    val currentInputSelection: CursorRange
        get() = selection.latest

    private val composing = CursorRange()
    private var composingText = FormattedText.Empty

    private fun clearT9CompositionState() {
        t9CompositionTracker.clear()
    }

    fun handleVirtualT9Backspace() {
        if (useT9KeyboardLayout && currentT9Mode == T9InputMode.CHINESE) {
            t9CompositionTracker.backspace()
        }
    }

    private fun resetComposingState() {
        composing.clear()
        composingText = FormattedText.Empty
        clearT9CompositionState()
    }

    private var cursorUpdateIndex: Int = 0

    private var highlightColor: Int = 0x66008577 // material_deep_teal_500 with alpha 0.4

    private val prefs = AppPrefs.getInstance()
    private val keyboardPrefs = prefs.keyboard
    private val inlineSuggestions by keyboardPrefs.inlineSuggestions
    private val useT9KeyboardLayout by keyboardPrefs.useT9KeyboardLayout
    private val ignoreSystemCursor by prefs.advanced.ignoreSystemCursor

    private val recreateInputViewPrefs: Array<ManagedPreference<*>> = arrayOf(
        prefs.keyboard.expandKeypressArea,
        prefs.advanced.disableAnimation,
        prefs.advanced.ignoreSystemWindowInsets,
    )

    private fun replaceInputView(theme: Theme): InputView {
        val newInputView = InputView(this, fcitx, theme)
        setInputView(newInputView)
        inputDeviceMgr.setInputView(newInputView)
        inputView = newInputView
        return newInputView
    }

    private fun replaceCandidateView(theme: Theme): CandidatesView {
        val newCandidatesView = CandidatesView(this, fcitx, theme)
        // replace CandidatesView manually
        contentView.removeView(candidatesView)
        // put CandidatesView directly under content view
        contentView.addView(newCandidatesView)
        inputDeviceMgr.setCandidatesView(newCandidatesView)
        candidatesView = newCandidatesView
        return newCandidatesView
    }

    private fun replaceInputViews(theme: Theme) {
        navbarMgr.evaluate(window.window!!, inputDeviceMgr.isVirtualKeyboard)
        replaceInputView(theme)
        replaceCandidateView(theme)
    }

    @Keep
    private val recreateInputViewListener = ManagedPreference.OnChangeListener<Any> { _, _ ->
        replaceInputView(ThemeManager.activeTheme)
    }

    @Keep
    private val recreateCandidatesViewListener = ManagedPreferenceProvider.OnChangeListener {
        replaceCandidateView(ThemeManager.activeTheme)
    }

    @Keep
    private val onThemeChangeListener = ThemeManager.OnThemeChangeListener {
        replaceInputViews(it)
    }

    /**
     * Post a fcitx operation to [jobs] to be executed
     *
     * Unlike `fcitx.runOnReady` or `fcitx.launchOnReady` where
     * subsequent operations can start if the prior operation is not finished (suspended),
     * [postFcitxJob] ensures that operations are executed sequentially.
     */
    fun postFcitxJob(block: suspend FcitxAPI.() -> Unit): Job {
        val job = fcitx.lifecycleScope.launch(start = CoroutineStart.LAZY) {
            fcitx.runOnReady(block)
        }
        jobs.trySend(job)
        return job
    }

    override fun onCreate() {
        fcitx = FcitxDaemon.connect(javaClass.name)
        lifecycleScope.launch {
            jobs.consumeEach { it.join() }
        }
        lifecycleScope.launch {
            fcitx.runImmediately { eventFlow }.collect {
                handleFcitxEvent(it)
            }
        }
        pkgNameCache = PackageNameCache(this)
        recreateInputViewPrefs.forEach {
            it.registerOnChangeListener(recreateInputViewListener)
        }
        prefs.candidates.registerOnChangeListener(recreateCandidatesViewListener)
        ThemeManager.addOnChangedListener(onThemeChangeListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            postFcitxJob {
                SubtypeManager.syncWith(enabledIme())
            }
        }
        super.onCreate()
        decorView = window.window!!.decorView
        contentView = decorView.findViewById(android.R.id.content)
        lastKnownConfig = resources.configuration
    }

    private fun handleFcitxEvent(event: FcitxEvent<*>) {
        when (event) {
            is FcitxEvent.CommitStringEvent -> {
                clearT9CompositionState()
                commitText(event.data.text, event.data.cursor)
            }
            is FcitxEvent.KeyEvent -> event.data.let event@{
                if (it.states.virtual) {
                    // KeyEvent from virtual keyboard
                    when (it.sym.sym) {
                        FcitxKeyMapping.FcitxKey_BackSpace -> handleBackspaceKey()
                        FcitxKeyMapping.FcitxKey_Return -> handleReturnKey()
                        FcitxKeyMapping.FcitxKey_Left -> handleArrowKey(KeyEvent.KEYCODE_DPAD_LEFT)
                        FcitxKeyMapping.FcitxKey_Right -> handleArrowKey(KeyEvent.KEYCODE_DPAD_RIGHT)
                        else -> if (it.unicode > 0) {
                            commitText(Character.toString(it.unicode))
                        } else {
                            Timber.w("Unhandled Virtual KeyEvent: $it")
                        }
                    }
                } else {
                    // KeyEvent from physical keyboard (or input method engine forwardKey)
                    // use cached event if available
                    cachedKeyEvents.remove(it.timestamp)?.let { keyEvent ->
                        /**
                         * intercept the KeyEvent which would cause the default [android.text.method.QwertyKeyListener]
                         * to show a Gingerbread-style CharacterPickerDialog
                         */
                        if (keyEvent.unicodeChar == KeyCharacterMap.PICKER_DIALOG_INPUT.code) {
                            currentInputConnection?.sendKeyEvent(
                                KeyEvent(
                                    keyEvent.downTime, keyEvent.eventTime,
                                    keyEvent.action, keyEvent.keyCode,
                                    keyEvent.repeatCount, keyEvent.metaState, -1,
                                    keyEvent.scanCode, keyEvent.flags, keyEvent.source
                                )
                            )
                            return@event
                        }
                        currentInputConnection?.sendKeyEvent(keyEvent)
                        if (KeyEvent.isModifierKey(keyEvent.keyCode)) {
                            when (keyEvent.action) {
                                KeyEvent.ACTION_DOWN -> {
                                    // save current metaState when modifier key down
                                    lastMetaState = keyEvent.metaState
                                }
                                KeyEvent.ACTION_UP -> {
                                    // only clear metaState that would be missing when this modifier key up
                                    currentInputConnection?.clearMetaKeyStates(lastMetaState xor keyEvent.metaState)
                                    lastMetaState = keyEvent.metaState
                                }
                            }
                        }
                        return@event
                    }
                    // simulate key event
                    val keyCode = it.sym.keyCode
                    if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                        // recognized keyCode
                        val eventTime = SystemClock.uptimeMillis()
                        if (it.up) {
                            sendUpKeyEvent(eventTime, keyCode, it.states.metaState)
                        } else {
                            sendDownKeyEvent(eventTime, keyCode, it.states.metaState)
                        }
                    } else {
                        // no matching keyCode, commit character once on key down
                        if (!it.up && it.unicode > 0) {
                            commitText(Character.toString(it.unicode))
                        } else {
                            Timber.w("Unhandled Fcitx KeyEvent: $it")
                        }
                    }
                }
            }
            is FcitxEvent.ClientPreeditEvent -> {
                updateComposingText(event.data)
            }
            is FcitxEvent.DeleteSurroundingEvent -> {
                val (before, after) = event.data
                handleDeleteSurrounding(before, after)
            }
            is FcitxEvent.IMChangeEvent -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val im = event.data.uniqueName
                    val subtype = SubtypeManager.subtypeOf(im) ?: return
                    skipNextSubtypeChange = im
                    // [^1]: notify system that input method subtype has changed
                    switchInputMethod(InputMethodUtil.componentName, subtype)
                }
            }
            is FcitxEvent.SwitchInputMethodEvent -> {
                val (reason) = event.data
                if (reason != FcitxEvent.SwitchInputMethodEvent.Reason.CapabilityChanged &&
                    reason != FcitxEvent.SwitchInputMethodEvent.Reason.Other
                ) {
                    if (inputDeviceMgr.evaluateOnInputMethodSwitch()) {
                        // show inputView for [CandidatesView] when input method switched by user
                        forceShowSelf()
                    }
                }
            }
            else -> {}
        }
    }

    private fun handleDeleteSurrounding(before: Int, after: Int) {
        val ic = currentInputConnection ?: return
        if (before > 0) {
            selection.predictOffset(-before)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ic.deleteSurroundingTextInCodePoints(before, after)
        } else {
            ic.deleteSurroundingText(before, after)
        }
    }

    private fun handleBackspaceKey() {
        if (useT9KeyboardLayout && currentT9Mode == T9InputMode.CHINESE) {
            t9CompositionTracker.backspace()
        }
        val lastSelection = selection.latest
        if (lastSelection.isNotEmpty()) {
            selection.predict(lastSelection.start)
        } else if (lastSelection.start > 0) {
            selection.predictOffset(-1)
        }
        // In practice nobody (apart form ourselves) would set `privateImeOptions` to our
        // `DeleteSurroundingFlag`, leading to a behavior of simulating backspace key pressing
        // in almost every EditText.
        if (currentInputEditorInfo.privateImeOptions != DeleteSurroundingFlag ||
            currentInputEditorInfo.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL
        ) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        if (lastSelection.isEmpty()) {
            if (lastSelection.start <= 0) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                currentInputConnection.deleteSurroundingTextInCodePoints(1, 0)
            } else {
                currentInputConnection.deleteSurroundingText(1, 0)
            }
        } else {
            currentInputConnection.commitText("", 0)
        }
    }

    private fun handleReturnKey() {
        currentInputEditorInfo.run {
            if (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL ||
                imeOptions.hasFlag(EditorInfo.IME_FLAG_NO_ENTER_ACTION)
            ) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                return
            }
            if (actionLabel?.isNotEmpty() == true && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
                currentInputConnection.performEditorAction(actionId)
                return
            }
            when (val action = imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_UNSPECIFIED,
                EditorInfo.IME_ACTION_NONE -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                else -> currentInputConnection.performEditorAction(action)
            }
        }
    }

    private fun handleArrowKey(keyCode: Int) {
        if (currentInputEditorInfo.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL) {
            sendDownUpKeyEvents(keyCode)
            return
        }
        val (start, end) = currentInputSelection
        val offset = if (start == end) 1 else 0
        val target = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> start - offset
            KeyEvent.KEYCODE_DPAD_RIGHT -> end + offset
            else -> return
        }
        currentInputConnection.setSelection(target, target)
    }

    fun commitText(text: String, cursor: Int = -1) {
        val ic = currentInputConnection ?: return
        // when composing text equals commit content, finish composing text as-is
        if (composing.isNotEmpty() && composingText.toString() == text) {
            val c = if (cursor == -1) text.length else cursor
            val target = composing.start + c
            resetComposingState()
            ic.withBatchEdit {
                if (selection.current.start != target) {
                    selection.predict(target)
                    ic.setSelection(target, target)
                }
                ic.finishComposingText()
            }
            return
        }
        // committed text should replace composing (if any), replace selected range (if any),
        // or simply prepend before cursor
        val start = if (composing.isEmpty()) selection.latest.start else composing.start
        resetComposingState()
        if (cursor == -1) {
            selection.predict(start + text.length)
            ic.commitText(text, 1)
        } else {
            val target = start + cursor
            selection.predict(target)
            ic.withBatchEdit {
                commitText(text, 1)
                setSelection(target, target)
            }
        }
    }

    private fun sendDownKeyEvent(eventTime: Long, keyEventCode: Int, metaState: Int = 0) {
        currentInputConnection?.sendKeyEvent(
            KeyEvent(
                eventTime,
                eventTime,
                KeyEvent.ACTION_DOWN,
                keyEventCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                ScancodeMapping.keyCodeToScancode(keyEventCode),
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
    }

    private fun sendUpKeyEvent(eventTime: Long, keyEventCode: Int, metaState: Int = 0) {
        currentInputConnection?.sendKeyEvent(
            KeyEvent(
                eventTime,
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP,
                keyEventCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                ScancodeMapping.keyCodeToScancode(keyEventCode),
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
    }

    fun deleteSelection() {
        val lastSelection = selection.latest
        if (lastSelection.isEmpty()) return
        selection.predict(lastSelection.start)
        currentInputConnection?.commitText("", 1)
    }

    fun sendCombinationKeyEvents(
        keyEventCode: Int,
        alt: Boolean = false,
        ctrl: Boolean = false,
        shift: Boolean = false
    ) {
        var metaState = 0
        if (alt) metaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (ctrl) metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (shift) metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        val eventTime = SystemClock.uptimeMillis()
        if (alt) sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_ALT_LEFT)
        if (ctrl) sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_CTRL_LEFT)
        if (shift) sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT)
        sendDownKeyEvent(eventTime, keyEventCode, metaState)
        sendUpKeyEvent(eventTime, keyEventCode, metaState)
        if (shift) sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT)
        if (ctrl) sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_CTRL_LEFT)
        if (alt) sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_ALT_LEFT)
    }

    fun applySelectionOffset(offsetStart: Int, offsetEnd: Int = 0) {
        val lastSelection = selection.latest
        currentInputConnection?.also {
            val start = max(lastSelection.start + offsetStart, 0)
            val end = max(lastSelection.end + offsetEnd, 0)
            if (start > end) return
            selection.predict(start, end)
            it.setSelection(start, end)
        }
    }

    fun cancelSelection() {
        val lastSelection = selection.latest
        if (lastSelection.isEmpty()) return
        val end = lastSelection.end
        selection.predict(end)
        currentInputConnection?.setSelection(end, end)
    }

    private lateinit var lastKnownConfig: Configuration

    override fun onConfigurationChanged(newConfig: Configuration) {
        postFcitxJob { reset() }
        /**
         * skip keyboard|keyboardHidden changes, because we have [inputDeviceMgr]
         * skip uiMode (system light/dark mode) changes, because we have [onThemeChangeListener]
         * to replace InputView(s) when needed
         * [android.inputmethodservice.InputMethodService.onConfigurationChanged] would call
         * resetStateForNewConfiguration() which calls initViews() causes InputView(s) to be replaced again
         * https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r36/core/java/android/inputmethodservice/InputMethodService.java#1984
         */
        val f = ActivityInfo.CONFIG_KEYBOARD or
                ActivityInfo.CONFIG_KEYBOARD_HIDDEN or
                ActivityInfo.CONFIG_UI_MODE
        val diff = lastKnownConfig.diff(newConfig)
        Timber.d("onConfigurationChanged diff=$diff")
        /**
         * perform `super.onConfigurationChanged` only when `newConfig` diff fall outside "skipped" flags
         * we have to calculate the mask ourselves because nobody knows how `handledConfigChanges` works
         * https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r36/core/java/android/inputmethodservice/InputMethodService.java#1876
         */
        if (diff and f != diff) {
            super.onConfigurationChanged(newConfig)
        }
        lastKnownConfig = newConfig
    }

    override fun onWindowShown() {
        super.onWindowShown()
        try {
            highlightColor = styledColor(android.R.attr.colorAccent).alpha(0.4f)
        } catch (_: Exception) {
            Timber.w("Device does not support android.R.attr.colorAccent which it should have.")
        }
        InputFeedbacks.syncSystemPrefs()
    }

    override fun onCreateInputView(): View? {
        replaceInputViews(ThemeManager.activeTheme)
        // We will call `setInputView` by ourselves. This is fine.
        return null
    }

    override fun setInputView(view: View) {
        super.setInputView(view)
        // input method layout has not changed in 11 years:
        // https://android.googlesource.com/platform/frameworks/base/+/ae3349e1c34f7aceddc526cd11d9ac44951e97b6/core/res/res/layout/input_method.xml
        // expand inputArea to fullscreen
        contentView.findViewById<FrameLayout>(android.R.id.inputArea)
            .updateLayoutParams<ViewGroup.LayoutParams> {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
        /**
         * expand InputView to fullscreen, since [android.inputmethodservice.InputMethodService.setInputView]
         * would set InputView's height to [ViewGroup.LayoutParams.WRAP_CONTENT]
         */
        view.updateLayoutParams<ViewGroup.LayoutParams> {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private var inputViewLocation = intArrayOf(0, 0)

    override fun onComputeInsets(outInsets: Insets) {
        // When using virtual keyboard OR T9 layout (physical T9 phone with on-screen control bar),
        // make the area of keyboardView touchable so it can receive tap events.
        if (inputDeviceMgr.isVirtualKeyboard || useT9KeyboardLayout) {
            inputView?.keyboardView?.getLocationInWindow(inputViewLocation)
            outInsets.apply {
                contentTopInsets = inputViewLocation[1]
                visibleTopInsets = inputViewLocation[1]
                touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
            }
        } else {
            val n = decorView.findViewById<View>(android.R.id.navigationBarBackground)?.height ?: 0
            val h = decorView.height - n
            outInsets.apply {
                contentTopInsets = h
                visibleTopInsets = h
                touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
            }
        }
    }

    // always show InputView since we delegate CandidatesView's visibility to it
    @SuppressLint("MissingSuperCall")
    override fun onEvaluateInputViewShown() = true

    fun superEvaluateInputViewShown() = super.onEvaluateInputViewShown()

    override fun onEvaluateFullscreenMode() = false

    /**
     * Maps T9 physical keys for input mode only.
     * - DPAD_CENTER (confirm) -> SPACE (for candidate selection)
     * - KEYCODE_BACK (back) -> KEYCODE_DEL (backspace)
     * When NOT in input mode, pass through original events so normal phone usage works.
     */
    private fun mapKeyEvent(keyCode: Int, event: KeyEvent): Pair<Int, KeyEvent> {
        if (!inputDeviceMgr.isInInputMode) {
            return keyCode to event
        }
        val newKeyCode = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER -> KeyEvent.KEYCODE_SPACE
            KeyEvent.KEYCODE_BACK -> KeyEvent.KEYCODE_DEL
            else -> keyCode
        }
        return if (newKeyCode != keyCode) {
            newKeyCode to KeyEvent(
                event.downTime,
                event.eventTime,
                event.action,
                newKeyCode,
                event.repeatCount,
                event.metaState,
                event.deviceId,
                event.scanCode,
                event.flags,
                event.source
            )
        } else {
            keyCode to event
        }
    }

    // ==================== T9 Input State ====================

    /**
     * T9 input modes (switched by # long press)
     */
    private enum class T9InputMode {
        CHINESE,  // 中文九键
        ENGLISH,  // 英文九键 (multi-tap)
        NUMBER    // 数字模式
    }

    /**
     * Current T9 input mode
     */
    private var currentT9Mode = T9InputMode.CHINESE

    /**
     * T9 input states for determining key behavior within Chinese mode
     */
    private enum class T9InputState {
        CHINESE_IDLE,      // 中文未输入
        CHINESE_COMPOSING  // 中文已输入
    }

    private enum class T9EnglishCaseState {
        OFF,
        SHIFT_ONCE,
        CAPS
    }

    private var t9EnglishCaseState = T9EnglishCaseState.OFF

    /**
     * Track if a long press action was triggered for # key.
     * This prevents short press action from firing on KEY_UP after a long press.
     */
    private var t9PoundLongPressTriggered = false

    /**
     * Track long press for digit keys (0-9)
     */
    private val digitLongPressFlags = mutableMapOf<Int, Boolean>()

    // ===== Multi-tap state for English mode =====
    private var multiTapLastKey = -1
    private var multiTapLastTime = 0L
    private var multiTapIndex = 0
    private var multiTapPendingChar: Char? = null
    private val MULTITAP_TIMEOUT = 1200L  // Increased from 500ms for easier typing

    /** T9 composition tracker for pinyin selection bar (digit sequence for current segment). */
    private val t9CompositionTracker = T9CompositionTracker()

    private val multiTapMap = mapOf(
        KeyEvent.KEYCODE_1 to ",.?!'\"-@/:",  // English punctuation (comma first)
        KeyEvent.KEYCODE_2 to "abc",
        KeyEvent.KEYCODE_3 to "def",
        KeyEvent.KEYCODE_4 to "ghi",
        KeyEvent.KEYCODE_5 to "jkl",
        KeyEvent.KEYCODE_6 to "mno",
        KeyEvent.KEYCODE_7 to "pqrs",
        KeyEvent.KEYCODE_8 to "tuv",
        KeyEvent.KEYCODE_9 to "wxyz"
    )

    // Handler for multi-tap timeout
    private val multiTapHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val multiTapTimeoutRunnable = Runnable {
        if (multiTapPendingChar != null) {
            commitMultiTapChar()
        }
    }

    // Handler for mode indicator
    private val modeIndicatorHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var modeIndicatorShowing = false
    private val modeIndicatorDismissRunnable = Runnable {
        if (modeIndicatorShowing) {
            currentInputConnection?.setComposingText("", 1)
            currentInputConnection?.finishComposingText()
            modeIndicatorShowing = false
        }
    }

    /**
     * Show mode indicator briefly using composing text
     */
    private fun showModeIndicator(mode: String) {
        modeIndicatorHandler.removeCallbacks(modeIndicatorDismissRunnable)
        currentInputConnection?.setComposingText("[$mode]", 1)
        modeIndicatorShowing = true
        modeIndicatorHandler.postDelayed(modeIndicatorDismissRunnable, 600)
    }


    /**
     * Get current T9 input state based on composing text (only used in CHINESE mode)
     */
    private fun getT9InputState(): T9InputState {
        return if (composing.isNotEmpty()) {
            T9InputState.CHINESE_COMPOSING
        } else {
            T9InputState.CHINESE_IDLE
        }
    }

    private fun clearChineseT9CompositionFromEditorTap() {
        if (!useT9KeyboardLayout || currentT9Mode != T9InputMode.CHINESE) return
        if (getT9InputState() != T9InputState.CHINESE_COMPOSING && t9CompositionTracker.isEmpty()) {
            return
        }
        resetComposingState()
        candidatesView?.clearTransientState()
        inputView?.clearTransientState()
        currentInputConnection?.finishComposingText()
        postFcitxJob {
            focusOutIn()
        }
    }

    private fun applyEnglishCase(char: Char): Char = when (t9EnglishCaseState) {
        T9EnglishCaseState.OFF -> char
        T9EnglishCaseState.SHIFT_ONCE, T9EnglishCaseState.CAPS -> char.uppercaseChar()
    }

    private fun consumeEnglishShiftOnce() {
        if (t9EnglishCaseState == T9EnglishCaseState.SHIFT_ONCE) {
            t9EnglishCaseState = T9EnglishCaseState.OFF
        }
    }

    private fun handleEnglishStarShortPress() {
        t9EnglishCaseState = when (t9EnglishCaseState) {
            T9EnglishCaseState.OFF -> T9EnglishCaseState.SHIFT_ONCE
            T9EnglishCaseState.SHIFT_ONCE, T9EnglishCaseState.CAPS -> T9EnglishCaseState.OFF
        }
        showModeIndicator(
            when (t9EnglishCaseState) {
                T9EnglishCaseState.OFF -> "abc"
                T9EnglishCaseState.SHIFT_ONCE -> "Abc"
                T9EnglishCaseState.CAPS -> "ABC"
            }
        )
    }

    private fun handleEnglishStarLongPress() {
        t9EnglishCaseState = when (t9EnglishCaseState) {
            T9EnglishCaseState.OFF, T9EnglishCaseState.SHIFT_ONCE -> T9EnglishCaseState.CAPS
            T9EnglishCaseState.CAPS -> T9EnglishCaseState.OFF
        }
        showModeIndicator(
            when (t9EnglishCaseState) {
                T9EnglishCaseState.OFF -> "abc"
                T9EnglishCaseState.SHIFT_ONCE -> "Abc"
                T9EnglishCaseState.CAPS -> "ABC"
            }
        )
    }

    /**
     * Commit any pending multi-tap character
     * Returns true if there was a character to commit
     */
    private fun commitMultiTapChar(): Boolean {
        multiTapHandler.removeCallbacks(multiTapTimeoutRunnable)
        val hadPendingChar = multiTapPendingChar != null
        multiTapPendingChar?.let {
            val char = applyEnglishCase(it)
            // Just commit the text - setComposingText will be replaced by this
            currentInputConnection?.commitText(char.toString(), 1)
            multiTapPendingChar = null
            consumeEnglishShiftOnce()
        }
        multiTapLastKey = -1
        multiTapIndex = 0
        return hadPendingChar
    }

    /**
     * Cancel any pending multi-tap character without committing
     */
    private fun cancelMultiTapChar() {
        multiTapHandler.removeCallbacks(multiTapTimeoutRunnable)
        if (multiTapPendingChar != null) {
            // Clear composing text without committing by setting empty string
            currentInputConnection?.setComposingText("", 1)
            currentInputConnection?.finishComposingText()
            multiTapPendingChar = null
        }
        multiTapLastKey = -1
        multiTapIndex = 0
    }

    /**
     * Reset all multi-tap state (used when switching modes)
     */
    private fun resetMultiTapState() {
        multiTapHandler.removeCallbacks(multiTapTimeoutRunnable)
        if (multiTapPendingChar != null) {
            currentInputConnection?.finishComposingText()
        }
        multiTapPendingChar = null
        multiTapLastKey = -1
        multiTapLastTime = 0L
        multiTapIndex = 0
    }

    /**
     * Handle multi-tap key press in English mode
     */
    private fun handleMultiTapKey(keyCode: Int): Boolean {
        val letters = multiTapMap[keyCode] ?: return false
        val currentTime = android.os.SystemClock.elapsedRealtime()

        // Cancel any pending timeout
        multiTapHandler.removeCallbacks(multiTapTimeoutRunnable)

        if (keyCode == multiTapLastKey && currentTime - multiTapLastTime < MULTITAP_TIMEOUT) {
            // Same key pressed within timeout - cycle to next letter
            multiTapIndex = (multiTapIndex + 1) % letters.length
        } else {
            // Different key or timeout expired - commit previous and start new
            commitMultiTapChar()
            multiTapIndex = 0
        }

        multiTapLastKey = keyCode
        multiTapLastTime = currentTime
        multiTapPendingChar = letters[multiTapIndex]

        val displayChar = applyEnglishCase(multiTapPendingChar!!)
        currentInputConnection?.setComposingText(displayChar.toString(), 1)

        // Schedule auto-commit after timeout
        multiTapHandler.postDelayed(multiTapTimeoutRunnable, MULTITAP_TIMEOUT)

        return true
    }

    /**
     * Callback invoked when T9 mode changes (for syncing space bar display).
     * Set by InputView when the input scope is created.
     */
    var onT9ModeChanged: ((String) -> Unit)? = null

    /**
     * Returns the current T9 mode label for display (e.g. "中", "En", "123").
     * Used by T9 keyboard to show the correct label on the space bar.
     */
    fun getCurrentT9ModeLabel(): String = when (currentT9Mode) {
        T9InputMode.CHINESE -> "中"
        T9InputMode.ENGLISH -> "En"
        T9InputMode.NUMBER -> "123"
    }

    /**
     * Switch to next T9 input mode
     */
    fun switchToNextT9Mode() {
        // Clear any pending multi-tap state before switching
        resetMultiTapState()
        
        currentT9Mode = when (currentT9Mode) {
            T9InputMode.CHINESE -> T9InputMode.ENGLISH
            T9InputMode.ENGLISH -> T9InputMode.NUMBER
            T9InputMode.NUMBER -> T9InputMode.CHINESE
        }
        
        val modeName = getCurrentT9ModeLabel()
        showModeIndicator(modeName)
        onT9ModeChanged?.invoke(modeName)
    }

    /**
     * Handle T9 special keys with state-aware behavior.
     * Returns true if the key was handled, false to pass through to default handling.
     * 
     * Arrow keys are NOT handled here - they are passed through to Rime/system:
     * - When composing: Rime's key_binder handles candidate navigation
     * - When not composing: System handles cursor movement
     */
    private fun handleT9SpecialKey(keyCode: Int, event: KeyEvent): Boolean {
        if (!inputDeviceMgr.isInInputMode) return false
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val chineseState = getT9InputState()

        return when (keyCode) {
            // # key: short press = enter (+ commit pending char), long press = switch input mode
            KeyEvent.KEYCODE_POUND -> {
                if (currentT9Mode == T9InputMode.CHINESE && chineseState == T9InputState.CHINESE_COMPOSING) {
                    false // Pass through to Rime when composing Chinese
                } else {
                    if (event.repeatCount == 0) {
                        t9PoundLongPressTriggered = false
                        true
                    } else if (event.repeatCount == 1) {
                        t9PoundLongPressTriggered = true
                        switchToNextT9Mode()
                        true
                    } else {
                        true
                    }
                }
            }

            // 0-9 keys: long press outputs digit
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                handleDigitKey(keyCode, event, chineseState)
            }

            // * key: English mode - short press = shift, long press = caps lock
            KeyEvent.KEYCODE_STAR -> {
                when (currentT9Mode) {
                    T9InputMode.ENGLISH -> {
                        if (event.repeatCount == 0) {
                            digitLongPressFlags[keyCode] = false
                            true
                        } else if (event.repeatCount == 1) {
                            digitLongPressFlags[keyCode] = true
                            handleEnglishStarLongPress()
                            true
                        } else {
                            true
                        }
                    }
                    T9InputMode.CHINESE -> {
                        if (chineseState == T9InputState.CHINESE_COMPOSING) {
                            false // Pass through - Rime handles * in composing
                        } else {
                            false // Pass through for now
                        }
                    }
                    T9InputMode.NUMBER -> false // * passes through in number mode
                }
            }

            // Confirm key: commit pending multi-tap char in English mode
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (currentT9Mode == T9InputMode.ENGLISH && multiTapPendingChar != null) {
                    if (event.repeatCount == 0) {
                        commitMultiTapChar()
                        true
                    } else {
                        true
                    }
                } else {
                    false // Pass through
                }
            }

            else -> false
        }
    }

    /**
     * Handle digit key (0-9) based on current mode
     */
    private fun handleDigitKey(keyCode: Int, event: KeyEvent, chineseState: T9InputState): Boolean {
        val digit = keyCode - KeyEvent.KEYCODE_0

        when (currentT9Mode) {
            T9InputMode.NUMBER -> {
                // Number mode: always output digit directly
                if (event.repeatCount == 0) {
                    currentInputConnection?.commitText(digit.toString(), 1)
                    return true
                }
                return true
            }

            T9InputMode.ENGLISH -> {
                // English mode: short press = multi-tap, long press = digit
                if (keyCode in KeyEvent.KEYCODE_2..KeyEvent.KEYCODE_9) {
                    if (event.repeatCount == 0) {
                        digitLongPressFlags[keyCode] = false
                        return handleMultiTapKey(keyCode)
                    } else if (event.repeatCount == 1) {
                        // Long press: cancel the pending letter and output digit
                        digitLongPressFlags[keyCode] = true
                        cancelMultiTapChar()
                        currentInputConnection?.commitText(digit.toString(), 1)
                        return true
                    }
                    return true
                } else if (keyCode == KeyEvent.KEYCODE_0) {
                    // 0 key: short press = space, long press = 0
                    if (event.repeatCount == 0) {
                        digitLongPressFlags[keyCode] = false
                        return true
                    } else if (event.repeatCount == 1) {
                        digitLongPressFlags[keyCode] = true
                        cancelMultiTapChar()
                        currentInputConnection?.commitText("0", 1)
                        return true
                    }
                    return true
                } else if (keyCode == KeyEvent.KEYCODE_1) {
                    // 1 key: short press = punctuation multi-tap, long press = 1
                    if (event.repeatCount == 0) {
                        digitLongPressFlags[keyCode] = false
                        return handleMultiTapKey(keyCode)  // Use multi-tap for English punctuation
                    } else if (event.repeatCount == 1) {
                        digitLongPressFlags[keyCode] = true
                        cancelMultiTapChar()
                        currentInputConnection?.commitText("1", 1)
                        return true
                    }
                    return true
                }
                return false
            }

            T9InputMode.CHINESE -> {
                // Chinese mode: short press = Rime T9, long press = digit
                if (chineseState == T9InputState.CHINESE_COMPOSING) {
                    // When composing, pass all digits to Rime
                    return false
                }
                // Not composing: long press outputs digit
                if (keyCode == KeyEvent.KEYCODE_0) {
                    // 0 key special: short press = space, long press = 0
                    if (event.repeatCount == 0) {
                        digitLongPressFlags[keyCode] = false
                        return true
                    } else if (event.repeatCount == 1) {
                        digitLongPressFlags[keyCode] = true
                        currentInputConnection?.commitText("0", 1)
                        return true
                    }
                    return true
                } else {
                    // 1-9 keys: short press = Rime, long press = digit
                    if (event.repeatCount == 0) {
                        digitLongPressFlags[keyCode] = false
                        return false // Pass to Rime for T9 input
                    } else if (event.repeatCount == 1) {
                        digitLongPressFlags[keyCode] = true
                        currentInputConnection?.commitText(digit.toString(), 1)
                        return true
                    }
                    return true
                }
            }
        }
    }

    private fun forwardKeyEvent(event: KeyEvent): Boolean {
        // reason to use a self increment index rather than timestamp:
        // KeyUp and KeyDown events actually can happen on the same time
        val timestamp = cachedKeyEventIndex++
        cachedKeyEvents.put(timestamp, event)
        val sym = KeySym.fromKeyEvent(event)
        if (sym != null) {
            if (useT9KeyboardLayout && currentT9Mode == T9InputMode.CHINESE && event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DEL -> t9CompositionTracker.backspace()
                    KeyEvent.KEYCODE_1 -> t9CompositionTracker.appendApostrophe()
                    in KeyEvent.KEYCODE_2..KeyEvent.KEYCODE_9 ->
                        t9CompositionTracker.appendDigit('0' + (event.keyCode - KeyEvent.KEYCODE_0))
                }
            }
            val states = KeyStates.fromKeyEvent(event)
            val up = event.action == KeyEvent.ACTION_UP
            postFcitxJob {
                sendKey(sym, states, event.scanCode, up, timestamp)
            }
            return true
        }
        Timber.d("Skipped KeyEvent: $event")
        return false
    }

    // ==================== T9 Pinyin selection bar ====================

    /** Current T9 segment (digits 2-9 after last apostrophe) for pinyin candidates. */
    fun getCurrentT9Segment(): String = t9CompositionTracker.getCurrentSegment()

    /** Keep the Kotlin-side T9 tracker in sync with Rime's composing state. */
    fun syncT9CompositionWithInputPanel(data: FcitxEvent.InputPanelEvent.Data) {
        if (!useT9KeyboardLayout || currentT9Mode != T9InputMode.CHINESE) {
            return
        }
        val rawPreedit = data.preedit.toString()
        if (rawPreedit.isNotEmpty() && rawPreedit.all { it in '2'..'9' || it == '\'' }) {
            if (rawPreedit != t9CompositionTracker.getFullComposition()) {
                t9CompositionTracker.replace(rawPreedit)
            }
            return
        }
        if (data.preedit.isEmpty() && !t9CompositionTracker.isEmpty()) {
            clearT9CompositionState()
        }
    }

    /** Total number of digit keys (2-9) in current composition, for truncating first-row pinyin. */
    fun getT9CompositionKeyCount(): Int =
        t9CompositionTracker.getFullComposition().count { it in '2'..'9' }

    /** Candidate pinyin list for current T9 segment; empty if segment empty or not T9. */
    fun getT9PinyinCandidates(): List<String> =
        if (useT9KeyboardLayout && currentT9Mode == T9InputMode.CHINESE)
            T9PinyinUtils.t9KeyToPinyin(getCurrentT9Segment())
        else
            emptyList()

    private fun buildT9PreeditDisplay(raw: String): FormattedText? {
        if (raw.isEmpty()) return null
        val segments = raw.split('\'')
        val pinyinSegments = segments.map { seg ->
            val digits = seg.filter { c -> c in '2'..'9' }
            if (digits.isEmpty()) seg
            else T9PinyinUtils.t9KeyToPinyin(digits).firstOrNull() ?: seg
        }
        val display = pinyinSegments.joinToString("'")
        if (display.isEmpty()) return null
        return FormattedText(arrayOf(display), intArrayOf(TextFormatFlag.NoFlag.flag), -1)
    }

    /** Preedit as pinyin with apostrophes for first row; null if not T9 Chinese or empty. */
    fun getT9PreeditDisplay(rawComposition: String? = null): FormattedText? {
        if (!useT9KeyboardLayout || currentT9Mode != T9InputMode.CHINESE) return null
        val raw = rawComposition ?: t9CompositionTracker.getFullComposition()
        return buildT9PreeditDisplay(raw)
    }

    /** Replace current T9 segment with selected pinyin (backspace + type pinyin). */
    fun selectT9Pinyin(pinyin: String) {
        val segment = getCurrentT9Segment()
        if (segment.isEmpty() || pinyin.isEmpty()) return
        val backspaceCount = t9CompositionTracker.getCurrentSegmentKeyLength()
        if (backspaceCount <= 0) return
        t9CompositionTracker.removeCurrentSegment()
        postFcitxJob {
            val k = KeyStates.Virtual
            for (_i in 0 until backspaceCount) {
                sendKey(KeySym(FcitxKeyMapping.FcitxKey_BackSpace), k, up = false)
                sendKey(KeySym(FcitxKeyMapping.FcitxKey_BackSpace), k, up = true)
            }
            for (c in pinyin) {
                sendKey(c, k.states, up = false)
                sendKey(c, k.states, up = true)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Handle T9 special keys first
        if (handleT9SpecialKey(keyCode, event)) {
            return true
        }

        // In English T9 mode, if there is a pending multi-tap character,
        // the first backspace should only cancel that pending char (and its composing)
        // instead of also deleting the previous committed character.
        if (currentT9Mode == T9InputMode.ENGLISH &&
            event.action == KeyEvent.ACTION_DOWN &&
            multiTapPendingChar != null &&
            (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_BACK)
        ) {
            cancelMultiTapChar()
            return true
        }

        val (mappedKeyCode, mappedEvent) = mapKeyEvent(keyCode, event)
        // request to show floating CandidatesView when pressing physical keyboard
        if (inputDeviceMgr.evaluateOnKeyDown(mappedEvent, this)) {
            postFcitxJob {
                focus(true)
            }
            forceShowSelf()
        }
        return forwardKeyEvent(mappedEvent) || super.onKeyDown(mappedKeyCode, mappedEvent)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // For T9 special keys that were handled in onKeyDown, consume the key up too
        if (handleT9SpecialKeyUp(keyCode, event)) {
            return true
        }

        val (mappedKeyCode, mappedEvent) = mapKeyEvent(keyCode, event)
        return forwardKeyEvent(mappedEvent) || super.onKeyUp(mappedKeyCode, mappedEvent)
    }

    /**
     * Handle key up events for T9 special keys.
     * Short press actions are executed here after confirming it wasn't a long press.
     */
    private fun handleT9SpecialKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!inputDeviceMgr.isInInputMode) return false
        val chineseState = getT9InputState()

        return when (keyCode) {
            // # key: short press = confirm pending char OR enter (if no pending)
            KeyEvent.KEYCODE_POUND -> {
                if (currentT9Mode == T9InputMode.CHINESE && chineseState == T9InputState.CHINESE_COMPOSING) {
                    false
                } else {
                    if (!t9PoundLongPressTriggered) {
                        // If there was a pending char, just confirm it (no enter)
                        // If no pending char, perform enter/return (respects IME action: search, go, etc.)
                        val hadPendingChar = commitMultiTapChar()
                        if (!hadPendingChar) {
                            handleReturnKey()
                        }
                    }
                    t9PoundLongPressTriggered = false
                    true
                }
            }

            // 0 key: short press = space (+ commit pending multi-tap char)
            KeyEvent.KEYCODE_0 -> {
                if (currentT9Mode == T9InputMode.CHINESE && chineseState == T9InputState.CHINESE_COMPOSING) {
                    false
                } else if (currentT9Mode == T9InputMode.NUMBER) {
                    true // Already handled in KEY_DOWN
                } else {
                    if (digitLongPressFlags[keyCode] != true) {
                        commitMultiTapChar()
                        currentInputConnection?.commitText(" ", 1)
                    }
                    digitLongPressFlags[keyCode] = false
                    true
                }
            }

            // 2-9 keys: in English mode, commit composing text on key up if it's not a long press
            in KeyEvent.KEYCODE_2..KeyEvent.KEYCODE_9 -> {
                when (currentT9Mode) {
                    T9InputMode.ENGLISH -> {
                        // Multi-tap handled in KEY_DOWN, just consume KEY_UP
                        // Don't commit here - wait for timeout or different key
                        true
                    }
                    T9InputMode.CHINESE -> {
                        if (chineseState == T9InputState.CHINESE_COMPOSING) {
                            false // Pass to Rime
                        } else if (digitLongPressFlags[keyCode] == true) {
                            digitLongPressFlags[keyCode] = false
                            true // Long press already handled
                        } else {
                            false // Short press passes to Rime
                        }
                    }
                    T9InputMode.NUMBER -> true // Already handled in KEY_DOWN
                }
            }

            // 1 key
            KeyEvent.KEYCODE_1 -> {
                when (currentT9Mode) {
                    T9InputMode.ENGLISH -> {
                        // Multi-tap handled in KEY_DOWN, just consume KEY_UP
                        true
                    }
                    else -> {
                        if (digitLongPressFlags[keyCode] == true) {
                            digitLongPressFlags[keyCode] = false
                            true
                        } else {
                            false // Short press passes to Rime for punctuation
                        }
                    }
                }
            }

            // * key: short press = shift in English mode
            KeyEvent.KEYCODE_STAR -> {
                when (currentT9Mode) {
                    T9InputMode.ENGLISH -> {
                        if (digitLongPressFlags[keyCode] != true) {
                            handleEnglishStarShortPress()
                        }
                        digitLongPressFlags[keyCode] = false
                        true
                    }
                    else -> false
                }
            }

            // Confirm key: already handled in KEY_DOWN
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (currentT9Mode == T9InputMode.ENGLISH) {
                    true // Consume to prevent double action
                } else {
                    false
                }
            }

            else -> false
        }
    }

    // Added in API level 14, deprecated in 29
    // it's needed because editors still use it even on API 36
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onViewClicked(focusChanged: Boolean) {
        super.onViewClicked(focusChanged)
        clearChineseT9CompositionFromEditorTap()
        inputDeviceMgr.evaluateOnViewClicked(this)
    }

    @RequiresApi(34)
    override fun onUpdateEditorToolType(toolType: Int) {
        super.onUpdateEditorToolType(toolType)
        inputDeviceMgr.evaluateOnUpdateEditorToolType(toolType, this)
    }

    private var firstBindInput = true

    override fun onBindInput() {
        val uid = currentInputBinding.uid
        val pkgName = pkgNameCache.forUid(uid)
        Timber.d("onBindInput: uid=$uid pkg=$pkgName")
        postFcitxJob {
            // ensure InputContext has been created before focusing it
            activate(uid, pkgName)
        }
        if (firstBindInput) {
            firstBindInput = false
            // only use input method from subtype for the first `onBindInput`, because
            // 1. fcitx has `ShareInputState` option, thus reading input method from subtype
            //    everytime would ruin `ShareInputState=Program`
            // 2. im from subtype should be read once, when user changes input method from other
            //    app to a subtype of ours via system input method picker (on 34+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val subtype = inputMethodManager.currentInputMethodSubtype ?: return
                val im = SubtypeManager.inputMethodOf(subtype)
                postFcitxJob {
                    activateIme(im)
                }
            }
        }
    }

    /**
     * When input method changes internally (eg. via language switch key or keyboard shortcut),
     * we want to notify system that subtype has changed (see [^1]), then ignore the incoming
     * [onCurrentInputMethodSubtypeChanged] callback.
     * Input method should only be changed when user changes subtype in system input method picker
     * manually.
     */
    private var skipNextSubtypeChange: String? = null

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val im = SubtypeManager.inputMethodOf(newSubtype)
            Timber.d("onCurrentInputMethodSubtypeChanged: im=$im")
            // don't change input method if this "subtype change" was our notify to system
            // see [^1]
            if (skipNextSubtypeChange == im) {
                skipNextSubtypeChange = null
                return
            }
            postFcitxJob {
                activateIme(im)
            }
        }
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        // update selection as soon as possible
        // sometimes when restarting input, onUpdateSelection happens before onStartInput, and
        // initialSel{Start,End} is outdated. but it's the client app's responsibility to send
        // right cursor position, try to workaround this would simply introduce more bugs.
        selection.resetTo(attribute.initialSelStart, attribute.initialSelEnd)
        resetComposingState()
        val flags = CapabilityFlags.fromEditorInfo(attribute)
        capabilityFlags = flags
        // EditorInfo may change between onStartInput and onStartInputView
        inputDeviceMgr.notifyOnStartInput(attribute)
        Timber.d("onStartInput: initialSel=${selection.current}, restarting=$restarting")
        val isNullType = attribute.isTypeNull()
        // wait until InputContext created/activated
        postFcitxJob {
            if (restarting) {
                // when input restarts in the same editor, focus out to clear previous state
                focus(false)
                // try focus out before changing CapabilityFlags,
                // to avoid confusing state of different text fields
            }
            // EditorInfo can be different in onStartInput and onStartInputView,
            // especially in browsers
            setCapFlags(flags)
            // for hardware keyboard, focus to allow switching input methods before onStartInputView
            if (!isNullType) {
                focus(true)
            }
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        Timber.d("onStartInputView: restarting=$restarting")
        postFcitxJob {
            focus(true)
        }
        if (inputDeviceMgr.evaluateOnStartInputView(info, this)) {
            // because onStartInputView will always be called after onStartInput,
            // editorInfo and capFlags should be up-to-date
            inputView?.startInput(info, capabilityFlags, restarting)
        } else {
            if (currentInputConnection?.monitorCursorAnchor() != true) {
                if (!decorLocationUpdated) {
                    updateDecorLocation()
                }
                // anchor CandidatesView to bottom-left corner in case InputConnection does not
                // support monitoring CursorAnchorInfo
                workaroundNullCursorAnchorInfo()
            }
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        // onUpdateSelection can left behind when user types quickly enough, eg. long press backspace
        cursorUpdateIndex += 1
        handleCursorUpdate(newSelStart, newSelEnd, cursorUpdateIndex)
        inputView?.updateSelection(newSelStart, newSelEnd)
    }

    private val contentSize = floatArrayOf(0f, 0f)
    private val decorLocation = floatArrayOf(0f, 0f)
    private val decorLocationInt = intArrayOf(0, 0)
    private var decorLocationUpdated = false

    private fun updateDecorLocation() {
        contentSize[0] = contentView.width.toFloat()
        contentSize[1] = contentView.height.toFloat()
        decorView.getLocationOnScreen(decorLocationInt)
        decorLocation[0] = decorLocationInt[0].toFloat()
        decorLocation[1] = decorLocationInt[1].toFloat()
        // contentSize and decorLocation can be completely wrong,
        // when measuring right after the very first onStartInputView() of an IMS' lifecycle
        if (contentSize[0] > 0 && contentSize[1] > 0) {
            decorLocationUpdated = true
        }
    }

    private val anchorPosition = floatArrayOf(0f, 0f, 0f, 0f)

    /**
     * anchor candidates view to bottom-left corner, only works if [decorLocationUpdated]
     */
    private fun workaroundNullCursorAnchorInfo() {
        anchorPosition[0] = 0f
        anchorPosition[1] = contentSize[1]
        anchorPosition[2] = 0f
        anchorPosition[3] = contentSize[1]
        candidatesView?.updateCursorAnchor(anchorPosition, contentSize)
    }

    override fun onUpdateCursorAnchorInfo(info: CursorAnchorInfo) {
        val bounds = info.getCharacterBounds(0)
        if (bounds != null) {
            // anchor to start of composing span instead of insertion mark if available
            val horizontal =
                if (candidatesView?.layoutDirection == View.LAYOUT_DIRECTION_RTL) bounds.right else bounds.left
            anchorPosition[0] = horizontal
            anchorPosition[1] = bounds.bottom
            anchorPosition[2] = horizontal
            anchorPosition[3] = bounds.top
        } else {
            anchorPosition[0] = info.insertionMarkerHorizontal
            anchorPosition[1] = info.insertionMarkerBottom
            anchorPosition[2] = info.insertionMarkerHorizontal
            anchorPosition[3] = info.insertionMarkerTop
        }
        // avoid calling `decorView.getLocationOnScreen` repeatedly
        if (!decorLocationUpdated) {
            updateDecorLocation()
        }
        if (anchorPosition.any(Float::isNaN)) {
            // anchor candidates view to bottom-left corner in case CursorAnchorInfo is invalid
            workaroundNullCursorAnchorInfo()
            return
        }
        // params of `Matrix.mapPoints` must be [x0, y0, x1, y1]
        info.matrix.mapPoints(anchorPosition)
        val (xOffset, yOffset) = decorLocation
        anchorPosition[0] -= xOffset
        anchorPosition[1] -= yOffset
        anchorPosition[2] -= xOffset
        anchorPosition[3] -= yOffset
        candidatesView?.updateCursorAnchor(anchorPosition, contentSize)
    }

    private fun handleCursorUpdate(newSelStart: Int, newSelEnd: Int, updateIndex: Int) {
        if (selection.consume(newSelStart, newSelEnd)) {
            return // do nothing if prediction matches
        } else {
            // cursor update can't match any prediction: it's treated as a user input
            selection.resetTo(newSelStart, newSelEnd)
        }
        // skip selection range update, we only care about selection cursor (zero width) here
        if (newSelStart != newSelEnd) return
        // do reset if composing is empty && input panel is not empty
        if (composing.isEmpty()) {
            postFcitxJob {
                if (!isEmpty()) {
                    clearT9CompositionState()
                    reset()
                }
            }
            return
        }
        // check if cursor inside composing text
        if (composing.contains(newSelStart)) {
            if (ignoreSystemCursor) return
            // fcitx cursor position is relative to client preedit (composing text)
            val position = newSelStart - composing.start
            // move fcitx cursor when cursor position changed
            if (position != composingText.cursor) {
                // cursor in InvokeActionEvent counts by "UTF-8 characters"
                val codePointPosition = composingText.codePointCountUntil(position)
                postFcitxJob {
                    if (updateIndex != cursorUpdateIndex) return@postFcitxJob
                    Timber.d("handleCursorUpdate: move fcitx cursor to $codePointPosition")
                    moveCursor(codePointPosition)
                }
            }
        } else {
            resetComposingState()
            candidatesView?.clearTransientState()
            inputView?.clearTransientState()
            // cursor outside composing range, finish composing as-is
            currentInputConnection?.finishComposingText()
            // `fcitx.reset()` here would commit preedit after new cursor position
            // since we have `ClientUnfocusCommit`, focus out and in would do the trick
            postFcitxJob {
                focusOutIn()
            }
        }
    }

    // because setComposingText(text, cursor) can only put cursor at end of composing,
    // sometimes onUpdateSelection would receive event with wrong cursor position.
    // those events need to be filtered.
    // because of https://android.googlesource.com/platform/frameworks/base.git/+/refs/tags/android-11.0.0_r45/core/java/android/view/inputmethod/BaseInputConnection.java#851
    // it's not possible to set cursor inside composing text
    private fun updateComposingText(text: FormattedText) {
        val ic = currentInputConnection ?: return
        val lastSelection = selection.latest
        ic.beginBatchEdit()
        if (composingText.spanEquals(text)) {
            // composing text content is up-to-date
            // update cursor only when it's not empty AND cursor position is valid
            if (text.length > 0 && text.cursor >= 0) {
                val p = text.cursor + composing.start
                if (p != lastSelection.start) {
                    Timber.d("updateComposingText: set Android selection ($p, $p)")
                    ic.setSelection(p, p)
                    selection.predict(p)
                }
            }
        } else {
            // composing text content changed
            Timber.d("updateComposingText: '$text' lastSelection=$lastSelection")
            if (text.isEmpty()) {
                if (composing.isEmpty()) {
                    // do not reset saved selection range when incoming composing
                    // and saved composing range are both empty:
                    // composing.start is invalid when it's empty.
                    selection.predict(lastSelection.start)
                } else {
                    // clear composing text, put cursor at start of original composing
                    selection.predict(composing.start)
                    composing.clear()
                }
                ic.setComposingText("", 1)
            } else {
                val start = if (composing.isEmpty()) lastSelection.start else composing.start
                composing.update(start, start + text.length)
                // skip cursor reposition when:
                // - preedit cursor is at the end
                // - cursor position is invalid
                if (text.cursor == text.length || text.cursor < 0) {
                    selection.predict(composing.end)
                    ic.setComposingText(text.toSpannedString(highlightColor), 1)
                } else {
                    val p = text.cursor + composing.start
                    selection.predict(p)
                    ic.setComposingText(text.toSpannedString(highlightColor), 1)
                    ic.setSelection(p, p)
                }
            }
            Timber.d("updateComposingText: composing=$composing")
        }
        composingText = text
        ic.endBatchEdit()
    }

    /**
     * Finish composing text and leave cursor position as-is.
     * Also updates internal composing state of [FcitxInputMethodService].
     */
    fun finishComposing() {
        val ic = currentInputConnection ?: return
        if (composing.isEmpty()) return
        composing.clear()
        composingText = FormattedText.Empty
        ic.finishComposingText()
    }

    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        // ignore inline suggestion when disabled by user || using physical keyboard with floating candidates view
        if (!inlineSuggestions || !inputDeviceMgr.isVirtualKeyboard) return null
        val theme = ThemeManager.activeTheme
        val chipDrawable =
            if (theme.isDark) R.drawable.bkg_inline_suggestion_dark else R.drawable.bkg_inline_suggestion_light
        val chipBg = Icon.createWithResource(this, chipDrawable).setTint(theme.keyTextColor)
        val style = InlineSuggestionUi.newStyleBuilder()
            .setSingleIconChipStyle(
                ViewStyle.Builder()
                    .setBackgroundColor(Color.TRANSPARENT)
                    .setPadding(0, 0, 0, 0)
                    .build()
            )
            .setChipStyle(
                ViewStyle.Builder()
                    .setBackground(chipBg)
                    .setPadding(dp(10), 0, dp(10), 0)
                    .build()
            )
            .setTitleStyle(
                TextViewStyle.Builder()
                    .setLayoutMargin(dp(4), 0, dp(4), 0)
                    .setTextColor(theme.keyTextColor)
                    .setTextSize(14f)
                    .build()
            )
            .setSubtitleStyle(
                TextViewStyle.Builder()
                    .setTextColor(theme.altKeyTextColor)
                    .setTextSize(12f)
                    .build()
            )
            .setStartIconStyle(
                ImageViewStyle.Builder()
                    .setTintList(ColorStateList.valueOf(theme.altKeyTextColor))
                    .build()
            )
            .setEndIconStyle(
                ImageViewStyle.Builder()
                    .setTintList(ColorStateList.valueOf(theme.altKeyTextColor))
                    .build()
            )
            .build()
        val styleBundle = UiVersions.newStylesBuilder()
            .addStyle(style)
            .build()
        val spec = InlinePresentationSpec
            .Builder(Size(0, 0), Size(Int.MAX_VALUE, Int.MAX_VALUE))
            .setStyle(styleBundle)
            .build()
        return InlineSuggestionsRequest.Builder(listOf(spec))
            .setMaxSuggestionCount(InlineSuggestionsRequest.SUGGESTION_COUNT_UNLIMITED)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if (!inlineSuggestions || !inputDeviceMgr.isVirtualKeyboard) return false
        return inputView?.handleInlineSuggestions(response) == true
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        Timber.d("onFinishInputView: finishingInput=$finishingInput")
        decorLocationUpdated = false
        inputDeviceMgr.onFinishInputView()
        currentInputConnection?.apply {
            finishComposingText()
            monitorCursorAnchor(false)
        }
        resetComposingState()
        candidatesView?.clearTransientState()
        inputView?.clearTransientState()
        postFcitxJob {
            focusOutIn()
        }
        showingDialog?.dismiss()
    }

    override fun onFinishInput() {
        Timber.d("onFinishInput")
        clearT9CompositionState()
        candidatesView?.clearTransientState()
        inputView?.clearTransientState()
        postFcitxJob {
            focus(false)
        }
        capabilityFlags = CapabilityFlags.DefaultFlags
    }

    override fun onUnbindInput() {
        cachedKeyEvents.evictAll()
        cachedKeyEventIndex = 0
        cursorUpdateIndex = 0
        // currentInputBinding can be null on some devices under some special Multi-screen mode
        val uid = currentInputBinding?.uid ?: return
        Timber.d("onUnbindInput: uid=$uid")
        postFcitxJob {
            deactivate(uid)
        }
    }

    override fun onDestroy() {
        recreateInputViewPrefs.forEach {
            it.unregisterOnChangeListener(recreateInputViewListener)
        }
        prefs.candidates.registerOnChangeListener(recreateCandidatesViewListener)
        ThemeManager.removeOnChangedListener(onThemeChangeListener)
        super.onDestroy()
        // Fcitx might be used in super.onDestroy()
        FcitxDaemon.disconnect(javaClass.name)
    }

    private var showingDialog: Dialog? = null

    fun showDialog(dialog: Dialog) {
        showingDialog?.dismiss()
        dialog.window?.also {
            it.attributes.apply {
                token = decorView.windowToken
                type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            }
            it.addFlags(
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )
            it.setDimAmount(styledFloat(android.R.attr.backgroundDimAmount))
        }
        dialog.setOnDismissListener {
            showingDialog = null
        }
        dialog.show()
        showingDialog = dialog
    }

    @Suppress("ConstPropertyName")
    companion object {
        const val DeleteSurroundingFlag = "org.fcitx.fcitx5.android.DELETE_SURROUNDING"
    }
}