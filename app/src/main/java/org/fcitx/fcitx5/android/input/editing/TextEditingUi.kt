/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.editing

import android.content.Context
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Guideline
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import splitties.dimensions.dp
import splitties.views.backgroundColor
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.leftOfParent
import splitties.views.dsl.constraintlayout.leftToRightOf
import splitties.views.dsl.constraintlayout.rightOfParent
import splitties.views.dsl.constraintlayout.rightToLeftOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.view

class TextEditingUi(
    override val ctx: Context,
    private val theme: Theme,
    private val border: Boolean,
    private val radius: Float
) : Ui {

    private fun textButton(
        @StringRes id: Int,
        shape: TextEditingButton.Shape = TextEditingButton.Shape.Standalone
    ) = TextEditingButton(ctx, theme, border, radius, shape).apply {
        setText(id)
    }

    private fun iconButton(
        @DrawableRes icon: Int,
        shape: TextEditingButton.Shape = TextEditingButton.Shape.Standalone
    ) = TextEditingButton(ctx, theme, border, radius, shape).apply {
        setIcon(icon)
    }

    val upButton = iconButton(
        R.drawable.ic_baseline_keyboard_arrow_up_24,
        shape = TextEditingButton.Shape.DpadUp
    ).apply {
        contentDescription = ctx.getString(R.string.move_cursor_up)
    }

    val rightButton = iconButton(
        R.drawable.ic_baseline_keyboard_arrow_right_24,
        shape = TextEditingButton.Shape.DpadRight
    ).apply {
        contentDescription = ctx.getString(R.string.move_cursor_right)
    }

    val downButton = iconButton(
        R.drawable.ic_baseline_keyboard_arrow_down_24,
        shape = TextEditingButton.Shape.DpadDown
    ).apply {
        contentDescription = ctx.getString(R.string.move_cursor_down)
    }

    val leftButton = iconButton(
        R.drawable.ic_baseline_keyboard_arrow_left_24,
        shape = TextEditingButton.Shape.DpadLeft
    ).apply {
        contentDescription = ctx.getString(R.string.move_cursor_left)
    }

    val selectButton = textButton(
        R.string.select,
        shape = TextEditingButton.Shape.DpadCenter
    ).apply {
        enableActivatedState()
    }

    val homeButton = iconButton(R.drawable.ic_baseline_first_page_24).apply {
        contentDescription = ctx.getString(R.string.move_cursor_to_start)
    }

    val endButton = iconButton(R.drawable.ic_baseline_last_page_24).apply {
        contentDescription = ctx.getString(R.string.move_cursor_to_end)
    }

    // Editing actions need the same visible key surface as navigation because this panel's
    // background intentionally equals barColor, which is also the alt-key color in some themes.
    val selectAllButton = textButton(android.R.string.selectAll)

    val cutButton = textButton(android.R.string.cut).apply {
        visibility = View.GONE
    }

    val copyButton = textButton(android.R.string.copy)

    val pasteButton = textButton(android.R.string.paste)

    val backspaceButton = iconButton(R.drawable.ic_baseline_backspace_24).apply {
        soundEffect = InputFeedbacks.SoundEffect.Delete
        contentDescription = ctx.getString(R.string.backspace)
    }

    private val actionGuide = view(::Guideline)

    private val dpad = constraintLayout {
        background = TextEditingDpadDrawable(
            theme.keyBackgroundColor,
            radius,
            theme.keyShadowColor,
            if (border) dp(1) else 0
        )

        add(leftButton, lParams {
            below(upButton)
            leftOfParent()
            above(downButton)
            rightToLeftOf(selectButton)
        })
        add(upButton, lParams {
            topOfParent()
            leftToRightOf(leftButton)
            above(selectButton)
            rightToLeftOf(rightButton)
        })
        add(selectButton, lParams {
            below(upButton)
            leftToRightOf(leftButton)
            above(downButton)
            rightToLeftOf(rightButton)
        })
        add(downButton, lParams {
            below(selectButton)
            leftToRightOf(leftButton)
            bottomOfParent()
            rightToLeftOf(rightButton)
        })
        add(rightButton, lParams {
            below(upButton)
            leftToRightOf(selectButton)
            above(downButton)
            rightOfParent()
        })
    }

    override val root = constraintLayout {
        // The title and controls are one editing surface; using the bar color here avoids a
        // second keyboard-colored band between them in themes where those tokens differ.
        backgroundColor = theme.barColor

        add(actionGuide, lParams {
            orientation = ConstraintLayout.LayoutParams.VERTICAL
            guidePercent = 0.7f
        })
        add(dpad, lParams {
            topOfParent()
            leftOfParent()
            above(homeButton)
            rightToLeftOf(actionGuide)
            matchConstraintPercentWidth = 0.48f
            horizontalBias = 0.5f
        })

        add(homeButton, lParams {
            below(dpad)
            leftOfParent()
            bottomOfParent()
            rightToLeftOf(endButton)
            matchConstraintPercentHeight = 0.25f
        })
        add(endButton, lParams {
            below(dpad)
            leftToRightOf(homeButton)
            bottomOfParent()
            rightToLeftOf(actionGuide)
            matchConstraintPercentHeight = 0.25f
        })

        add(selectAllButton, lParams {
            topOfParent()
            leftToRightOf(actionGuide)
            rightOfParent()
            above(cutButton)
        })
        add(cutButton, lParams {
            below(selectAllButton)
            leftToRightOf(actionGuide)
            rightOfParent()
            above(copyButton)
        })
        add(copyButton, lParams {
            below(cutButton)
            leftToRightOf(actionGuide)
            rightOfParent()
            above(pasteButton)
        })
        add(pasteButton, lParams {
            below(copyButton)
            leftToRightOf(actionGuide)
            rightOfParent()
            above(backspaceButton)
        })
        add(backspaceButton, lParams {
            below(pasteButton)
            leftToRightOf(actionGuide)
            rightOfParent()
            bottomOfParent()
        })
    }

    fun updateSelection(hasSelection: Boolean, userSelection: Boolean) {
        selectButton.isActivated = (hasSelection || userSelection)
        copyButton.isEnabled = hasSelection
        cutButton.isEnabled = hasSelection
        if (hasSelection) {
            selectAllButton.apply {
                visibility = View.GONE
            }
            cutButton.apply {
                visibility = View.VISIBLE
            }
        } else {
            selectAllButton.apply {
                visibility = View.VISIBLE
            }
            cutButton.apply {
                visibility = View.GONE
            }
        }
    }

    val clipboardButton = ToolButton(ctx, R.drawable.ic_clipboard, theme).apply {
        contentDescription = ctx.getString(R.string.clipboard)
    }

    val extension = horizontalLayout {
        add(clipboardButton, lParams(dp(40), dp(40)))
    }
}
