package kr.inmc.core

import kr.inmc.core.config.ConfigService
import kr.inmc.core.input.Clicks
import kr.inmc.core.listener.ProfileListener
import kr.inmc.core.store.PlayerStore
import kr.inmc.core.store.Profile
import kr.inmc.core.util.Placeholders
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * inmc-core 의 진입점.
 *
 * 이 플러그인은 게임 내에서 아무것도 하지 않는다. 명령어도 GUI 도 없다.
 * 하는 일은 두 가지뿐이다.
 *
 * 1. **kotlin-stdlib 을 서버에 한 벌만 둔다.** 4세대에는 relocate 된 stdlib 이
 *    플러그인마다 한 벌씩(약 2MB) 들어 있었다.
 * 2. **공유 프레임워크와 공용 도메인 서비스를 제공한다.** 세 플러그인이 글자 단위로
 *    같은 `Menu` · `ItemRef` · `ConfigService` 를 각자 들고 있었다.
 *
 * 의존 플러그인은 `paper-plugin.yml` 에 `inmc-core: { required: true, join-classpath: true }`
 * 를 선언한다. `join-classpath` 가 없으면 클래스는 보이지만 로드되지 않는다.
 */
class CorePlugin : JavaPlugin(), InmcHost {

    override val plugin: JavaPlugin get() = this

    override lateinit var io: ConfigService
        private set

    /** 세 플러그인이 같이 쓰는 플레이어 저장소. 네임스페이스로 갈라 담는다. */
    lateinit var players: PlayerStore
        private set

    @Volatile
    var ready: Boolean = false
        private set

    override fun onEnable() {
        instance = this
        io = ConfigService(this)

        val playerDir = File(dataFolder, "players").apply { mkdirs() }
        players = PlayerStore(io, playerDir)

        // ready 를 기다리지 않는 것이 의도다 — ProfileListener 의 주석 참조.
        Bukkit.getPluginManager().registerEvents(ProfileListener(players), this)
        // 유령 좌클릭 판정. core 가 먼저 켜지므로 LOWEST 중에서도 맨 먼저 돈다.
        Bukkit.getPluginManager().registerEvents(Clicks, this)

        players.load {
            ready = true
            // /reload confirm 뒤에는 이미 접속해 있는 사람이 있다. 정상 부팅에는 비어 있다.
            val now = System.currentTimeMillis()
            for (player in Bukkit.getOnlinePlayers()) {
                Profile.touch(players, player.uniqueId, player.name, now)
            }
            logger.info("inmc-core 활성화 완료 — 플레이어 ${players.knownPlayers().size}명 적재")
        }

        // 저장 플러시는 core 가 자기 티커 하나로 돈다. 의존 플러그인이 각자 돌리면
        // 같은 파일에 대한 쓰기가 겹친다 (가이드 5.3: 반복 태스크는 딱 하나).
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, {
            if (ready) players.flush()
        }, 20L * 30, 20L * 30)
    }

    override fun onDisable() {
        if (!::players.isInitialized) return
        players.flushAll()
        io.shutdown()
        instance = null
    }

    /**
     * core 자신은 messages.yml 을 갖지 않는다. core 의 프레임워크를 core 가 직접 쓸 일이
     * (아직) 없기 때문이다. 의존 플러그인은 각자의 로케이터에서 이 메서드를 구현한다.
     */
    override fun tell(target: CommandSender, key: String, ph: Placeholders?) {
        logger.warning("core 가 직접 보낸 메시지 키: $key (호스트 플러그인이 처리해야 한다)")
    }

    companion object {
        @Volatile
        private var instance: CorePlugin? = null

        /**
         * 의존 플러그인이 core 를 잡는 통로.
         *
         * `required: true` 로 선언했으므로 core 가 없으면 Paper 가 애초에 우리를 켜지 않는다.
         * 그래도 null 을 던지지 않고 예외로 명시하는 이유는, 이 값이 null 로 돌아오는
         * 상황(core 가 onEnable 도중 죽음)에서 원인을 바로 알 수 있게 하기 위해서다.
         */
        fun get(): CorePlugin = instance
            ?: error("inmc-core 가 아직 활성화되지 않았습니다. paper-plugin.yml 의 dependencies 를 확인하세요.")
    }
}
