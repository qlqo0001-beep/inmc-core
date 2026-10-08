package kr.inmc.core.reward

import kr.inmc.core.reward.RewardHost

import kr.inmc.core.reward.GiveMode
import kr.inmc.core.reward.RewardBundle
import kr.inmc.core.reward.RewardEntry
import kr.inmc.core.integration.TitleForgeNames
import kr.inmc.core.util.Text
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.random.Random

/**
 * Turns a [RewardBundle] into things a player actually has.
 *
 * Rolling and granting are two separate steps on purpose. [resolve] decides *what* is won and
 * produces concrete stacks; [give] hands them over. Season rewards roll once at settlement and
 * then either land in an inventory or sit in the mailbox as the exact same stacks, so being
 * offline can never change what you won.
 */
class RewardService(private val ng: RewardHost) {

    /** One decided reward: a built stack, an amount of money, and commands to run. */
    class Payout(
        val stacks: MutableList<ItemStack> = mutableListOf(),
        var money: Double = 0.0,
        /** 이름 있는 화폐의 돈(id → 금액, 2026-10-08). [money] 는 기본 화폐. */
        val moneyBy: LinkedHashMap<String, Double> = LinkedHashMap(),
        val commands: MutableList<String> = mutableListOf(),
        /** Labels of entries flagged for a server-wide announcement. */
        val announced: MutableList<String> = mutableListOf(),
        /** Labels of everything won, for the result screen. */
        val labels: MutableList<String> = mutableListOf(),
        /** Entries that could not be built because their source plugin is gone. */
        var unresolved: Int = 0,
    ) {
        fun isEmpty(): Boolean = stacks.isEmpty() && money <= 0.0 && moneyBy.isEmpty() && commands.isEmpty()
    }

    // --- rolling ---------------------------------------------------------------

    /** Picks the entries this payout consists of, honouring the bundle's [GiveMode]. */
    fun pick(bundle: RewardBundle, rng: Random): List<RewardEntry> {
        // Zero chance means "switched off", so such entries are dropped before any draw. The
        // weighted modes need this explicitly: a zero-weight entry can still be returned by a
        // cumulative walk that lands exactly on it, and when *every* weight is zero the
        // fallback would otherwise hand out a disabled reward at random.
        val usable = bundle.entries.filter { !it.isEmpty() && it.chance > 0.0 }
        if (usable.isEmpty()) return emptyList()

        return when (bundle.mode) {
            GiveMode.ALL -> usable.filter { rng.nextDouble() * 100.0 < it.chance }
            GiveMode.ROLL_ONE -> listOfNotNull(weightedPick(usable, rng))
            GiveMode.ROLL_N -> {
                val pool = usable.toMutableList()
                val take = bundle.rollCount.coerceAtMost(pool.size)
                buildList {
                    repeat(take) {
                        val chosen = weightedPick(pool, rng) ?: return@repeat
                        pool.remove(chosen)
                        add(chosen)
                    }
                }
            }
        }
    }

    private fun weightedPick(entries: List<RewardEntry>, rng: Random): RewardEntry? {
        val total = entries.sumOf { it.chance }
        if (total <= 0.0) return entries.randomOrNull(rng)
        var roll = rng.nextDouble() * total
        for (entry in entries) {
            roll -= entry.chance
            if (roll <= 0.0) return entry
        }
        return entries.last()
    }

    /** Rolls the bundle and builds every stack it awards. Safe to call off the main thread. */
    fun resolve(bundle: RewardBundle, rng: Random = Random.Default): Payout {
        val payout = Payout()
        for (entry in pick(bundle, rng)) {
            val amount =
                if (entry.maxAmount <= entry.minAmount) entry.minAmount
                else rng.nextInt(entry.minAmount, entry.maxAmount + 1)

            val stored = entry.item
            if (entry.giveItem && stored != null) {
                // Amounts may exceed a stack, so build as many stacks as it takes. `create`
                // clamps to the item's own max size, which is also how a 1-per-stack item like
                // a sword ends up as N separate stacks rather than one impossible pile.
                var left = amount
                var built = 0
                while (left > 0) {
                    val stack = ng.itemResolver.create(stored, left) ?: break
                    if (stack.amount <= 0) break
                    payout.stacks.add(stack)
                    left -= stack.amount
                    built++
                    if (built > MAX_STACKS_PER_ENTRY) break
                }
                if (built == 0) payout.unresolved++
            }
            if (entry.money > 0.0) {
                if (entry.currency.isBlank()) payout.money += entry.money else payout.moneyBy.merge(entry.currency, entry.money, Double::plus)
            }
            payout.commands.addAll(entry.commands)

            val label = if (amount > 1) entry.label() + " x" + amount else entry.label()
            payout.labels.add(label)
            if (entry.announce) payout.announced.add(label)
        }
        return payout
    }

