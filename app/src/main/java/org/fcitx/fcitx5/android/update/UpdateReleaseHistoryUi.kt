/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import org.fcitx.fcitx5.android.R
import splitties.dimensions.dp
import splitties.resources.styledColor

internal class UpdateReleaseHistoryUi(
    context: Context,
    releases: List<UpdateRelease>,
    latestArtifacts: List<UpdateArtifact>,
    onDownload: (UpdateArtifact) -> Unit
) : LinearLayout(context) {
    private val pageIndicator = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(context.styledColor(android.R.attr.textColorSecondary))
        textSize = 12f
        setPadding(0, context.dp(6), 0, context.dp(8))
    }

    init {
        orientation = VERTICAL
        setPadding(context.dp(20), 0, context.dp(20), 0)

        val pager = ViewPager2(context).apply {
            offscreenPageLimit = 1
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                releasePagerHeight(context)
            )
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateIndicator(position, releases.size)
                }
            })
        }
        addView(pager)
        pager.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            private var initialPageAligned = false

            override fun onPreDraw(): Boolean {
                val recycler = pager.getChildAt(0) as RecyclerView
                if (pager.adapter == null && recycler.width > 0) {
                    // Create pages only after the dialog has resolved its final viewport. Doing so
                    // before measurement lets short notes influence ViewPager2's first page width,
                    // which can expose a neighboring version or move the initial snap position.
                    pager.adapter = ReleaseAdapter(releases)
                    return false
                }
                if (recycler.childCount == 0) {
                    return false
                }
                if (!initialPageAligned) {
                    // The first item is attached in the same layout pass that establishes the
                    // dialog width. Align it explicitly before drawing so ViewPager2 cannot retain
                    // the provisional horizontal offset from that pass.
                    (recycler.layoutManager as LinearLayoutManager)
                        .scrollToPositionWithOffset(0, 0)
                    initialPageAligned = true
                    return false
                }
                pager.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        addView(pageIndicator, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        updateIndicator(0, releases.size)

        addView(View(context).apply {
            setBackgroundColor(
                ColorUtils.setAlphaComponent(
                    context.styledColor(android.R.attr.textColorSecondary),
                    52
                )
            )
        }, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(1)))

        latestArtifacts.forEach { artifact ->
            addView(downloadRow(artifact, onDownload))
        }
    }

    private fun updateIndicator(position: Int, pageCount: Int) {
        pageIndicator.text = if (pageCount > 1) {
            context.getString(
                R.string.update_release_page_indicator,
                position + 1,
                pageCount
            )
        } else {
            context.getString(R.string.update_release_single_page)
        }
    }

    private fun downloadRow(
        artifact: UpdateArtifact,
        onDownload: (UpdateArtifact) -> Unit
    ) = TextView(context).apply {
        text = artifact.downloadLabel(context)
        gravity = Gravity.CENTER_VERTICAL
        minHeight = context.dp(48)
        textSize = 14f
        setTextColor(context.styledColor(android.R.attr.textColorPrimary))
        setPadding(context.dp(4), 0, context.dp(4), 0)
        setBackgroundResource(context.selectableItemBackground())
        setOnClickListener { onDownload(artifact) }
    }

    private class ReleaseAdapter(
        private val releases: List<UpdateRelease>
    ) : RecyclerView.Adapter<ReleaseViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ReleaseViewHolder(parent.context)

        override fun getItemCount(): Int = releases.size

        override fun onBindViewHolder(holder: ReleaseViewHolder, position: Int) {
            holder.bind(releases[position], latest = position == 0)
        }
    }

    private class ReleaseViewHolder(context: Context) : RecyclerView.ViewHolder(
        object : LinearLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val viewportWidth = (parent as? RecyclerView)?.width
                    ?.takeIf { it > 0 }
                    ?: View.MeasureSpec.getSize(widthMeasureSpec)
                // ViewPager2's horizontal RecyclerView offers pages an at-most width. Release
                // notes vary greatly, so force every page to the stable viewport width instead of
                // letting short notes expose adjacent versions or change the swipe distance.
                super.onMeasure(
                    View.MeasureSpec.makeMeasureSpec(viewportWidth, View.MeasureSpec.EXACTLY),
                    heightMeasureSpec
                )
            }
        }.apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = VERTICAL
        }
    ) {
        private val container = itemView as LinearLayout
        private val title = TextView(context).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.styledColor(android.R.attr.textColorPrimary))
            setPadding(0, context.dp(4), 0, context.dp(8))
        }
        private val notes = TextView(context).apply {
            textSize = 14f
            setLineSpacing(0f, 1.18f)
            setTextColor(context.styledColor(android.R.attr.textColorPrimary))
            linksClickable = true
        }

        init {
            container.addView(
                title,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            container.addView(
                ScrollView(context).apply {
                    isFillViewport = true
                    addView(
                        notes,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }

        fun bind(release: UpdateRelease, latest: Boolean) {
            val channel = when (release.channel) {
                UpdateReleaseChannel.APP -> R.string.update_release_app_channel
                UpdateReleaseChannel.RIME_CONFIG -> R.string.update_release_rime_config_channel
            }
            title.text = buildString {
                append(itemView.context.getString(channel, release.version))
                if (latest) {
                    append(" · ")
                    append(itemView.context.getString(R.string.update_release_latest))
                }
            }
            notes.text = if (release.notes.isBlank()) {
                itemView.context.getString(R.string.update_release_notes_empty)
            } else {
                ReleaseNotesFormatter.format(release.notes)
            }
            Linkify.addLinks(notes, Linkify.WEB_URLS)
        }
    }

    private companion object {
        fun releasePagerHeight(context: Context): Int {
            val displayHeight = context.resources.displayMetrics.heightPixels
            return (displayHeight * 0.34f).toInt().coerceIn(context.dp(150), context.dp(300))
        }

        fun UpdateArtifact.downloadLabel(context: Context): String = when (component) {
            UpdateComponent.APP -> context.getString(R.string.download_app_update, version)
            UpdateComponent.RIME_PLUGIN ->
                context.getString(R.string.download_rime_plugin_update, version)
            UpdateComponent.RIME_CONFIG ->
                context.getString(R.string.download_rime_config_update, version)
        }

        fun Context.selectableItemBackground(): Int {
            val value = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
            return value.resourceId
        }
    }
}

internal object ReleaseNotesFormatter {
    private val MarkdownLink = Regex("""\[([^]]+)]\(([^)]+)\)""")

    fun format(markdown: String): CharSequence {
        val output = SpannableStringBuilder()
        markdown.trim().lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.matches(Regex("^-{3,}$"))) return@forEach
            val headingLevel = line.takeWhile { it == '#' }.length.takeIf { it in 1..6 }
            val content = when {
                headingLevel != null -> line.drop(headingLevel).trimStart()
                line.startsWith("- ") -> "• ${line.drop(2)}"
                else -> line
            }.cleanInlineMarkdown()
            val start = output.length
            output.append(content)
            if (headingLevel != null && content.isNotEmpty()) {
                output.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    output.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                output.setSpan(
                    RelativeSizeSpan(if (headingLevel <= 2) 1.08f else 1f),
                    start,
                    output.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            output.append('\n')
        }
        while (output.endsWith("\n")) output.delete(output.length - 1, output.length)
        return output
    }

    private fun String.cleanInlineMarkdown(): String =
        replace(MarkdownLink) { match -> "${match.groupValues[1]} (${match.groupValues[2]})" }
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
}
