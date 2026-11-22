package io.github.alexpp90.daemonsapprentice.engine

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * The phone's own shell (`/system/bin/sh`, mksh) on a pseudo-terminal,
 * interactive, so that `cd`, variables and `history` persist between
 * commands and Ctrl-C reaches the running command, not the game.
 *
 * Line editing is off and echo is off: the game shows the typed line
 * itself. The prompts carry markers (see [MarkerParser]).
 */
/** Set in tests to log what the terminal sends. */
var DEBUG = false

class ShellSession(
    private val rcFile: File,
    private val home: File,
    private val startFolder: File,
    private val extraEnv: Map<String, String> = emptyMap(),
) : Shell {

    private var fd = -1
    private var pid = -1
    private var pfd: ParcelFileDescriptor? = null // kept: its finalizer would close the terminal
    private lateinit var input: FileInputStream
    private lateinit var output: FileOutputStream
    private val lock = Object()
    private var pending: CompletableDeferred<ShellResult>? = null
    private var onOut: ((String) -> Unit)? = null
    private val buf = StringBuilder()
    private var interrupted = false

    @Volatile override var folder: String = startFolder.absolutePath
        private set
    @Volatile override var busy: Boolean = false
        private set
    @Volatile var alive = false
        private set

    private val parser = MarkerParser { ev ->
        synchronized(lock) {
            when (ev) {
                is MarkerParser.Event.Output -> { buf.append(ev.text); onOut?.invoke(ev.text) }
                is MarkerParser.Event.Prompt -> {
                    folder = ev.folder.ifEmpty { folder }
                    finish(ShellResult(buf.toString(), ev.status, folder, false, interrupted))
                }
                MarkerParser.Event.MoreInput -> finish(ShellResult(buf.toString(), 0, folder, true, interrupted))
            }
        }
    }

    private fun finish(r: ShellResult) {
        buf.setLength(0)
        interrupted = false
        busy = false
        val p = pending
        pending = null
        onOut = null
        p?.complete(r)
    }

    /** Starts the shell and waits for its first prompt. */
    suspend fun start(rows: Int = 30, cols: Int = 40): Boolean = withContext(Dispatchers.IO) {
        writeRc()
        val env = LinkedHashMap(System.getenv())
        env["HOME"] = home.absolutePath
        env["ENV"] = rcFile.absolutePath
        env["TERM"] = "dumb"
        env["PWD"] = startFolder.absolutePath
        env.remove("LANG"); env.remove("LC_ALL")
        env.putAll(extraEnv)
        val pidOut = IntArray(1)
        startFolder.mkdirs()
        fd = Pty.open("/system/bin/sh", arrayOf("sh", "-i"),
            env.map { "${it.key}=${it.value}" }.toTypedArray(), startFolder.absolutePath, rows, cols, pidOut)
        if (fd < 0) return@withContext false
        pid = pidOut[0]
        val p = ParcelFileDescriptor.adoptFd(fd)
        pfd = p
        input = FileInputStream(p.fileDescriptor)
        output = FileOutputStream(p.fileDescriptor)
        alive = true
        val first = CompletableDeferred<ShellResult>()
        synchronized(lock) { pending = first; busy = true }
        thread(name = "apprentice-shell-reader", isDaemon = true) { readLoop() }
        withTimeoutOrNull(10_000) { first.await() } != null
    }

    private fun readLoop() {
        val bytes = ByteArray(8192)
        val decoder = Charsets.UTF_8.newDecoder()
        val inBuf = java.nio.ByteBuffer.allocate(16384)
        val outBuf = java.nio.CharBuffer.allocate(16384)
        try {
            while (true) {
                val n = input.read(bytes)
                if (n <= 0) break
                inBuf.put(bytes, 0, n)
                inBuf.flip()
                decoder.decode(inBuf, outBuf, false)
                inBuf.compact()
                outBuf.flip()
                val s = outBuf.toString()
                outBuf.clear()
                if (DEBUG) android.util.Log.d("Apprentice", "pty> " + s.replace("\u001e", "<RS>").replace("\u001f", "<US>").replace("\u001d", "<GS>"))
                parser.feed(s)
            }
        } catch (_: Exception) {
        }
        alive = false
        synchronized(lock) { finish(ShellResult(buf.toString(), -1, folder)) }
    }

    private fun writeRc() {
        rcFile.parentFile?.mkdirs()
        // PS1 is expanded by the shell each time it is shown: exit status and folder.
        rcFile.writeText(
            "set +o emacs 2>/dev/null\n" +
            "set +o vi 2>/dev/null\n" +
            "PS1='\u001e\$?;\$PWD\u001f'\n" +
            "PS2='\u001d'\n" +
            "HISTSIZE=500\n" +
            // Android's system refuses a terminal handed to it by an app: these tools send their
            // input and output to the system, so they get /dev/null and a pipe instead.
            "pm() { command pm \"\$@\" < /dev/null 2>&1 | cat; return \${PIPESTATUS[0]}; }\n" +
            "settings() { command settings \"\$@\" < /dev/null 2>&1 | cat; return \${PIPESTATUS[0]}; }\n" +
            "cmd() { command cmd \"\$@\" < /dev/null 2>&1 | cat; return \${PIPESTATUS[0]}; }\n" +
            "am() { command am \"\$@\" < /dev/null 2>&1 | cat; return \${PIPESTATUS[0]}; }\n" +
            "dumpsys() { command dumpsys \"\$@\" < /dev/null 2>&1 | cat; return \${PIPESTATUS[0]}; }\n"
        )
    }

    private fun write(s: String) {
        try { output.write(s.toByteArray()); output.flush() } catch (_: Exception) {}
    }

    override suspend fun run(line: String, onOutput: (String) -> Unit): ShellResult {
        val d = CompletableDeferred<ShellResult>()
        synchronized(lock) {
            if (!alive) return ShellResult("", -1, folder)
            pending = d
            onOut = onOutput
            busy = true
        }
        withContext(Dispatchers.IO) { write(line + "\n") }
        return d.await()
    }

    override suspend fun runQuiet(line: String): ShellResult = run(line)

    override fun interrupt() {
        synchronized(lock) { interrupted = true }
        thread(isDaemon = true) {
            write("\u0003")
            Thread.sleep(1500)
            if (busy) {
                // The command ignored Ctrl-C: stop its whole process group, never the shell.
                val g = Pty.foregroundGroup(fd)
                if (g > 1 && g != pid) Pty.killGroup(g, 9)
            }
        }
    }

    override suspend fun cancelInput(): ShellResult {
        val d = CompletableDeferred<ShellResult>()
        synchronized(lock) {
            if (!alive) return ShellResult("", -1, folder)
            pending = d; onOut = null; busy = true; interrupted = true
        }
        // The terminal turns Ctrl-C into SIGINT for the shell itself: it drops the line and prompts again.
        withContext(Dispatchers.IO) { write("\u0003") }
        return withTimeoutOrNull(5_000) { d.await() } ?: ShellResult("", 130, folder, interrupted = true)
    }

    override val ownPids: Set<Int> get() = setOf(pid, android.os.Process.myPid())

    override fun sendInput(line: String) { thread(isDaemon = true) { write(line + "\n") } }

    override fun resize(rows: Int, cols: Int) { if (fd >= 0) Pty.resize(fd, rows, cols) }

    override fun close() {
        if (pid > 0) {
            val g = Pty.foregroundGroup(fd)
            if (g > 1 && g != pid) Pty.killGroup(g, 9)
            android.os.Process.killProcess(pid)
            android.os.Process.sendSignal(pid, 9)
        }
        try { pfd?.close() } catch (_: Exception) {}
        pfd = null
        alive = false
        fd = -1
    }
}
