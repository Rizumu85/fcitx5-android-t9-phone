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

/** Measures sustained Chinese T9 input at a cadence close to fast physical typing. */
@OptIn(ExperimentalMetricApi::class)
class ChineseT9TypingBenchmark {
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
    fun burstWithBaselineProfile() {
        benchmarkRule.measureRepeated(
            packageName = driver.targetPackage,
            metrics = listOf(ArtMetric(), FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            iterations = 5,
            setupBlock = {
                driver.prepareColdTarget()
                driver.openEditorAndWait()
            },
            measureBlock = {
                driver.pacedKeySequence(LONG_PINYIN_SEQUENCE)
            }
        )
    }

    private companion object {
        const val LONG_PINYIN_SEQUENCE = "946649366674494233"
    }
}
