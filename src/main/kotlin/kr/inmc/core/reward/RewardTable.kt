package kr.inmc.core.reward

import kr.inmc.core.rank.Better
import org.bukkit.configuration.ConfigurationSection

/** The occasions on which a game pays out a flat bundle. */
enum class RewardTrigger(val key: String, val display: String, val help: String) {
    JOIN("join", "참가 보상", "게임을 시작할 때마다 지급합니다."),
    CLEAR("clear", "클리어 보상", "게임을 성공으로 끝냈을 때 지급합니다."),
    FAIL("fail", "실패 보상", "시도 초과·시간 초과·포기로 끝났을 때 지급합니다."),
    DAILY_FIRST_CLEAR("daily-first", "일일 첫 클리어", "그 날 처음 클리어했을 때 한 번만 지급합니다.");

    companion object {
        fun byKey(raw: String?): RewardTrigger? =
            entries.firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) }
    }
}

/**
 * A "did this well enough" bracket.
 *
 * [threshold] is read through the game's [Better] direction, so one field expresses both
 * "3회 이내에 맞히면" (LOWER) and "20점 이상 내면" (HIGHER).
 */
class RecordTier(var threshold: Long, val bundle: RewardBundle = RewardBundle()) {

    fun matches(record: Long, better: Better): Boolean =
        if (better == Better.LOWER) record <= threshold else record >= threshold

    fun describe(better: Better, unit: String): String =
        if (better == Better.LOWER) "$threshold$unit 이내" else "$threshold$unit 이상"

    fun save(section: ConfigurationSection) {
        section.set("threshold", threshold)
        bundle.save(section.createSection("bundle"))
    }

    companion object {
        fun load(section: ConfigurationSection): RecordTier = RecordTier(
            threshold = section.getLong("threshold", 0L),
            bundle = RewardBundle.load(section.getConfigurationSection("bundle")),
        )
    }
}

/** A placing range on the season leaderboard, e.g. 1위, 2~3위, 4~10위. */
class RankBracket(from: Int, to: Int, val bundle: RewardBundle = RewardBundle()) {

    var from: Int = from.coerceAtLeast(1)
        set(value) {
            field = value.coerceIn(1, 10000)
            if (field > to) to = field
        }

    var to: Int = to.coerceAtLeast(from)
        set(value) {
            field = value.coerceIn(1, 10000)
            if (field < from) from = field
        }

    fun contains(rank: Int): Boolean = rank in from..to

    fun describe(): String = if (from == to) "${from}위" else "${from}~${to}위"

    fun save(section: ConfigurationSection) {
        section.set("from", from)
        section.set("to", to)
        bundle.save(section.createSection("bundle"))
    }

    companion object {
        fun load(section: ConfigurationSection): RankBracket = RankBracket(
            from = section.getInt("from", 1),
            to = section.getInt("to", section.getInt("from", 1)),
            bundle = RewardBundle.load(section.getConfigurationSection("bundle")),
        )
    }
}

/** Everything one game can pay out. */
class RewardTable {

    val triggers: MutableMap<RewardTrigger, RewardBundle> = LinkedHashMap()
    val recordTiers: MutableList<RecordTier> = mutableListOf()
    val rankBrackets: MutableList<RankBracket> = mutableListOf()

    fun bundle(trigger: RewardTrigger): RewardBundle = triggers.getOrPut(trigger) { RewardBundle() }

    fun bundleOrNull(trigger: RewardTrigger): RewardBundle? = triggers[trigger]

    /** Every tier the record qualifies for, best-first. All matching tiers pay out. */
    fun matchingTiers(record: Long, better: Better): List<RecordTier> =
        recordTiers.filter { it.matches(record, better) }
            .sortedBy { if (better == Better.LOWER) it.threshold else -it.threshold }

    /**
     * The bracket that pays out for a placing, most specific first.
     *
     * Overlap is legitimate and common - an admin sets up `1~10위` for everyone and then adds a
     * richer `1위` on top. Taking the first match in list order would hand first place the
     * generic prize, because `1~10위` sorts first. The narrowest range wins instead, which is
     * what "I added a special case" plainly means.
     */
    fun bracketFor(rank: Int): RankBracket? =
        rankBrackets.filter { it.contains(rank) }.minByOrNull { it.to - it.from }

    /** Bracket pairs that overlap, so the editor can point them out rather than silently pick. */
    fun overlaps(): List<Pair<RankBracket, RankBracket>> = buildList {
        for (i in rankBrackets.indices) {
            for (j in i + 1 until rankBrackets.size) {
                val a = rankBrackets[i]
                val b = rankBrackets[j]
                if (a.from <= b.to && b.from <= a.to) add(a to b)
            }
        }
    }

    fun sortBrackets() {
        rankBrackets.sortBy { it.from }
    }

    fun copyOf(): RewardTable {
        val copy = RewardTable()
        for ((trigger, bundle) in triggers) copy.triggers[trigger] = bundle.copyOf()
        recordTiers.forEach { copy.recordTiers.add(RecordTier(it.threshold, it.bundle.copyOf())) }
        rankBrackets.forEach { copy.rankBrackets.add(RankBracket(it.from, it.to, it.bundle.copyOf())) }
        return copy
    }

    fun save(section: ConfigurationSection) {
        val triggerSection = section.createSection("triggers")
        for ((trigger, bundle) in triggers) {
            if (bundle.entries.isEmpty()) continue
            bundle.save(triggerSection.createSection(trigger.key))
        }
        val tiers = section.createSection("record-tiers")
        recordTiers.forEachIndexed { i, tier -> tier.save(tiers.createSection(i.toString())) }
        val brackets = section.createSection("rank-brackets")
        rankBrackets.forEachIndexed { i, bracket -> bracket.save(brackets.createSection(i.toString())) }
    }

    companion object {
        fun load(section: ConfigurationSection?): RewardTable {
            val table = RewardTable()
            if (section == null) return table

            section.getConfigurationSection("triggers")?.let { triggers ->
                for (key in triggers.getKeys(false)) {
                    val trigger = RewardTrigger.byKey(key) ?: continue
                    table.triggers[trigger] = RewardBundle.load(triggers.getConfigurationSection(key))
                }
            }
            section.getConfigurationSection("record-tiers")?.let { tiers ->
                for (key in tiers.getKeys(false)) {
                    tiers.getConfigurationSection(key)?.let { table.recordTiers.add(RecordTier.load(it)) }
                }
            }
            section.getConfigurationSection("rank-brackets")?.let { brackets ->
                for (key in brackets.getKeys(false)) {
                    brackets.getConfigurationSection(key)?.let { table.rankBrackets.add(RankBracket.load(it)) }
                }
            }
            table.sortBrackets()
            return table
        }
    }
}
