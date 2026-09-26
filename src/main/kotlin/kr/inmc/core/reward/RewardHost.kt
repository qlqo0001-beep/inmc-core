package kr.inmc.core.reward

import kr.inmc.core.InmcHost
import kr.inmc.core.integration.EconomyHook
import kr.inmc.core.item.ItemResolver
import kr.inmc.core.rank.RankService
import kr.inmc.core.rank.Rankable
import net.kyori.adventure.text.Component

/**
 * 보상·우편함·랭킹 서비스가 호스트 플러그인에게 요구하는 것.
 *
 * [InmcHost] 를 넓힌 것이며, 각 플러그인의 서비스 로케이터(`Ng` / `Monsters` / `Urb`)가 구현한다.
 * [mailbox] · [rewards] · [ranks] 가 서로를 여기서 다시 찾는 구조는 의도된 것이다 —
 * 세 서비스가 서로를 참조하므로 생성자로는 엮을 수 없고, 4세대에서도 `ng` 를 통해 같은 방식으로
 * 늦게 찾고 있었다.
 */
interface RewardHost : InmcHost {

    val economy: EconomyHook

    val itemResolver: ItemResolver

    val rewardSettings: RewardSettings

    val mailbox: Mailbox

    val rewards: RewardService

    val ranks: RankService

    /** 랭킹을 붙일 대상 전체. 숫자야구는 게임 목록, 다른 플러그인은 각자의 것. */
    val rankables: List<Rankable>

    /**
     * core 가 보내는 메시지에 채울 토큰들을 호스트의 `Ph` 로 옮겨 달라고 부탁한다.
     *
     * core 는 의미 이름만 안다 — `subject`(랭킹 대상 이름) · `player` · `item` · `rank` · `season`.
     * 그것을 `{게임}` 인지 `{몹}` 인지로 옮기는 것은 호스트의 몫이다. 이 한 겹 덕분에
     * 같은 RankService 가 게임에도 몬스터에도 붙는다.
     */
    fun placeholders(vararg pairs: Pair<String, String>): kr.inmc.core.util.Placeholders

    /** messages.yml 의 키를 컴포넌트로. (4세대의 `messages.component`) */
    fun messageComponent(key: String, ph: kr.inmc.core.util.Placeholders? = null): Component
}
