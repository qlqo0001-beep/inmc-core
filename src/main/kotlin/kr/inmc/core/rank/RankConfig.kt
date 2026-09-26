package kr.inmc.core.rank

import org.bukkit.configuration.ConfigurationSection

/** A game's leaderboard settings. Season state (current season number, next reset) lives in data/. */
class RankConfig(
    var mode: RankMode = RankMode.BEST_RECORD,
    minPlays: Int = 1,
    boardSize: Int = 50,
    var reset: ResetSchedule = ResetSchedule(),
    /** Announce the new standings to the whole server when a season closes. */
    var announceReset: Boolean = true,
) {

    /**
     * Plays required before a player is eligible for a rank reward.
     *
     * Without it, a season that nobody played hands first place - and the top prize - to
     * whoever happened to try the game once on the last day.
     */
    var minPlays: Int = minPlays.coerceAtLeast(1)
        set(value) {
            field = value.coerceIn(1, 1000)
        }

    var boardSize: Int = boardSize.coerceAtLeast(1)
        set(value) {
            field = value.coerceIn(1, 500)
        }

    fun copyOf(): RankConfig =
        RankConfig(mode, minPlays, boardSize, reset.copyOf(), announceReset)

    fun save(section: ConfigurationSection) {
        section.set("mode", mode.name)
        section.set("min-plays", minPlays)
        section.set("board-size", boardSize)
        section.set("announce-reset", announceReset)
        reset.save(section.createSection("reset"))
    }

    companion object {
        fun load(section: ConfigurationSection?, defaultMode: RankMode): RankConfig {
            if (section == null) return RankConfig(mode = defaultMode)
            return RankConfig(
                mode = section.getString("mode")?.let { RankMode.parse(it) } ?: defaultMode,
                minPlays = section.getInt("min-plays", 1),
                boardSize = section.getInt("board-size", 50),
                reset = ResetSchedule.load(section.getConfigurationSection("reset")),
                announceReset = section.getBoolean("announce-reset", true),
            )
        }
    }
}
