package io.github.alexpp90.daemonsapprentice.game

import java.io.File

/** Level 15, "Life and Death of an App": how much Android values your app, in front and away, and who decides when it dies. */
object Level15 {

    val recorder = """
        # recorder.sh: every second, write down how much Android values this app.
        app=${'$'}(grep PPid /proc/${'$'}PPID/status | cut -f2)   # the app: your shell's parent
        i=0
        while [ ${'$'}i -lt 900 ]; do                         # at most 15 minutes
          echo "${'$'}(date +%T) ${'$'}(cat /proc/${'$'}app/oom_score_adj)" >> oom.log
          sleep 1 & wait ${'$'}!                               # wait a second
          i=${'$'}((i+1))
        done
    """.trimIndent() + "\n"

    private fun dir(d: GameDirs) = File(d.playground, "life")

    /** Recorders still running (their /proc entries are this app's own, so the app can see them). */
    fun recorderPids(): List<Int> = (File("/proc").listFiles() ?: emptyArray()).mapNotNull { p ->
        val pid = p.name.toIntOrNull() ?: return@mapNotNull null
        val cmd = runCatching { File(p, "cmdline").readText() }.getOrDefault("")
        if (cmd.contains("recorder.sh")) pid else null
    }

    private fun at(fresh: Boolean = false): suspend TaskContext.() -> Unit = {
        val d = dir(dirs)
        File(d, "recorder.sh").ensureText(recorder)
        goTo(d)
        if (fresh) {
            recorderPids().forEach { shell.runQuiet("kill $it 2> /dev/null") }
            File(d, "oom.log").delete()
        }
    }

