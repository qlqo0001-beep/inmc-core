package kr.inmc.core

import kr.inmc.core.store.PlayerStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 부팅 창에서의 데이터 유실을 막는 불변식.
 *
 * `PlayerStore.load` 의 콜백은 `Bukkit.getScheduler().runTask` 로 걸려 **첫 틱**에야 돈다.
 * 첫 틱은 모든 플러그인의 onEnable 이 끝난 뒤이므로, 의존 플러그인이 켜지는 동안 저장소는
 * 계속 준비되지 않은 상태다. 그 창에서 들어온 쓰기가 살아남는지를 여기서 지킨다.
 *
 * 예전에는 `cache.putAll(loaded)` 가 플레이어 맵을 통째로 갈아치웠고, 같은 창에서 `flush` 는
 * `!ready` 로 아무것도 안 썼다. 즉 **디스크에도 못 가고 메모리에서도 지워졌다.**
 */
class PlayerStoreMergeTest {

    @Test
    fun `부팅 중 들어온 쓰기가 디스크 로드에 덮이지 않는다`() {
        val fromDisk = mapOf("monster" to mapOf<String, Any?>("kills" to 10, "rank" to "gold"))
        // ready 전에 들어온 쓰기 — kills 를 올렸다
        val cached = mapOf("monster" to mapOf<String, Any?>("kills" to 11))

        val merged = PlayerStore.merge(cached, fromDisk)

        assertEquals(11, merged["monster"]?.get("kills"), "메모리 값이 디스크에 져서는 안 된다")
        assertEquals("gold", merged["monster"]?.get("rank"), "메모리에 없던 필드는 디스크 것이 남아야 한다")
    }

    @Test
    fun `디스크에만 있는 네임스페이스도 살아남는다`() {
        val fromDisk = mapOf(
            "monster" to mapOf<String, Any?>("kills" to 10),
            "urb" to mapOf<String, Any?>("opened" to 3),
        )
        val cached = mapOf("numbergame" to mapOf<String, Any?>("plays" to 1))

        val merged = PlayerStore.merge(cached, fromDisk)

        assertEquals(setOf("monster", "urb", "numbergame"), merged.keys)
        assertEquals(10, merged["monster"]?.get("kills"))
        assertEquals(1, merged["numbergame"]?.get("plays"))
    }

    @Test
    fun `캐시가 비어 있으면 디스크가 그대로 들어온다`() {
        val fromDisk = mapOf("urb" to mapOf<String, Any?>("opened" to 7))

        val merged = PlayerStore.merge(null, fromDisk)

        assertEquals(7, merged["urb"]?.get("opened"))
    }

    @Test
    fun `subject 묶음은 통째로 이기지 않고 필드 단위로 합쳐진다`() {
        // uuid → 트리거id → 필드. 메모리가 count 만 올렸다면 디스크의 나머지 필드가 남아야 한다.
        val fromDisk = mapOf(
            "monster" to mapOf<String, Any?>(
                // <String, Any?> 를 명시하는 것이 중요하다. 생략하면 1000L 때문에 Kotlin 이
                // 나머지 정수 리터럴까지 Long 으로 추론해, 실제 저장 값과 다른 것을 검증하게 된다.
                "광질" to mapOf<String, Any?>("count" to 5, "touched" to 1000L, "fired-today" to 2),
            ),
        )
        val cached = mapOf(
            "monster" to mapOf<String, Any?>("광질" to mapOf("count" to 6)),
        )

        val merged = PlayerStore.merge(cached, fromDisk)

        @Suppress("UNCHECKED_CAST")
        val subject = merged["monster"]?.get("광질") as Map<String, Any?>
        assertEquals(6, subject["count"], "메모리가 올린 값이 이겨야 한다")
        assertEquals(1000L, subject["touched"], "메모리가 안 건드린 형제 필드가 날아가면 안 된다")
        assertEquals(2, subject["fired-today"])
    }

    @Test
    fun `점 가드는 Bukkit 없이 도는 순수 함수다`() {
        // 인스턴스 메서드 안에 있으면 Plugin 을 만들어야 해서 테스트할 수 없었다.
        PlayerStore.requireFlat("네임스페이스" to "monster", "저장 키" to "kills")

        assertFailsWith<IllegalArgumentException> {
            PlayerStore.requireFlat("저장 키" to "boss.phase")
        }
        assertFailsWith<IllegalArgumentException> {
            PlayerStore.requireFlat("네임스페이스" to "a.b")
        }
    }

    @Test
    fun `null 값은 병합 결과에 남지 않는다`() {
        val fromDisk = mapOf("monster" to mapOf<String, Any?>("kills" to 10, "stale" to null))
        val cached = mapOf("monster" to mapOf<String, Any?>("rank" to null))

        val merged = PlayerStore.merge(cached, fromDisk)

        assertNull(merged["monster"]?.get("stale"))
        assertNull(merged["monster"]?.get("rank"))
        assertEquals(10, merged["monster"]?.get("kills"))
    }
}
