/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.snackbar.Snackbar
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.wm.EssentialWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.utils.AppUtil
import splitties.dimensions.dp

class HandwritingWindow : InputWindow.ExtendedInputWindow<HandwritingWindow>(), EssentialWindow {
    private val service: FcitxInputMethodService by manager.inputMethodService()
    private val theme by manager.theme()
    private val promptPref = AppPrefs.getInstance().internal.handwritingEnhancedPromptShown
    private val brushStylePref = AppPrefs.getInstance().keyboard.handwritingBrushStyle

    companion object : EssentialWindow.Key {
        private const val DisabledControlAlpha = 0.4f
    }

    override val key: EssentialWindow.Key
        get() = HandwritingWindow

    override val title: String
        get() = context.getString(R.string.handwriting_input)

    private lateinit var canvas: HandwritingCanvasView
    private lateinit var root: FrameLayout
    private lateinit var status: TextView
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
            background = roundedSurface(21f, theme.keyboardColor, withBorder = true)
            clipToOutline = true
            onStrokeFinished = service::addHandwritingStroke
        }
        status = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(theme.candidateCommentColor)
            textSize = 14f
            includeFontPadding = false
            maxWidth = (resources.displayMetrics.widthPixels - context.dp(40)).coerceAtLeast(
                context.dp(120)
            )
            minHeight = context.dp(28)
            setPadding(context.dp(10), context.dp(5), context.dp(10), context.dp(5))
            background = roundedSurface(10f, theme.keyboardColor)
        }
        root = FrameLayout(context).apply {
            setBackgroundColor(theme.barColor)
            // A drawable offset preserves the tray shadow without putting AndroidX Ink inside an
            // elevated render layer, which would delay or hide its front-buffer strokes.
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
                    setMargins(context.dp(10), context.dp(8), context.dp(10), context.dp(7))
                }
            )
            addView(
                canvas,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(context.dp(10), context.dp(6), context.dp(10), context.dp(10))
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

    private fun roundedSurface(radiusDp: Float, color: Int, withBorder: Boolean = false) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dp(radiusDp)
            setColor(color)
            if (withBorder) {
                setStroke(
                    context.dp(1),
                    ColorUtils.setAlphaComponent(theme.keyTextColor, (0.07f * 255).toInt())
                )
            }
        }
}
