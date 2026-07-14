/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.bar

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Size
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.FrameLayout
import android.widget.ViewAnimator
import android.widget.inline.InlineContentView
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlag
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.FcitxEvent.CandidateListEvent
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.VoiceInputEditorPolicy
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.State.ClickToAttachWindow
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.State.ClickToDetachWindow
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.State.Hidden
import org.fcitx.fcitx5.android.input.bar.KawaiiBarStateMachine.BooleanKey.CandidateEmpty
import org.fcitx.fcitx5.android.input.bar.KawaiiBarStateMachine.BooleanKey.PreeditEmpty
import org.fcitx.fcitx5.android.input.bar.KawaiiBarStateMachine.TransitionEvent.CandidatesUpdated
import org.fcitx.fcitx5.android.input.bar.KawaiiBarStateMachine.TransitionEvent.ExtendedWindowAttached
import org.fcitx.fcitx5.android.input.bar.KawaiiBarStateMachine.TransitionEvent.PreeditUpdated
import org.fcitx.fcitx5.android.input.bar.KawaiiBarStateMachine.TransitionEvent.WindowDetached
import org.fcitx.fcitx5.android.input.bar.ui.CandidateUi
import org.fcitx.fcitx5.android.input.bar.ui.IdleUi
import org.fcitx.fcitx5.android.input.bar.ui.TitleUi
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.candidates.expanded.window.GridExpandedCandidateWindow
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateComponent
import org.fcitx.fcitx5.android.input.clipboard.ClipboardWindow
import org.fcitx.fcitx5.android.input.dependency.UniqueViewComponent
import org.fcitx.fcitx5.android.input.dependency.context
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.editing.TextEditingWindow
import org.fcitx.fcitx5.android.input.handwriting.HandwritingWindow
import org.fcitx.fcitx5.android.input.keyboard.CommonKeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import org.fcitx.fcitx5.android.input.status.StatusAreaWindow
import org.fcitx.fcitx5.android.input.inputPanelTopCornerRadiusPx
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.DynamicScope
import org.mechdancer.dependency.manager.must
import splitties.bitflags.hasFlag
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.sin

