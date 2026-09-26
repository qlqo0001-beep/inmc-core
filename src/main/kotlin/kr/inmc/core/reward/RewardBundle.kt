package kr.inmc.core.reward

import org.bukkit.configuration.ConfigurationSection

/** How a bundle's entries are selected when it pays out. */
enum class GiveMode(val display: String, val help: String) {

    /** Every entry rolls its own chance independently. Several may land, or none. */
    ALL("전부 지급", "등록된 보상이 각자 확률대로 따로 판정됩니다."),

    /** Exactly one entry is drawn, weighted by chance. */
    ROLL_ONE("하나만 추첨", "확률을 가중치로 삼아 딱 하나만 뽑습니다."),

    /** [RewardBundle.rollCount] distinct entries are drawn, weighted by chance. */
    ROLL_N("N개 추첨", "확률 가중치로 지정한 개수만큼 중복 없이 뽑습니다.");

    companion object {
        fun parse(raw: String?): GiveMode =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: ALL
    }
}

/** A set of rewards paid out together for one occasion. */
class RewardBundle(
    var mode: GiveMode = GiveMode.ALL,
    rollCount: Int = 1,
    val entries: MutableList<RewardEntry> = mutableListOf(),
) {

    var rollCount: Int = rollCount.coerceAtLeast(1)
        set(value) {
            field = value.coerceIn(1, 64)
        }

    fun isEmpty(): Boolean = entries.none { !it.isEmpty() }

    fun copyOf(): RewardBundle = RewardBundle(
        mode = mode,
        rollCount = rollCount,
        entries = entries.map { it.copyOf() }.toMutableList(),
    )

    fun save(section: ConfigurationSection) {
        section.set("mode", mode.name)
        if (mode == GiveMode.ROLL_N) section.set("roll-count", rollCount)
        val list = section.createSection("entries")
        entries.forEachIndexed { index, entry -> entry.save(list.createSection(index.toString())) }
    }

    companion object {
        fun load(section: ConfigurationSection?): RewardBundle {
            if (section == null) return RewardBundle()
            val loaded = mutableListOf<RewardEntry>()
            section.getConfigurationSection("entries")?.let { list ->
                for (key in list.getKeys(false)) {
                    list.getConfigurationSection(key)?.let { loaded.add(RewardEntry.load(it)) }
                }
            }
            return RewardBundle(
                mode = GiveMode.parse(section.getString("mode")),
                rollCount = section.getInt("roll-count", 1),
                entries = loaded,
            )
        }
    }
}
