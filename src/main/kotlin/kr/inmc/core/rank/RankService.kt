package kr.inmc.core.rank

import kr.inmc.core.reward.RewardHost
import kr.inmc.core.rank.Rankable
import kr.inmc.core.rank.Outcome

import kr.inmc.core.rank.RankBoard
import kr.inmc.core.rank.RankMode
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Leaderboards, seasons and the settlement that connects them.
 *
 * Two boards are kept per game. The **season** board is what the reset wipes and what rank
 * rewards pay out on; the **all-time** board never resets, so a game with a weekly season still
 * has a hall of fame.
 *
 * The reset clock is stored as an absolute [GameBoards.nextResetAt] rather than recomputed from
 * "last reset plus a week". That is what makes downtime safe: [tick] fires at most one reset per
 * pass and then asks the schedule for the next instant strictly after *now*, so a server that was
 * off for a fortnight settles once, not fourteen times.
 */
class RankService(private val ng: RewardHost) {

    class GameBoards(val gameId: String) {
        var season: RankBoard = RankBoard()
        var allTime: RankBoard = RankBoard()
        var seasonNumber: Int = 1
        var nextResetAt: Long = 0L
        var lastResetAt: Long = 0L
    }

    /** A closed season, kept for the "지난 시즌" screen. */
    class SeasonRecord(
        val season: Int,
        val closedAt: Long,
        val mode: RankMode,
        val places: List<Place>,
    ) {
        class Place(val rank: Int, val name: String, val value: Long, val plays: Long)
    }

    private val boards = ConcurrentHashMap<String, GameBoards>()
    private val archive = ConcurrentHashMap<String, MutableList<SeasonRecord>>()

    @Volatile
    private var dirty = false

    fun boardsOf(gameId: String): GameBoards =
        boards.computeIfAbsent(gameId.lowercase()) { GameBoards(gameId.lowercase()) }

    fun seasonsOf(gameId: String): List<SeasonRecord> =
        archive[gameId.lowercase()]?.toList().orEmpty().sortedByDescending { it.season }

    // --- recording -------------------------------------------------------------

    fun record(player: Player, def: Rankable, outcome: Outcome, cleared: Boolean) {
        recordOffline(player.uniqueId, player.name, def, outcome, cleared)
    }

    /**
     * Same as [record] for someone who is not online.
     *
     * A scheduled lottery draw settles everyone who bought a ticket, and most of them will be
     * asleep - leaving them off the board because they happened not to be logged in at 4am would
     * make the leaderboard a record of who keeps odd hours.
     */
    fun recordOffline(
        playerId: UUID,
        name: String,
        def: Rankable,
        outcome: Outcome,
        cleared: Boolean,
    ) {
        val gb = boardsOf(def.id)
        gb.season.record(playerId, name, outcome, cleared, def.better)
        gb.allTime.record(playerId, name, outcome, cleared, def.better)
        ensureSchedule(def, System.currentTimeMillis())
        dirty = true
    }

    /**
     * **파생 랭킹**의 값을 올린다. [record] 와 달리 누적하지 않고 덮어쓴다.
     *
     * 어떤 순위는 사건의 누적이 아니라 **지금 상태**다 — 도감 점수, 보유 트로피 수, "이
     * 물고기의 최대 크기" 같은 것들. 그런 값은 언제 몇 번 계산하든 같은 결과가 나와야 하고,
     * [record] 로 올리면 계산할 때마다 점수가 불어난다.
     *
     * 보드를 여러 개 두고 싶으면 [Rankable.id] 를 다르게 주면 된다 — 보드는 문자열 키로
     * 나뉘므로 "물고기별 최대 크기 보드 50개" 같은 것도 따로 장치가 필요 없다.
     */
    fun put(
        def: Rankable,
        playerId: UUID,
        name: String,
        score: Long,
        record: Long = score,
        tiebreak: Long = 0L,
    ) {
        val gb = boardsOf(def.id)
        gb.season.put(playerId, name, score, record, tiebreak)
        gb.allTime.put(playerId, name, score, record, tiebreak)
        ensureSchedule(def, System.currentTimeMillis())
        dirty = true
    }

    /** 파생 랭킹에서 한 사람을 뺀다. */
    fun drop(def: Rankable, playerId: UUID) {
        val gb = boardsOf(def.id)
        val removed = gb.season.remove(playerId) or gb.allTime.remove(playerId)
        if (removed) dirty = true
    }

    fun rankOf(def: Rankable, id: UUID, allTime: Boolean = false): Int? {
        val gb = boardsOf(def.id)
        val board = if (allTime) gb.allTime else gb.season
        return board.rankOf(id, def.ranking.mode, def.better)
    }

    fun top(def: Rankable, limit: Int, allTime: Boolean = false): List<RankBoard.Entry> {
        val gb = boardsOf(def.id)
        val board = if (allTime) gb.allTime else gb.season
        return board.top(def.ranking.mode, def.better, limit)
    }

