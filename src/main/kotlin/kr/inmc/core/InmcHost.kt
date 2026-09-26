package kr.inmc.core

import kr.inmc.core.config.ConfigService
import kr.inmc.core.util.Placeholders
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

/**
 * core 가 각 플러그인에게 요구하는 **전부**.
 *
 * 4세대에서 프레임워크 코드가 플러그인 밖으로 못 나간 유일한 이유는 `Menu` 와 `ChatPrompt` 가
 * 서비스 로케이터(`Monsters` / `Ng` / `Urb`) 타입을 직접 들고 있었기 때문이다.
 * 실제로 쓰는 건 `plugin` 과 메시지 한 줄 보내기뿐이었다 — 그 둘만 여기로 뽑았다.
 *
 * 각 플러그인의 로케이터가 이 인터페이스를 구현하면 core 의 프레임워크가 그대로 얹힌다.
 * core 는 로케이터의 나머지 부분(레지스트리·도메인 서비스)을 알지도 못하고 알 필요도 없다.
 */
interface InmcHost {

    val plugin: JavaPlugin

    /** 이 플러그인의 I/O 워커. 메인 스레드에서 디스크를 만지지 않기 위한 유일한 통로. */
    val io: ConfigService

    /**
     * messages.yml 의 키 하나를 보낸다.
     *
     * `Ph` 자체는 넘기지 않는다. 토큰이 플러그인마다 달라
     * (몬스터 4620B / 숫자야구 3141B / urb 4033B) 공통화 대상이 아니었기 때문이다.
     * core 가 토큰을 채워야 할 때는 [kr.inmc.core.reward.RewardHost.placeholders] 로
     * 호스트에게 자기 `Ph` 를 만들어 달라고 부탁한다.
     */
    fun tell(target: CommandSender, key: String, ph: Placeholders? = null)
}
