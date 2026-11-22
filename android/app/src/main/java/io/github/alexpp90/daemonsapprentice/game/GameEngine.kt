package io.github.alexpp90.daemonsapprentice.game

import io.github.alexpp90.daemonsapprentice.engine.Guard
import io.github.alexpp90.daemonsapprentice.engine.Normalizer
import io.github.alexpp90.daemonsapprentice.engine.SaveFile
import io.github.alexpp90.daemonsapprentice.engine.Shell
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import kotlin.random.Random

/** A line of the transcript (ANDROID_CONCEPT "The screen"). */
sealed interface Line {
    val id: Long
    data class Text(override val id: Long, val text: String, val kind: Kind = Kind.Normal) : Line
    data class Command(override val id: Long, val text: String, val continuation: Boolean = false, val input: Boolean = false) : Line
    data class Output(override val id: Long, val text: String, val failed: Boolean = false) : Line
    data class Art(override val id: Long, val text: String) : Line
    enum class Kind { Normal, Card, Title, Pid1, Fact, Note, Warn, Help }
}

enum class Wait {
    /** The player reads; Next (or Enter on an empty line) goes on. */
    Continue,
    /** The game waits for a command. */
    Prompt,
    /** The shell waits for the rest of an unfinished command (`>`). */
    More,
    /** A command is running: Ctrl-C stops it; typed lines go to it. */
    Busy,
    /** The game's editor is open (`edit file`). */
    Editing,
    /** The level (in the slice: the game) is finished. */
    Done,
    Starting,
}

data class GameState(
    val level: Int = 0,
    val levelTitle: String = "",
    val task: Int = 0,
    val taskCount: Int = 0,
    val taskTitle: String = "",
    /** Title and command, for the pinned line while the keyboard is open. */
    val pinned: String = "",
    /** The whole task text, for the side pane on wide screens. */
    val taskText: String = "",
    val parts: List<Pair<String, String>> = emptyList(),
    val transcript: List<Line> = emptyList(),
    val wait: Wait = Wait.Starting,
    val chips: List<String> = emptyList(),
    val showChips: Boolean = false,
    /** The player asked for a hint in this task (with a hardware keyboard, chips show only then). */
    val hinted: Boolean = false,
    val keys: List<String> = emptyList(),
    val folder: String = "",
    val levels: List<LevelInfo> = emptyList(),
)

/** A level as the menu shows it. */
data class LevelInfo(val number: Int, val title: String, val done: Boolean, val current: Boolean)

sealed interface UiEvent {
    data object OpenFilePicker : UiEvent
    /** `edit file`: the editor screen, with the file's text; it answers with [GameEngine.editorClosed]. */
    data class OpenEditor(val path: String, val name: String, val text: String) : UiEvent
    data object OpenMenu : UiEvent
    data object Quit : UiEvent
}

