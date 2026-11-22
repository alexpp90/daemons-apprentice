package io.github.alexpp90.daemonsapprentice.game

import java.io.File

/** The level list, in one place (R8). */
object Levels {
    val all: List<Level> by lazy { listOf(Level0.level, Level1.level, Level2.level, Level3.level, Level4.level) }
}

/** Writes the file unless it already has exactly this text. */
internal fun File.ensureText(text: String) {
    if (!exists() || readText() != text) { parentFile?.mkdirs(); writeText(text) }
}

/** Moves the game's shell to [dir] (said out loud only when the task needs it). */
internal suspend fun TaskContext.goTo(dir: File) {
    dir.mkdirs()
    if (shell.folder != dir.absolutePath) shell.runQuiet("cd '${dir.absolutePath}'")
}
