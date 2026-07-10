/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.popup

import android.graphics.Paint
import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.broadcast.PunctuationComponent
import org.fcitx.fcitx5.android.input.dependency.ConcreteUniqueComponent
import org.fcitx.fcitx5.android.input.dependency.context
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import org.fcitx.fcitx5.android.input.keyboard.KeyDef
import org.mechdancer.dependency.Dependent
import org.mechdancer.dependency.manager.ManagedHandler
import org.mechdancer.dependency.manager.managedHandler
import org.mechdancer.dependency.manager.must
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import java.util.LinkedList
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PopupComponent :
    ConcreteUniqueComponent<PopupComponent>(), Dependent, ManagedHandler by managedHandler() {

    private val service by manager.inputMethodService()
    private val context by manager.context()
    private val theme by manager.theme()
    private val punctuation: PunctuationComponent by manager.must()

    private val showingEntryUi = HashMap<Int, PopupEntryUi>()
    private val dismissJobs = HashMap<Int, Job>()
    private val freeEntryUi = LinkedList<PopupEntryUi>()

    private val showingContainerUi = HashMap<Int, PopupContainerUi>()

    private val keyBottomMargin by lazy {
        context.dp(ThemeManager.prefs.keyVerticalMargin.getValue())
    }
    private val previewMinWidth by lazy {
        context.dp(38)
    }
    private val previewMaxWidth by lazy {
        context.dp(160)
    }
    private val previewHorizontalPadding by lazy {
        context.dp(8)
    }
    private val longPopupKeyWidth by lazy {
        context.dp(58)
    }
    private val popupHeight by lazy {
        context.dp(84)
    }
    private val popupKeyHeight by lazy {
        context.dp(42)
    }
    private val longPopupOffsetHeight by lazy {
        context.dp(116)
    }
    private val popupRadius by lazy {
        context.dp(ThemeManager.prefs.keyRadius.getValue()).toFloat()
    }
    private val previewTextPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG)
    }
    private val hideThreshold = 100L

    private val rootLocation = intArrayOf(0, 0)
    private val rootBounds: Rect = Rect()

    val root by lazy {
        context.frameLayout {
            // we want (0, 0) at top left
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            isClickable = false
            isFocusable = false

            addOnLayoutChangeListener { v, left, top, right, bottom, _, _, _, _ ->
                val (x, y) = rootLocation.also { v.getLocationInWindow(it) }
                val width = right - left
                val height = bottom - top
                rootBounds.set(x, y, x + width, y + height)
            }
        }
    }

    private fun previewWidth(content: String, icon: Int?, textSize: Float?): Int {
        if (icon != null) return max(previewMinWidth, popupKeyHeight)
        val textWidth = if (content.isBlank()) {
            0
        } else {
            previewTextPaint.textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                textSize ?: 18f,
                context.resources.displayMetrics
            )
            previewTextPaint.measureText(content).roundToInt()
        }
        val maxWidth = max(
            previewMinWidth,
            if (root.width > 0) min(previewMaxWidth, root.width) else previewMaxWidth
        )
        return (textWidth + previewHorizontalPadding * 2).coerceIn(previewMinWidth, maxWidth)
    }

    private fun popupLeftMargin(bounds: Rect, width: Int): Int {
        val centered = (bounds.left + bounds.right - width) / 2
        val rootWidth = root.width
        if (rootWidth <= 0 || width >= rootWidth) return centered
        return centered.coerceIn(0, rootWidth - width)
    }

    private fun applyPopupLayout(popup: PopupEntryUi, bounds: Rect, width: Int) {
        popup.root.layoutParams = FrameLayout.LayoutParams(width, popupHeight).apply {
            // align popup bottom with key border bottom [^1]
            topMargin = bounds.bottom - popupHeight - keyBottomMargin
            leftMargin = popupLeftMargin(bounds, width)
        }
    }

    private fun showPopup(
        viewId: Int,
        content: String,
        bounds: Rect,
        icon: Int? = null,
        textSize: Float? = null
    ) {
        showingEntryUi[viewId]?.apply {
            dismissJobs[viewId]?.also {
                dismissJobs.remove(viewId)?.cancel()
            }
            lastShowTime = System.currentTimeMillis()
            setContent(content, icon, textSize)
            applyPopupLayout(this, bounds, previewWidth(content, icon, textSize))
            return
        }
        val popup = (freeEntryUi.poll()
            ?: PopupEntryUi(context, theme, popupKeyHeight, popupRadius)).apply {
            lastShowTime = System.currentTimeMillis()
            setContent(content, icon, textSize)
        }
        applyPopupLayout(popup, bounds, previewWidth(content, icon, textSize))
        // make sure that popup.root does not have parent view before adding it under root container
        // it's wired that on some devices it would have a parent view despite it was newly created
        // or just polled from freeEntryUi
        if (popup.root.parent == null) {
            root.addView(popup.root)
        } else if (popup.root.parent !== root) {
            (popup.root.parent as? ViewGroup)?.removeView(popup.root)
            root.addView(popup.root)
        }
        showingEntryUi[viewId] = popup
    }

    private fun updatePopup(
        viewId: Int,
        content: String,
        icon: Int? = null,
        textSize: Float? = null
    ) {
        showingEntryUi[viewId]?.setContent(content, icon, textSize)
    }

    private fun showKeyboard(viewId: Int, keyboard: KeyDef.Popup.Keyboard, bounds: Rect) {
        val keys = keyboard.keys
            ?: PopupPreset[keyboard.label]
            ?: EmojiModifier.produceSkinTones(keyboard.label)
            ?: return
        showingEntryUi[viewId]?.let {
            dismissPopupEntry(viewId, it)
        }
        reallyShowKeyboard(viewId, keys, bounds)
    }

    private fun reallyShowKeyboard(viewId: Int, keys: Array<String>, bounds: Rect) {
        val labels = if (punctuation.enabled) {
            Array(keys.size) { punctuation.transform(keys[it]) }
        } else keys
        val keyboardUi = PopupKeyboardUi(
            context,
            theme,
            rootBounds,
            bounds,
            { dismissPopup(viewId) },
            popupRadius,
            longPopupKeyWidth,
            popupKeyHeight,
            // position popup keyboard higher, because of [^1]
            longPopupOffsetHeight + keyBottomMargin,
            keys,
            labels
        )
        showPopupContainer(viewId, keyboardUi)
    }

    private fun showMenu(viewId: Int, menu: KeyDef.Popup.Menu, bounds: Rect) {
        showingEntryUi[viewId]?.let {
            dismissPopupEntry(viewId, it)
        }
        val menuUi = PopupMenuUi(
            context,
            theme,
            rootBounds,
            bounds,
            { dismissPopup(viewId) },
            menu.items,
        )
        showPopupContainer(viewId, menuUi)
    }

    private fun showPopupContainer(viewId: Int, ui: PopupContainerUi) {
        root.apply {
            add(ui.root, lParams {
                leftMargin = ui.triggerBounds.left + ui.offsetX - rootBounds.left
                topMargin = ui.triggerBounds.top + ui.offsetY - rootBounds.top
            })
        }
        showingContainerUi[viewId] = ui
    }

    private fun changeFocus(viewId: Int, x: Float, y: Float): Boolean {
        return showingContainerUi[viewId]?.changeFocus(x, y) ?: false
    }

    private fun triggerFocused(viewId: Int): KeyAction? {
        return showingContainerUi[viewId]?.onTrigger()
    }

    private fun dismissPopup(viewId: Int) {
        dismissPopupContainer(viewId)
        showingEntryUi[viewId]?.also {
            val timeLeft = it.lastShowTime + hideThreshold - System.currentTimeMillis()
            if (timeLeft <= 0L) {
                dismissPopupEntry(viewId, it)
            } else {
                dismissJobs[viewId] = service.lifecycleScope.launch {
                    delay(timeLeft)
                    dismissPopupEntry(viewId, it)
                    dismissJobs.remove(viewId)
                }
            }
        }
    }

    private fun dismissPopupContainer(viewId: Int) {
        showingContainerUi[viewId]?.also {
            showingContainerUi.remove(viewId)
            root.removeView(it.root)
        }
    }

    private fun dismissPopupEntry(viewId: Int, popup: PopupEntryUi) {
        showingEntryUi.remove(viewId)
        root.removeView(popup.root)
        freeEntryUi.add(popup)
    }

    fun dismissAll() {
        // avoid modifying collection while iterating
        dismissJobs.forEach { (_, job) ->
            job.cancel()
        }
        dismissJobs.clear()
        // too
        showingContainerUi.forEach { (_, container) ->
            root.removeView(container.root)
        }
        showingContainerUi.clear()
        // too too
        showingEntryUi.forEach { (_, entry) ->
            root.removeView(entry.root)
            freeEntryUi.add(entry)
        }
        showingEntryUi.clear()
    }

    val listener = PopupActionListener { action ->
        with(action) {
            when (this) {
                is PopupAction.ChangeFocusAction -> outResult = changeFocus(viewId, x, y)
                is PopupAction.DismissAction -> dismissPopup(viewId)
                is PopupAction.PreviewAction -> showPopup(viewId, content, bounds, icon, textSize)
                is PopupAction.PreviewUpdateAction -> updatePopup(viewId, content, icon, textSize)
                is PopupAction.ShowKeyboardAction -> showKeyboard(viewId, keyboard, bounds)
                is PopupAction.ShowMenuAction -> showMenu(viewId, menu, bounds)
                is PopupAction.TriggerAction -> outAction = triggerFocused(viewId)
            }
        }
    }
}
