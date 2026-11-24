package io.github.alexpp90.daemonsapprentice.game

import kotlin.random.Random

object Pid1 {
    /** Pid1's one-line moods: real `ps` process states, as in the desktop game. */
    const val SLEEPING = "~( o_o )~"
    const val RUNNING = "~( ^_^ )~"
    const val INSPECTING = "~( o_O )~"
    const val STOPPED = "~( !_! )~"
    const val CHEER = "* ~( ^_^ )~ *"

    val ghost = listOf(
        "   .-.",
        "  (o o)  psst.",
        "  | O \\",
        "   \\   \\",
        "    `~~~'",
    ).joinToString("\n")

    val levelDone = listOf(
        listOf("  \\o/   .-.", "   |   (^ ^)", "  / \\  | O \\", "         `~~~'"),
        listOf("   *  .-.  *", "     (^o^)", "  *  | O \\  *", "      `~~~'"),
    )
}

object Praise {
    private val lines = listOf(
        "Solved!", "Nailed it.", "That's the one.", "Your phone did exactly what you said.",
        "Clean.", "Look at you, typing commands.", "Perfect.", "Yes!", "Smooth.",
        "The terminal likes you.", "Right on.", "That worked first time. Or did it?",
    )
    private val moods = listOf(Pid1.RUNNING, Pid1.CHEER, "~( ^o^ )~", "\\~( ^_^ )~/")
    fun next(random: Random = Random.Default): String = "${moods.random(random)}  ${lines.random(random)}"
}

object HelpText {
    val help = """
        **The game's help** (works at every prompt):
          `hint`     a nudge; again: a stronger one; again: the solution
          `explain`  the background and the task again
          `keys`     where the hard characters are on your keyboard
          `cheat`    every command you have learned so far
          `menu`     levels and settings
          `skip`     skip this task
          `edit file`  the game's editor (a phone has no nano)
        **The terminal's own help:**
          `ls --help`   most commands explain themselves with `--help`
          Ctrl-C        stops a command (on the key bar above the keyboard)
        On a phone there is no `man`. Long help opens in a page you can scroll.
    """.trimIndent()

    val keys = """
        **Hard characters on a phone keyboard**
          The key bar above the keyboard has them: Tab, arrows, - / . | > $ ~ *, Ctrl-C.
          On the keyboard itself they hide behind **?123** (and **=\<** for | ~ \ { }).
        **With a hardware keyboard (German layout)**
          |  AltGr + <        ~  AltGr + +       \  AltGr + ß
          {  AltGr + 7        }  AltGr + 0       @  AltGr + Q
          [  AltGr + 8        ]  AltGr + 9       ^  ^ then Space
        **US layout:** | is Shift + \, ~ is Shift + `, { } are Shift + [ ].
    """.trimIndent()

}

/** Where the hard characters are (R3, R4): shown the first time a task needs one. */
object KeyHelp {
    val where = linkedMapOf(
        "|" to ("the pipe" to "AltGr + <"),
        "~" to ("the tilde" to "AltGr + +"),
        "{" to ("an opening brace" to "AltGr + 7"),
        "}" to ("a closing brace" to "AltGr + 0"),
        "[" to ("an opening bracket" to "AltGr + 8"),
        "]" to ("a closing bracket" to "AltGr + 9"),
        "\\" to ("a backslash" to "AltGr + ß"),
        ">" to ("the arrow" to "Shift + <"),
        "*" to ("the star" to "Shift + +"),
        "$" to ("the dollar sign" to "Shift + 4"),
        ";" to ("a semicolon" to "Shift + ,"),
        "\"" to ("a double quote" to "Shift + 2"),
        "(" to ("an opening parenthesis" to "Shift + 8"),
        ")" to ("a closing parenthesis" to "Shift + 9"),
        "%" to ("the percent sign" to "Shift + 5"),
        "=" to ("the equals sign" to "Shift + 0"),
        "&" to ("an ampersand" to "Shift + 6"),
    )

    fun card(keys: List<String>): String = buildString {
        append("**New on the keyboard:**\n")
        keys.forEach { k ->
            val (name, german) = where.getValue(k)
            append("  `").append(k).append("`  ").append(name)
                .append(": on the key bar above the keyboard; on a German hardware keyboard ").append(german).append("\n")
        }
        append("On the phone's keyboard it hides behind **?123** (and **=\\<**). Type `keys` for all of them.")
    }
}
