/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.core

import org.fcitx.fcitx5.android.update.RimeConfigDeploymentRequirement

internal class RimeConfigDeploymentSession {
    enum class Effect {
        FORWARD,
        HOLD_READY,
        REQUEST_FULL_DEPLOYMENT,
        COMPLETE_FULL_DEPLOYMENT
    }

    private enum class Phase {
        IDLE,
        REQUESTED,
        DEPLOYING,
        COMPLETED
    }

    private var phase = Phase.IDLE
    private var requests = 0

    fun onAvailability(
        state: FcitxEvent.RimeAvailabilityEvent.State,
        requirement: RimeConfigDeploymentRequirement
    ): Effect {
        if (requirement == RimeConfigDeploymentRequirement.NONE) {
            phase = Phase.IDLE
            requests = 0
            return Effect.FORWARD
        }
        if (requirement == RimeConfigDeploymentRequirement.WAIT_FOR_SOURCE) {
            phase = Phase.IDLE
            return if (state == FcitxEvent.RimeAvailabilityEvent.State.Ready) {
                Effect.HOLD_READY
            } else {
                Effect.FORWARD
            }
        }

        return when (state) {
            FcitxEvent.RimeAvailabilityEvent.State.Ready -> when (phase) {
                // A Ready without Deploying after a request means the plugin did not start the
                // maintenance run. Retry a bounded number of times, then publish the lightweight
                // Ready rather than block Chinese input until Fcitx restarts. The durable marker
                // stays, so the next native start requests the full deployment again.
                Phase.IDLE,
                Phase.REQUESTED -> if (requests < MaxRequests) {
                    phase = Phase.REQUESTED
                    requests++
                    Effect.REQUEST_FULL_DEPLOYMENT
                } else {
                    phase = Phase.IDLE
                    Effect.FORWARD
                }
                Phase.DEPLOYING -> {
                    phase = Phase.COMPLETED
                    Effect.COMPLETE_FULL_DEPLOYMENT
                }
                Phase.COMPLETED -> Effect.FORWARD
            }
            FcitxEvent.RimeAvailabilityEvent.State.Deploying -> {
                if (phase == Phase.REQUESTED) {
                    phase = Phase.DEPLOYING
                }
                Effect.FORWARD
            }
            FcitxEvent.RimeAvailabilityEvent.State.Failed,
            FcitxEvent.RimeAvailabilityEvent.State.Unavailable -> {
                phase = Phase.IDLE
                Effect.FORWARD
            }
            FcitxEvent.RimeAvailabilityEvent.State.Starting -> Effect.FORWARD
        }
    }

    /**
     * The caller publishes the Ready it was holding, since no deployment will replace it. A later
     * Ready requests deployment again while attempts remain.
     */
    fun onRequestFailed() {
        phase = Phase.IDLE
    }

    fun reset() {
        phase = Phase.IDLE
        requests = 0
    }

    private companion object {
        const val MaxRequests = 3
    }
}
