package kr.inmc.core.gui

import kr.inmc.core.util.Particles
import kr.inmc.core.util.Text
import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.plugin.Plugin

/**
 * 입자 고르기 — 이 서버 판의 입자 **전부**를 한국어 이름으로 보여 주고, 우클릭하면 눈앞에 3초 뿌려 보여 준다(2026-10-14, 사용자 요청
 * "마크 안의 모든 입자를 GUI 로 보여 주고 예시를 볼 수 있게 · 한국 이름으로"). 채팅에 영어 이름을 치던 것을 대신한다.
 *
 * 미리보기는 **창을 닫고** 뿌린다 — 창이 열려 있으면 눈앞의 입자가 창에 가린다. 3초 뒤 같은 쪽으로 다시 연다(그 사이 다른 창을 열었으면 안 연다).
 * 자료가 필요한 입자(먼지 = 색 · 블록 조각 = 블록 …)는 기본 자료로 보인다([Particles.defaultData]) — 쓰는 쪽도 같은 기본 자료로 뿌린다.
 *
 * @param plugin 미리보기 일정을 걸 플러그인.
 * @param current 지금 고른 것(소문자 id) — 반짝임으로 표시. 없으면 null.
 * @param onPick 좌클릭한 입자. 화면을 어떻게 할지는 부르는 쪽이 정한다(보통 앞 화면을 다시 연다).
 */
class ParticlePickMenu(
    override val owner: Any?,
    private val plugin: Plugin,
    private val viewer: Player,
    title: String = "입자 고르기",
    private val current: String? = null,
    private val back: (() -> Unit)? = null,
    private val onPick: (Particle) -> Unit,
) : Menu(54, Text.renderFlat("<dark_gray>$title</dark_gray>")) {

    private val options = Particles.all().sortedBy { Particles.name(it) }
    private var page = startPage()

    fun show() = open(viewer)

    private fun startPage(): Int {
        val index = options.indexOfFirst { Particles.id(it) == current?.lowercase() }
        return if (index < 0) 0 else index / Paging.PER_PAGE
    }

    override fun draw() {
        clear()
        page = Paging.clamp(page, options.size)
        for ((slot, particle) in Paging.slice(options, page).withIndex()) {
            val chosen = Particles.id(particle) == current?.lowercase()
            val icon = Icon.of(Particles.icon(particle), (if (chosen) "<green>✔ " else "<yellow>") + Particles.name(particle), buildList {
                add("<dark_gray>" + Particles.id(particle) + "</dark_gray>")
                if (Particles.needsData(particle)) add("<gray>색·블록 같은 자료가 필요한 입자 — 기본 자료로 뿌립니다</gray>")
                add("")
                add("<yellow>▶ 좌클릭: 고르기</yellow>")
                add("<aqua>▶ 우클릭: 미리보기(눈앞에 3초)</aqua>")
            }).also { if (chosen) it.editMeta { meta -> meta.setEnchantmentGlintOverride(true) } }
            set(slot, icon) { event -> click(event, particle) }
        }
        fillEmpty(Icon.FILLER)
        set(SLOT_INFO, Icon.of(org.bukkit.Material.BOOK, "<aqua>입자 " + options.size + "개</aqua>", listOf(
            "<gray>한국어 이름 순입니다. 우클릭으로 먼저 보고 고르세요.</gray>",
        )))
        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { page--; refresh() }
        if (page < Paging.pageCount(options.size) - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { page++; refresh() }
        back?.let { go -> set(Paging.SLOT_BACK, Icon.back()) { go() } }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun click(event: InventoryClickEvent, particle: Particle) {
        if (!event.isRightClick) {
            onPick(particle)
            return
        }
        preview(particle)
    }

    private fun preview(particle: Particle) {
        viewer.closeInventory()
        viewer.sendActionBar(Text.render("<aqua>미리보기: <white>" + Particles.name(particle) + "</white></aqua>"))
        var ticks = 0
        Bukkit.getScheduler().runTaskTimer(plugin, { task ->
            if (!viewer.isOnline || ticks >= PREVIEW_TICKS) {
                task.cancel()
                // 그 사이 다른 창(채팅 입력·다른 메뉴)을 열었으면 덮지 않는다.
                if (viewer.isOnline && viewer.openInventory.topInventory.holder !is Menu) open(viewer)
                return@runTaskTimer
            }
            val eye = viewer.eyeLocation
            val at = eye.clone().add(eye.direction.normalize().multiply(2.5))
            Particles.show(viewer, particle, at, 12, 0.35, 0.35, 0.35, 0.02)
            ticks += PREVIEW_STEP
        }, 0L, PREVIEW_STEP.toLong())
    }

    private companion object {
        const val SLOT_INFO = 49
        const val PREVIEW_TICKS = 60
        const val PREVIEW_STEP = 5
    }
}
