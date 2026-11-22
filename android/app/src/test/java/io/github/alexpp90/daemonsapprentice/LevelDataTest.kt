package io.github.alexpp90.daemonsapprentice

import io.github.alexpp90.daemonsapprentice.engine.Guard
import io.github.alexpp90.daemonsapprentice.game.GameEngine
import io.github.alexpp90.daemonsapprentice.game.Level0
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules every task must follow (REQUIREMENTS R3, R4, R5, R28). */
class LevelDataTest {
    private val levels = listOf(Level0.level)
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

    /** R28: in Levels 0 and 1 every task can be solved by tapping chips alone. */
    @Test fun chipsCoverTheSolutionInTheFirstLevels() {
        for (lv in levels.filter { it.number <= 1 }) for (t in lv.tasks) {
            val words = guard.words(t.solution)
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
