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
import org.fcitx.fcitx5.android.input.t9.ChineseT9CompositionCoordinator
import org.fcitx.fcitx5.android.input.t9.ChineseT9CompositionLifecycle
import org.fcitx.fcitx5.android.input.t9.ChineseT9InputSnapshot
import org.fcitx.fcitx5.android.input.t9.ChineseT9PresentationSnapshotKey
import org.fcitx.fcitx5.android.input.t9.ChineseT9PresentationSource
import org.fcitx.fcitx5.android.input.t9.PhysicalT9KeyHandler
import org.fcitx.fcitx5.android.input.t9.PhysicalT9KeyHostAdapter
import org.fcitx.fcitx5.android.input.t9.PhysicalT9KeyPolicy
import org.fcitx.fcitx5.android.input.t9.SmartEnglishT9Coordinator
import org.fcitx.fcitx5.android.input.t9.SmartEnglishCaseCoordinator
import org.fcitx.fcitx5.android.input.t9.SmartEnglishLearningPolicy
import org.fcitx.fcitx5.android.input.t9.SmartEnglishT9ModeController
import org.fcitx.fcitx5.android.input.t9.T9CandidateFocus
import org.fcitx.fcitx5.android.input.t9.T9CandidateFocusController
import org.fcitx.fcitx5.android.input.t9.T9CandidateShortcutCommitter
import org.fcitx.fcitx5.android.input.t9.T9InputMode
import org.fcitx.fcitx5.android.input.t9.T9ModeCoordinator
import org.fcitx.fcitx5.android.input.t9.T9MultiTapCoordinator
import org.fcitx.fcitx5.android.input.t9.T9PresentationState
import org.fcitx.fcitx5.android.input.t9.T9PunctuationCoordinator
import org.fcitx.fcitx5.android.input.t9.T9ResponsivenessTrace
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
    private val t9CandidateShortcutCommitter = T9CandidateShortcutCommitter(
        commitPendingPunctuationIndex = { index ->
            candidatesView?.commitT9PendingPunctuationShortcut(index) == true
        },
        commitHanziIndex = { index ->
            candidatesView?.commitT9HanziShortcut(index) == true
        },
        commitSmartEnglishIndex = { index ->
            candidatesView?.commitSmartEnglishShortcut(index) == true
        }
    )
    private val physicalT9KeyHost = PhysicalT9KeyHostAdapter(
        state = PhysicalT9KeyHostAdapter.State(
            isInInputMode = { inputDeviceMgr.isInInputMode },
            mode = { currentT9Mode },
            isSmartEnglishActive = ::isSmartEnglishT9Active,
            chineseComposing = { getT9InputState() == ChineseT9CompositionLifecycle.InputState.COMPOSING },
            compositionKeyCount = ::getT9CompositionKeyCount,
            hasPendingPunctuation = { t9PunctuationCoordinator.isPending },
            pendingPunctuationOneKeyDeferred = { t9PunctuationCoordinator.oneKeyDeferred },
            pendingPunctuationSet = { t9PunctuationCoordinator.physicalSet },
            hasSmartEnglishDigits = { smartEnglishCoordinator.hasDigits },
            hasSmartEnglishCandidates = { smartEnglishCoordinator.hasCandidates },
            hasMultiTapPendingChar = { t9MultiTapCoordinator.hasPendingChar },
            hasTopPinyinCandidates = { getT9PinyinCandidates().isNotEmpty() },
            hasBottomCandidateRow = { candidatesView?.hasT9BottomCandidateRow() == true },
            candidateFocus = ::getT9CandidateFocus,
            keyHeldPastLongPressDelay = { input ->
                input.repeatCount > 0 &&
                    input.eventTime - input.downTime >= physicalLongPressDelay.toLong()
            }
        ),
        punctuation = PhysicalT9KeyHostAdapter.PunctuationActions(
            setOneKeyDeferred = { value -> t9PunctuationCoordinator.setOneKeyDeferred(value) },
            commitShortcut = t9CandidateShortcutCommitter::commitPendingPunctuationKey,
            commit = { t9PunctuationCoordinator.commit() },
            cancel = { t9PunctuationCoordinator.cancel() },
            handleChineseKey = {
                t9PunctuationCoordinator.handleChineseKey(
                    hasCompositionKeys = getT9CompositionKeyCount() > 0
                )
            },
            toggleSet = { t9PunctuationCoordinator.toggleSet() },
            showSmartEnglishCandidates = { t9PunctuationCoordinator.showEnglishCandidates() }
        ),
        english = PhysicalT9KeyHostAdapter.EnglishActions(
            commitSmartEnglishShortcut = t9CandidateShortcutCommitter::commitSmartEnglishKey,
            appendSmartEnglishDigit = { digit -> smartEnglishCoordinator.appendDigit(digit) },
            resetSmartEnglish = { smartEnglishCoordinator.reset() },
            commitSmartEnglishCandidate = { appendSpace, continuePrediction ->
                smartEnglishCoordinator.commitCandidate(
                    appendSpace = appendSpace,
                    continuePrediction = continuePrediction
                )
            },
            moveSmartEnglishCandidate = { delta -> smartEnglishCoordinator.moveCandidate(delta) },
            smartEnglishBackspace = { smartEnglishCoordinator.backspace() },
            flushLearningWord = { smartEnglishCoordinator.flushLearningWord() },
            handleStarShortPress = { smartEnglishCaseCoordinator.handleShortPress() },
            handleStarLongPress = { smartEnglishCaseCoordinator.handleLongPress() },
            handleMultiTapKey = { keyCode -> t9MultiTapCoordinator.handleKey(keyCode) },
            commitMultiTapChar = { t9MultiTapCoordinator.commitPending() },
            cancelMultiTapChar = { t9MultiTapCoordinator.cancelPending() }
        ),
        candidates = PhysicalT9KeyHostAdapter.CandidateActions(
            commitHanziShortcut = t9CandidateShortcutCommitter::commitHanziKey,
            moveFocus = ::moveT9CandidateFocus,
            moveHighlightedPinyin = { delta -> candidatesView?.moveHighlightedT9Pinyin(delta) == true },
            moveHighlightedBottomCandidate = { delta -> candidatesView?.moveHighlightedT9BottomCandidate(delta) == true },
            offsetBottomCandidatePage = { delta -> candidatesView?.offsetT9BottomCandidatePage(delta) == true },
            commitHighlightedPinyin = ::commitHighlightedT9Pinyin,
            commitHighlightedBottomCandidate = { candidatesView?.commitHighlightedT9BottomCandidate() == true }
        ),
        platform = PhysicalT9KeyHostAdapter.PlatformActions(
            switchToNextMode = ::switchToNextT9Mode,
            commitText = ::commitText,
            commitNumberOperatorForKey = { keyCode, fallbackDigit ->
                val operator = numberModeController.operatorForKey(keyCode) ?: DIGIT_TEXTS[fallbackDigit]
                commitNumberModeOperator(operator)
            },
            showNumberOperatorHintPanel = ::showNumberOperatorHintPanel,
            commitLiteralStarInCurrentChineseState = { commitLiteralT9Star(getT9InputState()) },
            handleReturnKey = ::handleReturnKey,
            forwardChineseT9KeyShortPress = ::forwardChineseT9KeyShortPress,
            forwardChineseT9SeparatorShortPress = ::forwardChineseT9SeparatorShortPress,
            discardChineseCompositionForModeSwitch = ::discardChineseCompositionForModeSwitch
        )
    )
    private val physicalT9KeyHandler = PhysicalT9KeyHandler(physicalT9KeyHost)

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
        chineseT9Composition.clear()
    }

    private fun hasT9CompositionState(): Boolean =
        chineseT9Composition.hasState()

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
        chineseT9Composition.backspaceFromVirtualKey()
        refreshT9UiOnMain()
        return false
    }

    /**
     * Delete should first undo any selected pinyin segment before shrinking the digit input.
     * While a pinyin is selected, the user's most recent decision is the selection itself; a
     * single delete rolls that back rather than editing the trailing digit suffix.
     */
    private fun shouldReopenLastResolvedSegment(): Boolean =
        chineseT9Composition.shouldReopenLastResolvedSegment(isChineseT9InputModeActive())

    /**
     * Undo the last pinyin selection by prepending its source digits to the unresolved suffix.
     * Engine-backed selections are also restored inside Rime so candidate narrowing follows the
     * same state as the Kotlin presentation model.
     */
    private suspend fun popLastResolvedSegment(fcitx: FcitxAPI): Boolean {
        return chineseT9Composition.popLastResolvedSegment(
            api = fcitx,
            candidatePagingMode = candidatePagingModeForCurrentInputDevice()
        )
    }

    fun clearHiddenChineseT9CompositionIfCandidateUiSuppressed() {
        if (!chineseT9Composition.shouldClearHiddenComposition(
                isActive = isChineseT9InputModeActive(),
                hasPendingPunctuation = t9PunctuationCoordinator.isPending
            )
        ) return
        clearT9CompositionState()
        clearTransientInputUiState()
        currentInputConnection?.finishComposingText()
        postFcitxJob {
            if (getRimeInput().isNotEmpty()) {
                focusOutIn()
            }
        }
    }

    private fun discardChineseCompositionForModeSwitch() {
        // Long-press # in Chinese composition means "I forgot to switch modes"; discard the
        // unfinished Chinese input instead of committing or opening Rime's expansion list.
        resetComposingState()
        clearTransientInputUiState()
        currentInputConnection?.finishComposingText()
        candidatesView?.refreshT9Ui()
        postFcitxJob {
            focusOutIn()
        }
    }

    private fun resetComposingState() {
        composing.clear()
        composingText = FormattedText.Empty
        clearT9CompositionState()
        t9CandidateFocusController.reset()
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
            smartEnglishModeController.onPreferenceChanged(value)
        }
    private val physicalLongPressDelayChangeListener =
        ManagedPreference.OnChangeListener<Int> { _, value ->
            physicalLongPressDelay = value
        }
    private val ignoreSystemCursorChangeListener =
        ManagedPreference.OnChangeListener<Boolean> { _, value ->
            ignoreSystemCursor = value
        }
    private val t9ResponsivenessTraceChangeListener =
        ManagedPreference.OnChangeListener<Boolean> { _, value ->
            T9ResponsivenessTrace.configure(enabled = value)
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
        T9ResponsivenessTrace.configure(enabled = prefs.internal.t9ResponsivenessTrace.getValue())
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
        prefs.internal.t9ResponsivenessTrace.registerOnChangeListener(t9ResponsivenessTraceChangeListener)
        prefs.keyboard.inputUiFont.registerOnChangeListener(recreateInputViewsListener)
        keyboardPrefs.passwordInputPreview.registerOnChangeListener(passwordInputPreviewChangeListener)
        prefs.candidates.registerOnChangeListener(recreateCandidatesViewListener)
        ThemeManager.addOnChangedListener(onThemeChangeListener)
        smartEnglishCoordinator.warmupIfEnabled()
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
            chineseT9Composition.backspaceFromVirtualKey()
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
        if (t9MultiTapCoordinator.hasPendingChar) return false
        if (t9PunctuationCoordinator.isPending) return false

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
            !t9MultiTapCoordinator.hasPendingChar &&
            !t9PunctuationCoordinator.isPending

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
            !t9PunctuationCoordinator.isPending &&
            !t9MultiTapCoordinator.hasPendingChar

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

    private val t9ModeCoordinator: T9ModeCoordinator by lazy {
        T9ModeCoordinator(
            beforeModeChange = {
                resetMultiTapState()
                smartEnglishCoordinator.reset()
                smartEnglishCoordinator.flushLearningWord()
            },
            onEnglishModeEntered = {
                smartEnglishCoordinator.onModeEntered()
            },
            onModeLabelChanged = { label ->
                onT9ModeChanged?.invoke(label)
            },
            showModeIndicator = { label ->
                inputView?.showModeIndicatorBadge(label)
            }
        )
    }

    private val currentT9Mode: T9InputMode
        get() = t9ModeCoordinator.current

    private val t9CandidateFocusController = T9CandidateFocusController(
        onFocusChanged = { candidatesView?.syncT9CandidateFocus() }
    )
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

    private val t9MultiTapCoordinator = T9MultiTapCoordinator(
        commitText = { text -> commitText(text) },
        setComposingText = { text -> currentInputConnection?.setComposingText(text, 1) },
        finishComposingText = { currentInputConnection?.finishComposingText() },
        applyCase = { char -> smartEnglishCoordinator.applyCase(char) },
        consumeShiftOnce = { smartEnglishCoordinator.consumeShiftOnce() },
        recordLearningChar = { char -> smartEnglishCoordinator.recordLearningChar(char) }
    )
    private val smartEnglishCaseCoordinator = SmartEnglishCaseCoordinator(
        toggleShiftOnce = { smartEnglishCoordinator.toggleShiftOnce() },
        toggleCaps = { smartEnglishCoordinator.toggleCaps() },
        pendingMultiTapDisplay = { t9MultiTapCoordinator.pendingDisplayText() },
        setComposingText = { text -> currentInputConnection?.setComposingText(text, 1) },
        caseLabel = { smartEnglishCoordinator.caseLabel },
        showModeIndicator = { label -> inputView?.showModeIndicatorBadge(label) }
    )
    private val smartEnglishModeController = SmartEnglishT9ModeController(
        initialEnabled = keyboardPrefs.smartEnglishT9.getValue(),
        setPreference = { enabled -> keyboardPrefs.smartEnglishT9.setValue(enabled) },
        onEnabledChanged = { enabled -> smartEnglishCoordinator.onEnabledChanged(enabled) },
        showModeIndicator = { label -> inputView?.showModeIndicatorBadge(label) }
    )
    private val smartEnglishCoordinator: SmartEnglishT9Coordinator by lazy {
        SmartEnglishT9Coordinator(
            candidateLimit = SmartEnglishCandidateLimit,
            noMatchText = SmartEnglishNoMatchText,
            scope = lifecycleScope,
            isEnabled = { smartEnglishModeController.enabled },
            isActive = ::isSmartEnglishT9Active,
            shouldLearnWords = ::shouldLearnEnglishWords,
            commitText = ::commitText,
            refreshUi = { candidatesView?.refreshT9Ui() },
            resetPendingDigit = ::resetSmartEnglishPendingDigit
        )
    }
    private val t9PunctuationCoordinator: T9PunctuationCoordinator = T9PunctuationCoordinator(
        clearTransientInputUiState = ::clearTransientInputUiState,
        refreshUi = { candidatesView?.refreshT9Ui() },
        cancelTimeout = {},
        commitText = ::commitText
    )

    private val chineseT9Composition = ChineseT9CompositionCoordinator(
        formatText = ::formattedT9Text,
        buildRawPreeditDisplay = ::buildT9PreeditDisplay
    )

    private fun resetSmartEnglishPendingDigit() {
        physicalT9KeyHandler.resetSmartEnglishPendingDigit()
    }


    private fun getT9InputState(): ChineseT9CompositionLifecycle.InputState =
        chineseT9Composition.inputState(composing.isNotEmpty())

    private fun clearChineseT9CompositionFromEditorTap() {
        if (!chineseT9Composition.shouldClearFromEditorTap(
                isActive = isChineseT9InputModeActive(),
                state = getT9InputState()
            )
        ) return
        resetComposingState()
        clearTransientInputUiState()
        currentInputConnection?.finishComposingText()
        postFcitxJob {
            focusOutIn()
        }
    }

    fun getT9CandidateFocus(): T9CandidateFocus = t9CandidateFocusController.current

    fun moveT9CandidateFocus(newFocus: T9CandidateFocus) {
        t9CandidateFocusController.moveTo(newFocus)
    }

    fun getT9CandidateFocusIndicatorColor(): Int = highlightColor

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
        chineseT9Composition.appendSeparator()
        candidatesView?.refreshT9Ui()
        postFcitxJob {
            val input = getRimeInput()
            if (input.isNotEmpty()) {
                replaceRimeInput(input.length, 0, "'", input.length + 1)
            }
        }
        return true
    }

    fun getPendingT9PunctuationPaged(): FcitxEvent.PagedCandidateEvent.Data? =
        t9PunctuationCoordinator.paged()

    fun commitPendingT9PunctuationCandidate(index: Int): Boolean =
        t9PunctuationCoordinator.selectAndCommitCandidate(index)

    fun previewPendingT9PunctuationCandidate(index: Int): Boolean =
        t9PunctuationCoordinator.previewCandidate(index)

    private fun isSmartEnglishT9Active(): Boolean =
        t9InputModeEnabled && currentT9Mode == T9InputMode.ENGLISH && smartEnglishModeController.enabled

    fun isSmartEnglishT9InputModeActive(): Boolean = isSmartEnglishT9Active()

    fun isSmartEnglishT9Enabled(): Boolean = smartEnglishModeController.enabled

    fun toggleSmartEnglishT9() {
        smartEnglishModeController.toggle()
    }

    fun getSmartEnglishT9Paged(): FcitxEvent.PagedCandidateEvent.Data? =
        smartEnglishCoordinator.paged()

    fun getSmartEnglishT9Presentation(): T9PresentationState? =
        smartEnglishCoordinator.presentation(::formattedT9Text)

    fun setSmartEnglishCandidateIndex(index: Int): Boolean =
        smartEnglishCoordinator.setCandidateIndex(index)

    fun commitSmartEnglishCandidate(
        index: Int? = null,
        appendSpace: Boolean = true,
        continuePrediction: Boolean = appendSpace
    ): Boolean {
        return smartEnglishCoordinator.commitCandidate(index, appendSpace, continuePrediction)
    }

    private fun shouldLearnEnglishWords(): Boolean {
        return SmartEnglishLearningPolicy.shouldLearnWords(currentInputEditorInfo)
    }

    private fun resetMultiTapState() {
        t9PunctuationCoordinator.commit()
        t9MultiTapCoordinator.reset()
    }

    /**
     * Callback invoked when T9 mode changes (for syncing space bar display).
     * Set by InputView when the input scope is created.
     */
    var onT9ModeChanged: ((String) -> Unit)? = null

    fun getCurrentT9ModeLabel(): String = t9ModeCoordinator.currentLabel

    fun switchToNextT9Mode() {
        t9ModeCoordinator.switchToNextMode()
    }

    private fun commitLiteralT9Star(chineseState: ChineseT9CompositionLifecycle.InputState) {
        resetMultiTapState()
        val resetChineseEngine = chineseT9Composition.shouldResetEngineForLiteralStar(
            isChineseMode = currentT9Mode == T9InputMode.CHINESE,
            state = chineseState
        )
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
                when (chineseT9Composition.handleForwardedKeyDown(event.keyCode)) {
                    ChineseT9CompositionLifecycle.ForwardedKeyAction.NONE -> Unit
                    ChineseT9CompositionLifecycle.ForwardedKeyAction.REFRESH_AFTER_ENGINE_CANDIDATES ->
                        candidatesView?.waitForT9EngineCandidatesThenRefresh()
                    ChineseT9CompositionLifecycle.ForwardedKeyAction.HIDE_CANDIDATE_UI_IMMEDIATELY ->
                        candidatesView?.hideT9CandidateUiImmediately()
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
    fun getCurrentT9Segment(): String =
        chineseT9Composition.currentSegment()

    fun getChineseT9InputSnapshot(data: FcitxEvent.InputPanelEvent.Data): ChineseT9InputSnapshot {
        return chineseT9Composition.snapshot(data.preedit.toString())
    }

    /** Total number of digit keys (2-9) in current composition, for truncating first-row pinyin. */
    fun getT9CompositionKeyCount(): Int =
        chineseT9Composition.keyCount()

    fun getT9CompositionDigitSequence(): String =
        chineseT9Composition.digitSequence()

    /** Candidate pinyin list for current T9 segment; empty if segment empty or not T9. */
    fun getT9PinyinCandidates(): List<String> =
        if (t9InputModeEnabled && currentT9Mode == T9InputMode.CHINESE)
            chineseT9Composition.pinyinCandidates()
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
                    append(token.split('\'').joinToString("'") {
                        ChineseT9PresentationSource.buildDigitSegmentDisplay(it)
                    })
                } else {
                    append(token)
                }
            }
        }
        return formattedT9Text(display)
    }

    /** Preedit for the shared T9 display row; null if there is nothing to show. */
    fun getT9PreeditDisplay(rawComposition: String? = null): FormattedText? {
        if (!t9InputModeEnabled) return null
        return when (currentT9Mode) {
            T9InputMode.CHINESE -> chineseT9Composition.preeditDisplay(rawComposition)
            T9InputMode.ENGLISH -> {
                t9MultiTapCoordinator.pendingDisplayText()?.let(::formattedT9Text)
            }
            T9InputMode.NUMBER -> null
        }
    }

    fun getT9PresentationState(key: ChineseT9PresentationSnapshotKey): T9PresentationState {
        if (!t9InputModeEnabled || currentT9Mode != T9InputMode.CHINESE) {
            return T9PresentationState(null, emptyList())
        }
        return chineseT9Composition.presentation(key)
    }

    fun shouldConsumeT9ResolvedPinyinPrefixAfterHanziSelection(
        prefix: String,
        candidate: FcitxEvent.Candidate
    ): Boolean = isChineseT9InputModeActive() &&
        chineseT9Composition.shouldConsumeResolvedPrefixAfterCandidate(prefix, candidate)

    fun consumeT9ResolvedPinyinPrefix(prefix: String): Boolean {
        if (!t9InputModeEnabled || currentT9Mode != T9InputMode.CHINESE) return false
        val rawPreedit = chineseT9Composition.consumeResolvedPrefix(prefix) ?: return false
        candidatesView?.prepareForT9CompositionReplay()
        inputView?.clearTransientState()
        replayT9RawComposition(rawPreedit)
        return true
    }

    private fun replayT9RawComposition(rawPreedit: String) {
        chineseT9Composition.prepareReplay(rawPreedit)
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
    ): Boolean = chineseT9Composition.candidateMatchesResolvedPrefix(candidate, expected)

    /**
     * Record the user's pinyin choice and try to narrow Rime itself by replacing the matching
     * T9 digit span with "pinyin'". The composition session owns the Kotlin-side state; this
     * adapter only mirrors that selection into Rime.
     */
    fun selectT9Pinyin(pinyin: String) {
        val request = chineseT9Composition.selectPinyin(pinyin) ?: return
        postFcitxJob {
            chineseT9Composition.mirrorPinyinSelection(this, request)
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
        if (!chineseT9Composition.consumeSelectedCandidateReading(candidate)) return false
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
        smartEnglishCoordinator.warmupIfEnabled()
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
        smartEnglishCoordinator.reset()
        smartEnglishCoordinator.flushLearningWord()
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
        smartEnglishCoordinator.reset()
        smartEnglishCoordinator.flushLearningWord()
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
        prefs.internal.t9ResponsivenessTrace.unregisterOnChangeListener(t9ResponsivenessTraceChangeListener)
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
