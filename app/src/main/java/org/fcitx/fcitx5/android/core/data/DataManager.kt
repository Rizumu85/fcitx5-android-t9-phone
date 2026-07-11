/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2024 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.core.data

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Build
import android.os.SystemClock
import android.util.AtomicFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.BuildConfig
import org.fcitx.fcitx5.android.core.performance.StartupPerformanceTrace
import org.fcitx.fcitx5.android.core.data.DataManager.dataDir
import org.fcitx.fcitx5.android.utils.FileUtil
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.isJavaIdentifier
import org.fcitx.fcitx5.android.utils.versionCodeCompat
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Build up a Filesystem hierarchy at [dataDir]
 *
 * Operations are synchronized
 */
object DataManager {

    data class PluginSet(
        val loaded: Set<PluginDescriptor>,
        val failed: Map<String, PluginLoadFailed>
    )

    const val PLUGIN_INTENT = "${BuildConfig.APPLICATION_ID}.plugin.MANIFEST"

    private val lock = ReentrantLock()

    private val json by lazy { Json { prettyPrint = true } }

    var synced = false
        private set

    // should be consistent with the deserialization in DataDescriptorPlugin (:build-logic)
    private fun deserializeDataDescriptor(raw: String): DataDescriptor {
        return json.decodeFromString<DataDescriptor>(raw)
    }

    private fun serializeDataDescriptor(descriptor: DataDescriptor): String {
        return json.encodeToString(descriptor)
    }

    private fun deserializeInstallationState(raw: String): DataInstallationState {
        return json.decodeFromString<DataInstallationState>(raw)
    }

    private fun serializeInstallationState(state: DataInstallationState): String {
        return json.encodeToString(state)
    }

