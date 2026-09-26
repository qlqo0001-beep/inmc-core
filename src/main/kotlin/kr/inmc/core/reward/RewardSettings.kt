package kr.inmc.core.reward

/**
 * 보상·우편함이 읽는 설정. 각 플러그인의 `PluginConfig` 가 이 다섯 개를 노출한다.
 *
 * 설정 객체 전체를 넘기지 않는 것이 의도다. `PluginConfig` 는 플러그인마다 완전히 다르고,
 * core 가 그중 무엇을 읽는지가 여기 다섯 줄로 남아야 나중에 늘어나는 것을 알아챌 수 있다.
 */
interface RewardSettings {

    /** 우편함 한 명당 보관 한도. 넘으면 새 보상이 들어가지 않는다. */
    val mailboxLimit: Int

    /** 보관 만료(초). 0 이면 만료 없음. */
    val mailboxExpireSeconds: Long

    /** 인벤토리가 꽉 찼을 때 바닥에 떨굴지, 우편함에 넣을지. */
    val dropWhenInventoryFull: Boolean

    /** 보상 지급을 전체 공지할지. */
    val broadcastRewards: Boolean

    /** 시즌 기록 보관 개수. */
    val seasonArchiveLimit: Int
}
