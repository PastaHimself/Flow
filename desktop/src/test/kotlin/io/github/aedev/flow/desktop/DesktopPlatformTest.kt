package io.github.aedev.flow.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

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
    fun relativeXdgDirectoriesFallBackUnderHome() {
        val home = temporaryFolder.root.toPath().resolve("home")

        assertEquals(home.resolve(".local/share/flow"), defaultDataDirectory(home.toString(), "relative-data"))
        assertEquals(home.resolve(".cache/flow"), defaultCacheDirectory(home.toString(), "relative-cache"))
    }

    @Test
    fun downloadDirectoryReadsXdgUserDirsAndExpandsHome() {
        val root = temporaryFolder.root.toPath()
        val home = root.resolve("home")
        val configHome = root.resolve("config")
        Files.createDirectories(configHome)
        Files.writeString(
            configHome.resolve("user-dirs.dirs"),
            "XDG_DESKTOP_DIR=\"${'$'}HOME/Desktop\"\nXDG_DOWNLOAD_DIR=\"${'$'}HOME/Incoming\"\n",
        )

        assertEquals(home.resolve("Incoming"), defaultDownloadDirectory(home.toString(), configHome.toString(), null))
    }

    @Test
    fun downloadDirectoryFallsBackToHomeDownloadsWithoutUserDirs() {
        val root = temporaryFolder.root.toPath()
        val home = root.resolve("home")

        assertEquals(
            home.resolve("Downloads"),
            defaultDownloadDirectory(home.toString(), root.resolve("missing-config").toString(), null),
        )
    }

    @Test
    fun downloadDirectoryUsesOnlyAbsoluteDirectOverride() {
        val root = temporaryFolder.root.toPath()
        val home = root.resolve("home")
        val configured = root.resolve("incoming")

        assertEquals(configured, defaultDownloadDirectory(home.toString(), null, configured.toString()))
        assertEquals(home.resolve("Incoming"), defaultDownloadDirectory(home.toString(), null, "${'$'}HOME/Incoming"))
        assertEquals(home.resolve("Downloads"), defaultDownloadDirectory(home.toString(), null, "relative-downloads"))
    }

    @Test
    fun findExecutableSkipsNonExecutableCandidatesAndUsesPathOrder() {
        val root = temporaryFolder.root.toPath()
        val first = Files.createDirectories(root.resolve("first"))
        val second = Files.createDirectories(root.resolve("second"))
        val third = Files.createDirectories(root.resolve("third"))
        Files.writeString(first.resolve("flow-helper"), "not executable")
        Files.createDirectories(second.resolve("flow-helper"))
        val expected = executableScript(third.resolve("flow-helper"), "exit 0")
        val path = listOf(first, second, third).joinToString(File.pathSeparator)

        assertEquals(expected, findExecutable("flow-helper", path))
        assertNull(findExecutable("missing-helper", path))
    }

    @Test
    fun runProcessCapturesStdoutStderrAndExitCode() =
        runBlocking {
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
    fun cancellingRunProcessTerminatesChildProcess() =
        runBlocking {
            val pidFile = temporaryFolder.root.toPath().resolve("process.pid")
            val script =
                executableScript(
                    temporaryFolder.root.toPath().resolve("long-process-helper"),
                    "sleep 30 & child=${'$'}!; printf '%s %s\\n' ${'$'}${'$'} ${'$'}child > \"${'$'}1\"; wait ${'$'}child",
                )
            val job = launch(Dispatchers.Default) { runProcess(listOf(script.toString(), pidFile.toString())) }

            withTimeout(2_000) {
                while (!Files.isRegularFile(pidFile)) delay(10)
            }
            val pids =
                Files
                    .readString(pidFile)
                    .trim()
                    .split(' ')
                    .map(String::toLong)

            job.cancelAndJoin()

            withTimeout(2_000) {
                while (pids.any { pid -> ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false) }) delay(10)
            }
            assertTrue(pids.all { pid -> !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false) })
        }

    @Test
    fun timingOutRunProcessTerminatesChildProcess() =
        runBlocking {
            val pidFile = temporaryFolder.root.toPath().resolve("timeout-process.pid")
            val script =
                executableScript(
                    temporaryFolder.root.toPath().resolve("timeout-process-helper"),
                    "sleep 30 & child=${'$'}!; printf '%s %s\\n' ${'$'}${'$'} ${'$'}child > \"${'$'}1\"; wait ${'$'}child",
                )

            val failure =
                runCatching {
                    runProcess(listOf(script.toString(), pidFile.toString()), timeout = Duration.ofMillis(200))
                }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            val pids =
                Files
                    .readString(pidFile)
                    .trim()
                    .split(' ')
                    .map(String::toLong)
            withTimeout(2_000) {
                while (pids.any { pid -> ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false) }) delay(10)
            }
            assertTrue(pids.all { pid -> !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false) })
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
