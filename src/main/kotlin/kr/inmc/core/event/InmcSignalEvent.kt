package kr.inmc.core.event

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * "InMC 플러그인에서 무슨 일이 일어났다" 는 범용 신호.
 *
 * 이게 있는 이유는 **업적이 낚시·랜덤박스·인벤키퍼를 컴파일 시점에 알면 안 되기 때문**이다.
 * 반대도 마찬가지다 — 낚시는 업적이 있는지 모른다. 발행하는 쪽은 한 줄을 적고, 듣는 쪽이
 * 없으면 [fire] 가 **이벤트 객체조차 만들지 않는다.**
 *
 * ## 모양을 이렇게 정한 이유
 *
 * - **[playerId] 가 필수고 [player] 는 null 일 수 있다.** 대회 우승자는 시상 시점에 접속해
 *   있지 않을 수 있다. `Player` 만 받으면 그 신호를 **아예 못 쏜다.** 진행 기록에는 UUID 면
 *   충분하고 `Player` 는 연출에만 필요하다.
 * - **[amount] 는 "몇 개가 일어났나" 하나의 뜻만 갖는다.** 크기·금액은 [data] 로 간다.
 *   한 출처에서 "크기"고 다른 데서 "개수"면 조건이 둘을 구분할 방법이 없다.
 * - **[subject] 는 [data] 한 칸의 복제다.** 빠른 길(주축 비교)과 일반 길(`data[k]==v`)을
 *   같이 주기 위한 것이므로 "중복이니 정리"하지 말 것.
 * - **취소할 수 없다.** 과거형이다. 취소되면 낚시는 잡았다는데 우리는 아니게 된다.
 *
 * 소수를 [data] 에 넣을 때는 **발행하는 쪽에서** 형식을 고정하라(`"%.1f"`).
 * `42.10000000001` 은 문자열 비교가 영원히 안 맞고, 조용히 안 맞는다.
 */
class InmcSignalEvent(
    /** 어느 플러그인인가. 소문자. 예: `fishing` · `urb` · `invkeeper`. */
    val source: String,
    /** 무슨 일인가. 소문자. 예: `catch` · `open` · `grave`. */
    val type: String,
    val playerId: UUID,
    /** 지금 접속해 있으면 그 사람. 연출에만 쓴다. */
    val player: Player?,
    /** 주축. 물고기 id, 상자 이름 등. */
    val subject: String,
    /** 몇 개가 일어났나. 그 뜻 하나뿐이다. */
    val amount: Long,
    val data: Map<String, String>,
) : Event() {

    override fun getHandlers(): HandlerList = HANDLERS

    /** `subject` 든 `data` 든 어느 쪽으로 물어도 같은 답이 나온다. */
    fun value(key: String): String? = data[key]

    companion object {

        @JvmStatic
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS

        /**
         * 발행한다. **구독자가 없으면 아무것도 만들지 않는다.**
         *
         * `data` 를 람다로 받는 것이 그 때문이다 — 맵을 만드는 비용조차 아무도 안 들을 때는
         * 치르지 않는다. 낚시의 `CatchService.grant` 처럼 잦은 자리에 들어가므로 중요하다.
         *
         * [player] 기본값이 `Bukkit.getPlayer(playerId)` 라 **발행하는 쪽은 온라인 여부를
         * 신경 쓸 필요가 없다.**
         */
        @JvmStatic
        fun fire(
            source: String,
            type: String,
            playerId: UUID,
            subject: String,
            amount: Long = 1L,
            player: Player? = Bukkit.getPlayer(playerId),
            data: () -> Map<String, String> = { emptyMap() },
        ) {
            if (HANDLERS.registeredListeners.isEmpty()) return
            // 듣는 쪽이 PlayerStore 를 만진다. 워커에서 쏘면 그게 워커에서 돈다.
            require(Bukkit.isPrimaryThread()) { "InmcSignalEvent 는 메인 스레드에서만 발행한다" }
            InmcSignalEvent(
                source = source.lowercase(),
                type = type.lowercase(),
                playerId = playerId,
                player = player,
                subject = subject,
                amount = amount,
                data = data(),
            ).callEvent()
        }

        /** 듣는 쪽이 하나라도 있는가. 발행하는 쪽이 비싼 준비를 건너뛸 때 쓴다. */
        @JvmStatic
        fun hasListeners(): Boolean = HANDLERS.registeredListeners.isNotEmpty()
    }
}
