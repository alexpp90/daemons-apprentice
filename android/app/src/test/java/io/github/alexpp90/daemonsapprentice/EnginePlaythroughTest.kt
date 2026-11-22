package io.github.alexpp90.daemonsapprentice

import io.github.alexpp90.daemonsapprentice.engine.MarkerParser
import io.github.alexpp90.daemonsapprentice.engine.Shell
import io.github.alexpp90.daemonsapprentice.engine.ShellResult
import io.github.alexpp90.daemonsapprentice.game.GameDirs
import io.github.alexpp90.daemonsapprentice.game.GameEngine
import io.github.alexpp90.daemonsapprentice.game.Line
import io.github.alexpp90.daemonsapprentice.game.Level0
import io.github.alexpp90.daemonsapprentice.game.Levels
import kotlinx.coroutines.delay
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
 * A stand-in for the phone's shell on the computer: bash (the computer's `sh` may know no
 * `{a,b}` braces), the folder carried over, HOME set to the game's home, Ctrl-C by stopping it.
 */
class HostShell(start: File, private val home: File = start) : Shell {
    override var folder: String = start.absolutePath
    @Volatile override var busy = false
    @Volatile private var proc: Process? = null
    @Volatile private var stopped = false
    override suspend fun run(line: String, onOutput: (String) -> Unit): ShellResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val script = "cd '$folder' || exit 1\n$line\nprintf '\\036%d;%s\\037' \"\$?\" \"\$PWD\""
        val pb = ProcessBuilder("bash", "-c", script).redirectErrorStream(true)
        pb.environment()["HOME"] = home.absolutePath
        stopped = false; busy = true
        val p = pb.start(); proc = p
        val text = p.inputStream.bufferedReader().readText()
        p.waitFor(); busy = false
        if (stopped) return@withContext ShellResult(text, 130, folder, interrupted = true)
        var out = ""; var status = 0
        MarkerParser { e ->
            when (e) {
                is MarkerParser.Event.Output -> out += e.text
                is MarkerParser.Event.Prompt -> { status = e.status; folder = e.folder }
                else -> {}
            }
        }.feed(text)
        onOutput(out)
        ShellResult(out, status, folder)
    }
    override fun interrupt() { stopped = true; proc?.destroy() }
    override fun sendInput(line: String) {}
    override fun close() {}
}

class EnginePlaythroughTest {

    private fun newGame(): Pair<GameEngine, File> {
        val root = Files.createTempDirectory("apprentice").toFile()
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
            when (t.drive) {
                "device" -> { g.submit("skip"); continue }
                "ctrlc" -> {
                    val job = launch { g.submit(t.solution) }
                    withTimeout(5000) { while (g.state.value.wait != Wait.Busy) delay(20) }
                    delay(300); g.interrupt(); job.join()
                }
                else -> g.submit(t.solution)
            }
            if (t.gameCommand == "files") g.filePickerClosed()
            assertEquals("${t.id}: not solved by '${t.solution}': ${g.state.value.transcript.takeLast(3)}", Wait.Continue, g.state.value.wait)
        }
        g.continueToPrompt()
        assertEquals(Wait.Done, g.state.value.wait)
        val save = root.resolve("home/.apprentice-save").readText()
        assertTrue(save, save.contains("done=0,1,2,3,4"))
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
