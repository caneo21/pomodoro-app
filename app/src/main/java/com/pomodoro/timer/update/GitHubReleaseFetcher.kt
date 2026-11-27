package com.pomodoro.timer.update

import com.pomodoro.timer.core.LatestRelease
import com.pomodoro.timer.core.ReleaseFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

const val LATEST_RELEASE_URL = "https://api.github.com/repos/caneo21/pomodoro-app/releases/latest"

private const val TIMEOUT_MILLIS = 10_000

/** Reads `tag_name` and `html_url` from GitHub's latest-release JSON; null if either is missing. */
internal fun parseLatestRelease(json: String): LatestRelease? = try {
    val obj = JSONObject(json)
    val tag = obj.optString("tag_name")
    val url = obj.optString("html_url")
    if (tag.isNotEmpty() && url.startsWith("https://")) LatestRelease(tag, url) else null
} catch (e: Exception) {
    null
}

/** Asks GitHub for the latest published release. Any failure (offline, 403, 404, bad body) is null. */
class GitHubReleaseFetcher(private val url: String = LATEST_RELEASE_URL) : ReleaseFetcher {

    override suspend fun fetchLatest(): LatestRelease? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                setRequestProperty("User-Agent", "PomodoroApp-UpdateCheck")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            parseLatestRelease(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
