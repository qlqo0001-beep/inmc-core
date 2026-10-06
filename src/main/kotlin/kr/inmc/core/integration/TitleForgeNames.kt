package kr.inmc.core.integration

import org.bukkit.Bukkit
import java.lang.reflect.Method
import java.util.UUID

/**
 * TitleForge 표시 이름 조회 — 닉네임을 바꾼 사람은 바뀐 이름으로.
 *
 * TitleForge 쪽 안정 진입점(`TitleForgePlugin.displayNameOf`)을 리플렉션으로 부른다.
 * 컴파일 의존은 두지 않는다. 없거나·닉네임이 없거나·본인이 표시를 끄면 [fallback](실명)을 돌려준다.
 *
 * 오프라인·저장된 이름(상점 주인·판매자·우편함 등)에는 쓰지 않는다 — 기록은 실명으로 남긴다.
 * 방송·알림처럼 지금 접속 중인 사람을 가리킬 때만 쓴다.
 *
 * 플러그인 인스턴스 기준으로 Method 를 캐시한다 — 리로드로 인스턴스가 바뀌면 다시 찾는다.
 */
object TitleForgeNames {

    @Volatile
    private var cachedPlugin: Any? = null

    @Volatile
    private var cachedMethod: Method? = null

    fun displayName(uuid: UUID, fallback: String): String {
        val plugin = Bukkit.getPluginManager().getPlugin("InMc-TitleForge") ?: return fallback
        return try {
            var method = cachedMethod
            if (method == null || cachedPlugin !== plugin) {
                method = plugin.javaClass.getMethod("displayNameOf", UUID::class.java)
                cachedPlugin = plugin
                cachedMethod = method
            }
            (method.invoke(plugin, uuid) as? String)?.takeIf { it.isNotBlank() } ?: fallback
        } catch (_: Throwable) {
            cachedPlugin = null
            cachedMethod = null
            fallback
        }
    }
}
