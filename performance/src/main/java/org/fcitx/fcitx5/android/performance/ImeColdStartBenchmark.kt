/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.performance

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMetricApi::class)
class ImeColdStartBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private lateinit var driver: ImePerformanceDriver

    @Before
    fun setUp() {
        driver = ImePerformanceDriver()
        driver.captureEnvironment()
        driver.configureTarget()
        driver.prepareStableRimeState()
    }

    @After
    fun tearDown() {
        driver.restoreEnvironment()
    }

    @Test
    fun coldStartWithoutCompilation() = measure(CompilationMode.None())

    @Test
    fun coldStartWithBaselineProfile() = measure(
        CompilationMode.Partial(BaselineProfileMode.Require)
    )

    private fun measure(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = driver.targetPackage,
            metrics = startupMetrics,
            compilationMode = compilationMode,
            iterations = 5,
            measureBlock = {
                // Selecting an IME starts its process on this device, so target selection must
                // stay inside the trace or Application/native startup would be measured as zero.
                driver.prepareColdTarget()
                driver.openEditorAndWait()
            }
        )
    }

    private companion object {
        val startupMetrics = listOf(
            traceMetric("application-create"),
            traceMetric("application-direct-boot"),
            traceMetric("application-crash-handler"),
            traceMetric("application-prefs-logging"),
            traceMetric("application-runtime-managers"),
            traceMetric("application-clipboard-init"),
            traceMetric("application-theme-init"),
            traceMetric("application-locales-init"),
            traceMetric("application-receiver-registration"),
            traceMetric("application-device-storage-sync"),
            traceMetric("application-restart-receiver"),
            traceMetric("data-installation"),
            traceMetric("fcitx-native-startup"),
            traceMetric("input-view-construction"),
            traceMetric("input-view-create"),
            traceMetric("input-auxiliary-surfaces-create"),
            traceMetric("input-chrome-primitives-create"),
            traceMetric("input-selection-panel-create"),
            traceMetric("input-number-panel-create"),
            traceMetric("input-password-preview-create"),
            traceMetric("input-components-create"),
            traceMetric("input-preference-bindings"),
            traceMetric("input-scope-registration"),
            traceMetric("input-scope-ready"),
            traceMetric("active-keyboard-create"),
            traceMetric("active-keyboard-attach"),
            traceMetric("input-chrome-create"),
            traceMetric("input-tree-assembly")
        )

        fun traceMetric(stage: String) = TraceSectionMetric(
            sectionName = "FcitxStartup:$stage",
            mode = TraceSectionMetric.Mode.Sum,
            label = stage
        )
    }
}
