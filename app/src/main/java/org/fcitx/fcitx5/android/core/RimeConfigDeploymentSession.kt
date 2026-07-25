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

    fun onAvailability(
        state: FcitxEvent.RimeAvailabilityEvent.State,
        requirement: RimeConfigDeploymentRequirement
    ): Effect {
        if (requirement == RimeConfigDeploymentRequirement.NONE) {
            phase = Phase.IDLE
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
                Phase.IDLE -> {
                    phase = Phase.REQUESTED
                    Effect.REQUEST_FULL_DEPLOYMENT
                }
                Phase.REQUESTED -> Effect.HOLD_READY
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
        }
    }

    fun reset() {
        phase = Phase.IDLE
    }
}
