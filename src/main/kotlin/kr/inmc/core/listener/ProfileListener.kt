package kr.inmc.core.listener

import kr.inmc.core.store.PlayerStore
import kr.inmc.core.store.Profile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * core 가 등록하는 리스너(다른 하나는 유령 좌클릭을 가리는 `input/Clicks`).
 *
 * 하는 일은 uuid→이름·마지막 접속 시각을 적는 것뿐이다. 세 플러그인이 각자 들고 있던
 * 이름 캐시를 여기 하나로 모으기 위한 것이며, 그게 `CorePlugin` 의 30초 티커에 처음으로
 * 실제 할 일을 준다.
 *
 * MONITOR 인 이유는 기록만 하고 이벤트를 바꾸지 않기 때문이다. 다른 플러그인이 접속을
 * 취소하거나 이름을 바꿨다면 그 최종 결과를 적어야 한다.
 */
class ProfileListener(private val store: PlayerStore) : Listener {

    /**
     * **[PlayerStore.ready] 를 기다리지 않는다.**
     *
     * 저장소 로드 콜백은 첫 틱에야 도는데 접속은 그보다 빠를 수 있고(특히 `/reload` 직후),
     * 그때 건너뛰면 그 플레이어의 이름을 다음 재접속까지 잃는다. 부팅 창의 쓰기가 안전한
     * 것은 `load` 가 덮어쓰지 않고 병합하기 때문이다 — 그게 이 순서의 전제다.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        Profile.touch(store, player.uniqueId, player.name, System.currentTimeMillis())
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        Profile.touch(store, player.uniqueId, player.name, System.currentTimeMillis())
    }
}
