package io.github.alexpp90.daemonsapprentice

import io.github.alexpp90.daemonsapprentice.engine.Guard
import io.github.alexpp90.daemonsapprentice.engine.MarkerParser
import io.github.alexpp90.daemonsapprentice.engine.Normalizer
import io.github.alexpp90.daemonsapprentice.engine.SaveFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EngineUnitTest {

    @Test fun markerParserSplitsOutputAndPrompt() {
        val ev = mutableListOf<MarkerParser.Event>()
        val p = MarkerParser { ev += it }
        p.feed("hel"); p.feed("lo\r\n\u001e0;/da"); p.feed("ta/x\u001f")
        val out = ev.filterIsInstance<MarkerParser.Event.Output>().joinToString("") { it.text }
        assertEquals("hello\n", out)
        assertEquals(MarkerParser.Event.Prompt(0, "/data/x"), ev.last())
    }

    @Test fun markerParserSeesContinuationAndDropsEscapes() {
        val ev = mutableListOf<MarkerParser.Event>()
        val p = MarkerParser { ev += it }
        p.feed("\u001b[1;34mblue\u001b[0m\u001d")
        assertEquals("blue", (ev[0] as MarkerParser.Event.Output).text)
        assertEquals(MarkerParser.Event.MoreInput, ev[1])
        assertEquals(MarkerParser.Event.Prompt(130, "/x"), MarkerParser.parsePrompt("130;/x"))
    }

    @Test fun normalizerUndoesPhoneKeyboards() {
        val r = Normalizer.normalize("Echo “hi” —n ")
        assertEquals("echo \"hi\" -n", r.line)
        assertEquals(3, r.changes.size)
        assertEquals("Linux", Normalizer.normalize("Linux").line) // not a command: untouched
        assertTrue(Normalizer.normalize("ls -l").changes.isEmpty())
    }

    @Test fun guardFindsCommandsAndBlocksTheDangerousOnes() {
        val g = Guard("/data/user/0/app/files/game", "/data/user/0/app/files/home/.apprentice-save")
        assertEquals(listOf("grep", "wc"), g.commands("grep 'a b' f.txt | wc -l"))
        assertEquals(listOf("echo"), g.commands("planet=Mars echo hi"))
        assertTrue(g.check("ls -l; echo hi > x.txt").allowed)
        assertFalse(g.check(":(){ :|:& };:").allowed)
        assertFalse(g.check("exec sh").allowed)
        val pg = "/data/user/0/app/files/home/playground"; val home = "/data/user/0/app/files/home"
        assertFalse(g.check("rm -rf ../../game", pg, home).allowed)
        assertFalse(g.check("rm -rf ../../g*", pg, home).allowed)
        assertTrue(g.check("rm -rf ../../gallery", pg, home).allowed)
        assertTrue(g.check("ls /d*", pg, home).allowed)
        assertTrue(g.check("cat ../../game/shrc", pg, home).allowed)
        assertFalse(g.check("rm -r /data/user/0/app/files/*", pg, home).allowed)
        assertFalse(g.check("rm ~/.apprentice-save", pg, home).allowed)
        assertFalse(g.check("echo x > ../.apprentice-save", pg, home).allowed)
        assertTrue(g.check("cat ~/.apprentice-save").allowed)
        assertFalse(g.check("set -e").allowed)
    }

    @Test fun saveFileKeepsOnlyKnownKeysAndSafeValues() {
        val dir = Files.createTempDirectory("save").toFile()
        val f = File(dir, ".apprentice-save")
        f.writeText("level=3\ntask=2\nevil=\$(rm -rf /)\nbadge=gold\nflags=a;rm\n")
        val s = SaveFile(f).load()
        assertEquals(3, s.int("level"))
        assertEquals("gold", s["badge"])
        assertEquals(null, s["evil"])
        assertEquals(null, s["flags"])
        s["task"] = "4"; s.store()
        assertEquals(4, SaveFile(f).load().int("task"))
        dir.deleteRecursively()
    }
}

/** Milestone 2 (Levels 5 to 9): chips with quotes, kill, loops, the editor. */
class Milestone2Test {
    private val guard = io.github.alexpp90.daemonsapprentice.engine.Guard("/f/game", "/f/home/.apprentice-save") { setOf(100, 200) }

