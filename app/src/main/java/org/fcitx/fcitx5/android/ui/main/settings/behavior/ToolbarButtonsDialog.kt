/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.bar.ToolbarButtonOrder
import splitties.dimensions.dp

class ToolbarButtonsDialog(
    private val context: Context,
    private val prefs: AppPrefs.Keyboard
) {
    private data class Item(
        val id: ToolbarButtonOrder,
        @StringRes val label: Int,
        @DrawableRes val icon: Int,
        var visible: Boolean
    )

    private val items = ToolbarButtonOrder.decode(prefs.toolbarButtonOrder.getValue()).map { id ->
        when (id) {
            ToolbarButtonOrder.Undo -> Item(id, R.string.undo, R.drawable.ic_baseline_undo_24, prefs.showUndoButton.getValue())
            ToolbarButtonOrder.Redo -> Item(id, R.string.redo, R.drawable.ic_baseline_redo_24, prefs.showRedoButton.getValue())
            ToolbarButtonOrder.TextEditing -> Item(id, R.string.text_editing, R.drawable.ic_cursor_move, prefs.showTextEditingButton.getValue())
            ToolbarButtonOrder.Clipboard -> Item(id, R.string.clipboard, R.drawable.ic_clipboard, prefs.showClipboardButton.getValue())
        }
    }.toMutableList()

    private var voiceVisible = prefs.showVoiceInputButton.getValue()
    private var hideVisible = prefs.showHideKeyboardButton.getValue()
    private val preview = LinearLayout(context).apply { gravity = Gravity.CENTER }
    private val adapter = Adapter()

    fun show() {
        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ToolbarButtonsDialog.adapter
            isNestedScrollingEnabled = false
        }
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(rv: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = source.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from !in items.indices || to !in items.indices) return false
                val moved = items.removeAt(from)
                items.add(to, moved)
                adapter.notifyItemMoved(from, to)
                renderPreview()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        }).also { it.attachToRecyclerView(recycler) }
        adapter.startDrag = touchHelper::startDrag

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(4))
            addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(56)))
            addView(fixedRow(R.string.show_voice_input_button, R.drawable.ic_baseline_keyboard_voice_24, voiceVisible) {
                voiceVisible = it
                renderPreview()
            })
            addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(208)))
            addView(fixedRow(R.string.status_area, R.drawable.ic_baseline_more_horiz_24, true, enabled = false) {})
            addView(fixedRow(R.string.hide_keyboard, R.drawable.ic_baseline_arrow_drop_down_24, hideVisible) {
                hideVisible = it
                renderPreview()
            })
        }
        renderPreview()
        AlertDialog.Builder(context)
            .setTitle(R.string.toolbar_buttons)
            .setView(body)
            .setPositiveButton(android.R.string.ok) { _, _ -> save() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun fixedRow(
        @StringRes label: Int,
        @DrawableRes icon: Int,
        checked: Boolean,
        enabled: Boolean = true,
        changed: (Boolean) -> Unit
    ) = row(label, icon, checked, enabled, draggable = false, changed = changed)

    private fun row(
        @StringRes label: Int,
        @DrawableRes icon: Int,
        checked: Boolean,
        enabled: Boolean,
        draggable: Boolean,
        changed: (Boolean) -> Unit
    ) = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dp(52)
        val checkBox = CheckBox(context).apply {
            isChecked = checked
            isEnabled = enabled
            setOnCheckedChangeListener { _, value -> changed(value) }
        }
        addView(
            iconView(if (draggable) R.drawable.ic_baseline_drag_handle_24 else 0),
            LinearLayout.LayoutParams(context.dp(44), context.dp(44))
        )
        addView(iconView(icon), LinearLayout.LayoutParams(context.dp(36), context.dp(36)))
        addView(TextView(context).apply {
            if (label != 0) setText(label)
            isEnabled = enabled
            textSize = 16f
            setPadding(context.dp(8), 0, context.dp(8), 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(checkBox, LinearLayout.LayoutParams(context.dp(48), context.dp(48)))
        setOnClickListener { if (enabled) checkBox.isChecked = !checkBox.isChecked }
    }

    private fun iconView(@DrawableRes icon: Int) = ImageView(context).apply {
        if (icon != 0) setImageResource(icon)
        setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
        imageTintList = ContextCompat.getColorStateList(context, android.R.color.darker_gray)
    }

    private fun renderPreview() {
        preview.removeAllViews()
        if (voiceVisible) preview.addView(iconView(R.drawable.ic_baseline_keyboard_voice_24))
        items.filter { it.visible }.forEach { preview.addView(iconView(it.icon)) }
        preview.addView(iconView(R.drawable.ic_baseline_more_horiz_24))
        if (hideVisible) preview.addView(iconView(R.drawable.ic_baseline_arrow_drop_down_24))
    }

    private fun save() {
        prefs.showVoiceInputButton.setValue(voiceVisible)
        prefs.showHideKeyboardButton.setValue(hideVisible)
        prefs.showUndoButton.setValue(items.first { it.id == ToolbarButtonOrder.Undo }.visible)
        prefs.showRedoButton.setValue(items.first { it.id == ToolbarButtonOrder.Redo }.visible)
        prefs.showTextEditingButton.setValue(items.first { it.id == ToolbarButtonOrder.TextEditing }.visible)
        prefs.showClipboardButton.setValue(items.first { it.id == ToolbarButtonOrder.Clipboard }.visible)
        prefs.toolbarButtonOrder.setValue(ToolbarButtonOrder.encode(items.map { it.id }))
    }

    private inner class Holder(val row: LinearLayout) : RecyclerView.ViewHolder(row)

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        var startDrag: (RecyclerView.ViewHolder) -> Unit = {}

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
            row(0, 0, false, enabled = true, draggable = true) {}
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val handle = holder.row.getChildAt(0)
            val icon = holder.row.getChildAt(1) as ImageView
            val label = holder.row.getChildAt(2) as TextView
            val checkBox = holder.row.getChildAt(3) as CheckBox
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = item.visible
            checkBox.setOnCheckedChangeListener { _, value -> item.visible = value; renderPreview() }
            icon.setImageResource(item.icon)
            label.setText(item.label)
            holder.row.setOnClickListener { checkBox.isChecked = !checkBox.isChecked }
            handle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) startDrag(holder)
                false
            }
        }
    }
}
