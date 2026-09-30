package kr.inmc.core.integration

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 플레이어가 **들고 다니는 보관함**(커스텀아이템의 배낭 등) — 가방 밖이지만 "가진 것"으로 치는 칸들.
 *
 * 열쇠·참가 아이템·화폐 실물·상점 판매처럼 **가방에서 세고 빼는 곳**이 가방 다음에 이 창구를 같이 본다(사용자 결정
 * 2026-09-30 — "배낭 안 물건도 인벤에 있는 것처럼"). 순서는 늘 **가방 먼저, 그다음 보관함**(공급처가 준 순서대로)이다.
 *
 * 사망 처리([ExtraInventory])와는 다른 창구다 — 배낭의 내용물은 배낭 아이템에 붙어 다니므로 죽을 때 따로 떨구면
 * 두 번 나온다. 여기 칸들은 **세고 빼는 데만** 쓴다.
 *
 * 공유 목록이라 object 다 — core 클래스는 플러그인들이 공유하므로(`join-classpath`) 진짜 하나의 목록이 된다.
 * 전부 메인 스레드에서 부른다.
 */
object CarriedStorage {

    interface Provider {
        /** 구분용 이름. 같은 이름으로 다시 꽂으면 교체된다(리로드가 새지 않게). */
        val name: String

        /**
         * 이 사람이 지금 가진 보관함들, 쓰는 순서대로. **다른 화면이 열어 두고 고치는 중인 것은 빼야 한다** — 두 곳에서
         * 고치면 복사되거나 사라진다. 같은 보관함(복사된 배낭)이 두 번 나와서도 안 된다 — 두 번 센다.
         */
        fun containers(player: Player): List<Container>
    }

    interface Container {
        /** 칸들의 사본. 빈 칸은 null. */
        fun contents(): List<ItemStack?>

        /** 칸들을 통째로 바꾼다([contents] 와 같은 길이). **곧바로 저장해야 한다** — 안 그러면 서버가 죽을 때 복사된다. */
        fun write(contents: List<ItemStack?>)
    }

    private val providers = CopyOnWriteArrayList<Provider>()

    fun register(provider: Provider) {
        providers.removeIf { it.name == provider.name }
        providers += provider
    }

    fun unregister(provider: Provider) {
        providers.remove(provider)
    }

    fun providers(): List<Provider> = providers.toList()

    /** 이 사람의 모든 보관함. */
    fun containers(player: Player): List<Container> = providers.flatMap { it.containers(player) }

    /** 보관함에 든 [test] 인 것의 개수(가방은 빼고). */
    fun count(player: Player, test: (ItemStack) -> Boolean): Int =
        containers(player).sumOf { container -> container.contents().sumOf { if (it != null && !it.type.isAir && test(it)) it.amount else 0 } }

    /**
     * 보관함에서 [test] 인 것을 [amount] 개까지 뺀다(가방은 빼고). 뺀 개수. 보관함마다 한 번에 쓴다.
     * [taken] 이 있으면 뺀 것의 사본(개수는 뺀 만큼)을 거기 모은다 — 되돌려 줄 때 쓴다.
     */
    fun take(player: Player, amount: Int, taken: MutableList<ItemStack>? = null, test: (ItemStack) -> Boolean): Int {
        var left = amount
        for (container in containers(player)) {
            if (left <= 0) break
            val contents = container.contents().toMutableList()
            val removal = plan(contents.map { stack -> stack?.takeIf { !it.type.isAir && test(it) }?.amount }, left)
            if (removal.isEmpty()) continue
            for ((index, used) in removal) {
                val stack = contents[index] ?: continue
                taken?.add(stack.clone().apply { this.amount = used })
                contents[index] = if (used >= stack.amount) null else stack.clone().apply { this.amount -= used }
                left -= used
            }
            container.write(contents)
        }
        return amount - left
    }

    /**
     * 칸마다 맞는 것의 개수([amounts], 안 맞으면 null)에서 [want] 개를 앞 칸부터 뺄 계획 — (칸, 뺄 개수).
     * 서버 없이 도는 계산이다.
     */
    fun plan(amounts: List<Int?>, want: Int): List<Pair<Int, Int>> {
        var left = want
        val out = ArrayList<Pair<Int, Int>>()
        for ((index, amount) in amounts.withIndex()) {
            if (left <= 0) break
            if (amount == null || amount <= 0) continue
            val used = minOf(left, amount)
            out += index to used
            left -= used
        }
        return out
    }
}
