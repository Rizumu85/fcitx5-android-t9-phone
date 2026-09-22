/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.core

import org.junit.Assert.*
import org.junit.Test

class FcitxPresentationFrameTest {
    @Test fun aFrameRetainsItsProducingCommandEvenAfterAnotherCommandStarts() {
        val source = FcitxPresentationSequencer()
        source.commandId = 17
        val panel = source.stamp(FcitxEvent.InputPanelEvent(FcitxEvent.InputPanelEvent.Data())) as FcitxEvent.InputPanelEvent
        source.commandId = 18
        val page = source.stamp(FcitxEvent.PagedCandidateEvent(FcitxEvent.PagedCandidateEvent.Data.Empty)) as FcitxEvent.PagedCandidateEvent
        assertEquals(17L, panel.origin.commandId)
        assertEquals(panel.origin, page.origin)
        assertEquals(panel.origin, source.completeFrame!!.origin)
    }

    @Test fun incompleteNewFrameCannotReplaceTheLastCompleteCache() {
        val source = FcitxPresentationSequencer()
        source.commandId = 1
        source.stamp(FcitxEvent.InputPanelEvent(FcitxEvent.InputPanelEvent.Data()))
        source.stamp(FcitxEvent.PagedCandidateEvent(FcitxEvent.PagedCandidateEvent.Data.Empty))
        val complete = source.completeFrame
        source.commandId = 2
        source.stamp(FcitxEvent.InputPanelEvent(FcitxEvent.InputPanelEvent.Data()))
        assertSame(complete, source.completeFrame)
        source.stamp(FcitxEvent.PagedCandidateEvent(FcitxEvent.PagedCandidateEvent.Data.Empty))
        assertEquals(2L, source.completeFrame!!.origin.commandId)
        assertNotEquals(complete!!.origin.frameId, source.completeFrame!!.origin.frameId)
    }

    @Test fun resetInvalidatesIncompleteAndCachedFrames() {
        val source = FcitxPresentationSequencer()
        source.commandId = 1
        source.stamp(FcitxEvent.InputPanelEvent(FcitxEvent.InputPanelEvent.Data()))
        source.reset()
        source.stamp(FcitxEvent.PagedCandidateEvent(FcitxEvent.PagedCandidateEvent.Data.Empty))
        assertNull(source.completeFrame)
        assertEquals(0L, source.commandId)
    }
}
