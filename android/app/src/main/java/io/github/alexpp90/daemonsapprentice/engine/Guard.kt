package io.github.alexpp90.daemonsapprentice.engine

/**
 * A seatbelt for the game's own shell. Android's sandbox already keeps the
 * player inside the app; the guard only stops what would break the game
 * itself (its own files, its shell), with an explanation. It errs on the
 * side of blocking.
 */
class Guard(private val gameDir: String, private val saveFile: String) {

    private companion object {
        val WRITERS = setOf("rm", "rmdir", "mv", "cp", "chmod", "touch", "mkdir", "ln", "tee", "sed", "dd", "truncate", "tar", "gzip", "unzip")
    }

    data class Verdict(val allowed: Boolean, val reason: String = "")

    /** Words of a command line, split at spaces and shell operators; quotes removed. */
    fun words(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        fun push() { if (cur.isNotEmpty()) { out += cur.toString(); cur.setLength(0) } }
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quote != null -> if (c == quote) quote = null else cur.append(c)
                c == '\'' || c == '"' -> quote = c
                c == '\\' && i + 1 < line.length -> { cur.append(line[i + 1]); i++ }
                c.isWhitespace() -> push()
                c in ";|&<>()" -> { push(); out += c.toString() }
                else -> cur.append(c)
            }
            i++
        }
        push()
        return out
    }

    /** The first word of every simple command in the line: what actually runs. */
    fun commands(line: String): List<String> {
        val w = words(line)
        val out = mutableListOf<String>()
        var expect = true
        for (x in w) {
            if (x in setOf(";", "|", "&", "(", ")")) { expect = true; continue }
            if (x == "<" || x == ">") continue
            if (expect) {
                if (x.contains('=') && !x.startsWith("=")) continue // planet=Mars before a command
                out += x
                expect = false
            }
        }
        return out
    }

    /** Where a word points to, if it were a path: `~`, `..` and relative names resolved; globs cut at the first wildcard. */
    fun resolve(word: String, folder: String, home: String): String {
        var w = word.substringBefore('*').substringBefore('?').substringBefore('[')
        w = when {
            w == "~" -> home
            w.startsWith("~/") -> home + w.substring(1)
            w.startsWith("\$HOME") -> home + w.removePrefix("\$HOME")
            else -> w
        }
        val f = if (w.startsWith("/")) java.io.File(w) else java.io.File(folder, w)
        return f.toPath().normalize().toString()
    }

    fun check(line: String, folder: String = "/", home: String = "/", saveAllowed: Boolean = false): Verdict {
        val compact = line.replace(" ", "")
        if (compact.contains(":(){") || compact.contains(":|:&"))
            return Verdict(false, "That is a fork bomb: a program that copies itself until the phone gives up. Not in this game.")
        val cmds = commands(line)
        for (c in cmds) {
            when (c) {
                "exec" -> return Verdict(false, "`exec` would replace the game's shell with another program, and the game would lose its terminal.")
                "trap", "ulimit" -> return Verdict(false, "`$c` would change the game's own shell. The game needs it as it is.")
                "set" -> if (Regex("""\bset\s+-[a-z]*e""").containsMatchIn(line))
                    return Verdict(false, "`set -e` would make the game's shell quit at the first error.")
            }
        }
        val w = words(line)
        val pathWords = w.filter { it !in setOf(";", "|", "&", "(", ")", "<", ">") && !it.startsWith("-") }
        val paths = pathWords.map { resolve(it, folder, home) }
        // A glob could match the game's folder if the folder's path starts with what comes before the wildcard.
        val globHits = pathWords.filter { it.any { c -> c in "*?[" } }.map { resolve(it, folder, home) }
            .any { p -> gameDir.startsWith(if (p.endsWith("/")) p else p) && gameDir.length >= p.length }
        val touchesGame = globHits || paths.any { it == gameDir || it.startsWith("$gameDir/") } || w.any { it.contains("/files/game") }
        val writes = cmds.any { it in WRITERS } || w.contains(">")
        // Reading is harmless; only changes to the game's own folder are stopped.
        if (touchesGame && writes) return Verdict(false, "That would change the game's own folder. Your playground is everything else in your app's room.")
        if (!saveAllowed && writes && (paths.any { it == saveFile } || w.any { it.endsWith(".apprentice-save") }))
            return Verdict(false, "That is your save file. A later level shows how to look at it safely.")
        return Verdict(true)
    }
}
