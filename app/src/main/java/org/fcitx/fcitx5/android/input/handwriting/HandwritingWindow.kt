/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.InputUiFont
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.keyboard.NumberKeyboard
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.wm.EssentialWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.fcitx.fcitx5.android.utils.AppUtil
import org.fcitx.fcitx5.android.utils.borderlessRippleDrawable
import org.mechdancer.dependency.manager.must
import splitties.dimensions.dp

class HandwritingWindow : InputWindow.ExtendedInputWindow<HandwritingWindow>(), EssentialWindow,
    InputBroadcastReceiver {
    private val service: FcitxInputMethodService by manager.inputMethodService()
    private val theme by manager.theme()
    private val windowManager: InputWindowManager by manager.must()
    private val returnKeyDrawable: ReturnKeyDrawableComponent by manager.must()
    private val chineseModelPromptPref =
        AppPrefs.getInstance().internal.handwritingChineseModelPromptShown
    private val englishModelPromptPref =
        AppPrefs.getInstance().internal.handwritingEnglishModelPromptShown
    private val brushStylePref = AppPrefs.getInstance().keyboard.handwritingBrushStyle

    companion object : EssentialWindow.Key {
        private const val DisabledControlAlpha = 0.4f
        private const val ActionRadiusDp = 14f
        private const val ActionRailWidthDp = 44
        private const val ActionSizeDp = 40
        private const val ActionSpacingDp = 4
        private const val MinimumActionSizeDp = 32
        private const val MinimumActionSpacingDp = 2
        private const val ActionLabelHeightDp = 18
        private const val ActionLabelWidthDp = 28
        private const val ActionLabelMaxSizeSp = 18f
        private const val EnglishLanguageLabelScale = 0.84f
        private const val ActionPunctuationHeightDp = 16
        private const val ActionPunctuationMaxSizeSp = 24f
        private const val ModelPromptDurationMillis = 8_000L
    }

    override val key: EssentialWindow.Key
        get() = HandwritingWindow

    override val title: String
        get() = context.getString(R.string.handwriting_input)

    override val showTitle: Boolean = false

    private lateinit var canvas: HandwritingCanvasView
    private lateinit var root: FrameLayout
    private lateinit var status: TextView
    private lateinit var returnButton: ToolButton
    private lateinit var languageButtonLabel: OpticallyCenteredActionTextView
    private lateinit var commaButtonLabel: OpticallyCenteredActionTextView
    private var currentLanguage = HandwritingLanguage.CHINESE
    private var modelPrompt: View? = null
    private val dismissModelPrompt = Runnable(::removeModelPrompt)

    private val undoButton by lazy {
        toolButton(R.drawable.ic_baseline_undo_24, R.string.handwriting_undo_stroke) {
            service.undoHandwritingStroke()
        }
    }
    private val backButton by lazy {
        toolButton(R.drawable.ic_baseline_arrow_back_24, R.string.back_to_keyboard) {
            windowManager.attachWindow(KeyboardWindow)
        }
    }
    private val titleLabel by lazy {
        TextView(context).apply {
            text = title
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTextColor(theme.altKeyTextColor)
            setPadding(context.dp(8), 0, context.dp(8), 0)
            InputUiFont.applyTo(this, Typeface.BOLD)
        }
    }
    private val candidateStrip by lazy {
        HandwritingCandidateStrip(
            context = context,
            theme = theme,
            // The title bar is fixed at 40dp, so extreme candidate-font settings are clamped here
            // rather than changing the bar height or clipping the physical shortcut labels.
            candidateTextSizeSp = AppPrefs.getInstance().candidates.fontSize
                .getValue()
                .toFloat()
                .coerceIn(16f, 22f),
            onCandidateClick = service::commitHandwritingCandidate
        )
    }
    private val barExtension by lazy {
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(backButton, LinearLayout.LayoutParams(context.dp(40), context.dp(40)))
            addView(
                FrameLayout(context).apply {
                    addView(
                        titleLabel,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                    addView(
                        candidateStrip,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                },
                LinearLayout.LayoutParams(0, context.dp(40), 1f)
            )
            addView(undoButton, LinearLayout.LayoutParams(context.dp(40), context.dp(40)))
        }
    }

    override fun onCreateView(): View {
        canvas = HandwritingCanvasView(context, brushStylePref::getValue).apply {
            brushColor = theme.keyTextColor
            background = roundedSurface(21f, theme.keyboardColor)
            clipToOutline = true
            onStrokeStarted = service::beginHandwritingStroke
            onStrokeFinished = service::addHandwritingStroke
            addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                service.updateHandwritingWritingArea(right - left, bottom - top)
            }
        }
        status = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(theme.candidateCommentColor)
            textSize = 14f
            includeFontPadding = false
            maxWidth = (
                resources.displayMetrics.widthPixels -
                    context.dp(ActionRailWidthDp * 2 + 40)
                ).coerceAtLeast(
                context.dp(120)
            )
            minHeight = context.dp(28)
            setPadding(context.dp(10), context.dp(5), context.dp(10), context.dp(5))
            background = roundedSurface(10f, theme.keyboardColor)
        }
        val tray = FrameLayout(context).apply {
            // A drawable offset preserves the tray shadow without putting AndroidX Ink inside an
            // elevated render layer, which would delay or hide incremental wet-ink strokes.
            addView(
                View(context).apply {
                    background = roundedSurface(
                        21f,
                        ColorUtils.blendARGB(theme.keyboardColor, android.graphics.Color.BLACK, 0.17f)
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(context.dp(6), context.dp(8), context.dp(6), context.dp(7))
                }
            )
            addView(
                canvas,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(context.dp(6), context.dp(6), context.dp(6), context.dp(10))
                }
            )
            addView(
                status,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = context.dp(16)
                }
            )
        }
        val leftRail = actionRail(
            iconActionButton(
                icon = R.drawable.ic_baseline_tag_faces_24,
                description = R.string.handwriting_open_emoji_keyboard,
                action = { performAction(HandwritingAction.OPEN_EMOJI) }
            ),
            textActionButton(
                text = context.getString(R.string.handwriting_number_shortcut),
                description = R.string.handwriting_open_number_keyboard,
                targetVisualHeightDp = ActionLabelHeightDp,
                maxTextSizeSp = ActionLabelMaxSizeSp,
                action = { performAction(HandwritingAction.OPEN_NUMBER) }
            ),
            textActionButton(
                text = context.getString(R.string.handwriting_chinese_shortcut),
                description = R.string.handwriting_switch_language,
                targetVisualHeightDp = ActionLabelHeightDp,
                maxTextSizeSp = ActionLabelMaxSizeSp,
                onLabelCreated = { languageButtonLabel = it },
                action = { performAction(HandwritingAction.SWITCH_LANGUAGE) }
            ),
            textActionButton(
                text = context.getString(R.string.handwriting_symbol_shortcut),
                description = R.string.handwriting_open_symbol_keyboard,
                targetVisualHeightDp = ActionLabelHeightDp,
                maxTextSizeSp = ActionLabelMaxSizeSp,
                action = { performAction(HandwritingAction.OPEN_SYMBOLS) }
            )
        )
        val rightRail = actionRail(
            iconActionButton(
                icon = R.drawable.ic_baseline_backspace_24,
                description = R.string.handwriting_delete_committed_text,
                soundEffect = InputFeedbacks.SoundEffect.Delete,
                action = { performAction(HandwritingAction.DELETE_TEXT) }
            ),
            iconActionButton(
                icon = R.drawable.ic_handwriting_space_bar_24,
                description = R.string.handwriting_insert_space,
                soundEffect = InputFeedbacks.SoundEffect.SpaceBar,
                action = { performAction(HandwritingAction.INSERT_SPACE) }
            ),
            textActionButton(
                text = context.getString(R.string.handwriting_comma_shortcut),
                description = R.string.handwriting_insert_comma,
                targetVisualHeightDp = ActionPunctuationHeightDp,
                maxTextSizeSp = ActionPunctuationMaxSizeSp,
                onLabelCreated = { commaButtonLabel = it },
                action = { performAction(HandwritingAction.INSERT_COMMA) }
            ),
            iconActionButton(
                icon = returnKeyDrawable.resourceId,
                description = R.string.handwriting_return,
                accent = true,
                soundEffect = InputFeedbacks.SoundEffect.Return,
                action = { performAction(HandwritingAction.RETURN) }
            ).also { returnButton = it }
        )
        root = FrameLayout(context).apply {
            setBackgroundColor(theme.barColor)
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    // The rails are direct-touch controls, so they need the same breathing room
                    // from the display edge that the tray already receives from its inner margin.
                    setPadding(context.dp(4), 0, context.dp(4), 0)
                    addView(
                        leftRail,
                        LinearLayout.LayoutParams(
                            context.dp(ActionRailWidthDp),
                            LinearLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                    addView(
                        tray,
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1f
                        )
                    )
                    addView(
                        rightRail,
                        LinearLayout.LayoutParams(
                            context.dp(ActionRailWidthDp),
                            LinearLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        return root
    }

    override fun beforeAttached() {
        service.beginHandwritingInput()
    }

    override fun onAttached() {
        service.setHandwritingStateListener(::renderState)
    }

    override fun onCreateBarExtension(): View = barExtension

    override fun onDetached() {
        service.setHandwritingStateListener(null)
        root.removeCallbacks(dismissModelPrompt)
        removeModelPrompt()
        // Leaving handwriting is an explicit cancellation boundary. Uncommitted strokes are
        // discarded immediately so returning to T9 never revives an accidental character.
        service.endHandwritingInput()
    }

    override fun onReturnKeyDrawableUpdate(resourceId: Int) {
        if (::returnButton.isInitialized) returnButton.setIcon(resourceId)
    }

    private fun renderState(state: HandwritingViewState) {
        currentLanguage = state.language
        canvas.setCompletedStrokes(state.strokes)
        candidateStrip.render(state.candidatePage, state.language)
        languageButtonLabel.setOpticalText(
            value = context.getString(
                when (state.language) {
                    HandwritingLanguage.CHINESE -> R.string.handwriting_chinese_shortcut
                    HandwritingLanguage.ENGLISH -> R.string.handwriting_english_shortcut
                }
            ),
            // Two Latin glyphs occupy more of the square rail button than one Han glyph at the
            // same nominal size. Balance their visible mass rather than their font metrics.
            sizeScale = when (state.language) {
                HandwritingLanguage.CHINESE -> 1f
                HandwritingLanguage.ENGLISH -> EnglishLanguageLabelScale
            }
        )
        commaButtonLabel.text = context.getString(
            when (state.language) {
                HandwritingLanguage.CHINESE -> R.string.handwriting_comma_shortcut
                HandwritingLanguage.ENGLISH -> R.string.handwriting_english_comma_shortcut
            }
        )
        // Once strokes exist, an empty center stays quiet until recognition publishes candidates;
        // flashing the title between strokes would make the dedicated result strip feel unstable.
        titleLabel.isVisible = state.strokes.isEmpty() && state.candidatePage.items.isEmpty()
        undoButton.isEnabled = state.strokes.isNotEmpty()
        undoButton.alpha = if (undoButton.isEnabled) 1f else DisabledControlAlpha
        when {
            state.noMatch -> {
                status.setText(R.string.handwriting_no_match)
                status.visibility = View.VISIBLE
            }
            state.pronunciation != null -> {
                status.text = context.getString(
                    R.string.handwriting_pronunciation_format,
                    state.pronunciation.character,
                    state.pronunciation.readings.joinToString(" · ")
                )
                status.visibility = View.VISIBLE
            }
            else -> status.visibility = View.GONE
        }
        val promptPref = when (state.language) {
            HandwritingLanguage.CHINESE -> chineseModelPromptPref
            HandwritingLanguage.ENGLISH -> englishModelPromptPref
        }
        if (state.modelState == HandwritingModelState.ENHANCED_MODEL_MISSING && !promptPref.getValue()) {
            promptPref.setValue(true)
            showModelPrompt(state.language)
        }
    }

    private fun showModelPrompt(language: HandwritingLanguage) {
        removeModelPrompt()
        val prompt = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(12), context.dp(8), context.dp(6), context.dp(8))
            background = roundedSurface(10f, theme.popupBackgroundColor)
            addView(
                TextView(context).apply {
                    setText(
                        when (language) {
                            HandwritingLanguage.CHINESE ->
                                R.string.handwriting_chinese_model_onboarding
                            HandwritingLanguage.ENGLISH ->
                                R.string.handwriting_english_model_onboarding
                        }
                    )
                    setTextColor(theme.popupTextColor)
                    textSize = 13f
                    maxLines = 2
                    InputUiFont.applyTo(this)
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(
                TextView(context).apply {
                    setText(R.string.handwriting_go_to_download)
                    setTextColor(theme.genericActiveBackgroundColor)
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(context.dp(10), context.dp(6), context.dp(10), context.dp(6))
                    background = borderlessRippleDrawable(
                        theme.keyPressHighlightColor,
                        context.dp(8)
                    )
                    InputUiFont.applyTo(this, Typeface.BOLD)
                    setOnClickListener {
                        removeModelPrompt()
                        AppUtil.launchMainToHandwritingModel(context, language)
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        modelPrompt = prompt
        root.addView(
            prompt,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                setMargins(context.dp(12), 0, context.dp(12), context.dp(12))
            }
        )
        root.removeCallbacks(dismissModelPrompt)
        root.postDelayed(dismissModelPrompt, ModelPromptDurationMillis)
    }

    private fun removeModelPrompt() {
        modelPrompt?.let { prompt ->
            (prompt.parent as? ViewGroup)?.removeView(prompt)
        }
        modelPrompt = null
    }

    private fun toolButton(icon: Int, description: Int, action: () -> Unit) =
        ToolButton(context, icon, theme).apply {
            contentDescription = context.getString(description)
            setOnClickListener { action() }
        }

    private fun actionRail(vararg buttons: View) = HandwritingActionRail(
        context = context,
        preferredButtonSizePx = context.dp(ActionSizeDp),
        preferredMarginPx = context.dp(ActionSpacingDp),
        minimumButtonSizePx = context.dp(MinimumActionSizeDp),
        minimumMarginPx = context.dp(MinimumActionSpacingDp)
    ).apply {
        buttons.forEach { button ->
            addView(
                button,
                LinearLayout.LayoutParams(
                    context.dp(ActionSizeDp),
                    context.dp(ActionSizeDp)
                ).apply {
                    topMargin = context.dp(ActionSpacingDp)
                    bottomMargin = context.dp(ActionSpacingDp)
                }
            )
        }
    }

    private fun iconActionButton(
        @DrawableRes icon: Int,
        description: Int,
        accent: Boolean = false,
        soundEffect: InputFeedbacks.SoundEffect = InputFeedbacks.SoundEffect.Standard,
        action: () -> Unit
    ) = ToolButton(context, icon, theme).apply {
        contentDescription = context.getString(description)
        applyActionSurface(this, accent)
        this.soundEffect = soundEffect
        image.imageTintList = ColorStateList.valueOf(
            if (accent) theme.genericActiveForegroundColor else theme.altKeyTextColor
        )
        setOnClickListener { action() }
    }

    private fun textActionButton(
        text: String,
        description: Int,
        targetVisualHeightDp: Int,
        maxTextSizeSp: Float,
        soundEffect: InputFeedbacks.SoundEffect = InputFeedbacks.SoundEffect.Standard,
        onLabelCreated: (OpticallyCenteredActionTextView) -> Unit = {},
        action: () -> Unit
    ) = CustomGestureView(context).apply {
        contentDescription = context.getString(description)
        applyActionSurface(this, accent = false)
        this.soundEffect = soundEffect
        addView(
            OpticallyCenteredActionTextView(context).apply {
                this.text = text
                configureOpticalSize(
                    targetHeightPx = context.dp(targetVisualHeightDp),
                    maxWidthPx = context.dp(ActionLabelWidthDp),
                    maximumTextSizePx = maxTextSizeSp * resources.displayMetrics.scaledDensity
                )
                gravity = Gravity.CENTER
                includeFontPadding = false
                isClickable = false
                isFocusable = false
                InputUiFont.applyTo(this, Typeface.BOLD)
                setTextColor(theme.altKeyTextColor)
                onLabelCreated(this)
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        setOnClickListener { action() }
    }

    private fun applyActionSurface(view: CustomGestureView, accent: Boolean) {
        view.background = actionSurface(accent)
        view.foreground = null
    }

    private fun actionSurface(accent: Boolean): Drawable {
        val backgroundColor =
            if (accent) theme.genericActiveBackgroundColor else theme.altKeyBackgroundColor
        return if (ToolButton.disableAnimation) {
            StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_pressed),
                    LayerDrawable(
                        arrayOf(
                            roundedSurface(ActionRadiusDp, backgroundColor),
                            roundedSurface(ActionRadiusDp, theme.keyPressHighlightColor)
                        )
                    )
                )
                addState(
                    intArrayOf(),
                    roundedSurface(ActionRadiusDp, backgroundColor)
                )
            }
        } else {
            RippleDrawable(
                ColorStateList.valueOf(theme.keyPressHighlightColor),
                roundedSurface(ActionRadiusDp, backgroundColor),
                roundedSurface(ActionRadiusDp, android.graphics.Color.WHITE)
            )
        }
    }

    fun performAction(action: HandwritingAction) {
        when (action) {
            HandwritingAction.OPEN_EMOJI -> openEmojiKeyboard()
            HandwritingAction.DELETE_TEXT -> service.deleteCommittedTextFromHandwriting()
            HandwritingAction.OPEN_NUMBER -> openNumberKeyboard()
            HandwritingAction.INSERT_SPACE -> service.commitHandwritingLiteral(" ")
            HandwritingAction.SWITCH_LANGUAGE -> service.switchHandwritingLanguage()
            HandwritingAction.INSERT_COMMA -> service.commitHandwritingLiteral(
                context.getString(
                    if (currentLanguage == HandwritingLanguage.ENGLISH) {
                        R.string.handwriting_english_comma_shortcut
                    } else {
                        R.string.handwriting_comma_shortcut
                    }
                )
            )
            HandwritingAction.OPEN_SYMBOLS -> openSymbolKeyboard()
            HandwritingAction.RETURN -> service.performHandwritingReturn()
        }
    }

    private fun openSymbolKeyboard() {
        service.commitHandwritingCandidateBeforeAuxiliaryInput()
        windowManager.beginAuxiliaryInput(HandwritingWindow)
        windowManager.attachWindow(PickerWindow.Key.Symbol)
    }

    private fun openEmojiKeyboard() {
        service.commitHandwritingCandidateBeforeAuxiliaryInput()
        windowManager.beginAuxiliaryInput(HandwritingWindow)
        windowManager.attachWindow(PickerWindow.Key.Emoji)
    }

    private fun openNumberKeyboard() {
        service.commitHandwritingCandidateBeforeAuxiliaryInput()
        windowManager.beginAuxiliaryInput(HandwritingWindow)
        val keyboardWindow = windowManager.getEssentialWindow(KeyboardWindow) as KeyboardWindow
        keyboardWindow.switchLayout(NumberKeyboard.Name)
        // KeyboardWindow switches layouts on the main executor; attach in the following turn so
        // users never see the previous T9 layout flash before the requested number pad.
        ContextCompat.getMainExecutor(context).execute {
            windowManager.attachWindow(KeyboardWindow)
        }
    }

    private fun roundedSurface(radiusDp: Float, color: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dp(radiusDp)
            setColor(color)
        }
}

private class HandwritingActionRail(
    context: android.content.Context,
    private val preferredButtonSizePx: Int,
    private val preferredMarginPx: Int,
    private val minimumButtonSizePx: Int,
    private val minimumMarginPx: Int
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED && childCount > 0) {
            val availableHeight = (
                MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
                ).coerceAtLeast(0)
            val sizing = HandwritingActionRailLayoutPolicy.resolve(
                availableHeightPx = availableHeight,
                buttonCount = childCount,
                preferredButtonSizePx = preferredButtonSizePx,
                preferredMarginPx = preferredMarginPx,
                minimumButtonSizePx = minimumButtonSizePx,
                minimumMarginPx = minimumMarginPx
            )
            repeat(childCount) { index ->
                val params = getChildAt(index).layoutParams as LayoutParams
                params.width = sizing.buttonSizePx
                params.height = sizing.buttonSizePx
                params.topMargin = sizing.verticalMarginPx
                params.bottomMargin = sizing.verticalMarginPx
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

private class OpticallyCenteredActionTextView(context: android.content.Context) : TextView(context) {
    private val visualBounds = Rect()
    private var targetHeightPx = 0
    private var maxWidthPx = 0
    private var maximumTextSizePx = 0f
    private var textSizeScale = 1f
    private var measuredSignature = 0

    fun configureOpticalSize(targetHeightPx: Int, maxWidthPx: Int, maximumTextSizePx: Float) {
        this.targetHeightPx = targetHeightPx
        this.maxWidthPx = maxWidthPx
        this.maximumTextSizePx = maximumTextSizePx
        measuredSignature = 0
        invalidate()
    }

    fun setOpticalText(value: String, sizeScale: Float = 1f) {
        if (text?.toString() == value && textSizeScale == sizeScale) return
        text = value
        textSizeScale = sizeScale
        measuredSignature = 0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val value = text?.toString().orEmpty()
        if (value.isEmpty()) return
        resolveOpticalTextSize(value)
        // Manual glyph drawing bypasses TextView's state-color application; keep custom themes and
        // disabled states authoritative by applying the resolved color explicitly.
        paint.color = currentTextColor
        // Advance width includes punctuation side bearings, so both axes use visible glyph bounds
        // to keep commas, Latin digits, and CJK labels optically centered by the same rule.
        paint.getTextBounds(value, 0, value.length, visualBounds)
        val x = width / 2f - (visualBounds.left + visualBounds.right) / 2f
        val baseline = height / 2f - (visualBounds.top + visualBounds.bottom) / 2f
        canvas.drawText(value, x, baseline, paint)
    }

    private fun resolveOpticalTextSize(value: String) {
        val signature = 31 * value.hashCode() + 31 * width + (typeface?.hashCode() ?: 0)
        if (signature == measuredSignature || targetHeightPx <= 0 || maxWidthPx <= 0) return
        val scaledMaximumTextSizePx = maximumTextSizePx * textSizeScale
        paint.textSize = scaledMaximumTextSizePx
        paint.getTextBounds(value, 0, value.length, visualBounds)
        val visibleHeight = visualBounds.height().coerceAtLeast(1).toFloat()
        val visibleWidth = paint.measureText(value).coerceAtLeast(1f)
        val scale = minOf(
            1f,
            targetHeightPx / visibleHeight,
            maxWidthPx / visibleWidth
        )
        paint.textSize = scaledMaximumTextSizePx * scale
        measuredSignature = signature
    }
}
