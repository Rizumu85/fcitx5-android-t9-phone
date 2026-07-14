/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.view.updateLayoutParams
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.performance.StartupPerformanceTrace
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcaster
import org.fcitx.fcitx5.android.input.broadcast.PreeditEmptyStateComponent
import org.fcitx.fcitx5.android.input.broadcast.PunctuationComponent
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateComponent
import org.fcitx.fcitx5.android.input.keyboard.CommonKeyActionListener
import org.fcitx.fcitx5.android.input.handwriting.HandwritingWindow
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.keyboard.T9Keyboard
import org.fcitx.fcitx5.android.input.keyboard.TemporaryFullKeyboard
import org.fcitx.fcitx5.android.input.picker.emojiPicker
import org.fcitx.fcitx5.android.input.picker.emoticonPicker
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.picker.symbolPicker
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import org.fcitx.fcitx5.android.input.preedit.PreeditComponent
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.fcitx.fcitx5.android.utils.unset
import org.mechdancer.dependency.DynamicScope
import org.mechdancer.dependency.manager.wrapToUniqueComponent
import org.mechdancer.dependency.plusAssign
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.withTheme
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable

@SuppressLint("ViewConstructor")
class InputView(
    service: FcitxInputMethodService,
    fcitx: FcitxConnection,
    theme: Theme
) : BaseInputView(service, fcitx, theme) {

    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        if (service.handlePreImeKeyEvent(event)) return true
        return super.dispatchKeyEventPreIme(event)
    }

    private val auxiliarySurfacesCreateStage = StartupPerformanceTrace.beginStage(
        StartupPerformanceTrace.Stage.INPUT_AUXILIARY_SURFACES_CREATE
    )
    private val keyBorder by ThemeManager.prefs.keyBorder

    private val chromePrimitivesCreateStage = StartupPerformanceTrace.beginStage(
        StartupPerformanceTrace.Stage.INPUT_CHROME_PRIMITIVES_CREATE
    )
    private val customBackground = imageView {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private val placeholderOnClickListener = OnClickListener { }

    // use clickable view as padding, so MotionEvent can be split to padding view and keyboard view
    private val leftPaddingSpace = view(::View) {
        setOnClickListener(placeholderOnClickListener)
    }
    private val rightPaddingSpace = view(::View) {
        setOnClickListener(placeholderOnClickListener)
    }
    private val bottomPaddingSpace = view(::View) {
        // height as keyboardBottomPadding
        // bottomMargin as WindowInsets (Navigation Bar) offset
        setOnClickListener(placeholderOnClickListener)
    }
    private val chromePrimitivesCreated =
        StartupPerformanceTrace.endStage(chromePrimitivesCreateStage)
    private val selectionActionPanel = lazy(LazyThreadSafetyMode.NONE) {
        StartupPerformanceTrace.measure(
            StartupPerformanceTrace.Stage.INPUT_SELECTION_PANEL_CREATE
        ) {
            // Selection hints are absent during ordinary typing. Attach them on first use so
            // every IME cold start does not pay for six hidden views and their font setup.
            SelectionActionPanel(context, theme).also { it.addTo(this) }
        }
    }
    private val numberOperatorPanel = lazy(LazyThreadSafetyMode.NONE) {
        StartupPerformanceTrace.measure(
            StartupPerformanceTrace.Stage.INPUT_NUMBER_PANEL_CREATE
        ) {
            // Number operators are an explicit secondary surface. Defer their 26-cell tree
            // until the user requests it instead of constructing it for Chinese/English input.
            NumberOperatorPanel(context, theme).also { it.addTo(this) }
        }
    }
    private val passwordPreviewCreateStage = StartupPerformanceTrace.beginStage(
        StartupPerformanceTrace.Stage.INPUT_PASSWORD_PREVIEW_CREATE
    )
    private var passwordInputPreviewValue = ""
    private var passwordInputPreviewCursor = 0
    private var passwordInputPreviewCursorVisible = true
    private val passwordInputPreviewBlinkHandler = Handler(Looper.getMainLooper())
    private val passwordInputPreviewBlinkRunnable = object : Runnable {
        override fun run() {
            if (passwordInputPreview.visibility != VISIBLE || passwordInputPreviewValue.isEmpty()) return
            passwordInputPreviewCursorVisible = !passwordInputPreviewCursorVisible
            renderPasswordInputPreview()
            passwordInputPreviewBlinkHandler.postDelayed(this, PasswordInputPreviewBlinkInterval)
        }
    }
    private val passwordInputPreviewBackground = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(theme.keyboardColor)
    }
    private val passwordInputPreviewText = view(::TextView) {
        gravity = Gravity.CENTER_VERTICAL
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.START
        setPadding(dp(10), 0, dp(10), 0)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        InputUiFont.applyTo(this, Typeface.NORMAL)
        setTextColor(theme.keyTextColor)
        background = passwordInputPreviewBackground
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        elevation = dp(7).toFloat()
        translationZ = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            outlineAmbientShadowColor = Color.argb(54, 0, 0, 0)
            outlineSpotShadowColor = Color.argb(78, 0, 0, 0)
        }
    }
    private val passwordInputPreview = view(::FrameLayout) {
        visibility = GONE
        isClickable = false
        isFocusable = false
        clipChildren = false
        clipToPadding = false
        addView(
            passwordInputPreviewText,
            FrameLayout.LayoutParams(matchParent, dp(30), Gravity.CENTER)
        )
    }
    private val passwordPreviewCreated =
        StartupPerformanceTrace.endStage(passwordPreviewCreateStage)
    private val auxiliarySurfacesCreated =
        StartupPerformanceTrace.endStage(auxiliarySurfacesCreateStage)

    private val componentsCreateStage = StartupPerformanceTrace.beginStage(
        StartupPerformanceTrace.Stage.INPUT_COMPONENTS_CREATE
    )
    private val scope = DynamicScope()
    private val themedContext = context.withTheme(R.style.Theme_InputViewTheme)
    private val broadcaster = InputBroadcaster()
    private val popup = PopupComponent()
    private val punctuation = PunctuationComponent()
    private val returnKeyDrawable = ReturnKeyDrawableComponent()
    private val preeditEmptyState = PreeditEmptyStateComponent()
    private val preedit = PreeditComponent()
    private val commonKeyActionListener = CommonKeyActionListener()
    private val windowManager = InputWindowManager()
    private val kawaiiBar = KawaiiBarComponent()
    private val horizontalCandidate = HorizontalCandidateComponent()
    private val keyboardWindow = KeyboardWindow()
    private val componentsCreated = StartupPerformanceTrace.endStage(componentsCreateStage)

    private fun setupScope() {
        StartupPerformanceTrace.measure(StartupPerformanceTrace.Stage.INPUT_SCOPE_REGISTRATION) {
            scope += this@InputView.wrapToUniqueComponent()
            scope += service.wrapToUniqueComponent()
            scope += fcitx.wrapToUniqueComponent()
            scope += theme.wrapToUniqueComponent()
            scope += themedContext.wrapToUniqueComponent()
            scope += broadcaster
            scope += popup
            scope += punctuation
            scope += returnKeyDrawable
            scope += preeditEmptyState
            scope += preedit
            scope += commonKeyActionListener
            scope += windowManager
            scope += kawaiiBar
            scope += horizontalCandidate
        }
        StartupPerformanceTrace.measure(StartupPerformanceTrace.Stage.INPUT_SCOPE_READY) {
            broadcaster.onScopeSetupFinished(scope)
        }
    }

    private val preferenceBindingsStage = StartupPerformanceTrace.beginStage(
        StartupPerformanceTrace.Stage.INPUT_PREFERENCE_BINDINGS
    )
    private val keyboardPrefs = AppPrefs.getInstance().keyboard

    private val t9KeyboardHeightPercent = keyboardPrefs.t9KeyboardHeightPercent
    private val t9KeyboardHeightPercentLandscape = keyboardPrefs.t9KeyboardHeightPercentLandscape
    private var isT9KeyboardActive = false

    private val keyboardSizePrefs = listOf(
        t9KeyboardHeightPercent,
        t9KeyboardHeightPercentLandscape,
    )

    private val keyboardHeightPx: Int
        get() {
            val height = fullKeyboardHeightPx
            if (keyboardWindow.isPasswordPeekMode()) {
                return height / TemporaryFullKeyboard.RowCount
            }
            return height
        }

    private val fullKeyboardHeightPx: Int
        get() {
            val percent = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE ->
                    if (isT9KeyboardActive) t9KeyboardHeightPercentLandscape.getValue() else 45
                else ->
                    if (isT9KeyboardActive) t9KeyboardHeightPercent.getValue() else 40
            }
            return resources.displayMetrics.heightPixels * percent / 100
        }

    private val keyboardSidePaddingPx: Int
        get() {
            return 0
        }

    private val keyboardBottomPaddingPx: Int
        get() {
            return 0
        }

    @Keep
    private val onKeyboardSizeChangeListener = ManagedPreferenceProvider.OnChangeListener { key ->
        if (keyboardSizePrefs.any { it.key == key }) {
            updateKeyboardSize()
        }
    }

    val keyboardView: TopRoundedClipLayout

    @Keep
    private val inputPanelTopRadiusChangeListener = ManagedPreference.OnChangeListener<Int> { _, _ ->
        updateInputPanelTopCornerRadius()
    }

    private fun updateInputPanelTopCornerRadius() {
        val radius = keyboardView.inputPanelTopCornerRadiusPx()
        keyboardView.topCornerRadiusPx = radius
        passwordInputPreviewBackground.cornerRadius = radius.toFloat()
        passwordInputPreviewText.invalidateOutline()
        kawaiiBar.updateTopCornerRadius()
    }

    private val preferenceBindingsCreated =
        StartupPerformanceTrace.endStage(preferenceBindingsStage)

    init {
        // MUST call before any operation
        StartupPerformanceTrace.measure(StartupPerformanceTrace.Stage.INPUT_SCOPE_SETUP) {
            setupScope()
        }

        // Sync T9 mode to space bar when user switches via # long press or language key
        service.onT9ModeChanged = { broadcaster.onT9ModeUpdate(it) }

        // restore punctuation mapping in case of InputView recreation
        fcitx.launchOnReady {
            punctuation.updatePunctuationMapping(it.statusAreaActionsCached)
        }

        // make sure KeyboardWindow's view has been created before it receives any broadcast
        StartupPerformanceTrace.measure(StartupPerformanceTrace.Stage.ACTIVE_KEYBOARD_CREATE) {
            windowManager.addEssentialWindow(keyboardWindow, createView = true)
        }
        windowManager.addLazyEssentialWindow(PickerWindow.Key.Symbol, ::symbolPicker)
        windowManager.addLazyEssentialWindow(PickerWindow.Key.Emoji, ::emojiPicker)
        windowManager.addLazyEssentialWindow(PickerWindow.Key.Emoticon, ::emoticonPicker)
        windowManager.addLazyEssentialWindow(HandwritingWindow, ::HandwritingWindow)

        // 1. Initialize T9 state and set callbacks before attachWindow so onAttached → onLayoutChanged is ready
        isT9KeyboardActive = true
        keyboardWindow.onLayoutChanged = { layoutName ->
            val shouldBeT9 = layoutName == T9Keyboard.Name
            if (isT9KeyboardActive != shouldBeT9) {
                isT9KeyboardActive = shouldBeT9
                updateKeyboardSize()
            }
            updatePasswordInputPreviewChrome()
        }
        keyboardWindow.onHeightChanged = {
            updatePasswordPeekChrome()
            updateKeyboardSize()
            updatePasswordInputPreviewChrome()
        }
        windowManager.onActiveWindowChanged = { oldWindow, newWindow ->
            if (oldWindow === keyboardWindow && isT9KeyboardActive) {
                // 离开键盘窗口 → 用默认高度 40/45
                isT9KeyboardActive = false
                updateKeyboardSize()
            } else if (newWindow === keyboardWindow && keyboardWindow.isCurrentLayoutT9()) {
                // 回到键盘窗口 → 恢复 T9 高度 10/15
                if (!isT9KeyboardActive) {
                    isT9KeyboardActive = true
                    updateKeyboardSize()
                }
            }
        }
        // 2. attach window (triggers onAttached → onLayoutChanged)
        StartupPerformanceTrace.measure(StartupPerformanceTrace.Stage.ACTIVE_KEYBOARD_ATTACH) {
            windowManager.attachWindow(KeyboardWindow)
        }

        broadcaster.onImeUpdate(fcitx.cachedState.inputMethodEntry)

        customBackground.imageDrawable = theme.backgroundDrawable(keyBorder)

        keyboardView = StartupPerformanceTrace.measure(
            StartupPerformanceTrace.Stage.INPUT_CHROME_CREATE
        ) {
            TopRoundedClipLayout(themedContext).apply {
                topCornerRadiusPx = inputPanelTopCornerRadiusPx()
                // allow MotionEvent to be delivered to keyboard while pressing on padding views.
                // although it should be default for apps targeting Honeycomb (3.0, API 11) and higher,
                // but it's not the case on some devices ... just set it here
                isMotionEventSplittingEnabled = true
                add(customBackground, lParams(matchParent, matchParent) {
                    centerVertically()
                    centerHorizontally()
                })
                add(kawaiiBar.view, lParams(matchParent, dp(KawaiiBarComponent.HEIGHT)) {
                    topOfParent()
                    centerHorizontally()
                })
                add(leftPaddingSpace, lParams {
                    below(kawaiiBar.view)
                    startOfParent()
                    bottomOfParent()
                })
                add(rightPaddingSpace, lParams {
                    below(kawaiiBar.view)
                    endOfParent()
                    bottomOfParent()
                })
                add(windowManager.view, lParams {
                    below(kawaiiBar.view)
                    above(bottomPaddingSpace)
                    /**
                     * set start and end constrain in [updateKeyboardSize]
                     */
                })
                add(bottomPaddingSpace, lParams {
                    startToEndOf(leftPaddingSpace)
                    endToStartOf(rightPaddingSpace)
                    bottomOfParent()
                })
            }
        }

        // 3. Add views to layout
        StartupPerformanceTrace.measure(StartupPerformanceTrace.Stage.INPUT_TREE_ASSEMBLY) {
            add(preedit.ui.root, lParams(matchParent, wrapContent) {
                above(passwordInputPreview)
                centerHorizontally()
            })
            add(passwordInputPreview, lParams(matchParent, dp(36)) {
                above(keyboardView)
                centerHorizontally()
            })
            add(keyboardView, lParams(matchParent, wrapContent) {
                centerHorizontally()
                bottomOfParent()
            })
            add(popup.root, lParams(matchParent, matchParent) {
                centerVertically()
                centerHorizontally()
            })
            // 4. updateKeyboardSize() after all add() so layoutParams exist (avoids NPE / wrong height)
            updateInputPanelTopCornerRadius()
            updatePasswordPeekChrome()
            updateKeyboardSize()
        }

        keyboardPrefs.registerOnChangeListener(onKeyboardSizeChangeListener)
        ThemeManager.prefs.inputPanelTopRadius.registerOnChangeListener(inputPanelTopRadiusChangeListener)
    }

    private fun updateKeyboardSize() {
        if (windowManager.view.layoutParams == null) return
        windowManager.view.updateLayoutParams {
            height = keyboardHeightPx
        }
        bottomPaddingSpace.updateLayoutParams {
            height = keyboardBottomPaddingPx
        }
        val sidePadding = keyboardSidePaddingPx
        if (sidePadding == 0) {
            // hide side padding space views when unnecessary
            leftPaddingSpace.visibility = GONE
            rightPaddingSpace.visibility = GONE
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToEnd = unset
                endToStart = unset
                startOfParent()
                endOfParent()
            }
        } else {
            leftPaddingSpace.visibility = VISIBLE
            rightPaddingSpace.visibility = VISIBLE
            leftPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            rightPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToStart = unset
                endToEnd = unset
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
            }
        }
        preedit.ui.root.setPadding(sidePadding, 0, sidePadding, 0)
        kawaiiBar.view.setPadding(sidePadding, 0, sidePadding, 0)
        passwordInputPreview.setPadding(sidePadding + dp(8), 0, sidePadding + dp(8), dp(6))
        keyboardView.post {
            service.requestImeInsetsRefresh()
        }
    }

    private fun updatePasswordPeekChrome() {
        kawaiiBar.view.visibility = if (keyboardWindow.isPasswordPeekMode()) GONE else VISIBLE
    }

    private fun updatePasswordInputPreviewChrome() {
        val visible = passwordInputPreviewValue.isNotEmpty() &&
            keyboardWindow.isTemporaryPasswordInputSessionActive() &&
            !keyboardWindow.isPasswordPeekMode()
        passwordInputPreview.visibility = if (visible) VISIBLE else GONE
        if (visible) {
            startPasswordInputPreviewBlink()
        } else {
            stopPasswordInputPreviewBlink()
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        updateInputPanelTopCornerRadius()
        bottomPaddingSpace.updateLayoutParams<LayoutParams> {
            bottomMargin = getNavBarBottomInset(insets)
        }
        return insets
    }

    /**
     * called when [InputView] is about to show, or restart
     */
    fun startInput(info: EditorInfo, capFlags: CapabilityFlags, restarting: Boolean = false) {
        broadcaster.onStartInput(info, capFlags, restarting)
        returnKeyDrawable.updateDrawableOnEditorInfo(info)
        windowManager.attachWindow(KeyboardWindow)
        // Re-entering an editor restores T9 geometry because detach clears the active callback.
        if (keyboardWindow.isCurrentLayoutT9() && !isT9KeyboardActive) {
            isT9KeyboardActive = true
            updateKeyboardSize()
        }
    }

    fun isTemporaryTextKeyboardEnabled(): Boolean {
        return keyboardWindow.isTemporaryTextKeyboardEnabled()
    }

    fun isTemporaryPasswordKeyboardVisible(): Boolean {
        return keyboardWindow.isTemporaryPasswordKeyboardVisible()
    }

    fun isTemporaryPasswordInputSessionActive(): Boolean {
        return keyboardWindow.isTemporaryPasswordInputSessionActive()
    }

    fun shouldKeepTemporaryPasswordModeOnRestart(capFlags: CapabilityFlags): Boolean {
        return keyboardWindow.shouldKeepTemporaryPasswordModeOnRestart(capFlags)
    }

    override fun onStartHandleFcitxEvent() {
        val cached = fcitx.cachedState
        val inputPanelData = cached.inputPanel
        val inputMethodEntry = cached.inputMethodEntry
        val statusAreaActions = cached.statusAreaActions
        arrayOf(
            FcitxEvent.InputPanelEvent(inputPanelData),
            FcitxEvent.IMChangeEvent(inputMethodEntry),
            FcitxEvent.StatusAreaEvent(
                FcitxEvent.StatusAreaEvent.Data(statusAreaActions, inputMethodEntry)
            )
        ).forEach { handleFcitxEvent(it) }
    }

    override fun handleFcitxEvent(it: FcitxEvent<*>) {
        when (it) {
            is FcitxEvent.CandidateListEvent -> {
                broadcaster.onCandidateUpdate(it.data)
            }
            is FcitxEvent.ClientPreeditEvent -> {
                preeditEmptyState.updatePreeditEmptyState(clientPreedit = it.data)
                broadcaster.onClientPreeditUpdate(it.data)
            }
            is FcitxEvent.InputPanelEvent -> {
                preeditEmptyState.updatePreeditEmptyState(preedit = it.data.preedit)
                broadcaster.onInputPanelUpdate(it.data)
            }
            is FcitxEvent.IMChangeEvent -> {
                broadcaster.onImeUpdate(it.data)
            }
            is FcitxEvent.StatusAreaEvent -> {
                punctuation.updatePunctuationMapping(it.data.actions)
                broadcaster.onStatusAreaUpdate(it.data.actions)
            }
            else -> {}
        }
    }

    fun updateSelection(start: Int, end: Int) {
        broadcaster.onSelectionUpdate(start, end)
    }

    fun clearTransientState() {
        kawaiiBar.clearTransientState()
    }

    private fun renderPasswordInputPreview() {
        val text = passwordInputPreviewValue
        if (text.isEmpty()) {
            passwordInputPreviewText.text = ""
            return
        }
        val cursor = passwordInputPreviewCursor.coerceIn(0, text.length)
        val displayText = StringBuilder(text)
            .insert(cursor, PasswordInputPreviewCursor)
            .toString()
        val styled = SpannableString(displayText).apply {
            setSpan(
                ForegroundColorSpan(
                    if (passwordInputPreviewCursorVisible) {
                        theme.accentKeyBackgroundColor
                    } else {
                        Color.TRANSPARENT
                    }
                ),
                cursor,
                cursor + PasswordInputPreviewCursor.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        passwordInputPreviewText.text = styled
    }

    private fun startPasswordInputPreviewBlink() {
        passwordInputPreviewBlinkHandler.removeCallbacks(passwordInputPreviewBlinkRunnable)
        passwordInputPreviewBlinkHandler.postDelayed(
            passwordInputPreviewBlinkRunnable,
            PasswordInputPreviewBlinkInterval
        )
    }

    private fun stopPasswordInputPreviewBlink() {
        passwordInputPreviewBlinkHandler.removeCallbacks(passwordInputPreviewBlinkRunnable)
        passwordInputPreviewCursorVisible = true
        renderPasswordInputPreview()
    }

    fun setPasswordInputPreview(text: String, cursor: Int, hasContent: Boolean) {
        passwordInputPreviewValue = if (hasContent) text else ""
        passwordInputPreviewCursor = cursor.coerceIn(0, passwordInputPreviewValue.length)
        passwordInputPreviewCursorVisible = true
        renderPasswordInputPreview()
        updatePasswordInputPreviewChrome()
    }

    fun setPasswordInputPreview(text: String) {
        passwordInputPreviewValue = text
        passwordInputPreviewCursor = text.length
        passwordInputPreviewCursorVisible = true
        renderPasswordInputPreview()
        updatePasswordInputPreviewChrome()
    }

    fun showSelectionActionHints() {
        selectionActionPanel.value.show()
    }

    fun hideSelectionActionHints() {
        if (selectionActionPanel.isInitialized()) {
            selectionActionPanel.value.hide()
        }
    }

    fun showNumberOperatorHints() {
        numberOperatorPanel.value.showOperators()
    }

    fun hideNumberOperatorHints() {
        if (numberOperatorPanel.isInitialized()) {
            numberOperatorPanel.value.hideOperators()
        }
    }

    fun showNumberEqualsChoice(prefix: String, result: String) {
        numberOperatorPanel.value.showEqualsChoice(prefix, result)
    }

    fun hideNumberEqualsChoice() {
        if (numberOperatorPanel.isInitialized()) {
            numberOperatorPanel.value.hideEqualsChoice()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(response: InlineSuggestionsResponse): Boolean {
        return kawaiiBar.handleInlineSuggestions(response)
    }

    override fun onDetachedFromWindow() {
        passwordInputPreviewBlinkHandler.removeCallbacks(passwordInputPreviewBlinkRunnable)
        keyboardWindow.onLayoutChanged = null
        keyboardWindow.onHeightChanged = null
        windowManager.onActiveWindowChanged = null
        keyboardPrefs.unregisterOnChangeListener(onKeyboardSizeChangeListener)
        ThemeManager.prefs.inputPanelTopRadius.unregisterOnChangeListener(inputPanelTopRadiusChangeListener)
        scope.clear()
        super.onDetachedFromWindow()
    }

}

private const val PasswordInputPreviewCursor = "|"
private const val PasswordInputPreviewBlinkInterval = 520L
