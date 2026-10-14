package kr.inmc.core.util

import java.io.File
import java.nio.charset.Charset
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 파일을 **통째로 바꿔치기**한다 — 옆의 임시 파일(`이름.<무작위>.tmp`)에 다 쓴 뒤 원자적으로 옮긴다.
 *
 * 임시 파일 이름은 쓸 때마다 다르다(2026-10-10) — 같은 파일을 두 스레드가 동시에 써도(io 워커와 끌 때의 저장) 서로의 임시 파일을 덮거나
 * 옮겨 가 한쪽이 `NoSuchFileException` 으로 실패하지 않게. 실패하면 제 임시 파일을 지운다. 예전의 고정 이름 `이름.tmp` 는 보이면 지운다.
 *
 * 쓰는 도중에 서버가 죽으면 원본은 그대로고 임시 파일만 남는다(이름이 매번 달라 다음 쓰기가 덮지 않는다 — 죽을 때만 생기는 조각). 바로 `writeText` 하면 그 순간 죽을 때
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
        // createTempFile 은 권한을 0600 으로 만들어 옮긴 뒤 원본 권한이 바뀐다 — 이름만 무작위로 하고 보통대로 만든다.
        val temp = File(target.parentFile, target.name + "." + java.util.UUID.randomUUID().toString().substring(0, 8) + ".tmp").toPath()
        try {
            Files.writeString(temp, text, charset, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE)
            try {
                Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                // 다른 볼륨 등 원자 이동이 안 되는 곳 — 그래도 "다 쓴 파일로 바꾸기"라 잘린 파일은 남지 않는다.
                Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (t: Throwable) {
            runCatching { Files.deleteIfExists(temp) }
            throw t
        }
        // 고정 이름을 쓰던 때(2026-10-10 전) 쓰다 죽은 조각 — 이제 그 이름으로 쓰는 곳이 없어 지워도 된다.
        File(target.parentFile, target.name + ".tmp").delete()
    }
}
