package io.github.alexpp90.daemonsapprentice.engine

/** What a command line gave back. */
data class ShellResult(
    val output: String,
    val status: Int,
    val folder: String,
    /** The shell waits for the rest of an unfinished command (it showed its `>` prompt). */
    val needsMore: Boolean = false,
    val interrupted: Boolean = false,
)

/** The game's shell. On a phone: [ShellSession]; in JVM tests: a fake. */
interface Shell {
    /** Runs one line; [onOutput] gets the output as it arrives. */
    suspend fun run(line: String, onOutput: (String) -> Unit = {}): ShellResult
    /** Runs a line the player does not see (preparing a task). */
    suspend fun runQuiet(line: String): ShellResult = run(line)
    /** Ctrl-C: stops the command that is running. */
    fun interrupt()
    /** Ctrl-C at the `>` prompt: the shell drops the unfinished command and shows its prompt again. */
    suspend fun cancelInput(): ShellResult { interrupt(); return ShellResult("", 130, folder, interrupted = true) }
    /** Sends a line to the program that is running (it reads from the terminal). */
    fun sendInput(line: String)
    val folder: String
    val busy: Boolean
    /** The game's own processes: the shell and the app. */
    val ownPids: Set<Int> get() = emptySet()
    fun resize(rows: Int, cols: Int) {}
    fun close()
}
