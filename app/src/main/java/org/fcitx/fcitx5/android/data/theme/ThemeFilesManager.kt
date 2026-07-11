package org.fcitx.fcitx5.android.data.theme

import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.UserKeySoundPack
import org.fcitx.fcitx5.android.data.theme.bds.BdsArchive
import org.fcitx.fcitx5.android.data.theme.bds.BdsSkinConverter
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.errorRuntime
import org.fcitx.fcitx5.android.utils.extract
import org.fcitx.fcitx5.android.utils.withTempDir
import timber.log.Timber
import java.io.File
import java.io.FileFilter
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ThemeFilesManager {

    data class BaiduSkinImportResult(
        val themes: List<Theme.Custom>,
        val importedKeySounds: Boolean
    )

    private val dir = File(appContext.getExternalFilesDir(null), "theme").also { it.mkdirs() }

    private fun themeFile(theme: Theme.Custom) = File(dir, theme.name + ".json")

    fun newCustomBackgroundImages(): Triple<String, File, File> {
        val themeName = UUID.randomUUID().toString()
        val croppedImageFile = File(dir, "$themeName-cropped.png")
        val srcImageFile = File(dir, "$themeName-src")
        return Triple(themeName, croppedImageFile, srcImageFile)
    }

    fun saveThemeFiles(theme: Theme.Custom) {
        themeFile(theme).writeText(Json.encodeToString(CustomThemeSerializer, theme))
    }

    fun deleteThemeFiles(theme: Theme.Custom) {
        themeFile(theme).delete()
        theme.backgroundImage?.let {
            File(it.croppedFilePath).delete()
            File(it.srcFilePath).delete()
        }
        theme.baiduSkin?.let { File(it.assetDirectoryPath).deleteRecursively() }
    }

    fun listThemes(): MutableList<Theme.Custom> {
        val files = dir.listFiles(FileFilter { it.extension == "json" }) ?: return mutableListOf()
        return files
            .sortedByDescending { it.lastModified() } // newest first
            .mapNotNull decode@{
                val (theme, migrated) = runCatching {
                    Json.decodeFromString(CustomThemeSerializer.WithMigrationStatus, it.readText())
                }.getOrElse { e ->
                    Timber.w("Failed to decode theme file ${it.absolutePath}: ${e.message}")
                    return@decode null
                }
                if (theme.backgroundImage != null) {
                    if (!File(theme.backgroundImage.croppedFilePath).exists() ||
                        !File(theme.backgroundImage.srcFilePath).exists()
                    ) {
                        Timber.w("Cannot find background image file for theme ${theme.name}")
                        return@decode null
                    }
                }
                if (theme.baiduSkin?.isUsable() == false) {
                    Timber.w("Cannot find Baidu skin assets for theme ${theme.name}")
                    return@decode null
                }
                // Update the saved file if migration happens
                if (migrated) {
                    saveThemeFiles(theme)
                }
                return@decode theme
            }.toMutableList()
    }

    /**
     * [dest] will be closed on finished
     */
    fun exportTheme(theme: Theme.Custom, dest: OutputStream) =
        runCatching {
            ZipOutputStream(dest.buffered()).use { zipStream ->
                // we don't export the internal path of images
                val tweakedTheme = theme.copy(
                    backgroundImage = theme.backgroundImage?.let {
                        it.copy(
                            croppedFilePath = it.croppedFilePath
                                .substringAfterLast('/'),
                            srcFilePath = it.srcFilePath
                                .substringAfterLast('/'),
                        )
                    },
                    baiduSkin = theme.baiduSkin?.let {
                        it.copy(assetDirectoryPath = File(it.assetDirectoryPath).name)
                    }
                )
                if (tweakedTheme.backgroundImage != null) {
                    requireNotNull(theme.backgroundImage)
                    // write cropped image
                    zipStream.putNextEntry(ZipEntry(tweakedTheme.backgroundImage.croppedFilePath))
                    File(theme.backgroundImage.croppedFilePath).inputStream()
                        .use { it.copyTo(zipStream) }
                    // write src image
                    zipStream.putNextEntry(ZipEntry(tweakedTheme.backgroundImage.srcFilePath))
                    File(theme.backgroundImage.srcFilePath).inputStream()
                        .use { it.copyTo(zipStream) }
                }
                theme.baiduSkin?.let { skin ->
                    val sourceDir = File(skin.assetDirectoryPath)
                    val exportedDir = File(tweakedTheme.baiduSkin!!.assetDirectoryPath)
                    sourceDir.walkTopDown().filter(File::isFile).forEach { file ->
                        val relative = file.relativeTo(sourceDir).invariantSeparatorsPath
                        zipStream.putNextEntry(ZipEntry("${exportedDir.name}/$relative"))
                        file.inputStream().use { it.copyTo(zipStream) }
                        zipStream.closeEntry()
                    }
                }
                // write json
                zipStream.putNextEntry(ZipEntry("${tweakedTheme.name}.json"))
                zipStream.write(
                    Json.encodeToString(CustomThemeSerializer, tweakedTheme)
                        .encodeToByteArray()
                )
                // done
                zipStream.closeEntry()
            }
        }

    /**
     * @return (newCreated, theme, migrated)
     */
    fun importTheme(src: InputStream): Result<Triple<Boolean, Theme.Custom, Boolean>> =
        runCatching {
            ZipInputStream(src).use { zipStream ->
                withTempDir { tempDir ->
                    val extracted = zipStream.extract(tempDir)
                    val jsonFile = extracted.find { it.extension == "json" }
                        ?: errorRuntime(R.string.exception_theme_json)
                    val (decoded, migrated) = Json.decodeFromString(
                        CustomThemeSerializer.WithMigrationStatus,
                        jsonFile.readText()
                    )
                    if (ThemeManager.BuiltinThemes.find { it.name == decoded.name } != null)
                        errorRuntime(R.string.exception_theme_name_clash)
                    val oldTheme = ThemeManager.getTheme(decoded.name) as? Theme.Custom
                    val newCreated = oldTheme == null
                    var newTheme = if (decoded.backgroundImage != null) {
                        val srcFile = File(dir, decoded.backgroundImage.srcFilePath)
                        val oldSrcFile = oldTheme?.backgroundImage?.srcFilePath?.let { File(it) }
                        val srcFileNameMatches = oldSrcFile?.name == srcFile.name
                        extracted.find { it.name == srcFile.name }
                            // allow overwriting background image files when theme and file names all are same
                            ?.copyTo(srcFile, overwrite = srcFileNameMatches)
                            ?: errorRuntime(R.string.exception_theme_src_image)
                        val croppedFile = File(dir, decoded.backgroundImage.croppedFilePath)
                        val oldCroppedFile =
                            oldTheme?.backgroundImage?.croppedFilePath?.let { File(it) }
                        val croppedFileNameMatches = oldCroppedFile?.name == croppedFile.name
                        extracted.find { it.name == croppedFile.name }
                            ?.copyTo(croppedFile, overwrite = croppedFileNameMatches)
                            ?: errorRuntime(R.string.exception_theme_cropped_image)
                        if (!srcFileNameMatches) {
                            oldSrcFile?.delete()
                        }
                        if (!croppedFileNameMatches) {
                            oldCroppedFile?.delete()
                        }
                        decoded.copy(
                            backgroundImage = decoded.backgroundImage.copy(
                                croppedFilePath = croppedFile.path,
                                srcFilePath = srcFile.path
                            )
                        )
                    } else {
                        decoded
                    }
                    decoded.baiduSkin?.let { importedSkin ->
                        val sourceDir = extracted.find {
                            it.isDirectory && it.name == File(importedSkin.assetDirectoryPath).name
                        } ?: error("Missing Baidu skin assets")
                        val targetDir = File(dir, sourceDir.name)
                        val oldDir = oldTheme?.baiduSkin?.assetDirectoryPath?.let(::File)
                        val sameDirectory = oldDir?.name == targetDir.name
                        sourceDir.copyRecursively(targetDir, overwrite = sameDirectory)
                        if (!sameDirectory) oldDir?.deleteRecursively()
                        newTheme = newTheme.copy(
                            baiduSkin = importedSkin.copy(assetDirectoryPath = targetDir.path)
                        )
                    }
                    saveThemeFiles(newTheme)
                    Triple(newCreated, newTheme, migrated)
                }
            }
        }

    fun importBaiduSkin(
        src: InputStream,
        fallbackName: String
    ): Result<BaiduSkinImportResult> = runCatching {
        val sourceBytes = src.use { it.readBytes() }
        val archive = BdsArchive.read(sourceBytes)
        val converted = BdsSkinConverter.convert(archive, fallbackName)
        val createdFiles = mutableListOf<File>()
        val createdThemes = mutableListOf<Theme.Custom>()
        try {
            val themes = converted.map { variant ->
                val suffix = when {
                    converted.size == 1 -> ""
                    variant.isDark -> appContext.getString(R.string.baidu_skin_dark_suffix)
                    else -> appContext.getString(R.string.baidu_skin_light_suffix)
                }
                val themeName = uniqueThemeName(variant.sourceName + suffix)
                val id = UUID.randomUUID().toString()
                val cropped = File(dir, "$id-bds-panel.png")
                val source = File(dir, "$id-bds-source.png")
                val assetDirectory = File(dir, "$id-bds-assets").apply { mkdirs() }
                cropped.writeBytes(variant.panelPng)
                source.writeBytes(variant.panelPng)
                createdFiles += cropped
                createdFiles += source
                createdFiles += assetDirectory
                val keyVisuals = variant.keyVisuals.mapValues { (key, visual) ->
                    val prefix = key.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    fun write(suffix: String, bytes: ByteArray?): String? = bytes?.let {
                        val name = "$prefix-$suffix.png"
                        File(assetDirectory, name).writeBytes(it)
                        name
                    }
                    Theme.Custom.BaiduSkin.KeyVisual(
                        normalBackground = checkNotNull(write("background", visual.normalBackground)),
                        pressedBackground = write("background-pressed", visual.pressedBackground),
                        normalForeground = write("foreground", visual.normalForeground),
                        pressedForeground = write("foreground-pressed", visual.pressedForeground)
                    )
                }

                val base = if (variant.isDark) ThemePreset.PixelDark else ThemePreset.PixelLight
                val palette = variant.palette
                base.deriveCustomNoBackground(themeName).copy(
                    backgroundImage = Theme.Custom.CustomBackground(
                        croppedFilePath = cropped.path,
                        srcFilePath = source.path,
                        brightness = 100,
                        cropRect = null
                    ),
                    baiduSkin = Theme.Custom.BaiduSkin(
                        assetDirectoryPath = assetDirectory.path,
                        keys = keyVisuals
                    ),
                    backgroundColor = palette.background,
                    barColor = palette.bar,
                    keyboardColor = palette.background,
                    keyBackgroundColor = palette.key,
                    keyTextColor = palette.text,
                    candidateTextColor = palette.text,
                    candidateLabelColor = palette.text,
                    altKeyBackgroundColor = palette.key,
                    altKeyTextColor = palette.text,
                    accentKeyBackgroundColor = palette.accent,
                    popupBackgroundColor = palette.bar,
                    popupTextColor = palette.text,
                    spaceBarColor = palette.key,
                    clipboardEntryColor = palette.key,
                    genericActiveBackgroundColor = palette.accent
                ).also { theme ->
                    saveThemeFiles(theme)
                    createdThemes += theme
                }
            }
            val importedSounds = UserKeySoundPack.importPackBytes(
                appContext,
                converted.first().sourceName,
                sourceBytes
            ).isSuccess
            BaiduSkinImportResult(themes, importedSounds)
        } catch (error: Throwable) {
            createdThemes.forEach { themeFile(it).delete() }
            createdFiles.forEach(File::deleteRecursively)
            throw error
        }
    }

    private fun uniqueThemeName(requested: String): String {
        val base = requested.trim().ifEmpty { appContext.getString(R.string.baidu_skin_default_name) }
        if (ThemeManager.getTheme(base) == null) return base
        var suffix = 2
        while (ThemeManager.getTheme("$base ($suffix)") != null) suffix++
        return "$base ($suffix)"
    }

}
