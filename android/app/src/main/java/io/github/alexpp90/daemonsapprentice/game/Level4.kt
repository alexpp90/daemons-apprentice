package io.github.alexpp90.daemonsapprentice.game

import java.io.File

/** Level 4, "Text Power": small tools, combined with pipes. */
object Level4 {

    val appLog = listOf(
        "08:00 INFO phone starts", "08:00 INFO network found", "08:01 INFO app opened: Camera",
        "08:02 INFO photo saved", "08:05 ERROR no network", "08:05 INFO network found",
        "08:10 INFO app opened: Maps", "08:12 WARN battery 20%", "08:13 ERROR GPS timeout",
        "08:15 INFO app opened: Music", "08:20 INFO update checked", "08:21 ERROR storage almost full",
        "08:25 INFO photo saved", "08:30 INFO app opened: Chat", "08:31 INFO message sent",
        "08:40 WARN battery 10%", "08:41 ERROR app crashed: Game", "08:45 INFO charging",
        "08:50 INFO app opened: Notes", "08:59 INFO phone locked",
    ).joinToString("\n", postfix = "\n")

    val fruits = listOf(
        "banana", "apple", "cherry", "apple", "kiwi", "banana", "apple",
        "mango", "cherry", "apple", "banana", "apple",
    ).joinToString("\n", postfix = "\n")

    private fun dir(t: TaskContext) = File(t.dirs.playground, "texts")

    private fun at(clean: Boolean = false): suspend TaskContext.() -> Unit = {
        val d = dir(this)
        File(d, "app.log").ensureText(appLog)
        File(d, "fruits.txt").ensureText(fruits)
        if (clean) File(d, "errors.txt").delete()
        else if (!File(d, "errors.txt").exists()) File(d, "errors.txt").writeText(appLog.lines().filter { "ERROR" in it }.joinToString("\n", postfix = "\n"))
        goTo(d)
    }

    private fun lines(a: Attempt) = a.output.lines().filter { it.isNotBlank() }

