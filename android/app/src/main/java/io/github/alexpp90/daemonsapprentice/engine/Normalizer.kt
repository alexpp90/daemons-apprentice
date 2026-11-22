package io.github.alexpp90.daemonsapprentice.engine

/**
 * Undoes what phone keyboards do to commands: curly quotes, long dashes,
 * a space after a suggestion, a capital letter at the start. Says what it
 * changed, so the player learns what the keyboard did.
 */
object Normalizer {

    data class Result(val line: String, val changes: List<String>)

    private val knownLower = setOf(
        "echo", "ls", "pwd", "cat", "grep", "cd", "help", "hint", "date", "whoami", "mkdir",
        "touch", "cp", "mv", "rm", "head", "tail", "wc", "sort", "uniq", "ps", "kill", "id",
        "uname", "getprop", "keys", "menu", "files", "cheat", "skip", "task", "explain", "quit",
    )

    fun normalize(raw: String): Result {
        val changes = mutableListOf<String>()
        var s = raw
        val quotes = s.replace('“', '"').replace('”', '"').replace('„', '"')
            .replace('‘', '\'').replace('’', '\'').replace('‚', '\'')
        if (quotes != s) { changes += "curly quotes became straight ones"; s = quotes }
        val dashes = s.replace('–', '-').replace('—', '-').replace('−', '-')
        if (dashes != s) { changes += "a long dash became a minus"; s = dashes }
        // A no-break space looks like a space; a space at the end comes from
        // the keyboard's suggestion. Neither is worth a note.
        s = s.replace(' ', ' ').trim()
        val first = s.substringBefore(' ')
        if (first.isNotEmpty() && first[0].isUpperCase() && first.lowercase() in knownLower &&
            first != first.lowercase()) {
            changes += "\"$first\" became \"${first.lowercase()}\": commands are small letters"
            s = first.lowercase() + s.substring(first.length)
        }
        return Result(s, changes)
    }
}
