package io.github.alexpp90.daemonsapprentice.game

import java.io.File

/** Level 9, "Building Blocks": a command inside a command, the `>` prompt, loops, and a loop that saves an afternoon. */
object Level9 {

    private fun blocks(d: GameDirs) = File(d.playground, "blocks")
    private fun calendar(d: GameDirs) = File(d.playground, "calendar")
    private fun photos(d: GameDirs) = File(d.playground, "photos")

    private val inBlocks: suspend TaskContext.() -> Unit = {
        val b = blocks(dirs)
        listOf("a", "b", "c").forEach { File(b, "$it.txt").ensureText("This is $it.\n") }
        goTo(b)
    }

    private fun inCalendar(clean: Boolean): suspend TaskContext.() -> Unit = {
        val c = calendar(dirs)
        if (clean) c.emptyIt()
        else (1..365).forEach { File(c, "day-$it").mkdirs() }
        c.mkdirs()
        goTo(c)
    }

    private val inPhotos: suspend TaskContext.() -> Unit = {
        val p = photos(dirs)
        p.emptyIt()
        (1..4).forEach { File(p, "IMG_$it.JPG").writeText("(a photo)\n") }
        goTo(p)
    }

    private fun days(a: Attempt) = File(a.pg, "calendar").listFiles()?.count { it.isDirectory && Regex("""day-\d+""").matches(it.name) } ?: 0

