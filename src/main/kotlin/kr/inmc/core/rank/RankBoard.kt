package kr.inmc.core.rank

import org.bukkit.configuration.ConfigurationSection
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * One leaderboard: every player who has played, and the numbers both ranking modes need.
 *
 * Both a best record and a running score are kept for everyone regardless of which mode the
 * game is set to. Storing only the active one would mean flipping a game from 최고기록 to
 * 누적점수 mid-season silently zeroes everybody, and admins do flip it.
 */
class RankBoard {

    /**
     * One player's standing.
     *
     * Every field is `@Volatile` because these are written on the main thread under the entry's
     * own lock but read without one from anywhere - most importantly from PlaceholderAPI, which
     * scoreboard plugins routinely resolve off the main thread. A plain `Long` is not written
     * atomically on every JVM, so `%ng_<game>_my_score%` could otherwise catch half of a value.
     * Volatile is the cheap fix for this shape of access: rare writes, frequent unlocked reads.
     */
    class Entry(val id: UUID) {
        @Volatile var name: String = "?"
        @Volatile var plays: Long = 0
        @Volatile var clears: Long = 0

        /** Best primary value seen; meaningless until [hasRecord]. */
        @Volatile var best: Long = 0
        @Volatile var bestTiebreak: Long = 0
        @Volatile var hasRecord: Boolean = false

        @Volatile var score: Long = 0
        @Volatile var lastPlayAt: Long = 0

        /** Net economy movement, for betting-style games. */
        @Volatile var netMoney: Double = 0.0

        fun valueFor(mode: RankMode): Long = if (mode == RankMode.BEST_RECORD) best else score

        fun eligible(mode: RankMode): Boolean =
            if (mode == RankMode.BEST_RECORD) hasRecord else plays > 0
    }

    private val entries = ConcurrentHashMap<UUID, Entry>()

    val size: Int get() = entries.size

    fun entryOf(id: UUID): Entry? = entries[id]

    fun isEmpty(): Boolean = entries.isEmpty()

    /** Folds one finished game into the board. [cleared] false still counts as a play. */
    fun record(id: UUID, name: String, outcome: Outcome, cleared: Boolean, better: Better) {
        val entry = entries.computeIfAbsent(id) { Entry(it) }
        synchronized(entry) {
            entry.name = name
            entry.plays++
            entry.lastPlayAt = System.currentTimeMillis()
            entry.netMoney += outcome.netMoney
            entry.score += outcome.score
            if (!cleared) return
            entry.clears++
            if (!entry.hasRecord || isBetter(outcome, entry, better)) {
                entry.best = outcome.record
                entry.bestTiebreak = outcome.tiebreak
                entry.hasRecord = true
            }
        }
    }

    /**
     * 이 사람의 값을 **덮어쓴다.** [record] 가 쌓는 것과 정반대다.
     *
     * 밖에서 이미 계산된 현재 상태를 그대로 올릴 때 쓴다 — 도감 점수나 "이 물고기의 최대
     * 크기" 처럼 **사건의 누적이 아니라 시점의 값**인 순위가 있다. 그런 값을 [record] 로
     * 올리면 같은 값을 두 번 올릴 때마다 점수가 배로 뛴다.
     *
     * [plays] 를 1 로 세워두는 이유는 [RankMode.CUMULATIVE_SCORE] 의 [Entry.eligible] 이 그 값을
     * 보기 때문이다. 파생 랭킹에는 "몇 번 했는가"라는 개념이 없다.
     */
    fun put(id: UUID, name: String, score: Long, record: Long = score, tiebreak: Long = 0L) {
        val entry = entries.computeIfAbsent(id) { Entry(it) }
        synchronized(entry) {
            entry.name = name
            entry.score = score
            entry.best = record
            entry.bestTiebreak = tiebreak
            entry.hasRecord = true
            if (entry.plays <= 0) entry.plays = 1
            entry.lastPlayAt = System.currentTimeMillis()
        }
    }

    /** 파생 랭킹에서 자격을 잃은 사람을 뺀다 (도감을 전부 초기화한 경우 등). */
    fun remove(id: UUID): Boolean = entries.remove(id) != null

    private fun isBetter(outcome: Outcome, entry: Entry, better: Better): Boolean {
        val primary = better.compare(outcome.record, entry.best)
        if (primary != 0) return primary < 0
        return outcome.tiebreak < entry.bestTiebreak
    }

