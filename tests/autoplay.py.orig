#!/usr/bin/env python3
"""Play The Daemon's Apprentice automatically through a pseudo-terminal.

Every scenario starts the real game script in a fresh, temporary HOME, so
the player's real home folder is never touched. The game is started with
TQ_TEST=1, which makes it print a marker <<TQ:task_id>> before each prompt;
the driver answers with the task's known solution (read from the game
itself via TQ_LIB=1) or with keystrokes for interactive tasks.

Usage:  python3 tests/autoplay.py [scenario ...]
        python3 tests/autoplay.py --list
        python3 tests/autoplay.py @standard     (all but "full")
Transcripts (ANSI codes stripped) go to tests/out/.
"""
import fcntl
import os
import pty
import re
import select
import shutil
import signal
import struct
import subprocess
import sys
import tempfile
import termios
import time
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# The version a player on this system gets (built by tools/build.sh).
GAME = os.path.join(ROOT, "game", "mac" if sys.platform == "darwin" else "linux",
                    "daemons-apprentice.sh")
OUT = os.path.join(ROOT, "tests", "out")
BASH = os.environ.get("TQ_BASH", "bash")
# The PATH the game sees: on a Mac the stock one (what a player has), so
# Homebrew tools here do not hide what a stock Mac lacks. TQ_PATH overrides.
GPATH = os.environ.get("TQ_PATH") or ("/usr/bin:/bin:/usr/sbin:/sbin" if sys.platform == "darwin"
                                      else os.environ.get("PATH", "/usr/bin:/bin"))

MARKER = re.compile(rb"<<TQ:([A-Za-z0-9_]+)(?::q(\d+))?>>")
ANSI = re.compile(rb"\x1b\[[0-9;?]*[A-Za-z]|\x1b[()][A-Z0-9]|\x1b[=>]")

# Keystroke scripts for tasks that need more than typing the solution,
# by the task's "drive" tag in the game. Each step: ("line", text) types
# text + Enter; ("keys", bytes) sends raw bytes; ("sleep", seconds);
# "{sol}" stands for the command part of the task's solution.
# In nano: Ctrl-O, Enter (save under the same name), Ctrl-X.
NANO_SAVE_QUIT = [("sleep", 0.3), ("keys", b"\x0f"), ("sleep", 0.5), ("keys", b"\r"),
                  ("sleep", 0.5), ("keys", b"\x18")]
SPECIAL = {
    "ctrlc": [("line", "{sol1}"), ("waitfor", rb"y\r?\ny\r?\ny"), ("ctrlc", None)],
    "uparrow-fix": [("keys", b"\x1b[A"), ("sleep", 0.3), ("keys", b"\x1b[D" * 6), ("sleep", 0.2),
                    ("keys", b"\x7f\x7f\x7f"), ("sleep", 0.2), ("keys", b"cho\r")],
    "man": [("line", "{sol2}"), ("sleep", 1.5), ("keys", b"q")],
    "top": [("line", "top"), ("sleep", 2.0), ("keys", b"q")],
    "ctrlc-once": [("line", "{sol2}"), ("sleep", 1.5), ("keys", b"\x03")],
    "nano-welcome": [("line", "nano welcome.txt"), ("sleep", 1.0), ("keys", b"\x1b[B" * 16),
                     ("sleep", 0.3), ("keys", b"\x05Ada")] + NANO_SAVE_QUIT,
    "nano-todo": [("line", "nano todo.txt"), ("sleep", 1.0), ("keys", b"\x1b[B\x1b[B"), ("sleep", 0.2),
                  ("keys", b"\x0b"), ("sleep", 0.2), ("keys", b"\x1b[B"), ("sleep", 0.2),
                  ("keys", b"\x0b")] + NANO_SAVE_QUIT,
    "nano-hello": [("line", "nano hello.sh"), ("sleep", 1.0), ("keys", b"\x1b[B" * 12), ("sleep", 0.3),
                   ("keys", b"echo \"Hello from my first script\"")] + NANO_SAVE_QUIT,
    "nano-greet": [("line", "nano greet.sh"), ("sleep", 1.0), ("keys", b"\x1b[B" * 12), ("sleep", 0.3),
                   ("keys", b"name=\"$1\"\recho \"Hello, $name! Today is $(date +%A).\"")] + NANO_SAVE_QUIT,
    "ctrlc-cont": [("line", "{sol}"), ("waitfor", rb"<<TQ:[A-Za-z0-9_]+>>"), ("sleep", 0.5), ("keys", b"\x03")],
    # Only this game's own practice process: other games run next to it.
    "killpid": [("line", "kill $(cat ~/apprentice-playground/lab/.miner.pid)")],
}
QUIZ = {}   # task id -> right answers, filled by solutions()
# The level numbers in play order, as the game itself lists them.
LEVELS = [int(x) for x in subprocess.run(
    [BASH, "-c", 'TQ_LIB=1; . "$1"; echo "$TQ_LEVELS"', "x", GAME],
    env={"PATH": GPATH, "HOME": "/nonexistent"},
    capture_output=True, text=True, check=True).stdout.split()]
