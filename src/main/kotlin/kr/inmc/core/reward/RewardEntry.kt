package kr.inmc.core.reward

import kr.inmc.core.item.StoredItem
import kr.inmc.core.util.Numbers
import org.bukkit.configuration.ConfigurationSection
import java.util.UUID

/**
 * One thing a player can receive.
 *
 * An entry may hand out an item, run commands, pay money, or any combination - a command-only
 * entry keeps [item] as its admin-GUI icon but sets [giveItem] false. [chance] is rolled
 * independently of every other entry in the bundle unless the bundle picks one at random.
 */
class RewardEntry(
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    var item: StoredItem? = null,
    chance: Double = 100.0,
    minAmount: Int = 1,
    maxAmount: Int = 1,
    var commands: MutableList<String> = mutableListOf(),
    var money: Double = 0.0,
    /** 돈의 화폐 id(core `Currencies`). 비우면 기본 화폐(2026-10-08). */
    var currency: String = "",
    var giveItem: Boolean = true,
    /** Broadcast to the whole server when this entry lands. */
    var announce: Boolean = false,
) {

    /**
     * Rolled independently per entry, as a percentage.
     *
     * Zero is allowed here even though [Numbers.clampChance] floors at 0.01 elsewhere: an admin
     * needs a way to switch an entry off for a while without deleting it and losing its item,
     * amounts and commands.
     */
    var chance: Double = clampRewardChance(chance)
        set(value) {
            field = clampRewardChance(value)
        }

    /**
     * Amounts are not capped at a stack.
     *
     * "64 diamonds" is a normal prize and "128" is not unreasonable; the give path splits the
     * total across stacks, so the only real limit is what an inventory can hold.
     */
    var minAmount: Int = minAmount.coerceAtLeast(1)
        set(value) {
            field = value.coerceIn(1, MAX_AMOUNT)
            if (field > maxAmount) maxAmount = field
        }

    var maxAmount: Int = maxAmount.coerceAtLeast(minAmount)
        set(value) {
            field = value.coerceIn(1, MAX_AMOUNT)
            if (field < minAmount) minAmount = field
        }

    /** True when this entry would do nothing at all - the dialog warns about these. */
    fun isEmpty(): Boolean =
        (item == null || !giveItem) && commands.isEmpty() && money <= 0.0

    /** 돈 보상 글 — 화폐가 있으면 그 화폐 형식으로, 없으면 "1,000원". */
    fun moneyLabel(): String = currency.takeIf { it.isNotBlank() }
        ?.let { id -> kr.inmc.core.economy.Currencies.get(id)?.format(Math.round(money)) ?: (Numbers.money(money) + " " + id) }
        ?: (Numbers.money(money) + "원")

    fun label(): String {
        val stored = item
        return when {
            stored != null && giveItem -> stored.label()
            money > 0.0 -> moneyLabel()
            commands.isNotEmpty() -> "명령어 " + commands.size + "개"
            else -> "빈 보상"
        }
    }

    fun copyOf(): RewardEntry = RewardEntry(
        id = id,
        item = item,
        chance = chance,
        minAmount = minAmount,
        maxAmount = maxAmount,
        commands = commands.toMutableList(),
        money = money,
        currency = currency,
        giveItem = giveItem,
        announce = announce,
    )

    fun save(section: ConfigurationSection) {
        section.set("id", id)
        item?.save(section)
        section.set("chance", chance)
        section.set("min-amount", minAmount)
        section.set("max-amount", maxAmount)
        if (money > 0.0) section.set("money", money)
        if (currency.isNotBlank()) section.set("currency", currency)
        if (commands.isNotEmpty()) section.set("commands", commands)
        if (!giveItem) section.set("give-item", false)
        if (announce) section.set("announce", true)
    }

    companion object {

        /** A full double chest of full stacks - past this an inventory cannot hold the prize. */
        const val MAX_AMOUNT = 3456

        /** Like [Numbers.clampChance] but 0 is a legal value meaning "never". */
        fun clampRewardChance(value: Double): Double =
            if (value <= 0.0) 0.0 else Numbers.clampChance(value)

        fun load(section: ConfigurationSection): RewardEntry = RewardEntry(
            id = section.getString("id") ?: UUID.randomUUID().toString().substring(0, 8),
            item = StoredItem.load(section),
            chance = section.getDouble("chance", 100.0),
            minAmount = section.getInt("min-amount", 1),
            maxAmount = section.getInt("max-amount", section.getInt("min-amount", 1)),
            commands = section.getStringList("commands").toMutableList(),
            money = section.getDouble("money", 0.0),
            currency = section.getString("currency").orEmpty(),
            giveItem = section.getBoolean("give-item", true),
            announce = section.getBoolean("announce", false),
        )
    }
}
