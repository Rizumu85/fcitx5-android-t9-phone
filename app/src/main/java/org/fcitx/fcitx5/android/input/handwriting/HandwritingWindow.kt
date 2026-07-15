/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.snackbar.Snackbar
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.InputUiFont
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.keyboard.NumberKeyboard
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.wm.EssentialWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.fcitx.fcitx5.android.utils.AppUtil
import org.fcitx.fcitx5.android.utils.rippleDrawable
import org.mechdancer.dependency.manager.must
import splitties.dimensions.dp

class HandwritingWindow : InputWindow.ExtendedInputWindow<HandwritingWindow>(), EssentialWindow,
    InputBroadcastReceiver {
    private val service: FcitxInputMethodService by manager.inputMethodService()
    private val theme by manager.theme()
    private val windowManager: InputWindowManager by manager.must()
    private val returnKeyDrawable: ReturnKeyDrawableComponent by manager.must()
    private val promptPref = AppPrefs.getInstance().internal.handwritingEnhancedPromptShown
    private val brushStylePref = AppPrefs.getInstance().keyboard.handwritingBrushStyle

    companion object : EssentialWindow.Key {
        private const val DisabledControlAlpha = 0.4f
        private const val ActionRadiusDp = 14f
        private const val ActionRailWidthDp = 44
        private const val ActionSizeDp = 40
        private const val ActionSpacingDp = 4
        private const val ActionLabelHeightDp = 18
        private const val ActionLabelWidthDp = 28
        private const val ActionLabelMaxSizeSp = 18f
        private const val ActionPunctuationHeightDp = 16
        private const val ActionPunctuationMaxSizeSp = 24f
    }

    override val key: EssentialWindow.Key
        get() = HandwritingWindow

    override val title: String
        get() = context.getString(R.string.handwriting_input)

    private lateinit var canvas: HandwritingCanvasView
    private lateinit var root: FrameLayout
    private lateinit var status: TextView
    private lateinit var returnButton: ToolButton
    private var modelPrompt: Snackbar? = null

    private val undoButton by lazy {
        toolButton(R.drawable.ic_baseline_undo_24, R.string.handwriting_undo_stroke) {
            service.undoHandwritingStroke()
        }
    }
    private val clearButton by lazy {
        toolButton(R.drawable.ic_baseline_delete_sweep_24, R.string.handwriting_clear) {
            service.clearHandwritingCharacter()
        }
    }
    private val barExtension by lazy {
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            addView(undoButton, LinearLayout.LayoutParams(context.dp(40), context.dp(40)))
            addView(clearButton, LinearLayout.LayoutParams(context.dp(40), context.dp(40)))
        }
    }

    override fun onCreateView(): View {
        canvas = HandwritingCanvasView(context, brushStylePref::getValue).apply {
            brushColor = theme.keyTextColor
            background = roundedSurface(21f, theme.keyboardColor)
            clipToOutline = true
            onStrokeStarted = service::beginHandwritingStroke
            onStrokeFinished = service::addHandwritingStroke
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
                action = ::openEmojiKeyboard
            ),
            textActionButton(
                text = context.getString(R.string.handwriting_number_shortcut),
                description = R.string.handwriting_open_number_keyboard,
                targetVisualHeightDp = ActionLabelHeightDp,
                maxTextSizeSp = ActionLabelMaxSizeSp,
                action = ::openNumberKeyboard
            ),
            textActionButton(
                text = context.getString(R.string.handwriting_chinese_shortcut),
                description = R.string.handwriting_language_switch_placeholder,
                targetVisualHeightDp = ActionLabelHeightDp,
                maxTextSizeSp = ActionLabelMaxSizeSp,
                action = {}
            ).apply {
                isEnabled = false
                alpha = 0.55f
            },
            textActionButton(
                text = context.getString(R.string.handwriting_comma_shortcut),
                description = R.string.handwriting_insert_comma,
                targetVisualHeightDp = ActionPunctuationHeightDp,
                maxTextSizeSp = ActionPunctuationMaxSizeSp
            ) {
                service.commitHandwritingLiteral(
                    context.getString(R.string.handwriting_comma_shortcut)
                )
            }
        )
        val rightRail = actionRail(
            iconActionButton(
                icon = R.drawable.ic_baseline_backspace_24,
                description = R.string.handwriting_delete_committed_text,
                action = service::deleteCommittedTextFromHandwriting
            ),
            iconActionButton(
                icon = R.drawable.ic_baseline_space_bar_24,
                description = R.string.handwriting_insert_space
            ) {
                service.commitHandwritingLiteral(" ")
            },
            textActionButton(
                text = context.getString(R.string.handwriting_symbol_shortcut),
                description = R.string.handwriting_open_symbol_keyboard,
                targetVisualHeightDp = ActionLabelHeightDp,
                maxTextSizeSp = ActionLabelMaxSizeSp,
                action = ::openSymbolKeyboard
            ),
            iconActionButton(
                icon = returnKeyDrawable.resourceId,
                description = R.string.handwriting_return,
                accent = true,
                action = service::performHandwritingReturn
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
        modelPrompt?.dismiss()
        modelPrompt = null
        // Leaving handwriting is an explicit cancellation boundary. Uncommitted strokes are
        // discarded immediately so returning to T9 never revives an accidental character.
        service.endHandwritingInput()
    }

    override fun onReturnKeyDrawableUpdate(resourceId: Int) {
        if (::returnButton.isInitialized) returnButton.setIcon(resourceId)
    }

    private fun renderState(state: HandwritingViewState) {
        canvas.setCompletedStrokes(state.strokes)
        undoButton.isEnabled = state.strokes.isNotEmpty()
        clearButton.isEnabled = state.strokes.isNotEmpty()
        undoButton.alpha = if (undoButton.isEnabled) 1f else DisabledControlAlpha
        clearButton.alpha = if (clearButton.isEnabled) 1f else DisabledControlAlpha
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
        if (
            state.modelState == HandwritingModelState.ENHANCED_MODEL_MISSING &&
            !promptPref.getValue()
        ) {
            promptPref.setValue(true)
            modelPrompt = Snackbar.make(
                root,
                R.string.handwriting_enhanced_onboarding,
                Snackbar.LENGTH_LONG
            )
                .setBackgroundTint(theme.popupBackgroundColor)
                .setTextColor(theme.popupTextColor)
                .setActionTextColor(theme.genericActiveBackgroundColor)
                .setAction(R.string.handwriting_go_to_download) {
                    AppUtil.launchMainToHandwritingModel(context)
                }
                .also(Snackbar::show)
        }
    }

    private fun toolButton(icon: Int, description: Int, action: () -> Unit) =
        ToolButton(context, icon, theme).apply {
            contentDescription = context.getString(description)
            setOnClickListener { action() }
        }

    private fun actionRail(vararg buttons: View) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
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
        action: () -> Unit
    ) = ToolButton(context, icon, theme).apply {
        contentDescription = context.getString(description)
        applyActionSurface(this, accent)
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
        action: () -> Unit
    ) = OpticallyCenteredActionTextView(context).apply {
        this.text = text
        configureOpticalSize(
            targetHeightPx = context.dp(targetVisualHeightDp),
            maxWidthPx = context.dp(ActionLabelWidthDp),
            maximumTextSizePx = maxTextSizeSp * resources.displayMetrics.scaledDensity
        )
        contentDescription = context.getString(description)
        gravity = Gravity.CENTER
        includeFontPadding = false
        InputUiFont.applyTo(this, Typeface.BOLD)
        setTextColor(theme.altKeyTextColor)
        applyActionSurface(this, accent = false)
        setOnClickListener { action() }
    }

    private fun applyActionSurface(view: View, accent: Boolean) {
        view.background = roundedSurface(
            ActionRadiusDp,
            if (accent) theme.genericActiveBackgroundColor else theme.altKeyBackgroundColor
        )
        view.foreground = rippleDrawable(
            theme.keyPressHighlightColor,
            roundedSurface(ActionRadiusDp, Color.WHITE)
        )
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

private class OpticallyCenteredActionTextView(context: android.content.Context) : TextView(context) {
    private val visualBounds = Rect()
    private var targetHeightPx = 0
    private var maxWidthPx = 0
    private var maximumTextSizePx = 0f
    private var measuredSignature = 0

    fun configureOpticalSize(targetHeightPx: Int, maxWidthPx: Int, maximumTextSizePx: Float) {
        this.targetHeightPx = targetHeightPx
        this.maxWidthPx = maxWidthPx
        this.maximumTextSizePx = maximumTextSizePx
        measuredSignature = 0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val value = text?.toString().orEmpty()
        if (value.isEmpty()) return
        resolveOpticalTextSize(value)
        // Font metrics center the em box, which leaves punctuation visibly low. Centering actual
        // glyph bounds gives commas, Latin digits, and CJK labels one optical alignment rule.
        paint.getTextBounds(value, 0, value.length, visualBounds)
        val x = (width - paint.measureText(value)) / 2f
        val baseline = height / 2f - (visualBounds.top + visualBounds.bottom) / 2f
        canvas.drawText(value, x, baseline, paint)
    }

    private fun resolveOpticalTextSize(value: String) {
        val signature = 31 * value.hashCode() + 31 * width + (typeface?.hashCode() ?: 0)
        if (signature == measuredSignature || targetHeightPx <= 0 || maxWidthPx <= 0) return
        paint.textSize = maximumTextSizePx
        paint.getTextBounds(value, 0, value.length, visualBounds)
        val visibleHeight = visualBounds.height().coerceAtLeast(1).toFloat()
        val visibleWidth = paint.measureText(value).coerceAtLeast(1f)
        val scale = minOf(
            1f,
            targetHeightPx / visibleHeight,
            maxWidthPx / visibleWidth
        )
        paint.textSize = maximumTextSizePx * scale
        measuredSignature = signature
    }
}
