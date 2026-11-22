package io.github.alexpp90.daemonsapprentice.game

/** Level 13, "Your App Is a Zip": the APK, its code, its fingerprint, and why an app cannot change itself. */
object Level13 {

    /** `$APP` and `$APK` for this level (said in the intro). */
    private val at: suspend TaskContext.() -> Unit = {
        goTo(dirs.playground)
        shell.runQuiet("APP='${AppInfo.pkg}'; APK='${AppInfo.apk}'")
    }

    private fun apkName() = AppInfo.apk.substringAfterLast('/')

    val level = Level(
        number = 13,
        title = "Your App Is a Zip",
        intro = listOf(
            "${Pid1.INSPECTING}  Where did your app come from? From a file: an **APK**. You downloaded it, Android installed it, Zygote's clone loaded it.",
            "For this level the game has set two variables: `\$APP` is the app's package name, `\$APK` the path of its APK. Let's open it.",
        ),
        tasks = listOf(
            Task(
                id = "t13_1", title = "Where is it?",
                chunks = listOf("Ask the package manager:\n\n      `pm path \$APP`"),
                command = "pm path \$APP", chips = listOf("pm", "path", "\$APP"), keys = listOf("$"),
                drive = "device",
                prep = at,
                check = { a, _ -> a.ran("pm") && a.outHas("package:") },
                hint1 = "`pm path` and the variable.", hint2 = "`pm path \$APP`",
                solution = "pm path \$APP", solutionWhy = "The APK's path, in `/data/app`: the same as `\$APK`.",
                fact = "The strange letters in the path are random, made at install time, so nobody can guess where an app lives.",
                how = "pm printed the package path",
            ),
            Task(
                id = "t13_2", title = "Whose is it?",
                chunks = listOf("      `ls -l \$APK`"),
                command = "ls -l \$APK", chips = listOf("ls", "-l", "\$APK"), keys = listOf("-", "$"),
                prep = at,
                check = { a, _ -> a.ran("ls") && a.outHas(apkName()) },
                hint1 = "`ls -l` and the variable.", hint2 = "`ls -l \$APK`",
                solution = "ls -l \$APK", solutionWhy = "Owner `system`, rights `rw-r--r--`: you may read your app, not change it.",
                fact = "The size is the download size, about the size of a photo or two.",
                how = "ls -l showed the APK",
            ),
            Task(
                id = "t13_3", title = "It is a zip",
                chunks = listOf("An APK is a zip file. List what is inside:\n\n      `unzip -l \$APK | head`"),
                command = "unzip -l \$APK | head", chips = listOf("unzip", "-l", "\$APK", "|", "head"), keys = listOf("-", "$", "|"),
                prep = at,
                check = { a, _ -> a.ran("unzip") && (a.outHas("AndroidManifest.xml") || a.outHas("Archive")) },
                hint1 = "`unzip -l` lists, then `| head`.", hint2 = "`unzip -l \$APK | head`",
                solution = "unzip -l \$APK | head", solutionWhy = "AndroidManifest.xml (what the app is and may do), code, pictures, and more.",
                fact = "Rename any APK to .zip and a computer opens it like a folder.",
                how = "unzip listed the APK",
            ),
            Task(
                id = "t13_4", title = "The code",
                chunks = listOf("The program itself is in `.dex` files:\n\n      `unzip -l \$APK | grep dex`"),
                command = "unzip -l \$APK | grep dex", chips = listOf("unzip", "-l", "\$APK", "|", "grep", "dex"), keys = listOf("-", "$", "|"),
                prep = at,
                check = { a, _ -> a.ran("grep") && a.outHas(".dex") },
                hint1 = "The list, then `grep dex`.", hint2 = "`unzip -l \$APK | grep dex`",
                solution = "unzip -l \$APK | grep dex", solutionWhy = "classes.dex, classes2.dex...: Dalvik executables, the app's code.",
                fact = "Kotlin and Java are turned into dex code, which Android's runtime (ART) turns into the phone's own machine code.",
                how = "grep found dex files",
            ),
            Task(
                id = "t13_5", title = "A file's first bytes",
                card = "Files often start with a few fixed bytes that say what they are: a **magic number**. `od -c` shows bytes as characters.",
                chunks = listOf("The first 8 bytes of the code:\n\n      `unzip -p \$APK classes.dex | head -c 8 | od -c`"),
                command = "unzip -p \$APK classes.dex | head -c 8 | od -c",
                chips = listOf("unzip", "-p", "\$APK", "classes.dex", "|", "head", "-c", "8", "od"), keys = listOf("-", "$", "|"),
                prep = at,
                check = { a, _ -> a.ran("od") && Regex("""d\s+e\s+x""").containsMatchIn(a.output) },
                hint1 = "`unzip -p` prints one file from the zip; then `head -c 8`; then `od -c`.", hint2 = "`unzip -p \$APK classes.dex | head -c 8 | od -c`",
                solution = "unzip -p \$APK classes.dex | head -c 8 | od -c", solutionWhy = "`d e x \\n 0 3 9`: \"dex\", then the format's version.",
                fact = "A zip starts with `PK` (for Phil Katz, who invented it), a PDF with `%PDF`. The ending of a name can lie; the first bytes rarely do.",
                how = "od shows d e x",
            ),
            Task(
                id = "t13_6", title = "Its fingerprint",
                chunks = listOf("A **checksum** is a fingerprint of a file: change one bit, and it is completely different.\n\n      `sha256sum \$APK`"),
                command = "sha256sum \$APK", chips = listOf("sha256sum", "\$APK"), keys = listOf("$"),
                prep = at,
                check = { a, _ -> a.ran("sha256sum") && Regex("""\b[0-9a-f]{64}\b""").containsMatchIn(a.output) },
                hint1 = "`sha256sum` and the variable.", hint2 = "`sha256sum \$APK`",
                solution = "sha256sum \$APK", solutionWhy = "64 hex digits: this APK's fingerprint.",
                fact = "A download page can publish this number; if yours matches, you have the very same file. Android checks the app's signature in a similar way before every update.",
                how = "sha256sum printed a fingerprint",
            ),
            Task(
                id = "t13_7", title = "The app's folder",
                chunks = listOf("`dirname` cuts the file name off a path. Look at the folder:\n\n      `ls -l \$(dirname \$APK)`"),
                command = "ls -l \$(dirname \$APK)", chips = listOf("ls", "-l", "\$(dirname \$APK)"), keys = listOf("-", "$", "(", ")"),
                prep = at,
                check = { a, _ -> a.ran("ls") && a.outHas(apkName()) },
                hint1 = "`ls -l`, then `\$(dirname \$APK)` from Level 9's `\$( )`.", hint2 = "`ls -l \$(dirname \$APK)`",
                solution = "ls -l \$(dirname \$APK)", solutionWhy = "base.apk and a lib folder, all owned by system.",
                fact = "Your app's code and your app's data live apart: code in /data/app (the system's), data in your room (yours).",
                how = "ls listed the APK's folder",
            ),
            Task(
                id = "t13_8", title = "Change yourself?",
                chunks = listOf("Try to add something to your own app. **A refusal is the right result.**\n\n      `echo x >> \$APK`"),
                command = "echo x >> \$APK", chips = listOf("echo", "x", ">>", "\$APK"), keys = listOf(">", "$"),
                prep = at,
                check = { a, _ -> a.ran("echo") && a.status != 0 },
                hint1 = "`echo x`, `>>`, the variable.", hint2 = "`echo x >> \$APK`",
                solution = "echo x >> \$APK", solutionWhy = "Permission denied: an app cannot change its own code.",
                fact = "Together with Level 8's rule (no running what you wrote), this means: an app runs only the code that was installed and checked. Changing it takes an update, signed by the same author.",
                how = "the write was refused",
            ),
            Task(
                id = "t13_9", title = "The terminal's helper",
                chunks = listOf("Besides dex code, an app may carry machine code: `.so` libraries in its `lib/` folder.\n\n      `unzip -l \$APK | grep lib/`"),
                command = "unzip -l \$APK | grep lib/", chips = listOf("unzip", "-l", "\$APK", "|", "grep", "lib/"), keys = listOf("-", "$", "|", "/"),
                prep = at,
                check = { a, _ -> a.ran("grep") && a.outHas(".so") },
                hint1 = "The list, then `grep lib/`.", hint2 = "`unzip -l \$APK | grep lib/`",
                solution = "unzip -l \$APK | grep lib/", solutionWhy = "`libapprentice-pty.so`: this game's own piece of machine code.",
                fact = "It makes the pseudo-terminal your shell runs in: the one thing Kotlin could not do. It is allowed to run because it came with the installed app.",
                how = "grep found .so libraries",
            ),
            Task(
                id = "t13_10", title = "The APK's label",
                chunks = listOf("And SELinux's view of it, from Level 10:\n\n      `ls -Z \$APK`"),
                command = "ls -Z \$APK", chips = listOf("ls", "-Z", "\$APK"), keys = listOf("-", "$"),
                drive = "device",
                prep = at,
                check = { a, _ -> a.ran("ls") && a.outHas("apk_data_file") },
                hint1 = "`ls -Z` and the variable.", hint2 = "`ls -Z \$APK`",
                solution = "ls -Z \$APK", solutionWhy = "`apk_data_file`: the label of installed app code.",
                fact = "Three locks on one file: owner system, read-only rights, and a label your app may only read and run.",
                how = "ls -Z shows apk_data_file",
            ),
        ),
        outro = listOf(
            "Your app is a signed zip: a manifest, dex code, a native library. Installed, it belongs to the system, and the app itself cannot change it.",
            "Next: privacy. What your app cannot reach, by design.",
        ),
        explore = listOf(
            "Settings > Apps > (an app) > App details in store, or the version at the bottom of its page: that is the APK you just took apart.",
        ),
    )
}