    @Test fun chipWordsKeepQuotesAndSubstitutionsTogether() {
        val split = io.github.alexpp90.daemonsapprentice.game.ChipWords::split
        org.junit.Assert.assertEquals(listOf("echo", "\"today is \$(date +%A)\""), split("echo \"today is \$(date +%A)\""))
        org.junit.Assert.assertEquals(listOf("for", "i", "in", "\$(seq 365)", ";", "do", "mkdir", "day-\$i", ";", "done"),
            split("for i in \$(seq 365); do mkdir day-\$i; done"))
        org.junit.Assert.assertEquals(listOf("echo", "\${f%.JPG}.jpg"), split("echo \${f%.JPG}.jpg"))
    }

    @Test fun killMayNotHitTheGameItself() {
        org.junit.Assert.assertFalse(guard.check("kill 100").allowed)
        org.junit.Assert.assertFalse(guard.check("kill -9 200").allowed)
        org.junit.Assert.assertFalse(guard.check("kill -9 -1").allowed)
        org.junit.Assert.assertFalse(guard.check("kill \$\$").allowed)
        org.junit.Assert.assertFalse(guard.check("pkill sh").allowed)
        org.junit.Assert.assertTrue(guard.check("kill 4321").allowed)
    }

    @Test fun loopsAreCheckedCommandByCommand() {
        org.junit.Assert.assertEquals(listOf("for", "mv"), guard.commands("for f in *.JPG; do mv \$f \${f%.JPG}.jpg; done"))
        org.junit.Assert.assertEquals(listOf("for", "rm"), guard.commands("for x in 1\ndo rm -r /f/game\ndone"))
        org.junit.Assert.assertFalse(guard.check("for x in 1\ndo rm -r /f/game\ndone", "/f/home", "/f/home").allowed)
    }

    @Test fun theEditorStaysInTheHomeFolder() = kotlinx.coroutines.runBlocking<Unit> {
        val root = java.nio.file.Files.createTempDirectory("apprentice").toFile()
        val dirs = io.github.alexpp90.daemonsapprentice.game.GameDirs(root)
        dirs.playground.mkdirs(); dirs.game.mkdirs()
        val g = io.github.alexpp90.daemonsapprentice.game.GameEngine(dirs, HostShell(dirs.playground, dirs.home), random = kotlin.random.Random(1))
        g.start()
        while (g.state.value.wait == io.github.alexpp90.daemonsapprentice.game.Wait.Continue) g.onContinue()
        for (bad in listOf("edit ../../game/shrc", "edit ~/.apprentice-save", "edit /etc/hosts")) {
            g.submit(bad)
            org.junit.Assert.assertEquals(bad, io.github.alexpp90.daemonsapprentice.game.Wait.Prompt, g.state.value.wait)
        }
        g.submit("edit mine.txt")
        org.junit.Assert.assertEquals(io.github.alexpp90.daemonsapprentice.game.Wait.Editing, g.state.value.wait)
        g.editorClosed("hello\n")
        org.junit.Assert.assertEquals("hello\n", java.io.File(dirs.playground, "mine.txt").readText())
        root.deleteRecursively()
    }
}

/** Milestone 3 (Levels 10 to 15): the trip to the home screen. */
class Milestone3Test {
    @Test fun aQuickTripHomeDoesNotCount() = kotlinx.coroutines.runBlocking<Unit> {
        val root = java.nio.file.Files.createTempDirectory("apprentice").toFile()
        val dirs = io.github.alexpp90.daemonsapprentice.game.GameDirs(root)
        dirs.playground.mkdirs(); dirs.game.mkdirs()
        val g = io.github.alexpp90.daemonsapprentice.game.GameEngine(dirs, HostShell(dirs.playground, dirs.home), random = kotlin.random.Random(1))
        g.start()
        g.jumpTo(15)
        while (g.state.value.wait == io.github.alexpp90.daemonsapprentice.game.Wait.Continue) g.onContinue()
        repeat(4) { g.submit("skip"); while (g.state.value.wait == io.github.alexpp90.daemonsapprentice.game.Wait.Continue) g.onContinue() }
        org.junit.Assert.assertEquals("Go away", g.state.value.taskTitle)
        g.cameBack(2_000)
        org.junit.Assert.assertEquals(io.github.alexpp90.daemonsapprentice.game.Wait.Prompt, g.state.value.wait)
        g.cameBack(9_000)
        org.junit.Assert.assertEquals(io.github.alexpp90.daemonsapprentice.game.Wait.Continue, g.state.value.wait)
        root.deleteRecursively()
    }
}

