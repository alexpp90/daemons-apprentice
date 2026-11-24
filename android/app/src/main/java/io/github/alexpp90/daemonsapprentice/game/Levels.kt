package io.github.alexpp90.daemonsapprentice.game

import java.io.File

/** The level list, in one place (R8). */
object Levels {
    /** The main path, in order. */
    val all: List<Level> by lazy { listOf(Level0.level, Level1.level, Level2.level, Level3.level, Level4.level,
        Level5.level, Level6.level, Level7.level, Level8.level, Level9.level,
        Level10.level, Level11.level, Level12.level, Level13.level, Level14.level, Level15.level) }
    /** The side quests: optional, each offered after its main level. */
    val side: List<Level> by lazy { listOf(SideQuest1.level) }
    /** Everything the game knows: the main path, then the side quests. */
    val everything: List<Level> by lazy { all + side }

    /** The order a player who says yes to every offer meets the levels in (from main level [from] on). */
    fun playOrder(from: Int = 0): List<Level> = all.filter { it.number >= from }.flatMap { l ->
        listOf(l) + side.filter { it.after == l.number && all.any { m -> m.number > l.number } }
    }
}

/** Writes the file unless it already has exactly this text. */
internal fun File.ensureText(text: String) {
    if (!exists() || readText() != text) { parentFile?.mkdirs(); writeText(text) }
}

/**
 * Empties a folder but keeps it: the game's shell may stand in it, and a folder deleted and made
 * again is a different folder (the shell would be left in the deleted one).
 */
internal fun File.emptyIt() {
    mkdirs()
    listFiles()?.forEach { it.deleteRecursively() }
}

/** Moves the game's shell to [dir] (said out loud only when the task needs it). */
internal suspend fun TaskContext.goTo(dir: File) {
    dir.mkdirs()
    if (shell.folder != dir.absolutePath) shell.runQuiet("cd '${dir.absolutePath}'")
}
