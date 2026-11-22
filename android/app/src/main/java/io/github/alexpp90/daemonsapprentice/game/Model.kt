package io.github.alexpp90.daemonsapprentice.game

import io.github.alexpp90.daemonsapprentice.engine.Shell
import java.io.File

/** Where things live in the app's private storage (ANDROID_CONCEPT "Folders"). */
class GameDirs(filesDir: File) {
    val home = File(filesDir, "home")
    val playground = File(home, "playground")
    val game = File(filesDir, "game")
    val save = File(home, ".apprentice-save")
    /** The PID of Level 6's background intruder. */
    val minerPid = File(game, "miner.pid")
    val rc = File(game, "shrc")
}

/** The app itself, as Level 13 shows it: its package name and its APK (set at start; tests set a stand-in). */
object AppInfo {
    @Volatile var pkg: String = "io.github.alexpp90.daemonsapprentice"
    @Volatile var apk: String = ""
}

/** One command line the player sent, and what came back. */
data class Attempt(
    val line: String,
    /** The first word of every command in the line (what actually ran). */
    val commands: List<String>,
    val words: List<String>,
    val output: String,
    val status: Int,
    val folder: String,
    /** Set when the line was one of the game's own commands. */
    val gameCommand: String? = null,
    val interrupted: Boolean = false,
    /** The playground and the home folder, for checks that look at files. */
    val pg: File = File("/"),
    val home: File = File("/"),
) {
    fun ran(c: String) = c in commands
    fun outHas(s: String) = output.contains(s)
    fun lines() = output.lines().map { it.trim() }.filter { it.isNotEmpty() }
}

/** The solution for this phone right now. */
fun Task.solutionNow(dirs: GameDirs) = solve?.invoke(dirs) ?: solution

/** Collects the specific feedback a check wants to give. */
class Feedback { var message: String? = null; fun say(m: String) { if (message == null) message = m } }

class TaskContext(val dirs: GameDirs, val shell: Shell)

class Task(
    val id: String,
    val title: String,
    /** Background before the task; the player reads it, then Next. */
    val card: String? = null,
    /** The task in pieces, each shown after Next. Markup: **bold**, `code`. */
    val chunks: List<String>,
    /** The command in the pinned line while the keyboard is open. */
    val command: String,
    /** The parts of the command and what each does. */
    val parts: List<Pair<String, String>> = emptyList(),
    /** Pieces to tap (R28); [decoys] are wrong pieces offered among them. */
    val chips: List<String> = emptyList(),
    val decoys: List<String> = emptyList(),
    /** Characters the key bar offers for this task. */
    val keys: List<String> = emptyList(),
    /** A game command that solves the task by itself (e.g. `help`). */
    val gameCommand: String? = null,
    val prep: suspend TaskContext.() -> Unit = {},
    val check: (Attempt, Feedback) -> Boolean,
    val hint1: String,
    val hint2: String,
    val solution: String,
    val solutionWhy: String,
    val fact: String,
    /** One line for the curriculum: how the check decides. */
    val how: String,
    /** A flag the task hands out (saved, counted in the cheatsheet). */
    val flag: String? = null,
    /**
     * For the test players: how this task is driven beyond typing. `ctrlc`: stop it with Ctrl-C;
     * `more-ctrlc`: Ctrl-C at the shell's `>` prompt; `lines`: the solution's lines one after
     * another; `edit-append:TEXT`: in the editor, add TEXT at the end; `device`: only on a phone.
     */
    val drive: String? = null,
    /** The solution as it is on this phone right now (e.g. with a PID); chips follow it. */
    val solve: ((GameDirs) -> String)? = null,
    /** This task may write the save file (Level 7 shows why a game must not trust it). */
    val allowSave: Boolean = false,
    /** Solved by something the player does outside the terminal: `away` = to the home screen and back. */
    val event: String? = null,
    /** Commands that only look around: never called wrong (R17). */
    val lookAround: Set<String> = setOf("ls", "pwd", "cat", "echo", "date", "whoami"),
)

class Level(
    val number: Int,
    val title: String,
    val intro: List<String>,
    val tasks: List<Task>,
    val outro: List<String>,
    val explore: List<String>,
)
