package kr.inmc.core.integration

import kr.inmc.core.economy.Currencies
import kr.inmc.core.economy.Currency
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.util.logging.Logger
import kotlin.math.roundToLong

/**
 * 기본 화폐, 선택.
 *
 * **inmc-economy 가 켜져 있으면 그 기본 화폐**([Currencies.default])를, 없으면 Vault 를 쓴다. 금액만 적힌 보상·가격은
 * 전부 여기를 지나므로, 쓰는 플러그인은 어느 쪽인지 모른다.
 *
 * 어느 쪽이든 **부를 때마다 찾는다** — 적재 순서가 보장되지 않아, [setup] 때 없던 공급처가 나중에 켜질 수 있다.
 * 옛날처럼 켜질 때 한 번만 찾으면 경제 플러그인보다 먼저 켜진 플러그인은 영영 돈을 못 준다.
 *
 * Vault 클래스는 compile-only 라 [isEnabled] 를 보기 전에는 [Economy] 를 건드리지 않는다.
 * 금액은 Double 로 받지만 inmc 화폐는 정수라 반올림한다(소수점은 쓰지 않는다).
 */
class EconomyHook(private val logger: Logger) {

    private var economy: Economy? = null

    val isEnabled: Boolean get() = Currencies.default() != null || vault() != null

    fun setup() {
        economy = null
        if (Currencies.available) {
            logger.info("INMC 화폐 연동 활성화")
            return
        }
        if (vault() == null) logger.info("경제 플러그인이 아직 없습니다 - 켜지면 그때부터 씁니다")
    }

    private fun currency(): Currency? = Currencies.default()

    /** 이 플러그인 이름 — 거래 기록의 까닭 앞머리. */
    private val source: String get() = logger.name.substringAfterLast('.').ifBlank { "unknown" }

    private fun vault(): Economy? {
        economy?.let { return it }
        return runCatching {
            if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return null
            Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider
        }.getOrNull()?.also {
            economy = it
            logger.info("Vault 연동 활성화 (${it.name})")
        }
    }

    fun balance(player: OfflinePlayer): Double {
        currency()?.let { return it.balance(player).toDouble() }
        return vault()?.let { runCatching { it.getBalance(player) }.getOrDefault(0.0) } ?: 0.0
    }

    fun has(player: OfflinePlayer, amount: Double): Boolean {
        if (amount <= 0.0) return true
        currency()?.let { return it.has(player, amount.roundToLong()) }
        val eco = vault() ?: return false
        return runCatching { eco.has(player, amount) }.getOrDefault(false)
    }

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        if (amount <= 0.0) return true
        currency()?.let { return it.withdraw(player, amount.roundToLong(), "$source:withdraw") }
        val eco = vault() ?: return false
        return runCatching { eco.withdrawPlayer(player, amount).transactionSuccess() }
            .getOrDefault(false)
    }

    /** 보상은 여기로 나간다. 경제가 없으면 조용히 아무것도 안 한다. */
    fun deposit(player: OfflinePlayer, amount: Double): Boolean {
        if (amount <= 0.0) return true
        currency()?.let { return it.deposit(player, amount.roundToLong(), "$source:deposit") }
        val eco = vault() ?: return false
        return runCatching { eco.depositPlayer(player, amount).transactionSuccess() }
            .getOrDefault(false)
    }

    /** 경제 플러그인의 형식을 그대로 쓴다 — 금액이 어디서나 같게 읽히도록. */
    fun format(amount: Double): String {
        currency()?.let { return it.format(amount.roundToLong()) }
        return vault()?.let { runCatching { it.format(amount) }.getOrNull() }
            ?: kr.inmc.core.util.Numbers.money(amount)
    }
}
