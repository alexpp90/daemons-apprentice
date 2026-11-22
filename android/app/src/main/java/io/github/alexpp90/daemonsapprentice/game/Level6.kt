package io.github.alexpp90.daemonsapprentice.game

import java.io.File

/** Level 6, "Processes and Variables": what runs (an intruder among it), and what the shell remembers. */
object Level6 {

    /** A pretend coin miner. It only sleeps, and gives up by itself after ten minutes. */
    val miner = """
        # miner.sh: pretends to mine coins on your phone. (It only sleeps.)
        i=0
        # (sleep, and wait for it: that way it stops at once when told to)
        while [ ${'$'}i -lt 600 ]; do sleep 1 & wait ${'$'}!; i=${'$'}((i+1)); done
    """.trimIndent() + "\n"

    private fun dir(t: TaskContext) = File(t.dirs.playground, "processes")

    fun minerPid(dirs: GameDirs): Int? = dirs.minerPid.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()

    /** The miner runs: its /proc entry is there, it is not a zombie, and it is really the miner. */
    fun minerAlive(dirs: GameDirs): Boolean {
        val pid = minerPid(dirs) ?: return false
        val stat = File("/proc/$pid/stat").takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() } ?: return false
        if (stat.substringAfterLast(')').trim().startsWith("Z")) return false
        val cmd = runCatching { File("/proc/$pid/cmdline").readText() }.getOrDefault("")
        return cmd.contains("miner.sh")
    }

    private fun at(miner: Boolean): suspend TaskContext.() -> Unit = {
        val d = dir(this)
        File(d, "miner.sh").ensureText(Level6.miner)
        goTo(d)
        val alive = minerAlive(dirs)
        if (miner && !alive) {
            dirs.game.mkdirs()
            // In a subshell: no job of the player's shell, so no "Terminated" notice later.
            shell.runQuiet("(sh miner.sh > /dev/null 2>&1 & echo \$! > '${dirs.minerPid.absolutePath}')")
        } else if (!miner && alive) shell.runQuiet("kill ${minerPid(dirs)} 2> /dev/null")
    }

    /** Keeps `$?` of the player's last command: no `cd` before the task, unless the folder changed. */
    private val quiet: suspend TaskContext.() -> Unit = { goTo(dir(this)) }

    val level = Level(
        number = 6,
        title = "Processes and Variables",
        intro = listOf(
            "${Pid1.INSPECTING}  Every running program is a **process**. I am one, your shell is one, every command you type becomes one for a moment.",
            "Today you look at the processes, find one that should not be there, and stop it. Then: what the shell remembers for you.",
        ),
        tasks = listOf(
            Task(
                id = "t6_1", title = "What is running?",
                chunks = listOf("`ps` lists processes:\n\n      `ps`"),
                command = "ps", chips = listOf("ps"),
                prep = at(false),
                check = { a, _ -> a.ran("ps") && a.outHas("PID") },
                hint1 = "Two letters: p, s.", hint2 = "`ps`",
                solution = "ps", solutionWhy = "`ps` lists the processes of this terminal: your shell, and ps itself.",
                fact = "Only yours. On a computer, `ps -e` shows hundreds. On a phone an app sees only its own processes: the others are hidden from it.",
                how = "ps printed its list",
            ),
            Task(
                id = "t6_2", title = "Who started whom?",
                card = "Every process has a number, its **PID**, and a parent that started it, the **PPID**.",
                chunks = listOf("Ask for exactly these columns: PID, parent and the full command.\n\n      `ps -o pid,ppid,args`"),
                command = "ps -o pid,ppid,args", chips = listOf("ps", "-o", "pid,ppid,args"), keys = listOf("-"),
                prep = at(false),
                check = { a, _ -> a.ran("ps") && a.outHas("PPID") },
                hint1 = "`ps`, `-o`, then the three names with commas and no spaces.", hint2 = "`ps -o pid,ppid,args`",
                solution = "ps -o pid,ppid,args", solutionWhy = "`-o` picks the columns: PID, parent PID, command.",
                fact = "The `ps` line's PPID is your shell's PID: the shell started it. And the shell's parent? This app. Level 11 follows the family further up.",
                how = "ps printed a PPID column",
            ),
            Task(
                id = "t6_3", title = "An intruder",
                card = "${Pid1.STOPPED}  Wait. Something is running that neither of us started. It makes your phone warm.",
                chunks = listOf("Look at the commands:\n\n      `ps -o pid,args`"),
                command = "ps -o pid,args", chips = listOf("ps", "-o", "pid,args"), keys = listOf("-"),
                prep = at(true),
                check = { a, _ -> a.ran("ps") && a.outHas("miner.sh") },
                hint1 = "Like before, but only `pid,args`.", hint2 = "`ps -o pid,args`",
                solution = "ps -o pid,args", solutionWhy = "The ARGS column shows each process's command: one of them is `sh miner.sh`.",
                fact = "A coin miner: it uses someone else's processor to earn money. Ours only sleeps. Real ones hide in apps from strange sources.",
                how = "ps shows the miner",
            ),
            Task(
                id = "t6_4", title = "Stop it",
                card = "`kill` sends a process a signal: by default, \"please stop\". It needs the PID: the number in front of `sh miner.sh`.",
                chunks = listOf("Stop the miner with its PID (yours is a different number than anybody else's):\n\n      `kill <PID>`"),
                command = "kill <PID>", chips = listOf("kill"),
                solve = { d -> "kill " + (minerPid(d) ?: "PID") },
                prep = at(true),
                check = { a, fb ->
                    if (a.ran("kill") && !minerAlive(GameDirs(a.home.parentFile!!))) true
                    else { if (a.ran("kill")) fb.say("The miner still runs. Its PID is the number in front of `sh miner.sh` in `ps -o pid,args`."); false }
                },
                hint1 = "`ps -o pid,args` shows the number. Then `kill` and that number.", hint2 = "`kill` followed by the miner's PID, for example `kill 4321`.",
                solution = "kill <PID>", solutionWhy = "`kill` with the miner's PID asks it to stop, and it does.",
                fact = "`kill` is a friendly name for a hard job. A process that ignores \"please\" gets `kill -9`: no question asked.",
                how = "the miner's process is gone",
            ),
            Task(
                id = "t6_5", title = "Check",
                chunks = listOf("Never trust, check:\n\n      `ps -o pid,args`"),
                command = "ps -o pid,args", chips = listOf("ps", "-o", "pid,args"), keys = listOf("-"),
                prep = at(false),
                check = { a, _ -> a.ran("ps") && a.outHas("PID") && !a.outHas("miner.sh") },
                hint1 = "The ps from before.", hint2 = "`ps -o pid,args`",
                solution = "ps -o pid,args", solutionWhy = "The list again: no miner left.",
                fact = "Android does this kind of thing all day: it stops processes nobody needs, to save battery.",
                how = "ps no longer shows the miner",
            ),
            Task(
                id = "t6_6", title = "What the shell knows",
                card = "The shell keeps **variables**: names with a value. Some are there from the start, the **environment**.",
                chunks = listOf("See them all:\n\n      `env`"),
                command = "env", chips = listOf("env"),
                prep = at(false),
                check = { a, _ -> a.ran("env") && a.outHas("HOME=") },
                hint1 = "Three letters: e, n, v.", hint2 = "`env`",
                solution = "env", solutionWhy = "`env` lists the environment: one NAME=value per line.",
                fact = "`HOME` is your home folder, inside the app's room. `ANDROID_DATA` and friends tell programs where Android keeps things.",
                how = "env printed HOME=",
            ),
            Task(
                id = "t6_7", title = "Your own variable",
                chunks = listOf("Make one. No spaces around the `=`:\n\n      `planet=Mars`"),
                command = "planet=Mars", chips = listOf("planet=Mars"), keys = listOf("="),
                prep = quiet,
                check = { a, fb ->
                    if (Regex("""^planet=\S+$""").matches(a.line.trim()) && a.status == 0) true
                    else { if (a.line.contains("planet") && a.line.contains(" = ")) fb.say("No spaces around `=`: `planet=Mars`. With spaces, the shell looks for a command called planet."); false }
                },
                hint1 = "The name, `=`, the value, all stuck together.", hint2 = "`planet=Mars`",
                solution = "planet=Mars", solutionWhy = "The shell now remembers: planet is Mars. It prints nothing.",
                fact = "Silence means it worked. The variable lives in this shell until the app closes.",
                how = "the line set planet",
            ),
            Task(
                id = "t6_8", title = "Use it",
                chunks = listOf("A `\$` in front of the name hands out its value:\n\n      `echo \$planet`"),
                command = "echo \$planet", chips = listOf("echo", "\$planet"), keys = listOf("$"),
                prep = quiet,
                check = { a, fb ->
                    if (a.ran("echo") && "\$planet" in a.words && a.output.isNotBlank()) true
                    else { if (a.ran("echo") && "\$planet" in a.words) fb.say("Empty: this shell does not know planet (it starts fresh with the app). Set it again: `planet=Mars`, then this."); false }
                },
                hint1 = "`echo`, then the name with `\$` in front.", hint2 = "`echo \$planet`",
                solution = "echo \$planet", solutionWhy = "The shell replaced `\$planet` with Mars before echo even started.",
                fact = "That is the shell's job, not echo's: echo only ever saw the word Mars.",
                how = "echo printed the variable's value",
            ),
            Task(
                id = "t6_9", title = "Where commands live",
                card = "When you type `ls`, how does the shell find the program? It looks in the folders listed in the variable `PATH`.",
                chunks = listOf("Show it:\n\n      `echo \$PATH`"),
                command = "echo \$PATH", chips = listOf("echo", "\$PATH"), keys = listOf("$"),
                prep = quiet,
                check = { a, _ -> a.ran("echo") && a.outHas("/bin") },
                hint1 = "Like before, `PATH` in capitals.", hint2 = "`echo \$PATH`",
                solution = "echo \$PATH", solutionWhy = "PATH is a list of folders, separated by colons. The shell searches them in order.",
                fact = "`/system/bin` is Android's: everything in it comes with the phone and nobody may change it. A computer has `/usr/bin` instead.",
                how = "PATH contains a bin folder",
            ),
            Task(
                id = "t6_10", title = "Which one?",
                chunks = listOf("Where exactly is `ls`?\n\n      `which ls`"),
                command = "which ls", chips = listOf("which", "ls"),
                prep = quiet,
                check = { a, _ -> a.ran("which") && a.outHas("/ls") },
                hint1 = "`which` and the command you want to find.", hint2 = "`which ls`",
                solution = "which ls", solutionWhy = "`which` searches PATH the way the shell does and prints the first match.",
                fact = "On the phone, `/system/bin/ls` is a link to one program, `toybox`, that plays almost all the commands of this game.",
                how = "which printed the path of ls",
            ),
            Task(
                id = "t6_11", title = "A command that fails",
                chunks = listOf("This folder does not exist, so `ls` fails. **The error is expected.**\n\n      `ls /nowhere`"),
                command = "ls /nowhere", chips = listOf("ls", "/nowhere"), keys = listOf("/"),
                prep = quiet,
                check = { a, _ -> a.ran("ls") && "/nowhere" in a.words && a.status != 0 },
                hint1 = "`ls` and `/nowhere`.", hint2 = "`ls /nowhere`",
                solution = "ls /nowhere", solutionWhy = "There is no /nowhere, so ls reports an error.",
                fact = "Besides the message, every command leaves a number behind: its **exit status**.",
                how = "ls /nowhere failed",
            ),
            Task(
                id = "t6_12", title = "How did it go?",
                chunks = listOf("`\$?` holds the exit status of the last command:\n\n      `echo \$?`"),
                command = "echo \$?", chips = listOf("echo", "\$?"), keys = listOf("$"),
                prep = quiet,
                check = { a, fb ->
                    val n = a.lines().lastOrNull()?.toIntOrNull()
                    if (a.ran("echo") && "\$?" in a.words && n != null && n != 0) true
                    else { if (n == 0) fb.say("0: the command before this one worked. Run `ls /nowhere` first, then `echo \$?` right after it."); false }
                },
                hint1 = "`echo`, a space, `$?`.", hint2 = "`echo \$?`",
                solution = "echo \$?", solutionWhy = "Not 0: the ls before failed. 0 means success, anything else is an error code.",
                fact = "The game reads this number after every command you type. That is how it knew, all along, whether your command worked.",
                how = "echo printed a non-zero status",
            ),
        ),
        outro = listOf(
            "Processes with PIDs and parents; an intruder found and stopped; variables, PATH and exit codes.",
            "You saw only your own processes. Next level: why. On a phone, every app is a user of its own.",
        ),
        explore = listOf(
            "Settings > Apps > (an app) > Force stop: that is kill, with a button.",
            "Settings > Developer options > Running services (if you have turned developer options on) shows processes, too.",
        ),
    )
}
