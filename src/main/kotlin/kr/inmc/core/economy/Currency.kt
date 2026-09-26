package kr.inmc.core.economy

import org.bukkit.OfflinePlayer

/**
 * 화폐 하나. **금액은 정수다** — 소수점은 헷갈려서 쓰지 않는다.
 *
 * 화폐를 **정의하는** 쪽(inmc-economy)이 [Currencies] 에 꽂고, **쓰는** 쪽(보상·가격)이 id 로 찾는다.
 * 쓰는 쪽은 그 화폐가 가상 숫자인지, 가방 속 아이템 개수인지, 여러 서버가 나눠 쓰는지 모른다 — 알 필요가 없게 한다.
 */
interface Currency {

    /** 설정·명령어에 쓰는 id. 영문 소문자·숫자·밑줄. */
    val id: String

    /** 보이는 이름(MiniMessage). */
    val name: String

    /** 보이는 금액 — "1,000원". */
    fun format(amount: Long): String

    /** 잔고. 알 수 없으면(가방 속 아이템 화폐인데 접속 중이 아니면) 0. */
    fun balance(player: OfflinePlayer): Long

    fun has(player: OfflinePlayer, amount: Long): Boolean = amount <= 0 || balance(player) >= amount

    /**
     * 뺀다. 모자라면 **아무것도 안 하고** false.
     *
     * @param reason 거래 기록에 남는 까닭. `"<플러그인>:<무엇>"` 모양 — `"inmc-monster:kill"`.
     */
    fun withdraw(player: OfflinePlayer, amount: Long, reason: String): Boolean

    /** 더한다. 최대 금액을 넘거나 줄 수 없으면(아이템 화폐인데 받을 곳이 없으면) false. */
    fun deposit(player: OfflinePlayer, amount: Long, reason: String): Boolean
}

/**
 * 화폐가 꽂히는 곳. 공급처는 하나다(inmc-economy).
 *
 * core 의 클래스는 모든 플러그인이 공유하므로(`join-classpath: true`) 이 객체가 곧 서버 전체에 하나다.
 * 공급처가 없으면 전부 null 로 답한다 — 쓰는 쪽([kr.inmc.core.integration.EconomyHook])은 그때 Vault 로 떨어진다.
 */
object Currencies {

    interface Provider {
        /** 기본 화폐 — 금액만 적힌 보상·가격이 쓰는 것. Vault 로도 보인다. */
        fun default(): Currency?

        fun get(id: String): Currency?

        fun all(): List<Currency>
    }

    @Volatile
    private var provider: Provider? = null

    fun register(provider: Provider) {
        this.provider = provider
    }

    /** 꽂은 그 공급처일 때만 뺀다 — 새로 켜진 공급처를 옛것이 내려가면서 지우지 않게. */
    fun unregister(provider: Provider) {
        if (this.provider === provider) this.provider = null
    }

    val available: Boolean get() = provider != null

    fun default(): Currency? = provider?.default()

    /** [id] 가 비었으면 기본 화폐. 모르는 id 면 null. */
    fun get(id: String?): Currency? {
        val current = provider ?: return null
        return if (id.isNullOrBlank()) current.default() else current.get(id.trim().lowercase())
    }

    fun all(): List<Currency> = provider?.all().orEmpty()
}
