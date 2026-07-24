/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RimeConfigProvisioningPolicyTest {
    @Test
    fun `healthy matching or newer configuration performs no work`() {
        assertEquals(
            RimeConfigProvisioningPolicy.Decision.READY,
            decide(installed = "3.0.0", healthy = true)
        )
        assertEquals(
            RimeConfigProvisioningPolicy.Decision.READY,
            decide(installed = "3.1.0", healthy = true)
        )
    }

    @Test
    fun `missing schema repairs even when receipt claims required version`() {
        assertEquals(
            RimeConfigProvisioningPolicy.Decision.DOWNLOAD,
            decide(installed = "3.0.0", healthy = false)
        )
    }

    @Test
    fun `matching pending work suppresses duplicate download`() {
        assertEquals(
            RimeConfigProvisioningPolicy.Decision.WAIT,
            decide(installed = null, healthy = false, pending = true)
        )
    }

    @Test
    fun `plugin package matching stays inside the current build family`() {
        assertTrue(
            RimePluginPackagePolicy.isCurrentVariant(
                "org.fcitx.fcitx5.android.plugin.rime",
                debug = false
            )
        )
        assertTrue(
            RimePluginPackagePolicy.isCurrentVariant(
                "org.fcitx.fcitx5.android.plugin.rime.debug",
                debug = true
            )
        )
        assertFalse(
            RimePluginPackagePolicy.isCurrentVariant(
                "org.fcitx.fcitx5.android.plugin.rime",
                debug = true
            )
        )
    }

    private fun decide(
        installed: String?,
        healthy: Boolean,
        pending: Boolean = false
    ) = RimeConfigProvisioningPolicy.decide(
        installedVersion = installed,
        requiredVersion = "3.0.0",
        healthy = healthy,
        matchingDownloadPending = pending
    )
}
