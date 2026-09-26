package kr.inmc.core

import kr.inmc.core.util.TokenBag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * 토큰 치환의 현재 동작을 못박는다.
 *
 * 세 플러그인의 `Ph` 에서 올라온 코드라, 여기서 동작이 바뀌면 세 플러그인의 메시지가 한꺼번에
 * 달라진다. 그래서 "이상해 보이지만 그대로 둔" 것까지 단정으로 남긴다.
 */
class TokenBagTest {

    /** 테스트용 최소 구현. 실제 `Ph` 들과 같은 모양이다. */
    private class Bag : TokenBag<Bag>() {
        override val aliases = mapOf(
            "player" to listOf("{플레이어}", "{player}"),
            "item" to listOf("{아이템}", "{item}"),
        )

        fun player(name: String) = put("player", name)
        fun item(name: String) = put("item", name)
        fun copy(): Bag = copyValuesInto(Bag())
    }

    @Test
    fun `한글과 영문 별칭이 같은 값으로 치환된다`() {
        val bag = Bag().player("Steve")

        assertEquals("안녕 Steve", bag.apply("안녕 {플레이어}"))
        assertEquals("hello Steve", bag.apply("hello {player}"))
    }

    @Test
    fun `한 문장에 같은 토큰이 여러 번 나와도 전부 바뀐다`() {
        val bag = Bag().player("Steve")

        assertEquals("Steve 님, Steve 님", bag.apply("{플레이어} 님, {player} 님"))
    }

    @Test
    fun `값이 없으면 원문 그대로`() {
        assertEquals("{플레이어} 없음", Bag().apply("{플레이어} 없음"))
        assertEquals("", Bag().player("Steve").apply(""))
    }

    @Test
    fun `별칭표에 없는 토큰은 raw 로 이름 그대로 넣는다`() {
        val bag = Bag().raw("커스텀", "값")

        assertEquals("값", bag.apply("커스텀"))
    }

    @Test
    fun `치환된 값 안의 별칭이 다시 치환된다`() {
        // 값들을 순서대로 훑기 때문에 생기는 동작이다. 이상해 보이지만 바꾸면 기존 메시지가
        // 달라지므로 그대로 두기로 했고, 그 사실을 여기서 못박는다.
        val bag = Bag().player("{아이템}").item("다이아몬드")

        // player 를 먼저 넣었으므로 "{아이템}" 이 들어간 뒤, 이어지는 item 치환이 그걸 잡는다.
        assertEquals("다이아몬드", bag.apply("{플레이어}"))
    }

    @Test
    fun `이어 쓰기가 자기 타입을 돌려준다`() {
        // Ph.of().player(x).item(y) 형태가 30곳쯤 있다. 베이스 타입을 돌려주면 전부 깨진다.
        val bag = Bag()
        val chained: Bag = bag.player("Steve").item("칼").raw("a", "b")

        assertSame(bag, chained)
    }

    @Test
    fun `copy 는 값을 복사하고 원본과 분리된다`() {
        val original = Bag().player("Steve")
        val clone = original.copy().item("칼")

        assertEquals("Steve 칼", clone.apply("{플레이어} {아이템}"))
        assertEquals("Steve {아이템}", original.apply("{플레이어} {아이템}"), "원본이 오염되면 안 된다")
    }
}
