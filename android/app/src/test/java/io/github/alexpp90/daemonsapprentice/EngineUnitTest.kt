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