    /**
     * The single definition of "who is ahead".
     *
     * Both [sorted] and [rankOf] read it, so the number shown beside a player and the position
     * they occupy in the list can never disagree - which matters because season rewards are paid
     * out by list position, and a leaderboard that says 2위 while the 3위 bracket pays out is a
     * support ticket waiting to happen.
     *
     * The final tiebreak on [Entry.id] looks arbitrary because it is: its only job is to make
     * this a *total* order. Ties on score are common - everyone who clears five rounds has the
     * same points - and without a last resort those entries compare equal, which left their
     * displayed order at the mercy of `ConcurrentHashMap` iteration and could reshuffle them
     * between two refreshes of the same screen.
     */
    fun ordering(mode: RankMode, better: Better): Comparator<Entry> =
        if (mode == RankMode.BEST_RECORD) {
            Comparator<Entry> { a, b -> better.compare(a.best, b.best) }
                .thenBy { it.bestTiebreak }
                .thenBy { it.lastPlayAt }
                .thenBy { it.id }
        } else {
            // Ties on score go to whoever got there first - the later arrival has to beat it.
            compareByDescending<Entry> { it.score }
                .thenBy { it.lastPlayAt }
                .thenBy { it.id }
        }

    /** Best first. Players with nothing to rank on are left out entirely. */
    fun sorted(mode: RankMode, better: Better): List<Entry> =
        entries.values.filter { it.eligible(mode) }.sortedWith(ordering(mode, better))

    fun top(mode: RankMode, better: Better, limit: Int): List<Entry> =
        sorted(mode, better).take(limit.coerceAtLeast(0))

    /**
     * 1-based placing, or null when the player has nothing on this board.
     *
     * Counts how many entries outrank this one rather than sorting the whole board and searching
     * it. That matters because this is the hot path: `%ng_<game>_my_rank%` is resolved by
     * scoreboards once per refresh per player, and the game list calls it once per game shown.
     * Sorting a few thousand entries several times a second was not affordable; counting is.
     *
     * Because [ordering] is a total order, this count is exactly the index the entry would have
     * held in [sorted] - the two answers are the same answer, arrived at more cheaply.
     */
    fun rankOf(id: UUID, mode: RankMode, better: Better): Int? {
        val mine = entries[id] ?: return null
        if (!mine.eligible(mode)) return null

        val ordering = ordering(mode, better)
        var ahead = 0
        for (other in entries.values) {
            if (other === mine || !other.eligible(mode)) continue
            if (ordering.compare(other, mine) < 0) ahead++
        }
        return ahead + 1
    }

    fun clear() {
        entries.clear()
    }

    fun copyEntries(): List<Entry> = entries.values.toList()

    // --- persistence -----------------------------------------------------------

    fun save(section: ConfigurationSection) {
        for ((id, entry) in entries) {
            synchronized(entry) {
                val path = id.toString()
                section.set("$path.name", entry.name)
                section.set("$path.plays", entry.plays)
                section.set("$path.clears", entry.clears)
                section.set("$path.score", entry.score)
                section.set("$path.last", entry.lastPlayAt)
                if (entry.netMoney != 0.0) section.set("$path.net-money", entry.netMoney)
                if (entry.hasRecord) {
                    section.set("$path.best", entry.best)
                    section.set("$path.best-tiebreak", entry.bestTiebreak)
                }
            }
        }
    }

    companion object {
        fun load(section: ConfigurationSection?): RankBoard {
            val board = RankBoard()
            if (section == null) return board
            for (raw in section.getKeys(false)) {
                val id = runCatching { UUID.fromString(raw) }.getOrNull() ?: continue
                val entry = Entry(id)
                entry.name = section.getString("$raw.name") ?: "?"
                entry.plays = section.getLong("$raw.plays", 0L)
                entry.clears = section.getLong("$raw.clears", 0L)
                entry.score = section.getLong("$raw.score", 0L)
                entry.lastPlayAt = section.getLong("$raw.last", 0L)
                entry.netMoney = section.getDouble("$raw.net-money", 0.0)
                if (section.contains("$raw.best")) {
                    entry.best = section.getLong("$raw.best", 0L)
                    entry.bestTiebreak = section.getLong("$raw.best-tiebreak", 0L)
                    entry.hasRecord = true
                }
                board.entries[id] = entry
            }
            return board
        }
    }
}