    val level = Level(
        number = 4,
        title = "Text Power",
        intro = listOf(
            "${Pid1.INSPECTING}  Your phone writes logs: little diaries of everything it does. Today you read them like a detective.",
            "Small tools, each doing one thing well, then joined together. That is the real power of the terminal. We work in `texts` in your playground.",
        ),
        tasks = listOf(
            Task(
                id = "t4_1", title = "The first lines",
                chunks = listOf("`app.log` is a made-up phone log, 20 lines. See the first ten:\n\n      `head app.log`"),
                command = "head app.log", chips = listOf("head", "app.log"),
                prep = at(true),
                check = { a, _ -> a.ran("head") && a.outHas("phone starts") && !a.outHas("phone locked") },
                hint1 = "`head` and the file.", hint2 = "`head app.log`",
                solution = "head app.log", solutionWhy = "`head` shows the first lines of a file, ten unless told otherwise.",
                fact = "`head -n 3 app.log` would show just three.",
                how = "the output starts the log and does not reach its end",
            ),
            Task(
                id = "t4_2", title = "The last lines",
                chunks = listOf("And the end of it:\n\n      `tail app.log`"),
                command = "tail app.log", chips = listOf("tail", "app.log"),
                prep = at(),
                check = { a, _ -> a.ran("tail") && a.outHas("phone locked") && !a.outHas("phone starts") },
                hint1 = "The opposite of head.", hint2 = "`tail app.log`",
                solution = "tail app.log", solutionWhy = "`tail` shows the last lines: in a log, the newest.",
                fact = "New lines go to the end of a log, so `tail` shows what just happened.",
                how = "the output shows the end of the log",
            ),
            Task(
                id = "t4_3", title = "Count",
                chunks = listOf("How many lines? `wc` counts (word count); `-l` counts lines:\n\n      `wc -l app.log`"),
                command = "wc -l app.log", chips = listOf("wc", "-l", "app.log"), keys = listOf("-"),
                prep = at(),
                check = { a, _ -> a.ran("wc") && a.outHas("20") },
                hint1 = "`wc`, `-l` (a small L), the file.", hint2 = "`wc -l app.log`",
                solution = "wc -l app.log", solutionWhy = "`wc -l` counts the lines of the file: 20.",
                fact = "Real phone logs have tens of thousands of lines. Counting them by eye: no.",
                how = "wc printed 20",
            ),
            Task(
                id = "t4_4", title = "Find the errors",
                chunks = listOf("Only the problems, please:\n\n      `grep ERROR app.log`"),
                command = "grep ERROR app.log", chips = listOf("grep", "ERROR", "app.log"),
                prep = at(),
                check = { a, fb ->
                    val l = lines(a)
                    if (a.ran("grep") && l.size == 4 && l.all { "ERROR" in it }) true
                    else { if (a.ran("grep") && a.status == 1) fb.say("Nothing found. Capitals: `ERROR`."); false }
                },
                hint1 = "`grep`, the word `ERROR` in capitals, the file.", hint2 = "`grep ERROR app.log`",
                solution = "grep ERROR app.log", solutionWhy = "`grep` keeps only the lines with ERROR.",
                fact = "Four errors. And a crashed game at 08:41.",
                how = "the output is the four ERROR lines",
            ),
            Task(
                id = "t4_5", title = "Your first pipe",
                card = "`|` (the **pipe**) hands what one command prints to the next command, instead of to the screen.",
                chunks = listOf("Count the errors, without counting yourself:\n\n      `grep ERROR app.log | wc -l`"),
                command = "grep ERROR app.log | wc -l",
                parts = listOf("grep ERROR app.log" to "find the error lines", "|" to "hand them on", "wc -l" to "count them"),
                chips = listOf("grep", "ERROR", "app.log", "|", "wc", "-l"), keys = listOf("|"),
                prep = at(),
                check = { a, fb ->
                    if (a.ran("grep") && a.ran("wc") && a.output.trim() == "4") true
                    else { if (a.ran("grep") && "-c" in a.words) fb.say("`grep -c` counts, too. Here, please try the pipe: `grep ERROR app.log | wc -l`."); false }
                },
                hint1 = "First the grep from before, then `|`, then `wc -l`.", hint2 = "`grep ERROR app.log | wc -l`",
                solution = "grep ERROR app.log | wc -l", solutionWhy = "grep finds the lines, the pipe hands them to wc, wc counts them.",
                fact = "Small tools plus pipes: that is how AI agents and admins answer questions about huge logs.",
                how = "grep and wc ran and printed 4",
            ),
            Task(
                id = "t4_6", title = "A messy list",
                chunks = listOf("Now a list of fruits people picked. Look at the mess first:\n\n      `cat fruits.txt`"),
                command = "cat fruits.txt", chips = listOf("cat", "fruits.txt"),
                prep = at(),
                check = { a, _ -> a.outHas("banana") && a.outHas("mango") },
                hint1 = "`cat` and the file.", hint2 = "`cat fruits.txt`",
                solution = "cat fruits.txt", solutionWhy = "`cat` shows the list as it is: unsorted, with repeats.",
                fact = "Which fruit wins? Hard to tell like this.",
                how = "the output is the fruit list",
            ),
            Task(
                id = "t4_7", title = "Sort it out",
                chunks = listOf("`sort` puts lines in order:\n\n      `sort fruits.txt`"),
                command = "sort fruits.txt", chips = listOf("sort", "fruits.txt"),
                prep = at(),
                check = { a, _ -> val l = lines(a); a.ran("sort") && l.size == 12 && l == l.sorted() },
                hint1 = "`sort` and the file.", hint2 = "`sort fruits.txt`",
                solution = "sort fruits.txt", solutionWhy = "`sort` prints the lines in alphabetical order; the file stays as it was.",
                fact = "Same fruits now stand next to each other. That is what the next tool needs.",
                how = "the output is the twelve fruits in order",
            ),
            Task(
                id = "t4_8", title = "Count them",
                chunks = listOf("`uniq -c` squeezes neighbours that are the same into one line and counts them:\n\n      `sort fruits.txt | uniq -c`"),
                command = "sort fruits.txt | uniq -c",
                chips = listOf("sort", "fruits.txt", "|", "uniq", "-c"), keys = listOf("|"),
                prep = at(),
                check = { a, _ -> a.ran("uniq") && Regex("""\b5 apple""").containsMatchIn(a.output) },
                hint1 = "The sort from before, a pipe, `uniq -c`.", hint2 = "`sort fruits.txt | uniq -c`",
                solution = "sort fruits.txt | uniq -c", solutionWhy = "sort brings equal lines together, uniq -c counts each group.",
                fact = "`uniq` only sees neighbours. Without `sort` first, it counts wrong.",
                how = "uniq counted 5 apples",
            ),
            Task(
                id = "t4_9", title = "Which fruit wins?",
                chunks = listOf(
                    "Biggest number first: `sort -rn` (r: reverse, n: by number). Then only the top: `head -1`.",
                    "Tap **↑** for the last line and add the two parts at the end:\n\n      `sort fruits.txt | uniq -c | sort -rn | head -1`",
                ),
                command = "sort fruits.txt | uniq -c | sort -rn | head -1",
                chips = listOf("sort", "fruits.txt", "|", "uniq", "-c", "-rn", "head", "-1"), keys = listOf("|", "-"),
                prep = at(),
                check = { a, _ -> val l = lines(a); l.size == 1 && l[0].contains("apple") },
                hint1 = "↑, then add ` | sort -rn | head -1`.", hint2 = "`sort fruits.txt | uniq -c | sort -rn | head -1`",
                solution = "sort fruits.txt | uniq -c | sort -rn | head -1",
                solutionWhy = "sort, count, sort by count from the top, keep the first line.",
                fact = "Four small tools, one answer: apple. That is a real data pipeline.",
                how = "one line came back, and it is apple",
            ),
            Task(
                id = "t4_10", title = "Keep the errors",
                chunks = listOf("Save the error lines in their own file, with `>` from Level 3:\n\n      `grep ERROR app.log > errors.txt`"),
                command = "grep ERROR app.log > errors.txt",
                chips = listOf("grep", "ERROR", "app.log", ">", "errors.txt"), keys = listOf(">"),
                prep = at(true),
                check = { a, _ -> File(a.pg, "texts/errors.txt").let { f -> f.isFile && f.readLines().count { "ERROR" in it } == 4 } },
                hint1 = "The grep from before, then `> errors.txt`.", hint2 = "`grep ERROR app.log > errors.txt`",
                solution = "grep ERROR app.log > errors.txt", solutionWhy = "`>` sends grep's lines into errors.txt instead of the screen.",
                fact = "Nothing on the screen: it all went into the file.",
                how = "errors.txt holds the four ERROR lines",
            ),
            Task(
                id = "t4_11", title = "Check the file",
                chunks = listOf("Did it land?\n\n      `cat errors.txt`"),
                command = "cat errors.txt", chips = listOf("cat", "errors.txt"),
                prep = at(),
                check = { a, _ -> lines(a).count { "ERROR" in it } == 4 },
                hint1 = "`cat` and the new file.", hint2 = "`cat errors.txt`",
                solution = "cat errors.txt", solutionWhy = "`cat` shows the four saved error lines.",
                fact = "Write, then check. Always.",
                how = "the output shows the four ERROR lines",
            ),
        ),
        outro = listOf(
            "head, tail, wc, grep, sort, uniq: six small tools. Joined with pipes they answered questions no single tool could.",
            "AI coding agents do exactly this with your logs and files. Now you can read their pipelines, and check them.",
        ),
        explore = listOf("Settings > Battery > Battery usage shows a chart. The same idea: many small records, counted and sorted."),
    )
}
