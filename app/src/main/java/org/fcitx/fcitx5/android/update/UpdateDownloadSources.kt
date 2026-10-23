/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.update

import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * GitHub is always tried first, so users whose network or proxy reaches it are unaffected.
 * Public GitHub proxies are only a fallback for networks where GitHub fails or trickles. They are
 * free third-party services that can disappear at any time, so every file fetched through them
 * must be checked against a SHA-256 obtained from GitHub or pinned in the app.
 */
object UpdateDownloadSources {
    /** Prefix proxies that accept a full `https://github.com/...` URL appended to the host. */
    internal val MirrorPrefixes = listOf(
        "https://gh-proxy.com/",
        "https://gh.llkk.cc/",
        "https://ghfast.top/",
        "https://ghproxy.net/"
    )

    /** The subset that also proxies `https://api.github.com/...`. */
    internal val ApiMirrorPrefixes = listOf(
        "https://gh-proxy.com/",
        "https://gh.llkk.cc/"
    )

    private const val GithubPrefix = "https://github.com/"
    private const val ProbeBytes = 256 * 1024
    private const val ProbeTimeoutMillis = 4_000L
    private const val MirrorProbeTimeoutMillis = 6_000L

    fun candidates(url: String): List<String> =
        listOf(url) + mirrorUrls(url)

    private fun mirrorUrls(url: String): List<String> =
        if (url.startsWith(GithubPrefix)) MirrorPrefixes.map { it + url } else emptyList()

    /**
     * Picks the URL for a long-running system download. Blocks for up to about ten seconds, so it
     * must run off the main thread. Falls back to the original URL when nothing answers, which
     * preserves the previous behavior of letting DownloadManager report the failure.
     */
    fun select(url: String, allowMirrors: Boolean): String {
        if (probe(url, ProbeTimeoutMillis)) return url
        val mirrors = if (allowMirrors) mirrorUrls(url) else emptyList()
        if (mirrors.isEmpty()) return url
        val executor = Executors.newFixedThreadPool(mirrors.size)
        try {
            val completion = ExecutorCompletionService<String?>(executor)
            mirrors.forEach { mirror ->
                completion.submit { mirror.takeIf { probe(it, MirrorProbeTimeoutMillis) } }
            }
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MirrorProbeTimeoutMillis + 1_000)
            repeat(mirrors.size) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) return url
                val winner = completion.poll(remaining, TimeUnit.NANOSECONDS)?.get() ?: return@repeat
                Timber.i("GitHub download is unavailable or slow; using mirror ${winner.substringBefore(GithubPrefix)}")
                return winner
            }
            return url
        } finally {
            executor.shutdownNow()
        }
    }

    /**
     * A source is usable when it streams the first [ProbeBytes] within the timeout. Connecting
     * alone is not enough: throttled GitHub connections often open and then stall.
     */
    internal fun probe(url: String, timeoutMillis: Long): Boolean = runCatching {
        val started = System.nanoTime()
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = timeoutMillis.toInt()
            connection.readTimeout = timeoutMillis.toInt()
            connection.setRequestProperty("Range", "bytes=0-${ProbeBytes - 1}")
            connection.setRequestProperty("User-Agent", "Fcitx5-Android-T9-Updater")
            if (connection.responseCode !in 200..299) return@runCatching false
            val deadline = started + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            var total = 0
            connection.inputStream.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (total < ProbeBytes) {
                    if (System.nanoTime() > deadline) return@runCatching false
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                }
            }
            total >= ProbeBytes || connection.contentLengthLong in 1..total.toLong()
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    /** Fetches GitHub API JSON, then retries through API-capable mirrors. */
    fun fetchApi(url: String, fetch: (String) -> String): ApiPayload {
        val direct = runCatching { fetch(url) }
        direct.getOrNull()?.let { return ApiPayload(it, viaMirror = false) }
        ApiMirrorPrefixes.forEach { prefix ->
            runCatching { fetch(prefix + url) }.getOrNull()?.let {
                return ApiPayload(it, viaMirror = true)
            }
        }
        throw direct.exceptionOrNull() ?: IllegalStateException("Unable to fetch $url")
    }

    data class ApiPayload(val json: String, val viaMirror: Boolean)
}
