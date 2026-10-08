package kr.inmc.core.integration

import org.bukkit.Bukkit
import java.util.logging.Logger

/**
 * 모든 플러그인이 켜진 뒤(`ServerLoadEvent`) **한 번** — 아이템 공급처가 정말 없는지 본다.
 *
 * 전에는 `CustomItemHook`·`MMOItemsHook` 이 `setup()` 때 "없음" 을 찍었다. 그런데 우리 공급처(inmc-customitems)는
 * 다른 플러그인보다 **나중에** 켜지고, 공급처 목록은 companion 공유라 나중에 꽂혀도 동작은 맞았다 — 로그만 거짓이었다
 * (플러그인 열 개가 "커스텀 아이템 플러그인 없음", 열여섯 개가 "MMOItems 미설치"). 켜진 뒤에 한 번 보면 참말만 남는다.
 */
object IntegrationReport {

    private val THIRD_PARTY_ITEMS = listOf("ItemsAdder", "Nexo", "Oraxen", "EcoItems")

    /** core 가 등록한다 — 정상 부팅(`STARTUP`)과 `/reload`(`RELOAD`) 둘 다 온다. */
    class Listener(private val logger: Logger) : org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler
        fun onServerLoad(@Suppress("UNUSED_PARAMETER") event: org.bukkit.event.server.ServerLoadEvent) {
            report(logger)
            kr.inmc.core.ApiVersionCheck.report(logger)
        }
    }

    fun report(logger: Logger) {
        val manager = Bukkit.getPluginManager()
        if (!CustomItemHook.hasFirstParty() && THIRD_PARTY_ITEMS.none { manager.isPluginEnabled(it) }) {
            logger.info("커스텀 아이템 공급처 없음(inmc-customitems·ItemsAdder·Nexo·Oraxen·EcoItems) — 네임스페이스 참조는 스냅샷으로 대체됩니다")
        }
        if (!manager.isPluginEnabled("MMOItems")) {
            logger.info("MMOItems 미설치 — mmoitems: 참조는 스냅샷으로 대체됩니다")
        }
    }
}
