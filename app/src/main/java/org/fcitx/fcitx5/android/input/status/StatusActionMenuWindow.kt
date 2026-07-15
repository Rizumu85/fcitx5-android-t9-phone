/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.status

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.core.Action
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must

class StatusActionMenuWindow(
    private val menuTitle: String,
    private val actions: Array<Action>,
    private val activeMenuLabel: String? = null,
    private val isRimeSchemeMenu: Boolean = false
) : InputWindow.ExtendedInputWindow<StatusActionMenuWindow>() {

    private val service: FcitxInputMethodService by manager.inputMethodService()
    private val theme by manager.theme()
    private val windowManager: InputWindowManager by manager.must()

    private val ui by lazy {
        StatusActionMenuUi(context, theme, actions, activeMenuLabel, isRimeSchemeMenu) { action ->
            activateAction(action)
        }
    }

    override val title: String
        get() = menuTitle

    override fun onCreateView() = ui.root

    override fun onTitleBackPressed(): Boolean {
        windowManager.attachWindow(StatusAreaWindow())
        return true
    }

    private fun activateAction(action: Action) {
        service.activateStatusAction(action, fromSchemeMenu = isRimeSchemeMenu)
        service.lifecycleScope.launch {
            windowManager.attachWindow(StatusAreaWindow())
        }
    }

    override fun onAttached() {}

    override fun onDetached() {}
}