# The side quests (R21, R22): off the main path, played like levels.
SIDE = subprocess.run(
    [BASH, "-c", 'TQ_LIB=1; . "$1"; echo "$TQ_SIDE"', "x", GAME],
    env={"PATH": GPATH, "HOME": "/nonexistent"},
    capture_output=True, text=True, check=True).stdout.split()


def lname(lvl):
    """"Level 4" or "Side quest S1", as the game writes it."""
    return f"Side quest {lvl}" if str(lvl).startswith("S") else f"Level {lvl}"


def solutions(env):
    """Ask the game for every task's solution (one or more lines)."""
    script = (
        'TQ_LIB=1; . "$1"; tq_detect_os; tq_find_sha; tq_find_pkg; tq_has_gui(){ return 1; }; '
        'for l in $TQ_LEVELS $TQ_SIDE; do '
        '  for t in $(tq_L$l tasks); do $t solution | sed "s/^/$t\t/"; '
        '    d=$($t drive 2>/dev/null); [ -n "$d" ] && printf "%s\t@drive %s\n" "$t" "$d"; '
        '    if [ "$($t kind 2>/dev/null)" = quiz ]; then a=""; i=1; '
        '      while [ $i -le $($t qn) ]; do a="$a $($t q$i | sed -n 3p)"; i=$((i+1)); done; '
        '      printf "%s\t@quiz%s\n" "$t" "$a"; fi; done; done'
    )
    out = subprocess.run([BASH, "-c", script, "x", GAME], env=env,
                         capture_output=True, text=True, check=True).stdout
    sol = {}
    for line in out.splitlines():
        tid, cmd = line.split("\t", 1)
        if cmd.startswith("@drive "):
            DRIVE[tid] = cmd[7:]
        elif cmd.startswith("@quiz "):
            QUIZ[tid] = cmd[6:].split()
        else:
            sol.setdefault(tid, []).append(cmd)
    return sol


DRIVE = {}   # task id -> drive tag, filled by solutions()


