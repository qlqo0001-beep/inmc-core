package kr.inmc.core

import kr.inmc.core.gui.Paging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 페이지 계산.
 *
 * 4세대에는 이 산술이 24곳에 복사돼 있었다 — 몬스터 12, urb 8, 숫자야구의 Dialog 4.
 * 스물네 벌을 눈으로 비교해 경계를 확인할 방법은 없으므로, 한 곳으로 모은 지금 여기서
 * 한 번에 못박는다.
 */
class PagingTest {

    @Test
    fun `빈 목록도 한 페이지다`() {
        // 0 이면 화면에 "0/0 쪽" 이 뜬다. 항목이 없다는 것과 페이지가 없다는 것은 다르다.
        assertEquals(1, Paging.pageCount(0))
        assertEquals(0, Paging.clamp(0, 0))
        assertTrue(Paging.slice(emptyList<String>(), 0).isEmpty())
    }

    @Test
    fun `정확히 한 페이지 분량은 한 페이지다`() {
        // 여기가 off-by-one 이 숨는 자리다. 45개면 2쪽이 아니라 1쪽이어야 한다.
        assertEquals(1, Paging.pageCount(45))
        assertEquals(2, Paging.pageCount(46))
        assertEquals(1, Paging.pageCount(1))
    }

    @Test
    fun `범위 밖 페이지는 가장 가까운 유효 페이지로`() {
        // 항목이 지워져 페이지 수가 줄어든 뒤 이전 페이지로 돌아올 때 필요하다.
        assertEquals(0, Paging.clamp(-3, 100))
        assertEquals(2, Paging.clamp(99, 100))   // 100개 → 3쪽(0..2)
        assertEquals(1, Paging.clamp(1, 100))
    }

    @Test
    fun `slice 가 페이지 경계에서 정확히 잘린다`() {
        val items = (1..100).toList()

        assertEquals(1, Paging.slice(items, 0).first())
        assertEquals(45, Paging.slice(items, 0).last())
        assertEquals(46, Paging.slice(items, 1).first())
        assertEquals(90, Paging.slice(items, 1).last())
        assertEquals(listOf(91, 92, 93, 94, 95, 96, 97, 98, 99, 100), Paging.slice(items, 2))
    }

    @Test
    fun `slice 도 범위 밖 페이지를 클램프한다`() {
        // 호출부가 clamp 를 잊어도 빈 화면이 아니라 마지막 페이지가 나와야 한다.
        val items = (1..10).toList()
        assertEquals(items, Paging.slice(items, 99))
        assertEquals(items, Paging.slice(items, -1))
    }

    @Test
    fun `perPage 를 바꿔도 경계가 유지된다`() {
        assertEquals(1, Paging.pageCount(7, perPage = 7))
        assertEquals(2, Paging.pageCount(8, perPage = 7))
        assertEquals(listOf(8), Paging.slice((1..8).toList(), 1, perPage = 7))
    }

    @Test
    fun `버튼 슬롯이 서로 겹치지 않는다`() {
        // urb 는 예전에 '다음' 을 53 에 뒀는데 통일안에서 53 은 '닫기' 다.
        // 상수를 한곳에 모은 이상, 겹치면 여기서 걸려야 한다.
        val slots = listOf(Paging.SLOT_BACK, Paging.SLOT_PREV, Paging.SLOT_NEXT, Paging.SLOT_CLOSE)
        assertEquals(slots.size, slots.toSet().size, "버튼 슬롯이 겹칩니다: $slots")
        assertTrue(slots.all { it in Paging.PER_PAGE..53 }, "버튼이 목록 영역을 침범합니다: $slots")
    }
}
