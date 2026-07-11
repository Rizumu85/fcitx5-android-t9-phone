/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2023 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.core

import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder
import androidx.annotation.RequiresApi
import org.fcitx.fcitx5.android.utils.InputMethodUtil
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.inputMethodManager

object SubtypeManager {

    private const val MODE_KEYBOARD = "keyboard"

    private val knownSubtypes: HashMap<String, InputMethodSubtype> = hashMapOf()

    fun subtypeOf(inputMethod: String): InputMethodSubtype? {
        return knownSubtypes[inputMethod]
    }

    fun inputMethodOf(subtype: InputMethodSubtype): String {
        return subtype.extraValue.ifEmpty { FirstRunInputMethodPolicy.RIME }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun syncWith(inputMethods: Array<InputMethodEntry>) {
        knownSubtypes.clear()
        // Android should expose the app as one Chinese keyboard. The US keyboard remains an
        // internal engine dependency for password fields and temporary full-keyboard sessions.
        val userVisibleInputMethods = inputMethods.filter {
            FirstRunInputMethodPolicy.isUserVisible(it.uniqueName)
        }
        val size = userVisibleInputMethods.size
        val subtypes = arrayOfNulls<InputMethodSubtype>(size)
        val hashCodes = IntArray(size)
        userVisibleInputMethods.forEachIndexed { i, im ->
            val subtype = InputMethodSubtypeBuilder()
                .setSubtypeId(im.uniqueName.hashCode())
                .setSubtypeExtraValue(im.uniqueName)
                .setSubtypeNameOverride(im.displayName)
                .setSubtypeMode(MODE_KEYBOARD)
                .setIsAsciiCapable(true)
                .build()
            val hashCode = subtype.hashCode()
            subtypes[i] = subtype
            hashCodes[i] = hashCode
            knownSubtypes[im.uniqueName] = subtype
        }
        val imm = appContext.inputMethodManager
        val imiId = InputMethodUtil.componentName
        // although this method has been marked as deprecated,
        // dynamic subtypes have to be "registered" before they can be "enabled"
        @Suppress("DEPRECATION")
        imm.setAdditionalInputMethodSubtypes(imiId, subtypes)
        imm.setExplicitlyEnabledInputMethodSubtypes(imiId, hashCodes)
    }
}
