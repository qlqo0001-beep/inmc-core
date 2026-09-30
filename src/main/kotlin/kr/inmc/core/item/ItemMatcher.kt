package kr.inmc.core.item

import kr.inmc.core.integration.CarriedStorage
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
 *
 * 가진 것을 세고 빼는 [has]·[count]·[consumeOne]·[takeOne] 은 가방 다음에 **들고 다니는 보관함**([CarriedStorage] —
 * 커스텀아이템의 배낭)까지 본다(사용자 결정 2026-09-30). [findSlot] 만 가방 칸 번호를 주므로 가방만 본다.
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
        return findSlot(player, spec) >= 0 || CarriedStorage.count(player) { matches(it, spec) } > 0
    }

    /** 가방(단축바 포함)과 들고 다니는 보관함에서 [test] 인 것의 개수. */
    fun count(player: Player, test: (ItemStack) -> Boolean): Int =
        player.inventory.storageContents.sumOf { if (it != null && !it.type.isAir && test(it)) it.amount else 0 } +
            CarriedStorage.count(player, test)

    fun count(player: Player, spec: StoredItem?): Int = if (spec == null) 0 else count(player) { matches(it, spec) }

    /** Removes exactly one matching item — 가방 먼저, 없으면 보관함. Returns false when the player had none. */
    fun consumeOne(player: Player, spec: StoredItem?): Boolean = spec == null || takeOne(player, spec) != null

    /** 하나를 빼고 **뺀 것의 사본**(1개)을 준다 — 뒤에서 실패하면 되돌려 줄 때 쓴다. 없으면 null. 가방 먼저, 없으면 보관함. */
    fun takeOne(player: Player, spec: StoredItem): ItemStack? {
        val slot = findSlot(player, spec)
        if (slot >= 0) {
            val stack = player.inventory.getItem(slot) ?: return null
            val taken = stack.clone().apply { amount = 1 }
            if (stack.amount <= 1) {
                player.inventory.setItem(slot, null)
            } else {
                stack.amount -= 1
                player.inventory.setItem(slot, stack)
            }
            return taken
        }
        val taken = ArrayList<ItemStack>(1)
        return if (CarriedStorage.take(player, 1, taken) { matches(it, spec) } == 1) taken.first() else null
    }
}