class KawaiiBarComponent : UniqueViewComponent<KawaiiBarComponent, FrameLayout>(),
    InputBroadcastReceiver {

    private val context by manager.context()
    private val theme by manager.theme()
    private val service by manager.inputMethodService()
    private val windowManager: InputWindowManager by manager.must()
    private val horizontalCandidate: HorizontalCandidateComponent by manager.must()
    private val commonKeyActionListener: CommonKeyActionListener by manager.must()
    private val popup: PopupComponent by manager.must()

    private val prefs = AppPrefs.getInstance()

    private val toolbarButtonPreferences = prefs.keyboard.toolbarButtonPreferences

    private var isInlineSuggestionPresent: Boolean = false
    private var isCapabilityFlagsPassword: Boolean = false
    private var isKeyboardLayoutNumber: Boolean = false
    private var isPasswordKeyboardLayout: Boolean = false
    private var isKeyboardWindowAttached: Boolean = true
    private var isVoiceInputAllowedForEditor: Boolean = true

    private enum class NumberRowState { Auto, ForceShow, ForceHide }

    private var numberRowState = NumberRowState.Auto

    @Keep
    private val onToolbarButtonPreferenceUpdateListener =
        ManagedPreference.OnChangeListener<Boolean> { _, _ ->
            service.lifecycleScope.launch { updateToolbarButtons() }
        }
    private val onToolbarButtonOrderUpdateListener =
        ManagedPreference.OnChangeListener<String> { _, value ->
            service.lifecycleScope.launch {
                idleUi.buttonsUi.setButtonOrder(ToolbarButtonOrder.decode(value))
            }
        }

    private fun evalIdleUiState(fromUser: Boolean = false) {
        val newState = when {
            isPasswordKeyboardLayout && isKeyboardWindowAttached -> IdleUi.State.NumberRow
            numberRowState == NumberRowState.ForceShow && isKeyboardWindowAttached && !isKeyboardLayoutNumber -> IdleUi.State.NumberRow
            isInlineSuggestionPresent -> IdleUi.State.InlineSuggestion
            isCapabilityFlagsPassword && isKeyboardWindowAttached && !isKeyboardLayoutNumber && numberRowState != NumberRowState.ForceHide -> IdleUi.State.NumberRow
            else -> IdleUi.State.Toolbar
        }
        if (newState == idleUi.currentState) return
        idleUi.updateState(newState, fromUser)
    }

    private val hideKeyboardCallback = View.OnClickListener {
        service.requestHideSelf(0)
    }

    private val swipeDownExpandCallback = CustomGestureView.OnGestureListener { _, e ->
        if (e.type == CustomGestureView.GestureType.Up && e.totalY > 0) {
            service.requestHideSelf(0)
            true
        } else false
    }

    // Combined gesture: determine primary direction by comparing totalX and totalY.
    // - If horizontal is dominant and left, show number row (when allowed).
    // - If vertical is dominant and down, hide keyboard.
    private val swipeHideKeyboardCallback = CustomGestureView.OnGestureListener { v, e ->
        val numberRowAvailable = isCapabilityFlagsPassword && !isKeyboardLayoutNumber
        if (numberRowAvailable) {
            val dir = if (context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR) 1 else -1
            // We can't access the rawX and rawY of the MotionEvent, so we need to do some math.
            // `e.x` and `e.y` are relative to the view's top-left corner, we want to rotate
            // around the center of the view, so we translate them to be relative to the center
            val relX = e.x - v.width / 2f
            val relY = e.y - v.height / 2f

            // rotate the relative coordinates by current rotation to get absolute coordinates
            // the button is ↓, so apply -90 degrees offset
            val theta = Math.toRadians(v.rotation.toDouble()) - PI / 2
            val c = cos(theta)
            val s = sin(theta)
            val screenX = c * relX - s * relY
            val screenY = s * relX + c * relY
            val distance = hypot(screenX, screenY)
            var angle = Math.toDegrees(atan2(screenY, screenX)).toFloat()

            when (e.type) {
                CustomGestureView.GestureType.Move -> {
                    angle = if (angle in -45f..45f) {
                        angle.coerceIn(-10f, 10f)
                    } else abs(angle).coerceIn(90f - 10f, 90f + 10f) * dir
                    v.rotation = angle
                }
                CustomGestureView.GestureType.Up -> {
                    val thresholdX = (v as CustomGestureView).swipeThresholdX
                    val thresholdY = v.swipeThresholdY
                    val handled = when (angle) {
                        in -45f..45f if distance > thresholdY -> {
                            service.requestHideSelf(0)
                            true
                        }
                        !in -45f..45f if distance > thresholdX -> {
                            v.rotation = 90f * dir
                            numberRowState = NumberRowState.ForceShow
                            evalIdleUiState(fromUser = true)
                            true
                        }
                        else -> false
                    }
                    v.rotation = 0f
                    return@OnGestureListener handled
                }
                else -> {}
            }
        }

        if (e.type == CustomGestureView.GestureType.Up && abs(e.totalY) > abs(e.totalX) && e.totalY > 0) {
            service.requestHideSelf(0)
            true
        } else false
    }

    private val switchToVoiceInputCallback = View.OnClickListener {
        service.switchToVoiceInput()
    }

    private val showToolbarCallback = View.OnClickListener {
        when (idleUi.currentState) {
            IdleUi.State.InlineSuggestion -> {
                isInlineSuggestionPresent = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    idleUi.inlineSuggestionsBar.clear()
                }
            }
            else -> Unit
        }
        evalIdleUiState(fromUser = true)
    }

    private val idleUi: IdleUi by lazy {
        IdleUi(context, theme, popup, commonKeyActionListener).apply {
            setShowToolbarCallback(showToolbarCallback)
            hideKeyboardButton.apply {
                setOnClickListener(hideKeyboardCallback)
                swipeEnabled = true
                swipeThresholdY = dp(HEIGHT.toFloat())
                swipeThresholdX = swipeThresholdY
                onGestureListener = swipeHideKeyboardCallback
            }
            buttonsUi.apply {
                undoButton.setOnClickListener {
                    service.sendCombinationKeyEvents(KeyEvent.KEYCODE_Z, ctrl = true)
                }
                redoButton.setOnClickListener {
                    service.sendCombinationKeyEvents(KeyEvent.KEYCODE_Z, ctrl = true, shift = true)
                }
                voiceInputButton.setOnClickListener(switchToVoiceInputCallback)
                handwritingButton.setOnClickListener {
                    windowManager.attachWindow(HandwritingWindow)
                }
                cursorMoveButton.setOnClickListener {
                    windowManager.attachWindow(TextEditingWindow())
                }
                clipboardButton.setOnClickListener {
                    windowManager.attachWindow(ClipboardWindow())
                }
                moreButton.setOnClickListener {
                    windowManager.attachWindow(StatusAreaWindow())
                }
            }
            numberRow.apply {
                onCollapseListener = {
                    numberRowState = NumberRowState.ForceHide
                    evalIdleUiState(fromUser = true)
                }
            }
        }
    }

    private fun updateToolbarButtons() {
        idleUi.setHideKeyboardButtonVisible(true)
        idleUi.buttonsUi.setOptionalButtonsVisible(
            undo = prefs.keyboard.showUndoButton.getValue(),
            redo = prefs.keyboard.showRedoButton.getValue(),
            voiceInput = prefs.keyboard.showVoiceInputButton.getValue() && isVoiceInputAllowedForEditor,
            handwriting = prefs.keyboard.showHandwritingButton.getValue(),
            textEditing = prefs.keyboard.showTextEditingButton.getValue(),
            clipboard = prefs.keyboard.showClipboardButton.getValue()
        )
    }

    private val candidateUi by lazy {
        CandidateUi(context, theme, horizontalCandidate.view).apply {
            expandButton.apply {
                swipeEnabled = true
                swipeThresholdY = dp(HEIGHT.toFloat())
                onGestureListener = swipeDownExpandCallback
            }
        }
    }

    private val titleUi by lazy {
        TitleUi(context, theme)
    }

    private val barStateMachine = KawaiiBarStateMachine.new {
        switchUiByState(it)
    }

    val expandButtonStateMachine = ExpandButtonStateMachine.new {
        when (it) {
            ClickToAttachWindow -> {
                setExpandButtonToAttach()
                setExpandButtonEnabled(true)
            }
            ClickToDetachWindow -> {
                setExpandButtonToDetach()
                setExpandButtonEnabled(true)
            }
            Hidden -> {
                setExpandButtonEnabled(false)
            }
        }
    }

    // set expand candidate button to create expand candidate
    private fun setExpandButtonToAttach() {
        candidateUi.expandButton.setOnClickListener {
            windowManager.attachWindow(GridExpandedCandidateWindow())
        }
        candidateUi.expandButton.setIcon(R.drawable.ic_baseline_expand_more_24)
        candidateUi.expandButton.contentDescription = context.getString(R.string.expand_candidates_list)
    }

    // set expand candidate button to close expand candidate
    private fun setExpandButtonToDetach() {
        candidateUi.expandButton.setOnClickListener {
            windowManager.attachWindow(KeyboardWindow)
        }
        candidateUi.expandButton.setIcon(R.drawable.ic_baseline_expand_less_24)
        candidateUi.expandButton.contentDescription = context.getString(R.string.hide_candidates_list)
    }

    // should be used with setExpandButtonToAttach or setExpandButtonToDetach
    private fun setExpandButtonEnabled(enabled: Boolean) {
        candidateUi.expandButton.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
    }

    private fun switchUiByState(state: KawaiiBarStateMachine.State) {
        val index = state.ordinal
        if (view.displayedChild == index) return
        val new = view.getChildAt(index)
        if (new != titleUi.root) {
            titleUi.setReturnButtonOnClickListener { }
            titleUi.setTitle("")
            titleUi.removeExtension()
        }
        view.displayedChild = index
    }

    private fun barBackground(radius: Float) = if (ThemeManager.prefs.keyBorder.getValue()) {
        null
    } else {
        GradientDrawable().apply {
            setColor(theme.barColor)
            cornerRadii = floatArrayOf(
                radius, radius,
                radius, radius,
                0f, 0f,
                0f, 0f
            )
        }
    }

    private fun View.updateBarBackground() {
        background = barBackground(inputPanelTopCornerRadiusPx().toFloat())
    }

    fun updateTopCornerRadius() {
        view.updateBarBackground()
    }

    override val view by lazy {
        ViewAnimator(context).apply {
            updateBarBackground()
            setOnApplyWindowInsetsListener { v, insets ->
                v.updateBarBackground()
                insets
            }
            add(idleUi.root, lParams(matchParent, matchParent))
            add(candidateUi.root, lParams(matchParent, matchParent))
            add(titleUi.root, lParams(matchParent, matchParent))
            post {
                updateBarBackground()
            }
        }
    }

    override fun onScopeSetupFinished(scope: DynamicScope) {
        toolbarButtonPreferences.forEach {
            it.registerOnChangeListener(onToolbarButtonPreferenceUpdateListener)
        }
        prefs.keyboard.toolbarButtonOrder.registerOnChangeListener(onToolbarButtonOrderUpdateListener)
        idleUi.buttonsUi.setButtonOrder(
            ToolbarButtonOrder.decode(prefs.keyboard.toolbarButtonOrder.getValue())
        )
        updateToolbarButtons()
    }

    override fun onStartInput(info: EditorInfo, capFlags: CapabilityFlags, restarting: Boolean) {
        val privateMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            info.imeOptions.hasFlag(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        idleUi.privateMode(privateMode)
        isCapabilityFlagsPassword = capFlags.has(CapabilityFlag.Password)
        isVoiceInputAllowedForEditor = VoiceInputEditorPolicy.allows(info, capFlags)
        if (!restarting || !capFlags.has(CapabilityFlag.Password)) {
            isPasswordKeyboardLayout = false
        }
        isInlineSuggestionPresent = false
        numberRowState = NumberRowState.Auto
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            idleUi.inlineSuggestionsBar.clear()
        }
        updateToolbarButtons()
        evalIdleUiState()
    }

    override fun onPreeditEmptyStateUpdate(empty: Boolean) {
        barStateMachine.push(PreeditUpdated, PreeditEmpty to empty)
    }

    override fun onCandidateUpdate(data: CandidateListEvent.Data) {
        if (service.isChineseT9InputModeActive()) {
            clearTransientState()
            return
        }
        barStateMachine.push(CandidatesUpdated, CandidateEmpty to data.candidates.isEmpty())
    }

    fun clearTransientState() {
        horizontalCandidate.clearTransientState()
        isInlineSuggestionPresent = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            idleUi.inlineSuggestionsBar.clear()
        }
        barStateMachine.push(PreeditUpdated, PreeditEmpty to true)
        barStateMachine.push(CandidatesUpdated, CandidateEmpty to true)
        evalIdleUiState()
    }

    override fun onWindowAttached(window: InputWindow) {
        isKeyboardWindowAttached = window is KeyboardWindow
        evalIdleUiState()
        when (window) {
            is InputWindow.ExtendedInputWindow<*> -> {
                titleUi.setTitle(window.title)
                window.onCreateBarExtension()?.let { titleUi.addExtension(it, window.showTitle) }
                titleUi.setReturnButtonOnClickListener {
                    if (!window.onTitleBackPressed()) {
                        windowManager.attachWindow(KeyboardWindow)
                    }
                }
                barStateMachine.push(ExtendedWindowAttached)
            }
            else -> {}
        }
    }

    override fun onWindowDetached(window: InputWindow) {
        if (window is KeyboardWindow) {
            isKeyboardWindowAttached = false
            evalIdleUiState()
        }
        barStateMachine.push(WindowDetached)
    }

    private val suggestionSize by lazy {
        Size(ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(HEIGHT))
    }

    private val directExecutor by lazy {
        Executor { it.run() }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(response: InlineSuggestionsResponse): Boolean {
        val suggestions = response.inlineSuggestions
        if (suggestions.isEmpty()) {
            isInlineSuggestionPresent = false
            evalIdleUiState()
            idleUi.inlineSuggestionsBar.clear()
            return true
        }
        var pinned: InlineSuggestion? = null
        val scrollable = mutableListOf<InlineSuggestion>()
        var extraPinnedCount = 0
        suggestions.forEach {
            if (it.info.isPinned) {
                if (pinned == null) {
                    pinned = it
                } else {
                    scrollable.add(extraPinnedCount++, it)
                }
            } else {
                scrollable.add(it)
            }
        }
        service.lifecycleScope.launch {
            idleUi.inlineSuggestionsBar.setPinnedView(
                pinned?.let { inflateInlineContentView(it) }
            )
        }
        service.lifecycleScope.launch {
            val views = scrollable.map { s ->
                service.lifecycleScope.async {
                    inflateInlineContentView(s)
                }
            }.awaitAll()
            idleUi.inlineSuggestionsBar.setScrollableViews(views)
        }
        isInlineSuggestionPresent = true
        evalIdleUiState()
        return true
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun inflateInlineContentView(suggestion: InlineSuggestion): InlineContentView? {
        return suspendCoroutine { c ->
            // callback view might be null
            suggestion.inflate(context, suggestionSize, directExecutor) { v ->
                c.resume(v)
            }
        }
    }

    companion object {
        const val HEIGHT = 40
    }

    fun onKeyboardLayoutSwitched(isNumber: Boolean, isPassword: Boolean) {
        isKeyboardLayoutNumber = isNumber
        isPasswordKeyboardLayout = isPassword
        evalIdleUiState()
    }

    fun onPasswordModeManuallyDisabled() {
        isCapabilityFlagsPassword = false
        isPasswordKeyboardLayout = false
        numberRowState = NumberRowState.ForceHide
        evalIdleUiState()
    }

    fun onPasswordModeExited() {
        isCapabilityFlagsPassword = false
        isPasswordKeyboardLayout = false
        numberRowState = NumberRowState.Auto
        evalIdleUiState()
    }

}
