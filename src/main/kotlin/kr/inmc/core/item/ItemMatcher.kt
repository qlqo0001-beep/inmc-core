package kr.inmc.core.item

import kr.inmc.core.integration.CustomItemHook
import kr.inmc.core.integration.MMOItemsHook
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Recognises key and capsule items in a player's inventory.
 *
 * Spec §75 defines the rule as "아이템 종류와 이름으로 인식" - match on material plus display
 * name. Items that carry a plugin identity (MMOItems, ItemsAdder, ...) are matched on that
 * identity instead, which is both stricter and cheaper than comparing NBT.
 */
class ItemMatcher(
    private val mmoItems: MMOItemsHook,
    private val customItems: CustomItemHook,
) {

    fun matches(stack: ItemStack?, spec: StoredItem?): Boolean {
        if (spec == null) return true
        if (stack == null || stack.type.isAir) return false

        return when (val ref = spec.ref) {
            is ItemRef.MMOItems -> mmoItems.identify(stack) == ref
            is ItemRef.Namespaced -> customItems.identify(stack) == ref

            // Plain vanilla: material, plus the name when one was registered. A stack that belongs to a
            // plugin (MMOItems, ItemsAdder, our custom items...) is that plugin's item, not the vanilla one
            // it is built on - otherwise an emerald currency counts, and takes, a custom item made of emerald.
            is ItemRef.Vanilla -> matchesByTypeAndName(stack, spec) && !hasIdentity(stack)

            // No reference could be derived, so this is a hand-made item (custom name, lore,
            // enchantments, model data). Comparing material and name alone would let anyone
            // rename a stick to the key's name and open the box, so the stored snapshot is
            // compared in full instead.
            is ItemRef.None -> matchesSnapshot(stack, spec)
        }
    }

    private fun matchesSnapshot(stack: ItemStack, spec: StoredItem): Boolean {
        val bytes = spec.snapshot ?: return matchesByTypeAndName(stack, spec)
        val expected = runCatching { ItemStack.deserializeBytes(bytes) }.getOrNull()
            ?: return matchesByTypeAndName(stack, spec)
        // isSimilar ignores stack size but compares every other component.
        return expected.isSimilar(stack)
    }

    /**
     * Material must match. The name is only compared when the registered item actually has
     * one - registering a plain gold nugget as the key should accept any gold nugget.
     */
    private fun matchesByTypeAndName(stack: ItemStack, spec: StoredItem): Boolean {
        if (stack.type != spec.material) return false
        val expected = spec.displayName?.takeIf { it.isNotBlank() } ?: return true
        val actual = StoredItem.plainName(stack) ?: return false
        return actual.equals(expected, ignoreCase = true)
    }

    private fun hasIdentity(stack: ItemStack): Boolean =
        mmoItems.identify(stack) != null || customItems.identify(stack) != null

    /** Storage slot index holding a matching item, or -1. Hotbar included, armour excluded. */
    fun findSlot(player: Player, spec: StoredItem?): Int {
        if (spec == null) return -1
        val storage = player.inventory.storageContents
        for (i in storage.indices) {
            if (matches(storage[i], spec)) return i
        }
        return -1
    }

    fun has(player: Player, spec: StoredItem?): Boolean {
        if (spec == null) return true
        return findSlot(player, spec) >= 0
    }

    /** Removes exactly one matching item. Returns false when the player had none. */
    fun consumeOne(player: Player, spec: StoredItem?): Boolean {
        if (spec == null) return true
        val slot = findSlot(player, spec)
        if (slot < 0) return false
        val stack = player.inventory.getItem(slot) ?: return false
        if (stack.amount <= 1) {
            player.inventory.setItem(slot, null)
        } else {
            stack.amount -= 1
            player.inventory.setItem(slot, stack)
        }
        return true
    }
}
