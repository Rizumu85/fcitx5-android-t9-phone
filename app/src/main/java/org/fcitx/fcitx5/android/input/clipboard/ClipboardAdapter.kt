/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.clipboard

import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardEntry
import org.fcitx.fcitx5.android.data.theme.Theme
import kotlin.math.min

abstract class ClipboardAdapter(
    private val theme: Theme,
    private val entryRadius: Float,
    private val maskSensitive: Boolean
) : PagingDataAdapter<ClipboardEntry, ClipboardAdapter.ViewHolder>(diffCallback) {

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<ClipboardEntry>() {
            override fun areItemsTheSame(
                oldItem: ClipboardEntry,
                newItem: ClipboardEntry
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: ClipboardEntry,
                newItem: ClipboardEntry
            ): Boolean {
                return oldItem == newItem
            }
        }

        /**
         * excerpt text to show on ClipboardEntryUi, to reduce render time of very long text
         * @param str text to excerpt
         * @param mask mask text content with "•"
         * @param lines max output lines
         * @param chars max chars per output line
         */
        fun excerptText(
            str: String,
            mask: Boolean = false,
            lines: Int = 4,
            chars: Int = 128
        ): String = buildString {
            val length = str.length
            var lineBreak = -1
            for (i in 1..lines) {
                val start = lineBreak + 1   // skip previous '\n'
                val excerptEnd = min(start + chars, length)
                lineBreak = str.indexOf('\n', start)
                if (lineBreak < 0) {
                    // no line breaks remaining, substring to end of text
                    if (mask) {
                        append(ClipboardEntry.BULLET.repeat(excerptEnd - start))
                    } else {
                        append(str.substring(start, excerptEnd))
                    }
                    break
                } else {
                    val end = min(excerptEnd, lineBreak)
                    // append one line exactly
                    if (mask) {
                        append(ClipboardEntry.BULLET.repeat(end - start))
                    } else {
                        appendLine(str.substring(start, end))
                    }
                }
            }
        }
    }

    private var actionMenu: ClipboardActionMenu? = null

    class ViewHolder(val entryUi: ClipboardEntryUi) : RecyclerView.ViewHolder(entryUi.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ClipboardEntryUi(parent.context, theme, entryRadius))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position) ?: return
        with(holder.entryUi) {
            setEntry(excerptText(entry.text, entry.sensitive && maskSensitive), entry.pinned)
            root.setOnClickListener {
                onPaste(entry)
            }
            root.setOnLongClickListener {
                actionMenu?.dismiss()
                actionMenu = ClipboardActionMenu(
                    anchor = root,
                    theme = theme,
                    actions = buildList {
                        add(
                            ClipboardActionMenu.Action(
                                label = if (entry.pinned) R.string.unpin else R.string.pin,
                                icon = if (entry.pinned) {
                                    R.drawable.ic_outline_push_pin_24
                                } else {
                                    R.drawable.ic_baseline_push_pin_24
                                },
                                invoke = {
                                    if (entry.pinned) onUnpin(entry.id) else onPin(entry.id)
                                }
                            )
                        )
                        add(
                            ClipboardActionMenu.Action(
                                R.string.edit,
                                R.drawable.ic_baseline_edit_24
                            ) { onEdit(entry.id) }
                        )
                        add(
                            ClipboardActionMenu.Action(
                                R.string.share,
                                R.drawable.ic_baseline_share_24
                            ) { onShare(entry) }
                        )
                        add(
                            ClipboardActionMenu.Action(
                                R.string.delete,
                                R.drawable.ic_baseline_delete_24
                            ) { onDelete(entry.id) }
                        )
                    },
                    onDismiss = { dismissed ->
                        if (dismissed === actionMenu) actionMenu = null
                    }
                ).also(ClipboardActionMenu::show)
                true
            }
        }
    }

    fun getEntryAt(position: Int) = getItem(position)

    fun onDetached() {
        actionMenu?.dismiss()
        actionMenu = null
    }

    abstract fun onPaste(entry: ClipboardEntry)

    abstract fun onPin(id: Int)

    abstract fun onUnpin(id: Int)

    abstract fun onEdit(id: Int)

    abstract fun onShare(entry: ClipboardEntry)

    abstract fun onDelete(id: Int)

}
