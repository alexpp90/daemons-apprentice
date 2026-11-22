package io.github.alexpp90.daemonsapprentice.game

/**
 * The pieces a command line is tapped together from (R28): split at spaces, but a quoted
 * text, a `$( )` or a `${ }` stays one piece, and `;` is a piece of its own. Tapping the
 * pieces in order (the chip inserts a space before each) gives a line that does the same.
 */
object ChipWords {
    fun split(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        var depth = 0
        fun push() { if (cur.isNotEmpty()) { out += cur.toString(); cur.setLength(0) } }
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quote != null -> { cur.append(c); if (c == quote) quote = null }
                c == '\'' || c == '"' -> { quote = c; cur.append(c) }
                c == '$' && i + 1 < line.length && (line[i + 1] == '(' || line[i + 1] == '{') -> { depth++; cur.append(c).append(line[i + 1]); i++ }
                depth > 0 && (c == ')' || c == '}') -> { depth--; cur.append(c) }
                depth > 0 -> cur.append(c)
                c.isWhitespace() -> push()
                c == ';' -> { push(); out += ";" }
                else -> cur.append(c)
            }
            i++
        }
        push()
        return out
    }
}
