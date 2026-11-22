package io.github.alexpp90.daemonsapprentice.engine

import java.io.File

/**
 * The save file: plain `key=value` lines, read with a whitelist of keys and
 * characters, never executed. Whatever is stored on the player's side, the
 * player controls (Level 7 shows it).
 */
class SaveFile(private val file: File) {

    val values = linkedMapOf<String, String>()

    fun load(): SaveFile {
        values.clear()
        if (!file.exists()) return this
        file.readLines().forEach { line ->
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            if (key in KEYS && value.all { it.isLetterOrDigit() || it in " _-.,:" } && value.length <= 200)
                values[key] = value
        }
        return this
    }

    fun store() {
        file.parentFile?.mkdirs()
        val text = buildString {
            append("# The Daemon's Apprentice: your progress. Plain text, read by the game, never run.\n")
            values.forEach { (k, v) -> append(k).append('=').append(v).append('\n') }
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) { file.writeText(text); tmp.delete() }
    }

    operator fun get(key: String) = values[key]
    operator fun set(key: String, value: String) { values[key] = value }
    fun int(key: String, default: Int = 0) = values[key]?.toIntOrNull() ?: default

    companion object {
        val KEYS = setOf("version", "level", "task", "done", "flags", "intro", "cheat", "badge")
    }
}
