package io.github.alexpp90.daemonsapprentice.game

import java.io.File

/** Level 8, "The Editor and the Script": write a file, run it, and meet Android's rule against running it directly. */
object Level8 {

    const val LINE = "----------"
    val note = """
        A note from Pid1:

        Write your name on the last line of this file, below the dashes.
        Then tap Done (with a keyboard: press Esc). That saves the file
        and takes you back to the terminal.
        $LINE
    """.trimIndent() + "\n"

    val hello = """
        # hello.sh: your first script. Lines that start with # are notes
        # for people; the shell skips them.
        # Add one line at the end, without the #:
        #     echo Hello from my first script
        # Then tap Done (or press Esc).
    """.trimIndent() + "\n"

    private fun dir(d: GameDirs) = File(d.playground, "scripts")
    private fun noteOf(a: Attempt) = File(a.pg, "scripts/note.txt")
    private fun helloOf(a: Attempt) = File(a.pg, "scripts/hello.sh")

    /** What the player wrote below the dashes. */
    fun written(f: File): String = f.takeIf { it.isFile }?.readText()?.substringAfter(LINE, "")?.trim() ?: ""
    /** The script's commands: its lines that are not notes. */
    fun commands(f: File): List<String> =
        f.takeIf { it.isFile }?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") } ?: emptyList()

    private fun at(step: Int): suspend TaskContext.() -> Unit = {
        val d = dir(dirs)
        d.mkdirs()
        val n = File(d, "note.txt"); val h = File(d, "hello.sh")
        if (step <= 1 || !n.isFile) n.writeText(note)
        if (step <= 3 || !h.isFile) h.writeText(hello)
        // A player who skipped a step gets what that step would have made.
        if (step >= 2 && written(n).isEmpty()) n.appendText("Apprentice\n")
        if (step >= 4 && commands(h).none { it.startsWith("echo") }) h.appendText("echo Hello from my first script\n")
        if (step <= 5) h.setExecutable(false, false)
        if (step >= 6) h.setExecutable(true, true)
        goTo(d)
    }

