package kr.inmc.core

import kr.inmc.core.economy.Currencies
import kr.inmc.core.economy.Currency
import kr.inmc.core.integration.EconomyHook
import org.bukkit.OfflinePlayer
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 화폐 창구. 공급처가 꽂히면 금액만 적힌 보상·가격(EconomyHook)이 그 기본 화폐로 간다. */
class CurrenciesTest {

    private class Wallet(override val id: String) : Currency {
        val balances = HashMap<UUID, Long>()
        val reasons = ArrayList<String>()
        override val name: String = id
        override fun format(amount: Long): String = "%,d원".format(amount)
        override fun balance(player: OfflinePlayer): Long = balances[player.uniqueId] ?: 0L
        override fun withdraw(player: OfflinePlayer, amount: Long, reason: String): Boolean {
            if (balance(player) < amount) return false
            balances[player.uniqueId] = balance(player) - amount
            reasons += reason
            return true
        }
        override fun deposit(player: OfflinePlayer, amount: Long, reason: String): Boolean {
            balances[player.uniqueId] = balance(player) + amount
            reasons += reason
            return true
        }
    }

    private class Registry(private val list: List<Currency>) : Currencies.Provider {
        override fun default(): Currency? = list.firstOrNull()
        override fun get(id: String): Currency? = list.firstOrNull { it.id == id }
        override fun all(): List<Currency> = list
    }

    /** 서버 없이 쓸 OfflinePlayer — uuid 만 답한다. */
    private fun player(uuid: UUID = UUID.randomUUID()): OfflinePlayer =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(OfflinePlayer::class.java)) { _, method, _ ->
            if (method.name == "getUniqueId") uuid else null
        } as OfflinePlayer

    private val registered = ArrayList<Currencies.Provider>()

    private fun register(vararg currencies: Currency): Currencies.Provider =
        Registry(currencies.toList()).also {
            Currencies.register(it)
            registered += it
        }

    @AfterTest
    fun clear() {
        registered.forEach(Currencies::unregister)
    }

    @Test
    fun `빈 id 는 기본 화폐이고 모르는 id 는 null 이다`() {
        val money = Wallet("money")
        val cash = Wallet("cash")
        register(money, cash)
        assertEquals(money, Currencies.get(null))
        assertEquals(money, Currencies.get(" "))
        assertEquals(cash, Currencies.get("CASH"))
        assertNull(Currencies.get("gold"))
    }

    @Test
    fun `옛 공급처가 내려가도 새로 꽂힌 공급처는 남는다`() {
        val old = register(Wallet("old"))
        register(Wallet("new"))
        Currencies.unregister(old)
        assertEquals("new", Currencies.default()?.id)
    }

    @Test
    fun `금액만 적힌 보상과 가격은 기본 화폐로 가고 반올림된다`() {
        val money = Wallet("money")
        register(money)
        val hook = EconomyHook(Logger.getLogger("inmc-monster"))
        val who = player()

        assertTrue(hook.isEnabled)
        assertTrue(hook.deposit(who, 1000.4))
        assertEquals(1000L, money.balance(who))
        assertFalse(hook.withdraw(who, 5000.0), "모자라면 빼지 않는다")
        assertTrue(hook.withdraw(who, 999.6))
        assertEquals(0L, money.balance(who))
        assertEquals("1,234원", hook.format(1234.0))
        assertEquals(listOf("inmc-monster:deposit", "inmc-monster:withdraw"), money.reasons, "거래 기록에 어느 플러그인인지 남는다")
    }
}
