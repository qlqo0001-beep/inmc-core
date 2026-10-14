package kr.inmc.core.event

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * 투명을 켜거나 끄며 내보내는 **가짜 접속·퇴장**(2026-10-15). 투명 기능(inmc-essentials)이 메인 스레드에서 쏜다 —
 * 퇴장([joining] false)은 투명 표식을 걸기 **전에**, 접속(true)은 표식을 푼 **뒤에**. 그래서 듣는 쪽은 그 사람을 투명이 아닌 사람으로 본다.
 *
 * - [message] 는 쏘는 쪽의 기본 문구로 시작한다. 접속 문구의 주인(타이틀포지)이 진짜 접속·퇴장과 같은 모양으로 바꾸거나 null 로 지운다
 *   (진짜 때 지웠을 상황 — `titleforge.join.silent` 등). null 이면 아무 데도 나가지 않는다.
 * - 쏜 쪽이 사건 뒤에 [message] 를 못 보던 사람과 본인에게 보낸다. 디스코드는 MONITOR 에서 진짜 접속·퇴장처럼 채널에 올린다.
 */
class FakeConnectionEvent(
    val player: Player,
    val joining: Boolean,
    var message: Component?,
) : Event() {

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
