package io.github.alexpp90.daemonsapprentice.game

import java.io.File

/** Level 0, "Hello, Terminal" (ANDROID_CURRICULUM.md): the vertical slice. */
object Level0 {

    /** A short history of computers, one event per line (facts checked for the desktop game, RESEARCH.md 1b). */
    val timeline = """
        1945  ENIAC, one of the first computers, fills a room.
        1947  The first computer "bug": a real moth.
        1969  Unix is born at Bell Labs.
        1969  First message on ARPANET: "LO". Then: crash.
        1971  The first email, and the @ sign.
        1973  Unix is rewritten in C: now it runs anywhere.
        1976  Steve Jobs and Steve Wozniak found Apple.
        1983  Richard Stallman starts GNU: a free Unix.
        1984  The first Macintosh: windows and a mouse.
        1989  Tim Berners-Lee proposes the World Wide Web.
        1991  Linus Torvalds, 21, starts Linux, "just a hobby".
        1998  Google starts in a garage.
        2001  Wikipedia starts.
        2007  The first iPhone.
        2008  The first Android phone. Inside: a Linux kernel.
        2016  AlphaGo, an AI, beats a world champion at Go.
        2021  Ingenuity, a helicopter running Linux, flies on Mars.
        2022  ChatGPT is released.
        2025  AI agents type commands in terminals. Like you.
    """.trimIndent() + "\n"

    private fun File.ensure(text: String) { if (!exists() || readText() != text) { parentFile?.mkdirs(); writeText(text) } }

    private val toPlayground: suspend TaskContext.() -> Unit = {
        dirs.playground.mkdirs()
        File(dirs.playground, "timeline.txt").ensure(timeline)
        if (shell.folder != dirs.playground.absolutePath) shell.runQuiet("cd '${dirs.playground.absolutePath}'")
    }

