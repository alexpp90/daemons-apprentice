package io.github.alexpp90.daemonsapprentice

import io.github.alexpp90.daemonsapprentice.engine.MarkerParser
import io.github.alexpp90.daemonsapprentice.engine.Shell
import io.github.alexpp90.daemonsapprentice.engine.ShellResult
import io.github.alexpp90.daemonsapprentice.game.GameDirs
import io.github.alexpp90.daemonsapprentice.game.GameEngine
import io.github.alexpp90.daemonsapprentice.game.Line
import io.github.alexpp90.daemonsapprentice.game.Level0
import io.github.alexpp90.daemonsapprentice.game.Levels
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlin.concurrent.thread
import io.github.alexpp90.daemonsapprentice.game.solutionNow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import io.github.alexpp90.daemonsapprentice.game.Wait
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

/**
 * A stand-in for the phone's shell on the computer: one interactive bash for the whole game, like
 * the phone's one mksh, so variables, `$?`, the `>` prompt and background processes behave the
 * same. English messages (LC_ALL=C), HOME set to the game's home, and a fake `getprop` (an
 * Android command). Ctrl-C is SIGINT to the running command, or to bash at its `>` prompt.
 */
class HostShell(start: File, home: File = start) : Shell {
    @Volatile override var folder: String = start.absolutePath
    @Volatile override var busy = false
    private val lock = Object()
    private var pending: CompletableDeferred<ShellResult>? = null
    private val buf = StringBuilder()
    private var interrupted = false
    private val proc: Process
    private val bashPid: Long
    private val parser = MarkerParser { ev ->
        synchronized(lock) {
            when (ev) {
                is MarkerParser.Event.Output -> buf.append(ev.text)
                is MarkerParser.Event.Prompt -> { folder = ev.folder.ifEmpty { folder }; finish(ShellResult(buf.toString(), ev.status, folder, false, interrupted)) }
                MarkerParser.Event.MoreInput -> finish(ShellResult(buf.toString(), 0, folder, true, interrupted))
            }
        }
    }

    private fun finish(r: ShellResult) {
        buf.setLength(0); interrupted = false; busy = false
        val p = pending; pending = null; p?.complete(r)
    }

    init {
        val bin = Files.createTempDirectory("fakebin").toFile()
        File(bin, "getprop").apply {
            writeText("#!/bin/sh\ncase \"$1\" in ro.build.version.release) echo 17;; ro.product.model) echo Host computer;; ro.zygote) echo zygote64;; ro.build.version.security_patch) echo 2026-08-05;; *) echo;; esac\n")
            setExecutable(true)
        }
        val pb = ProcessBuilder("bash", "--norc", "--noprofile", "--noediting", "-i").redirectErrorStream(true).directory(start)
        pb.environment()["HOME"] = home.absolutePath
        pb.environment()["LC_ALL"] = "C"
        pb.environment()["PATH"] = bin.absolutePath + ":" + System.getenv("PATH")
        proc = pb.start()
        thread(isDaemon = true) {
            val r = proc.inputStream.reader()
            val chars = CharArray(4096)
            while (true) { val n = r.read(chars); if (n <= 0) break; parser.feed(String(chars, 0, n)) }
        }
        val first = CompletableDeferred<ShellResult>()
        synchronized(lock) { pending = first; busy = true }
        val pidFile = File(bin, "bash.pid")
        write("echo \$\$ > '${pidFile.absolutePath}'; set +H; PS1=$'\\036$?;\$PWD\\037'; PS2=$'\\035'; cd '${start.absolutePath}'\n")
        runBlocking { withTimeout(10_000) { first.await() } }
        bashPid = pidFile.readText().trim().toLong()
    }

    private fun write(s: String) { proc.outputStream.write(s.toByteArray()); proc.outputStream.flush() }

    override suspend fun run(line: String, onOutput: (String) -> Unit): ShellResult {
        val d = CompletableDeferred<ShellResult>()
        synchronized(lock) { pending = d; busy = true }
        write(line + "\n")
        val r = withTimeout(60_000) { d.await() }
        onOutput(r.output)
        return r
    }

    private fun signal(vararg args: String) { ProcessBuilder(*args).start().waitFor() }

    override fun interrupt() {
        synchronized(lock) { interrupted = true }
        signal("pkill", "-INT", "-P", bashPid.toString())
    }

    override suspend fun cancelInput(): ShellResult {
        val d = CompletableDeferred<ShellResult>()
        synchronized(lock) { pending = d; busy = true; interrupted = true }
        signal("kill", "-INT", bashPid.toString())
        return withTimeout(5_000) { d.await() }
    }

    override val ownPids: Set<Int> get() = setOf(bashPid.toInt())
    override fun sendInput(line: String) = write(line + "\n")
    override fun close() { proc.destroy() }
}

class EnginePlaythroughTest {

    private fun newGame(): Pair<GameEngine, File> {
        val root = Files.createTempDirectory("apprentice").toFile()
        // Level 13's APK: a stand-in zip with code and a native library, read-only like the real one.
        val apk = File(root, "app/base.apk")
        apk.parentFile.mkdirs()
        java.util.zip.ZipOutputStream(apk.outputStream()).use { z ->
            for ((name, bytes) in listOf("AndroidManifest.xml" to "<manifest/>".toByteArray(),
                "classes.dex" to "dex\n039\u0000 code".toByteArray(), "lib/x86_64/libapprentice-pty.so" to ByteArray(16))) {
                z.putNextEntry(java.util.zip.ZipEntry(name)); z.write(bytes); z.closeEntry()
            }
        }
        apk.setWritable(false, false)
        io.github.alexpp90.daemonsapprentice.game.AppInfo.apk = apk.absolutePath
        val dirs = GameDirs(root)
        dirs.playground.mkdirs(); dirs.game.mkdirs()
        return GameEngine(dirs, HostShell(dirs.playground, dirs.home), random = Random(1)) to root
    }

