package io.github.alexpp90.daemonsapprentice.game

import java.io.File

/** Level 11, "Pid1's Family": init, Zygote, your app (a clone), your shell; the parents you cannot see. */
object Level11 {

    /** The app's own parent (Zygote on a phone), read by the app itself: the shell would say the same. */
    fun zygotePid(): String =
        runCatching { File("/proc/self/status").readLines().first { it.startsWith("PPid:") }.substringAfter(':').trim() }.getOrDefault("1")

    private val at: suspend TaskContext.() -> Unit = { goTo(dirs.playground) }

    val level = Level(
        number = 11,
        title = "Pid1's Family",
        intro = listOf(
            "${Pid1.RUNNING}  Time I introduced myself properly. I am **Pid1**: process number 1, the first one the kernel starts. On a phone my real name is `init`. Every other process descends from me.",
            "One of my children is **Zygote**. It starts once, loads all of Android's code, and from then on every app is a copy of it. Yours too. Let's walk up the family tree.",
        ),
        tasks = listOf(
            Task(
                id = "t11_1", title = "Visit me",
                chunks = listOf("My command line is in `/proc/1/cmdline`. Try to read it:\n\n      `cat /proc/1/cmdline`"),
                command = "cat /proc/1/cmdline", chips = listOf("cat", "/proc/1/cmdline"), keys = listOf("/"),
                drive = "device",
                prep = at,
                check = { a, _ -> a.ran("cat") && a.status != 0 },
                hint1 = "`cat` and the path with the 1.", hint2 = "`cat /proc/1/cmdline`",
                solution = "cat /proc/1/cmdline", solutionWhy = "No such file: for an app, I am not there.",
                fact = "${Pid1.SLEEPING}  Told you. On a phone an app sees only its own processes in /proc. I am there, just not for you.",
                how = "cat was refused",
            ),
            Task(
                id = "t11_2", title = "Your shell",
                chunks = listOf("`\$\$` is the PID of the shell itself:\n\n      `echo \$\$`"),
                command = "echo \$\$", chips = listOf("echo", "\$\$"), keys = listOf("$"),
                prep = at,
                check = { a, _ -> a.ran("echo") && a.output.trim().toIntOrNull() != null },
                hint1 = "`echo` and two dollar signs.", hint2 = "`echo \$\$`",
                solution = "echo \$\$", solutionWhy = "The number of the shell you are typing into.",
                fact = "`\$PPID` is its parent's number. Who is that?",
                how = "echo printed a number",
            ),
            Task(
                id = "t11_3", title = "Your shell's parent",
                chunks = listOf("      `ps -o pid,ppid,args`"),
                command = "ps -o pid,ppid,args", chips = listOf("ps", "-o", "pid,ppid,args"), keys = listOf("-"),
                prep = at,
                check = { a, _ -> a.ran("ps") && a.outHas("PPID") },
                hint1 = "The ps from Level 6.", hint2 = "`ps -o pid,ppid,args`",
                solution = "ps -o pid,ppid,args", solutionWhy = "`sh -i` is your shell; its PPID is a process that ps does not list.",
                fact = "ps shows only the processes of this terminal. The parent is the app itself: it started the shell.",
                how = "ps printed the PPID column",
            ),
            Task(
                id = "t11_4", title = "Meet your app",
                chunks = listOf("Its command line:\n\n      `cat /proc/\$PPID/cmdline`"),
                command = "cat /proc/\$PPID/cmdline", chips = listOf("cat", "/proc/\$PPID/cmdline"), keys = listOf("/", "$"),
                drive = "device",
                prep = at,
                check = { a, _ -> a.ran("cat") && a.outHas(AppInfo.pkg) },
                hint1 = "`cat`, then the path with `\$PPID` in it.", hint2 = "`cat /proc/\$PPID/cmdline`",
                solution = "cat /proc/\$PPID/cmdline", solutionWhy = "The app's package name: its unique name on the phone and in the store.",
                fact = "The process calls itself by its package name. But which program is it really?",
                how = "the output is the app's package name",
            ),
            Task(
                id = "t11_5", title = "A clone",
                chunks = listOf("`exe` points to the program a process runs:\n\n      `ls -l /proc/\$PPID/exe`"),
                command = "ls -l /proc/\$PPID/exe", chips = listOf("ls", "-l", "/proc/\$PPID/exe"), keys = listOf("-", "/", "$"),
                drive = "device",
                prep = at,
                check = { a, _ -> a.ran("ls") && a.outHas("app_process") },
                hint1 = "`ls -l` and the path with `exe` at the end.", hint2 = "`ls -l /proc/\$PPID/exe`",
                solution = "ls -l /proc/\$PPID/exe", solutionWhy = "`/system/bin/app_process64`: not a program of its own.",
                fact = "app_process is Zygote's program. Your app did not start from scratch: it is a copy of Zygote, which then loaded the game's code.",
                how = "exe points to app_process",
            ),
            Task(
                id = "t11_6", title = "Your app's parent",
                chunks = listOf("Who started the app?\n\n      `grep PPid /proc/\$PPID/status`"),
                command = "grep PPid /proc/\$PPID/status", chips = listOf("grep", "PPid", "/proc/\$PPID/status"), keys = listOf("/", "$"),
                prep = at,
                check = { a, _ -> a.ran("grep") && a.outHas("PPid") },
                hint1 = "`grep`, `PPid` (capital P twice), the status file.", hint2 = "`grep PPid /proc/\$PPID/status`",
                solution = "grep PPid /proc/\$PPID/status", solutionWhy = "That number is Zygote: the parent of every app.",
                fact = "Every app on your phone has this same parent number.",
                how = "grep printed the PPid line",
            ),
            Task(
                id = "t11_7", title = "Visit Zygote",
                chunks = listOf("Look into its folder in /proc, with the number you just saw. **A refusal is the right result.**\n\n      `ls /proc/<number>`"),
                command = "ls /proc/<number>", chips = listOf("ls"), keys = listOf("/"),
                solve = { "ls /proc/" + zygotePid() },
                drive = "device",
                prep = at,
                check = { a, _ -> a.ran("ls") && a.words.any { Regex("""/proc/\d+/?""").matches(it) } },
                hint1 = "`ls /proc/` and the PPid number from before.", hint2 = "`ls /proc/` followed by the number, for example `ls /proc/468`.",
                solution = "ls /proc/<number>", solutionWhy = "Permission denied: you can know your grandparent's number, not visit it.",
                fact = "The same wall as with me: an app sees only itself in /proc.",
                how = "ls tried a /proc number",
            ),
            Task(
                id = "t11_8", title = "Which Zygote?",
                chunks = listOf("      `getprop ro.zygote`"),
                command = "getprop ro.zygote", chips = listOf("getprop", "ro.zygote"),
                prep = at,
                check = { a, _ -> a.ran("getprop") && a.outHas("zygote") },
                hint1 = "`getprop` and `ro.zygote`.", hint2 = "`getprop ro.zygote`",
                solution = "getprop ro.zygote", solutionWhy = "zygote64: apps are 64-bit. zygote64_32: there is a second Zygote for old 32-bit apps.",
                fact = "Newest phones have dropped 32-bit apps altogether: one Zygote less to start.",
                how = "getprop printed a zygote name",
            ),
            Task(
                id = "t11_9", title = "Your app's threads",
                card = "A process can do several things at once, each in a **thread**. Each thread has a name.",
                chunks = listOf("Your app's threads, sorted:\n\n      `cat /proc/\$PPID/task/*/comm | sort`"),
                command = "cat /proc/\$PPID/task/*/comm | sort",
                chips = listOf("cat", "/proc/\$PPID/task/*/comm", "|", "sort"), keys = listOf("/", "$", "*", "|"),
                drive = "device",
                prep = at,
                check = { a, _ -> a.ran("cat") && a.output.contains("binder", ignoreCase = true) },
                hint1 = "`cat` the comm file of every task (`*`), then `| sort`.", hint2 = "`cat /proc/\$PPID/task/*/comm | sort`",
                solution = "cat /proc/\$PPID/task/*/comm | sort", solutionWhy = "Binder threads, HeapTaskDaemon, RenderThread...: Android's machinery inside your app.",
                fact = "HeapTaskDaemon cleans up memory; RenderThread draws the screen; Binder threads wait for calls from the system. Level 12 is about those.",
                how = "the threads include Binder ones",
            ),
            Task(
                id = "t11_10", title = "Inherited",
                chunks = listOf("How much of Android's framework did your app get from Zygote? Count the lines in its memory map:\n\n      `grep -c framework /proc/\$PPID/maps`"),
                command = "grep -c framework /proc/\$PPID/maps",
                chips = listOf("grep", "-c", "framework", "/proc/\$PPID/maps"), keys = listOf("-", "/", "$"),
                drive = "device",
                prep = at,
                check = { a, _ -> a.ran("grep") && (a.output.trim().toIntOrNull() ?: 0) > 0 },
                hint1 = "`grep -c framework` and the maps file.", hint2 = "`grep -c framework /proc/\$PPID/maps`",
                solution = "grep -c framework /proc/\$PPID/maps", solutionWhy = "Hundreds of pieces of the framework: loaded once by Zygote, shared by every app.",
                fact = "That is why apps start fast and fit in memory together: the framework exists once, and every clone shares it.",
                how = "grep counted framework lines",
            ),
        ),
        outro = listOf(
            "The family: me (init, process 1), my child Zygote, its clones (your app and every other), and your shell, started by your app.",
            "Next: how all these processes talk to each other, through the system.",
        ),
        explore = listOf(
            "Settings > Apps > (an app) > Force stop ends one of Zygote's clones. Opening the app makes a new one, in a fraction of a second.",
        ),
    )
}
