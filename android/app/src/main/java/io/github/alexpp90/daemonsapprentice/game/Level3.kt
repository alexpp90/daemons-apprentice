package io.github.alexpp90.daemonsapprentice.game

import java.io.File

/** Level 3, "Making Things": create, copy, move and delete, in the playground's workshop. */
object Level3 {

    private fun ws(t: TaskContext) = File(t.dirs.playground, "workshop")
    private fun wsOf(a: Attempt) = File(a.pg, "workshop")

    /** Each task starts from the state the earlier tasks leave behind (a cold start rebuilds it). */
    private fun state(step: Int): suspend TaskContext.() -> Unit = {
        val w = ws(this)
        w.mkdirs()
        val notes = File(w, "notes")
        val todo = File(notes, "todo.txt")
        if (step <= 1) notes.deleteRecursively() else notes.mkdirs()
        if (step == 2) todo.delete()
        if (step == 3 && !todo.exists()) todo.writeText("")
        if (step in 4..6) todo.ensureText("buy milk\n")
        if (step <= 5) File(notes, "backup.txt").delete()
        if (step == 6) File(notes, "backup.txt").ensureText("buy milk\n")
        if (step <= 6) File(notes, "old.txt").delete()
        if (step in 7..8) listOf("a.tmp", "b.tmp", "c.tmp").forEach { File(w, it).ensureText("temporary\n") }
        if (step in 7..8) File(w, "keep.txt").ensureText("Keep me!\n")
        val week = File(w, "week")
        if (step <= 8) week.deleteRecursively()
        if (step == 9) { week.deleteRecursively(); week.mkdirs() }
        if (step == 10 || step == 11) listOf("mon", "tue", "wed").forEach { File(week, it).mkdirs() }
        goTo(w)
    }

