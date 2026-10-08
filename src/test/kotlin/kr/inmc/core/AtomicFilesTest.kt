package kr.inmc.core

import kr.inmc.core.util.AtomicFiles
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtomicFilesTest {

    private fun tempDir(): File = Files.createTempDirectory("inmc-atomic").toFile().apply { deleteOnExit() }

    @Test
    fun `없는 폴더에도 쓰고 임시 파일을 남기지 않는다`() {
        val dir = tempDir()
        val target = File(dir, "deep/er/file.yml")
        AtomicFiles.write(target, "a: 1\n")
        assertEquals("a: 1\n", target.readText())
        assertFalse(File(target.parentFile, "file.yml.tmp").exists())
    }

    @Test
    fun `덮어쓰면 통째로 바뀐다 — 옛 내용이 섞이지 않는다`() {
        val dir = tempDir()
        val target = File(dir, "file.yml")
        AtomicFiles.write(target, "아주 긴 옛 내용 ".repeat(50))
        AtomicFiles.write(target, "짧음")
        assertEquals("짧음", target.readText())
    }

    @Test
    fun `임시 파일이 남아 있어도 다음 쓰기가 덮는다`() {
        val dir = tempDir()
        val target = File(dir, "file.yml")
        File(dir, "file.yml.tmp").writeText("쓰다 죽은 조각")
        AtomicFiles.write(target, "새 내용")
        assertEquals("새 내용", target.readText())
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }
}
