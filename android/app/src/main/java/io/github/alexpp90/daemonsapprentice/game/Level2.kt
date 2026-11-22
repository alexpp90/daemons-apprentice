package io.github.alexpp90.daemonsapprentice.game

import java.io.File

/** Level 2, "Finding Your Way": a small town of folders in the playground. */
object Level2 {

    private fun town(t: TaskContext) = File(t.dirs.playground, "town")

    private fun build(t: TaskContext) {
        val town = town(t)
        File(town, "sign.txt").ensureText("Welcome to Shell Town!\nThree places to visit: park, library, station.\n")
        File(town, "park/sign.txt").ensureText("The park. A pond lies further in: cd pond\n")
        File(town, "park/pond/sign.txt").ensureText("The pond. Ducks. Very quiet. The way back is ..\n")
        File(town, "library/sign.txt").ensureText("The library. Shhh.\n")
        File(town, "library/books.txt").ensureText("The C Programming Language (1978)\nJust for Fun (Linus Torvalds, 2001)\n")
        File(town, "station/sign.txt").ensureText("The station. Trains to everywhere, but only with cd.\n")
        File(town, ".secret-passage").ensureText("You found the hidden passage! Names starting with a dot are hidden.\nFLAG{hidden_in_plain_sight}\n")
    }

    private fun at(path: String): suspend TaskContext.() -> Unit = {
        build(this)
        goTo(if (path.isEmpty()) dirs.playground else File(town(this), path).let { if (path == ".") town(this) else it })
    }