    /** Formats a board value the way that game measures things. */
    fun formatValue(def: Rankable, entry: RankBoard.Entry): String =
        if (def.ranking.mode == RankMode.BEST_RECORD) {
            entry.best.toString() + def.recordUnit
        } else {
            String.format("%,d", entry.score) + "점"
        }

    // --- reset clock -----------------------------------------------------------

    /** Makes sure a game has a pending reset instant, and recomputes it if the policy changed. */
    fun ensureSchedule(def: Rankable, now: Long) {
        val gb = boardsOf(def.id)
        val policy = def.ranking.reset.policy
        if (!policy.automatic) {
            if (gb.nextResetAt != 0L) {
                gb.nextResetAt = 0L
                dirty = true
            }
            return
        }
        if (gb.nextResetAt <= 0L) {
            gb.nextResetAt = def.ranking.reset.nextAfter(now)
            dirty = true
        }
    }

    /** Called after the admin edits a schedule, so the new setting takes effect immediately. */
    fun rescheduleNow(def: Rankable) {
        val gb = boardsOf(def.id)
        gb.nextResetAt = def.ranking.reset.nextAfter(System.currentTimeMillis())
        dirty = true
    }

    fun nextResetAt(def: Rankable): Long = boardsOf(def.id).nextResetAt

    /** Ticker hook. At most one season closes per game per pass. */
    fun tick(now: Long) {
        for (def in ng.rankables) {
            ensureSchedule(def, now)
            val gb = boardsOf(def.id)
            if (gb.nextResetAt <= 0L || now < gb.nextResetAt) continue
            closeSeason(def, automatic = true)
        }
    }

    // --- settlement ------------------------------------------------------------

    data class SettleResult(val season: Int, val rewarded: Int, val ranked: Int)

    /**
     * Ends the current season: pays rank rewards, archives the standings, wipes the board.
     *
     * Rewards are rolled here, once, and then either handed over or mailed. Everyone who placed
     * the same gets the same roll outcome shape, and being offline changes nothing.
     */
    fun closeSeason(def: Rankable, automatic: Boolean): SettleResult {
        val gb = boardsOf(def.id)
        val now = System.currentTimeMillis()
        val standings = gb.season.sorted(def.ranking.mode, def.better)
        val eligible = standings.filter { it.plays >= def.ranking.minPlays }

        var rewarded = 0
        eligible.forEachIndexed { index, entry ->
            val rank = index + 1
            val bracket = def.rewards.bracketFor(rank) ?: return@forEachIndexed
            if (bracket.bundle.isEmpty()) return@forEachIndexed

            val source = def.displayName + " · 시즌 " + gb.seasonNumber + " · " + rank + "위"
            val payout = ng.rewards.resolve(bracket.bundle)
            if (payout.isEmpty()) return@forEachIndexed

            val online = Bukkit.getPlayer(entry.id)
            if (online != null) {
                ng.rewards.give(online, payout, def.displayName, source)
                ng.tell(
                    online, "rank-reward-received",
                    ng.placeholders("subject" to def.displayName, "rank" to rank.toString(), "season" to gb.seasonNumber.toString()),
                )
            } else {
                ng.rewards.mail(entry.id, payout, source)
            }
            rewarded++
        }

        archiveSeason(def, gb, standings, now)

        if (def.ranking.announceReset && automatic) {
            Bukkit.getServer().sendMessage(
                ng.messageComponent(
                    "rank-reset-broadcast",
                    ng.placeholders("subject" to def.displayName, "season" to gb.seasonNumber.toString()),
                )
            )
        }

        val closed = gb.seasonNumber
        gb.season = RankBoard()
        gb.seasonNumber = closed + 1
        gb.lastResetAt = now
        gb.nextResetAt = def.ranking.reset.nextAfter(now)
        dirty = true

        ng.plugin.logger.info(
            "랭킹 초기화: " + def.id + " 시즌 " + closed +
                " 종료 (순위 " + eligible.size + "명, 보상 " + rewarded + "명)"
        )
        return SettleResult(closed, rewarded, eligible.size)
    }

    private fun archiveSeason(
        def: Rankable,
        gb: GameBoards,
        standings: List<RankBoard.Entry>,
        now: Long,
    ) {
        val limit = ng.rewardSettings.seasonArchiveLimit
        if (limit <= 0 || standings.isEmpty()) return

        val places = standings.take(def.ranking.boardSize).mapIndexed { index, entry ->
            SeasonRecord.Place(
                rank = index + 1,
                name = entry.name,
                value = entry.valueFor(def.ranking.mode),
                plays = entry.plays,
            )
        }
        val list = archive.computeIfAbsent(def.id.lowercase()) {
            java.util.Collections.synchronizedList(mutableListOf())
        }
        synchronized(list) {
            list.add(SeasonRecord(gb.seasonNumber, now, def.ranking.mode, places))
            while (list.size > limit) list.removeAt(0)
        }
    }

