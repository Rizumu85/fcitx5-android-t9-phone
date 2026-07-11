/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.bar.ui.idle

import android.content.Context
import android.view.View
import androidx.annotation.DrawableRes
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.bar.ToolbarButtonOrder
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.view

class ButtonsBarUi(override val ctx: Context, private val theme: Theme) : Ui {

    override val root = view(::FlexboxLayout) {
        alignItems = AlignItems.CENTER
        // Toolbar actions form one ordered control group; distributing them across
        // the full row made related actions such as undo and redo feel disconnected.
        justifyContent = JustifyContent.CENTER
    }

    private fun toolButton(@DrawableRes icon: Int) = ToolButton(ctx, icon, theme).also {
        val size = ctx.dp(40)
        root.addView(it, FlexboxLayout.LayoutParams(size, size).apply {
            leftMargin = ctx.dp(4)
            rightMargin = ctx.dp(4)
        })
    }

    val undoButton = toolButton(R.drawable.ic_baseline_undo_24).apply {
        contentDescription = ctx.getString(R.string.undo)
    }

    val redoButton = toolButton(R.drawable.ic_baseline_redo_24).apply {
        contentDescription = ctx.getString(R.string.redo)
    }

    val voiceInputButton = toolButton(R.drawable.ic_baseline_keyboard_voice_24).apply {
        contentDescription = ctx.getString(R.string.switch_to_voice_input)
    }

    val cursorMoveButton = toolButton(R.drawable.ic_cursor_move).apply {
        contentDescription = ctx.getString(R.string.text_editing)
    }

    val clipboardButton = toolButton(R.drawable.ic_clipboard).apply {
        contentDescription = ctx.getString(R.string.clipboard)
    }

    val moreButton = toolButton(R.drawable.ic_baseline_more_horiz_24).apply {
        contentDescription = ctx.getString(R.string.status_area)
    }

    fun setButtonOrder(order: List<ToolbarButtonOrder>) {
        val buttons = mapOf(
            ToolbarButtonOrder.Undo to undoButton,
            ToolbarButtonOrder.Redo to redoButton,
            ToolbarButtonOrder.VoiceInput to voiceInputButton,
            ToolbarButtonOrder.TextEditing to cursorMoveButton,
            ToolbarButtonOrder.Clipboard to clipboardButton
        )
        order.forEachIndexed { index, id ->
            root.removeView(buttons.getValue(id))
            root.addView(buttons.getValue(id), index)
        }
    }

    fun setOptionalButtonsVisible(
        undo: Boolean,
        redo: Boolean,
        voiceInput: Boolean,
        textEditing: Boolean,
        clipboard: Boolean
    ) {
        undoButton.visibility = if (undo) View.VISIBLE else View.GONE
        redoButton.visibility = if (redo) View.VISIBLE else View.GONE
        voiceInputButton.visibility = if (voiceInput) View.VISIBLE else View.GONE
        cursorMoveButton.visibility = if (textEditing) View.VISIBLE else View.GONE
        clipboardButton.visibility = if (clipboard) View.VISIBLE else View.GONE
    }
}
