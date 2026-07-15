/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.status

import android.content.Context
import org.fcitx.fcitx5.android.core.Action
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Companion.isRimeSchemeSwitchAction

class PersistentStatusActionCoordinator private constructor(
    private val store: Store
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(FileName, Context.MODE_PRIVATE).let { preferences ->
            object : Store {
                override fun get(name: String): String? = preferences.getString(valueKey(name), null)
                override fun put(name: String, value: String) {
                    preferences.edit().putString(valueKey(name), value).apply()
                }
            }
        }
    )

    internal constructor(values: MutableMap<String, String>) : this(
        object : Store {
            override fun get(name: String): String? = values[name]
            override fun put(name: String, value: String) {
                values[name] = value
            }
        }
    )

    private interface Store {
        fun get(name: String): String?
        fun put(name: String, value: String)
    }

    private val pendingCapture = mutableMapOf<String, String>()
    private val pendingRestore = mutableMapOf<String, String>()

    fun recordUserActivation(action: Action, fromSchemeMenu: Boolean): Boolean {
        if (!isPersistentToggle(action, fromSchemeMenu)) return false
        // Rime's T9 actions intentionally report isCheckable=false. Their arrow label describes
        // the next state, so persistence captures the status snapshot emitted after activation
        // instead of pretending the action follows Android checkbox semantics.
        pendingCapture[action.name] = fingerprint(action)
        pendingRestore.remove(action.name)
        return true
    }

    fun actionsNeedingRestore(actions: Array<Action>): List<Action> = buildList {
        actions.forEach { root -> collectMismatches(root, root.isRimeSchemeSwitchAction(), this) }
    }

    private fun collectMismatches(action: Action, fromSchemeMenu: Boolean, result: MutableList<Action>) {
        action.menu?.forEach { collectMismatches(it, fromSchemeMenu, result) }
        if (!isPersistentToggle(action, fromSchemeMenu)) return
        val current = fingerprint(action)
        pendingCapture[action.name]?.let { before ->
            if (before != current) {
                store.put(action.name, current)
                pendingCapture.remove(action.name)
            }
            return
        }
        val desired = store.get(action.name) ?: return
        if (current == desired) {
            pendingRestore.remove(action.name)
        } else if (pendingRestore[action.name] != desired) {
            pendingRestore[action.name] = desired
            result += action
        }
    }

    private fun isPersistentToggle(action: Action, fromSchemeMenu: Boolean): Boolean =
        !fromSchemeMenu && action.menu.isNullOrEmpty() && action.name.isNotBlank() &&
            (action.isCheckable || action.name.startsWith(RimeT9ActionPrefix))

    private fun fingerprint(action: Action): String = buildString {
        append(action.isChecked).append('|')
        append(action.icon).append('|')
        append(action.shortText).append('|')
        append(action.longText)
    }

    private companion object {
        const val FileName = "persistent_status_actions"
        const val ValuePrefix = "desired."
        const val RimeT9ActionPrefix = "fcitx-rime-t9-"
        fun valueKey(actionName: String): String = "$ValuePrefix$actionName"
    }
}
