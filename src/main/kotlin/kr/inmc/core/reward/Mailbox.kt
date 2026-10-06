package kr.inmc.core.reward

import kr.inmc.core.reward.RewardHost
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Rewards waiting for a player who was not around to receive them.
 *
 * Season rewards are the reason this exists: a leaderboard closes on a schedule, and the person
 * who came first is frequently asleep. Rolling the reward at award time and parking the concrete
 * result here - rather than parking the *bundle* and re-rolling on claim - means two players who
 * placed the same get the same thing, and nobody can farm a re-roll by claiming at a better
 * moment.
 */
class Mailbox(private val ng: RewardHost) {

    class Entry(
        val id: String = UUID.randomUUID().toString().substring(0, 8),
        val at: Long = System.currentTimeMillis(),
        /** Human readable origin, e.g. "숫자야구 3자리 · 시즌 4 · 1위". */
        val source: String = "",
        val items: MutableList<ByteArray> = mutableListOf(),
        var money: Double = 0.0,
        val commands: MutableList<String> = mutableListOf(),
    ) {
        fun isEmpty(): Boolean = items.isEmpty() && money <= 0.0 && commands.isEmpty()

        fun describe(): String {
            val parts = mutableListOf<String>()
            if (items.isNotEmpty()) parts.add("아이템 " + items.size + "종")
            if (money > 0.0) parts.add(kr.inmc.core.util.Numbers.money(money) + "원")
            if (commands.isNotEmpty()) parts.add("특전 " + commands.size + "개")
            return if (parts.isEmpty()) "빈 보상" else parts.joinToString(" · ")
        }
    }

    private val boxes = ConcurrentHashMap<UUID, MutableList<Entry>>()

    @Volatile
    private var dirty = false

    fun countOf(playerId: UUID): Int = boxes[playerId]?.size ?: 0

    fun entriesOf(playerId: UUID): List<Entry> = boxes[playerId]?.toList().orEmpty()

    fun isEmpty(playerId: UUID): Boolean = countOf(playerId) == 0

    /**
     * Discards every entry waiting for this player. Returns how many were dropped.
     *
     * Admin reset path — unclaimed achievement rewards vanish with the records.
     * Expired entries ([purgeExpired]) drop silently; this one is always deliberate,
     * so the caller reports the count.
     */
    fun discard(playerId: UUID): Int {
        val removed = boxes.remove(playerId)?.size ?: 0
        if (removed > 0) dirty = true
        return removed
    }

    fun deposit(playerId: UUID, entry: Entry) {
        if (entry.isEmpty()) return
        val list = boxes.computeIfAbsent(playerId) { java.util.Collections.synchronizedList(mutableListOf()) }
        synchronized(list) {
            list.add(entry)
            // Oldest first out, so the reward someone just won is never the one that is dropped.
            while (list.size > ng.rewardSettings.mailboxLimit) list.removeAt(0)
        }
        dirty = true
    }

    /**
     * Hands everything over. Items that do not fit stay in the box, so claiming with three free
     * slots takes three stacks and leaves the rest rather than voiding them.
     *
     * Returns the number of entries fully claimed.
     */
    fun claimAll(player: Player): ClaimResult {
        val list = boxes[player.uniqueId] ?: return ClaimResult(0, 0, false)
        var claimed = 0
        var leftBehind = 0
        var anyPartial = false

        val snapshot = synchronized(list) { list.toList() }
        for (entry in snapshot) {
            val remaining = mutableListOf<ByteArray>()
            for (bytes in entry.items) {
                val stack = runCatching { ItemStack.deserializeBytes(bytes) }.getOrNull()
                if (stack == null) {
                    // Keeping it would make the entry unclaimable forever, so it goes - but it
                    // goes loudly, because otherwise a "받았습니다" with nothing in hand is
                    // impossible for an owner to investigate.
                    ng.plugin.logger.warning(
                        "우편함 아이템을 복원하지 못해 폐기했습니다: " + player.name +
                            " (" + entry.source + ")"
                    )
                    continue
                }
                val overflow = player.inventory.addItem(stack)
                if (overflow.isEmpty()) continue
                overflow.values.forEach { left ->
                    remaining.add(runCatching { left.serializeAsBytes() }.getOrNull() ?: return@forEach)
                }
            }

            if (remaining.isEmpty()) {
                // The items are already in hand, so the only thing that can still fail is the
                // money. If it does - Vault removed, provider rejecting the transaction - the
                // entry has to survive holding just the money, or the reward is destroyed with
                // a cheerful "받았습니다" and no trace of what was lost.
                val owed = entry.money
                val paid = owed <= 0.0 || (ng.economy.isEnabled && ng.economy.deposit(player, owed))
                if (!paid) {
                    ng.plugin.logger.warning(
                        "우편함 금액을 지급하지 못해 보관합니다: " + player.name + " " + owed + "원"
                    )
                    entry.items.clear()
                    leftBehind++
                    // Deliberately not `anyPartial`: that flag drives the "인벤토리가 부족해서"
                    // message, and blaming the player's inventory for an economy failure would
                    // send them off to clear slots that were never the problem.
                    ng.tell(player, "mailbox-money-held")
                    continue
                }
                entry.commands.forEach { ng.rewards.runCommand(it, player) }
                synchronized(list) { list.remove(entry) }
                claimed++
            } else {
                // Partially taken: keep only what did not fit, and do not pay the money twice.
                entry.items.clear()
                entry.items.addAll(remaining)
                leftBehind++
                anyPartial = true
            }
        }

        if (synchronized(list) { list.isEmpty() }) boxes.remove(player.uniqueId)
        dirty = true
        return ClaimResult(claimed, leftBehind, anyPartial)
    }

