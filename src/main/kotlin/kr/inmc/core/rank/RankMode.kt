package kr.inmc.core.rank

/** Which number a leaderboard sorts on. Chosen per game in the admin dialog. */
enum class RankMode(val display: String, val help: String) {

    /**
     * The single best result of the season. Skill-first: playing more does not move you up,
     * only playing *better* does.
     */
    BEST_RECORD("최고 기록", "시즌 중 세운 가장 좋은 기록 하나로 순위를 매깁니다."),

    /**
     * Points accumulated across every play of the season. Participation-first, and the only
     * mode that makes sense for games with no single "result" (speed rounds, betting).
     */
    CUMULATIVE_SCORE("누적 점수", "플레이할 때마다 점수를 쌓아 합계로 순위를 매깁니다.");

    companion object {
        fun parse(raw: String?): RankMode =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: BEST_RECORD
    }
}

/** Direction of "better" for [RankMode.BEST_RECORD]. */
enum class Better {
    /** Fewer attempts, less time. */
    LOWER,

    /** More correct answers, longer streak. */
    HIGHER;

    /** Negative when [a] outranks [b]. */
    fun compare(a: Long, b: Long): Int = if (this == LOWER) a.compareTo(b) else b.compareTo(a)
}
