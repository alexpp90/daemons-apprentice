/*
 * The Daemon's Apprentice: the pseudo-terminal helper.
 *
 * Starts the phone's own shell on a pseudo-terminal, so that the shell
 * behaves as in a real terminal: Ctrl-C reaches the running command, not
 * the game, and programs see a terminal. Copyright the Daemon's Apprentice
 * authors, GPL-3.0-or-later.
 */
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <pty.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

static char **to_cstrings(JNIEnv *env, jobjectArray arr) {
    jsize n = (*env)->GetArrayLength(env, arr);
    char **out = calloc((size_t)n + 1, sizeof(char *));
    for (jsize i = 0; i < n; i++) {
        jstring s = (jstring)(*env)->GetObjectArrayElement(env, arr, i);
        const char *c = (*env)->GetStringUTFChars(env, s, NULL);
        out[i] = strdup(c);
        (*env)->ReleaseStringUTFChars(env, s, c);
        (*env)->DeleteLocalRef(env, s);
    }
    return out;
}

JNIEXPORT jint JNICALL
Java_io_github_alexpp90_daemonsapprentice_engine_Pty_open(JNIEnv *env, jclass cls, jstring jpath,
        jobjectArray jargv, jobjectArray jenv, jstring jcwd, jint rows, jint cols, jintArray jpid) {
    (void)cls;
    char **argv = to_cstrings(env, jargv);
    char **envp = to_cstrings(env, jenv);
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    const char *cwd = (*env)->GetStringUTFChars(env, jcwd, NULL);
    struct winsize ws = { (unsigned short)rows, (unsigned short)cols, 0, 0 };
    int master = -1;
    pid_t pid = forkpty(&master, NULL, NULL, &ws);
    if (pid == 0) {
        /* The child: no echo (the game shows the typed line itself), keep
         * line editing by the terminal (ICANON) and signals (ISIG). */
        struct termios t;
        if (tcgetattr(0, &t) == 0) {
            t.c_lflag &= ~(ECHO | ECHOE | ECHOK | ECHONL | ECHOCTL);
            tcsetattr(0, TCSANOW, &t);
        }
        for (int fd = 3; fd < 256; fd++) close(fd);
        sigset_t none; sigemptyset(&none); sigprocmask(SIG_SETMASK, &none, NULL);
        for (int s = 1; s < 32; s++) signal(s, SIG_DFL);
        if (chdir(cwd) != 0) { /* the game prepares the folder; fall back to / */ chdir("/"); }
        execve(path, argv, envp);
        _exit(127);
    }
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    (*env)->ReleaseStringUTFChars(env, jcwd, cwd);
    for (char **p = argv; *p; p++) free(*p);
    for (char **p = envp; *p; p++) free(*p);
    free(argv); free(envp);
    if (pid < 0) return -errno;
    jint p = (jint)pid;
    (*env)->SetIntArrayRegion(env, jpid, 0, 1, &p);
    return master;
}

JNIEXPORT void JNICALL
Java_io_github_alexpp90_daemonsapprentice_engine_Pty_resize(JNIEnv *env, jclass cls, jint fd, jint rows, jint cols) {
    (void)env; (void)cls;
    struct winsize ws = { (unsigned short)rows, (unsigned short)cols, 0, 0 };
    ioctl(fd, TIOCSWINSZ, &ws);
}

/* The process group that owns the terminal now: the running command, or the shell. */
JNIEXPORT jint JNICALL
Java_io_github_alexpp90_daemonsapprentice_engine_Pty_foregroundGroup(JNIEnv *env, jclass cls, jint fd) {
    (void)env; (void)cls;
    return (jint)tcgetpgrp(fd);
}

JNIEXPORT jint JNICALL
Java_io_github_alexpp90_daemonsapprentice_engine_Pty_killGroup(JNIEnv *env, jclass cls, jint pgid, jint sig) {
    (void)env; (void)cls;
    if (pgid <= 1) return -1;
    return kill(-(pid_t)pgid, sig) == 0 ? 0 : -errno;
}

JNIEXPORT jint JNICALL
Java_io_github_alexpp90_daemonsapprentice_engine_Pty_waitFor(JNIEnv *env, jclass cls, jint pid) {
    (void)env; (void)cls;
    int status = 0;
    if (waitpid((pid_t)pid, &status, 0) < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

JNIEXPORT void JNICALL
Java_io_github_alexpp90_daemonsapprentice_engine_Pty_close(JNIEnv *env, jclass cls, jint fd) {
    (void)env; (void)cls;
    close(fd);
}