class Game:
    def __init__(self, home, name, extra_env=None, cols=80, rows=30):
        self.home = home
        self.name = name
        env = {
            "HOME": home, "PATH": GPATH, "TERM": "xterm-256color",
            "LANG": "C.UTF-8", "TQ_TEST": "1", "USER": os.environ.get("USER", "player"),
            "TMPDIR": home,
        }
        if os.environ.get("BASH_COMPAT"):
            env["BASH_COMPAT"] = os.environ["BASH_COMPAT"]
        if extra_env:
            env.update(extra_env)
        self.env = env
        pid, fd = pty.fork()
        if pid == 0:
            os.chdir(home)
            os.execvpe(BASH, [BASH, GAME], env)
        self.pid, self.fd = pid, fd
        fcntl.ioctl(fd, termios.TIOCSWINSZ, struct.pack("HHHH", rows, cols, 0, 0))
        self.buf = b""
        self.pos = 0
        self.alive = True

    def _pump(self, t):
        r, _, _ = select.select([self.fd], [], [], t)
        if r:
            try:
                data = os.read(self.fd, 65536)
            except OSError:
                data = b""
            if not data:
                self.alive = False
            self.buf += data
            # Safety net: a runaway program must not eat all memory.
            if len(self.buf) > 200_000_000:
                self.buf = self.buf[-1_000_000:]
                self.pos = 0
                self.alive = False
                os.kill(self.pid, signal.SIGKILL)

    def expect(self, patterns, timeout=15):
        """Wait until one of the regexes (bytes) matches after self.pos.
        Returns (index, match). index -1 on EOF, -2 on timeout."""
        pats = [re.compile(p) if isinstance(p, bytes) else p for p in patterns]
        end = time.time() + timeout
        while True:
            best = None
            for i, p in enumerate(pats):
                m = p.search(self.buf, self.pos)
                if m and (best is None or m.start() < best[1].start()):
                    best = (i, m)
            if best:
                self.pos = best[1].end()
                return best
            if not self.alive:
                return (-1, None)
            if time.time() > end:
                return (-2, None)
            self._pump(0.05)

    def ctrl_c_until_quiet(self, res):
        """Press Ctrl-C until the output stops growing."""
        for _ in range(6):
            self.send(b"\x03")
            self.drain(0.6)
            a = len(self.buf)
            self.drain(0.4)
            if len(self.buf) - a < 2000:
                return
        res.fail("Ctrl-C did not stop the running program")

    def drain(self, t=0.3):
        end = time.time() + t
        while time.time() < end and self.alive:
            self._pump(0.05)

    def send(self, data):
        if isinstance(data, str):
            data = data.encode()
        os.write(self.fd, data)

    def line(self, text):
        self.send(text + "\r")

    def close(self):
        self.drain(0.5)
        try:
            os.kill(self.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        try:
            os.waitpid(self.pid, 0)
        except ChildProcessError:
            pass
        os.makedirs(OUT, exist_ok=True)
        text = ANSI.sub(b"", self.buf).replace(b"\r", b"")
        with open(os.path.join(OUT, f"{self.name}.txt"), "wb") as f:
            f.write(text)
        return text.decode(errors="replace")

    def text(self):
        return ANSI.sub(b"", self.buf).replace(b"\r", b"").decode(errors="replace")


PRESS = rb"\[ press Enter \]"
CHOICE = rb"Your choice:"
LEVELNO = rb"Level number"
YESNO = rb"\[y/n\]"


class Result:
    def __init__(self, name):
        self.name = name
        self.ok = True
        self.notes = []

    def fail(self, msg):
        self.ok = False
        self.notes.append("FAIL: " + msg)

    def note(self, msg):
        self.notes.append(msg)


def play_tasks(g, sol, res, stop_when=None, max_steps=2000, quit_at_menu=True, yn="y"):
    """Answer markers with solutions until the game shows the main menu
    (or stop_when(task_id) returns True)."""
    sent = {}         # task id -> number of solution steps sent
    solved = []
    last = None
    for _ in range(max_steps):
        # A task tagged "slow" walks through real folders (R22): give it time.
        i, m = g.expect([MARKER, PRESS, CHOICE, YESNO], timeout=240 if DRIVE.get(last) == "slow" else 20)
        if i == -1:
            return solved, "eof"
        if i == -2:
            res.fail(f"timeout (last task {last})")
            return solved, "timeout"
        if i == 1:
            g.send("\r")
            continue
        if i == 2:
            return solved, "menu"
        if i == 3:
            g.line(yn)
            continue
        tid = m.group(1).decode()
        q = m.group(2)
        if q:
            g.drain(0.1)
            g.line(QUIZ[tid][int(q) - 1])
            continue
        if last is not None and tid != last:
            solved.append(last)
        if stop_when and tid != last and stop_when(tid):
            return solved, tid
        last = tid
        g.drain(0.15)
        k = sent.get(tid, 0)
        special = tid in DRIVE
        steps = SPECIAL.get(DRIVE.get(tid))
        if steps is None:
            steps = [("line", c) for c in sol.get(tid, [])]
        if k >= len(steps) if not special else k >= 1:
            res.fail(f"{tid}: not solved by its solution; skipping")
            sent[tid] = k + 1
            g.line("skip")
            continue
        if special:
            words = (sol.get(tid) or [""])[0].split()
            for kind, val in steps:
                if kind == "line":
                    val = val.replace("{sol1}", " ".join(words[:1])).replace("{sol2}", " ".join(words[:2]))
                    val = val.replace("{sol}", (sol.get(tid) or [""])[0])
                    g.line(val)
                elif kind == "keys":
                    g.send(val)
                elif kind == "waitfor":
                    g.expect([val], 10)
                elif kind == "ctrlc":
                    g.ctrl_c_until_quiet(res)
                else:
                    g.drain(val)
            sent[tid] = 1
        else:
            g.line(steps[k][1])
            sent[tid] = k + 1
    res.fail("too many steps")
    return solved, "steps"


def new_home():
    return tempfile.mkdtemp(prefix="tq-home-", dir=os.environ.get("TQ_TEST_TMP"))


# ---------------------------------------------------------------- scenarios

def sc_full(sol):
    """Whole game from the intro to the finale, every task by its solution."""
    res = Result("full")
    home = new_home()
    g = Game(home, "full")
    i, _ = g.expect([CHOICE])
    if i < 0:
        res.fail("no main menu")
    g.line("n")
    solved, why = play_tasks(g, sol, res)
    if why != "menu":
        res.fail(f"game did not return to the menu at the end ({why})")
    t = g.text()
    if not re.search(rf"Level {LEVELS[-1]}: (\d+) of \1 tasks solved", g.text()):
        res.fail(f"Level {LEVELS[-1]} not fully solved")
    for must in ["CERTIFICATE OF MASTERY",
                 "FLAG CAPTURED: FLAG{dots-hide-things}", "FLAG CAPTURED: FLAG{defender-certified}",
                 "FLAG CAPTURED: FLAG{quarantine-cleared}",
                 "FLAG CAPTURED: FLAG{walls-within-walls}",
                 "You judged 6 of 6 correctly",
                 "A side quest is open: Portrait of Your Computer",
                 "Side quest S1: 13 of 13 tasks solved", "FLAG CAPTURED: FLAG{know-your-machine}",
                 "A side quest is open: The Supervisor",
                 "Side quest S2: 11 of 11 tasks solved", "Your record: 11 of 11",
                 "FLAG CAPTURED: FLAG{read-first-then-allow}", "Both side quests done, too."]:
        if must not in t:
            res.fail(f"transcript lacks: {must}")
    g.line("q")
    g.expect([rb"Progress saved"], 5)
    text = g.close()
    save = open(os.path.join(home, ".apprentice-save")).read()
    if "levels_done=" + " ".join(str(l) for l in LEVELS) not in save:
        res.fail("save file does not list all levels as done:\n" + save)
    if "side_done=" + " ".join(SIDE) not in save:
        res.fail("save file does not list the side quests as done:\n" + save)
    if os.path.exists(os.path.join(home, ".bashrc")) or os.path.exists(os.path.join(home, ".zshrc")):
        res.fail("the agent wrote into the home folder")
    cheat_path = os.path.join(home, "apprentice-playground", "CHEATSHEET.md")
    if not os.path.isfile(cheat_path) or os.path.getsize(cheat_path) < 200:
        res.fail("CHEATSHEET.md missing or too small")
    n_solved = len(re.search(r"^solved=(.*)$", save, re.M).group(1).split())
    res.note(f"tasks recorded as solved: {n_solved}")
    flags = re.search(r"^flags=(.*)$", save, re.M).group(1).split()
    res.note(f"flags: {len(flags)}")
    if text.count("Not quite. Here is why") or "not solved by its solution" in " ".join(res.notes):
        pass
    shutil.rmtree(home, ignore_errors=True)
    return res


def sc_level(sol, lvl):
    """One level, started from the level menu in a fresh home, played up to
    the first task of the next level (R19: not the rest of the game)."""
    res = Result(f"level {lvl}")
    home = new_home()
    g = Game(home, f"level{lvl}_cold")
    g.expect([CHOICE])
    g.line("l")
    g.expect([LEVELNO])
    g.line(str(lvl))
    solved, why = play_tasks(g, sol, res, stop_when=lambda t: not t.startswith(f"t{lvl}_"))
    t = g.text()
    m = re.search(rf"{lname(lvl)}: (\d+) of (\d+) tasks solved", t)
    if not m:
        res.fail(f"level {lvl}: no level-complete summary ({why})")
    elif m.group(1) != m.group(2):
        res.fail(f"level {lvl}: {m.group(0)}")
    g.line("q" if why == "menu" else "quit")
    g.expect([rb"Progress saved"], 8)
    g.close()
    shutil.rmtree(home, ignore_errors=True)
    return res


def sc_save_continue(sol):
    """Quit mid-level, check the save file, restart and continue."""
    res = Result("save_continue")
    home = new_home()
    g = Game(home, "save_continue_1")
    g.expect([CHOICE]); g.line("l"); g.expect([LEVELNO]); g.line("2")
    solved, why = play_tasks(g, sol, res, stop_when=lambda t: t == "t2_4")
    if why != "t2_4":
        res.fail(f"did not reach t2_4 ({why})")
    g.drain(0.2)
    g.line("quit")
    i, _ = g.expect([rb"Progress saved"], 5)
    if i < 0:
        res.fail("no 'Progress saved' on quit")
    g.close()
    save = open(os.path.join(home, ".apprentice-save")).read()
    if "level=2" not in save or "task=4" not in save:
        res.fail("save file not at level 2 task 4:\n" + save)
    g = Game(home, "save_continue_2")
    i, _ = g.expect([rb"Welcome back! Saved progress: level 2, task 4"], 10)
    if i < 0:
        res.fail("no welcome-back line")
    g.expect([CHOICE]); g.line("c")
    i, m = g.expect([MARKER, PRESS], 10)
    while i == 1:
        g.send("\r"); i, m = g.expect([MARKER, PRESS], 10)
    if i < 0 or m.group(1) != b"t2_4":
        res.fail(f"continue did not resume at t2_4 (got {m.group(1) if m else None})")
    # the save file task: cat it from the game
    g.line("cat ~/.apprentice-save")
    i, _ = g.expect([rb"level=2"], 5)
    if i < 0:
        res.fail("cat of the save file did not show level=2")
    g.expect([MARKER], 5)
    g.drain(0.3)
    g.send(b"\x04")   # Ctrl-D quits too
    i, _ = g.expect([rb"Progress saved"], 5)
    if i < 0:
        res.fail("Ctrl-D did not quit with 'Progress saved'")
    g.close()
    shutil.rmtree(home, ignore_errors=True)
    return res


def sc_reset(sol):
    res = Result("reset")
    home = new_home()
    g = Game(home, "reset")
    g.expect([CHOICE]); g.line("l"); g.expect([LEVELNO]); g.line("3")
    play_tasks(g, sol, res, stop_when=lambda t: t == "t3_3")
    g.drain(0.2)
    g.line("menu")
    g.expect([CHOICE]); g.line("r")
    g.expect([YESNO]); g.line("y")
    i, _ = g.expect([rb"Clean slate"], 5)
    if i < 0:
        res.fail("no reset confirmation")
    g.drain(0.3)
    if os.path.exists(os.path.join(home, ".apprentice-save")):
        res.fail("save file still exists after reset")
    pg = os.path.join(home, "apprentice-playground")
    if not os.path.isdir(pg) or os.path.exists(os.path.join(pg, "workshop")):
        res.fail("playground not rebuilt empty after reset")
    if "New game" not in g.text()[-600:]:
        res.fail("menu after reset does not offer a new game")
    g.line("q")
    g.close()
    shutil.rmtree(home, ignore_errors=True)
    return res


def to_marker(g, res, label):
    """Press Enter at every pause until a task prompt; return its id."""
    for _ in range(12):
        i, m = g.expect([MARKER, PRESS], 10)
        if i == 1:
            g.send("\r")
            continue
        if i < 0:
            res.fail(f"{label}: no task prompt")
            return None
        return m.group(1).decode()
    res.fail(f"{label}: too many pauses")
    return None


def sc_wrong_hints_ctrlc(sol):
    """Wrong answers, all hint steps, explain/status/help, pasted text,
    Ctrl-C at the prompt and during a command, unknown commands."""
    res = Result("wrong_hints_ctrlc")
    home = new_home()
    g = Game(home, "wrong_hints_ctrlc")
    g.expect([CHOICE]); g.line("l"); g.expect([LEVELNO]); g.line("0")
    if to_marker(g, res, "start") != "t0_1":
        res.fail("level 0 does not start with t0_1")
    g.drain(0.2)
    g.line("help")
    if to_marker(g, res, "after help") != "t0_2":
        res.fail("help did not solve t0_1")
    g.drain(0.2)

    def step(line, want, label):
        g.line(line)
        i, _ = g.expect([re.compile(want.encode())], 8)
        if i < 0:
            res.fail(f"{label}: expected /{want}/")
        g.expect([MARKER], 8)
        g.drain(0.1)

    step("hello", r"So close", "wrong answer feedback")
    step("hint", r"Hint 1 of 3", "hint 1")
    step("hint", r"Hint 2 of 3", "hint 2")
    step("hint", r"Solution:[\s\S]*echo hello", "hint 3 shows solution")
    step("explain", r"argument", "explain shows the task")
    step("status", r"Your progress", "status")
    step("help", r"Help in this game", "help")
    step("keys", r"German keyboard", "keys")
    step("cheat", r"Your Growing Cheatsheet", "cheatsheet command")
    step("ls /definitely-not-here", r"reported a problem", "error feedback")
    step("ehco hi", r"does not know that command", "command-not-found feedback")
    step("  | completes it. If several names fit", r"text from the game", "pasted game text")
    step("$ whoami", r"Leave out the", "pasted prompt")
    step("pwd", r"Good look around", "looking around is fine")
    # Ctrl-C at the prompt drops the half-typed line, like a real shell.
    g.send(b"ech\x03")
    i, _ = g.expect([rb"ech\^C"], 4)
    if i < 0:
        res.fail("Ctrl-C at the prompt did not show ^C")
    g.drain(0.3)
    g.send(b"echo hello\r")
    i, _ = g.expect([rb"\*  .+  \*"], 8)
    if i < 0:
        res.fail("Ctrl-C at the prompt broke the line or the game")
    if to_marker(g, res, "after Ctrl-C") != "t0_3":
        res.fail("did not advance to t0_3 after Ctrl-C at the prompt")
    # Ctrl-C during a running command
    g.drain(0.2)
    g.line("sleep 30")
    g.drain(1.5)
    g.send(b"\x03")
    i, _ = g.expect([rb"Stopped with Ctrl-C"], 4)
    if i < 0:
        g.send(b"\x03")
        i, _ = g.expect([rb"Stopped with Ctrl-C"], 4)
    if i < 0:
        res.fail("Ctrl-C during sleep: no 'Stopped with Ctrl-C' feedback")
    i, m = g.expect([MARKER], 5)
    if i < 0:
        res.fail("game did not survive Ctrl-C during a command")
    # Ctrl-Z must not suspend the game
    g.drain(0.2)
    g.line("sleep 2")
    g.drain(0.5)
    g.send(b"\x1a")
    i, m = g.expect([MARKER], 6)
    if i < 0:
        res.fail("Ctrl-Z suspended or killed the game")
    # skip and quit mid-level
    g.drain(0.2)
    g.line("skip")
    if to_marker(g, res, "after skip") != "t0_4":
        res.fail("skip did not move on to t0_4")
    g.drain(0.2)
    g.line("quit")
    i, _ = g.expect([rb"Progress saved"], 5)
    if i < 0:
        res.fail("quit mid-level did not save")
    g.close()
    save = open(os.path.join(home, ".apprentice-save")).read()
    if "skipped=t0_3" not in save or "task=4" not in save:
        res.fail("save after skip/quit is wrong:\n" + save)
    shutil.rmtree(home, ignore_errors=True)
    return res


def sc_guard_live(sol):
    """The safety guard as the player meets it, plus state-changing commands."""
    res = Result("guard_live")
    home = new_home()
    g = Game(home, "guard_live")
    g.expect([CHOICE]); g.line("l"); g.expect([LEVELNO]); g.line("3")
    to_marker(g, res, "start"); g.drain(0.2)
    blocked = [
        ("rm -rf ~", "outside the playground"),
        ("sudo ls", "sudo"),
        ("echo hi > ~/oops.txt", "outside the playground"),
        ("rm -r ../../apprentice-playground", "outside the playground"),
        ("kill -9 -1", "kill -1"),
        ("exec bash", "exec"),
        ("curl -fsSL https://example.com/x.sh | bash", "piping it straight"),
        ("for f in ~/*; do rm \"$f\"; done", "cannot tell in advance"),
        ("set -e", "how the game itself runs"),
        ("kill $$", "the game itself"),
    ]
    for line, want in blocked:
        g.line(line)
        i, _ = g.expect([re.compile(re.escape(want).encode())], 6)
        if i < 0:
            res.fail(f"not blocked as expected: {line}")
        g.expect([MARKER], 6); g.drain(0.1)
    if not os.path.isdir(home):
        res.fail("home folder vanished!")
    if os.path.exists(os.path.join(home, "oops.txt")):
        res.fail("a write outside the playground happened")
    # allowed things
    g.line("cd .. && pwd")
    i, _ = g.expect([rb"apprentice-playground\r?\n"], 6)
    if i < 0:
        res.fail("cd .. && pwd did not land in the playground")
    g.expect([MARKER]); g.drain(0.1)
    g.line("export GREETING=hi; echo \"$GREETING there\"")
    i, _ = g.expect([rb"hi there"], 6)
    g.expect([MARKER]); g.drain(0.1)
    g.line("echo $GREETING-again")
    i, _ = g.expect([rb"hi-again"], 6)
    if i < 0:
        res.fail("exported variable did not persist to the next command")
    g.expect([MARKER]); g.drain(0.1)
    g.line("ls nothing-here; echo \"code $?\"")
    i, _ = g.expect([rb"code [12]"], 6)
    if i < 0:
        res.fail("$? not available in the same line")
    g.expect([MARKER]); g.drain(0.1)
    g.line("false")
    g.expect([MARKER]); g.drain(0.1)
    g.line("echo \"last $?\"")
    i, _ = g.expect([rb"last 1"], 6)
    if i < 0:
        res.fail("$? from the previous line not kept")
    g.expect([MARKER]); g.drain(0.1)
    g.line("quit")
    g.expect([rb"Progress saved"], 5)
    g.close()
    shutil.rmtree(home, ignore_errors=True)
    return res


def sc_placement(sol):
    res = Result("placement")
    for answers, want in [("a b a b b a a b b b", 11), ("a b b", 3), ("c", 0)]:
        home = new_home()
        g = Game(home, f"placement_{want}")
        g.expect([CHOICE])
        g.drain(0.2)
        g.send(b"\x03")            # Ctrl-C at the menu must not quit
        g.drain(0.3)
        if not g.alive:
            res.fail("Ctrl-C at the main menu ended the game")
        g.line("p")
        for a in answers.split():
            g.expect([rb"\n  > "], 6)
            g.line(a)
        i, _ = g.expect([re.compile(rb"Suggested start: Level %d" % want)], 6)
        if i < 0:
            res.fail(f"placement {answers!r} did not suggest level {want}")
        g.expect([YESNO]); g.line("y")
        tid = to_marker(g, res, "placement start")
        if not tid or not tid.startswith("t%d_" % want):
            res.fail(f"placement did not start level {want}")
        g.drain(0.2); g.line("quit"); g.expect([rb"Progress saved"], 5)
        g.close()
        shutil.rmtree(home, ignore_errors=True)
    return res


def sc_nocolor(sol):
    """NO_COLOR and a dumb terminal: no escape codes at all."""
    res = Result("nocolor")
    for extra, name in [({"NO_COLOR": "1"}, "nocolor"), ({"TERM": "dumb"}, "dumbterm")]:
        home = new_home()
        g = Game(home, name, extra)
        g.expect([CHOICE]); g.line("l"); g.expect([LEVELNO]); g.line("4")
        play_tasks(g, sol, res, stop_when=lambda t: t == "t4_3")
        g.drain(0.2); g.line("quit"); g.expect([rb"Progress saved"], 5)
        raw = g.buf
        g.close()
        # readline may emit a few cursor codes; colour codes must be absent
        if re.search(rb"\x1b\[[0-9;]*m", raw):
            res.fail(f"{name}: colour escape codes present")
        shutil.rmtree(home, ignore_errors=True)
    return res


def sc_side_quests(sol):
    """The offer after a level and saying no, the level menu, quitting
    inside a side quest, and the way back to the main path (R21, R22)."""
    res = Result("side_quests")
    home = new_home()
    save_path = os.path.join(home, ".apprentice-save")
    report = os.path.join(home, "apprentice-playground", "portrait", "my-computer.txt")
    g = Game(home, "side_quests_1")
    g.expect([CHOICE]); g.line("l"); g.expect([LEVELNO]); g.line("6")
    solved, why = play_tasks(g, sol, res, stop_when=lambda t: not t.startswith("t6_"), yn="n")
    if why != "t7_1":
        res.fail(f"after 'no' the game did not go on with Level 7 ({why})")
    t = g.text()
    if "A side quest is open: Portrait of Your Computer" not in t:
        res.fail("no side quest offer after Level 6")
    if "It waits in the level menu" not in t:
        res.fail("saying no to the offer is not acknowledged")
    g.drain(0.2); g.line("menu")
    g.expect([CHOICE]); g.line("l")
    if g.expect([rb"Side quests"], 8)[0] < 0:
        res.fail("the level menu does not list the side quests")
    g.expect([LEVELNO]); g.line("s1")
    solved, why = play_tasks(g, sol, res, stop_when=lambda t: t == "tS1_3")
    if why != "tS1_3":
        res.fail(f"did not reach tS1_3 ({why})")
    g.drain(0.2); g.line("quit")
    g.expect([rb"Progress saved"], 8)
    g.close()
    save = open(save_path).read()
    for must in ("level=S1", "task=3", "back=7", "back_task=1"):
        if must not in save.split("\n"):
            res.fail(f"save lacks {must}:\n" + save)
    g = Game(home, "side_quests_2")
    if g.expect([rb"Welcome back! Saved progress: side quest S1, task 3"], 10)[0] < 0:
        res.fail("no welcome-back line for the side quest")
    g.expect([CHOICE]); g.line("c")
    solved, why = play_tasks(g, sol, res, stop_when=lambda t: not t.startswith("tS1_"))
    if why != "t7_1":
        res.fail(f"after the side quest the game did not return to Level 7 ({why})")
    if "Side quest S1: 13 of 13 tasks solved" not in g.text():
        res.fail("side quest S1 not fully solved")
    g.drain(0.2); g.line("quit")
    g.expect([rb"Progress saved"], 8)
    g.close()
    save = open(save_path).read().split("\n")
    if "side_done=S1" not in save or "level=7" not in save or "levels_done=6" not in save:
        res.fail("save after the side quest is wrong:\n" + "\n".join(save))
    if any(l.startswith("back=") for l in save):
        res.fail("save still has a way back")
    try:
        rep = open(report).read()
    except OSError:
        rep = ""
    for must in ("Awake for: ", "Hungriest program: ", "    $ uptime", "Files "):
        if must not in rep:
            res.fail(f"my-computer.txt lacks: {must}")
    if any(len(l) > 74 for l in rep.split("\n")):
        res.fail("my-computer.txt has lines wider than 74 columns")
    shutil.rmtree(home, ignore_errors=True)
    return res


def sc_supervisor(sol):
    """The Supervisor (R21) with wrong calls: a yes too many really runs
    and is repaired, a no too many, and the guard as the second wall."""
    res = Result("supervisor")
    home = new_home()
    site = os.path.join(home, "apprentice-playground", "mission", "site")
    g = Game(home, "supervisor")
    g.expect([CHOICE]); g.line("l"); g.expect([LEVELNO]); g.line("S2")
    if to_marker(g, res, "start") != "tS2_1":
        res.fail("the side quest does not start with tS2_1")
    g.drain(0.2)

    def step(line, pattern, label):
        """A line that leaves the step open: answer, then the prompt again."""
        g.line(line)
        if g.expect([pattern], 8)[0] < 0:
            res.fail(f"{label}: expected {pattern!r}")
        g.expect([MARKER], 8)
        g.drain(0.15)

    def call(line, pattern, label):
        g.line(line)
        if g.expect([pattern], 8)[0] < 0:
            res.fail(f"{label}: expected {pattern!r}")

    def nxt(tid):
        got = to_marker(g, res, tid)
        if got != tid:
            res.fail(f"expected {tid}, got {got}")
        g.drain(0.2)

    def ouch(label):
        g.line("allow")
        if g.expect([rb"one yes too many"], 8)[0] < 0:
            res.fail(f"{label}: a wrong yes is not called one")
        g.expect([PRESS], 8); g.send("\r")
        if g.expect([rb"Look at the damage"], 8)[0] < 0:
            res.fail(f"{label}: the damage is not shown")
        g.expect([MARKER], 8)
        g.drain(0.2)

    def perm(name):
        return oct(os.stat(os.path.join(site, name)).st_mode & 0o777)

    step("why", rb"The agent explains", "why")
    step("ls", rb"You looked around", "looking around")
    call("allow", rb"Right call", "allow a harmless command"); nxt("tS2_2")
    call("deny", rb"this one was fine", "a no too many")
    if g.expect([rb"cache-1\.tmp"], 8)[0] < 0:
        res.fail("after a needless no the command did not run")
    nxt("tS2_3")
    ouch("rm -rf tmp/ *")
    if os.path.exists(os.path.join(site, "index.html")):
        res.fail("allowed rm did not really run")
    if not os.path.exists(os.path.join(site, ".env")):
        res.fail("rm took the hidden file too")
    step("allow", rb"First the repair", "allow during a repair")
    call("cp -R ../backup/. .", rb"Repaired", "the repair"); nxt("tS2_4")
    if not os.path.exists(os.path.join(site, "tmp", "keep.txt")) or \
            os.path.exists(os.path.join(site, "tmp", "cache-1.tmp")):
        res.fail("after the repair the project is not as the corrected rm leaves it")
    call("allow", rb"Right call", "counting"); nxt("tS2_5")
    call("deny", rb"drops the idea", "deny what nobody asked for"); nxt("tS2_6")
    ouch("chmod -R 777 .")
    if perm(".env") != "0o777":
        res.fail(f"allowed chmod did not really run ({perm('.env')})")
    call("chmod -R go-rwx .", rb"Repaired", "the chmod repair"); nxt("tS2_7")
    if int(perm(".env"), 8) & 0o077:
        res.fail(f".env is not private again ({perm('.env')})")
    if not os.access(os.path.join(site, "deploy.sh"), os.X_OK):
        res.fail("deploy.sh is not runnable after the corrected chmod")
    step("cd ~", rb"You looked around", "walking away")
    call("allow", rb"Done\. The site is live\.", "the script runs in the project folder"); nxt("tS2_8")
    call("allow", rb"To AI agents working here", "reading the notes"); nxt("tS2_9")
    ouch("cat .env >> index.html")
    if "API_KEY" not in open(os.path.join(site, "index.html")).read():
        res.fail("the allowed >> did not really run")
    call("cp ../backup/index.html .", rb"Repaired", "the page repair"); nxt("tS2_10")
    if "API_KEY" in open(os.path.join(site, "index.html")).read():
        res.fail("the key is still on the page")
    call("allow", rb"Right call", "the report"); nxt("tS2_11")
    call("allow", rb"You said yes\. The game said no", "the guard as the second wall")
    if os.path.exists(os.path.join(home, ".bashrc")) or os.path.exists(os.path.join(home, ".zshrc")):
        res.fail("the agent wrote into the home folder")
    play_tasks(g, sol, res, stop_when=lambda t: not t.startswith("tS2_"))
    t = g.text()
    for must in ("Side quest S2: 11 of 11 tasks solved", "Your record: 6 of 11",
                 "A yes too many: 4.", "A no too many: 1.",
                 "FLAG CAPTURED: FLAG{read-first-then-allow}"):
        if must not in t:
            res.fail(f"transcript lacks: {must}")
    g.line("quit")
    g.expect([rb"Progress saved"], 8)
    g.close()
    shutil.rmtree(home, ignore_errors=True)
    return res


SCENARIOS = {
    "full": sc_full, "levels_cold": None, "save_continue": sc_save_continue,
    "reset": sc_reset, "wrong_hints_ctrlc": sc_wrong_hints_ctrlc,
    "guard_live": sc_guard_live, "placement": sc_placement, "nocolor": sc_nocolor,
    "side_quests": sc_side_quests, "supervisor": sc_supervisor,
}


def width_check():
    """Every transcript line must fit in 80 columns (tabs count as 8)."""
    bad = []
    for fn in sorted(os.listdir(OUT)):
        if not fn.endswith(".txt"):
            continue
        for n, line in enumerate(open(os.path.join(OUT, fn), errors="replace"), 1):
            line = line.rstrip("\n").expandtabs()
            if "<<TQ:" in line:
                continue
            # Only lines the game draws itself: status lines, banners, art.
            # Command output and typed commands may be as wide as they are.
            if not re.match(r"^  (L\d \[|S\d \[|Level \d|Side quest S|\.-|'-|\+-- )|^   [.'|]", line):
                continue
            if len(line) > 80:
                bad.append(f"{fn}:{n}: {len(line)} cols: {line[:70]}...")
    return bad


def main(argv):
    if "--list" in argv:
        print("\n".join(SCENARIOS))
        return 0
    names = [a for a in argv if not a.startswith("-")] or list(SCENARIOS)
    if names == ["@all"]:
        names = list(SCENARIOS)
    elif names == ["@standard"]:        # everything except the long "full"
        names = [n for n in SCENARIOS if n != "full"]
    env = {"PATH": GPATH, "HOME": "/nonexistent", "LANG": "C.UTF-8"}
    sol = solutions(env)
    os.makedirs(OUT, exist_ok=True)
    # Every scenario (and every level of levels_cold) is its own job, with
    # its own home and pseudo-terminal; up to TQ_JOBS run at once (R19).
    jobs = []
    for n in names:
        if n == "levels_cold":
            every = LEVELS + SIDE
            sizes = {lv: sum(1 for t in sol if t.startswith(f"t{lv}_")) for lv in every}
            jobs += [(f"level {lv}", lambda lv=lv: sc_level(sol, lv))
                     for lv in sorted(every, key=lambda lv: -sizes[lv])]
        else:
            jobs.append((n, lambda n=n: SCENARIOS[n](sol)))
    jobs.sort(key=lambda j: j[0] != "full")     # the longest one first
    try:
        width = int(os.environ.get("TQ_JOBS") or min(os.cpu_count() or 2, 8))
    except ValueError:
        width = 1
    width = max(width, 1)
    print(f"autoplay.py: {len(jobs)} jobs, {width} at a time")

    def run(job):
        t0 = time.time()
        r = job[1]()
        return job[0], r, time.time() - t0

    t_all = time.time()
    allok = True
    with ThreadPoolExecutor(max_workers=width) as pool:
        for name, r, secs in pool.map(run, jobs):
            allok &= r.ok
            print(f"[{'PASS' if r.ok else 'FAIL'}] {name}  ({secs:.0f}s)", flush=True)
            for note in r.notes:
                print("    " + note)
    print(f"    (all jobs: {time.time() - t_all:.0f}s)")
    bad = width_check()
    if bad:
        allok = False
        print(f"[FAIL] width: {len(bad)} transcript lines wider than 80 columns")
        for b in bad[:15]:
            print("    " + b)
    else:
        print("[PASS] width: all transcript lines fit 80 columns")
    return 0 if allok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
