package kr.inmc.core.gui

import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

/**
 * 아이템 올리기 — "손에 든 것으로 만들기 · 정하기" 를 대신한다(사용자 요청 2026-10-14 "만들기 버튼을 누르면 아이템을 GUI 에 올려서 등록").
 * 가운데 칸에 **가방의 아이템을 누르거나**(아무 클릭) **커서로 집은 아이템을 놓으면** 그 사본이 올라가고, [등록] 을 누르면 [onSubmit] 이 받는다.
 *
 * 아이템은 **옮기지 않는다** — 사본만 쓰고 관리자 가방·커서의 것은 그대로다(손에 든 것으로 만들 때와 같다). 그래서 창을 닫아도 잃는 것이 없다.
 *
 * @param title 창 제목.
 * @param hint 가운데 칸 설명(무엇을 올리는지).
 * @param initial 처음부터 올라가 있을 것(지금 값) — 없으면 빈 칸.
 * @param submitLabel 등록 버튼 이름.
 * @param onSubmit 올린 아이템(수량 그대로의 사본). 화면을 어떻게 할지는 부르는 쪽이 정한다.
 */
open class ItemInputMenu(
    override val owner: Any?,
    private val viewer: Player,
    title: String,
    private val hint: List<String> = emptyList(),
    initial: ItemStack? = null,
    private val submitLabel: String = "등록",
    private val back: (() -> Unit)? = null,
    private val onSubmit: (ItemStack) -> Unit,
) : Menu(27, Text.renderFlat("<dark_gray>$title</dark_gray>")) {

    private var placed: ItemStack? = initial?.takeIf { !it.type.isAir }?.clone()

    fun show() = open(viewer)

    override fun draw() {
        clear()
        val item = placed
        if (item == null) {
            set(SLOT_ITEM, Icon.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<yellow>여기에 아이템을 올리세요</yellow>", hint + listOf(
                "",
                "<gray>아래 <white>내 가방의 아이템을 클릭</white>하거나,</gray>",
                "<gray>아이템을 집어 이 칸에 놓으면 올라갑니다.</gray>",
                "<dark_gray>가방의 아이템은 빠지지 않습니다(사본).</dark_gray>",
            ))) { event -> takeCursor(event) }
        } else {
            set(SLOT_ITEM, Icon.annotate(item, lore = listOf("", "<gray>다른 아이템을 클릭하면 바꿉니다.</gray>", "<red>▶ 우클릭: 내리기</red>"))) { event ->
                if (event.isRightClick && event.cursor.type.isAir) {
                    placed = null
                    refresh()
                } else {
                    takeCursor(event)
                }
            }
        }
        set(SLOT_SUBMIT, Icon.of(if (item == null) Material.GRAY_DYE else Material.LIME_DYE, if (item == null) "<gray>" + submitLabel + "</gray>" else "<green>✔ " + submitLabel + "</green>",
            if (item == null) listOf("<gray>먼저 아이템을 올리세요.</gray>") else listOf("<yellow>▶ 클릭: 올린 아이템으로 " + submitLabel + "</yellow>"))) {
            val stack = placed ?: return@set
            onSubmit(stack.clone())
        }
        back?.let { go -> set(SLOT_BACK, Icon.back()) { go() } }
        fillEmpty(Icon.FILLER)
    }

    /** 가운데 칸을 커서에 든 아이템으로 눌렀다 — 커서는 그대로 두고 사본을 올린다. */
    private fun takeCursor(event: InventoryClickEvent) {
        val cursor = event.cursor
        if (cursor.type.isAir) return
        placed = cursor.clone()
        refresh()
    }

    override fun onPlayerInventoryClick(event: InventoryClickEvent): Boolean {
        val clicked = event.currentItem ?: return false
        if (clicked.type.isAir || !event.cursor.type.isAir) return false
        placed = clicked.clone()
        refresh()
        return true
    }

    companion object {
        const val SLOT_ITEM = 13
        const val SLOT_SUBMIT = 15
        const val SLOT_BACK = 18
    }
}
