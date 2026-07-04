/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.status

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.core.Action
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must

class StatusActionMenuWindow(
    private val menuTitle: String,
    private val actions: Array<Action>,
    private val activeMenuLabel: String? = null
) : InputWindow.ExtendedInputWindow<StatusActionMenuWindow>() {

    private val service: FcitxInputMethodService by manager.inputMethodService()
    private val fcitx: FcitxConnection by manager.fcitx()
    private val theme by manager.theme()
    private val windowManager: InputWindowManager by manager.must()

    private val ui by lazy {
        StatusActionMenuUi(context, theme, actions, activeMenuLabel) { action ->
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
        fcitx.launchOnReady { api ->
            api.activateAction(action.id)
            service.lifecycleScope.launch {
                windowManager.attachWindow(StatusAreaWindow())
            }
        }
    }

    override fun onAttached() {}

    override fun onDetached() {}
}
