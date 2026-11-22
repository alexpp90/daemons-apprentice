package io.github.alexpp90.daemonsapprentice.game

import kotlin.random.Random

object Pid1 {
    /** Pid1's one-line moods: real `ps` process states (desktop CONCEPT "Mascot"). */
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
