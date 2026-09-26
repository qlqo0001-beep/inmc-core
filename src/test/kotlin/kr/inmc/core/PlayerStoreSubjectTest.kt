package kr.inmc.core

import kr.inmc.core.store.PlayerStore
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 3단(`네임스페이스 → 대상 → 필드`) 왕복 검증.
 *
 * 세 플러그인이 공유하는 모양이다 — 몬스터의 트리거 진행도, urb 의 박스별 개봉 기록,
 * 숫자야구의 게임별 플레이 기록. 디스크에 `네임스페이스.대상.필드` 로 그대로 쓰기 때문에
 * **운영자가 열어봐도 지금까지의 파일과 같은 모양**이라는 것이 이 설계의 요점이다.
 */
class PlayerStoreSubjectTest {

    private fun roundTrip(data: Map<String, Map<String, Any?>>): Map<String, MutableMap<String, Any?>> {
        val yaml = PlayerStore.serialize(data)
        val reloaded = YamlConfiguration()
        reloaded.loadFromString(yaml.saveToString())
        return PlayerStore.deserialize(reloaded)
    }

    @Suppress("UNCHECKED_CAST")
    private fun subjectOf(map: Map<String, MutableMap<String, Any?>>, ns: String, subject: String) =
        map[ns]?.get(subject) as? Map<String, Any?>

    @Test
    fun `대상 묶음이 왕복한다`() {
        val before = mapOf(
            "monster" to mapOf<String, Any?>(
                "광질" to mapOf<String, Any?>("count" to 7, "touched" to 1700000000000L),
                "밤사냥" to mapOf<String, Any?>("count" to 2, "fired-today" to 1),
            ),
        )

        val after = roundTrip(before)

        assertEquals(7, subjectOf(after, "monster", "광질")?.get("count"))
        assertEquals(1700000000000L, subjectOf(after, "monster", "광질")?.get("touched"))
        assertEquals(2, subjectOf(after, "monster", "밤사냥")?.get("count"))
    }

    @Test
    fun `평면 키와 대상이 한 네임스페이스에 공존해도 서로 안 먹는다`() {
        val before = mapOf(
            "monster" to mapOf<String, Any?>(
                "kills" to 40,                                        // 평면
                "광질" to mapOf<String, Any?>("count" to 7),           // 대상
            ),
        )

        val after = roundTrip(before)

        assertEquals(40, after["monster"]?.get("kills"), "평면 키가 대상에 먹히면 안 된다")
        assertEquals(7, subjectOf(after, "monster", "광질")?.get("count"))
    }

    @Test
    fun `섹션 노드는 스칼라가 아니라 대상 맵으로 되읽힌다`() {
        // 예전에는 살아 있는 MemorySection 을 값 맵에 밀어넣어, 다음 직렬화에서 모양이
        // 조용히 어긋났다. 그게 실제 손상이었고 이 단정이 그것을 닫는다.
        val yaml = YamlConfiguration()
        yaml.set("monster.광질.count", 3)

        val reloaded = YamlConfiguration().apply { loadFromString(yaml.saveToString()) }
        val after = PlayerStore.deserialize(reloaded)

        val subject = after["monster"]?.get("광질")
        assertTrue(subject is Map<*, *>, "대상은 Map 이어야 한다. 실제: ${subject?.javaClass}")
        assertEquals(3, (subject as Map<*, *>)["count"])
    }

    @Test
    fun `대상 이름의 점은 거부된다`() {
        // 몬스터의 트리거 id 는 파일명에서 그대로 온다. 운영자가 `triggers/광질.보스.yml` 을
        // 손으로 만들면 이 경로로 들어오므로, 로드 시점에 걸러내야 한다는 근거가 된다.
        assertFailsWith<IllegalArgumentException> {
            PlayerStore.requireFlat("대상" to "광질.보스")
        }
    }

    @Test
    fun `빈 대상은 YAML 에 남지 않되 형제는 살아남는다`() {
        val before = mapOf(
            "monster" to mapOf<String, Any?>(
                "광질" to emptyMap<String, Any?>(),
                "밤사냥" to mapOf<String, Any?>("count" to 1),
            ),
        )

        val after = roundTrip(before)

        assertNull(after["monster"]?.get("광질"))
        assertEquals(1, subjectOf(after, "monster", "밤사냥")?.get("count"))
    }

    @Test
    fun `대상 안에서도 Int 와 Long 이 구분된다`() {
        // 뒤바뀌면 getLong 이 0 을 돌려주고, 그러면 "진행도가 초기화됐다" 로 보인다.
        val before = mapOf(
            "monster" to mapOf<String, Any?>(
                "광질" to mapOf<String, Any?>("count" to 7, "touched" to 9_000_000_000L),
            ),
        )

        val after = roundTrip(before)
        val subject = subjectOf(after, "monster", "광질")!!

        assertEquals(7, (subject["count"] as Number).toInt())
        assertEquals(9_000_000_000L, (subject["touched"] as Number).toLong())
    }
}