    val level = Level(
        number = 15,
        title = "Life and Death of an App",
        intro = listOf(
            "${Pid1.SLEEPING}  The last secret. Your phone has little memory and many apps. When it runs short, someone has to go. Who decides?",
            "Android ranks every app, all the time. Today you watch your own app's rank change, while you are away. We are in `life`.",
        ),
        tasks = listOf(
            Task(
                id = "t15_1", title = "Your app's rank",
                card = "`oom_score_adj` is the app's rank when memory runs out (OOM: out of memory). Lower means more important; 0 is the app you are looking at.",
                chunks = listOf("      `cat /proc/\$PPID/oom_score_adj`"),
                command = "cat /proc/\$PPID/oom_score_adj", chips = listOf("cat", "/proc/\$PPID/oom_score_adj"), keys = listOf("/", "$"),
                prep = at(),
                check = { a, _ -> a.ran("cat") && a.output.trim().toIntOrNull() != null },
                hint1 = "`cat` and the path with `\$PPID`.", hint2 = "`cat /proc/\$PPID/oom_score_adj`",
                solution = "cat /proc/\$PPID/oom_score_adj", solutionWhy = "0: you are looking at it, so it is as important as an app gets.",
                fact = "The system itself runs below 0, down to -1000: things that must never be killed, like me.",
                how = "cat printed a number",
            ),
            Task(
                id = "t15_2", title = "Your app's group",
                chunks = listOf("Android also sorts apps into groups (**cgroups**) that share the processor:\n\n      `cat /proc/\$PPID/cgroup`"),
                command = "cat /proc/\$PPID/cgroup", chips = listOf("cat", "/proc/\$PPID/cgroup"), keys = listOf("/", "$"),
                prep = at(),
                check = { a, _ -> a.ran("cat") && a.output.isNotBlank() },
                hint1 = "Like before, `cgroup` at the end.", hint2 = "`cat /proc/\$PPID/cgroup`",
                solution = "cat /proc/\$PPID/cgroup", solutionWhy = "`top-app`: the app in front gets the fast cores first.",
                fact = "Apps in the background go to `background`, with less processor time. You will see your app move in a minute.",
                how = "cat printed the cgroups",
            ),
            Task(
                id = "t15_3", title = "A recorder",
                card = "The game wrote you a script that writes down your app's rank every second, into `oom.log`. Read it first: never run a script you have not read.",
                chunks = listOf("      `cat recorder.sh`"),
                command = "cat recorder.sh", chips = listOf("cat", "recorder.sh"),
                parts = listOf(
                    "app=\$(grep PPid ...)" to "the app's PID: your shell's parent",
                    "while ... done" to "at most 900 times",
                    "echo ... >> oom.log" to "the time and the rank, added to the log",
                    "sleep 1 & wait \$!" to "a second's pause",
                ),
                prep = at(),
                check = { a, _ -> a.ran("cat") && a.outHas("oom_score_adj") },
                hint1 = "`cat` and the script.", hint2 = "`cat recorder.sh`",
                solution = "cat recorder.sh", solutionWhy = "A loop that writes one line per second into oom.log.",
                fact = "Everything in it you have learned: \$( ), a variable, a while loop, >>, /proc.",
                how = "cat showed the script",
            ),
            Task(
                id = "t15_4", title = "Start it in the background",
                card = "`&` at the end starts a command in the background: the prompt comes back at once, and the command keeps running. The shell numbers it as **job** 1.",
                chunks = listOf("      `sh recorder.sh &`"),
                command = "sh recorder.sh &", chips = listOf("sh", "recorder.sh", "&"), keys = listOf("&"),
                prep = at(fresh = true),
                check = { a, _ -> a.ran("sh") && a.line.trim().endsWith("&") && a.status == 0 },
                hint1 = "`sh recorder.sh`, a space, `&`.", hint2 = "`sh recorder.sh &`",
                solution = "sh recorder.sh &", solutionWhy = "`[1]` and a PID: the recorder runs, and the prompt is free.",
                fact = "It is writing now, once a second, while you do other things.",
                how = "the recorder was started in the background",
            ),
            Task(
                id = "t15_5", title = "Go away",
                chunks = listOf("Now leave the app: go to your phone's **home screen**, wait about ten seconds, then come back to the game (tap its icon, or use the recent apps).\n\n      `Home, ten seconds, back`"),
                command = "Home, ten seconds, back",
                event = "away", drive = "home",
                prep = at(),
                check = { _, _ -> false },
                hint1 = "The home button or gesture of your phone (with a keyboard: often the Home key or Meta+Enter).", hint2 = "Home screen, count to ten, then open the game again.",
                solution = "(home screen, ten seconds, back)", solutionWhy = "While you were away, the recorder kept writing.",
                fact = "The app was not stopped while you were away: its processes kept running, and Android re-ranked it.",
                how = "the app was away from the screen for five seconds or more",
            ),
            Task(
                id = "t15_6", title = "What happened while you were away",
                chunks = listOf("      `cat oom.log`"),
                command = "cat oom.log", chips = listOf("cat", "oom.log"),
                prep = at(),
                check = { a, fb ->
                    if (a.ran("cat") && a.lines().isNotEmpty() && a.status == 0) true
                    else { if (a.ran("cat") && a.status != 0) fb.say("No log yet: start the recorder first (`sh recorder.sh &`), or type `skip`."); false }
                },
                hint1 = "`cat` and the log.", hint2 = "`cat oom.log`",
                solution = "cat oom.log", solutionWhy = "0 while in front; while you were away, a higher number (often 700, sometimes 200 first); 0 again when you came back.",
                fact = "The higher the number, the sooner the app dies when memory runs short. This is the **low-memory killer**: when apps need memory, it kills the highest-ranked first, process group and all, recorder included. A gap in the times means Android even froze the app to save battery.",
                how = "cat showed the log",
            ),
            Task(
                id = "t15_7", title = "Stop the recorder",
                chunks = listOf("`%1` means job 1: the recorder.\n\n      `kill %1`"),
                command = "kill %1", chips = listOf("kill", "%1"), keys = listOf("%"),
                prep = at(),
                check = { a, fb ->
                    if (a.ran("kill") && "%1" in a.words && a.status == 0) true
                    else { if (a.ran("kill") && a.status != 0) fb.say("No job 1 in this shell: the recorder is not running (the app may have been restarted). Type `skip`."); false }
                },
                hint1 = "`kill` and `%1`.", hint2 = "`kill %1`",
                solution = "kill %1", solutionWhy = "The recorder got the \"please stop\" signal, and stopped.",
                fact = "When Android kills an app for memory, it is not this polite: it sends the signal that cannot be ignored, to the whole group at once.",
                how = "kill %1 succeeded",
            ),
        ),
        outro = listOf(
            "In front: rank 0, the fast cores. Away: a higher rank, the slow lane, and first in line when memory runs out.",
            "From your first `help` to Zygote, Binder and the low-memory killer: you have seen your phone from the inside. ${Pid1.CHEER}",
        ),
        explore = listOf(
            "Settings > Apps > (an app) > Battery: \"Restricted\" pushes an app further down when it is in the background.",
            "Settings > Developer options > Background process limit (if you have developer options on): the same killer, set by hand.",
        ),
    )
}
