package kr.inmc.core.config

import kr.inmc.core.util.Placeholders
import kr.inmc.core.util.Text
import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player

/**
 * `messages.yml` 한 벌을 읽고 보내는 공통부.
 *
 * 세 플러그인이 `raw` · `component` · `prefixed` · `send` · `sendRaw` · `from` 을 주석 문구만
 * 다르게 갖고 있었다. 실제로 다른 것은 기본값 표(`DEFAULTS`)뿐이고 그건 도메인이다.
 *
 * [P] 를 남겨둔 것은 의도다. 이 자리를 그냥 [Placeholders] 로 넓히면 몬스터의 `Ph` 를 urb 의
 * 메시지에 넘겨도 컴파일이 통과한다 — 토큰표가 달라 조용히 치환이 안 될 뿐 아무도 모른다.
 */
open class MessageCatalog<P : Placeholders>(
    private val values: Map<String, String>,
    private val defaults: Map<String, String>,
) {

    /** 설정에 없으면 기본값, 그것도 없으면 빈 문자열. 예외를 던지지 않는다. */
    fun raw(key: String): String = values[key] ?: defaults[key] ?: ""

    fun component(key: String, ph: P? = null, viewer: Player? = null): Component =
        Text.render(raw(key), ph, viewer)

    /** 접두사를 앞에 붙여 렌더링한다. 채팅 피드백은 거의 항상 이쪽이다. */
    fun prefixed(key: String, ph: P? = null, viewer: Player? = null): Component =
        Text.render(raw(PREFIX) + raw(key), ph, viewer)

    fun send(sender: CommandSender, key: String, ph: P? = null) {
        val text = raw(key)
        // 빈 문자열은 "이 메시지를 끈다"는 뜻이다 — 관리자가 줄을 비워 끌 수 있어야 한다.
        if (text.isEmpty()) return
        sender.sendMessage(Text.render(raw(PREFIX) + text, ph, sender as? Player))
    }

    /** 이미 만들어진 본문을 보낸다 (몹별 문구, 상자별 문구 등). */
    fun sendRaw(sender: CommandSender, body: String?, ph: P? = null) {
        if (body.isNullOrBlank()) return
        sender.sendMessage(Text.render(raw(PREFIX) + body, ph, sender as? Player))
    }

    companion object {

        const val PREFIX = "prefix"

        /**
         * 설정 파일을 기본값 위에 덮어 읽는다.
         *
         * 섹션은 건너뛰고 잎 노드만 가져오므로, 관리자가 일부만 고친 파일도 나머지는
         * 기본값으로 채워져 그대로 돈다.
         */
        fun merge(defaults: Map<String, String>, config: YamlConfiguration): Map<String, String> {
            val values = HashMap<String, String>(defaults)
            for (key in config.getKeys(true)) {
                if (config.isConfigurationSection(key)) continue
                config.getString(key)?.let { values[key] = it }
            }
            return values
        }
    }
}
