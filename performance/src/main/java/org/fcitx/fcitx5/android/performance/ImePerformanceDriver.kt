/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.performance

import android.app.Instrumentation
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

/**
 * Owns the shell and physical-key contract shared by profile generation and benchmarks.
 */
class ImePerformanceDriver(
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation(),
    private val device: UiDevice = UiDevice.getInstance(instrumentation)
) {
    private val testPackage: String = instrumentation.context.getString(R.string.host_package)
    val targetPackage: String = instrumentation.context.getString(R.string.target_package)
    private val rimeFixturePackage: String =
        instrumentation.context.getString(R.string.rime_fixture_package)
    private val targetIme = "$targetPackage/$IME_SERVICE_CLASS"
    private var previousIme: String? = null
    private var runId = 0

    fun captureEnvironment() {
        previousIme = shell("settings get secure default_input_method")
            .trim()
            .takeUnless { it.isEmpty() || it == "null" }
    }

    fun configureTarget() {
        val fixturePath = shell("pm path $rimeFixturePackage")
        check(fixturePath.startsWith("package:")) {
            "The isolated Rime fixture is not installed: $rimeFixturePackage"
        }
        val fixtureActivities = shell("cmd package query-activities -a $RIME_FIXTURE_ACTION")
        check(rimeFixturePackage in fixtureActivities) {
            "The isolated Rime fixture does not expose $RIME_FIXTURE_ACTION"
        }
        seedMaintainedRimeConfig()
    }

    private fun seedMaintainedRimeConfig() {
        val revision = shell("cat $RIME_CONFIG_REVISION").trim()
        check(revision.matches(RIME_CONFIG_FINGERPRINT)) {
            "The maintained Rime configuration was not staged by the fixture installer"
        }
        val targetRimeDirectory =
            "/sdcard/Android/data/$targetPackage/files/data/rime"
        val targetRevision =
            shell("cat $targetRimeDirectory/.performance-config-revision").trim()
        if (targetRevision != revision) {
            ensureTargetOwnsRimeDirectory(targetRimeDirectory)
            // UiAutomation executes one argv command rather than an interactive shell on some
            // vendor builds, so control operators and quoting cannot define fixture correctness.
            shell("find $targetRimeDirectory -mindepth 1 -delete")
            shell("cp -R $RIME_CONFIG_CACHE/. $targetRimeDirectory/")
            shell(
                "cp $RIME_CONFIG_REVISION " +
                    "$targetRimeDirectory/.performance-config-revision"
            )
            val seededRevision =
                shell("cat $targetRimeDirectory/.performance-config-revision").trim()
            check(seededRevision == revision) {
                "Unable to seed maintained Rime revision $revision into $targetPackage"
            }
        }
        val missingFiles = RIME_CONFIG_FILES.filter { file ->
            shell("ls $targetRimeDirectory/$file").trim() != "$targetRimeDirectory/$file"
        }
        check(missingFiles.isEmpty()) {
            "The staged Rime configuration is incomplete in $targetPackage: $missingFiles"
        }
    }

    private fun ensureTargetOwnsRimeDirectory(targetRimeDirectory: String) {
        if (shell("ls -d $targetRimeDirectory").trim() == targetRimeDirectory) return
        prepareColdTarget()
        runId += 1
        try {
            val launchOutput = shell(
                "am start -W --activity-clear-top " +
                    "-n $testPackage/$HOST_ACTIVITY_CLASS --ei run_id $runId"
            )
            check("Status: ok" in launchOutput) {
                "Unable to initialize performance target storage: $launchOutput"
            }
            check(waitForTargetProcess() != null) {
                "Performance IME did not start while initializing its storage"
            }
            val deadline = SystemClock.uptimeMillis() + TARGET_STORAGE_TIMEOUT_MS
            while (SystemClock.uptimeMillis() < deadline) {
                if (shell("ls -d $targetRimeDirectory").trim() == targetRimeDirectory) return
                SystemClock.sleep(PROCESS_POLL_MS)
            }
            error("Performance IME did not create its Rime storage directory")
        } finally {
            device.pressHome()
            previousIme?.let {
                shell("ime enable $it")
                shell("ime set $it")
            }
            shell("am force-stop $targetPackage")
            SystemClock.sleep(IME_SELECTION_SETTLE_MS)
        }
    }

    fun prepareColdTarget() {
        device.wakeUp()
        shell("wm dismiss-keyguard")
        SystemClock.sleep(DEVICE_WAKE_SETTLE_MS)
        device.pressHome()
        shell("am force-stop $targetPackage")
        shell("ime enable $targetIme")
        val output = shell("ime set $targetIme")
        check("selected" in output.lowercase() || output.isBlank()) {
            "Unable to select performance IME: $output"
        }
        SystemClock.sleep(IME_SELECTION_SETTLE_MS)
    }

    fun prepareStableRimeState() {
        prepareColdTarget()
        openEditorAndWait(RIME_DEPLOYMENT_TIMEOUT_MS)
        device.pressHome()
        previousIme?.let {
            shell("ime enable $it")
            shell("ime set $it")
        }
        shell("am force-stop $targetPackage")
        SystemClock.sleep(IME_SELECTION_SETTLE_MS)
    }

    fun openEditorAndWait(rimeTimeoutMs: Long = RIME_READY_TIMEOUT_MS) {
        runId += 1
        val launchOutput = shell(
            "am start -W --activity-clear-top " +
                "-n $testPackage/$HOST_ACTIVITY_CLASS --ei run_id $runId"
        )
        check("Status: ok" in launchOutput) { "Unable to launch benchmark editor: $launchOutput" }
        val targetPid = waitForTargetProcess()
        check(targetPid != null) { "Performance IME process did not start" }
        check(waitForRimeReady(targetPid, rimeTimeoutMs)) {
            val logs = targetLogs(targetPid).takeLast(MAX_FAILURE_LOG_CHARS)
            "Performance Rime did not become active and ready. Target log:\n$logs"
        }
        device.waitForIdle(IME_IDLE_TIMEOUT_MS)
        SystemClock.sleep(IME_READY_SETTLE_MS)
    }

    fun restoreEnvironment() {
        previousIme?.let {
            shell("ime enable $it")
            shell("ime set $it")
        }
        if (previousIme != targetIme) {
            shell("ime disable $targetIme")
        }
    }

    fun keySequence(sequence: String) {
        sequence.forEach { key(it.digitToInt()) }
    }

    fun key(digit: Int) {
        require(digit in 0..9)
        physicalKey(KEYCODE_0 + digit)
    }

    fun symbol() = physicalKey(KEYCODE_STAR)

    fun confirm() = physicalKey(KEYCODE_DPAD_CENTER)

    fun backspace() = physicalKey(KEYCODE_DEL)

    fun exerciseHandwriting() {
        val targetPid = requireNotNull(waitForTargetProcess())
        val previousOccurrences = targetLogs(targetPid).countOccurrences(HANDWRITING_READY_MESSAGE)
        val width = device.displayWidth
        val height = device.displayHeight
        // IME child windows are absent from UiAutomator's accessibility hierarchy on this target.
        // The benchmark variant owns the default toolbar, so its fourth fixed slot is more
        // deterministic than a lookup that can never resolve. Orientation changes its row offset.
        val handwritingXPercent = if (width > height) 46 else 44
        val toolbarYPercent = if (width > height) 79 else 86
        device.click(width * handwritingXPercent / 100, height * toolbarYPercent / 100)
        val deadline = SystemClock.uptimeMillis() + HANDWRITING_UI_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (targetLogs(targetPid).countOccurrences(HANDWRITING_READY_MESSAGE) >
                previousOccurrences
            ) {
                break
            }
            SystemClock.sleep(HARNESS_STATE_POLL_MS)
        }
        check(targetLogs(targetPid).countOccurrences(HANDWRITING_READY_MESSAGE) >
            previousOccurrences
        ) { "Handwriting window did not open from the performance toolbar" }
        SystemClock.sleep(HANDWRITING_WINDOW_SETTLE_MS)

        // Relative coordinates keep profile collection independent of the physical test phone;
        // the handwriting tray intentionally owns the lower half of every portrait viewport.
        device.swipe(width * 28 / 100, height * 68 / 100, width * 72 / 100, height * 68 / 100, 18)
        device.swipe(width * 50 / 100, height * 60 / 100, width * 50 / 100, height * 82 / 100, 18)
        device.swipe(width * 38 / 100, height * 74 / 100, width * 63 / 100, height * 82 / 100, 14)
        SystemClock.sleep(HANDWRITING_RECOGNITION_SETTLE_MS)
        device.pressBack()
        SystemClock.sleep(HANDWRITING_WINDOW_SETTLE_MS)
    }

    fun clearComposition() {
        // Candidate confirmation can legitimately be a no-op for a dictionary miss. Repeated
        // Backspace gives the next scheme journey a composition-free boundary without a test hook.
        repeat(MAX_COMPOSITION_KEYS) { backspace() }
        SystemClock.sleep(KEY_SETTLE_MS)
    }

    fun cycleChineseScheme(expectedScheme: String) = physicalKeyAndAwaitState(
        keyCode = KEYCODE_STAR,
        expectedMessage = "Chinese scheme: $expectedScheme"
    )

    fun switchMode(expectedMode: String) = physicalKeyAndAwaitState(
        keyCode = KEYCODE_POUND,
        expectedMessage = "T9 mode: $expectedMode"
    )

    private fun physicalKeyAndAwaitState(keyCode: Int, expectedMessage: String) {
        val targetPid = requireNotNull(waitForTargetProcess())
        val previousOccurrences = targetLogs(targetPid).countOccurrences(expectedMessage)
        physicalKey(keyCode, longPress = true, settleMs = MODE_SETTLE_MS)
        val deadline = SystemClock.uptimeMillis() + HARNESS_STATE_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (targetLogs(targetPid).countOccurrences(expectedMessage) > previousOccurrences) return
            SystemClock.sleep(HARNESS_STATE_POLL_MS)
        }
        error("Performance IME did not reach '$expectedMessage'. Target log:\n${targetLogs(targetPid)}")
    }

    private fun physicalKey(
        keyCode: Int,
        longPress: Boolean = false,
        settleMs: Long = KEY_SETTLE_MS
    ) {
        // Explicit keyboard source is essential: ordinary shell injection can bypass the
        // physical-key route and write digits directly into the benchmark editor.
        val longPressOption = if (longPress) " --longpress" else ""
        shell("input keyboard keyevent$longPressOption $keyCode")
        SystemClock.sleep(settleMs)
    }

    private fun waitForTargetProcess(): String? {
        val deadline = SystemClock.uptimeMillis() + IME_PROCESS_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            shell("pidof $targetPackage").trim().takeIf(String::isNotEmpty)?.let { return it }
            SystemClock.sleep(PROCESS_POLL_MS)
        }
        return null
    }

    private fun waitForRimeReady(targetPid: String, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (PERFORMANCE_READY_MESSAGE in targetLogs(targetPid)) return true
            SystemClock.sleep(RIME_POLL_MS)
        }
        return false
    }

    private fun targetLogs(targetPid: String): String =
        shell("logcat --pid=$targetPid -d -v brief -s $PERFORMANCE_LOG_TAG:I")

    private fun shell(command: String): String = device.executeShellCommand(command)

    private fun String.countOccurrences(value: String): Int =
        lineSequence().count { value in it }

    private companion object {
        const val IME_SERVICE_CLASS =
            "org.fcitx.fcitx5.android.input.FcitxInputMethodService"
        const val HOST_ACTIVITY_CLASS =
            "org.fcitx.fcitx5.android.performance.BenchmarkHostActivity"
        const val RIME_FIXTURE_ACTION =
            "org.fcitx.fcitx5.android.plugin.MANIFEST"
        const val PERFORMANCE_LOG_TAG = "FcitxPerfHarness"
        const val PERFORMANCE_READY_MESSAGE = "Rime ready:"
        const val RIME_CONFIG_CACHE = "/data/local/tmp/fcitx-t9-rime-config"
        const val RIME_CONFIG_REVISION = "/data/local/tmp/fcitx-t9-rime-config.revision"

        val RIME_CONFIG_FINGERPRINT = Regex("^[0-9a-f]{40}-[0-9a-f]{64}-[0-9]+$")

        const val KEYCODE_0 = 7
        const val KEYCODE_STAR = 17
        const val KEYCODE_POUND = 18
        const val KEYCODE_DPAD_CENTER = 23
        const val KEYCODE_DEL = 67
        const val HANDWRITING_READY_MESSAGE = "Handwriting ready"
        const val HANDWRITING_UI_TIMEOUT_MS = 5_000L
        const val HANDWRITING_WINDOW_SETTLE_MS = 300L
        const val HANDWRITING_RECOGNITION_SETTLE_MS = 1_200L

        const val KEY_SETTLE_MS = 140L
        const val MODE_SETTLE_MS = 900L
        const val IME_SELECTION_SETTLE_MS = 250L
        const val DEVICE_WAKE_SETTLE_MS = 300L
        const val IME_READY_SETTLE_MS = 300L
        const val PROCESS_POLL_MS = 50L
        const val RIME_POLL_MS = 100L
        const val HARNESS_STATE_POLL_MS = 50L
        const val HARNESS_STATE_TIMEOUT_MS = 5_000L
        const val IME_PROCESS_TIMEOUT_MS = 10_000L
        const val TARGET_STORAGE_TIMEOUT_MS = 10_000L
        const val RIME_READY_TIMEOUT_MS = 90_000L
        const val RIME_DEPLOYMENT_TIMEOUT_MS = 5 * 60_000L
        const val IME_IDLE_TIMEOUT_MS = 2_000L
        const val MAX_FAILURE_LOG_CHARS = 8_000
        const val MAX_COMPOSITION_KEYS = 8

        val RIME_CONFIG_FILES = listOf(
            "default.custom.yaml",
            "t9.schema.yaml",
            "t9_stroke.schema.yaml",
            "t9_zhuyin.schema.yaml"
        )
    }
}
