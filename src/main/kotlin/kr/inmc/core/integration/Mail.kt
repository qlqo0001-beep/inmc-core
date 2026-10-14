package kr.inmc.core.integration

import kr.inmc.core.reward.Mailbox
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Level
import java.util.logging.Logger

/**
 * **우편 창구** (2026-10-10). 우편함을 가진 플러그인(inmc-essentials 의 mail 기능)이 [Provider] 로 꽂히고, 보상을 미뤄 두는 쪽은
 * 누가 우편을 맡는지 모른 채 [send] 로 넣는다. [Currencies][kr.inmc.core.economy.Currencies] 와 같은 모양 — 공급처는 **하나**다.
 *
 * - core [Mailbox](플러그인마다 하나씩 있던 `data/mailbox.yml`)는 공급처가 있으면 이리로 넘긴다 — 사람은 우편함 하나만 열면 된다.
 *   [Parcel.origin] 으로 어느 플러그인의 몫인지 가른다(숫자야구 화면은 제 것만 센다).
 * - 공급처가 없으면 [send] 는 false — 부른 쪽이 제 길(바닥에 떨구기·제 우편함)로 간다.
 * - core 의 클래스는 모든 플러그인이 공유하므로(`join-classpath: true`) 이 객체가 곧 서버 전체에 하나다.
 *
 * **스레드**: 모든 메서드는 메인 스레드에서 불러도 된다. 그래서 공급처의 [Provider.deposit] 은 **빨라야 하고 돌아오는 순간 잃지 않아야
 * 한다**(메모리에 넣고 DB 쓰기 스레드에 넘긴다 — 돌아온 뒤 서버가 죽어도 쓰기 줄에 든 것은 남는 정도). 메인에서 DB 를 기다리지 않는다.
 * [register]/[unregister] 는 **메인 스레드에서** 부른다(알림이 그 스레드에서 돈다 — core [Mailbox] 는 메인이 아니면 메인으로 넘긴다).
 *
 * **명령어의 자리 표시**: [Parcel.commands] 에서 받는 사람 이름은 **`{player}` 하나**다. 넣는 쪽이 제 별칭(`{플레이어}`·`{플레이어네임}` …)을
 * [PLAYER_TOKEN] 으로 바꿔 넣고(core [Mailbox] 는 넘길 때 호스트의 별칭을 바꾼다), 공급처는 받을 때 `{player}` 를 받는 사람 이름으로,
 * `%…%` 를 받는 사람 기준 PAPI 로 바꿔 콘솔로 실행한다. 그 밖의 `{…}` 는 그대로 나간다.
 */
object Mail {

    /** [Parcel.commands] 의 받는 사람 자리 — 이것 하나(위 KDoc). */
    const val PLAYER_TOKEN = "{player}"

    enum class Kind {
        /** 플러그인이 주는 보상(시즌 보상·가방이 찬 사람의 아이템). 공급처는 이것을 **스스로 지우지 않는다**. */
        REWARD,

        /** 사람이 보낸 편지(`/우편`). */
        LETTER,
    }

    /**
     * 우편 한 통. 아이템은 [ItemStack.serializeAsBytes] 의 바이트 — 받을 때 되살린다(되살리지 못하면 공급처가 알리고 버린다).
     *
     * @param origin 넣은 플러그인의 이름(`inmc-numbergame`). 화면이 제 것만 셀 때 쓴다.
     * @param source 사람이 읽는 출처("숫자야구 3자리 · 시즌 4 · 1위").
     * @param money 기본 화폐 금액. [moneyBy] 는 이름 있는 화폐(id → 금액).
     * @param commands 받을 때 콘솔이 실행하는 명령어. 받는 사람 자리는 `{player}` 만(위 KDoc) — 공급처가 받는 사람으로, `%…%` 는 PAPI 로 바꾼다.
     * @param at 넣은 시각(밀리초). [expiresAt] 이 null 이면 지우지 않는다.
     * @param key 넣은 쪽의 고유 열쇠(없으면 null). 같은 사람·[origin]·[key] 의 우편을 이미 쥐고 있으면 공급처는 **새로 넣지 않고 true** —
     *   core [Mailbox] 는 넘긴 것을 다음 저장 차례에야 제 파일에서 지우므로, 그 사이 죽으면 다음 부팅에 같은 것을 다시 넘긴다.
     */
    data class Parcel(
        val kind: Kind,
        val origin: String,
        val source: String,
        val senderId: UUID? = null,
        val senderName: String? = null,
        val text: String? = null,
        val items: List<ByteArray> = emptyList(),
        val money: Double = 0.0,
        val moneyBy: Map<String, Double> = emptyMap(),
        val commands: List<String> = emptyList(),
        val at: Long = System.currentTimeMillis(),
        val expiresAt: Long? = null,
        val key: String? = null,
    ) {
        /** 받을 것이 하나도 없다(글만 있는 편지는 비지 않았다). */
        fun isEmpty(): Boolean =
            items.isEmpty() && money <= 0.0 && moneyBy.values.none { it > 0.0 } && commands.isEmpty() && text.isNullOrBlank()
    }

    interface Provider {
        /**
         * 넣는다. 받지 못했으면 false — 부른 쪽이 제 길로 간다. **빠르고, 돌아오면 잃지 않을 것**(위 KDoc).
         * [Parcel.key] 가 있고 같은 사람·origin·key 의 우편을 이미 쥐고 있으면 넣지 않고 true(두 번 넘겨도 한 통).
         */
        fun deposit(playerId: UUID, parcel: Parcel): Boolean

        /** [origin] 이 null 이면 전부. */
        fun count(playerId: UUID, origin: String? = null): Int