/** Side quest S1: offered after Level 5, played from the menu, and back to where the main path waits. */
class SideQuestTest {
    private fun newGame(): Pair<io.github.alexpp90.daemonsapprentice.game.GameEngine, java.io.File> {
        val root = java.nio.file.Files.createTempDirectory("apprentice").toFile()
        val dirs = io.github.alexpp90.daemonsapprentice.game.GameDirs(root)
        dirs.playground.mkdirs(); dirs.game.mkdirs()
        return io.github.alexpp90.daemonsapprentice.game.GameEngine(dirs, HostShell(dirs.playground, dirs.home), random = kotlin.random.Random(1)) to root
    }
    private suspend fun io.github.alexpp90.daemonsapprentice.game.GameEngine.next() {
        var n = 0
        while (state.value.wait == io.github.alexpp90.daemonsapprentice.game.Wait.Continue && n++ < 60) onContinue()
    }
    private suspend fun io.github.alexpp90.daemonsapprentice.game.GameEngine.skipAll(count: Int) {
        repeat(count) { next(); submit("skip") }
    }

    @Test fun theOfferAfterLevel5CanWait() = kotlinx.coroutines.runBlocking<Unit> {
        val (g, root) = newGame()
        g.start(); g.jumpTo(5)
        g.skipAll(io.github.alexpp90.daemonsapprentice.game.Level5.level.tasks.size)
        g.next()
        assertEquals(listOf("yes", "no"), g.state.value.chips)
        g.submit("maybe")
        assertEquals(listOf("yes", "no"), g.state.value.chips)
        assertTrue(g.state.value.transcript.last().toString().contains("Type `yes`"))
        g.submit("no")
        g.next()
        assertEquals(6, g.state.value.level)
        assertEquals(io.github.alexpp90.daemonsapprentice.game.Level6.level.tasks[0].title, g.state.value.taskTitle)
        val s1 = g.state.value.levels.single { it.side }
        assertEquals("Side quest S1", s1.name); assertTrue(!s1.done)
        root.deleteRecursively()
    }

    @Test fun fromTheMenuAndBack() = kotlinx.coroutines.runBlocking<Unit> {
        val (g, root) = newGame()
        g.start(); g.jumpTo(3)
        g.skipAll(2); g.next()
        g.jumpTo(101); g.next()
        assertEquals("S1", g.state.value.levelLabel)
        g.skipAll(io.github.alexpp90.daemonsapprentice.game.SideQuest1.level.tasks.size)
        g.next()
        assertEquals(3, g.state.value.level)
        assertEquals(io.github.alexpp90.daemonsapprentice.game.Level3.level.tasks[2].title, g.state.value.taskTitle)
        val save = root.resolve("home/.apprentice-save").readText()
        assertTrue(save, save.contains("done=101") && !save.contains("back="))
        assertTrue(g.state.value.levels.single { it.side }.done)
        root.deleteRecursively()
    }
}

class PortraitTest {
    @Test fun uptimeInWords() {
        val f = io.github.alexpp90.daemonsapprentice.game.SideQuest1::upFor
        assertEquals("7 minutes", f(" 22:21:34 up 7 min,  0 users,  load average: 2.38, 4.60, 3.19"))
        assertEquals("1 hour, 23 minutes", f(" 10:00:00 up  1:23,  1 user,  load average: 0.1, 0.2, 0.3"))
        assertEquals("3 days, 4 hours", f(" 10:00:00 up 3 days,  4:05,  0 users,  load average: 1, 1, 1"))
        assertEquals("1 day, 0 hours", f(" 10:00:00 up 1 day, 12 min,  0 users,  load average: 1, 1, 1"))
        assertEquals("", f("uptime: unknown"))
    }
}
