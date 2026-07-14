/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.wm.EssentialWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import splitties.dimensions.dp

class HandwritingWindow : InputWindow.ExtendedInputWindow<HandwritingWindow>(), EssentialWindow {
    private val service: FcitxInputMethodService by manager.inputMethodService()
    private val theme by manager.theme()

    companion object : EssentialWindow.Key {
        private const val ControlHeightDp = 48
        private const val DisabledControlAlpha = 0.4f
    }

    override val key: EssentialWindow.Key
        get() = HandwritingWindow

    override val title: String
        get() = context.getString(R.string.handwriting_input)

    private lateinit var canvas: HandwritingCanvasView
    private lateinit var status: TextView
    private lateinit var undoButton: ImageButton
    private lateinit var clearButton: ImageButton
    private lateinit var retryButton: ImageButton

    override fun onCreateView(): View {
        canvas = HandwritingCanvasView(context).apply {
            brushColor = theme.keyTextColor
            setBackgroundColor(theme.keyboardColor)
            onStrokeFinished = service::addHandwritingStroke
        }
        status = TextView(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(theme.keyTextColor)
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(context.dp(12), 0, context.dp(8), 0)
        }
        undoButton = iconButton(R.drawable.ic_baseline_undo_24, R.string.handwriting_undo_stroke) {
            service.undoHandwritingStroke()
        }
        clearButton = iconButton(R.drawable.ic_baseline_delete_sweep_24, R.string.handwriting_clear) {
            service.clearHandwritingCharacter()
        }
        retryButton = iconButton(R.drawable.ic_baseline_sync_24, R.string.handwriting_retry_enhanced) {
            service.retryHandwritingEnhancedModel()
        }.apply {
            visibility = View.GONE
        }
        val controlRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.barColor)
            addView(status, LinearLayout.LayoutParams(0, context.dp(ControlHeightDp), 1f))
            addView(retryButton, LinearLayout.LayoutParams(context.dp(ControlHeightDp), context.dp(ControlHeightDp)))
            addView(undoButton, LinearLayout.LayoutParams(context.dp(ControlHeightDp), context.dp(ControlHeightDp)))
            addView(clearButton, LinearLayout.LayoutParams(context.dp(ControlHeightDp), context.dp(ControlHeightDp)))
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(canvas, LinearLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(controlRow, LinearLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, context.dp(ControlHeightDp)))
        }
    }

    override fun beforeAttached() {
        service.beginHandwritingInput()
    }

    override fun onAttached() {
        service.setHandwritingStateListener(::renderState)
    }

    override fun onDetached() {
        service.setHandwritingStateListener(null)
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
        val enhancedFailed = state.modelState == HandwritingModelState.ENHANCED_DOWNLOAD_FAILED
        retryButton.visibility = if (enhancedFailed) View.VISIBLE else View.GONE
        val message = when {
            state.recognizing -> R.string.handwriting_recognizing
            state.noMatch -> R.string.handwriting_no_match
            state.modelState == HandwritingModelState.PREPARING_OFFLINE ->
                R.string.handwriting_preparing_offline
            state.modelState == HandwritingModelState.DOWNLOADING_ENHANCED ->
                R.string.handwriting_downloading_enhanced
            enhancedFailed -> R.string.handwriting_enhanced_download_failed
            else -> 0
        }
        status.visibility = if (message == 0) View.INVISIBLE else View.VISIBLE
        if (message != 0) status.setText(message)
        status.setTextColor(if (enhancedFailed) theme.keyTextColor else theme.candidateCommentColor)
    }

    private fun iconButton(icon: Int, description: Int, action: () -> Unit) =
        ImageButton(context).apply {
            setImageResource(icon)
            contentDescription = context.getString(description)
            imageTintList = ColorStateList.valueOf(theme.keyTextColor)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(theme.barColor)
            }
            setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
            setOnClickListener { action() }
        }
}
