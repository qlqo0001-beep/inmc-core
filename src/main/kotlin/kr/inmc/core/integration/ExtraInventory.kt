package kr.inmc.core.integration

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 바닐라 가방 **밖에** 아이템을 들고 있는 곳(커스텀아이템의 장착 칸 등)을 사망 처리 플러그인(인벤키퍼)에게 알린다.
 * 그 칸은 죽을 때 **가방과 같이** 다뤄진다 — 드랍 비율·무덤·영혼각인 규칙을 똑같이 탄다.
 *
 * 둘은 서로를 모른다. 공급처는 이 창구에 꽂고, 사망 처리는 이 창구만 본다.
 *
 * **사망 처리 플러그인이 없을 때**는 공급처가 스스로 바닐라처럼 떨군다 — 약속은 이렇다: 사망 처리는 자기가 맡으면
 * `keepInventory` 를 켜고(인벤키퍼가 이미 그렇게 한다) 이 칸들을 [Provider.remove] 로 가져간다. 공급처는 사망 사건의
 * 끝(`HIGHEST`)에서 `keepInventory` 가 꺼져 있을 때만 스스로 떨군다. 게임룰 `keepInventory` 면 둘 다 아무것도 안 한다.
 *
 * 공유 목록이라 object 다 — core 클래스는 플러그인들이 공유하므로(`join-classpath`) 진짜 하나의 목록이 된다.
 */
object ExtraInventory {

    interface Provider {
        /** 구분용 이름. 같은 이름으로 다시 꽂으면 교체된다(리로드가 새지 않게). */
        val name: String

        /** 이 사람의 바깥 칸들. 빈 칸은 null. 목록의 순서가 칸 번호다. */
        fun items(player: Player): List<ItemStack?>

        /** [index] 칸을 비운다 — 사망 처리가 가져갔다. **즉시 저장해야 한다**(안 그러면 서버가 죽을 때 복사된다). */
        fun remove(player: Player, index: Int)
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
}
