package kr.inmc.core.gui

import kr.inmc.core.util.PotionEffects
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/**
 * 물약 효과 고르기 — 서버의 효과 전부를 **게임 속 한국어 이름**으로(`<lang:effect.minecraft.…>`), 효과 색의 물약 아이콘으로 보여 준다
 * (2026-10-14, 사용자 요청 "물약 효과도 고르는 식으로 · 한국 이름으로"). 우클릭하면 나에게 5초 걸어 본다.
 *
 * @param current 지금 고른 것(id) — 반짝임으로 표시.
 * @param onPick 좌클릭한 효과.
 */
class PotionPickMenu(
    override val owner: Any?,
    private val viewer: Player,
    title: String = "물약 효과 고르기",
    private val current: String? = null,
    private val back: (() -> Unit)? = null,
    private val onPick: (PotionEffectType) -> Unit,
) : Menu(54, Text.renderFlat("<dark_gray>$title</dark_gray>")) {

    private val options = PotionEffects.all()
    private var page = options.indexOfFirst { PotionEffects.id(it) == current?.lowercase()?.removePrefix("minecraft:") }.let { if (it < 0) 0 else it / Paging.PER_PAGE }

    fun show() = open(viewer)

    override fun draw() {
        clear()
        page = Paging.clamp(page, options.size)
        for ((slot, type) in Paging.slice(options, page).withIndex()) {
            val chosen = PotionEffects.id(type) == current?.lowercase()?.removePrefix("minecraft:")
            set(slot, icon(type, chosen)) { event ->
                if (event.isRightClick) {
                    viewer.addPotionEffect(PotionEffect(type, PREVIEW_TICKS, 0))
                    viewer.sendActionBar(Text.render("<aqua>미리보기: " + PotionEffects.lang(type) + " 5초</aqua>"))
                } else {
                    onPick(type)
                }
            }
        }
        fillEmpty(Icon.FILLER)
        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { page--; refresh() }
        if (page < Paging.pageCount(options.size) - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { page++; refresh() }
        back?.let { go -> set(Paging.SLOT_BACK, Icon.back()) { go() } }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun icon(type: PotionEffectType, chosen: Boolean): ItemStack {
        val stack = Icon.of(Material.POTION, (if (chosen) "<green>✔ " else "<yellow>") + PotionEffects.lang(type), listOf(
            "<dark_gray>" + PotionEffects.id(type) + "</dark_gray>",
            "",
            "<yellow>▶ 좌클릭: 고르기</yellow>",
            "<aqua>▶ 우클릭: 나에게 5초 걸어 보기</aqua>",
        ))
        stack.editMeta(PotionMeta::class.java) { meta ->
            runCatching { meta.color = type.color }
            if (chosen) meta.setEnchantmentGlintOverride(true)
        }
        return stack
    }

    private companion object {
        const val PREVIEW_TICKS = 100
    }
}