    val level = Level(
        number = 8,
        title = "The Editor and the Script",
        intro = listOf(
            "${Pid1.RUNNING}  So far you only made files with `>`. Today you write them properly, and then you make one that runs: a **script**.",
            "A computer has editors like `nano` in the terminal. The phone has none, so this game brings its own. We work in `scripts`.",
        ),
        tasks = listOf(
            Task(
                id = "t8_1", title = "The editor",
                card = "`edit` is the game's command (like `help`): it opens a file in the game's editor.",
                chunks = listOf("Open the note. What to do is written inside it.\n\n      `edit note.txt`"),
                command = "edit note.txt", chips = listOf("edit", "note.txt"),
                drive = "edit-append:Apprentice",
                prep = at(1),
                check = { a, fb ->
                    if (a.gameCommand == "edit" && written(noteOf(a)).isNotEmpty()) true
                    else { if (a.gameCommand == "edit") fb.say("Nothing below the dashes yet. `edit note.txt` again, write your name on the last line, then Done."); false }
                },
                hint1 = "`edit` and the file's name.", hint2 = "`edit note.txt`, write your name at the end, then Done.",
                solution = "edit note.txt", solutionWhy = "The editor opened the note; you added a line and saved it with Done.",
                fact = "Done saved the file in your app's room. The terminal and the editor work on the same files.",
                how = "the note has something below the dashes",
            ),
            Task(
                id = "t8_2", title = "Did it save?",
                chunks = listOf("Check from the terminal:\n\n      `cat note.txt`"),
                command = "cat note.txt", chips = listOf("cat", "note.txt"),
                prep = at(2),
                check = { a, _ -> a.ran("cat") && a.outHas(LINE) && written(noteOf(a)).let { it.isNotEmpty() && a.outHas(it.lines().first()) } },
                hint1 = "`cat` and the file.", hint2 = "`cat note.txt`",
                solution = "cat note.txt", solutionWhy = "Your name is at the end: the editor saved it.",
                fact = "Write, then check. You know that one from Level 4.",
                how = "cat shows what was written",
            ),
            Task(
                id = "t8_3", title = "Your first script",
                card = "A **script** is a text file full of commands. The shell reads it and runs them, one line after another.",
                chunks = listOf("Open it; the notes inside say which line to add:\n\n      `edit hello.sh`"),
                command = "edit hello.sh", chips = listOf("edit", "hello.sh"),
                drive = "edit-append:echo Hello from my first script",
                prep = at(3),
                check = { a, fb ->
                    if (a.gameCommand == "edit" && commands(helloOf(a)).any { it.startsWith("echo") }) true
                    else { if (a.gameCommand == "edit") fb.say("No command in it yet. Add a line that starts with `echo`, without a # in front, then Done."); false }
                },
                hint1 = "`edit hello.sh`, then add a line like `echo Hello from my first script` at the end.",
                hint2 = "`edit hello.sh`; at the end add: `echo Hello from my first script`; then Done.",
                solution = "edit hello.sh", solutionWhy = "The script now has one command: an echo.",
                fact = "`.sh` at the end tells people it is a shell script. The shell itself does not care about the name.",
                how = "hello.sh has an echo command",
            ),
            Task(
                id = "t8_4", title = "Run it",
                chunks = listOf("Hand the script to the shell:\n\n      `sh hello.sh`"),
                command = "sh hello.sh", chips = listOf("sh", "hello.sh"),
                prep = at(4),
                check = { a, _ -> a.ran("sh") && a.status == 0 && a.output.isNotBlank() },
                hint1 = "`sh` (the shell) and the script.", hint2 = "`sh hello.sh`",
                solution = "sh hello.sh", solutionWhy = "`sh` read the file and ran its echo.",
                fact = "Your first program. Ten lines or ten thousand: the idea stays the same.",
                how = "the script ran and printed something",
            ),
            Task(
                id = "t8_5", title = "Make it a program",
                card = "On a computer you make a script a program of its own with the **x** right (execute). Then it runs by its name, without `sh`.",
                chunks = listOf("Give it the x right:\n\n      `chmod +x hello.sh`"),
                command = "chmod +x hello.sh", chips = listOf("chmod", "+x", "hello.sh"),
                prep = at(5),
                check = { a, _ -> a.ran("chmod") && helloOf(a).canExecute() },
                hint1 = "`chmod`, `+x`, the script.", hint2 = "`chmod +x hello.sh`",
                solution = "chmod +x hello.sh", solutionWhy = "`+x` adds the right to execute.",
                fact = "`ls -l hello.sh` would now show an x in its rights: `-rwx...`.",
                how = "hello.sh may be executed",
            ),
            Task(
                id = "t8_6", title = "Run it by its name",
                card = "`./` means: the file right here, in this folder.",
                chunks = listOf("On a computer this runs it. On your phone? **A refusal is the right result.**\n\n      `./hello.sh`"),
                command = "./hello.sh", chips = listOf("./hello.sh"), keys = listOf(".", "/"),
                drive = "device",
                prep = at(6),
                check = { a, fb ->
                    if (a.commands.firstOrNull() == "./hello.sh" && a.status != 0 && a.output.contains("denied", ignoreCase = true)) true
                    else { if (a.commands.firstOrNull() == "./hello.sh" && a.status == 0) fb.say("It ran. Then this phone allows it, which Android 10 and newer do not. Type `skip`."); false }
                },
                hint1 = "A dot, a slash, the name: no space.", hint2 = "`./hello.sh`",
                solution = "./hello.sh", solutionWhy = "Refused, although the file has the x right.",
                fact = "Since Android 10 an app may not start a program from a file it wrote itself. It may only run programs that came with the phone or with the app. This one rule stops a lot of harmful apps: they cannot download new code and run it.",
                how = "./hello.sh was refused",
            ),
            Task(
                id = "t8_7", title = "The way around",
                chunks = listOf("And yet, this works again:\n\n      `sh hello.sh`"),
                command = "sh hello.sh", chips = listOf("sh", "hello.sh"),
                prep = at(7),
                check = { a, _ -> a.ran("sh") && a.status == 0 && a.output.isNotBlank() },
                hint1 = "Like in the fourth task.", hint2 = "`sh hello.sh`",
                solution = "sh hello.sh", solutionWhy = "The program that runs is `sh`, which came with the phone. Your script is only read, like a text.",
                fact = "So scripts still work on a phone, through `sh`. A program the app downloaded would not: there is no `sh` that could read it for you.",
                how = "sh hello.sh ran",
            ),
        ),
        outro = listOf(
            "You wrote files with an editor, wrote a script and ran it. And you met a rule a computer does not have: no running what you wrote yourself.",
            "Next: the building blocks that make scripts powerful. Loops, and the shell doing a hundred things for you.",
        ),
        explore = listOf(
            "Settings > Apps > Special app access > Install unknown apps: the other door for new code. Android asks you, app by app.",
        ),
    )
}
