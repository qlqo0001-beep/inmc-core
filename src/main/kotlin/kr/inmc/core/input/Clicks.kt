package kr.inmc.core.input

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.Collections
import java.util.UUID
import java.util.WeakHashMap

/**
 * 손 흔들기가 만든 **유령 좌클릭**을 가린다.
 *
 * 아이템을 쓰는 우클릭(낚싯대 던지기·감기 등)을 하면 클라이언트가 팔 휘두르기 패킷도 보내고, 서버는 그것을
 * `LEFT_CLICK_AIR` 로 한 번 더 부른다 — **같은 틱에.** 좌/우를 가리는 곳(낚시 미니게임·힘겨루기, 커스텀아이템 좌클릭 기능,
 * 인첸트 휘두르기)이 그대로 받으면 우클릭 한 번이 "우 + 좌" 가 된다.
 *
 * 규칙은 2세대 낚시의 `lastRightClickTick` 그대로다: **같은 틱에 우클릭(블록·허공·개체)이나 다른 좌클릭이 먼저 있었으면 그
 * 좌클릭은 유령이다.** 우클릭은 유령이 아니다.
 *
 * 판정은 core 가 등록한 이 리스너가 **LOWEST** 에서 이벤트마다 한 번 내려 그 이벤트에 붙여 둔다. 그래서 어느 우선순위에서
 * [isGhost] 를 물어도 답이 같다 — 기록한 뒤에 물으면 자기 자신을 "먼저 온 좌클릭" 으로 본다.
 */
object Clicks : Listener {

    /** 순수 판정. 서버 없이 시험한다. */
    class Tracker {
        private val lastRight = HashMap<UUID, Int>()
        private val lastLeft = HashMap<UUID, Int>()

        fun right(player: UUID, tick: Int) {
            lastRight[player] = tick
        }

        /** 좌클릭 하나를 넣는다. 유령이었으면 true. */
        fun left(player: UUID, tick: Int): Boolean {
            val ghost = lastRight[player] == tick || lastLeft[player] == tick
            lastLeft[player] = tick
            return ghost
        }

        fun forget(player: UUID) {
            lastRight.remove(player)
            lastLeft.remove(player)
        }
    }

    private val tracker = Tracker()

    /** 유령으로 판정된 이벤트. 약한 참조 — 배달이 끝난 이벤트는 저절로 빠진다. */
    private val ghosts: MutableSet<PlayerInteractEvent> = Collections.newSetFromMap(WeakHashMap())

    /** 이 좌클릭이 우클릭(이나 같은 틱의 다른 좌클릭)에 딸려 온 유령인가. 우클릭·다른 동작은 언제나 false. */
    fun isGhost(event: PlayerInteractEvent): Boolean = event in ghosts

    /**
     * 이 사람의 기록을 지운다. 나갈 때 core 가 부른다.
     *
     * 검증기도 부른다 — 검증은 한 틱에 여러 번 누른다(진짜 손으로는 못 한다). 누르기 전에 비우지 않으면 앞의 누름이 뒤를 유령으로 만든다.
     */
    fun forget(player: UUID) = tracker.forget(player)

    // 허공 클릭은 처음부터 '취소됨'으로 태어난다 — ignoreCancelled 로 받으면 허공 클릭이 통째로 빠진다.
    @EventHandler(priority = EventPriority.LOWEST)
    fun onInteract(event: PlayerInteractEvent) {
        val id = event.player.uniqueId
        val tick = Bukkit.getCurrentTick()
        when (event.action) {
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> tracker.right(id, tick)
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> if (tracker.left(id, tick)) ghosts += event
            else -> Unit
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        tracker.right(event.player.uniqueId, Bukkit.getCurrentTick())
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        forget(event.player.uniqueId)
    }
}
