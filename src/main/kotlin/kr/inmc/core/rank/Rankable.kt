package kr.inmc.core.rank

import kr.inmc.core.reward.RewardTable

/**
 * 랭킹을 붙일 수 있는 것 하나. 숫자야구에서는 게임 하나, 몬스터라면 몬스터 종류 하나,
 * urb 라면 박스 하나가 된다.
 *
 * [RankService] 는 4세대에서 `GameDefinition` 을 직접 받았다. 실제로 쓰는 것은 아래 여섯 개뿐이라
 * 그만큼만 계약으로 뽑았고, 덕분에 서비스 전체가 도메인 밖으로 나올 수 있었다.
 */
interface Rankable {

    /** 저장 키이자 파일 이름. 바뀌지 않는다. */
    val id: String

    val displayName: String

    val ranking: RankConfig

    val rewards: RewardTable

    /**
     * 두 기록 중 어느 쪽이 나은지.
     *
     * 도메인마다 방향이 다르다 — 기록 경신형은 클수록 좋고, 기록 단축형은 작을수록 좋다.
     */
    val better: Better

    /** 기록에 붙일 단위. 순위표에 그대로 나간다. */
    val recordUnit: String
}
