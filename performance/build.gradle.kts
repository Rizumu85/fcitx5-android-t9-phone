plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "org.fcitx.fcitx5.android.performance"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.rules)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.junit)
}

val rimeFixtureApkDirectory =
    project(":plugin:rime").layout.buildDirectory.dir("outputs/apk/performanceRelease")
val rimeFixtureDeviceScript = layout.projectDirectory.file("rime-fixture-device.sh")
val rimeFixtureSchemaOverride =
    layout.projectDirectory.file("rime-fixture/default.custom.yaml")
val rimeConfigDirectory = rootProject.file(
    providers.gradleProperty("performanceRimeConfigDir")
        .getOrElse("../rime-ice-t9-phone")
)
val rimeFixtureArchiveDirectory = layout.buildDirectory.dir("rime-fixture")
val adbExecutable = androidComponents.sdkComponents.adb

val installPerformanceRimeFixture by tasks.registering(Exec::class) {
    group = "verification"
    description = "Installs the isolated Rime engine and stages its maintained configuration."
    dependsOn(":plugin:rime:assemblePerformanceRelease")
    inputs.file(rimeFixtureDeviceScript)
    inputs.file(rimeFixtureSchemaOverride)
    inputs.dir(rimeFixtureApkDirectory)
    inputs.files(fileTree(rimeConfigDirectory) {
        exclude(".git/**", ".github/**", "scripts/**")
    })
    doFirst {
        require(rimeConfigDirectory.isDirectory) {
            "Missing maintained Rime configuration checkout: $rimeConfigDirectory. " +
                "Set -PperformanceRimeConfigDir=/absolute/path when it is not a sibling repo."
        }
        commandLine(
            rimeFixtureDeviceScript.asFile,
            "install",
            adbExecutable.get().asFile,
            rimeFixtureApkDirectory.get().asFile,
            rimeConfigDirectory,
            rimeFixtureArchiveDirectory.get().asFile,
            rimeFixtureSchemaOverride.asFile
        )
    }
}

val uninstallPerformanceRimeFixture by tasks.registering(Exec::class) {
    group = "verification"
    description = "Removes the isolated Rime fixture from connected test devices."
    inputs.file(rimeFixtureDeviceScript)
    doFirst {
        commandLine(
            rimeFixtureDeviceScript.asFile,
            "uninstall",
            adbExecutable.get().asFile,
            rimeFixtureApkDirectory.get().asFile,
            rimeConfigDirectory,
            rimeFixtureArchiveDirectory.get().asFile,
            rimeFixtureSchemaOverride.asFile
        )
    }
}

tasks.configureEach {
    if (name == "connectedNonMinifiedReleaseAndroidTest" ||
        name == "connectedBenchmarkReleaseAndroidTest"
    ) {
        dependsOn(installPerformanceRimeFixture)
        finalizedBy(uninstallPerformanceRimeFixture)
    }
}
