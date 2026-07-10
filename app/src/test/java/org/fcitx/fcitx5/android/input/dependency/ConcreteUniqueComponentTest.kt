/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.dependency

import android.view.View
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mechdancer.dependency.DynamicScope

class ConcreteUniqueComponentTest {
    private class FirstComponent : ConcreteUniqueComponent<FirstComponent>()
    private class SecondComponent : ConcreteUniqueComponent<SecondComponent>()
    private class TestWindow : InputWindow.SimpleInputWindow<TestWindow>() {
        override fun onCreateView(): View = error("View creation is outside this identity test")
        override fun onAttached() = Unit
        override fun onDetached() = Unit
    }

    @Test
    fun runtimeClassIsTheUniqueScopeIdentity() {
        assertEquals(FirstComponent::class, FirstComponent().type)
        assertEquals(SecondComponent::class, SecondComponent().type)
        assertEquals(TestWindow::class, TestWindow().type)
    }

    @Test
    fun dynamicScopeStillRejectsASecondComponentOfTheSameType() {
        val scope = DynamicScope()

        assertTrue(scope.setup(FirstComponent()))
        assertFalse(scope.setup(FirstComponent()))
        assertTrue(scope.setup(SecondComponent()))
    }
}