    val level = Level(
        number = 0,
        title = "Hello, Terminal",
        intro = listOf(
            "${Pid1.ghost}\nPsst. Down here. I'm **Pid1**, the very first program your phone started. Every other program is my great-great-grandchild.",
            "From inside an app you can't see me. Long story; we'll get to it. I'm a **daemon**: not the scary kind. The helpful kind. Mostly.",
            "Relax: nothing you type here can break your phone. We play in a folder that belongs only to this app.",
        ),
        tasks = listOf(
            Task(
                id = "t0_1", title = "Nothing to be afraid of",
                chunks = listOf(
                    "This screen is a **terminal**. It looks old-school, but it is just another way to use your phone: with text instead of taps. You type, the phone answers.",
                    "What you type is a **command**: a word that tells the phone what to do. You type it, then press Enter (or tap ⏎).\n\nThe most useful command first. Whenever you are stuck, type:\n\n      `help`",
                ),
                command = "help", chips = listOf("help"), gameCommand = "help",
                prep = toPlayground,
                check = { _, fb -> fb.say("Type just the word `help` and press Enter."); false },
                hint1 = "Four letters: h, e, l, p. Then Enter.",
                hint2 = "Nothing else on the line: `help`. Or tap the chip **help**, then ⏎.",
                solution = "help", solutionWhy = "`help` is the game's own command: it lists all the help there is.",
                fact = "That list works at every prompt in this game. Keep it in mind.",
                how = "the game command help was typed",
            ),
            Task(
                id = "t0_2", title = "Say hello",
                chunks = listOf("Many commands want one more thing: **what** to work on. That extra part is an **argument**. A space goes between the two.\n\n`echo` repeats whatever you write after it. Make your phone say hello:\n\n      `echo hello`"),
                command = "echo hello",
                parts = listOf("echo" to "the command: say this", "hello" to "the argument: what to say"),
                chips = listOf("echo", "hello"),
                prep = toPlayground,
                check = { a, fb ->
                    if (a.ran("echo") && a.status == 0 && a.output.isNotBlank()) true
                    else {
                        if (a.ran("hello")) fb.say("So close. The command comes first: `echo`, then a space, then what it should say.")
                        if (a.line.contains("echohello")) fb.say("Put a space between `echo` and `hello`.")
                        false
                    }
                },
                hint1 = "Type the four letters `echo`, a space, the word `hello`, then Enter.",
                hint2 = "Spelling matters: e-c-h-o. Small letters, too.",
                solution = "echo hello", solutionWhy = "`echo` is the command, `hello` its argument: what to say.",
                fact = "Your phone just said hello. Polite, isn't it?",
                how = "echo ran and printed something",
            ),
            Task(
                id = "t0_3", title = "Where am I?",
                chunks = listOf("Your files live in **folders**. The terminal is always \"standing\" in one folder. Which one? Ask it:\n\n      `pwd`      (print working directory: \"where am I?\")"),
                command = "pwd", chips = listOf("pwd"),
                prep = toPlayground,
                check = { a, _ -> a.ran("pwd") && a.status == 0 },
                hint1 = "Three letters: p, w, d.",
                hint2 = "Just `pwd`, then Enter.",
                solution = "pwd", solutionWhy = "`pwd` prints the folder the terminal stands in.",
                fact = "That long name is your app's own room on the phone: `/data/user/0/` and then this app's name. The `/` separates folders.",
                how = "pwd ran without an error",
            ),
            Task(
                id = "t0_4", title = "Look around",
                chunks = listOf("What is in this folder? List it:\n\n      `ls`      (list)"),
                command = "ls", chips = listOf("ls"),
                prep = toPlayground,
                check = { a, fb ->
                    if (a.ran("ls") && a.outHas("timeline.txt")) true
                    else { if (a.ran("ls")) fb.say("That listed another folder. Try `ls` on its own."); false }
                },
                hint1 = "Two letters: l and s, small.",
                hint2 = "Just `ls`, then Enter.",
                solution = "ls", solutionWhy = "`ls` lists what is in the current folder.",
                fact = "`timeline.txt` is a text file: a short history of computers, one event per line.",
                how = "ls ran and its output contains timeline.txt",
            ),
            Task(
                id = "t0_5", title = "The same folder, with your finger",
                chunks = listOf(
                    "On a phone you usually find files by tapping, in the **Files** app. Let's try that for `timeline.txt`.",
                    "Type `files`: the game opens your phone's file picker. Look around for `timeline.txt`, then come back with the Back button.\n\n      `files`      (a game command: opens the file picker)",
                ),
                command = "files", chips = listOf("files"), gameCommand = "files",
                prep = toPlayground,
                check = { _, fb -> fb.say("Type `files` to open the file picker, then come back."); false },
                hint1 = "Five letters: f, i, l, e, s. Then Enter.",
                hint2 = "Type `files`, look around in the picker, press Back.",
                solution = "files", solutionWhy = "`files` is the game's own command: it opens Android's file picker.",
                fact = "You didn't find it, did you? No other app can see your app's room, not even Files. That is Android's **sandbox**: every app has a locked room. You are inside one.",
                how = "the file picker was opened and closed",
            ),
            Task(
                id = "t0_6", title = "Read it in the terminal",
                chunks = listOf("In here you can read it. `cat` shows what is in a file:\n\n      `cat timeline.txt`\n\nThe dot belongs to the name: `timeline.txt`."),
                command = "cat timeline.txt",
                parts = listOf("cat" to "the command: show a file", "timeline.txt" to "the argument: which file"),
                chips = listOf("cat", "timeline.txt"),
                prep = toPlayground,
                check = { a, fb ->
                    if (a.status == 0 && a.outHas("ENIAC") && a.outHas("AI agents")) true
                    else {
                        if (a.ran("cat") && a.status != 0) fb.say("The name must be exact: `timeline.txt`, small letters, with the dot.")
                        false
                    }
                },
                hint1 = "`cat`, a space, then the file's name.",
                hint2 = "Tab helps: type `cat ti` and tap **Tab**: the terminal finishes the name.",
                solution = "cat timeline.txt", solutionWhy = "`cat` shows the content of the file named after it.",
                fact = "\"cat\" is short for \"concatenate\": it can glue files together, too.",
                how = "the output shows the whole timeline",
            ),
            Task(
                id = "t0_7", title = "Search it",
                chunks = listOf("A long file? Let the phone search it. `grep` shows only the lines with a word in them:\n\n      `grep Linux timeline.txt`"),
                command = "grep Linux timeline.txt",
                parts = listOf("grep" to "the command: find lines", "Linux" to "what to look for", "timeline.txt" to "where to look"),
                chips = listOf("grep", "Linux", "timeline.txt"),
                prep = toPlayground,
                check = { a, fb ->
                    if (a.ran("grep") && a.status == 0 && a.outHas("Linux")) true
                    else {
                        if (a.ran("grep") && a.status == 1) fb.say("Nothing found. `grep` cares about capitals: `Linux` with a big L.")
                        false
                    }
                },
                hint1 = "Three parts: `grep`, the word `Linux`, the file `timeline.txt`. Spaces between them.",
                hint2 = "Capitals matter: `Linux`, not `linux`.",
                solution = "grep Linux timeline.txt", solutionWhy = "`grep` prints the lines of `timeline.txt` that contain `Linux`.",
                fact = "One of those lines is about your phone: since 2008 there is a Linux kernel in every Android phone. Including this one.",
                how = "grep ran and found lines with Linux",
            ),
            Task(
                id = "t0_8", title = "Your own search",
                chunks = listOf("Now pick your own word. Try `Android`, or `Apple`, or a year:\n\n      `grep Android timeline.txt`"),
                command = "grep Android timeline.txt",
                chips = listOf("grep", "Android", "timeline.txt"),
                prep = toPlayground,
                check = { a, fb ->
                    val word = a.words.drop(1).firstOrNull()
                    when {
                        !a.ran("grep") -> false
                        word == null || word == "Linux" -> { fb.say("Pick another word than `Linux` this time."); false }
                        a.status == 0 -> true
                        a.status == 1 -> { fb.say("No line has `$word` in it. Capitals matter. Try `Android`."); false }
                        else -> false
                    }
                },
                hint1 = "Same as before, with another word: `grep` WORD `timeline.txt`.",
                hint2 = "For example `grep Android timeline.txt`.",
                solution = "grep Android timeline.txt", solutionWhy = "`grep` looks for `Android` in `timeline.txt`.",
                fact = "You just searched a file with one line of text. No app, no menu, no ads.",
                how = "grep ran with a word other than Linux and found it",
            ),
        ),
        outro = listOf(
            "You talked to your phone in text, looked for a file with your finger, read it and searched it. In the terminal. On day one.",
            "**Why does this matter today?** Buttons are made for human eyes and fingers. An AI like ChatGPT or Claude reads text and writes text. The terminal is text in, text out. That is why AI agents work in terminals: they type commands like yours.",
            "So if you can read commands, you can follow what an AI does, and stop it before it does something silly. One habit: **read first, then run.** Never run a command you don't understand, whoever suggests it.",
            "Stuck at any point? `hint` gives a nudge, `help` shows everything. Every command you learn lands in your cheatsheet: type `cheat`.",
        ),
        explore = listOf(
            "On your phone: Settings > About phone > Android version. The **Kernel version** listed there is the Linux you just talked to.",
        ),
    )
}
