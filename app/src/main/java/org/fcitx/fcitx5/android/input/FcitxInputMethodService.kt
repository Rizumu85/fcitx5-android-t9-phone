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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputMethodSubtype
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlag
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
import org.fcitx.fcitx5.android.input.t9.ChineseT9CompositionSession
import org.fcitx.fcitx5.android.input.t9.ChineseT9RimeBridge
import org.fcitx.fcitx5.android.input.t9.PhysicalT9KeyHandler
import org.fcitx.fcitx5.android.input.t9.PhysicalT9KeyPolicy
import org.fcitx.fcitx5.android.input.t9.SmartEnglishT9Session
import org.fcitx.fcitx5.android.input.t9.T9EnglishDictionary
import org.fcitx.fcitx5.android.input.t9.T9PresentationState
import org.fcitx.fcitx5.android.input.t9.T9PinyinUtils
import org.fcitx.fcitx5.android.input.t9.T9ResolvedSegment
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
import kotlin.math.min

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
    private val passwordInputPreviewBuffer = StringBuilder()
    private var passwordInputPreviewCursor = 0
    private val numberModeController = NumberModeController(
        commitText = { text -> commitText(text) },
        getTextBeforeCursor = {
            currentInputConnection
                ?.getTextBeforeCursor(96, 0)
                ?.toString()
        },
        showOperatorHints = { inputView?.showNumberOperatorHints() },
        hideOperatorHints = { inputView?.hideNumberOperatorHints() },
        showEqualsChoice = { prefix, result -> inputView?.showNumberEqualsChoice(prefix, result) },
        hideEqualsChoice = { inputView?.hideNumberEqualsChoice() }
    )
    private val physicalT9KeyHandler = PhysicalT9KeyHandler(object : PhysicalT9KeyHandler.Host {
        override val isInInputMode: Boolean
            get() = inputDeviceMgr.isInInputMode
        override val mode: PhysicalT9KeyHandler.Mode
            get() = when (currentT9Mode) {
                T9InputMode.CHINESE -> PhysicalT9KeyHandler.Mode.CHINESE
                T9InputMode.ENGLISH -> PhysicalT9KeyHandler.Mode.ENGLISH
                T9InputMode.NUMBER -> PhysicalT9KeyHandler.Mode.NUMBER
            }
        override val isSmartEnglishActive: Boolean
            get() = isSmartEnglishT9Active()
        override val chineseComposing: Boolean
            get() = getT9InputState() == T9InputState.CHINESE_COMPOSING
        override val compositionKeyCount: Int
            get() = getT9CompositionKeyCount()
        override val hasPendingPunctuation: Boolean
            get() = t9PendingPunctuation.isPending
        override val pendingPunctuationOneKeyDeferred: Boolean
            get() = t9PendingPunctuation.oneKeyDeferred
        override val pendingPunctuationSet: PhysicalT9KeyHandler.PunctuationSet
            get() = when (t9PendingPunctuation.set) {
                T9PunctuationSet.CHINESE -> PhysicalT9KeyHandler.PunctuationSet.CHINESE
                T9PunctuationSet.ENGLISH -> PhysicalT9KeyHandler.PunctuationSet.ENGLISH
            }
        override val hasSmartEnglishDigits: Boolean
            get() = smartEnglishSession.hasDigits
        override val hasMultiTapPendingChar: Boolean
            get() = multiTapPendingChar != null
        override val hasTopPinyinCandidates: Boolean
            get() = getT9PinyinCandidates().isNotEmpty()
        override val candidateFocus: PhysicalT9KeyHandler.CandidateFocus
            get() = when (getT9CandidateFocus()) {
                T9CandidateFocus.TOP -> PhysicalT9KeyHandler.CandidateFocus.TOP
                T9CandidateFocus.BOTTOM -> PhysicalT9KeyHandler.CandidateFocus.BOTTOM
            }

        override fun keyHeldPastLongPressDelay(input: PhysicalT9KeyHandler.KeyInput): Boolean =
            input.repeatCount > 0 &&
                input.eventTime - input.downTime >= physicalLongPressDelay.toLong()

        override fun setPendingPunctuationOneKeyDeferred(value: Boolean) {
            t9PendingPunctuation = t9PendingPunctuation.copy(oneKeyDeferred = value)
        }

        override fun commitPendingPunctuationShortcut(keyCode: Int): Boolean =
            commitPendingT9PunctuationShortcut(keyCode)

        override fun commitHanziShortcut(keyCode: Int): Boolean =
            commitT9HanziShortcutFromLongPress(keyCode)

        override fun commitSmartEnglishShortcut(keyCode: Int): Boolean =
            commitSmartEnglishShortcutFromLongPress(keyCode)

        override fun commitPendingPunctuation(): Boolean = commitPendingT9Punctuation()
        override fun cancelPendingPunctuation(): Boolean = cancelPendingT9Punctuation()
        override fun handleChinesePunctuationKey(): Boolean =
            this@FcitxInputMethodService.handleChinesePunctuationKey()

        override fun togglePendingPunctuationSet(): Boolean =
            this@FcitxInputMethodService.togglePendingT9PunctuationSet()
        override fun switchToNextMode() = switchToNextT9Mode()
        override fun commitText(text: String) = this@FcitxInputMethodService.commitText(text)

        override fun commitNumberOperatorForKey(keyCode: Int, fallbackDigit: Int): Boolean {
            val operator = numberModeController.operatorForKey(keyCode) ?: DIGIT_TEXTS[fallbackDigit]
            return commitNumberModeOperator(operator)
        }

        override fun showNumberOperatorHintPanel() = this@FcitxInputMethodService.showNumberOperatorHintPanel()
        override fun commitLiteralStarInCurrentChineseState() = commitLiteralT9Star(getT9InputState())
        override fun handleEnglishStarShortPress() = this@FcitxInputMethodService.handleEnglishStarShortPress()
        override fun handleEnglishStarLongPress() = this@FcitxInputMethodService.handleEnglishStarLongPress()
        override fun handleMultiTapKey(keyCode: Int): Boolean = this@FcitxInputMethodService.handleMultiTapKey(keyCode)
        override fun commitMultiTapChar(): Boolean = this@FcitxInputMethodService.commitMultiTapChar()
        override fun cancelMultiTapChar() = this@FcitxInputMethodService.cancelMultiTapChar()
        override fun deferSmartEnglishPunctuationKey() = this@FcitxInputMethodService.deferSmartEnglishPunctuationKey()
        override fun showSmartEnglishPunctuationCandidates() =
            this@FcitxInputMethodService.showSmartEnglishPunctuationCandidates()

        override fun appendSmartEnglishDigit(digit: Int) {
            smartEnglishSession.appendDigit(digit)
            candidatesView?.refreshT9Ui()
        }

        override fun resetSmartEnglishT9() = this@FcitxInputMethodService.resetSmartEnglishT9()
        override fun commitSmartEnglishCandidate(): Boolean =
            this@FcitxInputMethodService.commitSmartEnglishCandidate()

        override fun moveSmartEnglishCandidate(delta: Int): Boolean =
            this@FcitxInputMethodService.moveSmartEnglishCandidate(delta)

        override fun smartEnglishBackspace(): Boolean = handleSmartEnglishBackspace()
        override fun flushEnglishLearningWord() = this@FcitxInputMethodService.flushEnglishLearningWord()
        override fun handleReturnKey() = this@FcitxInputMethodService.handleReturnKey()
        override fun forwardChineseT9KeyShortPress(
            keyCode: Int,
            input: PhysicalT9KeyHandler.KeyInput
        ): Boolean =
            this@FcitxInputMethodService.forwardChineseT9KeyShortPress(keyCode, input)

        override fun forwardChineseT9SeparatorShortPress(): Boolean =
            this@FcitxInputMethodService.forwardChineseT9SeparatorShortPress()

        override fun moveCandidateFocus(focus: PhysicalT9KeyHandler.CandidateFocus) {
            moveT9CandidateFocus(
                when (focus) {
                    PhysicalT9KeyHandler.CandidateFocus.TOP -> T9CandidateFocus.TOP
                    PhysicalT9KeyHandler.CandidateFocus.BOTTOM -> T9CandidateFocus.BOTTOM
                }
            )
        }

        override fun moveHighlightedPinyin(delta: Int): Boolean =
            candidatesView?.moveHighlightedT9Pinyin(delta) == true

        override fun moveHighlightedBottomCandidate(delta: Int): Boolean =
            candidatesView?.moveHighlightedT9BottomCandidate(delta) == true

        override fun offsetBottomCandidatePage(delta: Int): Boolean =
            candidatesView?.offsetT9BottomCandidatePage(delta) == true

        override fun commitHighlightedPinyin(): Boolean = commitHighlightedT9Pinyin()
        override fun commitHighlightedBottomCandidate(): Boolean =
            candidatesView?.commitHighlightedT9BottomCandidate() == true
    })

    private fun isTemporaryPasswordKeyboardVisible(): Boolean =
        inputView?.isTemporaryPasswordKeyboardVisible() == true

    private fun isPasswordInputPreviewActive(): Boolean =
        passwordInputPreviewEnabled &&
            inputView?.isTemporaryPasswordInputSessionActive() == true

    fun clearPasswordInputPreview() {
        if (passwordInputPreviewBuffer.isNotEmpty()) {
            passwordInputPreviewBuffer.clear()
        }
        passwordInputPreviewCursor = 0
        inputView?.setPasswordInputPreview("", 0, hasContent = false)
    }

    private fun updatePasswordInputPreviewView() {
        val textLength = passwordInputPreviewBuffer.length
        passwordInputPreviewCursor = passwordInputPreviewCursor.coerceIn(0, textLength)
        if (textLength == 0) {
            inputView?.setPasswordInputPreview("", 0, hasContent = false)
            return
        }
        inputView?.setPasswordInputPreview(
            passwordInputPreviewBuffer.toString(),
            passwordInputPreviewCursor,
            hasContent = true
        )
    }

    private fun recordPasswordInputPreviewCommit(text: String) {
        if (!isPasswordInputPreviewActive() || text.isEmpty()) return
        passwordInputPreviewBuffer.insert(passwordInputPreviewCursor, text)
        passwordInputPreviewCursor += text.length
        updatePasswordInputPreviewView()
    }

    private fun recordPasswordInputPreviewBackspace(selectionDeleted: Boolean) {
        if (!isPasswordInputPreviewActive()) return
        if (selectionDeleted) {
            clearPasswordInputPreview()
            return
        }
        if (passwordInputPreviewBuffer.isEmpty() || passwordInputPreviewCursor <= 0) return
        val lastCodePointStart =
            passwordInputPreviewBuffer.offsetByCodePoints(passwordInputPreviewCursor, -1)
        passwordInputPreviewBuffer.delete(lastCodePointStart, passwordInputPreviewCursor)
        passwordInputPreviewCursor = lastCodePointStart
        updatePasswordInputPreviewView()
    }

    private fun movePasswordInputPreviewCursor(offset: Int) {
        if (!isPasswordInputPreviewActive() || passwordInputPreviewBuffer.isEmpty()) return
        passwordInputPreviewCursor = when {
            offset < 0 && passwordInputPreviewCursor > 0 ->
                passwordInputPreviewBuffer.offsetByCodePoints(passwordInputPreviewCursor, -1)
            offset > 0 && passwordInputPreviewCursor < passwordInputPreviewBuffer.length ->
                passwordInputPreviewBuffer.offsetByCodePoints(passwordInputPreviewCursor, 1)
            else -> passwordInputPreviewCursor
        }
        updatePasswordInputPreviewView()
    }

    private fun commitPhysicalPasswordLiteralKey(keyCode: Int, event: KeyEvent): Boolean {
        if (!isTemporaryPasswordKeyboardVisible() || event.action != KeyEvent.ACTION_DOWN) {
            return false
        }
        val decimalDigit = PhysicalT9KeyPolicy.decimalDigit(keyCode)
        val text = when {
            decimalDigit != null -> DIGIT_TEXTS[decimalDigit]
            keyCode == KeyEvent.KEYCODE_STAR ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> "*"
            keyCode == KeyEvent.KEYCODE_POUND -> "#"
            else -> return false
        }
        commitText(text)
        t9ConsumedNavigationKeyUp = keyCode
        return true
    }

    private fun handlePhysicalPasswordBackspaceKey(keyCode: Int, event: KeyEvent): Boolean {
        if (!isTemporaryPasswordKeyboardVisible() || event.action != KeyEvent.ACTION_DOWN) {
            return false
        }
        if (!PhysicalT9KeyPolicy.isDeleteKey(keyCode)) return false
        val lastSelection = selection.latest
        if (!deleteBeforeCursorDirectly()) return false
        recordPasswordInputPreviewBackspace(selectionDeleted = lastSelection.isNotEmpty())
        t9ConsumedNavigationKeyUp = keyCode
        return true
    }

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
        chineseT9Session.clear()
    }

    private fun hasT9CompositionState(): Boolean =
        chineseT9Session.hasState()

    private fun clearTransientInputUiState() {
        candidatesView?.clearTransientState()
        inputView?.clearTransientState()
    }

    fun candidatePagingModeForCurrentInputDevice(): Int =
        if (isChineseT9InputModeActive() || !inputDeviceMgr.isVirtualKeyboard) 1 else 0

    fun isChineseT9InputModeActive(): Boolean =
        t9InputModeEnabled && currentT9Mode == T9InputMode.CHINESE

    private suspend fun refreshT9UiOnMain() {
        withContext(Dispatchers.Main.immediate) {
            candidatesView?.refreshT9Ui()
        }
    }

    private suspend fun FcitxAPI.enforceHalfWidthForT9() {
        if (!t9InputModeEnabled) return
        val fullwidthAction = statusArea().firstOrNull {
            it.name == "fullwidth" &&
                (it.icon == "fcitx-fullwidth-active" || it.isChecked)
        } ?: return
        activateAction(fullwidthAction.id)
    }

    /**
     * Handle a virtual (on-screen) backspace press. Returns true when the press was consumed
     * by reopening a previously selected pinyin segment back into its source digits; the caller
     * must then skip sending a regular backspace to fcitx. Returns false for the normal
     * shrink-unresolved-suffix path (caller still forwards the backspace to fcitx/Rime).
     */
    suspend fun handleVirtualT9Backspace(fcitx: FcitxAPI): Boolean {
        if (!t9InputModeEnabled || currentT9Mode != T9InputMode.CHINESE) return false
        if (shouldReopenLastResolvedSegment()) {
            val consumed = popLastResolvedSegment(fcitx)
            if (consumed) refreshT9UiOnMain()
            return consumed
        }
        chineseT9Session.backspace()
        refreshT9UiOnMain()
        return false
    }

    /**
     * Delete should first undo any selected pinyin segment before shrinking the digit input.
     * While a pinyin is selected, the user's most recent decision is the selection itself; a
     * single delete rolls that back rather than editing the trailing digit suffix.
     */
    private fun shouldReopenLastResolvedSegment(): Boolean =
        t9InputModeEnabled &&
            currentT9Mode == T9InputMode.CHINESE &&
            chineseT9Session.shouldReopenLastResolvedSegment()

    /**
     * Undo the last pinyin selection by prepending its source digits to the unresolved suffix.
     * Engine-backed selections are also restored inside Rime so candidate narrowing follows the
     * same state as the Kotlin presentation model.
     */
    private suspend fun popLastResolvedSegment(fcitx: FcitxAPI): Boolean {
        val popped = chineseT9Session.popLastResolvedSegment() ?: return false
        if (popped.segment.engineBacked) {
            restoreT9ResolvedSegmentInEngine(
                fcitx,
                popped.segment,
                popped.previousUnresolved,
                popped.fallbackRawPreedit
            )
        }
        return true
    }

    private suspend fun restoreT9ResolvedSegmentInEngine(
        fcitx: FcitxAPI,
        segment: T9ResolvedSegment,
        previousUnresolved: String,
        fallbackRawPreedit: String
    ) {
        ChineseT9RimeBridge.from(chineseT9Session, fcitx).restoreResolvedSegment(
            segment = segment,
            previousUnresolved = previousUnresolved,
            fallbackRawPreedit = fallbackRawPreedit,
            candidatePagingMode = candidatePagingModeForCurrentInputDevice()
        )
    }

    fun clearHiddenChineseT9CompositionIfCandidateUiSuppressed() {
        if (!t9InputModeEnabled || currentT9Mode != T9InputMode.CHINESE) return
        if (t9PendingPunctuation.isPending || getT9CompositionKeyCount() > 0) return
        clearT9CompositionState()
        clearTransientInputUiState()
        currentInputConnection?.finishComposingText()
        postFcitxJob {
            if (getRimeInput().isNotEmpty()) {
                focusOutIn()
            }
        }
    }

    private fun resetComposingState() {
        composing.clear()
        composingText = FormattedText.Empty
        clearT9CompositionState()
        t9CandidateFocus = T9CandidateFocus.BOTTOM
    }

    private fun resetPhysicalSelectionState() {
        physicalSelectionMode = false
        pendingPhysicalSelectionOkKeyCode = null
        physicalSelectionOkLongPressTriggered = false
        physicalSelectionAnchor = -1
        physicalSelectionFocus = -1
        physicalSelectionRangeActive = false
        physicalSelectionActionPanelActive = false
        inputView?.hideSelectionActionHints()
        numberModeController.dismissTransientPanel()
        lastSelectionReplacementKeyUptime = 0L
    }

    private var cursorUpdateIndex: Int = 0

    private var highlightColor: Int = 0x66008577 // material_deep_teal_500 with alpha 0.4

    private val prefs = AppPrefs.getInstance()
    private val keyboardPrefs = prefs.keyboard

    @Volatile
    private var inlineSuggestions = keyboardPrefs.inlineSuggestions.getValue()

    @Volatile
    private var passwordInputPreviewEnabled = keyboardPrefs.passwordInputPreview.getValue()

    @Volatile
    private var physicalKeySound = keyboardPrefs.physicalKeySound.getValue()

    @Volatile
    private var t9InputModeEnabled = keyboardPrefs.useT9KeyboardLayout.getValue()

    @Volatile
    private var smartEnglishT9 = keyboardPrefs.smartEnglishT9.getValue()

    @Volatile
    private var physicalLongPressDelay = keyboardPrefs.longPressDelay.getValue()

    @Volatile
    private var ignoreSystemCursor = prefs.advanced.ignoreSystemCursor.getValue()

    private val inlineSuggestionsChangeListener =
        ManagedPreference.OnChangeListener<Boolean> { _, value ->
            inlineSuggestions = value
        }
    private val passwordInputPreviewEnabledChangeListener =
        ManagedPreference.OnChangeListener<Boolean> { _, value ->
            passwordInputPreviewEnabled = value
        }
    private val physicalKeySoundChangeListener =
        ManagedPreference.OnChangeListener<Boolean> { _, value ->
            physicalKeySound = value
        }
    private val t9InputModeEnabledChangeListener =
        ManagedPreference.OnChangeListener<Boolean> { _, value ->
            t9InputModeEnabled = value
        }
    private val smartEnglishT9ChangeListener =
        ManagedPreference.OnChangeListener<Boolean> { _, value ->
            smartEnglishT9 = value
            resetSmartEnglishT9()
        }
    private val physicalLongPressDelayChangeListener =
        ManagedPreference.OnChangeListener<Int> { _, value ->
            physicalLongPressDelay = value
        }
    private val ignoreSystemCursorChangeListener =
        ManagedPreference.OnChangeListener<Boolean> { _, value ->
            ignoreSystemCursor = value
        }

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

    private fun shouldKeepTemporaryPasswordModeOnRestart(
        restarting: Boolean,
        flags: CapabilityFlags
    ): Boolean {
        return restarting && inputView?.shouldKeepTemporaryPasswordModeOnRestart(flags) == true
    }

    private fun passwordModeCapabilityFlags(flags: CapabilityFlags): CapabilityFlags {
        return CapabilityFlags(flags.flags or CapabilityFlag.Password.flag)
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

    @Keep
    private val recreateInputViewsListener = ManagedPreference.OnChangeListener<Any> { _, _ ->
        replaceInputViews(ThemeManager.activeTheme)
    }

    @Keep
    private val passwordInputPreviewChangeListener =
        ManagedPreference.OnChangeListener<Boolean> { _, enabled ->
            if (!enabled) clearPasswordInputPreview()
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
        keyboardPrefs.inlineSuggestions.registerOnChangeListener(inlineSuggestionsChangeListener)
        keyboardPrefs.passwordInputPreview.registerOnChangeListener(
            passwordInputPreviewEnabledChangeListener
        )
        keyboardPrefs.physicalKeySound.registerOnChangeListener(physicalKeySoundChangeListener)
        keyboardPrefs.useT9KeyboardLayout.registerOnChangeListener(t9InputModeEnabledChangeListener)
        keyboardPrefs.smartEnglishT9.registerOnChangeListener(smartEnglishT9ChangeListener)
        keyboardPrefs.longPressDelay.registerOnChangeListener(physicalLongPressDelayChangeListener)
        prefs.advanced.ignoreSystemCursor.registerOnChangeListener(ignoreSystemCursorChangeListener)
        prefs.keyboard.inputUiFont.registerOnChangeListener(recreateInputViewsListener)
        keyboardPrefs.passwordInputPreview.registerOnChangeListener(passwordInputPreviewChangeListener)
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
        if (t9InputModeEnabled && currentT9Mode == T9InputMode.CHINESE) {
            chineseT9Session.backspace()
        }
        val lastSelection = selection.latest
        recordPasswordInputPreviewBackspace(selectionDeleted = lastSelection.isNotEmpty())
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

    private fun shouldHideImeForEmptyEditorDelete(): Boolean {
        if (composing.isNotEmpty()) return false
        if (hasT9CompositionState()) return false
        if (multiTapPendingChar != null) return false
        if (t9PendingPunctuation.isPending) return false

        val lastSelection = selection.latest
        if (lastSelection.isNotEmpty()) return false
        if (lastSelection.start > 0 || lastSelection.end > 0) return false

        val ic = currentInputConnection ?: return false
        ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.let {
            return it.isEmpty()
        }
        val before = ic.getTextBeforeCursor(1, 0) ?: return false
        val after = ic.getTextAfterCursor(1, 0) ?: return false
        return before.isEmpty() && after.isEmpty()
    }

    private fun shouldDirectDeleteForIdlePhysicalBackspace(): Boolean =
        composing.isEmpty() &&
            !hasT9CompositionState() &&
            multiTapPendingChar == null &&
            !t9PendingPunctuation.isPending

    private fun deleteBeforeCursorDirectly(): Boolean {
        val ic = currentInputConnection ?: return false
        val lastSelection = selection.latest
        if (lastSelection.isNotEmpty()) {
            selection.predict(lastSelection.start)
            ic.commitText("", 1)
            return true
        }
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        val extractedText = extracted?.text
        val extractedStart = extracted?.selectionStart ?: -1
        val extractedEnd = extracted?.selectionEnd ?: -1
        if (extractedText != null && extractedStart >= 0 && extractedEnd >= 0) {
            val start = minOf(extractedStart, extractedEnd)
            val end = maxOf(extractedStart, extractedEnd)
            if (start != end) {
                selection.predict(start)
                ic.commitText("", 1)
                return true
            }
            if (start > 0) {
                if (lastSelection.start > 0) {
                    selection.predictOffset(-1)
                } else {
                    selection.predict(start - 1)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    ic.deleteSurroundingTextInCodePoints(1, 0)
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
                return true
            }
            if (extractedText.isNotEmpty()) return false
        }
        if (lastSelection.start <= 0 &&
            ic.getTextBeforeCursor(1, 0).isNullOrEmpty()
        ) {
            return false
        }
        if (lastSelection.start > 0) {
            selection.predictOffset(-1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ic.deleteSurroundingTextInCodePoints(1, 0)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        return true
    }

    private fun commitT9PreviewPinyinFromReturn(): Boolean {
        val text = candidatesView?.getT9PreviewCommitText() ?: return false
        commitText(text)
        clearTransientInputUiState()
        currentInputConnection?.finishComposingText()
        postFcitxJob {
            focusOutIn()
        }
        return true
    }

    private val tt9StyleEnterAction = EditorInfo.IME_MASK_ACTION + 1

    private fun getTt9StyleEditorAction(): Int {
        currentInputEditorInfo.run {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionLabel != null) {
                return tt9StyleEnterAction
            }
            if (actionId > 0) return actionId

            val standardAction = imeOptions and
                (EditorInfo.IME_MASK_ACTION or EditorInfo.IME_FLAG_NO_ENTER_ACTION)
            return when (standardAction) {
                EditorInfo.IME_ACTION_DONE,
                EditorInfo.IME_ACTION_GO,
                EditorInfo.IME_ACTION_NEXT,
                EditorInfo.IME_ACTION_PREVIOUS,
                EditorInfo.IME_ACTION_SEARCH,
                EditorInfo.IME_ACTION_SEND,
                EditorInfo.IME_ACTION_UNSPECIFIED -> standardAction
                else -> tt9StyleEnterAction
            }
        }
    }

    private fun handleReturnKey() {
        if (commitT9PreviewPinyinFromReturn()) return
        if (isPasswordInputPreviewActive()) {
            clearPasswordInputPreview()
        }
        currentInputEditorInfo.run {
            if (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL) {
                sendTt9StyleDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                return
            }
            when (val action = getTt9StyleEditorAction()) {
                tt9StyleEnterAction -> {
                    Timber.d(
                        "handleReturnKey: route=tt9Enter, package=${packageName}, " +
                            "inputType=${inputType}, actionId=${actionId}, " +
                            "actionLabel=${actionLabel}, imeOptions=${imeOptions}"
                    )
                    sendTt9StyleDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
                else -> if (currentInputConnection?.performEditorAction(action) != true) {
                    Timber.d(
                        "handleReturnKey: route=actionFallback, action=${action}, " +
                            "package=${packageName}, inputType=${inputType}, " +
                            "actionId=${actionId}, actionLabel=${actionLabel}, " +
                            "imeOptions=${imeOptions}"
                    )
                    sendTt9StyleDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
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
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> movePasswordInputPreviewCursor(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> movePasswordInputPreviewCursor(1)
        }
    }

    fun commitText(text: String, cursor: Int = -1) {
        val ic = currentInputConnection ?: return
        if (physicalSelectionMode) {
            exitPhysicalSelectionMode(showBadge = true)
        }
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
            recordPasswordInputPreviewCommit(text)
        } else {
            val target = start + cursor
            selection.predict(target)
            ic.withBatchEdit {
                commitText(text, 1)
                setSelection(target, target)
            }
            recordPasswordInputPreviewCommit(text)
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

    private fun sendTt9StyleDownUpKeyEvents(keyEventCode: Int, metaState: Int = 0): Boolean {
        val ic = currentInputConnection ?: return false
        val down = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyEventCode, 0, metaState)
        val up = KeyEvent(0, 0, KeyEvent.ACTION_UP, keyEventCode, 0, metaState)
        val downSent = ic.sendKeyEvent(down)
        val upSent = ic.sendKeyEvent(up)
        Timber.d(
            "sendTt9StyleDownUpKeyEvents: keyCode=${keyEventCode}, " +
                "downSent=${downSent}, upSent=${upSent}"
        )
        if (!downSent || !upSent) sendDownUpKeyEvents(keyEventCode)
        return downSent && upSent
    }

    fun deleteSelection() {
        deleteSelectionIfAny()
    }

    fun deleteSelectionIfAny(): Boolean {
        val lastSelection = selection.latest
        if (lastSelection.isEmpty()) return false
        val ic = currentInputConnection ?: return false
        physicalSelectionRangeActive = false
        selection.predict(lastSelection.start)
        ic.commitText("", 1)
        lastExplicitSelectionDeleteUptime = SystemClock.uptimeMillis()
        updateSelectionBackCallback(false)
        return true
    }

    private val selectionBackCallback: OnBackInvokedCallback by lazy {
        OnBackInvokedCallback {
            if (!deleteSelectionIfAny()) {
                updateSelectionBackCallback(false)
            }
        }
    }
    private var selectionBackCallbackRegistered = false
    private var selectionPreImeBackConsumed = false
    private var lastEditorTouchUptime = 0L
    private var lastExplicitSelectionDeleteUptime = 0L
    private var lastSelectionReplacementKeyUptime = 0L
    private var deletingCollapsedSelection = false

    fun handlePreImeKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
        if (event.action == KeyEvent.ACTION_UP && selectionPreImeBackConsumed) {
            selectionPreImeBackConsumed = false
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false

        if (numberModeController.hasTransientPanel) {
            dismissNumberTransientPanel()
            selectionPreImeBackConsumed = true
            return true
        }
        if (physicalSelectionActionPanelActive) {
            cancelPhysicalSelectionActionPanelSelection()
            selectionPreImeBackConsumed = true
            return true
        }

        val consumed = deleteSelectionIfAny()
        selectionPreImeBackConsumed = consumed
        return consumed
    }

    private fun updateSelectionBackCallback(hasSelection: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val dispatcher = window.window?.onBackInvokedDispatcher ?: return
        if (hasSelection && !selectionBackCallbackRegistered) {
            dispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                selectionBackCallback
            )
            selectionBackCallbackRegistered = true
        } else if (!hasSelection && selectionBackCallbackRegistered) {
            dispatcher.unregisterOnBackInvokedCallback(selectionBackCallback)
            selectionBackCallbackRegistered = false
        }
    }

    private fun deleteCollapsedSelectionIfNeeded(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int
    ): Boolean {
        if (deletingCollapsedSelection) return false
        if (physicalSelectionMode || physicalSelectionRangeActive) return false
        if (!t9InputModeEnabled || inputDeviceMgr.isVirtualKeyboard) return false
        if (oldSelStart == oldSelEnd || newSelStart != newSelEnd) return false

        val now = SystemClock.uptimeMillis()
        if (now - lastSelectionReplacementKeyUptime > SELECTION_REPLACEMENT_KEY_WINDOW_MS) {
            return false
        }
        if (now - lastEditorTouchUptime < EDITOR_TOUCH_SELECTION_CANCEL_WINDOW_MS) return false
        if (now - lastExplicitSelectionDeleteUptime < EXPLICIT_SELECTION_DELETE_WINDOW_MS) return false

        val start = min(oldSelStart, oldSelEnd)
        val end = max(oldSelStart, oldSelEnd)
        if (start < 0 || end <= start) return false

        val ic = currentInputConnection ?: return false
        deletingCollapsedSelection = true
        try {
            selection.predict(start)
            ic.withBatchEdit {
                setSelection(start, end)
                commitText("", 1)
            }
            lastExplicitSelectionDeleteUptime = SystemClock.uptimeMillis()
            updateSelectionBackCallback(false)
            return true
        } finally {
            deletingCollapsedSelection = false
        }
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

    private fun canUsePhysicalSelectionMode(): Boolean =
        inputDeviceMgr.isInInputMode &&
            currentInputConnection != null &&
            composing.isEmpty() &&
            !hasT9CompositionState() &&
            !t9PendingPunctuation.isPending &&
            multiTapPendingChar == null

    private fun performDeferredPhysicalOkShortPress(keyCode: Int) {
        when (PhysicalT9KeyPolicy.confirmAction(keyCode)) {
            PhysicalT9KeyPolicy.ConfirmAction.SELECT -> sendDownUpKeyEvents(KeyEvent.KEYCODE_SPACE)
            PhysicalT9KeyPolicy.ConfirmAction.RETURN -> handleReturnKey()
            null -> Unit
        }
    }

    private fun enterPhysicalSelectionMode() {
        val cursor = selection.latest.end.coerceAtLeast(0)
        physicalSelectionMode = true
        physicalSelectionAnchor = cursor
        physicalSelectionFocus = cursor
        physicalSelectionActionPanelActive = false
        inputView?.hideSelectionActionHints()
        inputView?.showModeIndicatorBadge("进入选区")
    }

    private fun exitPhysicalSelectionMode(showBadge: Boolean = false) {
        if (!physicalSelectionMode &&
            physicalSelectionAnchor < 0 &&
            physicalSelectionFocus < 0
        ) {
            return
        }
        physicalSelectionMode = false
        pendingPhysicalSelectionOkKeyCode = null
        physicalSelectionOkLongPressTriggered = false
        physicalSelectionAnchor = -1
        physicalSelectionFocus = -1
        if (showBadge) {
            inputView?.showModeIndicatorBadge("退出选区")
        }
    }

    private fun showPhysicalSelectionActionPanel() {
        if (currentInputSelection.isEmpty()) return
        physicalSelectionActionPanelActive = true
        inputView?.showSelectionActionHints()
    }

    private fun dismissPhysicalSelectionActionPanel() {
        if (!physicalSelectionActionPanelActive) return
        physicalSelectionActionPanelActive = false
        inputView?.hideSelectionActionHints()
    }

    private fun performPhysicalSelectionContextAction(action: Int, label: String) {
        dismissPhysicalSelectionActionPanel()
        currentInputConnection?.performContextMenuAction(action)
        inputView?.showModeIndicatorBadge(label)
    }

    private fun performPhysicalSelectionDeleteAction() {
        dismissPhysicalSelectionActionPanel()
        deleteSelectionIfAny()
        inputView?.showModeIndicatorBadge("删除")
    }

    private fun cancelPhysicalSelectionActionPanelSelection() {
        dismissPhysicalSelectionActionPanel()
        cancelSelection()
    }

    private fun handlePhysicalSelectionActionPanelKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!physicalSelectionActionPanelActive) return false
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.repeatCount > 0) return true
        return when {
            keyCode == KeyEvent.KEYCODE_DPAD_UP -> {
                performPhysicalSelectionContextAction(android.R.id.copy, "复制")
                t9ConsumedNavigationKeyUp = keyCode
                true
            }
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> {
                performPhysicalSelectionContextAction(android.R.id.cut, "剪切")
                t9ConsumedNavigationKeyUp = keyCode
                true
            }
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                performPhysicalSelectionContextAction(android.R.id.paste, "粘贴")
                t9ConsumedNavigationKeyUp = keyCode
                true
            }
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                performPhysicalSelectionDeleteAction()
                t9ConsumedNavigationKeyUp = keyCode
                true
            }
            PhysicalT9KeyPolicy.isDeleteKey(keyCode) -> {
                cancelPhysicalSelectionActionPanelSelection()
                t9ConsumedNavigationKeyUp = keyCode
                true
            }
            PhysicalT9KeyPolicy.isConfirmKey(keyCode) -> {
                dismissPhysicalSelectionActionPanel()
                t9ConsumedNavigationKeyUp = keyCode
                true
            }
            else -> {
                dismissPhysicalSelectionActionPanel()
                false
            }
        }
    }

    private fun movePhysicalSelectionFocus(delta: Int): Boolean {
        val ic = currentInputConnection ?: return false
        if (physicalSelectionAnchor < 0 || physicalSelectionFocus < 0) {
            val cursor = selection.latest.end.coerceAtLeast(0)
            physicalSelectionAnchor = cursor
            physicalSelectionFocus = cursor
        }
        val extractedLength = ic.getExtractedText(ExtractedTextRequest(), 0)
            ?.text
            ?.length
            ?: Int.MAX_VALUE
        val nextFocus = (physicalSelectionFocus + delta)
            .coerceAtLeast(0)
            .coerceAtMost(extractedLength)
        if (nextFocus == physicalSelectionFocus) return true
        physicalSelectionFocus = nextFocus
        val start = min(physicalSelectionAnchor, physicalSelectionFocus)
        val end = max(physicalSelectionAnchor, physicalSelectionFocus)
        physicalSelectionRangeActive = start != end
        selection.predict(start, end)
        ic.setSelection(start, end)
        return true
    }

    private fun handlePhysicalSelectionModeKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (physicalSelectionMode) {
            return when {
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    movePhysicalSelectionFocus(if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                    t9ConsumedNavigationKeyUp = keyCode
                    true
                }
                keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                    physicalSelectionRangeActive = true
                    sendCombinationKeyEvents(keyCode, shift = true)
                    t9ConsumedNavigationKeyUp = keyCode
                    true
                }
                PhysicalT9KeyPolicy.isConfirmKey(keyCode) -> {
                    if (event.repeatCount == 0) {
                        val showActions = currentInputSelection.isNotEmpty()
                        exitPhysicalSelectionMode(showBadge = !showActions)
                        showPhysicalSelectionActionPanel()
                        t9ConsumedNavigationKeyUp = keyCode
                    }
                    true
                }
                PhysicalT9KeyPolicy.isDeleteKey(keyCode) -> {
                    exitPhysicalSelectionMode(showBadge = true)
                    false
                }
                else -> {
                    exitPhysicalSelectionMode(showBadge = true)
                    false
                }
            }
        }
        if (!PhysicalT9KeyPolicy.isConfirmKey(keyCode) || !canUsePhysicalSelectionMode()) return false
        if (event.repeatCount == 0) {
            pendingPhysicalSelectionOkKeyCode = keyCode
            physicalSelectionOkLongPressTriggered = false
            return true
        }
        if (pendingPhysicalSelectionOkKeyCode == keyCode) {
            if (!physicalSelectionOkLongPressTriggered &&
                keyHeldPastPhysicalLongPressDelay(event)
            ) {
                enterPhysicalSelectionMode()
                physicalSelectionOkLongPressTriggered = true
            }
            return true
        }
        return false
    }

    private fun handlePhysicalSelectionModeKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) return false
        if (pendingPhysicalSelectionOkKeyCode != keyCode) return false
        pendingPhysicalSelectionOkKeyCode = null
        val wasLongPress = physicalSelectionOkLongPressTriggered
        physicalSelectionOkLongPressTriggered = false
        if (!wasLongPress) {
            performDeferredPhysicalOkShortPress(keyCode)
        }
        return true
    }

    private fun markSelectionReplacementKeyIfNeeded(keyCode: Int, event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return
        if (inputDeviceMgr.isVirtualKeyboard) return
        val producesText = event.unicodeChar != 0 || keyCode == KeyEvent.KEYCODE_SPACE
        if (!producesText) return
        lastSelectionReplacementKeyUptime = SystemClock.uptimeMillis()
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
        physicalSelectionRangeActive = false
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

    fun requestImeInsetsRefresh() {
        inputView?.keyboardView?.requestLayout()
        inputView?.requestLayout()
        decorView.requestLayout()
        decorView.invalidate()
        decorView.requestApplyInsets()
        updateInputViewShown()
    }

    private var inputViewLocation = intArrayOf(0, 0)

    override fun onComputeInsets(outInsets: Insets) {
        if (inputDeviceMgr.isPassthroughInput) {
            val n = decorView.findViewById<View>(android.R.id.navigationBarBackground)?.height ?: 0
            val h = decorView.height - n
            outInsets.apply {
                contentTopInsets = h
                visibleTopInsets = h
                touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
            }
            return
        }
        // When using virtual keyboard OR T9 input mode (physical T9 phone with on-screen control bar),
        // make the area of keyboardView touchable so it can receive tap events.
        if (
            inputDeviceMgr.isVirtualKeyboard ||
            t9InputModeEnabled ||
            inputView?.isTemporaryPasswordKeyboardVisible() == true
        ) {
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
     * - SELECT-style confirm keys -> SPACE (for candidate selection)
     * - RETURN-style confirm keys -> ENTER
     * - KEYCODE_BACK (back) -> KEYCODE_DEL (backspace)
     * When NOT in input mode, pass through original events so normal phone usage works.
     */
    private fun mapKeyEvent(keyCode: Int, event: KeyEvent): Pair<Int, KeyEvent> {
        if (!inputDeviceMgr.isInInputMode) {
            return keyCode to event
        }
        val newKeyCode = PhysicalT9KeyPolicy.mappedInputModeKey(keyCode)
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

    enum class T9CandidateFocus {
        TOP,
        BOTTOM
    }

    private enum class T9EnglishCaseState {
        OFF,
        SHIFT_ONCE,
        CAPS
    }

    private var t9EnglishCaseState = T9EnglishCaseState.OFF
    private var t9CandidateFocus = T9CandidateFocus.BOTTOM
    private var t9ConsumedNavigationKeyUp: Int? = null
    private var physicalSelectionMode = false
    private var physicalSelectionActionPanelActive = false
    private var pendingPhysicalSelectionOkKeyCode: Int? = null
    private var physicalSelectionOkLongPressTriggered = false
    private var physicalSelectionAnchor = -1
    private var physicalSelectionFocus = -1
    private var physicalSelectionRangeActive = false

    private fun keyHeldPastPhysicalLongPressDelay(event: KeyEvent): Boolean =
        event.repeatCount > 0 &&
            event.eventTime - event.downTime >= physicalLongPressDelay.toLong()

    // ===== Multi-tap state for English mode =====
    private var multiTapLastKey = -1
    private var multiTapLastTime = 0L
    private var multiTapIndex = 0
    private var multiTapPendingChar: Char? = null
    private val englishDictionary = T9EnglishDictionary()
    private val smartEnglishSession = SmartEnglishT9Session(
        dictionary = englishDictionary,
        candidateLimit = SmartEnglishCandidateLimit,
        noMatchText = SmartEnglishNoMatchText
    )
    private val englishLearningWord = StringBuilder()
    private val MULTITAP_TIMEOUT = 1200L  // Increased from 500ms for easier typing

    private enum class T9PunctuationSet {
        CHINESE,
        ENGLISH
    }

    private data class T9PendingPunctuationState(
        val set: T9PunctuationSet = T9PunctuationSet.CHINESE,
        val index: Int = 0,
        val text: String? = null,
        val oneKeyDeferred: Boolean = false
    ) {
        val isPending: Boolean
            get() = text != null
    }

    private val t9ChinesePunctuation = listOf(
        "，", "。", "？", "！", "、", "：", "；", "…", "——", "“", "”", "‘", "’",
        "（", "）", "《", "》", "〈", "〉", "【", "】", "「", "」", "『", "』", "·",
        "～", "￥", "％", "＋", "－", "×", "÷", "＝", "℃"
    )
    private val t9EnglishPunctuation = listOf(
        ",", ".", "?", "!", "'", "\"", "-", "@", "/", ":", ";", "(", ")", "[", "]",
        "{", "}", "<", ">", "_", "+", "=", "*", "&", "#", "%", "$", "~", "`", "\\",
        "|", "^"
    )
    private var t9PendingPunctuation = T9PendingPunctuationState()

    private val chineseT9Session = ChineseT9CompositionSession()

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

    private val t9PunctuationTimeoutRunnable = Runnable {
        commitPendingT9Punctuation()
    }

    private fun showEnglishCaseStateOrRefreshPending() {
        val pendingChar = multiTapPendingChar
        if (pendingChar != null) {
            currentInputConnection?.setComposingText(applyEnglishCase(pendingChar).toString(), 1)
            return
        }
        inputView?.showModeIndicatorBadge(
            when (t9EnglishCaseState) {
                T9EnglishCaseState.OFF -> "abc"
                T9EnglishCaseState.SHIFT_ONCE -> "Abc"
                T9EnglishCaseState.CAPS -> "ABC"
            }
        )
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
        if (!t9InputModeEnabled || currentT9Mode != T9InputMode.CHINESE) return
        if (getT9InputState() != T9InputState.CHINESE_COMPOSING && !hasT9CompositionState()) {
            return
        }
        resetComposingState()
        clearTransientInputUiState()
        currentInputConnection?.finishComposingText()
        postFcitxJob {
            focusOutIn()
        }
    }

    fun getT9CandidateFocus(): T9CandidateFocus = t9CandidateFocus

    fun moveT9CandidateFocus(newFocus: T9CandidateFocus) {
        t9CandidateFocus = newFocus
        candidatesView?.syncT9CandidateFocus()
    }

    fun getT9CandidateFocusIndicatorColor(): Int = highlightColor

    private fun commitPendingT9PunctuationShortcut(keyCode: Int): Boolean {
        val index = PhysicalT9KeyPolicy.candidateShortcutIndex(keyCode) ?: return false
        return candidatesView?.commitT9PendingPunctuationShortcut(index) == true
    }

    private fun commitT9HanziShortcutFromLongPress(keyCode: Int): Boolean {
        val index = PhysicalT9KeyPolicy.candidateShortcutIndex(keyCode) ?: return false
        return candidatesView?.commitT9HanziShortcut(index) == true
    }

    private fun commitSmartEnglishShortcutFromLongPress(keyCode: Int): Boolean {
        val index = PhysicalT9KeyPolicy.candidateShortcutIndex(keyCode) ?: return false
        return candidatesView?.commitSmartEnglishShortcut(index) == true
    }

    private fun forwardChineseT9KeyShortPress(
        keyCode: Int,
        input: PhysicalT9KeyHandler.KeyInput
    ): Boolean {
        val down = KeyEvent(
            input.downTime,
            input.eventTime,
            KeyEvent.ACTION_DOWN,
            keyCode,
            0,
            input.metaState,
            input.deviceId,
            input.scanCode,
            input.flags,
            input.source
        )
        val up = KeyEvent(
            input.downTime,
            input.eventTime,
            KeyEvent.ACTION_UP,
            keyCode,
            0,
            input.metaState,
            input.deviceId,
            input.scanCode,
            input.flags,
            input.source
        )
        val sentDown = forwardKeyEvent(down)
        val sentUp = forwardKeyEvent(up)
        return sentDown || sentUp
    }

    private fun forwardChineseT9SeparatorShortPress(): Boolean {
        chineseT9Session.appendSeparator()
        candidatesView?.refreshT9Ui()
        postFcitxJob {
            val input = getRimeInput()
            if (input.isNotEmpty()) {
                replaceRimeInput(input.length, 0, "'", input.length + 1)
            }
        }
        return true
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
        showEnglishCaseStateOrRefreshPending()
    }

    private fun handleEnglishStarLongPress() {
        t9EnglishCaseState = when (t9EnglishCaseState) {
            T9EnglishCaseState.OFF, T9EnglishCaseState.SHIFT_ONCE -> T9EnglishCaseState.CAPS
            T9EnglishCaseState.CAPS -> T9EnglishCaseState.OFF
        }
        showEnglishCaseStateOrRefreshPending()
    }

    private fun activeT9PunctuationList(): List<String> = when (t9PendingPunctuation.set) {
        T9PunctuationSet.CHINESE -> t9ChinesePunctuation
        T9PunctuationSet.ENGLISH -> t9EnglishPunctuation
    }

    private fun showPendingT9Punctuation() {
        val punctuation = activeT9PunctuationList()[t9PendingPunctuation.index]
        t9PendingPunctuation = t9PendingPunctuation.copy(text = punctuation)
        clearTransientInputUiState()
        candidatesView?.refreshT9Ui()
        multiTapHandler.removeCallbacks(t9PunctuationTimeoutRunnable)
    }

    private fun deferSmartEnglishPunctuationKey() {
        t9PendingPunctuation = T9PendingPunctuationState(
            set = T9PunctuationSet.ENGLISH,
            oneKeyDeferred = true
        )
    }

    private fun showSmartEnglishPunctuationCandidates() {
        t9PendingPunctuation = T9PendingPunctuationState(set = T9PunctuationSet.ENGLISH)
        showPendingT9Punctuation()
    }

    fun getPendingT9PunctuationPaged(): FcitxEvent.PagedCandidateEvent.Data? {
        if (!t9PendingPunctuation.isPending) return null
        val punctuations = activeT9PunctuationList()
        return FcitxEvent.PagedCandidateEvent.Data(
            candidates = punctuations.map {
                FcitxEvent.Candidate(label = "", text = it, comment = "")
            }.toTypedArray(),
            cursorIndex = t9PendingPunctuation.index.coerceIn(punctuations.indices),
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )
    }

    fun commitPendingT9PunctuationCandidate(index: Int): Boolean {
        val punctuations = activeT9PunctuationList()
        if (!t9PendingPunctuation.isPending || index !in punctuations.indices) return false
        t9PendingPunctuation = t9PendingPunctuation.copy(index = index, text = punctuations[index])
        return commitPendingT9Punctuation()
    }

    fun previewPendingT9PunctuationCandidate(index: Int): Boolean {
        val punctuations = activeT9PunctuationList()
        if (!t9PendingPunctuation.isPending || index !in punctuations.indices) return false
        t9PendingPunctuation = t9PendingPunctuation.copy(index = index, text = punctuations[index])
        candidatesView?.refreshT9Ui()
        return true
    }

    private fun handleChinesePunctuationKey(): Boolean {
        multiTapHandler.removeCallbacks(t9PunctuationTimeoutRunnable)
        if (!t9PendingPunctuation.isPending && getT9CompositionKeyCount() > 0) {
            return true
        }
        if (!t9PendingPunctuation.isPending) {
            t9PendingPunctuation = T9PendingPunctuationState()
        } else {
            t9PendingPunctuation = t9PendingPunctuation.copy(
                index = (t9PendingPunctuation.index + 1) % activeT9PunctuationList().size
            )
        }
        showPendingT9Punctuation()
        return true
    }

    private fun togglePendingT9PunctuationSet(): Boolean {
        if (!t9PendingPunctuation.isPending) return false
        val nextSet = when (t9PendingPunctuation.set) {
            T9PunctuationSet.CHINESE -> T9PunctuationSet.ENGLISH
            T9PunctuationSet.ENGLISH -> T9PunctuationSet.CHINESE
        }
        val nextList = when (nextSet) {
            T9PunctuationSet.CHINESE -> t9ChinesePunctuation
            T9PunctuationSet.ENGLISH -> t9EnglishPunctuation
        }
        t9PendingPunctuation = t9PendingPunctuation.copy(
            set = nextSet,
            index = t9PendingPunctuation.index.coerceIn(nextList.indices)
        )
        showPendingT9Punctuation()
        return true
    }

    private fun commitPendingT9Punctuation(): Boolean {
        multiTapHandler.removeCallbacks(t9PunctuationTimeoutRunnable)
        val punctuation = t9PendingPunctuation.text ?: return false
        t9PendingPunctuation = T9PendingPunctuationState()
        commitText(punctuation)
        candidatesView?.refreshT9Ui()
        return true
    }

    private fun cancelPendingT9Punctuation(): Boolean {
        multiTapHandler.removeCallbacks(t9PunctuationTimeoutRunnable)
        if (!t9PendingPunctuation.isPending) return false
        t9PendingPunctuation = T9PendingPunctuationState()
        candidatesView?.refreshT9Ui()
        return true
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
            commitText(char.toString())
            recordEnglishLearningChar(char)
            multiTapPendingChar = null
            consumeEnglishShiftOnce()
        }
        multiTapLastKey = -1
        multiTapIndex = 0
        return hadPendingChar
    }

    private fun isSmartEnglishT9Active(): Boolean =
        t9InputModeEnabled && currentT9Mode == T9InputMode.ENGLISH && smartEnglishT9

    fun isSmartEnglishT9InputModeActive(): Boolean = isSmartEnglishT9Active()

    fun isSmartEnglishT9Enabled(): Boolean = smartEnglishT9

    fun toggleSmartEnglishT9() {
        smartEnglishT9 = !smartEnglishT9
        keyboardPrefs.smartEnglishT9.setValue(smartEnglishT9)
        resetSmartEnglishT9()
        if (smartEnglishT9) {
            preloadSmartEnglishDictionary()
        }
        inputView?.showModeIndicatorBadge(if (smartEnglishT9) "T9" else "abc")
    }

    private fun preloadSmartEnglishDictionary() {
        lifecycleScope.launch(Dispatchers.IO) {
            englishDictionary.preload()
        }
    }

    private fun resetSmartEnglishT9() {
        physicalT9KeyHandler.resetSmartEnglishPendingDigit()
        smartEnglishSession.reset()
        candidatesView?.refreshT9Ui()
    }

    fun getSmartEnglishT9Paged(): FcitxEvent.PagedCandidateEvent.Data? {
        if (!isSmartEnglishT9Active() || !smartEnglishSession.hasDigits) return null
        val shown = smartEnglishSession.visibleCandidates(::applyEnglishCaseToWord).map {
            FcitxEvent.Candidate(label = "", text = it, comment = "")
        }
        val cursor = smartEnglishSession.cursor.coerceIn(shown.indices)
        return FcitxEvent.PagedCandidateEvent.Data(
            candidates = shown.toTypedArray(),
            cursorIndex = cursor,
            layoutHint = FcitxEvent.PagedCandidateEvent.LayoutHint.Horizontal,
            hasPrev = false,
            hasNext = false
        )
    }

    fun getSmartEnglishT9Presentation(): T9PresentationState? {
        if (!isSmartEnglishT9Active() || !smartEnglishSession.hasDigits) return null
        val preview = smartEnglishSession.inputPreviewText()
        return T9PresentationState(
            topReading = formattedT9Text(applyEnglishCaseToWord(preview)),
            pinyinOptions = emptyList()
        )
    }

    fun moveSmartEnglishCandidate(delta: Int): Boolean {
        if (!isSmartEnglishT9Active()) return false
        if (!smartEnglishSession.moveCandidate(delta)) return false
        candidatesView?.refreshT9Ui()
        return true
    }

    fun setSmartEnglishCandidateIndex(index: Int): Boolean {
        if (!isSmartEnglishT9Active()) return false
        if (!smartEnglishSession.setCandidateIndex(index)) return false
        candidatesView?.refreshT9Ui()
        return true
    }

    fun commitSmartEnglishCandidate(index: Int? = null): Boolean {
        if (!isSmartEnglishT9Active() || !smartEnglishSession.hasDigits) return false
        val selected = smartEnglishSession.selectedRawCandidate(index) ?: run {
            resetSmartEnglishT9()
            return true
        }
        val committed = applyEnglishCaseToWord(selected)
        commitText(committed)
        resetSmartEnglishT9()
        consumeEnglishShiftOnce()
        return true
    }

    private fun applyEnglishCaseToWord(word: String): String = when (t9EnglishCaseState) {
        T9EnglishCaseState.OFF -> word
        T9EnglishCaseState.SHIFT_ONCE -> word.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        T9EnglishCaseState.CAPS -> word.uppercase()
    }

    private fun recordEnglishLearningChar(char: Char) {
        if (!shouldLearnEnglishWords()) {
            englishLearningWord.clear()
            return
        }
        if (char in 'a'..'z' || char in 'A'..'Z') {
            englishLearningWord.append(char.lowercaseChar())
        } else {
            flushEnglishLearningWord()
        }
    }

    private fun flushEnglishLearningWord() {
        if (!shouldLearnEnglishWords()) {
            englishLearningWord.clear()
            return
        }
        if (englishLearningWord.isNotEmpty()) {
            englishDictionary.learn(englishLearningWord.toString())
            englishLearningWord.clear()
        }
    }

    private fun shouldLearnEnglishWords(): Boolean {
        val info = currentInputEditorInfo
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return false
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return when (inputClass) {
            InputType.TYPE_CLASS_TEXT -> variation !in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )
            InputType.TYPE_CLASS_NUMBER -> variation != InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun handleSmartEnglishBackspace(): Boolean {
        if (!isSmartEnglishT9Active() || !smartEnglishSession.backspace()) return false
        candidatesView?.refreshT9Ui()
        return true
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
        commitPendingT9Punctuation()
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
        resetSmartEnglishT9()
        flushEnglishLearningWord()
        
        currentT9Mode = when (currentT9Mode) {
            T9InputMode.CHINESE -> T9InputMode.ENGLISH
            T9InputMode.ENGLISH -> T9InputMode.NUMBER
            T9InputMode.NUMBER -> T9InputMode.CHINESE
        }
        if (currentT9Mode == T9InputMode.ENGLISH && smartEnglishT9) {
            preloadSmartEnglishDictionary()
        }
        
        val modeName = getCurrentT9ModeLabel()
        onT9ModeChanged?.invoke(modeName)
        inputView?.showModeIndicatorBadge(modeName)
    }

    private fun commitLiteralT9Star(chineseState: T9InputState) {
        resetMultiTapState()
        val resetChineseEngine =
            currentT9Mode == T9InputMode.CHINESE && chineseState == T9InputState.CHINESE_COMPOSING
        if (resetChineseEngine) {
            clearTransientInputUiState()
        }
        commitText("*")
        if (resetChineseEngine) {
            postFcitxJob {
                focusOutIn()
            }
        }
    }

    private fun showNumberOperatorHintPanel() {
        numberModeController.showOperatorHintPanel()
    }

    private fun dismissNumberTransientPanel() {
        numberModeController.dismissTransientPanel()
    }

    private fun commitNumberModeOperator(operator: String): Boolean {
        return numberModeController.commitOperator(operator)
    }

    private fun handleNumberTransientPanelKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val result = numberModeController.handleTransientPanelKeyDown(keyCode, event)
        result.consumedKeyUp?.let {
            t9ConsumedNavigationKeyUp = it
        }
        return result.handled
    }

    private fun forwardKeyEvent(event: KeyEvent): Boolean {
        // reason to use a self increment index rather than timestamp:
        // KeyUp and KeyDown events actually can happen on the same time
        val timestamp = cachedKeyEventIndex++
        cachedKeyEvents.put(timestamp, event)
        val sym = KeySym.fromKeyEvent(event)
        if (sym != null) {
            if (t9InputModeEnabled && currentT9Mode == T9InputMode.CHINESE && event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DEL -> chineseT9Session.backspace()
                    KeyEvent.KEYCODE_1 -> chineseT9Session.appendSeparator()
                    else -> PhysicalT9KeyPolicy.t9Digit(event.keyCode)
                        ?.takeIf { it in 2..9 }
                        ?.let { chineseT9Session.appendDigit('0' + it) }
                }
                candidatesView?.waitForT9EngineCandidatesThenRefresh()
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
    fun getCurrentT9Segment(): String =
        chineseT9Session.currentSegment(::getFirstUnresolvedRawT9Segment)

    /**
     * Keep the Kotlin-side T9 model in sync with Rime's composing state.
     *
     * The tracker (driven by user key events in [forwardKeyEvent]) is the authoritative source
     * for the digit sequence the user actually typed. Rime's preedit here is a display form -
     * the t9 schema has `isDisplayOriginalPreedit: false`, so it arrives already converted
     * (e.g. "a" for "2", "ai'96" for "2496"). Parsing that display form would mis-read user
     * intent (take only the trailing digits and wipe the earlier segment).
     *
     * So we only use Rime's preedit here to detect empty state (Rime cleared/committed) and
     * to record a display fallback; we never rebuild `unresolvedDigits` from it.
     */
    fun syncT9CompositionWithInputPanel(data: FcitxEvent.InputPanelEvent.Data) {
        if (!t9InputModeEnabled || currentT9Mode != T9InputMode.CHINESE) return
        chineseT9Session.syncFromPreedit(data.preedit.toString())
    }

    /** Total number of digit keys (2-9) in current composition, for truncating first-row pinyin. */
    fun getT9CompositionKeyCount(): Int =
        chineseT9Session.keyCount()

    private fun getT9CompositionDigitSequence(): String =
        chineseT9Session.digitSequence()

    private fun getT9CompositionRawSequence(): String =
        chineseT9Session.rawSequence()

    private fun getFirstUnresolvedRawT9Segment(
        raw: String,
        resolved: List<T9ResolvedSegment>
    ): String {
        val remaining = removeResolvedPrefixFromRawT9Source(raw, resolved)
        return remaining
            .split('\'')
            .firstOrNull { segment -> segment.any { it in '2'..'9' } }
            ?.filter { it in '2'..'9' }
            .orEmpty()
    }

    private fun removeResolvedPrefixFromRawT9Source(
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

    private fun pinyinCharToT9Digit(char: Char): Char? =
        when (char.lowercaseChar()) {
            in 'a'..'c' -> '2'
            in 'd'..'f' -> '3'
            in 'g'..'i' -> '4'
            in 'j'..'l' -> '5'
            in 'm'..'o' -> '6'
            in 'p'..'s' -> '7'
            in 't'..'v' -> '8'
            in 'w'..'z' -> '9'
            else -> null
        }

    /** Candidate pinyin list for current T9 segment; empty if segment empty or not T9. */
    fun getT9PinyinCandidates(): List<String> =
        if (t9InputModeEnabled && currentT9Mode == T9InputMode.CHINESE)
            T9PinyinUtils.t9KeyToPinyin(getCurrentT9Segment())
        else
            emptyList()

    private fun formattedT9Text(text: String): FormattedText? {
        if (text.isEmpty()) return null
        return FormattedText(arrayOf(text), intArrayOf(TextFormatFlag.NoFlag.flag), -1)
    }

    private fun buildT9PreeditDisplay(raw: String): FormattedText? {
        if (raw.isEmpty()) return null
        val display = buildString {
            Regex("[2-9']+|[^2-9']+").findAll(raw).forEach { match ->
                val token = match.value
                if (token.all { it in '2'..'9' || it == '\'' }) {
                    append(token.split('\'').joinToString("'") { buildT9DigitSegmentDisplay(it) })
                } else {
                    append(token)
                }
            }
        }
        return formattedT9Text(display)
    }

    private fun buildT9DigitSegmentDisplay(digits: String): String {
        if (digits.isEmpty()) return ""
        val parts = mutableListOf<String>()
        var rest = digits
        while (rest.isNotEmpty()) {
            val pinyin = T9PinyinUtils.t9KeyToPinyin(rest).firstOrNull()
            val consumed = T9PinyinUtils.matchedPrefixLength(rest, pinyin)
            if (pinyin == null || consumed <= 0) {
                parts += rest.first().toString()
                rest = rest.drop(1)
            } else {
                parts += pinyin
                rest = rest.drop(consumed)
            }
        }
        return parts.joinToString(" ")
    }

    private fun buildT9CompositionModelDisplay(): FormattedText? {
        val model = chineseT9Session.model
        if (!model.hasResolvedSegments && model.unresolvedDigits.isEmpty()) {
            return null
        }
        if (model.rawPreedit.contains('\'')) {
            var rawDisplay = model.rawPreedit
            model.resolvedSegments.forEach { segment ->
                rawDisplay = rawDisplay.replaceFirst(segment.sourceDigits, segment.pinyin)
            }
            return buildT9PreeditDisplay(rawDisplay)
        }
        val parts = model.resolvedSegments.map { it.pinyin }.toMutableList()
        if (model.unresolvedDigits.isNotEmpty()) {
            parts += T9PinyinUtils.t9KeyToPinyin(model.unresolvedDigits).firstOrNull()
                ?: model.unresolvedDigits
        }
        return formattedT9Text(parts.joinToString(" "))
    }

    /** Preedit for the shared T9 display row; null if there is nothing to show. */
    fun getT9PreeditDisplay(rawComposition: String? = null): FormattedText? {
        if (!t9InputModeEnabled) return null
        return when (currentT9Mode) {
            T9InputMode.CHINESE -> {
                if (rawComposition != null) buildT9PreeditDisplay(rawComposition)
                else if (chineseT9Session.hasResolvedSegments) buildT9CompositionModelDisplay()
                else buildT9PreeditDisplay(chineseT9Session.fullComposition())
            }
            T9InputMode.ENGLISH -> {
                multiTapPendingChar?.let { pending ->
                    formattedT9Text(applyEnglishCase(pending).toString())
                }
            }
            T9InputMode.NUMBER -> null
        }
    }

    fun getT9PresentationState(
        inputPanel: FcitxEvent.InputPanelEvent.Data,
        paged: FcitxEvent.PagedCandidateEvent.Data
    ): T9PresentationState {
        if (!t9InputModeEnabled || currentT9Mode != T9InputMode.CHINESE) {
            return T9PresentationState(null, emptyList())
        }
        t9PendingPunctuation.text?.let {
            return T9PresentationState(
                topReading = formattedT9Text(it),
                pinyinOptions = emptyList()
            )
        }
        val comment = paged.candidates.getOrNull(paged.cursorIndex)?.comment
            ?: paged.candidates.firstOrNull()?.comment.orEmpty()
        val candidateReading = buildT9CandidatePreviewReading(normalizeT9CandidateComment(comment))
            .takeIf { it.isNotEmpty() }
            ?.let { formattedT9Text(it) }
        val localPreeditReading = getT9PreeditDisplay()
        val rawEndsWithSeparator = getT9CompositionRawSequence().lastOrNull() == '\''
        val model = chineseT9Session.model
        val topReading = if (model.hasResolvedSegments) {
            // The user picked a pinyin prefix; the top row must reflect their selection,
            // not whatever Rime's first unfiltered candidate happens to read.
            if (rawEndsWithSeparator) {
                localPreeditReading ?: buildT9CompositionModelDisplay() ?: candidateReading
            } else {
                candidateReading ?: buildT9CompositionModelDisplay()
            }
                ?: localPreeditReading
        } else {
            if (rawEndsWithSeparator) {
                localPreeditReading ?: candidateReading
            } else {
                candidateReading ?: localPreeditReading
            }
                ?: getT9PreeditDisplay(model.rawPreedit.takeIf { it.isNotEmpty() })
                ?: getT9PreeditDisplay(inputPanel.preedit.toString().takeIf { it.isNotEmpty() })
        }
        return T9PresentationState(
            topReading = topReading,
            pinyinOptions = getT9PinyinCandidates()
        )
    }

    /**
     * Filter Rime's Hanzi candidates to only those whose pinyin comment begins with the
     * selected pinyin prefix. This is the point of the pinyin selection feature: narrow
     * the Hanzi row to the reading the user chose.
     *
     * Note: Rime is still composing the full digit sequence (Option B), so this filter
     * only trims the current page client-side. If the current page has no matches, show
     * an empty candidate page with pagination state instead of falling back to unrelated
     * Hanzi; the user can move to the next page with the bottom-row Down key or arrows.
     */
    fun filterPagedByResolvedPinyin(
        paged: FcitxEvent.PagedCandidateEvent.Data
    ): FcitxEvent.PagedCandidateEvent.Data {
        if (!t9InputModeEnabled || currentT9Mode != T9InputMode.CHINESE) return paged
        val expected = getT9ResolvedPinyinPrefix() ?: return paged
        if (paged.candidates.isEmpty()) return paged
        val filteredList = paged.candidates.filter { candidate ->
            candidateMatchesT9ResolvedPrefix(candidate, expected)
        }
        if (filteredList.isEmpty()) {
            return paged.copy(candidates = emptyArray(), cursorIndex = -1)
        }
        if (filteredList.size == paged.candidates.size) return paged
        val originallyHighlighted = paged.candidates.getOrNull(paged.cursorIndex)
        val newCursor = originallyHighlighted
            ?.let { filteredList.indexOf(it) }
            ?.takeIf { it >= 0 }
            ?: 0
        return paged.copy(
            candidates = filteredList.toTypedArray(),
            cursorIndex = newCursor
        )
    }

    fun getT9ResolvedPinyinPrefix(): String? {
        if (!t9InputModeEnabled || currentT9Mode != T9InputMode.CHINESE) return null
        val resolved = chineseT9Session.resolvedSegments
        if (resolved.isEmpty()) return null
        return resolved.joinToString(" ") { it.pinyin }
    }

    fun hasPendingT9PinyinSelection(): Boolean {
        return t9InputModeEnabled &&
            currentT9Mode == T9InputMode.CHINESE &&
            chineseT9Session.pendingSelection != null
    }

    fun getT9ResolvedPinyinFilterPrefixes(): List<String> {
        val resolvedSegments = chineseT9Session.resolvedSegments
        val resolved = resolvedSegments.map { it.pinyin }
        if (resolved.isEmpty()) return emptyList()
        if (chineseT9Session.pendingSelection != null ||
            resolvedSegments.all { it.engineBacked }
        ) {
            return emptyList()
        }
        return (resolved.size downTo 1)
            .map { count -> resolved.take(count).joinToString(" ") }
            .distinct()
    }

    fun isT9PartialResolvedPinyinPrefix(prefix: String): Boolean {
        val full = getT9ResolvedPinyinPrefix() ?: return false
        return prefix.isNotEmpty() && prefix != full
    }

    fun shouldConsumeT9ResolvedPinyinPrefixAfterHanziSelection(
        prefix: String,
        candidate: FcitxEvent.Candidate
    ): Boolean {
        if (isT9PartialResolvedPinyinPrefix(prefix)) return true
        if (prefix != getT9ResolvedPinyinPrefix()) return false
        if (chineseT9Session.unresolvedDigits.isEmpty()) return false
        return normalizeT9CandidateComment(candidate.comment) == prefix
    }

    fun consumeT9ResolvedPinyinPrefix(prefix: String): Boolean {
        if (!t9InputModeEnabled || currentT9Mode != T9InputMode.CHINESE) return false
        val rawPreedit = chineseT9Session.consumeResolvedPrefix(
            prefix = prefix,
            removeResolvedPrefixFromRawSource = ::removeResolvedPrefixFromRawT9Source,
            firstUnresolvedRawSegment = ::getFirstUnresolvedRawT9Segment
        )
            ?: return false
        candidatesView?.prepareForT9CompositionReplay()
        inputView?.clearTransientState()
        replayT9RawComposition(rawPreedit)
        return true
    }

    private fun replayT9RawComposition(rawPreedit: String) {
        chineseT9Session.prepareReplay(rawPreedit)
        postFcitxJob {
            setCandidatePagingMode(candidatePagingModeForCurrentInputDevice())
            reset()
            rawPreedit.forEach { ch ->
                if (ch in '2'..'9' || ch == '\'') {
                    sendKey(ch)
                }
            }
        }
    }

    fun candidateMatchesT9ResolvedPrefix(
        candidate: FcitxEvent.Candidate,
        expected: String
    ): Boolean {
        val normalized = normalizeT9CandidateComment(candidate.comment)
        val expectedSegments = resolvedSegmentsForT9FilterPrefix(expected)
        if (!expectedSegments.isNullOrEmpty()) {
            val commentSegments = normalized
                .split(' ')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
            if (commentSegments.size >= expectedSegments.size &&
                expectedSegments.indices.all { index ->
                    commentSegmentMatchesResolvedSegment(commentSegments[index], expectedSegments[index])
                }
            ) {
                return true
            }
        }
        return normalized == expected || normalized.startsWith("$expected ")
    }

    private fun normalizeT9CandidateComment(comment: String): String =
        comment.replace('\'', ' ').trim().lowercase()

    private fun buildT9CandidatePreviewReading(normalizedComment: String): String {
        if (normalizedComment.isEmpty()) return ""
        val rawTyped = getT9CompositionRawSequence()
        if (rawTyped.contains('\'')) {
            return buildT9SeparatorAwareCandidatePreviewReading(normalizedComment, rawTyped)
        }
        val typedDigits = getT9CompositionDigitSequence()
        if (typedDigits.isEmpty()) return ""
        val segments = normalizedComment.split(' ').filter { it.isNotEmpty() }
        val parts = mutableListOf<String>()
        var typedIndex = 0
        var mergeNextPart = false
        segments.forEach { segment ->
            if (typedIndex >= typedDigits.length) return@forEach
            val part = StringBuilder()
            var skippedBeforeFirstMatch = false
            var skippedAfterMatch = false
            segment.forEach { char ->
                if (typedIndex >= typedDigits.length) return@forEach
                val digit = pinyinCharToT9Digit(char) ?: return@forEach
                if (digit == typedDigits[typedIndex]) {
                    if (parts.isEmpty() && part.isEmpty() && skippedBeforeFirstMatch) {
                        return ""
                    }
                    part.append(char)
                    typedIndex++
                } else if (part.isEmpty()) {
                    skippedBeforeFirstMatch = true
                } else {
                    skippedAfterMatch = true
                }
            }
            if (part.isNotEmpty()) {
                if (mergeNextPart && parts.isNotEmpty()) {
                    parts[parts.lastIndex] = parts.last() + part
                } else {
                    parts += part.toString()
                }
                mergeNextPart = skippedAfterMatch && typedIndex < typedDigits.length
            }
        }
        if (typedIndex < typedDigits.length) {
            buildT9DigitSegmentDisplay(typedDigits.drop(typedIndex))
                .takeIf { it.isNotEmpty() }
                ?.let {
                    if (mergeNextPart && parts.isNotEmpty()) {
                        parts[parts.lastIndex] = parts.last() + it.replace(" ", "")
                    } else {
                        parts += it
                    }
                }
        }
        return parts.joinToString(" ")
    }

    private fun buildT9SeparatorAwareCandidatePreviewReading(
        normalizedComment: String,
        rawTyped: String
    ): String {
        val typedSegments = rawTyped.filter { it in '2'..'9' || it == '\'' }
            .split('\'')
            .map { segment -> segment.filter { it in '2'..'9' } }
        if (typedSegments.isEmpty()) return ""
        val commentSegments = normalizedComment.split(' ').filter { it.isNotEmpty() }
        var commentIndex = 0
        var resolvedIndex = 0
        val parts = typedSegments.map { digits ->
            if (digits.isEmpty()) return@map ""
            val resolved = chineseT9Session.resolvedSegments.getOrNull(resolvedIndex)
                ?.takeIf { digits.startsWith(it.sourceDigits) }
            val resolvedDisplay = resolved?.pinyin.orEmpty()
            var remainingDigits = digits
            if (resolved != null) {
                remainingDigits = digits.drop(resolved.sourceDigits.length)
                commentIndex = advanceT9PreviewCommentIndexForResolved(
                    commentSegments,
                    commentIndex,
                    resolved
                )
                resolvedIndex++
            }
            if (remainingDigits.isEmpty()) {
                resolvedDisplay
            } else {
                val (candidateDisplay, nextCommentIndex) =
                    buildT9CandidatePreviewForRawSegment(
                        commentSegments,
                        commentIndex,
                        remainingDigits
                    )
                commentIndex = nextCommentIndex
                resolvedDisplay + (
                    candidateDisplay ?: buildT9DigitSegmentDisplay(remainingDigits).replace(" ", "")
                )
            }
        }
        val display = parts.joinToString("'")
        return if (rawTyped.lastOrNull() == '\'') "$display'" else display
    }

    private fun advanceT9PreviewCommentIndexForResolved(
        commentSegments: List<String>,
        startIndex: Int,
        resolved: T9ResolvedSegment
    ): Int {
        if (commentSegments.getOrNull(startIndex)
                ?.let { commentSegmentMatchesResolvedSegment(it, resolved) } == true
        ) {
            return startIndex + 1
        }
        return (startIndex + 1).coerceAtMost(commentSegments.size)
    }

    private fun buildT9CandidatePreviewForRawSegment(
        commentSegments: List<String>,
        startIndex: Int,
        typedDigits: String
    ): Pair<String?, Int> {
        if (typedDigits.isEmpty()) return null to startIndex
        val display = StringBuilder()
        var commentIndex = startIndex
        var typedIndex = 0
        while (typedIndex < typedDigits.length && commentIndex < commentSegments.size) {
            val (part, nextTypedIndex) = matchT9CandidatePreviewSyllable(
                commentSegments[commentIndex],
                typedDigits,
                typedIndex
            )
            if (part.isEmpty() || nextTypedIndex == typedIndex) break
            display.append(part)
            typedIndex = nextTypedIndex
            commentIndex++
        }
        if (display.isEmpty()) return null to startIndex
        if (typedIndex < typedDigits.length) {
            display.append(buildT9DigitSegmentDisplay(typedDigits.drop(typedIndex)).replace(" ", ""))
        }
        return display.toString() to commentIndex
    }

    private fun matchT9CandidatePreviewSyllable(
        commentSegment: String,
        typedDigits: String,
        startIndex: Int
    ): Pair<String, Int> {
        val part = StringBuilder()
        var typedIndex = startIndex
        var skippedBeforeFirstMatch = false
        commentSegment.forEach { char ->
            if (typedIndex >= typedDigits.length) return@forEach
            val digit = pinyinCharToT9Digit(char) ?: return@forEach
            if (digit == typedDigits[typedIndex]) {
                if (part.isEmpty() && skippedBeforeFirstMatch) return "" to startIndex
                part.append(char)
                typedIndex++
            } else if (part.isEmpty()) {
                skippedBeforeFirstMatch = true
            }
        }
        return part.toString() to typedIndex
    }

    private fun resolvedSegmentsForT9FilterPrefix(prefix: String): List<T9ResolvedSegment>? {
        val resolved = chineseT9Session.resolvedSegments
        for (count in 1..resolved.size) {
            val segments = resolved.take(count)
            if (segments.joinToString(" ") { it.pinyin } == prefix) {
                return segments
            }
        }
        return null
    }

    private fun commentSegmentMatchesResolvedSegment(
        commentSegment: String,
        resolvedSegment: T9ResolvedSegment
    ): Boolean {
        if (commentSegment == resolvedSegment.pinyin) return true
        return T9PinyinUtils.pinyinToT9Keys(commentSegment) == resolvedSegment.sourceDigits
    }

    /**
     * Record the user's pinyin choice and try to narrow Rime itself by replacing the matching
     * T9 digit span with "pinyin'". The composition session owns the Kotlin-side state; this
     * adapter only mirrors that selection into Rime.
     */
    fun selectT9Pinyin(pinyin: String) {
        val selection = chineseT9Session.selectPinyin(pinyin) ?: return
        replaceT9SelectedDigitsInEngine(
            selection.selectedSegment,
            selection.originalSegment,
            selection.remainingDigits,
            consumeExplicitSeparator = selection.consumeExplicitSeparator,
            replaceFromStart = selection.replaceFromStart
        )
    }

    private fun replaceT9SelectedDigitsInEngine(
        selectedSegment: T9ResolvedSegment,
        originalSegment: String,
        remainingDigits: String,
        consumeExplicitSeparator: Boolean = false,
        replaceFromStart: Boolean = false
    ) {
        val request = ChineseT9CompositionSession.PinyinSelectionRequest(
            selectedSegment = selectedSegment,
            originalSegment = originalSegment,
            remainingDigits = remainingDigits,
            consumeExplicitSeparator = consumeExplicitSeparator,
            replaceFromStart = replaceFromStart
        )
        postFcitxJob {
            ChineseT9RimeBridge.from(chineseT9Session, this).mirrorPinyinSelection(request)
            this@FcitxInputMethodService.lifecycleScope.launch {
                candidatesView?.refreshT9Ui()
            }
        }
    }

    fun commitT9PinyinSelection(pinyin: String): Boolean {
        if (pinyin.isEmpty()) return false
        selectT9Pinyin(pinyin)
        moveT9CandidateFocus(T9CandidateFocus.BOTTOM)
        candidatesView?.refreshT9Ui()
        return true
    }

    fun consumeT9PinyinFromSelectedCandidate(candidate: FcitxEvent.Candidate): Boolean {
        if (!t9InputModeEnabled || currentT9Mode != T9InputMode.CHINESE) return false
        val commentSegments = normalizeT9CandidateComment(candidate.comment)
            .split(' ')
            .filter { it.isNotEmpty() }
        if (!chineseT9Session.consumeSelectedCandidateReading(commentSegments)) return false
        candidatesView?.refreshT9Ui()
        return true
    }

    fun commitHighlightedT9Pinyin(): Boolean {
        val pinyin = candidatesView?.getHighlightedT9Pinyin() ?: return false
        return commitT9PinyinSelection(pinyin)
    }

    private fun playPhysicalKeySound(keyCode: Int, event: KeyEvent) {
        if (!physicalKeySound) return
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return
        val effect = when (keyCode) {
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_FORWARD_DEL -> InputFeedbacks.SoundEffect.Delete
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> InputFeedbacks.SoundEffect.Return
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BUTTON_SELECT -> InputFeedbacks.SoundEffect.SpaceBar
            else -> InputFeedbacks.SoundEffect.Standard
        }
        InputFeedbacks.soundEffect(effect)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (inputDeviceMgr.shouldPassThroughHardwareKeys) {
            return super.onKeyDown(keyCode, event)
        }
        playPhysicalKeySound(keyCode, event)
        if (handleNumberTransientPanelKeyDown(keyCode, event)) {
            return true
        }
        if (handlePhysicalSelectionActionPanelKeyDown(keyCode, event)) {
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN &&
            isTemporaryPasswordKeyboardVisible() &&
            PhysicalT9KeyPolicy.isHorizontalFocusKey(keyCode)
        ) {
            handleArrowKey(keyCode)
            t9ConsumedNavigationKeyUp = keyCode
            return true
        }
        if (handlePhysicalPasswordBackspaceKey(keyCode, event)) {
            return true
        }
        if (commitPhysicalPasswordLiteralKey(keyCode, event)) {
            return true
        }

        // When selection mode is already active, let it consume selection controls
        // or exit before T9 special keys commit replacement text.
        if (physicalSelectionMode && handlePhysicalSelectionModeKeyDown(keyCode, event)) {
            return true
        }

        val physicalT9Result = physicalT9KeyHandler.handleKeyDown(keyCode, event)
        physicalT9Result.consumedKeyUp?.let {
            t9ConsumedNavigationKeyUp = it
        }
        if (physicalT9Result.handled) {
            return true
        }

        if (handlePhysicalSelectionModeKeyDown(keyCode, event)) {
            return true
        }

        val (mappedKeyCode, mappedEvent) = mapKeyEvent(keyCode, event)
        if (mappedKeyCode == KeyEvent.KEYCODE_DEL &&
            mappedEvent.action == KeyEvent.ACTION_DOWN &&
            mappedEvent.repeatCount == 0 &&
            shouldHideImeForEmptyEditorDelete()
        ) {
            requestHideSelf(0)
            t9ConsumedNavigationKeyUp = keyCode
            return true
        }
        // Intercept physical DEL (including BACK remapped to DEL) when the only thing left to
        // undo is a previously selected pinyin segment; reopen it as digits instead of letting
        // Rime swallow another letter.
        if (mappedKeyCode == KeyEvent.KEYCODE_DEL &&
            mappedEvent.action == KeyEvent.ACTION_DOWN &&
            shouldReopenLastResolvedSegment()
        ) {
            postFcitxJob {
                if (popLastResolvedSegment(this)) {
                    this@FcitxInputMethodService.lifecycleScope.launch {
                        candidatesView?.refreshT9Ui()
                    }
                }
            }
            t9ConsumedNavigationKeyUp = keyCode
            return true
        }
        if (mappedKeyCode == KeyEvent.KEYCODE_DEL &&
            mappedEvent.action == KeyEvent.ACTION_DOWN &&
            shouldDirectDeleteForIdlePhysicalBackspace() &&
            deleteBeforeCursorDirectly()
        ) {
            t9ConsumedNavigationKeyUp = keyCode
            return true
        }
        markSelectionReplacementKeyIfNeeded(mappedKeyCode, mappedEvent)
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
        if (inputDeviceMgr.shouldPassThroughHardwareKeys) {
            return super.onKeyUp(keyCode, event)
        }
        if (handlePhysicalSelectionModeKeyUp(keyCode, event)) {
            return true
        }
        if (t9ConsumedNavigationKeyUp == keyCode) {
            t9ConsumedNavigationKeyUp = null
            return true
        }
        val physicalT9Result = physicalT9KeyHandler.handleKeyUp(keyCode, event)
        physicalT9Result.consumedKeyUp?.let {
            t9ConsumedNavigationKeyUp = it
        }
        if (physicalT9Result.handled) {
            return true
        }

        val (mappedKeyCode, mappedEvent) = mapKeyEvent(keyCode, event)
        return forwardKeyEvent(mappedEvent) || super.onKeyUp(mappedKeyCode, mappedEvent)
    }

    // Added in API level 14, deprecated in 29
    // it's needed because editors still use it even on API 36
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onViewClicked(focusChanged: Boolean) {
        super.onViewClicked(focusChanged)
        lastEditorTouchUptime = SystemClock.uptimeMillis()
        clearChineseT9CompositionFromEditorTap()
        inputDeviceMgr.evaluateOnViewClicked(this)
    }

    @RequiresApi(34)
    override fun onUpdateEditorToolType(toolType: Int) {
        super.onUpdateEditorToolType(toolType)
        if (toolType == MotionEvent.TOOL_TYPE_FINGER || toolType == MotionEvent.TOOL_TYPE_STYLUS) {
            lastEditorTouchUptime = SystemClock.uptimeMillis()
        }
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
        clearPasswordInputPreview()
        // update selection as soon as possible
        // sometimes when restarting input, onUpdateSelection happens before onStartInput, and
        // initialSel{Start,End} is outdated. but it's the client app's responsibility to send
        // right cursor position, try to workaround this would simply introduce more bugs.
        selection.resetTo(attribute.initialSelStart, attribute.initialSelEnd)
        updateSelectionBackCallback(attribute.initialSelStart != attribute.initialSelEnd)
        resetComposingState()
        resetPhysicalSelectionState()
        val flags = CapabilityFlags.fromEditorInfo(attribute)
        val fcitxFlags = if (shouldKeepTemporaryPasswordModeOnRestart(restarting, flags)) {
            passwordModeCapabilityFlags(flags)
        } else {
            flags
        }
        capabilityFlags = flags
        // EditorInfo may change between onStartInput and onStartInputView
        inputDeviceMgr.notifyOnStartInput(attribute)
        Timber.d(
            "onStartInput: initialSel=${selection.current}, restarting=$restarting, " +
                "package=${attribute.packageName}, inputType=${attribute.inputType}, " +
                "actionId=${attribute.actionId}, actionLabel=${attribute.actionLabel}, " +
                "imeOptions=${attribute.imeOptions}, privateImeOptions=${attribute.privateImeOptions}"
        )
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
            setCapFlags(fcitxFlags)
            // for hardware keyboard, focus to allow switching input methods before onStartInputView
            if (!isNullType && !inputDeviceMgr.isPassthroughInput) {
                focus(true)
            }
            enforceHalfWidthForT9()
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        Timber.d("onStartInputView: restarting=$restarting")
        capabilityFlags = CapabilityFlags.fromEditorInfo(info)
        postFcitxJob {
            focus(true)
        }
        val shouldStartInputView = inputDeviceMgr.evaluateOnStartInputView(info, this) ||
            (!inputDeviceMgr.isPassthroughInput &&
                (capabilityFlags.has(CapabilityFlag.Password) ||
                    inputView?.isTemporaryPasswordKeyboardVisible() == true))
        if (shouldStartInputView) {
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
        if (deleteCollapsedSelectionIfNeeded(oldSelStart, oldSelEnd, newSelStart, newSelEnd)) {
            inputView?.updateSelection(newSelStart, newSelEnd)
            return
        }
        handleCursorUpdate(newSelStart, newSelEnd, cursorUpdateIndex)
        if (physicalSelectionMode && physicalSelectionAnchor >= 0) {
            physicalSelectionFocus = when (physicalSelectionAnchor) {
                newSelStart -> newSelEnd
                newSelEnd -> newSelStart
                else -> newSelEnd
            }
        } else if (physicalSelectionRangeActive && newSelStart == newSelEnd) {
            physicalSelectionRangeActive = false
        }
        updateSelectionBackCallback(newSelStart != newSelEnd)
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
            if (t9InputModeEnabled && currentT9Mode == T9InputMode.CHINESE) {
                clearT9CompositionState()
                clearTransientInputUiState()
                currentInputConnection?.finishComposingText()
            }
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
            clearTransientInputUiState()
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
        updateSelectionBackCallback(false)
        decorLocationUpdated = false
        inputDeviceMgr.onFinishInputView()
        currentInputConnection?.apply {
            finishComposingText()
            monitorCursorAnchor(false)
        }
        resetComposingState()
        resetSmartEnglishT9()
        flushEnglishLearningWord()
        candidatesView?.clearTransientState()
        inputView?.clearTransientState()
        clearPasswordInputPreview()
        postFcitxJob {
            focusOutIn()
        }
        showingDialog?.dismiss()
    }

    override fun onFinishInput() {
        Timber.d("onFinishInput")
        updateSelectionBackCallback(false)
        inputDeviceMgr.onFinishInput()
        clearT9CompositionState()
        resetSmartEnglishT9()
        flushEnglishLearningWord()
        candidatesView?.clearTransientState()
        inputView?.clearTransientState()
        clearPasswordInputPreview()
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
        updateSelectionBackCallback(false)
        recreateInputViewPrefs.forEach {
            it.unregisterOnChangeListener(recreateInputViewListener)
        }
        keyboardPrefs.inlineSuggestions.unregisterOnChangeListener(inlineSuggestionsChangeListener)
        keyboardPrefs.passwordInputPreview.unregisterOnChangeListener(
            passwordInputPreviewEnabledChangeListener
        )
        keyboardPrefs.physicalKeySound.unregisterOnChangeListener(physicalKeySoundChangeListener)
        keyboardPrefs.useT9KeyboardLayout.unregisterOnChangeListener(t9InputModeEnabledChangeListener)
        keyboardPrefs.smartEnglishT9.unregisterOnChangeListener(smartEnglishT9ChangeListener)
        keyboardPrefs.longPressDelay.unregisterOnChangeListener(physicalLongPressDelayChangeListener)
        prefs.advanced.ignoreSystemCursor.unregisterOnChangeListener(ignoreSystemCursorChangeListener)
        prefs.keyboard.inputUiFont.unregisterOnChangeListener(recreateInputViewsListener)
        keyboardPrefs.passwordInputPreview.unregisterOnChangeListener(passwordInputPreviewChangeListener)
        prefs.candidates.unregisterOnChangeListener(recreateCandidatesViewListener)
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
        private const val EDITOR_TOUCH_SELECTION_CANCEL_WINDOW_MS = 300L
        private const val EXPLICIT_SELECTION_DELETE_WINDOW_MS = 500L
        private const val SELECTION_REPLACEMENT_KEY_WINDOW_MS = 300L
        private const val SmartEnglishCandidateLimit = 80
        private const val SmartEnglishNoMatchText = "No match"
        private val DIGIT_TEXTS = arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
    }
}
