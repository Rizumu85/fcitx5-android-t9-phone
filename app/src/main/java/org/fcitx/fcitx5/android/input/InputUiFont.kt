/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.widget.TextView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import java.io.File

object InputUiFont {
    const val SystemDefaultValue = ""

    private const val FontFilePrefix = "font-file:"
    private const val CustomFontsDirName = "fonts"
    private const val PublicFontsDirName = "Fonts"
    private const val SourceAppFonts = "app fonts"
    private const val SourcePublicFonts = "public Fonts"

    private val fontExtensions = setOf("ttf", "otf", "ttc")

    private data class FontFileEntry(
        val file: File,
        val source: String
    )

    fun customFontPreferenceEntries(context: Context): List<Pair<String, CharSequence>> {
        val entries = mutableListOf(SystemDefaultValue to context.getString(R.string.system_default))
        entries += customFontFiles(context).map { fontFileValue(it.file) to fontLabel(it) }
        return entries.distinctBy { it.first }.sortedWith(
            compareBy<Pair<String, CharSequence>> { it.first.isNotEmpty() }
                .thenBy { it.second.toString().lowercase() }
        )
    }

    private fun selectedTypeface(): Typeface? {
        val value = AppPrefs.getInstance().keyboard.inputUiFont.getValue()
        if (value == SystemDefaultValue || !value.startsWith(FontFilePrefix)) return null
        return typefaceFromFontFileValue(value)
    }

    fun applyTo(view: TextView, style: Int = Typeface.NORMAL) {
        view.setTypeface(selectedTypeface(), style)
    }

    fun applyWeightTo(
        view: TextView,
        weight: Int,
        italic: Boolean = false,
        fallbackStyle: Int = Typeface.BOLD
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.typeface = Typeface.create(selectedTypeface() ?: Typeface.DEFAULT, weight, italic)
        } else {
            applyTo(view, fallbackStyle)
        }
    }

    private fun customFontFiles(context: Context): List<FontFileEntry> {
        val appFontsDir = (context.getExternalFilesDir(null) ?: context.filesDir)
            .resolve(CustomFontsDirName)
            .also { it.mkdirs() }
        val publicFontsDir = Environment.getExternalStorageDirectory().resolve(PublicFontsDirName)
        return listOf(
            appFontsDir to SourceAppFonts,
            publicFontsDir to SourcePublicFonts
        )
            .flatMap { (dir, source) -> fontFilesIn(dir).map { FontFileEntry(it, source) } }
            .sortedBy { it.file.name.lowercase() }
    }

    private fun fontFilesIn(dir: File): List<File> {
        return runCatching {
            dir.listFiles { file ->
                file.isFile && file.extension.lowercase() in fontExtensions
            }?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun fontLabel(entry: FontFileEntry): String {
        return "${entry.file.nameWithoutExtension} (${entry.source})"
    }

    private fun fontFileValue(file: File): String {
        return "$FontFilePrefix${file.absolutePath}"
    }

    private fun typefaceFromFontFileValue(value: String): Typeface? {
        return runCatching {
            Typeface.createFromFile(File(value.removePrefix(FontFilePrefix)))
        }.getOrNull()
    }
}