    val level = Level(
        number = 3,
        title = "Making Things",
        intro = listOf(
            "${Pid1.RUNNING}  Looking is nice, making is better. Today you create folders and files, copy, move, and delete them.",
            "We work in a folder called `workshop` in your playground. Nothing outside it gets touched.",
        ),
        tasks = listOf(
            Task(
                id = "t3_1", title = "A new folder",
                chunks = listOf("`mkdir` makes a folder (make directory). Make one for your notes:\n\n      `mkdir notes`"),
                command = "mkdir notes", chips = listOf("mkdir", "notes"),
                prep = state(1),
                check = { a, _ -> File(wsOf(a), "notes").isDirectory },
                hint1 = "`mkdir`, a space, `notes`.", hint2 = "`mkdir notes`",
                solution = "mkdir notes", solutionWhy = "`mkdir notes` creates a folder named notes.",
                fact = "`ls` would show it now. In the terminal, a new folder costs one word.",
                how = "workshop/notes is a folder",
            ),
            Task(
                id = "t3_2", title = "An empty file",
                chunks = listOf("`touch` makes an empty file. Put one into the new folder:\n\n      `touch notes/todo.txt`"),
                command = "touch notes/todo.txt", chips = listOf("touch", "notes/todo.txt"), keys = listOf("/"),
                prep = state(2),
                check = { a, _ -> File(wsOf(a), "notes/todo.txt").isFile },
                hint1 = "`touch`, then the path `notes/todo.txt`.", hint2 = "`touch notes/todo.txt`",
                solution = "touch notes/todo.txt", solutionWhy = "`touch` creates the file `todo.txt` inside `notes`.",
                fact = "`touch` really sets a file's time. If there is no file, it makes one: that's why people use it.",
                how = "workshop/notes/todo.txt exists",
            ),
            Task(
                id = "t3_3", title = "Write into a file",
                card = "`>` **sends** what a command prints into a file instead of the screen. Careful: it replaces what was in the file.",
                chunks = listOf("Write your first note:\n\n      `echo buy milk > notes/todo.txt`"),
                command = "echo buy milk > notes/todo.txt",
                parts = listOf("echo buy milk" to "print \"buy milk\"", ">" to "send it into a file", "notes/todo.txt" to "this file"),
                chips = listOf("echo", "buy", "milk", ">", "notes/todo.txt"), keys = listOf(">"),
                prep = state(3),
                check = { a, _ -> File(wsOf(a), "notes/todo.txt").let { it.isFile && it.readText().contains("buy milk") } },
                hint1 = "`echo buy milk`, then `>`, then the file.", hint2 = "`echo buy milk > notes/todo.txt`",
                solution = "echo buy milk > notes/todo.txt", solutionWhy = "`echo` prints the words, `>` writes them into the file.",
                fact = "Nothing appeared on the screen: the words went into the file.",
                how = "todo.txt contains buy milk",
            ),
            Task(
                id = "t3_4", title = "Did it land?",
                chunks = listOf("Check it:\n\n      `cat notes/todo.txt`"),
                command = "cat notes/todo.txt", chips = listOf("cat", "notes/todo.txt"),
                prep = state(4),
                check = { a, _ -> a.outHas("buy milk") },
                hint1 = "`cat` and the path.", hint2 = "`cat notes/todo.txt`",
                solution = "cat notes/todo.txt", solutionWhy = "`cat` shows the file you just wrote.",
                fact = "Write, then check. AI agents should work like that, too.",
                how = "the output shows buy milk",
            ),
            Task(
                id = "t3_5", title = "Copy",
                chunks = listOf("`cp` copies: first what, then where to.\n\n      `cp notes/todo.txt notes/backup.txt`"),
                command = "cp notes/todo.txt notes/backup.txt",
                parts = listOf("cp" to "copy", "notes/todo.txt" to "from", "notes/backup.txt" to "to"),
                chips = listOf("cp", "notes/todo.txt", "notes/backup.txt"),
                prep = state(5),
                check = { a, _ -> File(wsOf(a), "notes/backup.txt").let { it.isFile && it.readText().contains("buy milk") } },
                hint1 = "`cp`, the file, the new name.", hint2 = "`cp notes/todo.txt notes/backup.txt`",
                solution = "cp notes/todo.txt notes/backup.txt", solutionWhy = "`cp` makes `backup.txt` with the same content.",
                fact = "If backup.txt had existed, `cp` would have replaced it without asking.",
                how = "backup.txt exists with the same text",
            ),
            Task(
                id = "t3_6", title = "Move",
                chunks = listOf("`mv` moves a file, or renames it. Rename the backup:\n\n      `mv notes/backup.txt notes/old.txt`"),
                command = "mv notes/backup.txt notes/old.txt",
                chips = listOf("mv", "notes/backup.txt", "notes/old.txt"),
                prep = state(6),
                check = { a, _ -> File(wsOf(a), "notes/old.txt").isFile && !File(wsOf(a), "notes/backup.txt").exists() },
                hint1 = "`mv`, the old name, the new name.", hint2 = "`mv notes/backup.txt notes/old.txt`",
                solution = "mv notes/backup.txt notes/old.txt", solutionWhy = "`mv` gives the file a new name.",
                fact = "Renaming and moving are the same thing to the computer: the file gets a new path.",
                how = "old.txt exists and backup.txt is gone",
            ),
            Task(
                id = "t3_7", title = "Look before you delete",
                card = "Next we delete. There is **no trash can** in the terminal: deleted is gone. So first look at what would be hit.",
                chunks = listOf("`*` matches any name part. `*.tmp` means every name ending in .tmp. Look:\n\n      `ls *.tmp`"),
                command = "ls *.tmp", chips = listOf("ls", "*.tmp"), keys = listOf("*"),
                prep = state(7),
                check = { a, _ -> a.ran("ls") && a.outHas("a.tmp") && !a.outHas("keep.txt") },
                hint1 = "`ls`, then `*.tmp`. The star is on the key bar.", hint2 = "`ls *.tmp`",
                solution = "ls *.tmp", solutionWhy = "`*.tmp` matches a.tmp, b.tmp and c.tmp, nothing else.",
                fact = "keep.txt is not in the list, so a delete with *.tmp leaves it alone.",
                how = "ls listed the .tmp files and not keep.txt",
            ),
            Task(
                id = "t3_8", title = "Clean up",
                chunks = listOf("Same pattern, now with `rm` (remove):\n\n      `rm *.tmp`"),
                command = "rm *.tmp", chips = listOf("rm", "*.tmp"), keys = listOf("*"),
                prep = state(8),
                check = { a, fb ->
                    val w = wsOf(a)
                    val left = w.listFiles()?.any { it.name.endsWith(".tmp") } ?: false
                    if (!File(w, "keep.txt").exists()) fb.say("keep.txt is gone, too: there is no trash can. That's why we look first.")
                    !left
                },
                hint1 = "`rm`, then the same `*.tmp`.", hint2 = "`rm *.tmp`",
                solution = "rm *.tmp", solutionWhy = "`rm` deletes every name `*.tmp` matches.",
                fact = "`ls` first, `rm` second: the habit that saves you one day.",
                how = "no .tmp file left, keep.txt still there",
            ),
            Task(
                id = "t3_9", title = "Three at once",
                card = "Braces make a list: `week/{mon,tue,wed}` becomes week/mon, week/tue and week/wed.",
                chunks = listOf("Make three folders with one command:\n\n      `mkdir week/{mon,tue,wed}`"),
                command = "mkdir week/{mon,tue,wed}",
                chips = listOf("mkdir", "week/{mon,tue,wed}"), keys = listOf("{", "}", ","),
                prep = state(9),
                check = { a, _ -> listOf("mon", "tue", "wed").all { File(wsOf(a), "week/$it").isDirectory } },
                hint1 = "`mkdir week/` then `{mon,tue,wed}`: commas, no spaces.", hint2 = "`mkdir week/{mon,tue,wed}`",
                solution = "mkdir week/{mon,tue,wed}", solutionWhy = "The shell turns the braces into three names, then `mkdir` makes all three.",
                fact = "On a computer, Bash also knows `{1..365}`: 365 folders in one line. Your phone's shell doesn't; Level 9 does it with a loop.",
                how = "week/mon, week/tue and week/wed exist",
            ),
            Task(
                id = "t3_10", title = "See them",
                chunks = listOf("Look into the new folder:\n\n      `ls week`"),
                command = "ls week", chips = listOf("ls", "week"),
                prep = state(10),
                check = { a, _ -> a.ran("ls") && a.outHas("mon") && a.outHas("wed") },
                hint1 = "`ls` and the folder name.", hint2 = "`ls week`",
                solution = "ls week", solutionWhy = "`ls week` lists what is inside week.",
                fact = "`ls` can look into any folder without walking in.",
                how = "ls listed mon and wed",
            ),
            Task(
                id = "t3_11", title = "Undo",
                chunks = listOf("`rm` alone refuses folders. `-r` (recursive) deletes a folder with everything in it:\n\n      `rm -r week`"),
                command = "rm -r week", chips = listOf("rm", "-r", "week"), keys = listOf("-"),
                prep = state(11),
                check = { a, _ -> !File(wsOf(a), "week").exists() },
                hint1 = "`rm`, `-r`, then the folder.", hint2 = "`rm -r week`",
                solution = "rm -r week", solutionWhy = "`-r` deletes the folder and all folders in it.",
                fact = "`rm -r` is the strongest delete you have. Read it twice before you press Enter.",
                how = "week is gone",
            ),
            Task(
                id = "t3_12", title = "Read the help",
                chunks = listOf("What else can `rm` do? Ask it:\n\n      `rm --help`"),
                command = "rm --help", chips = listOf("rm", "--help"), keys = listOf("-"),
                prep = state(12),
                check = { a, _ -> a.ran("rm") && "--help" in a.words && a.output.isNotBlank() },
                hint1 = "`rm --help`: two minus signs.", hint2 = "`rm --help`",
                solution = "rm --help", solutionWhy = "`--help` lists `rm`'s options, like `-r` and `-f`.",
                fact = "`-f` (force) skips every question. You will see `rm -rf` in scripts: now you can read it.",
                how = "rm ran with --help",
            ),
        ),
        outro = listOf(
            "Folders, files, writing with `>`, copying, renaming, a pattern with `*`, three folders with braces, and deleting, carefully.",
            "An AI agent that tidies up your files runs exactly these commands. You can now read every one of them before you say yes.",
        ),
        explore = listOf("In your phone's Files app, long-press a file: Copy, Move, Rename, Delete. The same four things, with taps."),
    )
}
