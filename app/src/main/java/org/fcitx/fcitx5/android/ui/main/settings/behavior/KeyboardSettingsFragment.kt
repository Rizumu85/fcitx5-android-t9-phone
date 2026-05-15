/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.UserKeySoundPack
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fcitx.fcitx5.android.ui.common.withLoadingDialog
import org.fcitx.fcitx5.android.utils.importErrorDialog
import org.fcitx.fcitx5.android.utils.queryFileName
import org.fcitx.fcitx5.android.utils.toast
import splitties.dimensions.dp
import splitties.resources.resolveThemeAttribute
import splitties.resources.styledColor
import splitties.resources.styledDimenPxSize
import splitties.resources.styledDrawable
import splitties.views.textAppearance

class KeyboardSettingsFragment : ManagedPreferenceFragment(AppPrefs.getInstance().keyboard) {

    private val keyboardPrefs = AppPrefs.getInstance().keyboard

    private lateinit var soundPackLauncher: ActivityResultLauncher<String>
    private var pendingPackUri: Uri? = null
    private var pendingPackFileName = ""
    private var packPreference: Preference? = null
    private var importPreference: Preference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soundPackLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            val context = requireContext()
            val fileName = context.contentResolver.queryFileName(uri).orEmpty()
            if (!fileName.endsWith(".bds", ignoreCase = true)) {
                context.toast(R.string.key_sound_pack_wrong_file)
                return@registerForActivityResult
            }
            pendingPackUri = uri
            pendingPackFileName = fileName
            showImportNameDialog(UserKeySoundPack.suggestedName(fileName))
        }
    }

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        val context = screen.context
        val managePreference = Preference(context).apply {
            key = "key_sound_pack_manage"
            isIconSpaceReserved = false
            isSingleLineTitle = false
            setTitle(R.string.key_sound_pack_select)
            summary = UserKeySoundPack.displayNameOrDefault(context)
            setOnPreferenceClickListener {
                showKeySoundPackDialog()
                true
            }
        }
        packPreference = managePreference
        val importPreference = Preference(context).apply {
            key = "key_sound_pack_import"
            isIconSpaceReserved = false
            isSingleLineTitle = false
            setTitle(R.string.key_sound_pack_import)
            setSummary(R.string.key_sound_pack_import_summary)
            setOnPreferenceClickListener {
                soundPackLauncher.launch("*/*")
                true
            }
        }
        this.importPreference = importPreference
        val previewPreference = Preference(context).apply {
            key = "key_sound_preview"
            isIconSpaceReserved = false
            isSingleLineTitle = false
            setTitle(R.string.key_sound_preview)
            setSummary(R.string.key_sound_preview_summary)
            setOnPreferenceClickListener {
                showKeySoundPreviewDialog()
                true
            }
        }
        screen.addAfter(keyboardPrefs.soundOnKeyPressVolume.key, managePreference)
        screen.addAfter(managePreference.key, importPreference)
        screen.addAfter(importPreference.key, previewPreference)
    }

    private fun showImportNameDialog(defaultName: String) {
        val context = requireContext()
        val input = context.keySoundPackNameInput(defaultName)
        AlertDialog.Builder(context)
            .setTitle(R.string.key_sound_pack_import)
            .setMessage(R.string.key_sound_pack_import_message)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString().orEmpty().trim()
                if (name.isEmpty()) {
                    context.toast(R.string.key_sound_pack_name_empty)
                    return@setPositiveButton
                }
                val uri = pendingPackUri ?: return@setPositiveButton
                importKeySoundPack(name, uri, pendingPackFileName)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importKeySoundPack(name: String, uri: Uri, fileName: String) {
        val context = requireContext()
        lifecycleScope.launch {
            lifecycleScope.withLoadingDialog(context) {
                withContext(NonCancellable + Dispatchers.IO) {
                    val result = UserKeySoundPack.importPack(context, name, uri, fileName)
                    withContext(Dispatchers.Main) {
                        val error = result.exceptionOrNull()
                        if (error == null) {
                            InputFeedbacks.syncSystemPrefs()
                            refreshKeySoundPackSummaries(context)
                            context.toast(R.string.key_sound_pack_imported)
                        } else {
                            context.importErrorDialog(error)
                        }
                    }
                }
            }
        }
    }

    private fun showKeySoundPackDialog() {
        val context = requireContext()
        var dialog: AlertDialog? = null
        val content = ScrollView(context).apply {
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, context.dp(8), 0, context.dp(8))
                    UserKeySoundPack.listPacks(context).forEach { pack ->
                        addView(
                            context.soundPackRow(
                                pack,
                                onSelect = {
                                    UserKeySoundPack.selectPack(pack.id)
                                    InputFeedbacks.syncSystemPrefs()
                                    refreshKeySoundPackSummaries(context)
                                    dialog?.dismiss()
                                },
                                onRename = if (pack.isDefault) null else ({
                                    showRenameSoundPackDialog(pack)
                                    dialog?.dismiss()
                                }),
                                onDelete = if (pack.isDefault) null else ({
                                    showDeleteSoundPackDialog(pack)
                                    dialog?.dismiss()
                                })
                            )
                        )
                    }
                }
            )
        }
        dialog = AlertDialog.Builder(context)
            .setTitle(R.string.key_sound_pack_select)
            .setView(content)
            .setPositiveButton(R.string.key_sound_pack_import) { _, _ ->
                soundPackLauncher.launch("*/*")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRenameSoundPackDialog(pack: UserKeySoundPack.Pack) {
        val context = requireContext()
        val input = context.keySoundPackNameInput(pack.name)
        AlertDialog.Builder(context)
            .setTitle(R.string.key_sound_pack_rename)
            .setMessage(R.string.key_sound_pack_rename_message)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString().orEmpty().trim()
                if (name.isEmpty()) {
                    context.toast(R.string.key_sound_pack_name_empty)
                    return@setPositiveButton
                }
                val result = UserKeySoundPack.renamePack(context, pack.id, name)
                val error = result.exceptionOrNull()
                if (error == null) {
                    refreshKeySoundPackSummaries(context)
                    context.toast(R.string.key_sound_pack_renamed)
                } else {
                    context.toast(error.localizedMessage ?: error.message.orEmpty())
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteSoundPackDialog(pack: UserKeySoundPack.Pack) {
        val context = requireContext()
        AlertDialog.Builder(context)
            .setTitle(R.string.key_sound_pack_delete)
            .setMessage(context.getString(R.string.key_sound_pack_delete_message, pack.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                if (UserKeySoundPack.deletePack(pack.id)) {
                    InputFeedbacks.syncSystemPrefs()
                    refreshKeySoundPackSummaries(context)
                    context.toast(R.string.key_sound_pack_deleted)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshKeySoundPackSummaries(context: Context) {
        packPreference?.summary = UserKeySoundPack.displayNameOrDefault(context)
        importPreference?.setSummary(R.string.key_sound_pack_import_summary)
    }

    private fun showKeySoundPreviewDialog() {
        val context = requireContext()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, context.dp(8), 0, context.dp(4))
            addView(
                context.previewRow(
                    R.string.key_sound_preview_standard,
                    R.drawable.ic_baseline_keyboard_24,
                    InputFeedbacks.SoundEffect.Standard
                )
            )
            addView(
                context.previewRow(
                    R.string.key_sound_preview_space,
                    R.drawable.ic_baseline_space_bar_24,
                    InputFeedbacks.SoundEffect.SpaceBar
                )
            )
            addView(
                context.previewRow(
                    R.string.key_sound_preview_delete,
                    R.drawable.ic_baseline_backspace_24,
                    InputFeedbacks.SoundEffect.Delete
                )
            )
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.key_sound_preview)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun Context.soundPackRow(
        pack: UserKeySoundPack.Pack,
        onSelect: () -> Unit,
        onRename: (() -> Unit)?,
        onDelete: (() -> Unit)?
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        background = styledDrawable(android.R.attr.selectableItemBackground)
        minimumHeight = styledDimenPxSize(android.R.attr.listPreferredItemHeightSmall)
        setPaddingRelative(dp(8), dp(10), dp(6), dp(10))
        setOnClickListener { onSelect() }

        addView(
            RadioButton(context).apply {
                isChecked = pack.id == UserKeySoundPack.activePackId
                isClickable = false
            },
            LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        addView(
            TextView(context).apply {
                textAppearance = resolveThemeAttribute(android.R.attr.textAppearanceListItem)
                setTextColor(styledColor(android.R.attr.textColorPrimary))
                text = pack.name
                maxLines = 4
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        if (onRename != null) {
            addView(
                ImageButton(context).apply {
                    setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_baseline_edit_24))
                    imageTintList = ColorStateList.valueOf(styledColor(android.R.attr.colorControlNormal))
                    background = styledDrawable(android.R.attr.selectableItemBackgroundBorderless)
                    contentDescription = context.getString(R.string.key_sound_pack_rename)
                    setOnClickListener { onRename() }
                },
                LinearLayout.LayoutParams(dp(44), dp(48))
            )
        }
        if (onDelete != null) {
            addView(
                ImageButton(context).apply {
                    setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_baseline_delete_24))
                    imageTintList = ColorStateList.valueOf(styledColor(android.R.attr.colorControlNormal))
                    background = styledDrawable(android.R.attr.selectableItemBackgroundBorderless)
                    contentDescription = context.getString(R.string.key_sound_pack_delete)
                    setOnClickListener { onDelete() }
                },
                LinearLayout.LayoutParams(dp(44), dp(48))
            )
        }
    }

    private fun Context.keySoundPackNameInput(defaultName: String) = EditText(this).apply {
        setSingleLine()
        setText(defaultName)
        hint = getString(R.string.key_sound_pack_name_hint)
        setSelectAllOnFocus(true)
        setPaddingRelative(dp(24), dp(10), dp(24), dp(10))
    }

    private fun Context.previewRow(
        @StringRes title: Int,
        @DrawableRes icon: Int,
        effect: InputFeedbacks.SoundEffect
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        background = styledDrawable(android.R.attr.selectableItemBackground)
        minimumHeight = styledDimenPxSize(android.R.attr.listPreferredItemHeightSmall)
        setPaddingRelative(dp(24), dp(10), dp(24), dp(10))
        setOnClickListener {
            InputFeedbacks.previewSoundEffect(effect)
        }

        addView(
            ImageView(context).apply {
                setImageDrawable(ContextCompat.getDrawable(context, icon))
                imageTintList = ColorStateList.valueOf(styledColor(android.R.attr.colorControlNormal))
            },
            LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                rightMargin = dp(24)
            }
        )
        addView(
            TextView(context).apply {
                textAppearance = resolveThemeAttribute(android.R.attr.textAppearanceListItem)
                setTextColor(styledColor(android.R.attr.textColorPrimary))
                setText(title)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
    }

    private fun PreferenceScreen.addAfter(afterKey: String, preference: Preference) {
        val afterIndex = (0 until preferenceCount).firstOrNull {
            getPreference(it).key == afterKey
        } ?: run {
            addPreference(preference)
            return
        }

        val afterOrder = getPreference(afterIndex).order
        if (afterOrder != Int.MAX_VALUE) {
            preference.order = afterOrder + 1
            for (i in 0 until preferenceCount) {
                val existing = getPreference(i)
                if (existing.order > afterOrder) {
                    existing.order = existing.order + 1
                }
            }
        }
        addPreference(preference)
    }
}