    val level = Level(
        number = 9,
        title = "Building Blocks",
        intro = listOf(
            "${Pid1.RUNNING}  The shell is not only a place to type commands. It is a programming language.",
            "Today: a command inside a command, and **loops**, which do the same thing again and again so you don't have to. We start in `blocks`.",
        ),
        tasks = listOf(
            Task(
                id = "t9_1", title = "A command inside a command",
                card = "`\$( )` runs the command inside it first and puts its output in its place. `date +%A` prints just the weekday.",
                chunks = listOf("Quotes keep the sentence together:\n\n      `echo \"today is \$(date +%A)\"`"),
                command = "echo \"today is \$(date +%A)\"",
                parts = listOf("\$(date +%A)" to "replaced by the weekday", "\"...\"" to "one sentence for echo"),
                chips = listOf("echo", "\"today is \$(date +%A)\""), keys = listOf("\"", "$", "(", ")", "%"),
                prep = inBlocks,
                check = { a, _ -> a.ran("echo") && a.output.trim().startsWith("today is ") && !a.outHas("$(") && a.output.trim().length > 12 },
                hint1 = "`echo`, then in double quotes: `today is \$(date +%A)`.", hint2 = "`echo \"today is \$(date +%A)\"`",
                solution = "echo \"today is \$(date +%A)\"", solutionWhy = "The shell ran `date +%A` first and put the weekday into the sentence.",
                fact = "Commands inside commands: that is how scripts use one result to build the next command.",
                how = "echo printed the sentence with the weekday filled in",
            ),
            Task(
                id = "t9_2", title = "The shell is waiting",
                card = "Forget the closing quote, and the shell waits for the rest of the command. It shows its second prompt, `>`.",
                chunks = listOf("Type this, with only one quote, then press Enter:\n\n      `echo \"hello`\n\nAt the `>` prompt, get out with **Ctrl-C**."),
                command = "echo \"hello", chips = listOf("echo", "\"hello"), keys = listOf("\""),
                drive = "more-ctrlc",
                prep = inBlocks,
                check = { a, fb ->
                    if (a.interrupted && a.line.trim().startsWith("echo")) true
                    else { if (a.ran("echo") && !a.interrupted) fb.say("That closed the quote, which works too. This time try the way out: `echo \"hello`, Enter, then Ctrl-C at the `>` prompt."); false }
                },
                hint1 = "`echo \"hello` (one quote), Enter; then Ctrl-C.", hint2 = "`echo \"hello`, Enter, Ctrl-C (on the key bar).",
                solution = "echo \"hello", solutionWhy = "Ctrl-C at the `>` prompt throws the unfinished command away.",
                fact = "Stuck at a `>`? Either finish the command (here: type the closing quote) or Ctrl-C.",
                how = "the unfinished echo was stopped with Ctrl-C",
            ),
            Task(
                id = "t9_3", title = "A loop, line by line",
                card = "A `for` loop runs the commands between `do` and `done` once for every word after `in`. Each time, the variable holds the next word.",
                chunks = listOf("Type it as four lines, Enter after each. The shell shows `>` until the loop is complete:\n\n      `for n in 1 2 3`\n      `do`\n      `echo \$n`\n      `done`"),
                command = "for n in 1 2 3", chips = listOf("for", "n", "in", "1", "2", "3", "do", "echo", "\$n", "done"), keys = listOf("$"),
                drive = "lines",
                prep = inBlocks,
                check = { a, _ -> a.ran("for") && a.lines() == listOf("1", "2", "3") },
                hint1 = "First line `for n in 1 2 3`, then `do`, then `echo \$n`, then `done`.", hint2 = "Four lines: `for n in 1 2 3`  `do`  `echo \$n`  `done`",
                solution = "for n in 1 2 3\ndo\necho \$n\ndone", solutionWhy = "Three words after `in`, so the echo ran three times: 1, 2, 3.",
                fact = "`n` is a variable like planet in Level 6: each round it holds the next word.",
                how = "the loop printed 1 2 3",
            ),
            Task(
                id = "t9_4", title = "The same loop, one line",
                card = "`;` ends a command, like Enter does. So the loop fits on one line.",
                chunks = listOf("      `for n in 1 2 3; do echo \$n; done`"),
                command = "for n in 1 2 3; do echo \$n; done",
                chips = listOf("for", "n", "in", "1", "2", "3", ";", "do", "echo", "\$n", "done"), keys = listOf(";", "$"),
                prep = inBlocks,
                check = { a, _ -> a.ran("for") && a.lines() == listOf("1", "2", "3") },
                hint1 = "Like the four lines, with `;` instead of the Enter after `3` and after `\$n`.", hint2 = "`for n in 1 2 3; do echo \$n; done`",
                solution = "for n in 1 2 3; do echo \$n; done", solutionWhy = "The same loop: `;` separates the parts.",
                fact = "One-line loops are what you will see most, in scripts and from AI helpers. Now you can read them.",
                how = "the loop printed 1 2 3",
            ),
            Task(
                id = "t9_5", title = "A loop over files",
                chunks = listOf("`*.txt` stands for all the text files here. Loop over them:\n\n      `for f in *.txt; do echo \$f; done`"),
                command = "for f in *.txt; do echo \$f; done",
                chips = listOf("for", "f", "in", "*.txt", ";", "do", "echo", "\$f", "done"), keys = listOf("*", ";", "$"),
                prep = inBlocks,
                check = { a, _ -> a.ran("for") && a.lines() == listOf("a.txt", "b.txt", "c.txt") },
                hint1 = "Like the loop before, with `f` and `*.txt`.", hint2 = "`for f in *.txt; do echo \$f; done`",
                solution = "for f in *.txt; do echo \$f; done", solutionWhy = "The shell turned `*.txt` into the three names, and the loop went through them.",
                fact = "Change `echo` into any command, and it works on every file. That is the trick of the next tasks.",
                how = "the loop printed the three file names",
            ),
            Task(
                id = "t9_6", title = "Counting",
                chunks = listOf("`seq` counts for you:\n\n      `seq 3`"),
                command = "seq 3", chips = listOf("seq", "3"),
                prep = inBlocks,
                check = { a, _ -> a.ran("seq") && a.lines() == listOf("1", "2", "3") },
                hint1 = "`seq` and a number.", hint2 = "`seq 3`",
                solution = "seq 3", solutionWhy = "`seq 3` prints the numbers from 1 to 3, one per line.",
                fact = "`seq 365` would print a whole year of numbers. Together with `\$( )` and a loop, that is a superpower.",
                how = "seq printed 1 2 3",
            ),
            Task(
                id = "t9_7", title = "A folder for every day",
                card = "You want a folder for every day of the year. By tapping in the Files app: an afternoon. In the terminal: one line. We are in `calendar` now.",
                chunks = listOf("Take your time typing it:\n\n      `for i in \$(seq 365); do mkdir day-\$i; done`"),
                command = "for i in \$(seq 365); do mkdir day-\$i; done",
                parts = listOf("\$(seq 365)" to "the numbers 1 to 365", "mkdir day-\$i" to "a folder for each"),
                chips = listOf("for", "i", "in", "\$(seq 365)", ";", "do", "mkdir", "day-\$i", "done"), keys = listOf("$", "(", ")", ";"),
                prep = inCalendar(true),
                check = { a, fb ->
                    val n = days(a)
                    if (n == 365) true else { if (n > 0) fb.say("$n folders, not 365. Check the number after seq."); false }
                },
                hint1 = "A loop over `\$(seq 365)`, with `mkdir day-\$i` inside.", hint2 = "`for i in \$(seq 365); do mkdir day-\$i; done`",
                solution = "for i in \$(seq 365); do mkdir day-\$i; done", solutionWhy = "seq made the numbers, the loop made a folder for each: day-1 to day-365.",
                fact = "A computer's Bash can write `{1..365}` instead of `\$(seq 365)`. The phone's shell cannot, but the loop works everywhere.",
                how = "calendar holds 365 day folders",
            ),
            Task(
                id = "t9_8", title = "Count them",
                chunks = listOf("Did it really make 365? Count with the pipe from Level 4:\n\n      `ls | wc -l`"),
                command = "ls | wc -l", chips = listOf("ls", "|", "wc", "-l"), keys = listOf("|", "-"),
                prep = inCalendar(false),
                check = { a, _ -> a.ran("wc") && a.output.trim() == "365" },
                hint1 = "`ls`, a pipe, `wc -l`.", hint2 = "`ls | wc -l`",
                solution = "ls | wc -l", solutionWhy = "ls lists one name per line into the pipe; wc counts the lines: 365.",
                fact = "Always count after a big job. Typing it took you a minute; checking took a second.",
                how = "wc counted 365",
            ),
            Task(
                id = "t9_9", title = "A photo's name",
                card = "Cameras name photos like `IMG_1.JPG`, in capitals. Let's say you want `.jpg`. First, a name in a variable. We are in `photos` now.",
                chunks = listOf("      `f=IMG_1.JPG`"),
                command = "f=IMG_1.JPG", chips = listOf("f=IMG_1.JPG"), keys = listOf("="),
                prep = inPhotos,
                check = { a, _ -> Regex("""^f=\S+$""").matches(a.line.trim()) && a.status == 0 },
                hint1 = "`f`, `=`, the name, no spaces.", hint2 = "`f=IMG_1.JPG`",
                solution = "f=IMG_1.JPG", solutionWhy = "The variable f now holds the photo's name.",
                fact = "Silence again: it worked.",
                how = "the line set f",
            ),
            Task(
                id = "t9_10", title = "Cut off the end",
                card = "`\${f%.JPG}` is f's value with `.JPG` cut off the end. Then add `.jpg`.",
                chunks = listOf("      `echo \${f%.JPG}.jpg`"),
                command = "echo \${f%.JPG}.jpg", chips = listOf("echo", "\${f%.JPG}.jpg"), keys = listOf("$", "{", "}", "%"),
                prep = { goTo(photos(dirs)) },
                check = { a, fb ->
                    if (a.ran("echo") && a.output.trim() == "IMG_1.jpg") true
                    else { if (a.output.trim() == ".jpg") fb.say("Only `.jpg`: this shell does not know f (it starts fresh with the app). Set it again: `f=IMG_1.JPG`, then this."); false }
                },
                hint1 = "`echo`, then `\${f%.JPG}` and `.jpg` stuck together.", hint2 = "`echo \${f%.JPG}.jpg`",
                solution = "echo \${f%.JPG}.jpg", solutionWhy = "`%.JPG` cut the old ending off; `.jpg` is the new one.",
                fact = "`%` cuts from the end, `#` from the start: `\${f#IMG_}` would be `1.JPG`.",
                how = "echo printed IMG_1.jpg",
            ),
            Task(
                id = "t9_11", title = "Rename them all",
                chunks = listOf("Now every photo here, in one line:\n\n      `for f in *.JPG; do mv \$f \${f%.JPG}.jpg; done`"),
                command = "for f in *.JPG; do mv \$f \${f%.JPG}.jpg; done",
                chips = listOf("for", "f", "in", "*.JPG", ";", "do", "mv", "\$f", "\${f%.JPG}.jpg", "done"), keys = listOf("*", ";", "$", "{", "}", "%"),
                prep = inPhotos,
                check = { a, _ ->
                    val names = File(a.pg, "photos").list()?.toList() ?: emptyList()
                    names.size == 4 && names.all { it.endsWith(".jpg") }
                },
                hint1 = "A loop over `*.JPG`; inside: `mv \$f` and the new name.", hint2 = "`for f in *.JPG; do mv \$f \${f%.JPG}.jpg; done`",
                solution = "for f in *.JPG; do mv \$f \${f%.JPG}.jpg; done", solutionWhy = "For each photo: move (rename) it to the same name with the new ending.",
                fact = "Four photos or four thousand: the same line. `ls` shows them renamed.",
                how = "all four photos end in .jpg",
            ),
        ),
        outro = listOf(
            "Commands inside commands, the `>` prompt, loops in lines and in one line, a year of folders, photos renamed.",
            "That is the end of the Linux part. From Level 10 on, the phone's own machinery: the walls you cannot see, and who builds them.",
        ),
        explore = listOf(
            "The Files app can rename one file at a time. Some file managers can rename many at once: underneath, they run a loop like yours.",
        ),
    )
}
