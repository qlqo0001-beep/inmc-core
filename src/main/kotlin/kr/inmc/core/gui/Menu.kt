package kr.inmc.core.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

/**
 * 자작 인벤토리 메뉴 베이스. GUI 라이브러리를 쓰지 않는다("의존성 0" 목표).
 *
 * 4세대에는 이 파일이 세 플러그인에 글자 단위로 같은 내용으로 복사돼 있었고,
 * 서비스 로케이터 타입을 받는 생성자 인자 하나만 서로 달랐다. 그 인자를 떼어내
 * 각 플러그인이 얇은 서브클래스로 다시 붙이게 했다 — 기존 메뉴 70여 개는 손대지 않았다.
 *
 * Clicks are cancelled by default and dispatched through a per-slot action map; menus that
 * genuinely need the player to move items (the reward editor, the loot window) opt out by
 * overriding [isSlotEditable].
 */
abstract class Menu(
    val size: Int,
    title: Component,
) : InventoryHolder {

    private val inv: Inventory = Bukkit.createInventory(this, size, title)
    private val actions = HashMap<Int, (InventoryClickEvent) -> Unit>()

    /**
     * 이 화면을 소유한 플러그인의 서비스 로케이터.
     *
     * 각 플러그인은 리로드할 때 열려 있는 자기 GUI 를 닫는다 — 정의 객체가 전부 교체되므로
     * 그대로 두면 관리자가 버려진 객체를 계속 편집하게 되고, 저장은 되는데 반영은 안 된다.
     * 그 판별을 예전에는 `holder !is <플러그인>.gui.Menu` 로 했는데, 그러면 **core 가 소유한
     * 화면(공용 확인창·설정 화면)이 청소에서 빠진다.** core 메뉴는 플러그인의 Menu 서브클래스가
     * 아니기 때문이다. 로그에는 아무것도 안 남는다.
     *
     * 그래서 소유자를 값으로 들고 다닌다. 플러그인의 얇은 `Menu` 층이 이걸 자기 로케이터로
     * 재정의하고, core 메뉴는 생성자로 받는다. 판별은 `holder is core.Menu && holder.owner === this`.
     */
    open val owner: Any? get() = null

    final override fun getInventory(): Inventory = inv

    /** Fills the inventory. Called on open and whenever state changes. */
    abstract fun draw()

    open fun onClose(event: InventoryCloseEvent) {}

    /** Slots the player may freely put items into or take items out of. */
    open fun isSlotEditable(slot: Int): Boolean = false

    /** True when the player may shift-click items in from their own inventory. */
    open fun acceptsShiftInsert(): Boolean = false

    open fun onDrag(event: InventoryDragEvent) {
        val topSize = size
        val touchesLocked = event.rawSlots.any { it < topSize && !isSlotEditable(it) }
        if (touchesLocked) event.isCancelled = true
    }

    open fun handleClick(event: InventoryClickEvent) {
        val raw = event.rawSlot
        val inTop = raw in 0 until size

        if (!inTop) {
            // Clicking one's own inventory: only allow shift-inserting into menus that want it.
            if (event.isShiftClick && !acceptsShiftInsert()) event.isCancelled = true
            return
        }

        if (!isSlotEditable(raw)) event.isCancelled = true
        actions[raw]?.invoke(event)
    }

    fun open(player: Player) {
        draw()
        player.openInventory(inv)
    }

    fun refresh() {
        draw()
    }

    // --- drawing helpers -------------------------------------------------------

    protected fun clear() {
        inv.clear()
        actions.clear()
    }

    protected fun set(slot: Int, stack: ItemStack?, onClick: ((InventoryClickEvent) -> Unit)? = null) {
        if (slot !in 0 until size) return
        inv.setItem(slot, stack)
        if (onClick != null) actions[slot] = onClick else actions.remove(slot)
    }

    protected fun action(slot: Int, onClick: (InventoryClickEvent) -> Unit) {
        if (slot in 0 until size) actions[slot] = onClick
    }

    protected fun fillBorder(stack: ItemStack) {
        val rows = size / 9
        for (i in 0 until size) {
            val row = i / 9
            val col = i % 9
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                if (inv.getItem(i) == null) inv.setItem(i, stack)
            }
        }
    }

    protected fun fillEmpty(stack: ItemStack) {
        for (i in 0 until size) {
            if (inv.getItem(i) == null) inv.setItem(i, stack)
        }
    }

    protected fun viewers(): List<Player> = inv.viewers.filterIsInstance<Player>()
}
