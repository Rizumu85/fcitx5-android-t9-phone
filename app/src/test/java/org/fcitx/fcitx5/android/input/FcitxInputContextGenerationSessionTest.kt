/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FcitxInputContextGenerationSessionTest {
    @Test
    fun `bound editor is restored after a new native generation`() {
        val session = FcitxInputContextGenerationSession()
        session.bind(uid = 42, packageName = "example.editor")

        val request = session.onFcitxReady()

        assertNotNull(request)
        assertTrue(session.isRestoreRequired(request!!))
        assertTrue(session.completeRestore(request))
        assertFalse(session.isRestoreRequired(request))
    }

    @Test
    fun `normal activation suppresses a queued restore for the same generation`() {
        val session = FcitxInputContextGenerationSession()
        val binding = session.bind(uid = 42, packageName = "example.editor")
        val request = session.onFcitxReady()!!
        val activation = session.beginActivation(binding)!!

        assertTrue(session.completeActivation(activation))

        assertFalse(session.isRestoreRequired(request))
    }

    @Test
    fun `activation from an older native generation cannot suppress recovery`() {
        val session = FcitxInputContextGenerationSession()
        val binding = session.bind(uid = 42, packageName = "example.editor")
        val activation = session.beginActivation(binding)!!

        val request = session.onFcitxReady()!!

        assertFalse(session.completeActivation(activation))
        assertTrue(session.isRestoreRequired(request))
    }

    @Test
    fun `each native generation requires its own restore`() {
        val session = FcitxInputContextGenerationSession()
        session.bind(uid = 42, packageName = "example.editor")
        val first = session.onFcitxReady()!!
        session.completeRestore(first)

        val second = session.onFcitxReady()!!

        assertFalse(session.isRestoreRequired(first))
        assertTrue(session.isRestoreRequired(second))
    }

    @Test
    fun `stale binding cannot restore a replacement editor`() {
        val session = FcitxInputContextGenerationSession()
        session.bind(uid = 42, packageName = "first.editor")
        val stale = session.onFcitxReady()!!

        session.bind(uid = 84, packageName = "second.editor")

        assertFalse(session.isRestoreRequired(stale))
    }

    @Test
    fun `unbound service does not request restoration`() {
        val session = FcitxInputContextGenerationSession()
        session.bind(uid = 42, packageName = "example.editor")
        session.unbind()

        assertNull(session.onFcitxReady())
    }
}
