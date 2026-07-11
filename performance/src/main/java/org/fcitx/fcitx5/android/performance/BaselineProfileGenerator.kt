/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.performance

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

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
    fun startup() = baselineProfileRule.collect(
        packageName = driver.targetPackage,
        outputFilePrefix = "ime-startup",
        includeInStartupProfile = true
    ) {
        driver.prepareColdTarget()
        driver.openEditorAndWait()
    }

    @Test
    fun criticalT9Journeys() = baselineProfileRule.collect(
        packageName = driver.targetPackage,
        outputFilePrefix = "t9-critical-journeys",
        includeInStartupProfile = false
    ) {
        driver.prepareColdTarget()
        driver.openEditorAndWait()
        T9CriticalUserJourneys.exercise(driver)
    }
}
