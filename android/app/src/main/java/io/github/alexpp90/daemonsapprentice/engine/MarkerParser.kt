package io.github.alexpp90.daemonsapprentice.engine

/**
 * Splits what the shell writes to the terminal into output and prompts.
 *
 * The game sets the shell's prompts to control characters the player
 * cannot type: PS1 is `\u001e<exit status>;<current folder>\u001f`, PS2
 * (the shell waits for the rest of an unfinished command) is `\u001d`.
 * Everything else is the output of the command that ran.
 */
class MarkerParser(private val sink: (Event) -> Unit) {

    sealed interface Event {
        data class Output(val text: String) : Event
        data class Prompt(val status: Int, val folder: String) : Event
        data object MoreInput : Event
    }

    private val out = StringBuilder()
    private val marker = StringBuilder()
    private var inMarker = false
    private var escape = 0 // 0 none, 1 after ESC, 2 inside CSI

    fun feed(chunk: String) {
        for (c in chunk) {
            if (inMarker) {
                if (c == END) {
                    inMarker = false
                    flush()
                    sink(parsePrompt(marker.toString()))
                    marker.setLength(0)
                } else marker.append(c)
                continue
            }
            when {
                escape == 1 -> escape = if (c == '[') 2 else 0
                escape == 2 -> if (c in '@'..'~') escape = 0
                c == START -> { inMarker = true }
                c == MORE -> { flush(); sink(Event.MoreInput) }
                c == '\u001b' -> escape = 1
                c == '\r' -> {}
                c == '\n' || c == '\t' -> out.append(c)
                c < ' ' || c == '\u007f' -> {}
                else -> out.append(c)
            }
        }
        flush()
    }

    private fun flush() {
        if (out.isNotEmpty()) {
            sink(Event.Output(out.toString()))
            out.setLength(0)
        }
    }

    companion object {
        const val START = '\u001e'
        const val END = '\u001f'
        const val MORE = '\u001d'

        fun parsePrompt(body: String): Event.Prompt {
            val semi = body.indexOf(';')
            val status = if (semi > 0) body.substring(0, semi).trim().toIntOrNull() ?: 0 else 0
            val folder = if (semi >= 0) body.substring(semi + 1) else ""
            return Event.Prompt(status, folder)
        }
    }
}
