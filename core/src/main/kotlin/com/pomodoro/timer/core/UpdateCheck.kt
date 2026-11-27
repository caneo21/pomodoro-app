package com.pomodoro.timer.core

import kotlin.coroutines.cancellation.CancellationException

/** A release version `MAJOR.MINOR.PATCH`, as used in `vX.Y.Z` release tags. */
data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int =
        compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

        /** Parses `v1.2.3` or `1.2.3`; anything else (pre-release suffixes, dev builds) is null. */
        fun parse(text: String?): SemVer? {
            val match = PATTERN.matchEntire(text ?: return null) ?: return null
            val (major, minor, patch) = match.destructured
            return SemVer(
                major.toIntOrNull() ?: return null,
                minor.toIntOrNull() ?: return null,
                patch.toIntOrNull() ?: return null,
            )
        }
    }
}

/** The newest published release: its tag (e.g. `v1.2.0`) and the page to download it from. */
data class LatestRelease(val tag: String, val url: String)

data class StoredUpdateInfo(
    val lastCheckedAtMillis: Long = 0L,
    val latestTag: String? = null,
    val latestUrl: String? = null,
    val dismissedTag: String? = null,
)

object UpdatePolicy {
    const val CHECK_INTERVAL_MILLIS: Long = 86_400_000L

    fun isNewer(latestTag: String?, installedVersion: String): Boolean {
        val latest = SemVer.parse(latestTag) ?: return false
        val installed = SemVer.parse(installedVersion) ?: return false
        return latest > installed
    }

    /**
     * Due if never checked (0), once a day after that, and also when the stored time is in the
     * future (the clock moved back), so a bad clock can't block checks forever.
     */
    fun shouldCheck(lastCheckedAtMillis: Long, nowMillis: Long): Boolean =
        lastCheckedAtMillis == 0L ||
            nowMillis < lastCheckedAtMillis ||
            nowMillis - lastCheckedAtMillis >= CHECK_INTERVAL_MILLIS

    fun shouldShow(latestTag: String?, installedVersion: String, dismissedTag: String?): Boolean =
        latestTag != dismissedTag && isNewer(latestTag, installedVersion)
}

interface UpdateInfoStore {
    suspend fun load(): StoredUpdateInfo
    suspend fun saveCheckResult(nowMillis: Long, release: LatestRelease)
    suspend fun saveDismissed(tag: String)
}

/** Returns the latest release, or null on any failure (offline, rate-limited, bad response). */
fun interface ReleaseFetcher {
    suspend fun fetchLatest(): LatestRelease?
}

class UpdateCoordinator(
    private val store: UpdateInfoStore,
    private val fetcher: ReleaseFetcher,
    private val installedVersion: String,
    private val nowMillis: () -> Long,
) {
    /** Checks GitHub if a check is due, then returns the release to tell the user about, or null. */
    suspend fun refresh(): LatestRelease? {
        // Dev builds ("0.0.0-dev") can't be compared, so they never check or show anything.
        SemVer.parse(installedVersion) ?: return null

        var info = store.load()
        val now = nowMillis()
        if (UpdatePolicy.shouldCheck(info.lastCheckedAtMillis, now)) {
            val fetched = try {
                fetcher.fetchLatest()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (fetched != null) {
                store.saveCheckResult(now, fetched)
                info = store.load()
            }
        }

        val tag = info.latestTag ?: return null
        val url = info.latestUrl ?: return null
        return if (UpdatePolicy.shouldShow(tag, installedVersion, info.dismissedTag)) LatestRelease(tag, url) else null
    }

    suspend fun dismiss(tag: String) = store.saveDismissed(tag)
}