    private suspend fun GameEngine.continueToPrompt() {
        var n = 0
        while (state.value.wait == Wait.Continue && n++ < 50) onContinue()
    }

    /**
     * Every level, every task solved by its own solution, from a fresh start, in order (the
     * desktop's autoplay). `history` needs the phone's interactive shell: skipped here, played on
     * the device.
     */
    @Test fun allLevelsPlayThroughWithTheirSolutions() = runBlocking<Unit> {
        val (g, root) = newGame()
        g.start()
        for (lv in Levels.all) for (t in lv.tasks) {
            g.continueToPrompt()
            assertEquals("${t.id}: prompt", Wait.Prompt, g.state.value.wait)
            assertEquals("${t.id}", t.title, g.state.value.taskTitle)
            val d = t.drive ?: ""
            val solution = t.solutionNow(GameDirs(root))
            when {
                d == "device" -> { g.submit("skip"); continue }
                d == "ctrlc" -> {
                    val job = launch { g.submit(solution) }
                    withTimeout(5000) { while (g.state.value.wait != Wait.Busy) delay(20) }
                    delay(300); g.interrupt(); job.join()
                }
                d == "more-ctrlc" -> {
                    g.submit(solution)
                    assertEquals("${t.id}: the > prompt", Wait.More, g.state.value.wait)
                    g.interrupt()
                }
                d == "lines" -> solution.lines().forEach { g.submit(it) }
                d == "home" -> { delay(2_000); g.cameBack(10_000) } // a real player is away for seconds
                d.startsWith("edit-append:") -> {
                    g.submit(solution)
                    assertEquals("${t.id}: editor", Wait.Editing, g.state.value.wait)
                    val f = File(GameDirs(root).playground, "scripts/" + solution.substringAfter(' '))
                    g.editorClosed(f.readText() + d.removePrefix("edit-append:") + "\n")
                }
                else -> g.submit(solution)
            }
            if (t.gameCommand == "files") g.filePickerClosed()
            assertEquals("${t.id}: not solved by '${t.solution}': ${g.state.value.transcript.takeLast(3)}", Wait.Continue, g.state.value.wait)
        }
        g.continueToPrompt()
        assertEquals(Wait.Done, g.state.value.wait)
        val save = root.resolve("home/.apprentice-save").readText()
        assertTrue(save, save.contains("done=" + Levels.all.joinToString(",") { it.number.toString() }))
        assertTrue(save, save.contains("badge=gold"))
        assertTrue(save, save.contains("flags=hidden_in_plain_sight"))
        root.deleteRecursively()
    }

    @Test fun theMenuJumpsToALevelAndItsIntroShowsOnce() = runBlocking<Unit> {
        val (g, root) = newGame()
        g.start(); g.continueToPrompt()
        g.jumpTo(3)
        assertEquals(3, g.state.value.level)
        assertEquals(Wait.Continue, g.state.value.wait) // the intro
        g.continueToPrompt()
        assertEquals("A new folder", g.state.value.taskTitle)
        g.jumpTo(0); g.continueToPrompt()
        g.jumpTo(3)
        assertEquals(Wait.Prompt, g.state.value.wait) // intro already seen
        root.deleteRecursively()
    }

    @Test fun typingHelpAppearsTheFirstTimeAHardCharacterIsNeeded() = runBlocking<Unit> {
        val (g, root) = newGame()
        g.start(); g.continueToPrompt()
        g.jumpTo(2); g.continueToPrompt()
        repeat(6) { g.submit("skip"); g.continueToPrompt() }         // to t2_7: cd ~
        assertEquals("Beam me home", g.state.value.taskTitle)
        assertTrue(g.state.value.transcript.any { it is Line.Text && it.text.contains("New on the keyboard") && it.text.contains("tilde") })
        root.deleteRecursively()
    }

    @Test fun wrongAnswersGetSpecificFeedbackAndHintsGrow() = runBlocking<Unit> {
        val (g, root) = newGame()
        g.start(); g.continueToPrompt()
        g.submit("help"); g.continueToPrompt()           // t0_1 solved, now t0_2 "echo hello"
        g.submit("hello")
        val last = g.state.value.transcript.last() as Line.Text
        assertTrue(last.text, last.text.contains("The command comes first"))
        g.submit("hint"); g.submit("hint"); g.submit("hint")
        assertTrue(g.state.value.transcript.any { it is Line.Text && it.text.contains("The solution: `echo hello`") })
        assertTrue(g.state.value.hinted)
        g.submit("Echo hello")                           // a capital from the phone keyboard is fixed
        assertEquals(Wait.Continue, g.state.value.wait)
        root.deleteRecursively()
    }

    @Test fun progressSurvivesARestart() = runBlocking<Unit> {
        val (g, root) = newGame()
        g.start(); g.continueToPrompt(); g.submit("help"); g.continueToPrompt(); g.submit("echo hello")
        val g2 = GameEngine(GameDirs(root), HostShell(GameDirs(root).playground, GameDirs(root).home), random = Random(2))
        g2.start(); g2.continueToPrompt()
        assertEquals("Where am I?", g2.state.value.taskTitle)
        root.deleteRecursively()
    }

    @Test fun tabCompletesFileNames() = runBlocking<Unit> {
        val (g, root) = newGame()
        g.start(); g.continueToPrompt()
        assertEquals("cat timeline.txt ", g.complete("cat ti").text)
        assertEquals("echo ", g.complete("ech").text)
        root.deleteRecursively()
    }
}
