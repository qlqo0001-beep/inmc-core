package kr.inmc.core.scheduler

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 플러그인의 반복 작업 하나를 잡고 있는 공통부.
 *
 * 네 벌의 `Ticker` 가 `task` 필드·`start`·`stop`·`step` 을 **글자 단위로 같게** 갖고 있었다.
 * 서로 다른 것은 [tick] 안에서 무엇을 도느냐뿐이고 그건 도메인이다.
 *
 * [step] 으로 각 단계를 감싸는 것이 이 클래스의 존재 이유다 — 몹 정의 하나가 터져도 그 뒤의
 * 저장 단계까지 같이 멈추면 안 된다.
 */
abstract class TickerBase(private val plugin: JavaPlugin) {

    protected val logger: Logger = plugin.logger

    private var task: ScheduledTask? = null

    /**
     * 반복 주기(틱).
     *
     * [start] 때마다 읽는다. 설정에서 오는 주기(스킬 틱 등)가 리로드로 바뀌면 그대로 따라가야
     * 하기 때문이다 — 생성자 인자로 받으면 그게 안 된다.
     */
    protected abstract val periodTicks: Long

    fun start() {
        stop()
        // 리전 스케줄러를 쓴다. Folia 에서 Bukkit.getScheduler() 는 아예 던지고, 일반 Paper
        // 에서도 이 API 가 같은 동작을 한다.
        //
        // 1 미만으로 깎는 것은 Folia 가 주기 0 이하를 거부하기 때문이다. 예전 스케줄러는
        // 조용히 받아줬으므로, 값이 어디서 오는지 모르는 하위 클래스가 여기서 터지면 안 된다.
        val period = periodTicks.coerceAtLeast(1L)
        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, { tickNow() }, period, period)
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    /**
     * 이번 회차를 돌려도 되는지.
     *
     * **[tick] 보다 먼저 불린다.** 준비 전에도 반드시 해야 하는 일이 있다면 여기서 `true` 를
     * 돌려주고 진짜 게이트를 [tick] 안 제자리에 두어야 한다 — 순서가 의미를 갖는 경우가 있다.
     */
    protected abstract fun ready(): Boolean

    /** @param now 회차 시작 시각. 같은 회차의 모든 단계가 같은 값을 본다. */
    protected abstract fun tick(now: Long)

    private fun tickNow() {
        if (!ready()) return
        tick(System.currentTimeMillis())
    }

    /** 단계 하나를 격리한다. 터져도 로그만 남기고 다음 단계로 넘어간다. */
    protected inline fun step(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            logger.log(Level.SEVERE, "틱 처리 실패 ($name)", t)
        }
    }
}
