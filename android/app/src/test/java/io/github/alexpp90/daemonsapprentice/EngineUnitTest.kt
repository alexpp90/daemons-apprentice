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