        /** (공급처의 id, 우편). 오래된 것부터. [origin] 이 null 이면 전부. */
        fun entries(playerId: UUID, origin: String?): List<Pair<String, Parcel>>

        /** 받게 한다. 가방에 안 들어간 아이템은 우편에 남는다(core [Mailbox.claimAll] 과 같은 규칙). */
        fun claim(player: Player, origin: String?): Mailbox.ClaimResult

        /** 버린다(관리자 초기화). 버린 개수. */
        fun discard(playerId: UUID, origin: String?): Int
    }

    @Volatile
    private var provider: Provider? = null

    private class Watcher(val owner: String, val callback: () -> Unit)

    private val watchers = CopyOnWriteArrayList<Watcher>()

    /** 꽂는다. 이미 있으면 바꾼다(리로드). 꽂은 뒤 [listen] 한 곳에 알린다 — core [Mailbox] 가 여기서 남은 것을 넘긴다. */
    @JvmStatic
    fun register(provider: Provider) {
        this.provider = provider
        changed()
    }

    /** 꽂은 그 공급처일 때만 뺀다 — 새로 켜진 공급처를 옛것이 내려가면서 지우지 않게. */
    @JvmStatic
    fun unregister(provider: Provider) {
        if (this.provider !== provider) return
        this.provider = null
        changed()
    }

    @JvmStatic
    val available: Boolean get() = provider != null

    /**
     * 꽂히고 빠질 때 알림. 같은 [owner] 는 교체. **적재 순서가 보장되지 않으므로** 받는 쪽은 켤 때 [available] 을 한 번 보고, 이후는 이것으로.
     * 알림은 [register]/[unregister] 를 부른 스레드에서 바로 돈다 — 메인이어야 하지만 막지는 않으므로, 메인 상태를 만지는 받는 쪽은
     * 메인이 아니면 메인으로 넘긴다.
     */
    @JvmStatic
    fun listen(owner: String, callback: () -> Unit) {
        watchers.removeAll { it.owner == owner }
        watchers += Watcher(owner, callback)
    }

    @JvmStatic
    fun unlisten(owner: String) {
        watchers.removeAll { it.owner == owner }
    }

    private fun changed() {
        for (watcher in watchers) {
            try {
                watcher.callback()
            } catch (t: Throwable) {
                Logger.getLogger("inmc-core").log(Level.WARNING, "우편 공급처 알림 실패 (${watcher.owner})", t)
            }
        }
    }

    // --- 넣기 -----------------------------------------------------------------------------

    /** 우편 한 통을 넣는다. 공급처가 없거나 비었거나 공급처가 거절하면 false. */
    @JvmStatic
    fun deposit(playerId: UUID, parcel: Parcel): Boolean {
        val current = provider ?: return false
        if (parcel.isEmpty()) return false
        return try {
            current.deposit(playerId, parcel)
        } catch (t: Throwable) {
            Logger.getLogger("inmc-core").log(Level.WARNING, "우편 넣기 실패 (${parcel.origin} → $playerId)", t)
            false
        }
    }

    /**
     * 보상을 우편으로. 아이템은 여기서 바이트로 바꾼다(공기·못 바꾸는 것은 뺀다). 공급처가 없거나 넣을 것이 없으면 false — 부른 쪽이 제 길로.
     *
     * @param origin 넣는 플러그인의 이름(`plugin.name`). @param source 사람이 읽는 출처.
     */
    @JvmStatic
    @JvmOverloads
    fun send(
        playerId: UUID,
        origin: String,
        source: String,
        stacks: List<ItemStack> = emptyList(),
        money: Double = 0.0,
        moneyBy: Map<String, Double> = emptyMap(),
        commands: List<String> = emptyList(),
    ): Boolean {
        if (provider == null) return false
        val items = stacks.filter { !it.type.isAir && it.amount > 0 }
            .mapNotNull { runCatching { it.serializeAsBytes() }.getOrNull() }
        return deposit(
            playerId,
            Parcel(
                kind = Kind.REWARD, origin = origin, source = source, items = items,
                money = money.coerceAtLeast(0.0), moneyBy = moneyBy.filterValues { it > 0.0 }, commands = commands.toList(),
            ),
        )
    }

    // --- 읽기·받기 (공급처가 없으면 비어 있다) --------------------------------------------------

    @JvmStatic
    fun count(playerId: UUID, origin: String? = null): Int =
        provider?.let { runCatching { it.count(playerId, origin) }.getOrDefault(0) } ?: 0

    @JvmStatic
    fun entries(playerId: UUID, origin: String? = null): List<Pair<String, Parcel>> =
        provider?.let { runCatching { it.entries(playerId, origin) }.getOrDefault(emptyList()) }.orEmpty()

    /** 공급처가 없거나 공급처가 던지면 null(던진 것은 로그로) — 부른 쪽의 제 우편함 받기는 이미 끝났을 수 있어 그것까지 무르지 않는다. */
    @JvmStatic
    fun claim(player: Player, origin: String? = null): Mailbox.ClaimResult? {
        val current = provider ?: return null
        return try {
            current.claim(player, origin)
        } catch (t: Throwable) {
            Logger.getLogger("inmc-core").log(Level.WARNING, "우편 받기 실패 (${player.name})", t)
            null
        }
    }

    @JvmStatic
    fun discard(playerId: UUID, origin: String? = null): Int {
        val current = provider ?: return 0
        return try {
            current.discard(playerId, origin)
        } catch (t: Throwable) {
            Logger.getLogger("inmc-core").log(Level.WARNING, "우편 버리기 실패 ($playerId)", t)
            0
        }
    }
}
