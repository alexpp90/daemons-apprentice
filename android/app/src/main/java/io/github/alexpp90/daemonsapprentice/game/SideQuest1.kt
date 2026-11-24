package io.github.alexpp90.daemonsapprentice.game

import java.io.File
import java.time.LocalDate

/**
 * Side quest S1, "Portrait of Your Phone": read-only questions about the player's own phone. The game
 * works each answer out itself (so the portrait does not depend on how a task was solved) and writes
 * it into `portrait/my-phone.txt`, with the command that found it.
 */
object SideQuest1 {

    /** The rows of the portrait, in order: key, label, the command shown under the answer. */
    private val rows = listOf(
        Triple("android", "Android version", "getprop ro.build.version.sdk"),
        Triple("patch", "Security patch", "getprop ro.build.version.security_patch"),
        Triple("phone", "Phone", "getprop ro.product.manufacturer"),
        Triple("kernel", "Linux kernel", "uname -a"),
        Triple("up", "Awake for", "uptime"),
        Triple("cores", "Processor cores", "grep -c processor /proc/cpuinfo"),
        Triple("memory", "Memory (RAM)", "grep MemTotal /proc/meminfo"),
        Triple("storage", "Storage for apps", "df -h ~"),
        Triple("boot", "Verified boot", "getprop ro.boot.verifiedbootstate"),
        Triple("services", "System services", "service list | head -n 1"),
        Triple("tools", "Commands in toybox", "toybox | wc -w"),
        Triple("apps", "Apps this app can see", "pm list packages | wc -l"),
    )

    fun dir(d: GameDirs) = File(d.playground, "portrait")
    private fun answers(d: GameDirs) = File(dir(d), ".answers")

    private fun get(d: GameDirs): Map<String, String> =
        answers(d).takeIf { it.isFile }?.readLines()?.mapNotNull { l ->
            l.substringBefore('=', "").takeIf { it.isNotEmpty() }?.let { it to l.substringAfter('=') }
        }?.toMap() ?: emptyMap()

    /** Notes one answer (one line, at most 50 characters) and draws the portrait again. */
    private fun set(d: GameDirs, key: String, value: String) {
        val v = value.replace(Regex("\\s+"), " ").trim().take(50)
        if (v.isEmpty()) return
        dir(d).mkdirs()
        val all = get(d) + (key to v)
        answers(d).writeText(all.entries.joinToString("") { "${it.key}=${it.value}\n" })
        draw(d, all)
    }

    private fun draw(d: GameDirs, all: Map<String, String>) {
        val text = buildString {
            append("A portrait of my phone\n")
            append("======================\n")
            append("Drawn in the terminal on ${LocalDate.now()},\n")
            append("one question at a time. Under each answer:\n")
            append("the command that found it. Run them again\n")
            append("in a year and compare.\n\n")
            for ((k, label, cmd) in rows) {
                val v = all[k] ?: continue
                append("$label: $v\n    \$ $cmd\n\n")
            }
        }
        File(dir(d), "my-phone.txt").writeText(text)
    }

    private fun label(key: String) = rows.first { it.first == key }.second

    /** Works out [key]'s answer with [work] in the game's shell, notes it, and says what was noted. */
    private fun note(key: String, work: suspend TaskContext.() -> String): suspend TaskContext.(Attempt) -> String? = {
        val v = runCatching { work() }.getOrDefault("")
        set(dirs, key, v)
        val got = get(dirs)[key]
        if (got != null) "In your portrait now: **${label(key)}: $got**" else "In your portrait now: ${label(key)}."
    }

    private suspend fun TaskContext.out(cmd: String) = shell.runQuiet(cmd).output.trim()

    private fun human(kb: Long): String = when {
        kb >= 1024L * 1024 -> String.format(java.util.Locale.ROOT, "%.1f GB", kb / 1048576.0)
        kb >= 1024 -> "${kb / 1024} MB"
        else -> "$kb KB"
    }

    private fun awake(seconds: Long): String {
        val d = seconds / 86400; val h = seconds % 86400 / 3600; val m = seconds % 3600 / 60
        fun n(x: Long, w: String) = "$x $w" + if (x == 1L) "" else "s"
        return when {
            d > 0 -> n(d, "day") + ", " + n(h, "hour")
            h > 0 -> n(h, "hour") + ", " + n(m, "minute")
            else -> n(m, "minute")
        }
    }

