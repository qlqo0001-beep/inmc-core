package kr.inmc.core.util

import com.google.gson.JsonParser
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.EntityType
import java.io.File

/**
 * 바닐라 재질·엔티티의 **한글 이름**(2026-10-08, 사용자 결정). 서버는 클라이언트 번역을 모른다 — 화면은 `<lang:…>` 로 클라이언트가 그리지만,
 * **서버가 글자로 견줘야 하는 곳**(상점 검색·경매장 검색)은 한글을 알아야 한다.
 *
 * 출처는 inmc-discord 가 받아 둔 클라이언트 번역(`plugins/inmc-discord/assets/objects/assets/minecraft/lang/ko_kr.json`)이다. 파일이 없으면
 * 빈 표 — 부르는 쪽은 null 을 받고 영어 이름으로 물러난다. 처음 물을 때 한 번 읽고, [reload] 로 다시 읽는다.
 */
object VanillaNames {

    @Volatile
    private var names: Map<String, String>? = null

    /** 한글 이름을 아는가 — 파일이 있고 읽혔는가. */
    val available: Boolean get() = table().isNotEmpty()

    fun of(material: Material): String? = table()[material.translationKey()]

    fun of(type: EntityType): String? = runCatching { table()[type.translationKey()] }.getOrNull()

    fun ofKey(translationKey: String): String? = table()[translationKey]

    fun reload() {
        names = null
    }

    private fun table(): Map<String, String> = names ?: synchronized(this) { names ?: load().also { names = it } }

    private fun load(): Map<String, String> {
        val file = File(Bukkit.getPluginsFolder(), "inmc-discord/assets/objects/assets/minecraft/lang/ko_kr.json")
        if (!file.isFile) return emptyMap()
        return runCatching {
            val json = JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
            json.entrySet().filter { it.value.isJsonPrimitive }.associate { it.key to it.value.asString }
        }.getOrElse { emptyMap() }
    }
}
