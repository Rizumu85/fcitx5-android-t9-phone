/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.core

import org.fcitx.fcitx5.android.update.RimeConfigDeploymentRequirement
import org.junit.Assert.assertEquals
import org.junit.Test

class RimeConfigDeploymentSessionTest {
    private val deploying = FcitxEvent.RimeAvailabilityEvent.State.Deploying
    private val ready = FcitxEvent.RimeAvailabilityEvent.State.Ready

    @Test
    fun `new configuration holds lightweight ready until full deployment succeeds`() {
        val session = RimeConfigDeploymentSession()

        assertEquals(
            RimeConfigDeploymentSession.Effect.FORWARD,
            session.onAvailability(deploying, RimeConfigDeploymentRequirement.REQUIRED)
        )
        assertEquals(
            RimeConfigDeploymentSession.Effect.REQUEST_FULL_DEPLOYMENT,
            session.onAvailability(ready, RimeConfigDeploymentRequirement.REQUIRED)
        )
        assertEquals(
            RimeConfigDeploymentSession.Effect.FORWARD,
            session.onAvailability(deploying, RimeConfigDeploymentRequirement.REQUIRED)
        )
        assertEquals(
            RimeConfigDeploymentSession.Effect.COMPLETE_FULL_DEPLOYMENT,
            session.onAvailability(ready, RimeConfigDeploymentRequirement.REQUIRED)
        )
        assertEquals(
            RimeConfigDeploymentSession.Effect.FORWARD,
            session.onAvailability(ready, RimeConfigDeploymentRequirement.REQUIRED)
        )
    }

    @Test
    fun `incomplete source tree never publishes a misleading ready state`() {
        val session = RimeConfigDeploymentSession()

        assertEquals(
            RimeConfigDeploymentSession.Effect.FORWARD,
            session.onAvailability(deploying, RimeConfigDeploymentRequirement.WAIT_FOR_SOURCE)
        )
        assertEquals(
            RimeConfigDeploymentSession.Effect.HOLD_READY,
            session.onAvailability(ready, RimeConfigDeploymentRequirement.WAIT_FOR_SOURCE)
        )
    }
}
