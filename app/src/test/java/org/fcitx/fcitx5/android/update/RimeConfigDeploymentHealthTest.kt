/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RimeConfigDeploymentHealthTest {
    private val temporaryFolder = TemporaryFolder()

    @Test
    fun `source receipt is not deployment readiness`() {
        temporaryFolder.create()
        val rimeDir = temporaryFolder.newFolder("rime")
        writeRequiredSources(rimeDir)

        assertEquals(
            RimeConfigDeploymentRequirement.REQUIRED,
            RimeConfigDeploymentHealth.deploymentRequirement(rimeDir)
        )

        writeRequiredCompiledSchemas(rimeDir)
        assertEquals(
            RimeConfigDeploymentRequirement.NONE,
            RimeConfigDeploymentHealth.deploymentRequirement(rimeDir)
        )
        temporaryFolder.delete()
    }

    @Test
    fun `durable marker requires deployment even with existing compiled schemas`() {
        temporaryFolder.create()
        val rimeDir = temporaryFolder.newFolder("rime")
        writeRequiredSources(rimeDir)
        writeRequiredCompiledSchemas(rimeDir)
        rimeDir.resolve(RimeConfigDeploymentHealth.DeploymentRequiredMarker).writeText("3.1.0")

        assertEquals(
            RimeConfigDeploymentRequirement.REQUIRED,
            RimeConfigDeploymentHealth.deploymentRequirement(rimeDir)
        )
        temporaryFolder.delete()
    }

    @Test
    fun `partial overlay waits for source repair instead of compiling it`() {
        temporaryFolder.create()
        val rimeDir = temporaryFolder.newFolder("rime")
        writeRequiredSources(rimeDir)
        rimeDir.resolve(RimeConfigDeploymentHealth.InstallInProgressMarker).writeText("3.1.0")

        assertEquals(
            RimeConfigDeploymentRequirement.WAIT_FOR_SOURCE,
            RimeConfigDeploymentHealth.deploymentRequirement(rimeDir)
        )
        temporaryFolder.delete()
    }

    private fun writeRequiredSources(rimeDir: java.io.File) {
        listOf(
            "t9.schema.yaml",
            "t9_stroke.schema.yaml",
            "t9_zhuyin.schema.yaml",
            "predict.db"
        ).forEach { rimeDir.resolve(it).writeText("schema") }
    }

    private fun writeRequiredCompiledSchemas(rimeDir: java.io.File) {
        listOf(
            "build/t9.prism.bin",
            "build/t9_stroke.prism.bin",
            "build/t9_zhuyin.prism.bin"
        ).forEach {
            rimeDir.resolve(it).apply {
                parentFile?.mkdirs()
                writeText("compiled")
            }
        }
    }
}
