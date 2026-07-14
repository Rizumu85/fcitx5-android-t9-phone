/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
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
        screen.addPreference(
            Preference(context).apply {
                key = "toolbar_buttons_manage"
                order = -1
                isIconSpaceReserved = false
                isSingleLineTitle = false
                setTitle(R.string.toolbar_buttons)
                setSummary(R.string.toolbar_buttons_summary)
                setOnPreferenceClickListener {
                    showToolbarButtonsDialog()
                    true
                }
            }
        )
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
        screen.addAfter(keyboardPrefs.soundOnKeyPress.key, managePreference)

        val productSettingKeys = setOf(
            keyboardPrefs.hapticOnKeyPress.key,
            keyboardPrefs.soundOnKeyPress.key,
            keyboardPrefs.passwordInputPreview.key,
            keyboardPrefs.t9KeyboardHeightPercent.key,
            "toolbar_buttons_manage",
            "key_sound_pack_manage"
        )
        // The product settings page exposes physical T9 controls only. Full-keyboard geometry and
        // gesture preferences are implementation details of temporary fallback keyboards.
        screen.preferences()
            .filterNot { it.key in productSettingKeys }
            .forEach(screen::removePreference)

        screen.groupPreferences(
            R.string.toolbar,
            listOf("toolbar_buttons_manage")
        )
        screen.groupPreferences(
            R.string.key_feedback,
            listOf(
                keyboardPrefs.hapticOnKeyPress.key,
                keyboardPrefs.soundOnKeyPress.key,
                "key_sound_pack_manage"
            )
        )
        screen.groupPreferences(
            R.string.keyboard_layout,
            listOf(
                keyboardPrefs.passwordInputPreview.key,
                keyboardPrefs.t9KeyboardHeightPercent.key
            )
        )
    }

    private fun PreferenceScreen.groupPreferences(@StringRes title: Int, keys: List<String>) {
        val preferences = keys.mapNotNull(::findPreference)
        if (preferences.isEmpty()) return
        val category = PreferenceCategory(context).apply {
            setTitle(title)
            isIconSpaceReserved = false
        }
        addPreference(category)
        preferences.forEach { preference ->
            removePreference(preference)
            category.addPreference(preference)
        }
    }

    private fun PreferenceScreen.preferences(): List<Preference> =
        (0 until preferenceCount).map(::getPreference)

    private fun showToolbarButtonsDialog() {
        ToolbarButtonsDialog(requireContext(), keyboardPrefs).show()
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
                    setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(8))
                    addView(context.soundVolumeControl())
                    addView(context.soundPreviewControl())
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
    }

    private fun Context.soundVolumeControl() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPaddingRelative(dp(8), dp(4), dp(8), dp(4))
        val value = TextView(context)
        fun updateLabel(progress: Int) {
            value.text = if (progress == 0) {
                getString(R.string.system_default)
            } else {
                "$progress%"
            }
        }
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                setText(R.string.button_sound_volume)
                textAppearance = context.resolveThemeAttribute(android.R.attr.textAppearanceListItem)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(value)
        })
        addView(SeekBar(context).apply {
            max = 100
            progress = keyboardPrefs.soundOnKeyPressVolume.getValue()
            updateLabel(progress)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    updateLabel(progress)
                    if (fromUser) {
                        keyboardPrefs.soundOnKeyPressVolume.setValue(progress)
                        InputFeedbacks.syncSystemPrefs()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })
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
        setPaddingRelative(0, dp(4), 0, dp(4))
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
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
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

    private fun Context.soundPreviewControl() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPaddingRelative(dp(8), dp(4), dp(8), dp(8))
        addView(TextView(context).apply {
            setText(R.string.key_sound_preview)
            textAppearance = resolveThemeAttribute(android.R.attr.textAppearanceListItem)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        listOf(
            Triple(R.string.key_sound_preview_standard, R.drawable.ic_baseline_keyboard_24, InputFeedbacks.SoundEffect.Standard),
            Triple(R.string.key_sound_preview_space, R.drawable.ic_baseline_space_bar_24, InputFeedbacks.SoundEffect.SpaceBar),
            Triple(R.string.key_sound_preview_delete, R.drawable.ic_baseline_backspace_24, InputFeedbacks.SoundEffect.Delete)
        ).forEach { (description, icon, effect) ->
            addView(ImageButton(context).apply {
                setImageDrawable(ContextCompat.getDrawable(context, icon))
                imageTintList = ColorStateList.valueOf(styledColor(android.R.attr.colorControlNormal))
                background = styledDrawable(android.R.attr.selectableItemBackgroundBorderless)
                contentDescription = getString(description)
                setOnClickListener { InputFeedbacks.previewSoundEffect(effect) }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
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
