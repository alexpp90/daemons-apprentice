package io.github.alexpp90.daemonsapprentice.game

import java.io.File

/** The level list, in one place (R8). */
object Levels {
    val all: List<Level> by lazy { listOf(Level0.level, Level1.level, Level2.level, Level3.level, Level4.level,
        Level5.level, Level6.level, Level7.level, Level8.level, Level9.level) }
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
