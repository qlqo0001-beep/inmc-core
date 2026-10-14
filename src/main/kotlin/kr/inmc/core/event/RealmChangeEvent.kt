package kr.inmc.core.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * 사람의 모드가 바뀌었다(2026-10-15, [kr.inmc.core.integration.Realms]). 모드 공급처(inmc-worlds)가 **몸을 다 바꾼 뒤** 메인 스레드에서 쏜다.
 * 사람별 캐시를 `(uuid, scope)` 로 쥐는 플러그인은 따로 할 일이 없고, 사람에게 건 것(능력치 속성·장비 효과·이름표)과 열린 화면만 다시 그린다.
 *
 * 취소할 수 없다 — 과거형이다. 막아야 하는 활동(낚시 힘겨루기·진행 중인 판)은 여기서 무른다.
 *
 * @param from 떠난 모드(처음 들어온 사람은 null). @param firstEntry 이 모드에 처음 들어왔다(첫 킷·첫 스폰).
 */
class RealmChangeEvent(
    val player: Player,
    val from: String?,
    val to: String,
    val firstEntry: Boolean,
) : Event() {

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
