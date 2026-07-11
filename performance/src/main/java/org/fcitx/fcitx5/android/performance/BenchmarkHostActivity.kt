/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.performance

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

class BenchmarkHostActivity : Activity() {
    private lateinit var editor: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_benchmark_host)
        editor = findViewById(R.id.benchmark_editor)
        resetEditorAndShowIme()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resetEditorAndShowIme()
    }

    private fun resetEditorAndShowIme() {
        editor.text.clear()
        editor.post {
            editor.requestFocus()
            val inputMethodManager = getSystemService(InputMethodManager::class.java)
            inputMethodManager.restartInput(editor)
            inputMethodManager.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}
