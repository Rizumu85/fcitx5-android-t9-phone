/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.transition.Slide
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlag
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupActionListener
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import org.fcitx.fcitx5.android.input.wm.EssentialWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent

class KeyboardWindow : InputWindow.SimpleInputWindow<KeyboardWindow>(), EssentialWindow,
    InputBroadcastReceiver {

    private val service by manager.inputMethodService()
    private val fcitx by manager.fcitx()
    private val theme by manager.theme()
    private val commonKeyActionListener: CommonKeyActionListener by manager.must()
    private val windowManager: InputWindowManager by manager.must()
    private val popup: PopupComponent by manager.must()
    private val bar: KawaiiBarComponent by manager.must()
    private val returnKeyDrawable: ReturnKeyDrawableComponent by manager.must()

    companion object : EssentialWindow.Key {
        private const val PasswordInputMethod = "keyboard-us"
    }

    override val key: EssentialWindow.Key
        get() = KeyboardWindow

    override fun enterAnimation(lastWindow: InputWindow) = Slide().apply {
        slideEdge = Gravity.BOTTOM
    }.takeIf {
        // disable animation switching between picker
        lastWindow !is PickerWindow
    }

    override fun exitAnimation(nextWindow: InputWindow) =
        super.exitAnimation(nextWindow).takeIf {
            // disable animation switching between picker
            nextWindow !is PickerWindow
        }

    private lateinit var rootView: FrameLayout
    private lateinit var keyboardView: FrameLayout

    private val keyboards = hashMapOf<String, BaseKeyboard>()
    private var currentKeyboardName = ""
    private var lastSymbolType: String by AppPrefs.getInstance().internal.lastSymbolLayout

    var onLayoutChanged: ((String) -> Unit)? = null
    var onHeightChanged: (() -> Unit)? = null

    private val currentKeyboard: BaseKeyboard? get() = keyboards[currentKeyboardName]

    private fun supportsLayout(name: String): Boolean = when (name) {
        TextKeyboard.Name,
        NumberKeyboard.Name,
        TemporaryFullKeyboard.Name,
        T9Keyboard.Name -> true
        else -> false
    }

    private fun keyboardFor(name: String): BaseKeyboard? {
        if (!supportsLayout(name)) return null
        return keyboards.getOrPut(name) {
            // Inactive layouts are expensive View trees; the current layout is the only one that
            // belongs on the first input-surface critical path.
            when (name) {
                TextKeyboard.Name -> TextKeyboard(context, theme)
                NumberKeyboard.Name -> NumberKeyboard(context, theme)
                TemporaryFullKeyboard.Name -> TemporaryFullKeyboard(context, theme)
                T9Keyboard.Name -> T9Keyboard(context, theme)
                else -> error("Unsupported keyboard layout $name")
            }
        }
    }

    private val keyActionListener = KeyActionListener { it, source ->
        when (it) {
            is KeyAction.LayoutSwitchAction -> {
                if (it.act == TemporaryFullKeyboard.ExitTarget) {
                    disableTemporaryTextKeyboardForCurrentSession()
                } else {
                    switchLayout(it.act)
                }
            }
            is KeyAction.PasswordPeekAction -> setPasswordPeekMode(it.enabled)
            else -> commonKeyActionListener.listener.onKeyAction(it, source)
        }
    }

    private val popupActionListener: PopupActionListener by lazy {
        popup.listener
    }

    // This will be called EXACTLY ONCE
    override fun onCreateView(): View {
        rootView = context.frameLayout()
        keyboardView = context.frameLayout(R.id.keyboard_view)
        rootView.apply {
            add(keyboardView, lParams(matchParent, matchParent))
        }
        val initialLayout = if (useT9KeyboardLayout) T9Keyboard.Name else TextKeyboard.Name
        attachLayout(initialLayout)
        updatePasswordPeekViews()
        return rootView
    }

    private fun detachCurrentLayout() {
        currentKeyboard?.also {
            it.onDetach()
            keyboardView.removeView(it)
            it.keyActionListener = null
            it.popupActionListener = null
        }
    }

    private fun attachLayout(target: String) {
        currentKeyboardName = target
        if (target != TemporaryFullKeyboard.Name) {
            setPasswordPeekMode(false)
        }
        keyboardFor(target)?.let {
            it.keyActionListener = keyActionListener
            it.popupActionListener = popupActionListener
            keyboardView.apply { add(it, lParams(matchParent, matchParent)) }
            it.onAttach()
            it.onReturnDrawableUpdate(returnKeyDrawable.resourceId)
            val pendingInputMethod = pendingInputMethodDisplayAfterTemporary
            val cachedInputMethod = fcitx.cachedState.inputMethodEntry
            val displayInputMethod = pendingInputMethod
                ?: displayInputMethodForLayout(cachedInputMethod, target)?.takeUnless {
                    suppressPasswordInputMethodDisplayAfterTemporary &&
                        it.uniqueName == PasswordInputMethod &&
                        target != TemporaryFullKeyboard.Name
                }
            displayInputMethod?.let(it::onInputMethodUpdate)
            if (pendingInputMethod != null && target != TemporaryFullKeyboard.Name) {
                pendingInputMethodDisplayAttached = true
            }
            (it as? T9Keyboard)?.updateT9ModeLabel(service.getCurrentT9ModeLabel())
        }
        onLayoutChanged?.invoke(target)
    }

    private var passwordPeekMode = false

    private fun updatePasswordPeekViews() {
        if (!::keyboardView.isInitialized) return
        keyboardView.visibility = View.VISIBLE
        (keyboards[TemporaryFullKeyboard.Name] as? TemporaryFullKeyboard)?.setPeekMode(passwordPeekMode)
    }

    private fun setPasswordPeekMode(enabled: Boolean) {
        val newValue = enabled &&
            temporaryTextKeyboard &&
            currentKeyboardName == TemporaryFullKeyboard.Name
        if (passwordPeekMode == newValue) return
        passwordPeekMode = newValue
        updatePasswordPeekViews()
        onHeightChanged?.invoke()
    }

    fun isPasswordPeekMode(): Boolean = passwordPeekMode

    fun switchLayout(
        to: String,
        remember: Boolean = true,
        allowTextKeyboardInT9Mode: Boolean = false
    ) {
        val defaultMainKeyboard = if (useT9KeyboardLayout) T9Keyboard.Name else TextKeyboard.Name
        val requestedTarget = when {
            to.isNotEmpty() -> to
            supportsLayout(lastSymbolType) -> lastSymbolType
            else -> defaultMainKeyboard
        }
        val target = if (temporaryTextKeyboard && requestedTarget == TextKeyboard.Name) {
            TemporaryFullKeyboard.Name
        } else {
            requestedTarget
        }
        val leavingTemporaryTextKeyboard =
            temporaryTextKeyboard &&
                supportsLayout(target) &&
                target != TemporaryFullKeyboard.Name &&
                target != NumberKeyboard.Name
        if (leavingTemporaryTextKeyboard) {
            setPasswordPeekMode(false)
            service.clearPasswordInputPreview()
            temporaryTextKeyboard = false
            temporaryTextKeyboardSource = null
            bar.onPasswordModeExited()
            restoreInputMethodBeforeTemporary()
        }
        val resolvedTarget = if (target == TextKeyboard.Name && useT9KeyboardLayout && !allowTextKeyboardInT9Mode) {
            T9Keyboard.Name
        } else {
            target
        }
        ContextCompat.getMainExecutor(service).execute {
            if (supportsLayout(resolvedTarget)) {
                if (remember && resolvedTarget != TextKeyboard.Name && resolvedTarget != T9Keyboard.Name) {
                    lastSymbolType = resolvedTarget
                }
                if (resolvedTarget == currentKeyboardName) return@execute
                detachCurrentLayout()
                attachLayout(resolvedTarget)
                if (windowManager.isAttached(this)) {
                    notifyBarLayoutChanged()
                }
            } else {
                if (remember) {
                    lastSymbolType = PickerWindow.Key.Symbol.name
                }
                windowManager.attachWindow(PickerWindow.Key.Symbol)
            }
        }
    }

    private val useT9KeyboardLayout by AppPrefs.getInstance().keyboard.useT9KeyboardLayout
    private enum class TemporaryTextKeyboardSource {
        Manual,
        AutomaticPassword
    }

    private var temporaryTextKeyboard = false
    private var temporaryTextKeyboardSource: TemporaryTextKeyboardSource? = null
    private var suppressAutomaticPasswordKeyboardForSession = false
    private var imeBeforeTemporaryTextKeyboard: String? = null
    private var imeEntryBeforeTemporaryTextKeyboard: InputMethodEntry? = null
    private var pendingInputMethodDisplayAfterTemporary: InputMethodEntry? = null
    private var pendingInputMethodDisplayAttached = false
    private var suppressPasswordInputMethodDisplayAfterTemporary = false
    private var lastNonPasswordInputMethod: String? = null
    private var lastNonPasswordInputMethodEntry: InputMethodEntry? = null
    private var currentCapabilityFlags = CapabilityFlags.DefaultFlags
    private var capabilityFlagsBeforeTemporaryTextKeyboard: CapabilityFlags? = null

    private fun CapabilityFlags.withoutPassword() =
        CapabilityFlags(flags and CapabilityFlag.Password.flag.inv())

    private fun rememberNonPasswordInputMethod(ime: InputMethodEntry?) {
        if (ime == null || ime.uniqueName == PasswordInputMethod) return
        lastNonPasswordInputMethod = ime.uniqueName
        lastNonPasswordInputMethodEntry = ime
    }

    private fun firstEnabledNonPasswordInputMethodNow(): InputMethodEntry? {
        return runCatching {
            fcitx.runImmediately {
                enabledIme().firstOrNull { it.uniqueName != PasswordInputMethod }
            }
        }.getOrNull()
    }

    private fun displayInputMethodForLayout(
        ime: InputMethodEntry,
        targetLayout: String
    ): InputMethodEntry? {
        if (targetLayout == TemporaryFullKeyboard.Name || ime.uniqueName != PasswordInputMethod) {
            return ime
        }
        val enabledInputMethods = runCatching {
            fcitx.runImmediately {
                enabledIme().toList()
            }
        }.getOrNull()
        if (enabledInputMethods?.any { it.uniqueName == PasswordInputMethod } == true) {
            return ime
        }
        return lastNonPasswordInputMethodEntry
            ?: enabledInputMethods?.firstOrNull { it.uniqueName != PasswordInputMethod }
    }

    fun isTemporaryTextKeyboardEnabled(): Boolean = temporaryTextKeyboard

    fun isTemporaryPasswordInputSessionActive(): Boolean = temporaryTextKeyboard

    fun isTemporaryPasswordKeyboardVisible(): Boolean {
        return temporaryTextKeyboard &&
            currentKeyboardName == TemporaryFullKeyboard.Name &&
            windowManager.isAttached(this)
    }

    fun shouldKeepTemporaryPasswordModeOnRestart(capFlags: CapabilityFlags): Boolean {
        return isTemporaryPasswordKeyboardVisible() &&
            (temporaryTextKeyboardSource == TemporaryTextKeyboardSource.Manual ||
                capFlags.has(CapabilityFlag.Password))
    }

    fun toggleTemporaryTextKeyboard(): Boolean {
        val enabling = !temporaryTextKeyboard
        if (!enabling) {
            disableTemporaryTextKeyboardForCurrentSession()
            return temporaryTextKeyboard
        } else {
            suppressAutomaticPasswordKeyboardForSession = false
        }
        setTemporaryTextKeyboard(
            enabling,
            source = if (enabling) TemporaryTextKeyboardSource.Manual else null
        )
        return temporaryTextKeyboard
    }

    private fun disableTemporaryTextKeyboardForCurrentSession() {
        val suppressAutomaticPasswordKeyboard = currentCapabilityFlags.has(CapabilityFlag.Password)
        if (suppressAutomaticPasswordKeyboard) {
            suppressAutomaticPasswordKeyboardForSession = true
        }
        setTemporaryTextKeyboard(
            false,
            restoreCapabilityFlags = if (suppressAutomaticPasswordKeyboard) {
                currentCapabilityFlags.withoutPassword()
            } else {
                null
            }
        )
        if (suppressAutomaticPasswordKeyboard) {
            bar.onPasswordModeManuallyDisabled()
        }
    }

    private fun setTemporaryTextKeyboard(
        enabled: Boolean,
        source: TemporaryTextKeyboardSource? = null,
        restoreCapabilityFlags: CapabilityFlags? = null
    ) {
        val wasEnabled = temporaryTextKeyboard
        temporaryTextKeyboard = enabled
        temporaryTextKeyboardSource = source.takeIf { enabled }
        if (enabled && !wasEnabled) {
            pendingInputMethodDisplayAfterTemporary = null
            pendingInputMethodDisplayAttached = false
            suppressPasswordInputMethodDisplayAfterTemporary = false
            setPasswordPeekMode(false)
            activatePasswordInputMethod()
        } else if (!enabled && wasEnabled) {
            setPasswordPeekMode(false)
            service.clearPasswordInputPreview()
            bar.onPasswordModeExited()
            restoreInputMethodBeforeTemporary(restoreCapabilityFlags)
        }
        val targetLayout = if (enabled) {
            TemporaryFullKeyboard.Name
        } else if (useT9KeyboardLayout) {
            T9Keyboard.Name
        } else {
            TextKeyboard.Name
        }
        switchLayout(
            targetLayout,
            remember = false
        )
    }

    private fun activatePasswordInputMethod() {
        val capabilityFlagsBeforeTemporary = currentCapabilityFlags
        capabilityFlagsBeforeTemporaryTextKeyboard = capabilityFlagsBeforeTemporary
        service.postFcitxJob {
            val currentIme = inputMethodEntryCached
            val previousIme = if (currentIme.uniqueName != PasswordInputMethod) {
                currentIme
            } else {
                lastNonPasswordInputMethodEntry
                    ?: enabledIme().firstOrNull { it.uniqueName != PasswordInputMethod }
            }?.takeIf { it.uniqueName != PasswordInputMethod }
            rememberNonPasswordInputMethod(previousIme)
            imeEntryBeforeTemporaryTextKeyboard = previousIme
            imeBeforeTemporaryTextKeyboard = previousIme?.uniqueName
            setCapFlags(
                CapabilityFlags(
                    capabilityFlagsBeforeTemporary.flags or CapabilityFlag.Password.flag
                )
            )
            activateIme(PasswordInputMethod)
        }
    }

    private fun ensurePasswordInputMethod() {
        val capabilityFlagsBeforeTemporary =
            capabilityFlagsBeforeTemporaryTextKeyboard ?: currentCapabilityFlags
        service.postFcitxJob {
            setCapFlags(
                CapabilityFlags(
                    capabilityFlagsBeforeTemporary.flags or CapabilityFlag.Password.flag
                )
            )
            if (inputMethodEntryCached.uniqueName != PasswordInputMethod) {
                activateIme(PasswordInputMethod)
            }
        }
    }

    private fun restoreInputMethodBeforeTemporary(restoreCapabilityFlags: CapabilityFlags? = null) {
        val previousCapabilityFlags =
            restoreCapabilityFlags ?: capabilityFlagsBeforeTemporaryTextKeyboard
        capabilityFlagsBeforeTemporaryTextKeyboard = null
        previousCapabilityFlags?.let {
            currentCapabilityFlags = it
        }
        val previousIme = imeBeforeTemporaryTextKeyboard
        val previousImeEntry = imeEntryBeforeTemporaryTextKeyboard
            ?: lastNonPasswordInputMethodEntry
            ?: firstEnabledNonPasswordInputMethodNow()
        rememberNonPasswordInputMethod(previousImeEntry)
        imeBeforeTemporaryTextKeyboard = null
        imeEntryBeforeTemporaryTextKeyboard = null
        pendingInputMethodDisplayAfterTemporary = previousImeEntry
        pendingInputMethodDisplayAttached = false
        suppressPasswordInputMethodDisplayAfterTemporary = true
        service.postFcitxJob {
            previousCapabilityFlags?.let {
                setCapFlags(it)
            }
            if (previousIme != null &&
                inputMethodEntryCached.uniqueName == PasswordInputMethod
            ) {
                activateIme(previousIme)
            }
        }
    }

    private fun keepPasswordModeManuallyDisabled(capFlags: CapabilityFlags) {
        val effectiveCapFlags = capFlags.withoutPassword()
        currentCapabilityFlags = effectiveCapFlags
        bar.onPasswordModeManuallyDisabled()
        service.postFcitxJob {
            setCapFlags(effectiveCapFlags)
            if (inputMethodEntryCached.uniqueName != PasswordInputMethod) return@postFcitxJob
            val restoreIme = lastNonPasswordInputMethod?.takeIf { it != PasswordInputMethod }
            if (restoreIme != null) {
                activateIme(restoreIme)
                return@postFcitxJob
            }
            val enabledInputMethods = enabledIme()
            enabledInputMethods.firstOrNull { it.uniqueName != PasswordInputMethod }?.let {
                lastNonPasswordInputMethod = it.uniqueName
                lastNonPasswordInputMethodEntry = it
                pendingInputMethodDisplayAfterTemporary = it
                pendingInputMethodDisplayAttached = false
                suppressPasswordInputMethodDisplayAfterTemporary = true
                activateIme(it.uniqueName)
            }
        }
    }

    private fun restoreLeakedPasswordInputMethod(capFlags: CapabilityFlags) {
        if (capFlags.has(CapabilityFlag.Password)) return
        restorePasswordInputMethodAfterTemporary(null, capFlags)
    }

    private fun restorePasswordInputMethodAfterTemporary(
        previousIme: String?,
        capFlags: CapabilityFlags
    ) {
        service.postFcitxJob {
            if (inputMethodEntryCached.uniqueName != PasswordInputMethod) return@postFcitxJob
            setCapFlags(capFlags)
            val restoreIme = previousIme
                ?.takeIf { it != PasswordInputMethod }
                ?: lastNonPasswordInputMethod?.takeIf { it != PasswordInputMethod }
            if (restoreIme != null) {
                pendingInputMethodDisplayAfterTemporary = lastNonPasswordInputMethodEntry
                    ?.takeIf { it.uniqueName == restoreIme }
                pendingInputMethodDisplayAttached = false
                suppressPasswordInputMethodDisplayAfterTemporary = true
                activateIme(restoreIme)
                return@postFcitxJob
            }
            val enabledInputMethods = enabledIme()
            enabledInputMethods.firstOrNull { it.uniqueName != PasswordInputMethod }?.let {
                lastNonPasswordInputMethod = it.uniqueName
                lastNonPasswordInputMethodEntry = it
                pendingInputMethodDisplayAfterTemporary = it
                pendingInputMethodDisplayAttached = false
                suppressPasswordInputMethodDisplayAfterTemporary = true
                activateIme(it.uniqueName)
            }
        }
    }

    override fun onStartInput(info: EditorInfo, capFlags: CapabilityFlags, restarting: Boolean) {
        currentCapabilityFlags = capFlags
        if (!restarting) {
            suppressAutomaticPasswordKeyboardForSession = false
        }
        if (temporaryTextKeyboard) {
            if (restarting && shouldKeepTemporaryPasswordModeOnRestart(capFlags)) {
                ensurePasswordInputMethod()
                if (windowManager.isAttached(this)) {
                    notifyBarLayoutChanged()
                }
                return
            } else {
                val previousIme = imeBeforeTemporaryTextKeyboard
                imeBeforeTemporaryTextKeyboard = null
                imeEntryBeforeTemporaryTextKeyboard = null
                capabilityFlagsBeforeTemporaryTextKeyboard = null
                setPasswordPeekMode(false)
                service.clearPasswordInputPreview()
                temporaryTextKeyboard = false
                temporaryTextKeyboardSource = null
                bar.onPasswordModeExited()
                if (!capFlags.has(CapabilityFlag.Password)) {
                    restorePasswordInputMethodAfterTemporary(previousIme, capFlags)
                }
            }
        } else {
            temporaryTextKeyboardSource = null
            imeBeforeTemporaryTextKeyboard = null
            imeEntryBeforeTemporaryTextKeyboard = null
            capabilityFlagsBeforeTemporaryTextKeyboard = null
            restoreLeakedPasswordInputMethod(capFlags)
        }
        if (capFlags.has(CapabilityFlag.Password) && suppressAutomaticPasswordKeyboardForSession) {
            keepPasswordModeManuallyDisabled(capFlags)
        }
        if (capFlags.has(CapabilityFlag.Password) && !suppressAutomaticPasswordKeyboardForSession) {
            setTemporaryTextKeyboard(
                true,
                source = TemporaryTextKeyboardSource.AutomaticPassword
            )
            return
        }
        val targetLayout = when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER -> NumberKeyboard.Name
            InputType.TYPE_CLASS_PHONE -> NumberKeyboard.Name
            else -> if (useT9KeyboardLayout) {
                T9Keyboard.Name
            } else {
                TextKeyboard.Name
            }
        }
        switchLayout(targetLayout, remember = false)
    }

    override fun onImeUpdate(ime: InputMethodEntry) {
        if (!temporaryTextKeyboard && !currentCapabilityFlags.has(CapabilityFlag.Password)) {
            rememberNonPasswordInputMethod(ime)
        }
        pendingInputMethodDisplayAfterTemporary?.let {
            if (ime.uniqueName == PasswordInputMethod) {
                return
            }
            if (currentKeyboardName != TemporaryFullKeyboard.Name && pendingInputMethodDisplayAttached) {
                pendingInputMethodDisplayAfterTemporary = null
                pendingInputMethodDisplayAttached = false
            }
        }
        if (suppressPasswordInputMethodDisplayAfterTemporary) {
            if (ime.uniqueName == PasswordInputMethod) {
                return
            }
            suppressPasswordInputMethodDisplayAfterTemporary = false
        }
        if (isTemporaryPasswordKeyboardVisible() && ime.uniqueName != PasswordInputMethod) {
            ensurePasswordInputMethod()
        }
        val displayIme = displayInputMethodForLayout(ime, currentKeyboardName)
        displayIme?.let { currentKeyboard?.onInputMethodUpdate(it) }
        (currentKeyboard as? T9Keyboard)?.updateT9ModeLabel(service.getCurrentT9ModeLabel())
    }

    override fun onT9ModeUpdate(modeLabel: String) {
        (currentKeyboard as? T9Keyboard)?.updateT9ModeLabel(modeLabel)
    }

    override fun onPunctuationUpdate(mapping: Map<String, String>) {
        currentKeyboard?.onPunctuationUpdate(mapping)
    }

    override fun onReturnKeyDrawableUpdate(resourceId: Int) {
        currentKeyboard?.onReturnDrawableUpdate(resourceId)
    }

    override fun onAttached() {
        currentKeyboard?.let {
            it.keyActionListener = keyActionListener
            it.popupActionListener = popupActionListener
            it.onAttach()
        }
        updatePasswordPeekViews()
        onLayoutChanged?.invoke(currentKeyboardName)
        notifyBarLayoutChanged()
    }

    fun isCurrentLayoutT9(): Boolean = currentKeyboardName == T9Keyboard.Name

    override fun onDetached() {
        currentKeyboard?.let {
            it.onDetach()
            it.keyActionListener = null
            it.popupActionListener = null
        }
        popup.dismissAll()
    }

    // Call this when
    // 1) the keyboard window was newly attached
    // 2) currently keyboard window is attached and switchLayout was used
    private fun notifyBarLayoutChanged() {
        bar.onKeyboardLayoutSwitched(
            isNumber = currentKeyboardName == NumberKeyboard.Name,
            isPassword = currentKeyboardName == TemporaryFullKeyboard.Name
        )
    }

}
