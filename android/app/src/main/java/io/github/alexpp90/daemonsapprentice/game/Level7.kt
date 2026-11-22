package io.github.alexpp90.daemonsapprentice.game

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/** Level 7, "Every App Is a User": owners, rights, no root, and a save file that should not be trusted. */
object Level7 {

    private const val DIARY = "Dear diary, today the apprentice found a coin miner and stopped it.\n"
    private const val SECRET = "The password to Pid1's attic: penguin\n"
    private const val ENV = "# settings for a made-up app\nSERVER=example.org\nAPI_KEY=demo-12345-not-a-real-key\n"

    private fun dir(d: GameDirs) = File(d.playground, "vault")
    fun rights(f: File): String = runCatching { PosixFilePermissions.toString(Files.getPosixFilePermissions(f.toPath())) }.getOrDefault("")
    private fun setRights(f: File, r: String) { runCatching { Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString(r)) } }

    /** The vault, with diary and secret at the rights the task starts from. */
    private fun at(diary: String = "rw-r--r--", secret: String = "rw-r--r--"): suspend TaskContext.() -> Unit = {
        val d = dir(dirs)
        val s = File(d, "secret.txt")
        if (s.exists()) setRights(s, "rw-r--r--") // so that it can be rewritten
        File(d, "diary.txt").ensureText(DIARY)
        s.ensureText(SECRET)
        File(d, ".env").ensureText(ENV)
        if (rights(File(d, "diary.txt")) != diary) setRights(File(d, "diary.txt"), diary)
        if (rights(s) != secret) setRights(s, secret)
        goTo(d)
    }

    private fun vault(a: Attempt) = File(a.pg, "vault")

    val level = Level(
        number = 7,
        title = "Every App Is a User",
        intro = listOf(
            "${Pid1.INSPECTING}  On a computer, a **user** is a person with a login. On your phone it is different: every **app** is a user of its own.",
            "That is why you saw only your own processes. Today: who owns what, who may do what, and why nobody on your phone is all-powerful. We work in `vault`.",
        ),
        tasks = listOf(
            Task(
                id = "t7_1", title = "Who am I, really?",
                chunks = listOf("`id` shows your user number and your groups:\n\n      `id`"),
                command = "id", chips = listOf("id"),
                prep = at(),
                check = { a, _ -> a.ran("id") && a.outHas("uid=") },
                hint1 = "Two letters: i, d.", hint2 = "`id`",
                solution = "id", solutionWhy = "`id` prints uid (your user number), gid and the groups you are in.",
                fact = "The uid is this app's number, given when it was installed. Another app has another number; Android never gives the same one twice on a phone.",
                how = "id printed uid=",
            ),
            Task(
                id = "t7_2", title = "Who owns these files?",
                chunks = listOf("`-l` makes `ls` show the long version: rights, owner, size, date.\n\n      `ls -l`"),
                command = "ls -l", chips = listOf("ls", "-l"), keys = listOf("-"),
                prep = at(),
                check = { a, _ -> a.ran("ls") && "-l" in a.words && a.outHas("diary.txt") },
                hint1 = "`ls`, a space, `-l` (a small L).", hint2 = "`ls -l`",
                solution = "ls -l", solutionWhy = "Each line: rights, links, owner, group, size, date, name. The owner is this app's user.",
                fact = "`ls -ln` shows the owner as a number: the same uid as `id` printed.",
                how = "ls -l listed the vault",
            ),
            Task(
                id = "t7_3", title = "Next door",
                card = "Every app has a room like yours. The Settings app's room is `/data/data/com.android.settings`.",
                chunks = listOf("Try to look in. **A refusal is the right result.**\n\n      `ls /data/data/com.android.settings`"),
                command = "ls /data/data/com.android.settings", chips = listOf("ls", "/data/data/com.android.settings"), keys = listOf("/"),
                prep = at(),
                check = { a, _ -> a.ran("ls") && a.words.any { it.startsWith("/data/data/") } && a.status != 0 },
                hint1 = "`ls` and the long path, every part with a slash.", hint2 = "`ls /data/data/com.android.settings`",
                solution = "ls /data/data/com.android.settings", solutionWhy = "Another app's room: not yours, so it stays closed.",
                fact = "Older Android says Permission denied. Newer Android even pretends the room is not there: an app should not learn which other apps are installed.",
                how = "ls was refused",
            ),
            Task(
                id = "t7_4", title = "Lock your diary",
                card = "Rights in numbers: each digit is one kind of person, owner, group, others. 6 = read and write, 0 = nothing.",
                chunks = listOf("Only the owner may read and write it:\n\n      `chmod 600 diary.txt`"),
                command = "chmod 600 diary.txt", chips = listOf("chmod", "600", "diary.txt"),
                prep = at(),
                check = { a, _ -> rights(File(vault(a), "diary.txt")) == "rw-------" },
                hint1 = "`chmod`, the number `600`, the file.", hint2 = "`chmod 600 diary.txt`",
                solution = "chmod 600 diary.txt", solutionWhy = "6 for you (read, write), 0 for the group, 0 for everyone else.",
                fact = "On a phone the other apps cannot get in anyway: the room's walls stop them first. On a computer shared by people, this is what keeps a diary private.",
                how = "diary.txt has the rights rw-------",
            ),
            Task(
                id = "t7_5", title = "Check the lock",
                chunks = listOf("Look at its rights:\n\n      `ls -l diary.txt`"),
                command = "ls -l diary.txt", chips = listOf("ls", "-l", "diary.txt"), keys = listOf("-"),
                prep = at(diary = "rw-------"),
                check = { a, _ -> a.ran("ls") && a.outHas("-rw-------") },
                hint1 = "`ls -l` and the file name.", hint2 = "`ls -l diary.txt`",
                solution = "ls -l diary.txt", solutionWhy = "`-rw-------`: the first dash means file, then rw for you, nothing for the others.",
                fact = "Read the letters in threes: rw- (owner) --- (group) --- (others).",
                how = "ls -l shows -rw-------",
            ),
            Task(
                id = "t7_6", title = "Lock everyone out",
                chunks = listOf("Now a file nobody may read, not even you:\n\n      `chmod 000 secret.txt`"),
                command = "chmod 000 secret.txt", chips = listOf("chmod", "000", "secret.txt"),
                prep = at(diary = "rw-------"),
                check = { a, _ -> rights(File(vault(a), "secret.txt")) == "---------" },
                hint1 = "`chmod`, three zeros, the file.", hint2 = "`chmod 000 secret.txt`",
                solution = "chmod 000 secret.txt", solutionWhy = "Zero rights for everyone.",
                fact = "You are still the owner. That matters in a moment.",
                how = "secret.txt has no rights at all",
            ),
            Task(
                id = "t7_7", title = "Locked out",
                chunks = listOf("Try to read it. **A refusal is the right result.**\n\n      `cat secret.txt`"),
                command = "cat secret.txt", chips = listOf("cat", "secret.txt"),
                prep = at(diary = "rw-------", secret = "---------"),
                check = { a, _ -> a.ran("cat") && a.status != 0 && a.output.contains("denied", ignoreCase = true) },
                hint1 = "`cat` and the file.", hint2 = "`cat secret.txt`",
                solution = "cat secret.txt", solutionWhy = "Permission denied: the file's rights say no, even to you.",
                fact = "Rights apply to the owner, too. But the owner may always change them back.",
                how = "cat was refused",
            ),
            Task(
                id = "t7_8", title = "Let yourself in",
                card = "Rights in letters: `u` the owner (user), `+` add, `r` read.",
                chunks = listOf("Give the owner the right to read again:\n\n      `chmod u+r secret.txt`"),
                command = "chmod u+r secret.txt", chips = listOf("chmod", "u+r", "secret.txt"),
                prep = at(diary = "rw-------", secret = "---------"),
                check = { a, _ -> rights(File(vault(a), "secret.txt")).startsWith("r") },
                hint1 = "`chmod`, `u+r` stuck together, the file.", hint2 = "`chmod u+r secret.txt`",
                solution = "chmod u+r secret.txt", solutionWhy = "`u+r`: add read for the owner, change nothing else.",
                fact = "Letters change one right; numbers set all of them at once. `chmod go-w` would take writing away from group and others.",
                how = "the owner may read secret.txt again",
            ),
            Task(
                id = "t7_9", title = "The secret",
                chunks = listOf("Now read it:\n\n      `cat secret.txt`"),
                command = "cat secret.txt", chips = listOf("cat", "secret.txt"),
                prep = at(diary = "rw-------", secret = "r--------"),
                check = { a, _ -> a.ran("cat") && a.outHas("penguin") },
                hint1 = "`cat` and the file.", hint2 = "`cat secret.txt`",
                solution = "cat secret.txt", solutionWhy = "With the read right back, cat can open the file.",
                fact = "A penguin: the mascot of Linux. His name is Tux.",
                how = "cat showed the secret",
            ),
            Task(
                id = "t7_10", title = "Is there a boss?",
                card = "On a computer one user may do everything: **root**, the administrator. People become root with `su` or `sudo`.",
                chunks = listOf("Is there an `su` on this phone?\n\n      `which su`"),
                command = "which su", chips = listOf("which", "su"),
                prep = at(diary = "rw-------"),
                check = { a, _ -> a.ran("which") && "su" in a.words },
                hint1 = "`which` and `su`.", hint2 = "`which su`",
                solution = "which su", solutionWhy = "Nothing printed: there is no su for apps on a normal phone.",
                fact = "On a phone nobody is root, not even you. Android itself runs a few things as root; everything else, apps included, gets walls. (If a path came back, this phone was rooted.)",
                how = "which su ran",
            ),
            Task(
                id = "t7_11", title = "Hidden settings",
                chunks = listOf("Dot-files, from Level 2. Show them:\n\n      `ls -a`"),
                command = "ls -a", chips = listOf("ls", "-a"), keys = listOf("-"),
                prep = at(diary = "rw-------"),
                check = { a, _ -> a.ran("ls") && a.outHas(".env") },
                hint1 = "`ls` and `-a`.", hint2 = "`ls -a`",
                solution = "ls -a", solutionWhy = "`-a` shows all files, also the ones whose name starts with a dot.",
                fact = "`.env` files hold settings for programs. Often secrets, too.",
                how = "ls -a showed .env",
            ),
            Task(
                id = "t7_12", title = "A secret in plain text",
                chunks = listOf("Look inside:\n\n      `cat .env`"),
                command = "cat .env", chips = listOf("cat", ".env"),
                prep = at(diary = "rw-------"),
                check = { a, _ -> a.ran("cat") && a.outHas("API_KEY") },
                hint1 = "`cat` and `.env`, with the dot.", hint2 = "`cat .env`",
                solution = "cat .env", solutionWhy = "An API key, in plain text, for anyone who can read the file.",
                fact = "Keys like this land in public code all the time and get stolen within minutes. Before you share a folder, look for dot-files.",
                how = "cat showed the API key",
            ),
            Task(
                id = "t7_13", title = "Your save file",
                chunks = listOf("This game keeps your progress in a dot-file in your home folder:\n\n      `cat ~/.apprentice-save`"),
                command = "cat ~/.apprentice-save", chips = listOf("cat", "~/.apprentice-save"), keys = listOf("~", "/"),
                prep = at(diary = "rw-------"),
                check = { a, _ -> a.ran("cat") && a.outHas("level=") },
                hint1 = "`cat`, then `~/` and the name with its dot.", hint2 = "`cat ~/.apprentice-save`",
                solution = "cat ~/.apprentice-save", solutionWhy = "Your level, your task, the levels you have done, your flags: plain text.",
                fact = "It is in your app's room, so you, the app, may change it. Let's see whether the game notices.",
                how = "cat showed the save file",
            ),
            Task(
                id = "t7_14", title = "Never trust the client",
                card = "`>>` adds a line to the end of a file (one `>` would replace the whole file).",
                chunks = listOf("Give yourself a badge you never earned:\n\n      `echo badge=gold >> ~/.apprentice-save`"),
                command = "echo badge=gold >> ~/.apprentice-save",
                chips = listOf("echo", "badge=gold", ">>", "~/.apprentice-save"), keys = listOf(">", "~", "=", "/"),
                allowSave = true,
                prep = at(diary = "rw-------"),
                check = { a, _ -> File(a.home, ".apprentice-save").readText().contains("badge=gold") },
                hint1 = "`echo badge=gold`, then `>>`, then the save file.", hint2 = "`echo badge=gold >> ~/.apprentice-save`",
                solution = "echo badge=gold >> ~/.apprentice-save", solutionWhy = "The line badge=gold is now at the end of the save file.",
                fact = "The game believed it: type `cheat`, there is your gold badge. A game that keeps the score on your phone can be cheated like this. That is why online games keep it on their server: never trust the client.",
                how = "the save file says badge=gold",
            ),
        ),
        outro = listOf(
            "Every app is its own user, with its own room. Rights in numbers and letters; no root; secrets in dot-files.",
            "And a game that trusts its own save file can be cheated, by you. Next: you write files with an editor, and make one run.",
        ),
        explore = listOf(
            "Settings > Apps > (an app) > Storage: that is the app's room, seen from outside. Clear storage empties it.",
            "Settings > Security (or Privacy): a phone that is rooted often says so here, and some banking apps refuse to run on it.",
        ),
    )
}
