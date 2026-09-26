package kr.inmc.core

import kr.inmc.core.store.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `profile` 병합 규칙.
 *
 * 임포터가 둘(숫자야구 `plays.yml` 의 `names:`, urb `stats.yml` 의 `players.*.name`)이라
 * **실행 순서가 결과를 바꾸면 안 된다.** 그래서 `seen` 이 큰 쪽이 이기는 규칙 하나로 정한다.
 */
class ProfileTest {

    @Test
    fun `최근에 본 쪽이 이긴다`() {
        val result = Profile.merge("옛이름", 1_000L, "새이름", 2_000L)
        assertEquals("새이름" to 2_000L, result)
    }

    @Test
    fun `오래된 쪽은 무시된다`() {
        assertNull(
            Profile.merge("현재이름", 2_000L, "옛이름", 1_000L),
            "더 오래된 기록이 최신 이름을 덮으면 안 된다",
        )
    }

    @Test
    fun `순서를 바꿔도 결과가 같다`() {
        // urb 먼저 → 숫자야구
        val a = Profile.merge(null, 0L, "urb이름", 5_000L)!!
        val b = Profile.merge(a.first, a.second, "ng이름", 3_000L)
        // 숫자야구 먼저 → urb
        val c = Profile.merge(null, 0L, "ng이름", 3_000L)!!
        val d = Profile.merge(c.first, c.second, "urb이름", 5_000L)!!

        assertNull(b, "더 오래된 두 번째 임포트는 아무것도 안 바꾼다")
        assertEquals("urb이름" to 5_000L, d)
        assertEquals(a, d, "임포트 순서와 무관하게 같은 결과여야 한다")
    }

    @Test
    fun `타임스탬프 없는 기록은 어떤 실제 기록에도 진다`() {
        // 숫자야구의 `names:` 에는 시각이 없어 seen = 0 으로 들어온다.
        assertNull(Profile.merge("실제이름", 1L, "타임스탬프없음", 0L))
        // 다만 아무것도 없을 때는 그것이라도 남긴다 — UUID 만 보이는 것보다 낫다.
        assertEquals("타임스탬프없음" to 0L, Profile.merge(null, 0L, "타임스탬프없음", 0L))
    }

    @Test
    fun `같은 seen 이면 기존 것을 유지한다`() {
        assertNull(
            Profile.merge("기존", 5_000L, "신규", 5_000L),
            "동점이면 안 바꾼다 — 그래야 임포트를 두 번 돌려도 결과가 같다",
        )
    }

    @Test
    fun `이름이 없는 입력은 아무것도 바꾸지 않는다`() {
        assertNull(Profile.merge("기존", 1_000L, null, 9_000L))
    }
}
