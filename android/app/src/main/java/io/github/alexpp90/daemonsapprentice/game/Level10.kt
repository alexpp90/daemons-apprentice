package io.github.alexpp90.daemonsapprentice.game

import java.io.File

/** Level 10, "The Wall You Cannot See": SELinux says no where the file rights say yes; seccomp stops a program cold. */
object Level10 {

    private const val ENFORCE = "/sys/fs/selinux/enforce"

    private val at: suspend TaskContext.() -> Unit = {
        val d = File(dirs.playground, "labels")
        File(d, "note.txt").ensureText("A file of yours, with your label.\n")
        goTo(d)
    }

    /** Before reading the log: make sure there is a fresh refusal in it. */
    private val withDenial: suspend TaskContext.() -> Unit = {
        at(this)
        shell.runQuiet("cat $ENFORCE > /dev/null 2>&1")
    }

    /** Keeps `$?` for the task after dmesg. */
    private val quiet: suspend TaskContext.() -> Unit = { goTo(File(dirs.playground, "labels")) }

    val level = Level(
        number = 10,
        title = "The Wall You Cannot See",
        intro = listOf(
            "${Pid1.INSPECTING}  You have met walls: `/dev`, another app's room, files you could not read. Some of them you could explain with the rights from Level 7. Not all.",
            "Today you meet the guard behind them. It has a name, **SELinux**, and it writes down every time it says no. We are in `labels`.",
        ),
        tasks = listOf(
            Task(
                id = "t10_1", title = "Everybody may read it",
                card = "`$ENFORCE` is a file of the system. Look at its rights.",
                chunks = listOf("      `ls -l $ENFORCE`"),
                command = "ls -l $ENFORCE", chips = listOf("ls", "-l", ENFORCE), keys = listOf("-", "/"),
                drive = "device",
                prep = at,
                check = { a, _ -> a.ran("ls") && a.outHas("rw-r--r--") },
                hint1 = "`ls -l` and the long path.", hint2 = "`ls -l $ENFORCE`",
                solution = "ls -l $ENFORCE", solutionWhy = "`-rw-r--r--`: the last `r` means everybody may read it. You too.",
                fact = "By the rules of Level 7, you may read this file.",
                how = "ls -l shows rw-r--r--",
            ),
            Task(
                id = "t10_2", title = "And yet",
                chunks = listOf("Read it. **A refusal is the right result.**\n\n      `cat $ENFORCE`"),
                command = "cat $ENFORCE", chips = listOf("cat", ENFORCE), keys = listOf("/"),
                drive = "device",
                prep = at,
                check = { a, _ -> a.ran("cat") && a.status != 0 },
                hint1 = "`cat` and the same path.", hint2 = "`cat $ENFORCE`",
                solution = "cat $ENFORCE", solutionWhy = "Permission denied, although the rights said yes.",
                fact = "Something besides the file rights decides. On Android that is **SELinux**: a second guard, who checks *who* asks, not only which user.",
                how = "cat was refused",
            ),
            Task(
                id = "t10_3", title = "Your label",
                card = "SELinux gives every process a **label**, and every file too. A rule list says which label may do what with which other label. Everything not in the list is forbidden.",
                chunks = listOf("Your shell's label:\n\n      `id -Z`"),
                command = "id -Z", chips = listOf("id", "-Z"), keys = listOf("-"),
                drive = "device",
                prep = at,
                check = { a, _ -> a.ran("id") && a.outHas("untrusted_app") },
                hint1 = "`id` with a capital `-Z`.", hint2 = "`id -Z`",
                solution = "id -Z", solutionWhy = "`untrusted_app`: the label of every app you install.",
                fact = "The numbers at the end (like `c235,c256`) are your app's own categories: they keep apps apart even when the rules would let two untrusted apps meet.",
                how = "id -Z shows untrusted_app",
            ),
            Task(
                id = "t10_4", title = "Your files' labels",
                chunks = listOf("`-Z` works for `ls`, too:\n\n      `ls -Z`"),
                command = "ls -Z", chips = listOf("ls", "-Z"), keys = listOf("-"),
                drive = "device",
                prep = at,
                check = { a, _ -> a.ran("ls") && a.outHas("app_data_file") },
                hint1 = "`ls` and `-Z`.", hint2 = "`ls -Z`",
                solution = "ls -Z", solutionWhy = "`app_data_file`, with the same categories as your process: yours.",
                fact = "Another app's files carry its categories. Your label may not touch them, whatever their rights say.",
                how = "ls -Z shows app_data_file",
            ),
            Task(
                id = "t10_5", title = "The shell's label",
                chunks = listOf("And the program you are talking to right now:\n\n      `ls -Z /system/bin/sh`"),
                command = "ls -Z /system/bin/sh", chips = listOf("ls", "-Z", "/system/bin/sh"), keys = listOf("-", "/"),
                drive = "device",
                prep = at,
                check = { a, _ -> a.ran("ls") && a.outHas("shell_exec") },
                hint1 = "`ls -Z` and the shell's path.", hint2 = "`ls -Z /system/bin/sh`",
                solution = "ls -Z /system/bin/sh", solutionWhy = "`shell_exec`: a program apps may run.",
                fact = "`./hello.sh` in Level 8 had the label of your files, `app_data_file`, and running those is forbidden. That was SELinux, too.",
                how = "ls -Z shows shell_exec",
            ),
            Task(
                id = "t10_6", title = "The guard's diary",
                card = "Every refusal goes into the phone's log as an **avc** line. An app may read the log lines about itself.",
                chunks = listOf("The last three:\n\n      `logcat -d | grep avc | tail -3`"),
                command = "logcat -d | grep avc | tail -3",
                chips = listOf("logcat", "-d", "|", "grep", "avc", "tail", "-3"), keys = listOf("-", "|"),
                drive = "device",
                prep = withDenial,
                check = { a, _ -> a.ran("logcat") && a.outHas("avc") },
                hint1 = "`logcat -d`, then `grep avc`, then `tail -3`, joined with pipes.", hint2 = "`logcat -d | grep avc | tail -3`",
                solution = "logcat -d | grep avc | tail -3", solutionWhy = "Your own refusals: `denied { read }`, who asked (scontext) and what (tcontext).",
                fact = "`scontext` is your label, `tcontext` the file's. The rule list had no line allowing the one to read the other.",
                how = "the output has avc lines",
            ),
            Task(
                id = "t10_7", title = "Is the guard on?",
                chunks = listOf("`getenforce` asks whether SELinux is on. **A refusal is the right result.**\n\n      `getenforce`"),
                command = "getenforce", chips = listOf("getenforce"),
                drive = "device",
                prep = at,
                check = { a, _ -> a.ran("getenforce") },
                hint1 = "One word.", hint2 = "`getenforce`",
                solution = "getenforce", solutionWhy = "Even asking is refused: getenforce reads the very file from the start of this level.",
                fact = "On a computer it would say Enforcing or Permissive. On every phone sold, it is Enforcing: no switching it off.",
                how = "getenforce ran",
            ),
            Task(
                id = "t10_8", title = "A second guard",
                card = "Besides SELinux there is **seccomp**: a filter on the requests (system calls) a program may make to the kernel. A forbidden one, and the program is stopped on the spot.",
                chunks = listOf("`dmesg` reads the kernel's messages. Try it:\n\n      `dmesg`"),
                command = "dmesg", chips = listOf("dmesg"),
                drive = "device",
                prep = quiet,
                check = { a, _ -> a.ran("dmesg") && a.status != 0 },
                hint1 = "One word.", hint2 = "`dmesg`",
                solution = "dmesg", solutionWhy = "Nothing at all printed: dmesg was stopped before it could even ask.",
                fact = "No error message, because the program never got to write one. Only its exit status tells.",
                how = "dmesg failed",
            ),
            Task(
                id = "t10_9", title = "What happened?",
                chunks = listOf("The exit status, from Level 6:\n\n      `echo \$?`"),
                command = "echo \$?", chips = listOf("echo", "\$?"), keys = listOf("$"),
                drive = "device",
                prep = quiet,
                check = { a, fb ->
                    val n = a.lines().lastOrNull()?.toIntOrNull()
                    if (a.ran("echo") && n != null && n != 0) true
                    else { if (n == 0) fb.say("0: the command before worked. Run `dmesg` again, then `echo \$?` right after it."); false }
                },
                hint1 = "`echo`, a space, `\$?`.", hint2 = "`echo \$?`",
                solution = "echo \$?", solutionWhy = "159 = 128 + 31: stopped by signal number 31, SIGSYS, \"bad system call\".",
                fact = "Above 128 means: stopped by a signal. Ctrl-C gave 130 in Level 1: 128 + 2, SIGINT.",
                how = "echo printed a non-zero status",
            ),
            Task(
                id = "t10_10", title = "The filter is on",
                chunks = listOf("Every process can see whether it is filtered:\n\n      `grep Seccomp /proc/self/status`"),
                command = "grep Seccomp /proc/self/status", chips = listOf("grep", "Seccomp", "/proc/self/status"), keys = listOf("/"),
                prep = at,
                check = { a, _ -> a.ran("grep") && a.outHas("Seccomp") },
                hint1 = "`grep`, the word `Seccomp` with a capital S, the file.", hint2 = "`grep Seccomp /proc/self/status`",
                solution = "grep Seccomp /proc/self/status", solutionWhy = "`Seccomp: 2` means: a filter is on, for this process and everything it starts.",
                fact = "`/proc/self` is always the process that reads it: here, grep. Every app on your phone has the same filter, set by the system before the app's first line ran.",
                how = "grep printed the Seccomp line",
            ),
        ),
        outro = listOf(
            "Two invisible guards: SELinux checks every access by label, seccomp checks every request to the kernel. Neither can be switched off by an app.",
            "Next: the family. Who started your app, and why you cannot see them.",
        ),
        explore = listOf(
            "Settings > Security: \"Google Play system update\" and \"Security update\" bring new rules for these guards, too.",
        ),
    )
}
