/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.handwriting

import kotlinx.coroutines.runBlocking
import org.fcitx.fcitx5.android.input.t9.ChineseT9OutputScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChineseHandwritingSessionTest {
    @Test
    fun selectedRecognitionRequestsPredictionAndKeepsRecognitionContext() = runBlocking {
        val requests = mutableListOf<Triple<String, ChineseT9OutputScript, Int>>()
        val session = ChineseHandwritingSession(
            predictionProvider = ChineseHandwritingPredictionProvider { context, script, limit ->
                requests += Triple(context, script, limit)
                listOf(" 好 ", "好", "吗")
            },
            predictionEnabled = { true },
            outputScript = { ChineseT9OutputScript.Simplified },
            candidateLimit = 10
        )
        session.begin("你")

        val request = requireNotNull(
            session.commitCandidate("好", continuePrediction = true)
        )

        assertEquals("你好", session.recognitionPreContext())
        assertEquals(listOf("好", "吗"), session.resolve(request))
        assertEquals(
            listOf(Triple("好", ChineseT9OutputScript.Simplified, 10)),
            requests
        )
    }

    @Test
    fun disabledOrImplicitCommitDoesNotOpenPrediction() {
        var enabled = false
        val session = ChineseHandwritingSession(
            predictionProvider = ChineseHandwritingPredictionProvider { _, _, _ ->
                error("Provider should not be queried")
            },
            predictionEnabled = { enabled },
            outputScript = { ChineseT9OutputScript.Traditional },
            candidateLimit = 10
        )
        session.begin("")

        assertNull(session.commitCandidate("你", continuePrediction = true))
        enabled = true
        assertNull(session.commitCandidate("好", continuePrediction = false))
        assertEquals("你好", session.recognitionPreContext())
    }
}
