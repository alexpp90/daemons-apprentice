package io.github.alexpp90.daemonsapprentice

import io.github.alexpp90.daemonsapprentice.engine.Guard
import io.github.alexpp90.daemonsapprentice.game.ChipWords
import io.github.alexpp90.daemonsapprentice.game.GameEngine
import io.github.alexpp90.daemonsapprentice.game.Levels
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules every task must follow (REQUIREMENTS R3, R4, R5, R28). */
class LevelDataTest {
    private val levels = Levels.all
    private val guard = Guard("/game", "/save")

    @Test fun everyTaskIsComplete() {
        for (lv in levels) for (t in lv.tasks) {
            for ((name, v) in listOf("title" to t.title, "command" to t.command, "hint1" to t.hint1, "hint2" to t.hint2,
                "solution" to t.solution, "solutionWhy" to t.solutionWhy, "fact" to t.fact, "how" to t.how))
                assertTrue("${t.id}: $name is empty", v.isNotBlank())
            assertTrue("${t.id}: no task text", t.chunks.isNotEmpty())
        }
        assertTrue(levels.flatMap { it.tasks }.map { it.id }.let { it.size == it.toSet().size })
    }

    /** R28: every task can be solved by tapping chips alone (from Level 2 on, after `hint`). */
    @Test fun chipsCoverEverySolution() {
        for (lv in levels) for (t in lv.tasks) {
            if (t.solve != null || t.event != null) continue // chips from the live solution; or no command at all
            val words = ChipWords.split(t.solution)
            assertTrue("${t.id}: chips ${t.chips} do not cover '${t.solution}'", words.all { it in t.chips })
        }
    }

    /** R3: Levels 0 and 1 need only letters, digits, space, dot and minus (the rest is on the key bar later). */
    @Test fun noHardCharactersAtTheStart() {
        val ok = Regex("^[A-Za-z0-9 .-]*$")
        for (lv in levels.filter { it.number <= 1 }) for (t in lv.tasks) {
            assertTrue("${t.id}: '${t.solution}'", ok.matches(t.solution))
            t.chips.forEach { assertTrue("${t.id}: chip '$it'", ok.matches(it)) }
        }
    }

    /** R5: every command the player is asked to type is shown in the task text (explained first). */
    @Test fun theCommandIsInTheTaskText() {
        for (lv in levels) for (t in lv.tasks)
            assertTrue("${t.id}: `${t.command}` not in the task text", t.chunks.last().contains("`${t.command}`"))
    }

    @Test fun gameCommandsAreKnown() {
        for (lv in levels) for (t in lv.tasks) t.gameCommand?.let { assertTrue(it in GameEngine.GAME_COMMANDS) }
    }
}

class LevelOrderTest {
    @Test fun levelsRunWithoutGaps() {
        assertTrue(Levels.all.map { it.number } == (0 until Levels.all.size).toList())
    }

    @Test fun hardCharactersAreOfferedOnTheKeyBar() {
        // A solution with | ~ { } > * must name it in `keys`, so the key bar highlights it and the typing help appears.
        for (lv in Levels.all) for (t in lv.tasks.filter { it.solve == null && it.event == null }) // placeholders, no command
            for (c in listOf("|", "~", "{", "}", ">", "*", "$", ";", "\"", "(", ")", "%", "=", "&"))
                if (t.solution.contains(c)) assertTrue("${t.id}: '$c' not in keys", c in t.keys)
    }
}