    val level = Level(
        number = 2,
        title = "Finding Your Way",
        intro = listOf(
            "${Pid1.RUNNING}  Folders inside folders inside folders. Today you walk through them, without a single tap.",
            "I built a small town in your playground. Signs everywhere. Let's go.",
        ),
        tasks = listOf(
            Task(
                id = "t2_1", title = "Walk in",
                chunks = listOf("`cd` means **change directory**: walk into a folder. There is a folder called `town` here. Walk in:\n\n      `cd town`"),
                command = "cd town", parts = listOf("cd" to "change directory: walk into", "town" to "the folder"),
                chips = listOf("cd", "town"),
                prep = at(""),
                check = { a, _ -> a.folder.endsWith("/playground/town") },
                hint1 = "`cd`, a space, `town`.", hint2 = "`cd town`",
                solution = "cd town", solutionWhy = "`cd town` walks into the folder `town`.",
                fact = "`cd` prints nothing when it works. Silence means success in the terminal.",
                how = "the shell's folder is playground/town",
            ),
            Task(
                id = "t2_2", title = "Look around",
                chunks = listOf("Where can you go from here? List the folder:\n\n      `ls`"),
                command = "ls", chips = listOf("ls"),
                prep = at("."),
                check = { a, _ -> a.ran("ls") && a.outHas("park") },
                hint1 = "Two letters, small.", hint2 = "`ls`",
                solution = "ls", solutionWhy = "`ls` lists the folder you stand in: now that is the town.",
                fact = "Folders and files look alike in a list. `ls -F` would put a / behind every folder.",
                how = "ls listed the town (park is in it)",
            ),
            Task(
                id = "t2_3", title = "Read the sign",
                chunks = listOf("There is a sign. Read it:\n\n      `cat sign.txt`"),
                command = "cat sign.txt", chips = listOf("cat", "sign.txt"),
                prep = at("."),
                check = { a, fb ->
                    if (a.outHas("Welcome to Shell Town")) true
                    else { if (a.ran("cat") && a.status != 0) fb.say("Are you in the town? `pwd` tells you. The sign is in `town`."); false }
                },
                hint1 = "`cat`, then the file name.", hint2 = "`cat sign.txt`",
                solution = "cat sign.txt", solutionWhy = "`cat` shows the sign of the folder you are in.",
                fact = "Every place in this town has its own sign.txt. Same name, different folders: that's fine.",
                how = "the town's sign was shown",
            ),
            Task(
                id = "t2_4", title = "Deeper in",
                chunks = listOf("You can walk through several folders at once. The `/` separates them:\n\n      `cd park/pond`"),
                command = "cd park/pond", parts = listOf("park/pond" to "into park, then into pond"),
                chips = listOf("cd", "park/pond"), keys = listOf("/"),
                prep = at("."),
                check = { a, _ -> a.folder.endsWith("/town/park/pond") },
                hint1 = "`cd park/pond`: the slash is on the key bar.", hint2 = "`cd park/pond`",
                solution = "cd park/pond", solutionWhy = "`park/pond` is a path: park first, then pond inside it.",
                fact = "A list of folders joined by / is called a **path**. `pwd` shows the whole path from the very top.",
                how = "the shell's folder is town/park/pond",
            ),
            Task(
                id = "t2_5", title = "One step back",
                chunks = listOf("`..` (two dots) always means **the folder above**. Go one step back:\n\n      `cd ..`"),
                command = "cd ..", chips = listOf("cd", ".."),
                prep = at("park/pond"),
                check = { a, fb ->
                    if (a.folder.endsWith("/town/park")) true
                    else { if (a.line.contains("cd..")) fb.say("A space between `cd` and `..`."); false }
                },
                hint1 = "`cd`, a space, two dots.", hint2 = "`cd ..`",
                solution = "cd ..", solutionWhy = "`..` is the folder above the one you are in.",
                fact = "`cd ../..` would go up two at once.",
                how = "the shell's folder is town/park",
            ),
            Task(
                id = "t2_6", title = "The lazy way",
                chunks = listOf(
                    "You are in the park. The library is next to it: up one (`..`), then into `library`. Together: `../library`.",
                    "Don't type it all. Type `cd ../li` and tap **Tab**: the terminal finishes the name. Then Enter.\n\n      `cd ../library`",
                ),
                command = "cd ../library", parts = listOf("../library" to "up one, then into library"),
                chips = listOf("cd", "../library"), keys = listOf("/"),
                prep = at("park"),
                check = { a, _ -> a.folder.endsWith("/town/library") },
                hint1 = "Type `cd ../li`, then Tab.", hint2 = "`cd ../library`",
                solution = "cd ../library", solutionWhy = "`../library`: up to the town, then into the library.",
                fact = "Tab saves typing and typos. If Tab does nothing, the start fits no name, or more than one: Tab shows them.",
                how = "the shell's folder is town/library",
                drive = "tab:cd ../li",
            ),
            Task(
                id = "t2_7", title = "Beam me home",
                chunks = listOf("`~` (the tilde) is your **home** folder. From anywhere, `cd ~` brings you back:\n\n      `cd ~`"),
                command = "cd ~", chips = listOf("cd", "~"), keys = listOf("~"),
                prep = at("library"),
                check = { a, _ -> a.folder == a.home.absolutePath },
                hint1 = "`cd`, a space, `~` (on the key bar).", hint2 = "`cd ~`",
                solution = "cd ~", solutionWhy = "`~` stands for your home folder.",
                fact = "In this app your home is inside the app's room. On a laptop it is your own folder, like /home/you. `cd` alone also goes home.",
                how = "the shell's folder is the home folder",
            ),
            Task(
                id = "t2_8", title = "Hidden things",
                card = "Names that start with a dot are **hidden**: `ls` skips them. Settings and secrets like to hide that way.",
                chunks = listOf("I put you back in the town. Show everything, hidden things too:\n\n      `ls -a`      (-a: all)"),
                command = "ls -a", chips = listOf("ls", "-a"), keys = listOf("-"),
                prep = at("."),
                check = { a, fb ->
                    if (a.ran("ls") && a.outHas(".secret-passage")) true
                    else { if (a.ran("ls") && !a.folder.endsWith("/town")) fb.say("Do it in the town: `cd ~/playground/town`, then `ls -a`."); false }
                },
                hint1 = "`ls`, a space, `-a`.", hint2 = "`ls -a`",
                solution = "ls -a", solutionWhy = "`-a` makes `ls` show all names, the hidden ones too.",
                fact = "`.` is the folder itself and `..` the one above: they show up in every `ls -a`.",
                how = "ls showed .secret-passage",
            ),
            Task(
                id = "t2_9", title = "The secret passage",
                chunks = listOf("There it is: `.secret-passage`. Read it:\n\n      `cat .secret-passage`"),
                command = "cat .secret-passage", chips = listOf("cat", ".secret-passage"),
                prep = at("."),
                check = { a, _ -> a.outHas("FLAG{") },
                hint1 = "`cat`, then the name with its dot. Tab helps: `cat .se` and Tab.", hint2 = "`cat .secret-passage`",
                solution = "cat .secret-passage", solutionWhy = "`cat` shows the hidden file; the dot is part of its name.",
                fact = "That is a **flag**: proof you found something hidden. There are more in later levels.",
                how = "the output shows the flag",
                flag = "hidden_in_plain_sight",
            ),
        ),
        outro = listOf(
            "You walked into folders, looked around, went deeper, came back, let Tab do the typing, went home with `~` and found a hidden passage.",
            "Paths are how programs, and AI agents, point at files. If you can read `../library`, you know where a command will act.",
        ),
        explore = listOf("Open your phone's Files app and walk into a few folders. Every tap is a `cd`, every folder you see an `ls`."),
    )
}
