/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.wm

import android.view.View
import android.widget.FrameLayout
import androidx.transition.Transition
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcaster
import org.fcitx.fcitx5.android.input.dependency.UniqueViewComponent
import org.fcitx.fcitx5.android.input.dependency.context
import org.mechdancer.dependency.DynamicScope
import org.mechdancer.dependency.manager.must
import org.mechdancer.dependency.minusAssign
import org.mechdancer.dependency.plusAssign
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import timber.log.Timber

class InputWindowManager : UniqueViewComponent<InputWindowManager, FrameLayout>(),
    InputBroadcastReceiver {

    private val context by manager.context()
    private val broadcaster: InputBroadcaster by manager.must()
    private lateinit var scope: DynamicScope

    private val essentialWindows = mutableMapOf<EssentialWindow.Key, Pair<InputWindow, View?>>()
    private val lazyEssentialWindows = mutableMapOf<EssentialWindow.Key, () -> InputWindow>()

    private var currentWindow: InputWindow? = null
    private var currentView: View? = null
    private val auxiliaryReturnSession = AuxiliaryInputReturnSession()

    /**
     * Invoked when the active window changes (after detach of old, after attach of new).
     * Parameters: (oldWindow, newWindow). Either may be null when no previous window.
     */
    var onActiveWindowChanged: ((InputWindow?, InputWindow) -> Unit)? = null

    private val disableAnimation by AppPrefs.getInstance().advanced.disableAnimation

    private fun prepareAnimation(
        exitAnimation: Transition?,
        enterAnimation: Transition?,
        remove: View,
        add: View
    ) {
        if (disableAnimation)
            return
        enterAnimation?.addTarget(add)
        exitAnimation?.addTarget(remove)
        TransitionManager.beginDelayedTransition(view, TransitionSet().apply {
            enterAnimation?.let { addTransition(it) }
            exitAnimation?.let { addTransition(it) }
            duration = 100
        })
    }

    /**
     * Associate essential window with its key and add it to scope
     * If [createView] is `true`, the view will be created immediately.
     * Otherwise, it will be created on first attach
     */
    fun <R> addEssentialWindow(
        window: R,
        createView: Boolean = false
    ) where R : InputWindow, R : EssentialWindow {
        essentialWindows[window.key]?.let { (existing) ->
            if (existing === window) {
                Timber.d("Skip adding essential window $window")
                return
            }
            throw IllegalStateException("${window.key} is already occupied")
        }
        if (window.key in lazyEssentialWindows) {
            throw IllegalStateException("${window.key} is already occupied")
        }
        scope += window
        val view = if (createView) window.onCreateView() else null
        essentialWindows[window.key] = window to view
    }

    fun <R> addLazyEssentialWindow(
        key: EssentialWindow.Key,
        create: () -> R
    ) where R : InputWindow, R : EssentialWindow {
        require(key !in essentialWindows && key !in lazyEssentialWindows) {
            "$key is already occupied"
        }
        lazyEssentialWindows[key] = create
    }

    fun getEssentialWindow(windowKey: EssentialWindow.Key) =
        materializeEssentialWindow(windowKey)?.first
            ?: throw IllegalArgumentException("Unable to find essential window associated with $windowKey")

    /**
     * Attach an essential window by key
     * IMPORTANT: the window key must be known,
     * i.e. the essential window should be added first via [addEssentialWindow].
     * Moreover, [attachWindow] can also add the essential window with key.
     */
    fun attachWindow(windowKey: EssentialWindow.Key) {
        materializeEssentialWindow(windowKey)?.let { (window, _) ->
            attachWindow(window)
        } ?: throw IllegalStateException("$windowKey is not a known essential window key")
    }

    fun beginAuxiliaryInput(returnTarget: EssentialWindow.Key) {
        auxiliaryReturnSession.begin(returnTarget)
    }

    fun hasAuxiliaryReturnTarget(): Boolean = auxiliaryReturnSession.hasTarget()

    fun returnFromAuxiliaryInput(): Boolean {
        val target = auxiliaryReturnSession.consume() ?: return false
        attachWindow(target)
        return true
    }

    fun clearAuxiliaryInputReturnTarget() {
        auxiliaryReturnSession.clear()
    }

    /**
     * Remove an essential window
     */
    fun removeEssentialWindow(windowKey: EssentialWindow.Key) {
        if (lazyEssentialWindows.remove(windowKey) != null) return
        val (window, _) = essentialWindows[windowKey]
            ?: throw IllegalStateException("$windowKey is not a known essential window key")
        if (currentWindow === window)
            throw IllegalStateException("$windowKey cannot be removed when it's active")
        // remove from scope
        scope -= window
        // remove from map
        essentialWindows.remove(windowKey)
    }

    private fun materializeEssentialWindow(
        key: EssentialWindow.Key
    ): Pair<InputWindow, View?>? {
        essentialWindows[key]?.let { return it }
        val create = lazyEssentialWindows.remove(key) ?: return null
        val window = create()
        require(window is EssentialWindow && window.key == key) {
            "Lazy essential window factory for $key returned $window"
        }
        scope += window
        return (window to null).also { essentialWindows[key] = it }
    }

    /**
     * Attach a new window, removing the old one
     * This function initialize the view for windows and save it for essential windows.
     * [attachWindow] includes the operation done by [addEssentialWindow].
     */
    fun attachWindow(window: InputWindow) {
        if (window === currentWindow)
            Timber.d("Skip attaching $window")
        val newView = if (window is EssentialWindow) {
            // keep the view for essential windows
            essentialWindows[window.key]?.second ?: window.onCreateView()
                .also { essentialWindows[window.key] = window to it }
        } else {
            // add the new window to scope, except essential windows (they are always in scope)
            scope += window
            window.onCreateView()
        }
        if (currentWindow != null) {
            val oldWindow = currentWindow!!
            val oldView = currentView!!
            prepareAnimation(
                oldWindow.exitAnimation(window),
                window.enterAnimation(oldWindow),
                oldView,
                newView
            )
            // notify the window that it will be detached
            oldWindow.onDetached()
            // remove the old window from layout
            view.removeView(oldView)
            // broadcast the old window was removed from layout
            broadcaster.onWindowDetached(oldWindow)
            Timber.d("Detach $oldWindow")
            // finally remove the old window from scope only if it's not an essential window,
            if (oldWindow !is EssentialWindow)
                scope -= oldWindow
        }
        // call before attached for essential window
        if (window is EssentialWindow)
            window.beforeAttached()
        // add the new window to layout
        view.apply { add(newView, lParams(matchParent, matchParent)) }
        currentView = newView
        Timber.d("Attach $window")
        // notify the window it was attached
        window.onAttached()
        val oldWindow = currentWindow
        currentWindow = window
        onActiveWindowChanged?.invoke(oldWindow, window)
        // broadcast the new window was added to layout
        broadcaster.onWindowAttached(window)
    }

    override val view: FrameLayout by lazy { context.frameLayout(R.id.input_window) }

    override fun onScopeSetupFinished(scope: DynamicScope) {
        this.scope = scope
    }

    fun isAttached(window: InputWindow) = currentWindow === window
}
