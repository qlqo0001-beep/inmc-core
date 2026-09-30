package kr.inmc.core

import kr.inmc.core.integration.CarriedStorage
import org.bukkit.entity.Player
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 배낭 안 물건을 가방처럼 세고 빼는 창구(2026-09-30). */
class CarriedStorageTest {

    private class Fake(override val name: String) : CarriedStorage.Provider {
        override fun containers(player: Player): List<CarriedStorage.Container> = emptyList()
    }

    @AfterTest
    fun cleanUp() {
        for (provider in CarriedStorage.providers()) CarriedStorage.unregister(provider)
    }

    @Test
    fun `같은 이름으로 다시 꽂으면 교체된다 — 리로드가 공급처를 쌓지 않는다`() {
        val first = Fake("backpacks")
        val second = Fake("backpacks")
        CarriedStorage.register(first)
        CarriedStorage.register(second)
        assertEquals(listOf<CarriedStorage.Provider>(second), CarriedStorage.providers())
        CarriedStorage.unregister(second)
        assertTrue(CarriedStorage.providers().isEmpty())
    }

    @Test
    fun `앞 칸부터 필요한 만큼만 뺀다`() {
        // 칸: 5개 · 안 맞음 · 3개 · 빈칸 · 10개
        val amounts = listOf(5, null, 3, null, 10)
        assertEquals(listOf(0 to 5, 2 to 2), CarriedStorage.plan(amounts, 7))
        assertEquals(listOf(0 to 1), CarriedStorage.plan(amounts, 1))
        assertEquals(listOf(0 to 5, 2 to 3, 4 to 10), CarriedStorage.plan(amounts, 100), "모자라면 있는 만큼")
        assertTrue(CarriedStorage.plan(amounts, 0).isEmpty())
        assertTrue(CarriedStorage.plan(listOf(null, 0), 3).isEmpty())
    }
}
