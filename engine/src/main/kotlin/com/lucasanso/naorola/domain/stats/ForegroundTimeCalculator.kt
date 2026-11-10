package com.lucasanso.naorola.domain.stats

/** The usage events that matter for foreground time (mapped from Android's UsageEvents). */
enum class UsageEventType { RESUMED, PAUSED, DEVICE_SHUTDOWN }

data class UsageEventRecord(
    val timeMillis: Long,
    val packageName: String,
    val activity: String?,
    val type: UsageEventType,
)

/**
 * Total time each app was in the foreground inside [from, to), computed from activity
 * resume/pause events (more precise than Android's daily buckets, which straddle midnight).
 *
 * This is time INSIDE the app. Time spent on short videos is a different number, measured by
 * the detector itself.
 */
object ForegroundTimeCalculator {

    fun compute(events: List<UsageEventRecord>, from: Long, to: Long, packages: Set<String>): Map<String, Long> {
        if (to <= from) return packages.associateWith { 0L }
        val totals = packages.associateWithTo(HashMap()) { 0L }
        val resumed = HashMap<String, MutableSet<String>>()
        val foregroundSince = HashMap<String, Long>()
        val seen = HashSet<String>()

        fun close(pkg: String, at: Long) {
            val since = foregroundSince.remove(pkg) ?: return
            val end = at.coerceAtMost(to)
            if (end > since) totals[pkg] = (totals[pkg] ?: 0L) + (end - since)
        }

        for (e in events.sortedBy { it.timeMillis }) {
            if (e.timeMillis >= to) break
            if (e.type == UsageEventType.DEVICE_SHUTDOWN) {
                foregroundSince.keys.toList().forEach { close(it, e.timeMillis) }
                resumed.clear()
                continue
            }
            val pkg = e.packageName
            if (pkg !in packages) continue
            val activities = resumed.getOrPut(pkg) { HashSet() }
            val key = e.activity ?: pkg
            when (e.type) {
                UsageEventType.RESUMED -> {
                    if (activities.isEmpty()) foregroundSince[pkg] = e.timeMillis.coerceAtLeast(from)
                    activities += key
                }
                UsageEventType.PAUSED -> {
                    if (activities.isEmpty() && pkg !in seen && e.timeMillis > from) {
                        // Paused without a resume in the window: it was already open when the window began.
                        foregroundSince[pkg] = from
                        close(pkg, e.timeMillis)
                    } else {
                        activities -= key
                        if (activities.isEmpty()) close(pkg, e.timeMillis)
                    }
                }
                UsageEventType.DEVICE_SHUTDOWN -> Unit
            }
            seen += pkg
        }
        // Still in the foreground at the end of the window.
        foregroundSince.keys.toList().forEach { close(it, to) }
        return totals
    }
}
