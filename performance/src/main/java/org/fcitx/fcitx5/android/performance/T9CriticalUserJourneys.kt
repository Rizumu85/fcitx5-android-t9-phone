/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.performance

object T9CriticalUserJourneys {
    fun exercise(driver: ImePerformanceDriver) {
        handwriting(driver)
        pinyin(driver)
        stroke(driver)
        zhuyin(driver)
        punctuation(driver)
        smartEnglish(driver)
        number(driver)
    }

    private fun pinyin(driver: ImePerformanceDriver) {
        driver.keySequence("435")
        driver.confirm()
        driver.pacedKeySequence("946649366674494233")
        driver.clearComposition()
    }

    private fun stroke(driver: ImePerformanceDriver) {
        driver.clearComposition()
        driver.cycleChineseScheme("STROKE")
        driver.keySequence("12345")
        driver.confirm()
    }

    private fun zhuyin(driver: ImePerformanceDriver) {
        driver.clearComposition()
        driver.cycleChineseScheme("ZHUYIN")
        driver.keySequence("38")
        driver.confirm()
    }

    private fun punctuation(driver: ImePerformanceDriver) {
        driver.clearComposition()
        driver.cycleChineseScheme("PINYIN")
        driver.symbol()
        driver.confirm()
    }

    private fun smartEnglish(driver: ImePerformanceDriver) {
        driver.clearComposition()
        driver.switchMode("ENGLISH")
        driver.keySequence("43556")
        driver.confirm()
        driver.key(0)
        driver.backspace()
    }

    private fun number(driver: ImePerformanceDriver) {
        driver.clearComposition()
        driver.switchMode("NUMBER")
        driver.keySequence("123")
        driver.switchMode("CHINESE")
    }

    private fun handwriting(driver: ImePerformanceDriver) {
        driver.exerciseHandwriting()
    }
}
