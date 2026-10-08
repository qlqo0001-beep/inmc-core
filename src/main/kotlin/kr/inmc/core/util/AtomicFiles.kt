package kr.inmc.core.util

import java.io.File
import java.nio.charset.Charset
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 파일을 **통째로 바꿔치기**한다 — 옆에 `.tmp` 로 다 쓴 뒤 원자적으로 옮긴다.
 *
 * 쓰는 도중에 서버가 죽으면 원본은 그대로고 `.tmp` 만 남는다(다음 쓰기가 덮는다). 바로 `writeText` 하면 그 순간 죽을 때
 * **잘린 YAML** 이 남아 다음 부팅에 정의·기록이 통째로 사라진다(ARCHITECTURE 지뢰 10). 던전 `Returns`·업적 `ClaimStore` 가 각자
 * 하던 것을 한 곳으로 모았다 — core 의 `ConfigService.save`·`YamlFileStore`·`YamlFolder` 가 전부 이것을 지난다.
 *
 * `fsync` 는 하지 않는다. JVM 크래시·`/stop`·플러그인 예외에서는 OS 페이지 캐시가 살아 디스크에 닿는다(정전은 다루지 않는다).
 */
object AtomicFiles {

    @JvmStatic
    fun write(file: File, text: String, charset: Charset = Charsets.UTF_8) {
        val target = file.absoluteFile
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(text, charset)
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            // 다른 볼륨 등 원자 이동이 안 되는 곳 — 그래도 "다 쓴 파일로 바꾸기"라 잘린 파일은 남지 않는다.
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
