package kr.inmc.core.integration

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * **채팅 숨김 창구** (2026-10-13). 어떤 사람의 공개 채팅이 "남에게 가지 않는 글"인지 — 그림자 뮤트(본인만 봄)·감옥 안 거리 채팅처럼
 * 사건은 취소되지 않았는데 `viewers()` 만 줄어든 경우 — 를 다른 플러그인(디스코드 중계 등)이 묻는 자리.
 *
 * - 상태의 주인은 inmc-essentials moderation 기능([provide] 로 끼우고 `disable()` 에서 [withdraw]). 여럿이 끼울 수 있고 **하나라도 참이면** 숨김.
 * - **어느 스레드에서나** 부른다(비동기 채팅 MONITOR). 공급자는 메모리만 읽어야 한다.
 * - 쓰는 쪽: 글을 서버 밖으로 보내거나 `viewers()` 를 보지 않고 전파하는 곳(inmc-discord `ChatListener.onChatSent`)은 `isHidden(보낸 사람)` 이면 보내지 않는다.
 */
object ChatVisibility {

    private class Provider(val owner: String, val hidden: (UUID) -> Boolean)

    private val providers = CopyOnWriteArrayList<Provider>()

    /** [owner](플러그인·기능 이름) 의 공급자를 끼운다 — 같은 이름은 바꿔 끼운다. */
    @JvmStatic
    fun provide(owner: String, hidden: (UUID) -> Boolean) {
        providers.removeIf { it.owner == owner }
        providers += Provider(owner, hidden)
    }

    @JvmStatic
    fun withdraw(owner: String) {
        providers.removeIf { it.owner == owner }
    }

    /** 이 사람의 공개 채팅이 남에게 가지 않는 글인가(그림자 뮤트 등). 공급자가 없으면 false. 공급자가 던지면 그 공급자는 false 로 친다. */
    @JvmStatic
    fun isHidden(id: UUID): Boolean = providers.any { runCatching { it.hidden(id) }.getOrDefault(false) }

    /** 끼운 공급자 수(검증·테스트용). */
    @JvmStatic
    fun providerCount(): Int = providers.size
}
