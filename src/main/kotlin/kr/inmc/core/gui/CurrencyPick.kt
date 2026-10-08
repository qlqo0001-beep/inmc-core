package kr.inmc.core.gui

import kr.inmc.core.economy.Currencies
import kr.inmc.core.integration.EconomyHook
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 화폐 고르기(2026-10-08, 사용자 결정 "돈을 주는 곳 전부") — 보상·참가비·상자 비용의 화폐를 [PickMenu] 로 고른다.
 * 첫 칸은 "기본 화폐"(id `""`, 화폐 플러그인의 기본). 화폐가 하나뿐인 서버는 묻지 않는 것이 맞다 — 부르는 쪽이 [EconomyHook.multiCurrency] 를 본다.
 */
object CurrencyPick {

    /** [current] 가 지금 값(빈 문자열 = 기본). [onPick] 은 고른 id(기본이면 `""`). */
    @JvmStatic
    fun open(owner: Any?, viewer: Player, current: String?, back: (() -> Unit)? = null, onPick: (String) -> Unit) {
        val options = listOf("" to "기본 화폐") + Currencies.all().map { it.id to it.name }
        val chosen = options.firstOrNull { it.first == current.orEmpty() } ?: options[0]
        PickMenu(
            owner, viewer, "화폐 고르기", options,
            icon = { (id, name) ->
                Icon.of(
                    if (id.isEmpty()) Material.GOLD_NUGGET else Material.GOLD_INGOT,
                    "<yellow>$name</yellow>",
                    listOf("<gray>" + (if (id.isEmpty()) "화폐 플러그인의 기본 화폐" else "id: $id") + "</gray>"),
                )
            },
            selected = { setOf(chosen) },
            back = back,
        ) { picked -> onPick(picked.first) }.show()
    }

    /** 편집 화면 로어 한 줄 — "화폐: 기본(돈)" 꼴. */
    @JvmStatic
    fun line(economy: EconomyHook, current: String?): String =
        "<gray>화폐: <white>" + (if (current.isNullOrBlank()) "기본" else economy.currencyName(current)) + "</white></gray>"
}