    // --- granting --------------------------------------------------------------

    /**
     * Hands a payout to an online player.
     *
     * Overflow goes to the mailbox by default rather than the floor: a reward that despawns
     * because the winner had a full inventory is the kind of thing that ends up in a ticket.
     */
    fun give(player: Player, payout: Payout, gameName: String, source: String): GiveResult {
        var overflowed = false
        val leftovers = mutableListOf<ItemStack>()

        for (stack in payout.stacks) {
            val overflow = player.inventory.addItem(stack)
            if (overflow.isEmpty()) continue
            overflowed = true
            overflow.values.forEach { leftovers.add(it) }
        }

        if (leftovers.isNotEmpty()) {
            if (ng.rewardSettings.dropWhenInventoryFull) {
                leftovers.forEach { player.world.dropItemNaturally(player.location, it) }
                ng.tell(player, "reward-dropped")
            } else {
                val entry = Mailbox.Entry(source = source)
                leftovers.forEach { left ->
                    runCatching { left.serializeAsBytes() }.getOrNull()?.let { entry.items.add(it) }
                }
                ng.mailbox.deposit(player.uniqueId, entry)
                ng.tell(player, "reward-inventory-full")
            }
        }

        if (payout.money > 0.0) {
            if (ng.economy.isEnabled) ng.economy.deposit(player, payout.money)
            else ng.plugin.logger.warning("경제 플러그인이 없어 보상 금액을 지급하지 못했습니다: " + payout.money)
        }
        for ((currency, amount) in payout.moneyBy) {
            if (ng.economy.isEnabled) ng.economy.deposit(player, amount, currency)
            else ng.plugin.logger.warning("경제 플러그인이 없어 보상 금액을 지급하지 못했습니다: $amount ($currency)")
        }

        payout.commands.forEach { runCommand(it, player) }

        if (ng.rewardSettings.broadcastRewards) {
            for (label in payout.announced) {
                val message = ng.messageComponent(
                    "reward-announce",
                    ng.placeholders("player" to TitleForgeNames.displayName(player.uniqueId, player.name), "subject" to gameName, "item" to label),
                )
                Bukkit.getServer().sendMessage(message)
            }
        }

        return GiveResult(payout.labels.toList(), overflowed, payout.unresolved)
    }

    data class GiveResult(val labels: List<String>, val overflowed: Boolean, val unresolved: Int)

    /** Rolls and grants in one step, for the common online case. */
    fun award(
        player: Player,
        bundle: RewardBundle,
        gameName: String,
        source: String,
        rng: Random = Random.Default,
    ): GiveResult? {
        if (bundle.isEmpty()) return null
        val payout = resolve(bundle, rng)
        if (payout.isEmpty()) return null
        return give(player, payout, gameName, source)
    }

    /** Parks an already-resolved payout for someone who is not online. */
    fun mail(playerId: UUID, payout: Payout, source: String) {
        if (payout.isEmpty()) return
        val entry = Mailbox.Entry(source = source, money = payout.money, moneyBy = LinkedHashMap(payout.moneyBy))
        payout.stacks.forEach { stack ->
            runCatching { stack.serializeAsBytes() }.getOrNull()?.let { entry.items.add(it) }
        }
        entry.commands.addAll(payout.commands)
        ng.mailbox.deposit(playerId, entry)
    }

    /**
     * Runs one configured command from the console.
     *
     * Placeholders are substituted first so `give {플레이어네임} diamond 1` works, and a leading
     * slash is tolerated because half of everyone types one.
     */
    fun runCommand(template: String, player: Player) {
        // Substitution only - never MiniMessage. A command is not a chat message, and running it
        // through the message pipeline silently eats anything in angle brackets and every `&`
        // sequence that happens to look like a colour code.
        val resolved = Text.substituteOnly(template, ng.placeholders("player" to player.name), player)
            .trim()
            .removePrefix("/")
        if (resolved.isEmpty()) return
        runCatching { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved) }
            .onFailure { ng.plugin.logger.warning("보상 명령어 실행 실패 (" + resolved + "): " + it.message) }
    }

    private companion object {
        /** Guard against a runaway split if an item ever reports a zero stack size. */
        const val MAX_STACKS_PER_ENTRY = 64
    }
}