    /** Admin action: wipe a board without paying anything out. */
    fun wipe(def: Rankable, allTime: Boolean) {
        val gb = boardsOf(def.id)
        if (allTime) gb.allTime = RankBoard() else gb.season = RankBoard()
        dirty = true
    }

    // --- persistence -----------------------------------------------------------

    fun load(then: () -> Unit = {}) {
        ng.io.async({
            val ranking = ng.io.file("data", "ranking.yml")
            val seasons = ng.io.file("data", "seasons.yml")
            val a = if (ranking.exists()) ng.io.load(ranking) else YamlConfiguration()
            val b = if (seasons.exists()) ng.io.load(seasons) else YamlConfiguration()
            a to b
        }) { (rankingConfig, seasonsConfig) ->
            boards.clear()
            archive.clear()

            rankingConfig.getConfigurationSection("games")?.let { games ->
                for (gameId in games.getKeys(false)) {
                    val section = games.getConfigurationSection(gameId) ?: continue
                    val gb = GameBoards(gameId.lowercase())
                    gb.seasonNumber = section.getInt("season-number", 1).coerceAtLeast(1)
                    gb.nextResetAt = section.getLong("next-reset-at", 0L)
                    gb.lastResetAt = section.getLong("last-reset-at", 0L)
                    gb.season = RankBoard.load(section.getConfigurationSection("season-board"))
                    gb.allTime = RankBoard.load(section.getConfigurationSection("all-time"))
                    boards[gb.gameId] = gb
                }
            }

            seasonsConfig.getConfigurationSection("games")?.let { games ->
                for (gameId in games.getKeys(false)) {
                    val section = games.getConfigurationSection(gameId) ?: continue
                    val list = java.util.Collections.synchronizedList(mutableListOf<SeasonRecord>())
                    for (key in section.getKeys(false)) {
                        val record = section.getConfigurationSection(key) ?: continue
                        val places = mutableListOf<SeasonRecord.Place>()
                        record.getConfigurationSection("places")?.let { placeSection ->
                            for (placeKey in placeSection.getKeys(false)) {
                                places.add(
                                    SeasonRecord.Place(
                                        rank = placeSection.getInt("$placeKey.rank", 0),
                                        name = placeSection.getString("$placeKey.name") ?: "?",
                                        value = placeSection.getLong("$placeKey.value", 0L),
                                        plays = placeSection.getLong("$placeKey.plays", 0L),
                                    )
                                )
                            }
                        }
                        list.add(
                            SeasonRecord(
                                season = record.getInt("season", 0),
                                closedAt = record.getLong("closed-at", 0L),
                                mode = RankMode.parse(record.getString("mode")),
                                places = places.sortedBy { it.rank },
                            )
                        )
                    }
                    if (list.isNotEmpty()) archive[gameId.lowercase()] = list
                }
            }

            dirty = false
            then()
        }
    }

    fun flush() {
        if (!dirty) return
        dirty = false
        val ranking = serializeRanking()
        val seasons = serializeSeasons()
        ng.io.asyncRun { write(ranking, seasons) }
    }

    fun flushBlocking() {
        runCatching { write(serializeRanking(), serializeSeasons()) }
            .onFailure { ng.plugin.logger.severe("랭킹 저장 실패: " + it.message) }
        dirty = false
    }

    private fun write(ranking: String, seasons: String) {
        val rankingFile = ng.io.file("data", "ranking.yml")
        rankingFile.parentFile?.mkdirs()
        rankingFile.writeText(ranking, Charsets.UTF_8)
        ng.io.file("data", "seasons.yml").writeText(seasons, Charsets.UTF_8)
    }

    private fun serializeRanking(): String {
        val config = YamlConfiguration()
        for ((gameId, gb) in boards) {
            val path = "games." + gameId
            config.set("$path.season-number", gb.seasonNumber)
            config.set("$path.next-reset-at", gb.nextResetAt)
            config.set("$path.last-reset-at", gb.lastResetAt)
            gb.season.save(config.createSection("$path.season-board"))
            gb.allTime.save(config.createSection("$path.all-time"))
        }
        return config.saveToString()
    }

    private fun serializeSeasons(): String {
        val config = YamlConfiguration()
        for ((gameId, list) in archive) {
            val snapshot = synchronized(list) { list.toList() }
            snapshot.forEachIndexed { index, record ->
                val path = "games." + gameId + "." + index
                config.set("$path.season", record.season)
                config.set("$path.closed-at", record.closedAt)
                config.set("$path.mode", record.mode.name)
                record.places.forEachIndexed { placeIndex, place ->
                    val placePath = "$path.places.$placeIndex"
                    config.set("$placePath.rank", place.rank)
                    config.set("$placePath.name", place.name)
                    config.set("$placePath.value", place.value)
                    config.set("$placePath.plays", place.plays)
                }
            }
        }
        return config.saveToString()
    }
}
