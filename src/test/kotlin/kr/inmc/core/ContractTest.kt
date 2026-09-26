package kr.inmc.core

import kr.inmc.core.rank.Rankable
import kr.inmc.core.reward.RewardHost
import kr.inmc.core.reward.RewardSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * core 가 호스트 플러그인에게 요구하는 표면이 **작게 유지되는지** 지킨다.
 *
 * 이 계약이 넓어지면 core 를 새 플러그인에 붙이는 비용이 그만큼 올라간다. 4세대에서
 * 프레임워크가 밖으로 못 나온 이유가 정확히 그것이었다 — `Menu` 가 서비스 로케이터
 * 전체를 들고 있었고, 그래서 로케이터를 가진 플러그인 안에서만 살 수 있었다.
 *
 * 숫자가 늘어나야 할 때가 오면 이 테스트를 고치는 것이 맞다. 다만 **고치는 김에
 * 왜 늘었는지 생각하게 만드는 것**이 목적이다.
 */
class ContractTest {

    private fun membersOf(type: Class<*>): List<String> =
        type.declaredMethods.map { it.name.substringBefore('$') }.distinct().sorted()

    @Test
    fun `InmcHost 는 네 가지만 요구한다`() {
        // plugin / io / tell — getter 이름 기준
        val members = membersOf(InmcHost::class.java)
        assertEquals(listOf("getIo", "getPlugin", "tell"), members, "InmcHost 표면이 변했습니다: $members")
    }

    @Test
    fun `RewardHost 가 추가로 요구하는 것은 아홉 가지를 넘지 않는다`() {
        val members = membersOf(RewardHost::class.java)
        assertTrue(
            members.size <= 9,
            "RewardHost 표면이 $members 로 넓어졌습니다. 새 플러그인마다 이만큼을 구현해야 합니다.",
        )
    }

    @Test
    fun `Rankable 은 여섯 가지만 요구한다`() {
        val members = membersOf(Rankable::class.java)
        assertEquals(
            listOf("getBetter", "getDisplayName", "getId", "getRanking", "getRecordUnit", "getRewards"),
            members,
            "Rankable 표면이 변했습니다: $members",
        )
    }

    @Test
    fun `RewardSettings 는 다섯 개의 설정만 읽는다`() {
        val members = membersOf(RewardSettings::class.java)
        assertEquals(5, members.size, "core 가 읽는 설정이 늘었습니다: $members")
    }

    @Test
    fun `PlayerStore 는 3단까지만 쓴다`() {
        // 4단을 열면 저장소가 일반 YAML 트리로 번져 각 플러그인이 이미 갖고 있는 것과
        // 다를 바 없어진다. `네임스페이스 → 대상 → 필드` 는 세 플러그인이 실제로 공유하는
        // 모양이라서 고른 것이지, 깊이가 편해서 고른 것이 아니다.
        val setters = kr.inmc.core.store.PlayerStore::class.java.declaredMethods
            .filter { it.name == "set" }
            .map { it.parameterCount }
            .sorted()

        assertEquals(
            listOf(4, 5),
            setters,
            "set 의 인자 수가 늘었습니다: $setters (4=평면, 5=대상. 6이 생겼다면 4단입니다)",
        )
    }
}
