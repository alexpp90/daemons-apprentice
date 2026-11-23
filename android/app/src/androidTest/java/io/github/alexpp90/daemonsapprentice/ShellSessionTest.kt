package io.github.alexpp90.daemonsapprentice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alexpp90.daemonsapprentice.engine.ShellSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The phone's own shell on a pseudo-terminal, as the game uses it (ANDROID_CONCEPT "How a command runs"). */
@RunWith(AndroidJUnit4::class)
class ShellSessionTest {
    private lateinit var root: File
    private lateinit var shell: ShellSession

    @Before fun setUp() = runBlocking<Unit> {
        root = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "shell-test")
        root.deleteRecursively()
        val pg = File(root, "home/playground").apply { mkdirs() }
        shell = ShellSession(File(root, "game/shrc"), File(root, "home"), pg)
        assertTrue("the shell started and showed its first prompt", shell.start())
    }

    @After fun tearDown() { shell.close(); root.deleteRecursively() }

    /** A closed shell leaves no zombie behind (Android 10's livelock watchdog kills an app that has one), even with a job still running. */
    @Test fun closedShellIsReaped() = runBlocking<Unit> {
        val pid = (shell.ownPids - android.os.Process.myPid()).single()
        shell.run("sleep 30 &")
        shell.close()
        val status = File("/proc/$pid/status")
        val gone = withTimeout(5_000) {
            while (runCatching { status.readLines() }.getOrDefault(emptyList()).any { it.startsWith("State:") }) delay(100)
            true
        }
        assertTrue("the shell $pid was reaped", gone)
    }

    @Test fun outputAndStatus() = runBlocking<Unit> {
        val r = shell.run("echo hi")
        assertEquals("hi\n", r.output)
        assertEquals(0, r.status)
        assertEquals(127, shell.run("nosuchcommand").status)
    }

    @Test fun folderAndVariablesPersist() = runBlocking<Unit> {
        shell.run("mkdir -p sub")
        assertTrue(shell.run("cd sub").folder.endsWith("/playground/sub"))
        shell.run("planet=Mars")
        assertEquals("Mars\n", shell.run("echo \$planet").output)
        assertTrue(shell.run("pwd").output.trim().endsWith("/playground/sub"))
    }

    @Test fun unfinishedCommandWaitsForTheRest() = runBlocking<Unit> {
        val first = shell.run("echo 'one")
        assertTrue(first.needsMore)
        val done = shell.run("two'")
        assertEquals("one\ntwo\n", done.output)
    }

    @Test fun ctrlCStopsTheCommandNotTheShell() = runBlocking<Unit> {
        var result: io.github.alexpp90.daemonsapprentice.engine.ShellResult? = null
        val job = launch(kotlinx.coroutines.Dispatchers.IO) { result = shell.run("sleep 30") }
        delay(800)
        shell.interrupt()
        withTimeout(5000) { job.join() }
        assertEquals(130, result!!.status)
        assertEquals("still here\n", shell.run("echo still here").output)
    }

    @Test fun historyIsTheShellsOwn() = runBlocking<Unit> {
        shell.run("echo first")
        val h = shell.run("fc -l")
        assertTrue(h.output, h.output.contains("echo first"))
    }

    /** Android 10+: an app may not run a file it wrote itself (W^X); `sh file` works. */
    @Test fun ownScriptsRunOnlyThroughSh() = runBlocking<Unit> {
        shell.run("printf 'echo from script\\n' > s.sh; chmod 755 s.sh")
        assertNotEquals(0, shell.run("./s.sh").status)
        assertEquals("from script\n", shell.run("sh s.sh").output)
    }
}
