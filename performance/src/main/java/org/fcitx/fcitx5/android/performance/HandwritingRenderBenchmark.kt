/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.performance

import androidx.benchmark.macro.ArtMetric
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Measures the first handwriting interaction rather than an already warmed drawing surface. */
@OptIn(ExperimentalMetricApi::class)
class HandwritingRenderBenchmark {
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
    fun firstInputWithoutCompilation() = measure(CompilationMode.None())

    @Test
    fun firstInputWithBaselineProfile() = measure(
        CompilationMode.Partial(BaselineProfileMode.Require)
    )

    private fun measure(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = driver.targetPackage,
            metrics = listOf(ArtMetric(), FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = 3,
            setupBlock = {
                driver.prepareColdTarget()
                driver.openEditorAndWait()
            },
            measureBlock = {
                driver.exerciseHandwriting()
            }
        )
    }
}