    // If Android version supports direct boot, we put the hierarchy in device encrypted storage
    // instead of credential encrypted storage so that data can be accessed before user unlock
    val dataDir: File = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Timber.d("Using device protected storage")
        appContext.createDeviceProtectedStorageContext().dataDir
    } else {
        File(appContext.applicationInfo.dataDir)
    }

    private fun AssetManager.getDataDescriptor(): DataDescriptor {
        return open(BuildConfig.DATA_DESCRIPTOR_NAME)
            .bufferedReader()
            .use { it.readText() }
            .let { deserializeDataDescriptor(it) }
    }

    private fun AssetManager.getDataDescriptorFingerprint(): SHA256? = runCatching {
        open(BuildConfig.DATA_DESCRIPTOR_FINGERPRINT_NAME)
            .bufferedReader()
            .use { it.readText() }
            .trim()
            .takeIf { value ->
                value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
            }
            ?: error("Invalid data descriptor fingerprint")
    }.getOrNull()

    private fun File.contentSha256(): SHA256 {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toLowerHex()
    }

    private fun ByteArray.contentSha256(): SHA256 =
        MessageDigest.getInstance("SHA-256").digest(this).toLowerHex()

    private fun ByteArray.toLowerHex(): String = buildString(size * 2) {
        for (byte in this@toLowerHex) {
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }

    private fun File.readAtomicText(): String =
        AtomicFile(this).openRead().bufferedReader().use { it.readText() }

    private fun File.writeAtomicText(value: String) {
        val atomicFile = AtomicFile(this)
        val stream = atomicFile.startWrite()
        try {
            stream.write(value.toByteArray())
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    private val loadedPlugins = mutableSetOf<PluginDescriptor>()
    private val failedPlugins = mutableMapOf<String, PluginLoadFailed>()

    fun getLoadedPlugins(): Set<PluginDescriptor> = loadedPlugins
    fun getFailedPlugins(): Map<String, PluginLoadFailed> = failedPlugins

    fun getSyncedPluginSet() = PluginSet(loadedPlugins, failedPlugins)

    /**
     * Will be cleared after each sync
     */
    private val callbacks = mutableListOf<() -> Unit>()

    fun addOnNextSyncedCallback(block: () -> Unit) =
        callbacks.add(block)

    private fun pluginPackageNames(): List<String> {
        val pm = appContext.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                Intent(PLUGIN_INTENT),
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            pm.queryIntentActivities(Intent(PLUGIN_INTENT), PackageManager.MATCH_ALL)
        }.map { it.activityInfo.packageName }.distinct().sorted()
    }

    private fun discoverPluginPackages(): List<PluginPackageIdentity> {
        val pm = appContext.packageManager
        return pluginPackageNames().mapNotNull { packageName ->
            runCatching {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    pm.getPackageInfo(packageName, 0)
                }
                PluginPackageIdentity(
                    packageName = packageName,
                    versionCode = info.versionCodeCompat,
                    lastUpdateTime = info.lastUpdateTime
                )
            }.onFailure {
                Timber.w(it, "Plugin package disappeared while discovering $packageName")
            }.getOrNull()
        }.canonicalOrder()
    }

    fun detectPlugins(): PluginSet = detectPlugins(discoverPluginPackages())

    private fun detectPlugins(pluginPackages: List<PluginPackageIdentity>): PluginSet {
        val toLoad = mutableSetOf<PluginDescriptor>()
        val preloadFailed = mutableMapOf<String, PluginLoadFailed>()

        val pm = appContext.packageManager

        Timber.d("Detected plugin packages: ${pluginPackages.joinToString { it.packageName }}")

        // Parse plugin.xml
        for ((packageName) in pluginPackages) {
            val res = pm.getResourcesForApplication(packageName)

            @SuppressLint("DiscouragedApi")
            val resId = res.getIdentifier("plugin", "xml", packageName)
            if (resId == 0) {
                Timber.w("Failed to get the plugin descriptor of $packageName")
                preloadFailed[packageName] = PluginLoadFailed.MissingPluginDescriptor
                continue
            }
            val parser = res.getXml(resId)
            var eventType = parser.eventType
            var domain: String? = null
            var apiVersion: String? = null
            var description: String? = null
            var hasService = false
            var text: String? = null
            while ((eventType != XmlPullParser.END_DOCUMENT)) {
                when (eventType) {
                    XmlPullParser.TEXT -> text = parser.text
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "apiVersion" -> apiVersion = text
                        "domain" -> domain = text
                        "description" -> description = text
                        "hasService" -> hasService = text?.lowercase() == "true"
                    }
                }
                eventType = parser.next()
            }
            parser.close()

            if (description?.startsWith("@string/") == true) {
                // Replace "@string/" with string resource
                val s = description.substring(8)
                if (s.isJavaIdentifier()) {
                    @SuppressLint("DiscouragedApi")
                    val id = res.getIdentifier(s, "string", packageName)
                    if (id != 0) description = res.getString(id)
                }
            }

            if (apiVersion != null && description != null) {
                if (PluginDescriptor.pluginAPI == apiVersion) {
                    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(
                            packageName,
                            PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                        )
                    } else {
                        pm.getPackageInfo(packageName, PackageManager.GET_META_DATA)
                    }
                    toLoad.add(
                        PluginDescriptor(
                            packageName,
                            apiVersion,
                            domain,
                            description,
                            hasService,
                            info.versionName ?: "",
                            info.applicationInfo?.nativeLibraryDir ?: ""
                        )
                    )
                } else {
                    Timber.w("$packageName's api version [$apiVersion] doesn't match with the current [${PluginDescriptor.pluginAPI}]")
                    preloadFailed[packageName] = PluginLoadFailed.PluginAPIIncompatible(apiVersion)
                }
            } else {
                Timber.w("Failed to parse plugin descriptor of $packageName")
                preloadFailed[packageName] = PluginLoadFailed.PluginDescriptorParseError
            }
        }
        return PluginSet(toLoad, preloadFailed)
    }

    private fun completeSync() {
        callbacks.forEach { it() }
        callbacks.clear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Device-protected storage is authoritative; stale credential data must not win later.
            val oldDataDir = appContext.dataDir
            val oldDataDescriptor = oldDataDir.resolve(BuildConfig.DATA_DESCRIPTOR_NAME)
            if (oldDataDescriptor.exists()) {
                oldDataDescriptor.delete()
                oldDataDir.resolve("README.md").delete()
                oldDataDir.resolve("usr").deleteRecursively()
            }
        }
        synced = true
    }

    fun sync() = lock.withLock {
        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
        synced = false
        loadedPlugins.clear()
        failedPlugins.clear()

        val destDescriptorFile = File(dataDir, BuildConfig.DATA_DESCRIPTOR_NAME)
        val installationStateFile = File(dataDir, "${BuildConfig.DATA_DESCRIPTOR_NAME}.installed")

        val mainDescriptorSha256 = StartupPerformanceTrace.measure(
            StartupPerformanceTrace.Stage.DATA_MAIN_FINGERPRINT_LOAD
        ) {
            appContext.assets.getDataDescriptorFingerprint()
        }
        val pluginPackages = StartupPerformanceTrace.measure(
            StartupPerformanceTrace.Stage.DATA_PLUGIN_DISCOVERY
        ) {
            discoverPluginPackages()
        }

        val mergedDescriptorFileSha256 = StartupPerformanceTrace.measure(
            StartupPerformanceTrace.Stage.DATA_MERGED_FINGERPRINT_LOAD
        ) {
            destDescriptorFile.runCatching { contentSha256() }.getOrNull()
        }

        val completedState = StartupPerformanceTrace.measure(
            StartupPerformanceTrace.Stage.DATA_INSTALLATION_STATE_LOAD
        ) {
            installationStateFile
                .runCatching { deserializeInstallationState(readAtomicText()) }
                .getOrNull()
        }

        if (mainDescriptorSha256 != null && mergedDescriptorFileSha256 != null &&
            completedState?.canReuse(
                mainDescriptorSha256 = mainDescriptorSha256,
                mergedDescriptorFileSha256 = mergedDescriptorFileSha256,
                pluginPackages = pluginPackages
            ) == true
        ) {
            loadedPlugins.addAll(completedState.restoredPlugins())
            StartupPerformanceTrace.measure(StartupPerformanceTrace.Stage.DATA_COMPLETION) {
                completeSync()
            }
            Timber.i(
                "Data installation fast path completed in %.2f ms",
                (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000.0
            )
            return@withLock
        }

        // Only a fully completed install may authorize the next cold-start fast path.
        AtomicFile(installationStateFile).delete()

        val mainDescriptor = appContext.assets.getDataDescriptor()
        val oldDescriptor = destDescriptorFile
            .runCatching { deserializeDataDescriptor(readAtomicText()) }
            .getOrNull()

        val (parsedDescriptors, failed) = detectPlugins(pluginPackages)
        failedPlugins.putAll(failed)

        Timber.d("Plugins to load: $parsedDescriptors")

        // Create an empty hierarchy
        val newHierarchy = DataHierarchy()
        // Always add app's first
        newHierarchy.install(mainDescriptor, FileSource.Main)

        val pluginAssets = mutableMapOf<String, AssetManager>()

        // Add plugin's one by one
        for (plugin in parsedDescriptors) {
            val pluginContext = appContext.createPackageContext(plugin.packageName, 0)
            val assets = pluginContext.assets
            val descriptor = runCatching { assets.getDataDescriptor() }.onFailure { error ->
                Timber.w("Failed to get or decode data descriptor of '${plugin.name}'")
                Timber.w(error)
                failedPlugins[plugin.packageName] = if (error is FileNotFoundException) {
                    PluginLoadFailed.MissingDataDescriptor(plugin)
                } else {
                    PluginLoadFailed.DataDescriptorParseError(plugin)
                }
            }.getOrNull() ?: continue
            try {
                newHierarchy.install(descriptor, FileSource.Plugin(plugin))
            } catch (e: DataHierarchy.PathConflict) {
                Timber.w("Path '${e.path}' has already been created by '${e.src}', cannot create file")
                failedPlugins[plugin.packageName] =
                    PluginLoadFailed.PathConflict(plugin, e.path, e.src)
                continue
            } catch (e: DataHierarchy.SymlinkConflict) {
                Timber.w("Path '${e.path}' has already been created by '${e.src}', cannot create symlink")
                failedPlugins[plugin.packageName] =
                    PluginLoadFailed.PathConflict(plugin, e.path, e.src)
                continue
            }
            pluginAssets[plugin.name] = assets
            loadedPlugins.add(plugin)
            Timber.d("Merged data hierarchy of ${plugin.name}")
        }

        Timber.d("Hierarchy created")

        // Compute the difference of the created one and the old one
        // Run actions to migrate to the new hierarchy
        DataHierarchy.diff(
            oldDescriptor ?: DataDescriptor("", emptyMap(), emptyMap()),
            newHierarchy
        ).sortedByDescending { it.ordinal }.forEach {
            Timber.d("Action: $it")
            when (it) {
                is FileAction.CreateFile -> {
                    val assets = if (it.src is FileSource.Plugin)
                        pluginAssets.getValue(it.src.descriptor.name)
                    else appContext.assets
                    assets.copyFile(it.path)
                }
                is FileAction.DeleteDir -> {
                    removePath(it.path).getOrThrow()
                }
                is FileAction.DeleteFile -> {
                    removePath(it.path).getOrThrow()
                }
                is FileAction.UpdateFile -> {
                    val assets = if (it.src is FileSource.Plugin)
                        pluginAssets.getValue(it.src.descriptor.name)
                    else appContext.assets
                    assets.copyFile(it.path)
                }
                is FileAction.CreateSymlink -> {
                    removePath(it.path).getOrThrow()
                    symlink(it.src, it.path).getOrThrow()
                }
            }
        }
        val mergedDescriptor = newHierarchy.downToDataDescriptor()
        val serializedMergedDescriptor = serializeDataDescriptor(mergedDescriptor)
        destDescriptorFile.writeAtomicText(serializedMergedDescriptor)
        if (failedPlugins.isEmpty()) {
            installationStateFile.writeAtomicText(
                serializeInstallationState(
                    DataInstallationState.completed(
                        mainDescriptorSha256 = mainDescriptor.sha256,
                        mergedDescriptorSha256 = mergedDescriptor.sha256,
                        mergedDescriptorFileSha256 = serializedMergedDescriptor
                            .toByteArray()
                            .contentSha256(),
                        pluginPackages = pluginPackages,
                        loadedPlugins = loadedPlugins
                    )
                )
            )
        }
        completeSync()
        Timber.i(
            "Full data installation completed in %.2f ms",
            (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000.0
        )
    }

    private fun removePath(path: String) =
        FileUtil.removeFile(dataDir.resolve(path))

    private fun symlink(source: String, target: String) =
        FileUtil.symlink(dataDir.resolve(source), dataDir.resolve(target))

    private fun AssetManager.copyFile(filename: String) {
        open(filename).use { i ->
            File(dataDir, filename)
                .also { it.parentFile?.mkdirs() }
                .outputStream()
                .use { o -> i.copyTo(o) }
        }
    }

    private const val HEX_DIGITS = "0123456789abcdef"

    fun deleteAndSync() {
        lock.withLock {
            AtomicFile(dataDir.resolve(BuildConfig.DATA_DESCRIPTOR_NAME)).delete()
            AtomicFile(dataDir.resolve("${BuildConfig.DATA_DESCRIPTOR_NAME}.installed")).delete()
            dataDir.resolve("README.md").delete()
            dataDir.resolve("usr").deleteRecursively()
        }
        sync()
    }

}
