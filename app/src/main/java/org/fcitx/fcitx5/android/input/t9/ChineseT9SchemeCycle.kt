/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.t9

import org.fcitx.fcitx5.android.core.Action

object ChineseT9SchemeCycle {
    fun next(
        current: ChineseT9Scheme,
        enabled: Collection<ChineseT9Scheme>
    ): ChineseT9Scheme? {
        val cycle = ChineseT9Scheme.entries.filter(enabled.toSet()::contains)
            .ifEmpty { listOf(ChineseT9Scheme.PINYIN) }
        val currentIndex = cycle.indexOf(current)
        if (currentIndex < 0) return cycle.first()
        if (cycle.size < 2) return null
        return cycle[(currentIndex + 1) % cycle.size]
    }

    fun findAction(
        actions: Array<Action>,
        target: ChineseT9Scheme
    ): Action? = actions
        .firstOrNull { action -> action.name == RimeSchemeActionName }
        ?.menu
        ?.firstOrNull { action ->
            target.matchesRimeIdentity(action.shortText) ||
                target.matchesRimeIdentity(action.longText)
        }

    private const val RimeSchemeActionName = "fcitx-rime-im"
}

class ChineseT9SchemeCycleSession {
    enum class ActivationPresentation {
        SHOW_CONFIRMATION,
        KEEP_REQUEST_ACKNOWLEDGEMENT
    }

    private var requested: ChineseT9Scheme? = null

    fun requestNext(
        active: ChineseT9Scheme,
        enabled: Collection<ChineseT9Scheme>
    ): ChineseT9Scheme? {
        val target = ChineseT9SchemeCycle.next(requested ?: active, enabled) ?: return null
        requested = target
        return target
    }

    fun observeActive(scheme: ChineseT9Scheme): ActivationPresentation {
        val pending = requested ?: return ActivationPresentation.SHOW_CONFIRMATION
        if (pending == scheme) requested = null
        return ActivationPresentation.KEEP_REQUEST_ACKNOWLEDGEMENT
    }

    fun reject(target: ChineseT9Scheme) {
        if (requested == target) requested = null
    }
}
