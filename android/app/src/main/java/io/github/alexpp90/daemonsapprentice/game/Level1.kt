package io.github.alexpp90.daemonsapprentice.game

/** Level 1, "Talking to the Shell" (ANDROID_CURRICULUM.md). */
object Level1 {
    private val home: suspend TaskContext.() -> Unit = { goTo(dirs.playground) }

    val level = Level(
        number = 1,
        title = "Talking to the Shell",
        intro = listOf(
            "${Pid1.SLEEPING}  Psst, Pid1 again. The program that reads what you type is called the **shell**. Let's get to know it.",
            "One new thing per task. If something goes wrong: good. That's how you learn the terminal.",
        ),
        tasks = listOf(
            Task(
                id = "t1_1", title = "Who are you?",
                chunks = listOf("The shell knows who is typing. Ask it:\n\n      `whoami`      (who am I?)"),
                command = "whoami", chips = listOf("whoami"), decoys = listOf("who", "me"),
                prep = home,
                check = { a, _ -> a.ran("whoami") && a.status == 0 },
                hint1 = "One word, no spaces: who-am-i.", hint2 = "`whoami`, then Enter.",
                solution = "whoami", solutionWhy = "`whoami` prints the name of the user who runs the shell.",
                fact = "That odd name is not you: it is **this app**. On Android every app is its own user. Level 7 shows why that keeps your phone safe.",
                how = "whoami ran without an error",
            ),
            Task(
                id = "t1_2", title = "What time is it?",
                chunks = listOf("Ask your phone for the date and time:\n\n      `date`"),
                command = "date", chips = listOf("date"), decoys = listOf("time", "clock"),
                prep = home,
                check = { a, _ -> a.ran("date") && a.status == 0 },
                hint1 = "Four letters: d, a, t, e.", hint2 = "`date`, then Enter.",
                solution = "date", solutionWhy = "`date` prints the date and the time.",
                fact = "Your phone counts the time in seconds since 1 January 1970. `date` turns that into words.",
                how = "date ran without an error",
            ),
            Task(
                id = "t1_3", title = "An option",
                chunks = listOf("Many commands have **options**: a minus and a letter that change what they do. `-u` shows the time on the world's clock (UTC) instead of yours:\n\n      `date -u`\n\nThe minus is on the key bar above the keyboard."),
                command = "date -u",
                parts = listOf("date" to "the command", "-u" to "an option: universal time"),
                chips = listOf("date", "-u"), decoys = listOf("u", "--"), keys = listOf("-"),
                prep = home,
                check = { a, fb ->
                    if (a.ran("date") && "-u" in a.words && a.status == 0) true
                    else { if (a.line.contains("date-u")) fb.say("Put a space between `date` and `-u`."); false }
                },
                hint1 = "`date`, a space, a minus, a small u.", hint2 = "`date -u`",
                solution = "date -u", solutionWhy = "`-u` is an option: it tells `date` to use universal time.",
                fact = "Options start with a minus. Almost every command has some.",
                how = "date ran with the option -u",
            ),
            Task(
                id = "t1_4", title = "The terminal's own help",
                chunks = listOf("Which options are there? Most commands explain themselves with `--help` (two minus signs):\n\n      `date --help`"),
                command = "date --help",
                chips = listOf("date", "--help"), decoys = listOf("help", "-help"), keys = listOf("-"),
                prep = home,
                check = { a, fb ->
                    if (a.ran("date") && "--help" in a.words && a.output.isNotBlank()) true
                    else { if ("-help" in a.words) fb.say("Two minus signs: `--help`."); false }
                },
                hint1 = "`date`, a space, then `--help` with two minus signs.", hint2 = "`date --help`",
                solution = "date --help", solutionWhy = "`--help` asks a command to explain its options.",
                fact = "On a computer, `man date` opens the full manual. A phone has no manuals, so `--help` is the help that comes along.",
                how = "date ran with --help and printed something",
            ),
            Task(
                id = "t1_5", title = "Errors are friends",
                chunks = listOf("Now make a typo, on purpose. Type `echo` wrong:\n\n      `ehco hello`\n\nAn error is the result we want here."),
                command = "ehco hello", chips = listOf("ehco", "hello"),
                prep = home,
                check = { a, _ -> a.status == 127 },
                hint1 = "Swap the h and the c in echo: `ehco`.", hint2 = "`ehco hello`",
                solution = "ehco hello", solutionWhy = "`ehco` is no command, so the shell says it cannot find it.",
                fact = "The shell says something like `ehco: inaccessible or not found`. Errors say what went wrong: read them.",
                how = "the shell reported an unknown command (exit status 127)",
            ),
            Task(
                id = "t1_6", title = "Fix it, don't retype it",
                chunks = listOf("Don't type the line again. Tap **↑** (on the key bar, or the Up arrow on a keyboard): your last line comes back. Move with **←** to the mistake, fix it, Enter.\n\n      `echo hello`"),
                command = "echo hello", chips = listOf("echo", "hello"), decoys = listOf("ehco"),
                prep = home,
                check = { a, _ -> a.ran("echo") && a.outHas("hello") },
                hint1 = "↑ brings back `ehco hello`. Fix `ehco` to `echo`.", hint2 = "In the end the line reads `echo hello`.",
                solution = "echo hello", solutionWhy = "↑ recalls the last line; fixed, it is `echo hello`.",
                fact = "↑ goes back through everything you typed, ↓ comes forward again. Huge time saver.",
                how = "echo hello ran",
                drive = "up-fix",
            ),
            Task(
                id = "t1_7", title = "Your history",
                chunks = listOf("The shell remembers what you typed. See it:\n\n      `history`"),
                command = "history", chips = listOf("history"), decoys = listOf("past"),
                prep = home,
                check = { a, _ -> a.ran("history") && a.status == 0 && a.outHas("echo") },
                hint1 = "One word: history.", hint2 = "`history`",
                solution = "history", solutionWhy = "`history` lists the lines the shell has run, numbered.",
                fact = "`help` is missing from the list: game commands never reach the shell.",
                how = "history ran and listed an earlier echo",
                drive = "device",
            ),
            Task(
                id = "t1_8", title = "The emergency brake",
                chunks = listOf(
                    "Sometimes a command runs too long. `sleep 30` does nothing, for 30 seconds.",
                    "Start it, then stop it: tap **Ctrl-C** (next to the line, or on the key bar), or press Ctrl+C on a keyboard.\n\n      `sleep 30`      then Ctrl-C",
                ),
                command = "sleep 30", chips = listOf("sleep", "30"), decoys = listOf("stop"),
                prep = home,
                check = { a, fb ->
                    if (a.ran("sleep") && (a.interrupted || a.status == 130)) true
                    else { if (a.ran("sleep") && a.status == 0) fb.say("It finished by itself. Start it again and stop it with Ctrl-C while it runs."); false }
                },
                hint1 = "Run `sleep 30`. While it runs, tap Ctrl-C.", hint2 = "Ctrl-C is the red key while a command runs.",
                solution = "sleep 30", solutionWhy = "`sleep 30` waits 30 seconds; Ctrl-C stops it early.",
                fact = "Ctrl-C is the terminal's emergency brake. It stops the command, never the shell.",
                how = "sleep was stopped with Ctrl-C (exit status 130)",
                drive = "ctrlc",
            ),
            Task(
                id = "t1_9", title = "A clean slate",
                chunks = listOf("The screen is full. Clean it:\n\n      `clear`"),
                command = "clear", chips = listOf("clear"), decoys = listOf("clean"), gameCommand = "clear",
                prep = home,
                check = { _, fb -> fb.say("Type `clear`."); false },
                hint1 = "One word: clear.", hint2 = "`clear`",
                solution = "clear", solutionWhy = "`clear` empties the screen; nothing else changes.",
                fact = "On a keyboard, Ctrl+L does the same.",
                how = "the screen was cleared",
            ),
        ),
        outro = listOf(
            "You asked who you are, read the time two ways, found the help, made an error on purpose, fixed it with ↑, read your history and pulled the emergency brake.",
            "That is how people really work in a terminal: try, read the answer, adjust. **Read first, then run.**",
        ),
        explore = listOf("Settings > Date & time: the same clock `date` reads. Change the time zone and run `date` again."),
    )
}