    /** `uptime`'s "up 3 days,  4:05," or "up 7 min," as words ("" if it says something else). */
    internal fun upFor(uptime: String): String {
        val m = Regex("""up\s+(?:(\d+)\s+days?,\s*)?(?:(\d+):(\d+)|(\d+)\s+min)""").find(uptime) ?: return ""
        val (d, h, mm, min) = m.destructured
        val s = (d.toLongOrNull() ?: 0) * 86400 + (h.toLongOrNull() ?: 0) * 3600 + ((mm.toLongOrNull() ?: min.toLongOrNull()) ?: 0) * 60
        return awake(s)
    }

    private val at: suspend TaskContext.() -> Unit = { goTo(dir(dirs)) }

    val level = Level(
        number = 101,
        side = "S1",
        after = 5,
        title = "Portrait of Your Phone",
        pitch = "Twelve questions about **your own** phone, one command each, and a last look at the answers. We only look; nothing is changed. I write the answers into a file for you: a portrait of your phone. (Optional, about 10 minutes.)",
        intro = listOf(
            "${Pid1.INSPECTING}  Enough of my playground. Let's look at YOUR phone.",
            "We only look: no command here changes anything. I write down what we find, in `portrait/my-phone.txt`, each answer with the command that found it. At the end you read the whole portrait.",
        ),
        tasks = listOf(
            Task(
                id = "tS1_1", title = "Which Android?",
                card = "Android has two numbers: the version people talk about (like 14), and the **API level** that apps check.",
                chunks = listOf("Ask for the API level:\n\n      `getprop ro.build.version.sdk`"),
                command = "getprop ro.build.version.sdk", chips = listOf("getprop", "ro.build.version.sdk"),
                prep = at,
                check = { a, _ -> a.ran("getprop") && a.output.trim().toIntOrNull() != null },
                hint1 = "`getprop` and the property's name.", hint2 = "`getprop ro.build.version.sdk`",
                solution = "getprop ro.build.version.sdk", solutionWhy = "A number like 34: the API level of this phone's Android.",
                fact = "An app says which API level it needs at least. This game needs 29: Android 10 or newer.",
                how = "getprop printed a number",
                noted = note("android") { "${out("getprop ro.build.version.release")} (API level ${out("getprop ro.build.version.sdk")})" },
            ),
            Task(
                id = "tS1_2", title = "How up to date?",
                chunks = listOf("When did your phone last get its security holes fixed?\n\n      `getprop ro.build.version.security_patch`"),
                command = "getprop ro.build.version.security_patch", chips = listOf("getprop", "ro.build.version.security_patch"),
                prep = at,
                check = { a, _ -> a.ran("getprop") && Regex("""\d{4}-\d{2}-\d{2}""").containsMatchIn(a.output) },
                hint1 = "`getprop` and `ro.build.version.security_patch`.", hint2 = "`getprop ro.build.version.security_patch`",
                solution = "getprop ro.build.version.security_patch", solutionWhy = "A date: the patch level of this phone.",
                fact = "Every month the holes found in Android are fixed; your phone's maker brings the fixes to your phone. Months old? Look for an update in Settings.",
                how = "getprop printed a date",
                noted = note("patch") { out("getprop ro.build.version.security_patch") },
            ),
            Task(
                id = "tS1_3", title = "Who made it?",
                chunks = listOf("Level 5 asked for the model. Now the maker:\n\n      `getprop ro.product.manufacturer`"),
                command = "getprop ro.product.manufacturer", chips = listOf("getprop", "ro.product.manufacturer"),
                prep = at,
                check = { a, _ -> a.ran("getprop") && a.output.isNotBlank() },
                hint1 = "`getprop` and `ro.product.manufacturer`.", hint2 = "`getprop ro.product.manufacturer`",
                solution = "getprop ro.product.manufacturer", solutionWhy = "The company that built the phone.",
                fact = "`ro.` at the start means read-only: set when the phone's system was built, and no app can change it.",
                how = "getprop printed the maker",
                noted = note("phone") {
                    val model = out("getprop ro.product.model"); val maker = out("getprop ro.product.manufacturer")
                    if (maker.isEmpty()) model else "$model, by $maker"
                },
            ),
            Task(
                id = "tS1_4", title = "The kernel, all of it",
                chunks = listOf("`uname -r` gave the kernel's version. `-a` gives **all** it knows:\n\n      `uname -a`"),
                command = "uname -a", chips = listOf("uname", "-a"), keys = listOf("-"),
                prep = at,
                check = { a, _ -> a.ran("uname") && "-a" in a.words && a.outHas("Linux") },
                hint1 = "`uname` with the option `-a`.", hint2 = "`uname -a`",
                solution = "uname -a", solutionWhy = "Linux, the phone's name, the kernel's version and the date it was built, and the kind of processor.",
                fact = "`aarch64` near the end means a 64-bit ARM processor, as in almost every phone (a computer, and an emulator on it, says `x86_64`).",
                how = "uname -a printed Linux",
                noted = note("kernel") { out("uname -r") },
            ),
            Task(
                id = "tS1_5", title = "Awake for how long?",
                chunks = listOf("When did your phone last restart?\n\n      `uptime`"),
                command = "uptime", chips = listOf("uptime"),
                prep = at,
                check = { a, _ -> a.ran("uptime") && a.outHas("up") },
                hint1 = "One word.", hint2 = "`uptime`",
                solution = "uptime", solutionWhy = "The time, how long the phone has been up, and the load: how busy it was in the last 1, 5 and 15 minutes.",
                fact = "Phones restart rarely: days or weeks are normal. Sleeping with the screen off counts as up.",
                how = "uptime printed up",
                // (`/proc/uptime` itself is closed to apps; `uptime` asks the kernel another way.)
                noted = note("up") { upFor(out("uptime")) },
            ),
            Task(
                id = "tS1_6", title = "How many cores?",
                card = "`/proc/cpuinfo` describes the processor, one block per **core** (a processor in the processor). `grep -c` counts the lines that match instead of showing them.",
                chunks = listOf("      `grep -c processor /proc/cpuinfo`"),
                command = "grep -c processor /proc/cpuinfo", chips = listOf("grep", "-c", "processor", "/proc/cpuinfo"), keys = listOf("-", "/"),
                prep = at,
                check = { a, _ -> a.ran("grep") && (a.output.trim().toIntOrNull() ?: 0) > 0 },
                hint1 = "`grep -c`, the word `processor`, then the file.", hint2 = "`grep -c processor /proc/cpuinfo`",
                solution = "grep -c processor /proc/cpuinfo", solutionWhy = "The number of cores, often 8.",
                fact = "Phones mix fast and slow cores: slow ones save battery for small jobs, fast ones open apps. Level 15 shows which group your app gets.",
                how = "grep counted cores",
                noted = note("cores") { out("grep -c processor /proc/cpuinfo").ifEmpty { out("nproc") } },
            ),
            Task(
                id = "tS1_7", title = "How much memory?",
                chunks = listOf("`/proc/meminfo` is what `free` reads. The total:\n\n      `grep MemTotal /proc/meminfo`"),
                command = "grep MemTotal /proc/meminfo", chips = listOf("grep", "MemTotal", "/proc/meminfo"), keys = listOf("/"),
                prep = at,
                check = { a, _ -> a.ran("grep") && a.outHas("MemTotal") },
                hint1 = "`grep`, `MemTotal`, the file.", hint2 = "`grep MemTotal /proc/meminfo`",
                solution = "grep MemTotal /proc/meminfo", solutionWhy = "The memory in kB (kilobytes): divide by a million for GB.",
                fact = "A little less than the shop says: part of it belongs to the system and to the graphics before Linux even starts.",
                how = "grep found MemTotal",
                noted = note("memory") {
                    val kb = Regex("""\d+""").find(out("grep MemTotal /proc/meminfo"))?.value?.toLongOrNull()
                    if (kb == null) "" else human(kb)
                },
            ),
            Task(
                id = "tS1_8", title = "How much room?",
                chunks = listOf("`~` is your home. How big is the place it lives on?\n\n      `df -h ~`"),
                command = "df -h ~", chips = listOf("df", "-h", "~"), keys = listOf("-", "~"),
                prep = at,
                check = { a, _ -> a.ran("df") && a.lines().size >= 2 },
                hint1 = "`df -h` and `~`.", hint2 = "`df -h ~`",
                solution = "df -h ~", solutionWhy = "Size, used, available: the phone's big data partition, where your home is.",
                fact = "Every app's room is on this one partition, each locked against the others. Your photos live there, too.",
                how = "df printed the partition",
                noted = note("storage") {
                    val f = out("df -k ~ | tail -n 1").split(Regex("\\s+"))
                    val size = f.getOrNull(1)?.toLongOrNull(); val free = f.getOrNull(3)?.toLongOrNull()
                    if (size == null || free == null) "" else "${human(size)}, ${human(free)} free"
                },
            ),
            Task(
                id = "tS1_9", title = "Checked at start",
                card = "When a phone starts, it checks that its system is the maker's own, untouched: **verified boot**. The result has a colour.",
                chunks = listOf("      `getprop ro.boot.verifiedbootstate`"),
                command = "getprop ro.boot.verifiedbootstate", chips = listOf("getprop", "ro.boot.verifiedbootstate"),
                prep = at,
                check = { a, _ -> a.ran("getprop") && a.status == 0 },
                hint1 = "`getprop` and `ro.boot.verifiedbootstate`.", hint2 = "`getprop ro.boot.verifiedbootstate`",
                solution = "getprop ro.boot.verifiedbootstate", solutionWhy = "green: checked, the maker's own system. orange: the phone was unlocked to start any system (a developer's phone). An emulator may say nothing at all.",
                fact = "That check is why malware cannot simply hide in the system: changed system files fail it, and the phone warns at every start.",
                how = "getprop ran",
                noted = note("boot") { out("getprop ro.boot.verifiedbootstate").ifEmpty { "not told" } },
            ),
            Task(
                id = "tS1_10", title = "The system's helpers",
                card = "Android's system is made of **services**: one runs apps, one knows where you are, one minds the battery.",
                chunks = listOf("The first line of the list says how many there are:\n\n      `service list | head -n 1`"),
                command = "service list | head -n 1", chips = listOf("service", "list", "|", "head", "-n", "1"), keys = listOf("|", "-"),
                drive = "device",
                prep = at,
                check = { a, _ -> a.ran("service") && a.outHas("services") },
                hint1 = "`service list`, then `| head -n 1`.", hint2 = "`service list | head -n 1`",
                solution = "service list | head -n 1", solutionWhy = "Found ... services: hundreds of them.",
                fact = "Every app talks to them all the time, through a door called Binder. Level 12 opens that door.",
                how = "service list counted services",
                noted = note("services") { Regex("""\d+""").find(out("service list | head -n 1"))?.value ?: "" },
            ),
            Task(
                id = "tS1_11", title = "One program, many commands",
                card = "Most commands you typed are not separate programs: they are all **toybox**, one program that acts as `ls`, `cat`, `grep` and many more.",
                chunks = listOf("Without a command, toybox lists what it can be. Count them:\n\n      `toybox | wc -w`"),
                command = "toybox | wc -w", chips = listOf("toybox", "|", "wc", "-w"), keys = listOf("|", "-"),
                drive = "device",
                prep = at,
                check = { a, _ -> a.ran("wc") && (a.output.trim().toIntOrNull() ?: 0) > 0 },
                hint1 = "`toybox`, then `| wc -w` to count the words.", hint2 = "`toybox | wc -w`",
                solution = "toybox | wc -w", solutionWhy = "Around two hundred commands in one program.",
                fact = "One program saves room: the commands share most of their code. On a computer they are usually separate programs.",
                how = "wc counted toybox's commands",
                noted = note("tools") { out("toybox | wc -w") },
            ),
            Task(
                id = "tS1_12", title = "Which apps?",
                card = "`pm` is the package manager: it knows what is installed. An app is a **package**.",
                chunks = listOf("Count the apps this game may know about:\n\n      `pm list packages | wc -l`"),
                command = "pm list packages | wc -l", chips = listOf("pm", "list", "packages", "|", "wc", "-l"), keys = listOf("|", "-"),
                drive = "device",
                prep = at,
                check = { a, _ -> a.ran("wc") && (a.output.trim().toIntOrNull() ?: 0) > 0 },
                hint1 = "`pm list packages`, then `| wc -l`.", hint2 = "`pm list packages | wc -l`",
                solution = "pm list packages | wc -l", solutionWhy = "A number of packages: mostly the system's own.",
                fact = "Not all your apps: an app sees only the system's apps and the ones it says it needs. Your list of apps says a lot about you; Level 14 is about that.",
                how = "wc counted packages",
                noted = note("apps") { out("pm list packages | wc -l") },
            ),
            Task(
                id = "tS1_13", title = "The portrait",
                chunks = listOf("Everything we found, in one file:\n\n      `cat my-phone.txt`"),
                command = "cat my-phone.txt", chips = listOf("cat", "my-phone.txt"),
                prep = at,
                check = { a, _ -> a.ran("cat") && a.outHas("portrait of my phone") },
                hint1 = "`cat` and the file.", hint2 = "`cat my-phone.txt`",
                solution = "cat my-phone.txt", solutionWhy = "Your phone, in twelve answers, each with its command.",
                fact = "It stays in `portrait`. Run the commands again in a year: a newer Android, a newer patch, more services.",
                how = "cat showed the portrait",
            ),
        ),
        outro = listOf(
            "Twelve questions, twelve answers, and nothing changed: that is what looking around in a terminal is.",
            "${Pid1.CHEER}  Back to the main path.",
        ),
        explore = listOf(
            "Settings > About phone: model, Android version and security update, the same answers in the phone's own words.",
            "Settings > Storage: the room `df` measured, by what uses it.",
        ),
    )
}
