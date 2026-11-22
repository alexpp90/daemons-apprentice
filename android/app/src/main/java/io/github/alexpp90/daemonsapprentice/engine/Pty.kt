package io.github.alexpp90.daemonsapprentice.engine

/** The pseudo-terminal helper in `cpp/pty.c`. */
object Pty {
    init { System.loadLibrary("apprentice-pty") }

    /** Starts [path] on a new pseudo-terminal; returns the terminal's file descriptor (negative: error). */
    @JvmStatic external fun open(path: String, argv: Array<String>, env: Array<String>, cwd: String,
                                 rows: Int, cols: Int, pidOut: IntArray): Int
    @JvmStatic external fun resize(fd: Int, rows: Int, cols: Int)
    @JvmStatic external fun foregroundGroup(fd: Int): Int
    @JvmStatic external fun killGroup(pgid: Int, signal: Int): Int
    @JvmStatic external fun waitFor(pid: Int): Int
    @JvmStatic external fun close(fd: Int)
}
