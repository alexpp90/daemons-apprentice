package io.github.alexpp90.daemonsapprentice

import io.github.alexpp90.daemonsapprentice.engine.MarkerParser
import io.github.alexpp90.daemonsapprentice.engine.Shell
import io.github.alexpp90.daemonsapprentice.engine.ShellResult
import io.github.alexpp90.daemonsapprentice.game.GameDirs
import io.github.alexpp90.daemonsapprentice.game.GameEngine
import io.github.alexpp90.daemonsapprentice.game.Line
import io.github.alexpp90.daemonsapprentice.game.Level0
import io.github.alexpp90.daemonsapprentice.game.Wait
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

/** A stand-in for the phone's shell on the computer: `sh -c`, with the folder carried over. */
class HostShell(start: File) : Shell {
    override var folder: String = start.absolutePath
    override val busy = false
    override suspend fun run(line: String, onOutput: (String) -> Unit): ShellResult {
        val script = "cd '$folder' || exit 1\n$line\nprintf '\\036%d;%s\\037' \"\$?\" \"\$PWD\""
        val p = ProcessBuilder("sh", "-c", script).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        p.waitFor()
        var out = ""; var status = 0
        MarkerParser { e ->
            when (e) {
                is MarkerParser.Event.Output -> out += e.text
                is MarkerParser.Event.Prompt -> { status = e.status; folder = e.folder }
                else -> {}
            }
        }.feed(text)
        onOutput(out)
        return ShellResult(out, status, folder)
    }
    override fun interrupt() {}
    override fun sendInput(line: String) {}
    override fun close() {}
}

class EnginePlaythroughTest {

    private fun newGame(): Pair<GameEngine, File> {
        val root = Files.createTempDirectory("apprentice").toFile()
        val dirs = GameDirs(root)
        dirs.playground.mkdirs(); dirs.game.mkdirs()
        return GameEngine(dirs, HostShell(dirs.playground), random = Random(1)) to root
    }

    private suspend fun GameEngine.continueToPrompt() {
        var n = 0
        while (state.value.wait == Wait.Continue && n++ < 20) onContinue()
    }

    /** Every task's own solution solves it, from a fresh start, in order (the desktop's autoplay). */
    @Test fun level0PlaysThroughWithItsSolutions() = runBlocking<Unit> {
        val (g, root) = newGame()
        g.start()
        for (t in Level0.level.tasks) {
            g.continueToPrompt()
            assertEquals("${t.id}: prompt", Wait.Prompt, g.state.value.wait)
            assertEquals(t.title, g.state.value.taskTitle)
            g.submit(t.solution)
            if (t.gameCommand == "files") g.filePickerClosed()
            assertEquals("${t.id}: not solved by '${t.solution}': ${g.state.value.transcript.takeLast(2)}", Wait.Continue, g.state.value.wait)
        }
        g.continueToPrompt()
        assertEquals(Wait.Done, g.state.value.wait)
        assertTrue(root.resolve("home/.apprentice-save").readText().contains("done=0"))
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
        val g2 = GameEngine(GameDirs(root), HostShell(GameDirs(root).playground), random = Random(2))
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
