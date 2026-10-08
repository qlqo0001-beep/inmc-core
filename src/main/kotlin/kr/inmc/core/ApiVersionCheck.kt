package kr.inmc.core

import java.io.File
import java.util.logging.Logger

/**
 * 서버가 품은 Paper API 가 우리 컴파일 대상보다 오래됐으면 **켤 때 한 줄** 경고한다.
 *
 * 테섭 Leaf 26.2 build 46 은 `paper-api-26.1.2.build.69` 를 품고 있었고 우리는 `26.2.build.1xx` 로 컴파일했다. 그 차이는
 * 켜질 때는 보이지 않고, 새 API 를 **부르는 순간** `NoSuchMethodError` 로만 나타난다(업적 토스트가 그랬다 — 켤 때마다
 * "쓸 수 없습니다" 한 줄뿐이라 원인을 몰랐다). 여기서 한 줄 적어 두면 그런 오류를 봤을 때 먼저 의심할 곳이 생긴다.
 *
 * 서버 쪽 판은 paperclip 이 푸는 `libraries/io/papermc/paper/paper-api/<판>/` 폴더 이름으로 본다(pom.properties 가 없다).
 * 그 폴더가 없으면 조용히 넘어간다 — 경고가 틀리게 나는 것보다 안 나는 쪽이 낫다.
 */
object ApiVersionCheck {

    /** 서버가 품은 paper-api 판(예 `26.1.2.build.69-stable`). 모르면 null. */
    fun bundled(serverRoot: File = File(".")): String? {
        val dir = File(serverRoot, "libraries/io/papermc/paper/paper-api")
        val versions = dir.listFiles { f -> f.isDirectory }?.map { it.name }.orEmpty()
        return versions.maxWithOrNull { a, b -> compare(numbers(a), numbers(b)) }
    }

    /** 자리마다 견준다. 짧은 쪽은 0 으로 채운다 — `26.2` 와 `26.2.0` 은 같다. */
    fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /** `26.1.2.build.69-stable` → [26, 1, 2] (build 앞까지만 — 우리 컴파일 대상은 `26.2` 처럼 짧다). */
    fun numbers(version: String): List<Int> =
        version.substringBefore(".build").substringBefore('-').split('.').mapNotNull { it.toIntOrNull() }

    /** 서버 판이 컴파일 대상보다 **낮으면** 경고 문구, 아니면 null. 둘 중 하나를 모르면 null. */
    fun warning(compiled: String?, bundled: String?): String? {
        if (compiled == null || bundled == null) return null
        val ours = numbers(compiled)
        val theirs = numbers(bundled)
        if (ours.isEmpty() || theirs.isEmpty()) return null
        // 컴파일 대상이 `26.2` 처럼 짧으면 그 자리까지만 본다 — `26.2` 로 컴파일했을 때 서버 `26.2.1` 은 충분하다.
        if (compare(theirs.take(ours.size), ours) >= 0) return null
        return "서버가 품은 Paper API($bundled)가 플러그인 컴파일 대상($compiled)보다 오래됐습니다 — " +
            "새 API 를 쓰는 기능은 부르는 순간 NoSuchMethodError 가 납니다. 서버(Leaf/Paper)를 올리세요."
    }

    /**
     * 모든 플러그인이 켜진 뒤 — 우리 플러그인마다 `paper-plugin.yml` 의 `api-version`(= 컴파일 대상 `inmc { paper }`)을 서버 판과
     * 견준다. core 자신의 판으로만 보면 안 된다 — core 는 26.1.2, 다른 플러그인은 26.2 로 컴파일한다.
     */
    fun report(logger: Logger) {
        val bundled = bundled() ?: return
        val stale = org.bukkit.Bukkit.getPluginManager().plugins
            .filter { it.name.startsWith("inmc", ignoreCase = true) }
            .mapNotNull { plugin ->
                val api = plugin.pluginMeta.getAPIVersion() ?: return@mapNotNull null
                if (warning(api, bundled) == null) null else "${plugin.name}(${api})"
            }
        if (stale.isEmpty()) return
        logger.warning(
            "서버가 품은 Paper API($bundled)가 플러그인 컴파일 대상보다 오래됐습니다: ${stale.joinToString(" · ")} — " +
                "새 API 를 쓰는 기능은 부르는 순간 NoSuchMethodError 가 납니다(업적 토스트가 그랬다). 서버(Leaf/Paper)를 올리세요.",
        )
    }
}
