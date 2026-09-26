package kr.inmc.core

import kr.inmc.core.store.PlayerStore
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 공유 저장소의 왕복 검증.
 *
 * 세 플러그인이 같은 파일에 각자의 네임스페이스로 쓰기 때문에, 한 네임스페이스의 저장이
 * 다른 네임스페이스를 지우거나 못 읽게 만드는 것이 가장 위험한 고장이다. 그런 고장은
 * 로그에 아무것도 남기지 않고 "저 플러그인이 내 데이터를 날렸다" 로만 보인다.
 */
class PlayerStoreTest {

    private fun roundTrip(data: Map<String, Map<String, Any?>>): Map<String, MutableMap<String, Any?>> {
        val yaml = PlayerStore.serialize(data)
        // 실제 저장 경로와 같아지도록 문자열을 거쳐 되읽는다.
        // 메모리 객체를 그대로 넘기면 YAML 이 표현하지 못하는 타입을 놓친다.
        val reloaded = YamlConfiguration()
        reloaded.loadFromString(yaml.saveToString())
        return PlayerStore.deserialize(reloaded)
    }

    @Test
    fun `네임스페이스가 섞이지 않고 왕복한다`() {
        val before = mapOf(
            "monster" to mapOf<String, Any?>("kills" to 12, "lastMobId" to "ice_golem"),
            "numbergame" to mapOf<String, Any?>("plays" to 3L, "bestRecord" to 41L),
            "urb" to mapOf<String, Any?>("opened" to 7, "streak" to 2),
        )

        val after = roundTrip(before)

        assertEquals(setOf("monster", "numbergame", "urb"), after.keys)
        assertEquals(12, after["monster"]?.get("kills"))
        assertEquals("ice_golem", after["monster"]?.get("lastMobId"))
        assertEquals(7, after["urb"]?.get("opened"))
    }

    @Test
    fun `한 네임스페이스를 비워도 다른 네임스페이스는 남는다`() {
        val before = mapOf(
            "monster" to mapOf<String, Any?>("kills" to 1),
            "urb" to emptyMap<String, Any?>(),
        )

        val after = roundTrip(before)

        assertEquals(1, after["monster"]?.get("kills"))
        // 빈 네임스페이스는 YAML 에 남지 않는다 — 그래도 남은 쪽이 멀쩡해야 한다.
        assertTrue("urb" !in after || after["urb"].isNullOrEmpty())
    }

    @Test
    fun `점이 든 키가 계층을 만들어버리지 않는지`() {
        // "a.b" 를 키로 쓰면 YamlConfiguration 이 이를 경로로 해석해 계층을 만든다.
        // 그러면 되읽을 때 값이 아니라 섹션이 나와 조용히 사라진 것처럼 보인다.
        val before = mapOf("monster" to mapOf<String, Any?>("boss.phase" to 2))

        val after = roundTrip(before)

        // 이 상황이 실제로 일어나는지 여기서 못박는다. 지금 동작은 계층이 생기는 것이므로
        // 네임스페이스 안의 키에는 점을 쓰지 않는다는 규약이 필요하다.
        assertTrue(
            after["monster"]?.get("boss.phase") == null,
            "점이 든 키가 평평하게 저장된다면 이 테스트를 지우고 규약을 완화해도 된다",
        )
    }

    @Test
    fun `숫자 타입이 왕복에서 보존된다`() {
        // Long 과 Int 가 뒤바뀌면 getLong 이 0 을 돌려주고, 그러면 "기록이 초기화됐다" 로 보인다.
        val before = mapOf("numbergame" to mapOf<String, Any?>("small" to 7, "big" to 9_000_000_000L))

        val after = roundTrip(before)

        assertEquals(7, (after["numbergame"]?.get("small") as Number).toInt())
        assertEquals(9_000_000_000L, (after["numbergame"]?.get("big") as Number).toLong())
    }
}
