/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.floating

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.textView

class LabeledCandidateItemUi(
    override val ctx: Context,
    val theme: Theme,
    setupTextView: TextView.() -> Unit,
    private val highlightCornerRadiusPx: Int
) : Ui {

    private val t9InputModeEnabled by AppPrefs.getInstance().keyboard.useT9KeyboardLayout
    private val activeBackground = GradientDrawable().apply {
        setColor(theme.genericActiveBackgroundColor)
        cornerRadius = highlightCornerRadiusPx.toFloat()
        alpha = 0
    }
    private var lastActive = false
    private var lastCandidateSignature = ""
    private var highlightAnimator: ValueAnimator? = null

    override val root = textView {
        setupTextView(this)
        background = activeBackground
    }

    fun update(candidate: FcitxEvent.Candidate, active: Boolean) {
        val candidateSignature = "${candidate.label}|${candidate.text}|${candidate.comment}"
        val labelFg = if (active) theme.genericActiveForegroundColor else theme.candidateLabelColor
        val fg = if (active) theme.genericActiveForegroundColor else theme.candidateTextColor
        val altFg = if (active) theme.genericActiveForegroundColor else theme.candidateCommentColor
        root.text = buildSpannedString {
            // Hide label and comment in T9 mode for cleaner display
            if (!t9InputModeEnabled) {
                color(labelFg) { append(candidate.label) }
            }
            color(fg) { append(candidate.text) }
            if (!t9InputModeEnabled && candidate.comment.isNotBlank()) {
                append(" ")
                color(altFg) { append(candidate.comment) }
            }
        }
        if (candidateSignature != lastCandidateSignature) {
            lastCandidateSignature = candidateSignature
            highlightAnimator?.cancel()
            lastActive = active
            activeBackground.alpha = if (active) 255 else 0
            val scale = if (active) ACTIVE_HIGHLIGHT_SCALE else 1f
            root.scaleX = scale
            root.scaleY = scale
            return
        }
        animateHighlight(active)
    }

    private fun animateHighlight(active: Boolean) {
        val targetAlpha = if (active) 255 else 0
        val targetScale = if (active) ACTIVE_HIGHLIGHT_SCALE else 1f
        if (lastActive == active && activeBackground.alpha == targetAlpha) {
            root.scaleX = targetScale
            root.scaleY = targetScale
            return
        }
        lastActive = active
        highlightAnimator?.cancel()
        val startAlpha = activeBackground.alpha
        val startScale = root.scaleX.takeIf { it > 0f } ?: 1f
        highlightAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = HIGHLIGHT_ANIMATION_DURATION_MS
            interpolator = if (active) HIGHLIGHT_ACTIVATE_INTERPOLATOR else AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                activeBackground.alpha =
                    (startAlpha + (targetAlpha - startAlpha) * progress).toInt()
                val scale = startScale + (targetScale - startScale) * progress
                root.scaleX = scale
                root.scaleY = scale
            }
            start()
        }
    }

    companion object {
        private const val HIGHLIGHT_ANIMATION_DURATION_MS = 190L
        private const val ACTIVE_HIGHLIGHT_SCALE = 1.07f
        private val HIGHLIGHT_ACTIVATE_INTERPOLATOR = OvershootInterpolator(0.55f)
    }
}
