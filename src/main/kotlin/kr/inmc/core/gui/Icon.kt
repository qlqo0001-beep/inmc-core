package kr.inmc.core.gui

import io.papermc.paper.datacomponent.DataComponentType
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.TooltipDisplay
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/** Builders for the decorative items every menu is made of. */
object Icon {

    val FILLER: ItemStack by lazy { blank(Material.GRAY_STAINED_GLASS_PANE) }
    val EDGE: ItemStack by lazy { blank(Material.BLACK_STAINED_GLASS_PANE) }
    val SLOT_HINT: ItemStack by lazy { blank(Material.LIGHT_GRAY_STAINED_GLASS_PANE) }

    /** 빈 칸(테두리·채움). 툴팁 자체를 숨긴다 — 마우스를 올려도 빈 상자가 안 뜬다. */
    fun blank(material: Material): ItemStack = ItemStack(material).apply {
        editMeta { meta -> meta.displayName(Text.renderFlat(" ")) }
        setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build())
    }

    fun of(material: Material, name: String, vararg lore: String): ItemStack =
        of(material, name, lore.toList())

    fun of(material: Material, name: String, lore: List<String>): ItemStack =
        ItemStack(material).apply { decorate(this, name, lore) }

    /**
     * Adds menu information to a real item **below whatever lore it already has**.
     *
     * Overwriting the lore would erase exactly the part that identifies a custom item - an
     * MMOItems weapon's stat block, an ItemsAdder item's description - leaving the admin (and,
     * in the `/monsters info` chance table, the player) looking at an anonymous vanilla-looking
     * stack. Anything the source plugin wrote stays; our lines go after a separator.
     */
    fun annotate(stack: ItemStack, name: String? = null, lore: List<String>): ItemStack =
        stack.clone().apply {
            editMeta { meta: ItemMeta ->
                if (name != null) meta.displayName(Text.renderFlat(name))
                if (lore.isEmpty()) return@editMeta

                val existing = meta.lore().orEmpty()
                val merged = if (existing.isEmpty()) {
                    Text.renderLore(lore)
                } else {
                    existing + Text.renderFlat(SEPARATOR) + Text.renderLore(lore)
                }
                meta.lore(merged)
            }
        }

    /** Replaces name and lore outright. Only for icons we authored ourselves. */
    fun relabel(stack: ItemStack, name: String?, lore: List<String>): ItemStack =
        stack.clone().apply { decorate(this, name, lore) }

    /**
     * 우리가 만든 아이콘은 이름·설명만 보인다. 검·갑옷·물약 재질의 아이콘에 바닐라 줄("주로 사용하는 손에 있을 때…"·물약 효과·
     * 내구도)이 따라 나오던 것(2026-10-07 낚시 연습 모드·업적 칭호 보상)을 [hideExtras] 가 막는다. 반짝임은 그대로다.
     */
    private fun decorate(stack: ItemStack, name: String?, lore: List<String>) {
        stack.editMeta { meta: ItemMeta ->
            if (name != null) meta.displayName(Text.renderFlat(name))
            if (lore.isNotEmpty()) meta.lore(Text.renderLore(lore))
        }
        hideExtras(stack)
    }

    /** 재질이 딸고 오는 툴팁 줄을 숨긴다 — [annotate] 처럼 **실제 아이템**을 보여 주는 곳에서는 부르지 않는다. */
    fun hideExtras(stack: ItemStack): ItemStack = stack.apply {
        setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().addHiddenComponents(*HIDDEN_FOR_ICONS).build())
    }

    private val HIDDEN_FOR_ICONS: Array<DataComponentType> = arrayOf(
        DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.UNBREAKABLE, DataComponentTypes.DYED_COLOR, DataComponentTypes.TRIM,
        DataComponentTypes.ENCHANTMENTS, DataComponentTypes.STORED_ENCHANTMENTS, DataComponentTypes.POTION_CONTENTS,
        DataComponentTypes.JUKEBOX_PLAYABLE, DataComponentTypes.BANNER_PATTERNS, DataComponentTypes.CONTAINER, DataComponentTypes.BUNDLE_CONTENTS,
        DataComponentTypes.FIREWORKS, DataComponentTypes.FIREWORK_EXPLOSION, DataComponentTypes.MAP_ID, DataComponentTypes.CHARGED_PROJECTILES,
        DataComponentTypes.INSTRUMENT, DataComponentTypes.OMINOUS_BOTTLE_AMPLIFIER, DataComponentTypes.LODESTONE_TRACKER,
        DataComponentTypes.TROPICAL_FISH_PATTERN, DataComponentTypes.WRITTEN_BOOK_CONTENT, DataComponentTypes.WRITABLE_BOOK_CONTENT,
        DataComponentTypes.CAN_BREAK, DataComponentTypes.CAN_PLACE_ON, DataComponentTypes.SUSPICIOUS_STEW_EFFECTS,
    )

    private const val SEPARATOR = "<dark_gray>────────────</dark_gray>"

    fun toggle(value: Boolean): String = if (value) "<green>켜짐</green>" else "<red>꺼짐</red>"

    fun toggleMaterial(value: Boolean): Material =
        if (value) Material.LIME_DYE else Material.GRAY_DYE

    fun back(): ItemStack = of(Material.ARROW, "<gray>◀ 뒤로</gray>")

    fun close(): ItemStack = of(Material.BARRIER, "<red>✖ 닫기</red>")

    fun confirm(name: String = "<green>✔ 확인</green>", lore: List<String> = emptyList()): ItemStack =
        of(Material.LIME_CONCRETE, name, lore)

    fun cancel(name: String = "<red>✖ 취소</red>", lore: List<String> = emptyList()): ItemStack =
        of(Material.RED_CONCRETE, name, lore)

    fun prevPage(): ItemStack = of(Material.SPECTRAL_ARROW, "<yellow>◀ 이전 페이지</yellow>")

    fun nextPage(): ItemStack = of(Material.SPECTRAL_ARROW, "<yellow>다음 페이지 ▶</yellow>")
}