class GameEngine(
    private val dirs: GameDirs,
    private val shell: Shell,
    private val levels: List<Level> = Levels.all,
    private val random: Random = Random.Default,
) {
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events

    private val save = SaveFile(dirs.save)
    private val guard = Guard(dirs.game.absolutePath, dirs.save.absolutePath) { shell.ownPids }
    private var nextId = 1L
    private var pages = ArrayDeque<List<Line>>()
    private var afterPages: (suspend () -> Unit)? = null
    private var levelIndex = 0
    private var taskIndex = 0
    private var hintLevel = 0
    private var misses = 0
    private var moreLines = mutableListOf<String>()
    private var pickerOpen = false
    private class EditTarget(val file: File, val name: String, val line: String)
    private var editing: EditTarget? = null

    private val level get() = levels[levelIndex]
    private val task get() = level.tasks[taskIndex]

    private fun id() = nextId++
    private fun text(t: String, k: Line.Kind = Line.Kind.Normal) = Line.Text(id(), t, k)

    private fun append(vararg lines: Line) = _state.update { it.copy(transcript = it.transcript + lines) }
    private fun setWait(w: Wait) = _state.update { it.copy(wait = w, folder = shell.folder) }
    private fun clear() = _state.update { it.copy(transcript = emptyList()) }

    // ----------------------------------------------------------------- flow

    private fun saved(key: String) = save[key]?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
    private fun addTo(key: String, v: String) { save[key] = (saved(key) + v).distinct().joinToString(",") }
    private fun isDone(l: Level) = l.number.toString() in saved("done")

    suspend fun start() {
        save.load()
        // A save file from the first slice said intro=seen for Level 0.
        if (save["intro"] == "seen") save["intro"] = "0"
        levelIndex = levels.indexOfFirst { it.number == save.int("level", 0) }.coerceAtLeast(0)
        taskIndex = save.int("task", 0).coerceIn(0, level.tasks.size - 1)
        if (isDone(level) && taskIndex == level.tasks.lastIndex) { levelComplete(); return }
        enterLevel()
    }

    /** The level's intro (once), then its current task. */
    private suspend fun enterLevel() {
        publishLevels()
        if (level.number.toString() !in saved("intro") && taskIndex == 0) {
            updateTaskHeader()
            clear()
            showPages(level.intro.map { introPage(it) }) {
                addTo("intro", level.number.toString()); store()
                beginTask()
            }
        } else beginTask()
    }

    private fun publishLevels() {
        val infos = levels.map { LevelInfo(it.number, it.title, isDone(it), it == level) }
        _state.update { it.copy(levels = infos) }
    }

    /** Menu: play this level (from its first task; its intro only once). */
    suspend fun jumpTo(number: Int) {
        val i = levels.indexOfFirst { it.number == number }
        if (i < 0 || _state.value.wait == Wait.Busy) return
        levelIndex = i; taskIndex = 0
        store()
        enterLevel()
    }

    /** A page of Pid1's: its ASCII art as art, the words as text. */
    private fun introPage(s: String): List<Line> =
        if (s.startsWith(Pid1.ghost)) listOf(Line.Art(id(), Pid1.ghost), text(s.removePrefix(Pid1.ghost).trim(), Line.Kind.Pid1))
        else listOf(text(s, Line.Kind.Pid1))

    private fun store() {
        save["version"] = "1"
        save["level"] = level.number.toString()
        save["task"] = taskIndex.toString()
        save.store()
    }

    /** Shows the pages one after another (Next between them), then runs [then]. */
    private suspend fun showPages(list: List<List<Line>>, then: suspend () -> Unit) {
        pages = ArrayDeque(list)
        afterPages = then
        val first = pages.removeFirstOrNull()
        if (first == null) { afterPages = null; then(); return }
        append(*first.toTypedArray())
        setWait(Wait.Continue)
    }

    suspend fun onContinue() {
        if (_state.value.wait != Wait.Continue) return
        val next = pages.removeFirstOrNull()
        if (next != null) { append(*next.toTypedArray()); return }
        val then = afterPages
        afterPages = null
        then?.invoke()
    }

    private fun updateTaskHeader() {
        val t = task
        _state.update {
            it.copy(
                level = level.number, levelTitle = level.title, task = taskIndex + 1,
                taskCount = level.tasks.size, taskTitle = t.title,
                pinned = "**${t.title}**: `${t.command}`",
                taskText = (listOfNotNull(t.card) + t.chunks).joinToString("\n\n"),
                parts = t.parts, chips = chipsFor(t), keys = t.keys,
                showChips = level.number <= 1, hinted = false, folder = shell.folder,
            )
        }
    }

    private fun chipsFor(t: Task): List<String> {
        val all = (t.solve?.let { ChipWords.split(it(dirs)).distinct() } ?: t.chips) + t.decoys
        return if (t.decoys.isEmpty()) all else all.shuffled(random)
    }

    private suspend fun beginTask() {
        hintLevel = 0; misses = 0; moreLines.clear()
        task.prep(TaskContext(dirs, shell))
        clear()
        updateTaskHeader()
        val t = task
        val chunkPages = t.chunks.mapIndexed { i, c ->
            val lines = mutableListOf<Line>(text(c))
            if (i == t.chunks.lastIndex && t.parts.isNotEmpty())
                lines += text(t.parts.joinToString("\n") { (p, w) -> "  `$p`   $w" }, Line.Kind.Card)
            lines.toList()
        }
        val all = listOfNotNull(t.card?.let { listOf<Line>(text(it, Line.Kind.Card)) }) + chunkPages
        // No Next after the last chunk: the prompt follows directly.
        val newKeys = t.keys.filter { it in KeyHelp.where && it !in explainedKeys }
        explainedKeys += newKeys
        showPages(all.dropLast(1)) {
            append(*all.last().toTypedArray())
            if (newKeys.isNotEmpty()) append(text(KeyHelp.card(newKeys), Line.Kind.Help))
            setWait(Wait.Prompt)
        }
    }

    private val explainedKeys = mutableSetOf<String>()

    private suspend fun solved() {
        append(text(Praise.next(random), Line.Kind.Pid1), text(task.fact, Line.Kind.Fact))
        task.flag?.let { f ->
            addTo("flags", f)
            append(text("${Pid1.CHEER}  Flag found! You have ${saved("flags").size}. They are in your cheatsheet.", Line.Kind.Pid1))
        }
        val last = taskIndex == level.tasks.lastIndex
        if (!last) { taskIndex++; store() } else store()
        _state.update { it.copy(showChips = level.number <= 1) }
        setWait(Wait.Continue)
        pages = ArrayDeque()
        afterPages = { if (last) levelOutro() else beginTask() }
    }

    private suspend fun levelOutro() {
        clear()
        val p = level.outro.map { listOf<Line>(text(it)) } +
            listOf(listOf(text("**Explore on your phone**", Line.Kind.Title)) + level.explore.map { text(it, Line.Kind.Note) })
        showPages(p) {
            addTo("done", level.number.toString())
            store()
            levelComplete()
        }
    }

    private suspend fun levelComplete() {
        clear()
        publishLevels()
        val art = Pid1.levelDone[level.number % Pid1.levelDone.size].joinToString("\n")
        append(Line.Art(id(), art), text("**Level ${level.number} complete: ${level.title}!**", Line.Kind.Title))
        _state.update { it.copy(task = it.taskCount, pinned = "**Level ${level.number} complete**", chips = emptyList(), showChips = false) }
        val next = levels.getOrNull(levelIndex + 1)
        if (next == null) {
            append(text("That was the last level of this build. More comes with the next one; your progress is saved.", Line.Kind.Note))
            setWait(Wait.Done)
            return
        }
        append(text("Next: **Level ${next.number}, ${next.title}.**", Line.Kind.Note))
        setWait(Wait.Continue)
        pages = ArrayDeque()
        afterPages = {
            levelIndex++; taskIndex = 0
            store()
            enterLevel()
        }
    }

    // ------------------------------------------------------------- commands

    /** Enter, or the ⏎ key: runs the line, or goes on when the game waits for Next. */
    suspend fun submit(raw: String) {
        val w = _state.value.wait
        if (w == Wait.Continue) { if (raw.isBlank()) onContinue() else { append(text("Tap **Next** (or press Enter on an empty line) to go on.", Line.Kind.Note)) }; return }
        if (w == Wait.Busy) {
            append(Line.Command(id(), raw, input = true))
            shell.sendInput(raw)
            return
        }
        if (w != Wait.Prompt && w != Wait.More) return
        if (raw.contains('\n')) {
            append(text("That was several lines at once, probably pasted. Nothing ran. Type one command, then press Enter.", Line.Kind.Warn))
            return
        }
        if (raw.isBlank() && w == Wait.Prompt) return
        val n = Normalizer.normalize(raw)
        val line = n.line
        if (w == Wait.More) {
            // The guard looks at the whole command, not only at its last line.
            val v = guard.check((moreLines + raw).joinToString("\n"), shell.folder, dirs.home.absolutePath, task.allowSave)
            if (!v.allowed) {
                append(Line.Command(id(), raw, continuation = true), text("The game stopped this one: ${v.reason}", Line.Kind.Warn))
                moreLines = mutableListOf()
                setWait(Wait.Busy)
                shell.cancelInput()
                setWait(Wait.Prompt)
                return
            }
            continueCommand(raw); return
        }
        append(Line.Command(id(), line))
        if (n.changes.isNotEmpty())
            append(text("Your keyboard changed something; fixed: ${n.changes.joinToString("; ")}.", Line.Kind.Note))
        val first = line.substringBefore(' ')
        if (first == "edit" && line.trim() != first) { openEditor(line.trim().removePrefix("edit").trim(), line); return }
        if (first in GAME_COMMANDS && line.trim() == first) { gameCommand(first); return }
        if (first == "exit" || first == "logout") { gameCommand("quit"); return }
        val verdict = guard.check(line, shell.folder, dirs.home.absolutePath, task.allowSave)
        if (!verdict.allowed) {
            append(text("The game stopped this one: ${verdict.reason}", Line.Kind.Warn))
            return
        }
        runShell(line)
    }

    private suspend fun continueCommand(raw: String) {
        append(Line.Command(id(), raw, continuation = true))
        moreLines += raw
        runShell(raw, continuation = true)
    }

    private suspend fun runShell(line: String, continuation: Boolean = false) {
        if (!continuation) moreLines = mutableListOf(line)
        setWait(Wait.Busy)
        val outId = id()
        var shown = ""
        append(Line.Output(outId, ""))
        val r = shell.run(line) { chunk ->
            shown += chunk
            val now = shown
            _state.update { s -> s.copy(transcript = s.transcript.map { if (it.id == outId) Line.Output(outId, now) else it }) }
        }
        val finalText = r.output.ifEmpty { shown }.trimEnd('\n')
        _state.update { s ->
            s.copy(transcript = s.transcript.mapNotNull {
                if (it.id != outId) it else if (finalText.isEmpty()) null else Line.Output(outId, finalText, r.status != 0)
            })
        }
        if (r.needsMore) {
            if (!continuation) append(text("The shell waits for the rest of the command (that is its `>` prompt). Type the rest, or Ctrl-C to start over.", Line.Kind.Note))
            setWait(Wait.More)
            return
        }
        val full = moreLines.joinToString("\n")
        moreLines = mutableListOf()
        // A task that lets the player write the save file: the game reads it again (and believes it).
        if (task.allowSave && full.contains(".apprentice-save")) save.load()
        val attempt = Attempt(
            line = full, commands = guard.commands(full), words = guard.words(full),
            output = finalText, status = r.status, folder = r.folder, interrupted = r.interrupted,
            pg = dirs.playground, home = dirs.home,
        )
        evaluate(attempt)
    }

    private suspend fun evaluate(a: Attempt) {
        if (_state.value.wait == Wait.Done) return
        val fb = Feedback()
        val ok = task.check(a, fb)
        if (ok) { solved(); return }
        misses++
        val msg = fb.message ?: when {
            a.interrupted || a.status == 130 -> "Stopped with Ctrl-C. Nothing broke."
            a.status == 127 -> "`${a.commands.firstOrNull() ?: a.line}`: the phone knows no command with that name. Check the spelling."
            a.commands.isNotEmpty() && a.commands.all { it in task.lookAround } && a.status == 0 ->
                "Looking around is always fine. The task is still: `${task.command}`"
            a.status != 0 -> "That gave an error (above). Read it, then try again."
            else -> "That ran, but it is not what this task asks for. The task: `${task.command}`"
        }
        append(text(msg, Line.Kind.Note))
        if (misses >= 2 && hintLevel == 0) append(text("Stuck? Type `hint`.", Line.Kind.Note))
        setWait(Wait.Prompt)
    }

    private suspend fun gameCommand(c: String) {
        when (c) {
            "help" -> append(text(HelpText.help, Line.Kind.Help))
            "hint" -> {
                hintLevel++
                when (hintLevel) {
                    1 -> append(text(task.hint1, Line.Kind.Note))
                    2 -> append(text(task.hint2, Line.Kind.Note))
                    else -> append(text("The solution: `${task.solutionNow(dirs)}`\n${task.solutionWhy}\nType it rather than copying it: your fingers learn, too.", Line.Kind.Note))
                }
                _state.update { it.copy(showChips = true, hinted = true) }
            }
            "explain" -> {
                task.card?.let { append(text(it, Line.Kind.Card)) }
                task.chunks.forEach { append(text(it)) }
            }
            "task" -> append(text(task.chunks.last()))
            "keys" -> append(text(HelpText.keys, Line.Kind.Help))
            "cheat" -> append(text(cheatsheet(), Line.Kind.Help))
            "skip" -> {
                append(text("Skipped. You can come back to it from the menu.", Line.Kind.Note))
                val last = taskIndex == level.tasks.lastIndex
                if (!last) taskIndex++
                store()
                if (last) levelOutro() else beginTask()
                return
            }
            "menu" -> _events.tryEmit(UiEvent.OpenMenu)
            "quit" -> { store(); _events.tryEmit(UiEvent.Quit) }
            "clear" -> clear()
            "edit" -> append(text("`edit` opens one file in the game's editor: `edit note.txt`.", Line.Kind.Note))
            "files" -> {
                pickerOpen = true
                _events.tryEmit(UiEvent.OpenFilePicker)
                return
            }
        }
        if (task.gameCommand == c && _state.value.wait == Wait.Prompt) { solved(); return }
        if (_state.value.wait != Wait.Continue) setWait(Wait.Prompt)
    }

    /** The app was away from the screen for [awayMs] (the player went to the home screen) and is back. */
    suspend fun cameBack(awayMs: Long) {
        if (task.event != "away" || _state.value.wait != Wait.Prompt) return
        if (awayMs >= 5_000) { append(text("Welcome back. You were away ${awayMs / 1000} seconds.", Line.Kind.Note)); solved() }
        else append(text("That was quick (${awayMs / 1000} s). Stay away about ten seconds, then come back.", Line.Kind.Note))
    }

    /** The file picker came back (the player pressed Back or picked something). */
    suspend fun filePickerClosed() {
        if (!pickerOpen) return
        pickerOpen = false
        if (task.gameCommand == "files" && _state.value.wait == Wait.Prompt) solved()
        else append(text("Back from the file picker.", Line.Kind.Note))
    }

    suspend fun interrupt() {
        when (_state.value.wait) {
            Wait.Busy -> shell.interrupt()
            Wait.More -> {
                // Ctrl-C at the `>` prompt: the shell forgets the unfinished command.
                val partial = moreLines.joinToString("\n")
                moreLines = mutableListOf()
                setWait(Wait.Busy)
                val r = shell.cancelInput()
                evaluate(Attempt(
                    line = partial, commands = guard.commands(partial), words = guard.words(partial),
                    output = "", status = 130, folder = r.folder, interrupted = true, pg = dirs.playground, home = dirs.home,
                ))
            }
            else -> {}
        }
    }

    // ------------------------------------------------------------- editor

    /** `edit file`: opens the game's editor (a phone has no nano). Only files in the player's home. */
    private fun openEditor(arg: String, line: String) {
        val home = dirs.home.absolutePath
        val path = guard.resolve(arg, shell.folder.ifEmpty { dirs.playground.absolutePath }, home)
        val f = File(path)
        val problem = when {
            arg.contains(' ') -> "`edit` opens one file at a time: `edit note.txt`."
            !path.startsWith("$home/") -> "The editor opens files in your home folder and below: that is your app's room."
            f.absolutePath == dirs.save.absolutePath -> "That is your save file. Look at it with `cat`; the editor leaves it alone."
            f.isDirectory -> "`$arg` is a folder. The editor opens files."
            else -> null
        }
        if (problem != null) { append(text(problem, Line.Kind.Note)); setWait(Wait.Prompt); return }
        editing = EditTarget(f, arg, line)
        setWait(Wait.Editing)
        _events.tryEmit(UiEvent.OpenEditor(f.absolutePath, arg, if (f.isFile) f.readText() else ""))
    }

    /** The editor closed: [text] is saved (null: nothing to save), then the task looks at the file. */
    suspend fun editorClosed(text: String?) {
        val e = editing ?: return
        editing = null
        if (text != null) { e.file.parentFile?.mkdirs(); e.file.writeText(text) }
        append(text(if (text != null) "Saved `${e.name}`." else "Closed `${e.name}`.", Line.Kind.Note))
        setWait(Wait.Prompt)
        evaluate(Attempt(
            line = e.line, commands = listOf("edit"), words = guard.words(e.line), output = "", status = 0,
            folder = shell.folder, gameCommand = "edit", pg = dirs.playground, home = dirs.home,
        ))
    }

    private fun cheatsheet(): String {
        val done = save["done"]?.split(',') ?: emptyList()
        val entries = levels.flatMap { lv ->
            val upTo = if (lv.number.toString() in done) lv.tasks.size else if (lv == level) taskIndex else 0
            lv.tasks.take(upTo).map { "  `${it.solution}`   ${it.title}" }
        }
        val flags = saved("flags")
        val flagLine = (if (flags.isEmpty()) "" else "\n**Flags found:** ${flags.size}") +
            (save["badge"]?.let { "\n**Badge:** $it" } ?: "")
        return if (entries.isEmpty()) "Your cheatsheet is still empty. Solve a task and its command lands here."
        else "**Your cheatsheet**\n" + entries.joinToString("\n") + flagLine
    }

    // ------------------------------------------------------------- Tab

    data class Completion(val text: String, val options: List<String>)

    /** Tab: completes the last word (a command name or a file in the current folder). */
    fun complete(current: String): Completion {
        val start = current.lastIndexOf(' ') + 1
        val word = current.substring(start)
        val isFirst = current.substring(0, start).isBlank()
        val candidates: List<String> = if (isFirst) {
            (GAME_COMMANDS + COMMON_COMMANDS).filter { it.startsWith(word) }.distinct().sorted()
        } else {
            val slash = word.lastIndexOf('/')
            val dirPart = if (slash >= 0) word.substring(0, slash + 1) else ""
            val namePart = word.substring(slash + 1)
            val base = when {
                dirPart.startsWith("/") -> File(dirPart)
                dirPart.startsWith("~/") -> File(dirs.home, dirPart.removePrefix("~/"))
                else -> File(shell.folder.ifEmpty { dirs.playground.absolutePath }, dirPart)
            }
            (base.listFiles()?.toList() ?: emptyList())
                .filter { it.name.startsWith(namePart) && (namePart.startsWith(".") || !it.name.startsWith(".")) }
                .map { dirPart + it.name + if (it.isDirectory) "/" else "" }
                .sorted()
        }
        if (candidates.isEmpty()) return Completion(current, emptyList())
        val prefix = candidates.reduce { a, b -> a.commonPrefixWith(b) }
        val head = current.substring(0, start)
        return if (candidates.size == 1) {
            val c = candidates[0]
            Completion(head + c + if (c.endsWith("/")) "" else " ", emptyList())
        } else Completion(head + prefix, candidates)
    }

    /** Several names fit after Tab: show them, like a shell does. */
    fun showOptions(options: List<String>) {
        append(text("Tab: " + options.take(30).joinToString("   ") { "`$it`" }, Line.Kind.Note))
    }

    companion object {
        val GAME_COMMANDS = listOf("help", "hint", "explain", "task", "keys", "cheat", "skip", "menu", "quit", "clear", "files", "edit")
        val COMMON_COMMANDS = listOf(
            "echo", "ls", "pwd", "cat", "grep", "cd", "date", "whoami", "mkdir", "touch", "cp", "mv", "rm",
            "head", "tail", "wc", "sort", "uniq", "ps", "kill", "id", "uname", "getprop", "sleep", "history",
            "clear", "rmdir", "nproc", "free", "df", "base64", "env", "which", "chmod", "seq", "sh",
        )
    }
}
