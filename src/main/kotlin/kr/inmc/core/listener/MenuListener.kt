package kr.inmc.core.listener

import kr.inmc.core.InmcHost
import kr.inmc.core.gui.Menu
import kr.inmc.core.util.Text
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import java.util.Collections
import java.util.WeakHashMap
import java.util.logging.Level

/**
 * Single routing point for every plugin menu.
 *
 * Menus are identified by their [org.bukkit.inventory.InventoryHolder], so one listener covers
 * all of them and no menu has to register anything of its own.
 *
 * Every handler is wrapped. A menu that throws part-way through drawing otherwise leaves the
 * admin looking at a half-filled window with no idea anything went wrong - the only trace is a
 * stack trace in a console they may not be watching. Worse, the click stays uncancelled, so a
 * failure in a screen that holds real items can hand those items to the player. Cancelling and
 * saying so is the only honest outcome.
 */
class MenuListener(private val host: InmcHost) : Listener {

    private fun menuOf(holder: Any?): Menu? = holder as? Menu

    @EventHandler(priority = EventPriority.HIGH)
    fun onClick(event: InventoryClickEvent) {
        val menu = menuOf(event.view.topInventory.holder) ?: return
        if (!firstDelivery(event)) return
        try {
            menu.handleClick(event)
        } catch (t: Throwable) {
            // Cancel first: whatever the menu was doing did not finish, so the click must not
            // be allowed to move items on its own.
            event.isCancelled = true
            report(menu, event.whoClicked as? Player, "클릭", t)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDrag(event: InventoryDragEvent) {
        val menu = menuOf(event.view.topInventory.holder) ?: return
        if (!firstDelivery(event)) return
        try {
            menu.onDrag(event)
        } catch (t: Throwable) {
            event.isCancelled = true
            report(menu, event.whoClicked as? Player, "드래그", t)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onClose(event: InventoryCloseEvent) {
        val menu = menuOf(event.view.topInventory.holder) ?: return
        if (!firstDelivery(event)) return
        try {
            menu.onClose(event)
        } catch (t: Throwable) {
            // A close handler is what returns staged items, so a failure here can genuinely eat
            // an admin's items. It is logged loudly rather than swallowed.
            report(menu, event.player as? Player, "창 닫기", t)
        }
    }

    /**
     * Tells both the console and the player.
     *
     * The player gets the exception type but not a stack trace: enough to know the screen failed
     * and to say something useful when reporting it, without pasting internals into chat.
     */
    private fun report(menu: Menu, player: Player?, action: String, error: Throwable) {
        // 이 리스너는 플러그인마다 하나씩 있고 이벤트를 먼저 받은 쪽이 처리한다 — 그 플러그인 이름으로 찍으면
        // 커스텀아이템 화면의 오류가 "[inmc-urb]" 로 나와 엉뚱한 곳을 찾게 된다. 화면 주인의 이름으로 찍는다.
        val owner = (menu.owner as? InmcHost)?.plugin ?: host.plugin
        owner.logger.log(
            Level.SEVERE,
            "GUI 처리 실패 (" + menu.javaClass.simpleName + " / " + action + ")",
            error,
        )
        player?.closeInventory()
        player?.sendMessage(
            Text.render(
                "<red>화면을 처리하는 중 오류가 발생했습니다. <gray>(" +
                    error.javaClass.simpleName + ")</gray></red>",
            ),
        )
        player?.sendMessage(
            Text.render("<dark_gray>콘솔 로그를 확인해주세요. 창을 다시 열면 계속할 수 있습니다.</dark_gray>"),
        )
    }

    companion object {
        /**
         * 이미 처리한 이벤트. **마지막 하나만 기억하면 안 된다** — 클릭 처리 안에서 창을 닫으면
         * `InventoryCloseEvent` 가 그 자리에서 끼어들어 "마지막"을 덮고, 다음 플러그인의 리스너가 같은
         * 클릭을 새것으로 보고 또 처리한다(무덤 "모두 회수" 한 번에 회수 처리가 두 번 돌았다).
         * 키가 약한 참조라 끝난 이벤트는 GC 가 치운다. 이벤트는 `equals` 를 재정의하지 않아 동일성으로 비교된다.
         */
        private val handled: MutableSet<Any> = Collections.newSetFromMap(WeakHashMap())

        /**
         * 같은 이벤트를 **한 번만** 처리하게 한다.
         *
         * 이 리스너는 core `Menu` 를 쓰는 플러그인마다 하나씩 등록되고(2026-09-23 기준 7개), 각각이
         * **모든** core `Menu` 를 받는다. 거르지 않으면 클릭 한 번이 플러그인 수만큼 처리된다 —
         * 토글은 7번 뒤집히고(짝수 개면 아무 일도 안 한 것처럼 보인다), 지급 버튼은 7번 주고,
         * 맡긴 아이템을 돌려주는 닫기 처리는 7번 돌려준다. 실서버에서 대회 사전 신청 한 번이
         * 신청/취소 7줄로 찍혀 드러났다.
         *
         * 등록을 하나로 줄이지 않은 이유: 그 하나를 등록한 플러그인이 꺼지면 남의 화면까지 죽는다.
         * `owner` 로 가르지 않은 이유: `owner` 가 없는 화면은 아무도 안 받아 클릭이 취소되지 않는다.
         *
         * 이 클래스는 core 가 한 벌만 싣고(`join-classpath`) 인벤토리 이벤트는 메인 스레드에서만
         * 나므로, 이 기록 하나를 모든 플러그인이 공유한다.
         */
        internal fun firstDelivery(event: Any): Boolean = handled.add(event)
    }
}
