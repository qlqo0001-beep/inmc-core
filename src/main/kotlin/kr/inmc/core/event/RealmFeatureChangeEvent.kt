package kr.inmc.core.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * 관리자가 한 기능의 "모드마다 따로 / 같이" 를 바꿨다(2026-10-15, [kr.inmc.core.integration.Realms]). 재시작 없이 바로 적용된다 —
 * 바꾼 직후부터 [kr.inmc.core.integration.Realms.scope] 가 새 값을 준다. 메인 스레드.
 *
 * 그 기능을 가진 플러그인은 접속자 전부에 대해 열린 그 기능의 화면을 닫고, 그 기능이 사람에게 건 효과를 다시 그린다.
 * 옛 칸의 자료는 지우지 않는다(다시 바꾸면 돌아온다).
 */
class RealmFeatureChangeEvent(
    val key: String,
    val shared: Boolean,
) : Event() {

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
