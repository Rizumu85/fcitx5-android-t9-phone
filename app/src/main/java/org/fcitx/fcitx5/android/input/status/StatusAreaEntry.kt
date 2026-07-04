/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.status

import android.content.Context
import androidx.annotation.DrawableRes
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.Action

sealed class StatusAreaEntry(
    val label: String,
    @DrawableRes
    val icon: Int,
    val active: Boolean
) {
    class Android(label: String, icon: Int, val type: Type, active: Boolean = false) :
        StatusAreaEntry(label, icon, active) {
        enum class Type {
            InputMethod,
            ReloadConfig,
            Keyboard,
            ThemeList,
            TemporaryFullKeyboard,
            SmartEnglishT9
        }
    }

    class Fcitx(val action: Action, label: String, icon: Int, active: Boolean) :
        StatusAreaEntry(label, icon, active)

    companion object {
        private fun drawableFromIconName(icon: String) = when (icon) {
            // androidkeyboard
            "tools-check-spelling" -> R.drawable.ic_baseline_spellcheck_24
            // fcitx5-chinese-addons
            "fcitx-chttrans-active" -> R.drawable.ic_fcitx_status_chttrans_trad
            "fcitx-chttrans-inactive" -> R.drawable.ic_fcitx_status_chttrans_simp
            "fcitx-punc-active" -> R.drawable.ic_fcitx_status_punc_active
            "fcitx-punc-inactive" -> R.drawable.ic_fcitx_status_punc_inactive
            "fcitx-fullwidth-active" -> R.drawable.ic_fcitx_status_fullwidth_active
            "fcitx-fullwidth-inactive" -> R.drawable.ic_fcitx_status_fullwidth_inactive
            "fcitx-remind-active" -> R.drawable.ic_fcitx_status_prediction_active
            "fcitx-remind-inactive" -> R.drawable.ic_fcitx_status_prediction_inactive
            // fcitx5-unikey
            "document-edit" -> R.drawable.ic_baseline_edit_24
            "character-set" -> R.drawable.ic_baseline_text_format_24
            "edit-find" -> R.drawable.ic_baseline_search_24
            // fallback
            "" -> 0
            else -> {
                if (icon.endsWith("-inactive")) {
                    R.drawable.ic_baseline_code_off_24
                } else {
                    R.drawable.ic_baseline_code_24
                }
            }
        }

        fun labelForAction(context: Context, action: Action): String {
            if (action.isRimeSchemaMenuAction()) return context.getString(R.string.rime_status_menu)
            val directLabel = action.shortText.trim().ifEmpty { action.longText.trim() }
            if (directLabel.isNotEmpty()) return directLabel
            return action.name.trim().ifEmpty { context.getString(R.string.status_action) }
        }

        fun labelForActionMenuItem(action: Action): String? {
            return action.shortText.trim()
                .ifEmpty { action.longText.trim() }
                .ifEmpty { null }
        }

        fun titleForActionMenu(context: Context, action: Action): String {
            if (action.isRimeSchemaMenuAction()) return context.getString(R.string.rime_status_menu)
            return labelForAction(context, action)
        }

        fun fromAction(context: Context, it: Action): Fcitx {
            val active = it.icon.endsWith("-active") || it.isChecked
            return Fcitx(it, labelForAction(context, it), drawableFromIconName(it.icon), active)
        }

        fun Action.isRimeSchemaMenuAction(): Boolean =
            name == "fcitx-rime-im"
    }
}
