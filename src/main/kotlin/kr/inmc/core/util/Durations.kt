package kr.inmc.core.util

/**
 * Parses and formats the `1d 1h 1m 1s` duration syntax used throughout the config
 * and the GUI. Everything is kept in whole seconds - the plugin never needs finer
 * granularity than one tick of 각 플러그인의 Ticker.
 */
object Durations {

    private val TOKEN = Regex("""(\d+)\s*([dhms])""", RegexOption.IGNORE_CASE)

    /**
     * Accepts `1d 1h 1m 1s`, `90m`, `3600`, or any subset/ordering of those.
     * A bare number is read as seconds. Returns [fallback] when nothing parses.
     */
    fun parse(raw: String?, fallback: Long = 0L): Long {
        if (raw.isNullOrBlank()) return fallback
        val text = raw.trim()

        text.toLongOrNull()?.let { return if (it < 0) fallback else it }

        var total = 0L
        var matched = false
        for (m in TOKEN.findAll(text)) {
            val value = m.groupValues[1].toLongOrNull() ?: continue
            val unit = m.groupValues[2].lowercase()
            total += when (unit) {
                "d" -> value * 86400L
                "h" -> value * 3600L
                "m" -> value * 60L
                else -> value
            }
            matched = true
        }
        return if (matched) total else fallback
    }

    /** Renders seconds back into the canonical `1d 1h 1m 1s` form. */
    fun format(seconds: Long): String {
        if (seconds <= 0L) return "0s"
        val d = seconds / 86400L
        val h = (seconds % 86400L) / 3600L
        val m = (seconds % 3600L) / 60L
        val s = seconds % 60L
        return buildList {
            if (d > 0) add("${d}d")
            if (h > 0) add("${h}h")
            if (m > 0) add("${m}m")
            if (s > 0) add("${s}s")
        }.joinToString(" ")
    }

    /** Short human readable form for GUI lore and the tracking action bar. */
    fun formatShort(seconds: Long): String {
        if (seconds <= 0L) return "0초"
        val d = seconds / 86400L
        val h = (seconds % 86400L) / 3600L
        val m = (seconds % 3600L) / 60L
        val s = seconds % 60L
        return buildList {
            if (d > 0) add("${d}일")
            if (h > 0) add("${h}시간")
            if (m > 0) add("${m}분")
            if (s > 0) add("${s}초")
        }.joinToString(" ")
    }
}