    data class ClaimResult(val claimed: Int, val leftBehind: Int, val partial: Boolean)

    // --- persistence -----------------------------------------------------------

    fun load(then: () -> Unit = {}) {
        ng.io.async({
            val file = ng.io.file("data", "mailbox.yml")
            if (file.exists()) ng.io.load(file) else YamlConfiguration()
        }) { config ->
            boxes.clear()
            config.getConfigurationSection("players")?.let { players ->
                for (raw in players.getKeys(false)) {
                    val id = runCatching { UUID.fromString(raw) }.getOrNull() ?: continue
                    val section = players.getConfigurationSection(raw) ?: continue
                    val list = java.util.Collections.synchronizedList(mutableListOf<Entry>())
                    for (key in section.getKeys(false)) {
                        val entrySection = section.getConfigurationSection(key) ?: continue
                        val entry = Entry(
                            id = entrySection.getString("id") ?: key,
                            at = entrySection.getLong("at", 0L),
                            source = entrySection.getString("source").orEmpty(),
                            money = entrySection.getDouble("money", 0.0),
                            commands = entrySection.getStringList("commands").toMutableList(),
                        )
                        entrySection.getStringList("items").forEach { encoded ->
                            runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
                                ?.let { entry.items.add(it) }
                        }
                        if (!entry.isEmpty()) list.add(entry)
                    }
                    if (list.isNotEmpty()) boxes[id] = list
                }
            }
            dirty = false
            then()
        }
    }

    fun flush() {
        if (!dirty) return
        dirty = false
        val text = serialize()
        ng.io.asyncRun { write(text) }
    }

    fun flushBlocking() {
        runCatching { write(serialize()) }
            .onFailure { ng.plugin.logger.severe("우편함 저장 실패: " + it.message) }
        dirty = false
    }

    private fun write(text: String) {
        val file = ng.io.file("data", "mailbox.yml")
        file.parentFile?.mkdirs()
        file.writeText(text, Charsets.UTF_8)
    }

    private fun serialize(): String {
        val config = YamlConfiguration()
        for ((id, list) in boxes) {
            val snapshot = synchronized(list) { list.toList() }
            snapshot.forEachIndexed { index, entry ->
                val path = "players." + id + "." + index
                config.set(path + ".id", entry.id)
                config.set(path + ".at", entry.at)
                config.set(path + ".source", entry.source)
                config.set(path + ".money", entry.money)
                config.set(path + ".commands", entry.commands)
                config.set(path + ".items", entry.items.map { Base64.getEncoder().encodeToString(it) })
            }
        }
        return config.saveToString()
    }

    /** Ticker hook: nudges anyone who logged in with unclaimed rewards. */
    fun notifyOnJoin(player: Player) {
        val count = countOf(player.uniqueId)
        if (count <= 0) return
        ng.tell(player, "mailbox-waiting", ng.placeholders("count" to count.toString()))
    }

    fun onlineHolders(): List<Player> =
        boxes.keys.mapNotNull { Bukkit.getPlayer(it) }

    /**
     * Drops entries nobody has come back for.
     *
     * Without this the file only ever grows: a player who stops playing keeps their unclaimed
     * season rewards forever, and every season adds more. Returns how many were discarded so the
     * caller can log a sweep that actually removed something.
     */
    fun purgeExpired(now: Long): Int {
        val maxAge = ng.rewardSettings.mailboxExpireSeconds
        if (maxAge <= 0L) return 0
        val cutoff = now - maxAge * 1000L

        var removed = 0
        for ((playerId, list) in boxes) {
            synchronized(list) {
                val before = list.size
                list.removeIf { it.at in 1..cutoff }
                removed += before - list.size
            }
            if (synchronized(list) { list.isEmpty() }) boxes.remove(playerId)
        }
        if (removed > 0) dirty = true
        return removed
    }
}
