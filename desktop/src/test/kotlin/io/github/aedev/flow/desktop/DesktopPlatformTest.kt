package io.github.aedev.flow.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class DesktopPlatformTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun xdgDataAndCacheDirectoriesOverrideHomeDefaults() {
        val root = temporaryFolder.root.toPath()
        val home = root.resolve("home")
        val dataHome = root.resolve("xdg-data")
        val cacheHome = root.resolve("xdg-cache")

        assertEquals(dataHome.resolve("flow"), defaultDataDirectory(home.toString(), dataHome.toString()))
        assertEquals(cacheHome.resolve("flow"), defaultCacheDirectory(home.toString(), cacheHome.toString()))
    }

    @Test
    fun blankXdgDirectoriesFallBackUnderHome() {
        val home = temporaryFolder.root.toPath().resolve("home")

        assertEquals(home.resolve(".local/share/flow"), defaultDataDirectory(home.toString(), ""))
        assertEquals(home.resolve(".cache/flow"), defaultCacheDirectory(home.toString(), ""))
    }

    @Test
    fun downloadDirectoryReadsXdgUserDirsAndExpandsHome() {
        assumeTrue(System.getenv("XDG_DOWNLOAD_DIR").isNullOrBlank())
        val root = temporaryFolder.root.toPath()
        val home = root.resolve("home")
        val configHome = root.resolve("config")
        Files.createDirectories(configHome)
        Files.writeString(
            configHome.resolve("user-dirs.dirs"),
            "XDG_DESKTOP_DIR=\"${'$'}HOME/Desktop\"\nXDG_DOWNLOAD_DIR=\"${'$'}HOME/Incoming\"\n",
        )

        assertEquals(home.resolve("Incoming"), defaultDownloadDirectory(home.toString(), configHome.toString()))
    }

    @Test
    fun downloadDirectoryFallsBackToHomeDownloadsWithoutUserDirs() {
        assumeTrue(System.getenv("XDG_DOWNLOAD_DIR").isNullOrBlank())
        val root = temporaryFolder.root.toPath()
        val home = root.resolve("home")

        assertEquals(home.resolve("Downloads"), defaultDownloadDirectory(home.toString(), root.resolve("missing-config").toString()))
    }

    @Test
    fun findExecutableSkipsNonExecutableCandidatesAndUsesPathOrder() {
        val root = temporaryFolder.root.toPath()
        val first = Files.createDirectories(root.resolve("first"))
        val second = Files.createDirectories(root.resolve("second"))
        Files.writeString(first.resolve("flow-helper"), "not executable")
        val expected = executableScript(second.resolve("flow-helper"), "exit 0")
        val path = listOf(first, second).joinToString(File.pathSeparator)

        assertEquals(expected, findExecutable("flow-helper", path))
        assertNull(findExecutable("missing-helper", path))
    }

    @Test
    fun runProcessCapturesStdoutStderrAndExitCode() {
        val script =
            executableScript(
                temporaryFolder.root.toPath().resolve("process-helper"),
                "printf 'hello stdout'; printf 'hello stderr' >&2; exit 7",
            )

        val result = runProcess(listOf(script.toString()))

        assertEquals(7, result.exitCode)
        assertEquals("hello stdout", result.stdout)
        assertEquals("hello stderr", result.stderr)
    }

    @Test
    fun xdgOpenFailureIsReported() {
        val opener = executableScript(temporaryFolder.root.toPath().resolve("xdg-open"), "exit 9")

        val failure = launchXdgOpen("https://example.invalid", opener).exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure!!.message!!.contains("exit code 9"))
    }

    private fun executableScript(
        path: Path,
        body: String,
    ): Path {
        Files.writeString(path, "#!/bin/sh\n$body\n")
        assertTrue(path.toFile().setExecutable(true, true))
        return path
    }
}
