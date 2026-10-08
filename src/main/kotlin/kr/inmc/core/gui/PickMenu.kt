package kr.inmc.core.gui

import kr.inmc.core.util.Text
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 여러 보기 중 하나(또는 여럿)를 고르는 화면 — 54칸, 45개씩 쪽, ◀ 뒤로 · ✖ 닫기.
 *
 * 인첸트·상점이 글자 단위로 같은 것을 각자 갖고 있던 것을 올렸다(2026-10-08). 좌/우클릭으로 돌리는 `Editors.cycle` 은
 * 보기가 **6개 이하의 고정 목록**일 때만 쓰고, 그보다 많거나 가변 목록이면 이 화면을 연다(사용자 2026-09-30 "긴 목록은 들어가서 고르게").
 *
 * @param owner 그 플러그인의 로케이터 — 리로드가 열린 화면을 닫을 때 제 것을 가려낸다([Menu.owner]).
 * @param icon 보기 하나를 그린 아이콘.
 * @param selected 여럿 고르기면 켜진 것. 한 개 고르기면 비워 둔다.
 * @param back ◀ 뒤로. null 이면 버튼이 없다.
 * @param onPick 누른 보기. 여럿 고르기면 누를 때마다 불리고 화면은 그대로다.
 */
open class PickMenu<T>(
    override val owner: Any?,
    protected val viewer: Player,
    title: String,
    private val options: List<T>,
    private val icon: (T) -> ItemStack,
    private val multi: Boolean = false,
    private val selected: () -> Set<T> = { emptySet() },
    private val back: (() -> Unit)? = null,
    private val onPick: (T) -> Unit,
) : Menu(54, Text.renderFlat(title)) {

    private var page = 0

    fun show() = open(viewer)

    override fun draw() {
        clear()
        page = Paging.clamp(page, options.size)
        val chosen = selected()
        for ((slot, option) in Paging.slice(options, page).withIndex()) {
            val base = icon(option)
            val shown = if (!multi) base else Icon.annotate(
                base.clone().also { if (option in chosen) it.editMeta { meta -> meta.setEnchantmentGlintOverride(true) } },
                lore = listOf("", if (option in chosen) "<green>▶ 켜짐 - 클릭해서 끄기</green>" else "<gray>▶ 꺼짐 - 클릭해서 켜기</gray>"),
            )
            set(slot, shown) {
                onPick(option)
                if (multi) refresh()
            }
        }
        fillEmpty(Icon.FILLER)
        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { page--; refresh() }
        if (page < Paging.pageCount(options.size) - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { page++; refresh() }
        back?.let { go -> set(Paging.SLOT_BACK, Icon.back()) { go() } }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }
}
