/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.status

import android.os.Build
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.Action
import org.fcitx.fcitx5.android.core.SubtypeManager
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.editorinfo.EditorInfoWindow
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.InputMethod
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.Keyboard
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.ReloadConfig
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.SmartEnglishT9
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.TemporaryFullKeyboard
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Android.Type.ThemeList
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry.Companion.isRimeSchemeSwitchAction
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.fcitx.fcitx5.android.utils.AppUtil
import org.mechdancer.dependency.manager.must
import splitties.dimensions.dp
import splitties.views.backgroundColor
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.verticalLayout

class StatusAreaWindow : InputWindow.ExtendedInputWindow<StatusAreaWindow>(),
    InputBroadcastReceiver {

    private val service: FcitxInputMethodService by manager.inputMethodService()
    private val fcitx: FcitxConnection by manager.fcitx()
    private val theme by manager.theme()
    private val windowManager: InputWindowManager by manager.must()

    private val editorInfoInspector by AppPrefs.getInstance().internal.editorInfoInspector

    private var latestFcitxActions: Array<Action> = emptyArray()

    private val keyboardWindow: KeyboardWindow
        get() = windowManager.getEssentialWindow(KeyboardWindow) as KeyboardWindow

    private fun staticEntries() =
        arrayOf(
            StatusAreaEntry.Android(
                context.getString(R.string.status_area_theme),
                R.drawable.ic_baseline_palette_24,
                ThemeList
            ),
            StatusAreaEntry.Android(
                context.getString(R.string.reload_config),
                R.drawable.ic_baseline_sync_24,
                ReloadConfig
            ),
            StatusAreaEntry.Android(
                context.getString(R.string.virtual_keyboard),
                R.drawable.ic_baseline_keyboard_24,
                Keyboard
            ),
            StatusAreaEntry.Android(
                context.getString(R.string.temporary_full_keyboard),
                R.drawable.ic_baseline_keyboard_24,
                TemporaryFullKeyboard,
                keyboardWindow.isTemporaryTextKeyboardEnabled()
            ),
            StatusAreaEntry.Android(
                context.getString(R.string.smart_english_t9),
                R.drawable.ic_baseline_auto_awesome_24,
                SmartEnglishT9,
                service.isSmartEnglishT9Enabled()
            ),
            StatusAreaEntry.Android(
                context.getString(R.string.input_method_options),
                R.drawable.ic_baseline_language_24,
                InputMethod
            )
        )

    private fun refreshEntries(actions: Array<Action> = latestFcitxActions) {
        latestFcitxActions = actions
        val visibleActions = actions
            .filterNot { shouldHideStatusAction(it) }
            .map { StatusAreaEntry.fromAction(context, it) }
        renderEntries(arrangeHorizontalColumns(staticEntries().toList(), visibleActions))
    }

    private fun arrangeHorizontalColumns(
        staticEntries: List<StatusAreaEntry.Android>,
        fcitxEntries: List<StatusAreaEntry.Fcitx>
    ): List<List<StatusAreaEntry>> {
        val firstPageStaticEntries = staticEntries.take(5)
        val firstPageActions = fcitxEntries.take(firstPageStaticEntries.size)
        val firstPageColumns = firstPageStaticEntries.mapIndexed { index, staticEntry ->
            buildList {
                add(staticEntry)
                firstPageActions.getOrNull(index)?.let(::add)
            }
        }
        val extraEntries = staticEntries.drop(5) + fcitxEntries.drop(firstPageStaticEntries.size)
        return firstPageColumns + extraEntries.chunked(2)
    }

    private fun shouldHideStatusAction(action: Action): Boolean {
        val shortText = action.shortText.filterNot { it.isWhitespace() }
        return shortText.contains("$") && (shortText.contains("¥") || shortText.contains("￥"))
    }

    private fun activateAction(action: Action) {
        service.activateStatusAction(action)
    }

    private fun onItemClick(entry: StatusAreaEntry) {
        when (entry) {
            is StatusAreaEntry.Fcitx -> {
                val actions = entry.action.menu
                if (actions.isNullOrEmpty()) {
                    activateAction(entry.action)
                    return
                }
                windowManager.attachWindow(
                    StatusActionMenuWindow(
                        StatusAreaEntry.titleForActionMenu(context, entry.action),
                        actions,
                        StatusAreaEntry.activeMenuLabelForAction(entry.action),
                        entry.action.isRimeSchemeSwitchAction()
                    )
                )
            }
            is StatusAreaEntry.Android -> when (entry.type) {
                InputMethod -> fcitx.cachedState.inputMethodEntry.let {
                    AppUtil.launchMainToInputMethodConfig(
                        context, it.uniqueName, it.displayName
                    )
                }
                ReloadConfig -> fcitx.launchOnReady { f ->
                    f.reloadConfig()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        SubtypeManager.syncWith(f.enabledIme())
                    }
                    service.lifecycleScope.launch {
                        Toast.makeText(service, R.string.done, Toast.LENGTH_SHORT).show()
                    }
                }
                Keyboard -> AppUtil.launchMainToKeyboard(context)
                ThemeList -> AppUtil.launchMainToThemeList(context)
                TemporaryFullKeyboard -> {
                    keyboardWindow.toggleTemporaryTextKeyboard()
                    refreshEntries()
                    windowManager.attachWindow(KeyboardWindow)
                }
                SmartEnglishT9 -> {
                    service.toggleSmartEnglishT9()
                    refreshEntries()
                    windowManager.attachWindow(KeyboardWindow)
                }
            }
        }
    }

    private val keyBorder by ThemeManager.prefs.keyBorder

    private val entriesContainer by lazy {
        context.horizontalLayout {
            clipChildren = false
            clipToPadding = false
        }
    }

    val view by lazy {
        context.view(::HorizontalScrollView) {
            if (!keyBorder) {
                backgroundColor = theme.barColor
            }
            setPadding(dp(12), 0, dp(12), 0)
            clipToPadding = false
            isFillViewport = false
            isHorizontalScrollBarEnabled = false
            addView(
                entriesContainer,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun renderEntries(columns: List<List<StatusAreaEntry>>) {
        entriesContainer.removeAllViews()
        columns.forEach { columnEntries ->
            val column = context.verticalLayout {
                columnEntries.forEach { entry ->
                    val ui = StatusAreaEntryUi(context, theme)
                    ui.setEntry(entry)
                    ui.root.setOnClickListener { onItemClick(entry) }
                    add(ui.root, lParams(matchParent, dp(108)))
                }
            }
            entriesContainer.addView(
                column,
                LinearLayout.LayoutParams(context.dp(66), ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
    }

    override fun onStatusAreaUpdate(actions: Array<Action>) {
        refreshEntries(actions)
    }

    override fun onCreateView() = view.also {
        // The cached status snapshot is maintained by Fcitx events. Rendering it before attach
        // keeps local quick actions usable even while the engine queue is busy.
        refreshEntries(fcitx.cachedState.statusAreaActions)
    }

    private val editorInfoButton by lazy {
        ToolButton(context, R.drawable.ic_baseline_info_24, theme).apply {
            contentDescription = context.getString(R.string.editor_info_inspector)
            setOnClickListener { windowManager.attachWindow(EditorInfoWindow()) }
        }
    }

    private val settingsButton by lazy {
        ToolButton(context, R.drawable.ic_baseline_settings_24, theme).apply {
            contentDescription = context.getString(R.string.open_input_method_settings)
            setOnClickListener { AppUtil.launchMain(context) }
        }
    }

    private val barExtension by lazy {
        context.horizontalLayout {
            if (editorInfoInspector) {
                add(editorInfoButton, lParams(dp(40), dp(40)))
            }
            add(settingsButton, lParams(dp(40), dp(40)))
        }
    }

    override fun onCreateBarExtension() = barExtension

    override fun onAttached() {}

    override fun onDetached() {}
}
