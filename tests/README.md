# Tests

The game is tested by playing it. Two automated players (a Bash one that
needs nothing but a stock system, and a Python one built on a different
pseudo-terminal) start the real game in a throwaway home folder, read the
known solution of each task from the game itself, and type it. The checks
that play nothing come first.

Your real home folder is never touched: every game gets its own temporary
`HOME`.

## Four levels: pick the one that fits the moment

| Command | What runs | Time (8 cores, Linux) |
|---|---|---|
| `bash tests/run_all.sh quick` | static checks, then Levels 0 and 1 from a fresh home, save and continue, no colours, the live guard | about 30 s |
| `bash tests/run_all.sh` | static checks, then **every level and side quest** from a fresh home, and every scenario | about 70 s |
| `bash tests/run_all.sh full` | everything above, plus the whole game in one home (218 tasks in a row), the second (Python) driver, and on a Mac the runs without developer tools and as Intel code | about 4 min |
| `bash tests/run_all.sh static` | only the checks that play nothing | about 20 s |

The result is printed and saved to `tests/out/report.txt`. A transcript of
every game (colours removed) goes to `tests/out/`.

Useful while editing:

```bash
TQ_COLD_LEVELS="8 9" bash tests/autoplay.sh levels_cold
```

That plays only Levels 8 and 9. `bash tests/autoplay.sh @quick` and
`bash tests/autoplay.sh supervisor` are other examples.

Options (environment): `TQ_BASH=/path/to/bash` picks the bash that runs the
game (default: `/bin/bash` on a Mac), `TQ_JOBS=N` sets how many games run
at once (default: the CPU cores, at most 8), `TQ_PATH=...` sets the `PATH`
the game sees (default on a Mac: the stock one, so Homebrew can't hide
what a player's Mac lacks).

## What is checked

**Static checks** (`static`):

- syntax, using the bash that will run the game
- a scan for Bash 4+ constructs and GNU-only flags, because the Mac's
  `/bin/bash` is 3.2 and its tools are BSD tools
- shellcheck on every script (when installed)
- the design rules (`rules_test.sh`):
  - both builds are up to date, and each one refuses the other system
  - each README talks only about its own system
  - the first tasks need no hard-to-type characters, and every hard
    character has typing help
  - help is named on every prompt, levels have no gaps, and every task
    has a cheatsheet entry
  - the side quests are off the main path, and the portrait only reads
- the safety guard: 150+ command lines, each one either allowed or
  blocked for the right reason (`guard_test.sh`)
- text widths: every text fits 80 columns, in both the Mac and the Linux
  variant (`lint_text.sh`)

**Playthroughs** (`autoplay.sh`, `autoplay.py`):

| Scenario | What it proves |
|---|---|
| `levels_cold` (one job per level) | every level and side quest can be finished from a fresh home, with the known solutions |
| `full` | the whole game in one home: 218 tasks, 16 flags, both side quests offered and finished, the certificate, a correct save file, and nothing written outside the playground |
| `save_continue` | quit in the middle of a level, start again, continue at the same task |
| `reset` | reset deletes progress and rebuilds an empty playground |
| `wrong_hints_ctrlc` | wrong answers get feedback, hints escalate, `keys` help is offered, and Ctrl-C stops a running program without ending the game |
| `guard_live` | dangerous lines typed into the running game are blocked |
| `placement` | the placement check suggests the right level for three kinds of players |
| `nocolor` | `NO_COLOR=1` and `TERM=dumb` produce no colour codes |
| `side_quests` | the side-quest offer, saying no, the level menu, and the way back to the main path |
| `supervisor` | wrong "allow" answers do real damage, the repairs work, and the guard holds even behind a symbolic link |

After the playthroughs, every line the game draws is checked to fit 80
columns, and the transcripts are scanned for system error messages
(encoding errors, "command not found", developer-tool prompts).

## Variants: what is automated, and where

This list comes from looking at what can differ between two players'
computers, and at which of those differences have caused bugs or could.

| Variant | Why it matters | Where it runs |
|---|---|---|
| Linux, Bash 5, GNU tools (Ubuntu x86_64) | most Linux players | every push: standard, `full`, Python driver |
| macOS on Apple silicon, the system's `/bin/bash` 3.2, BSD tools, stock `PATH` | most Mac players; Bash 3.2 lacks many newer features | every push: static + standard; weekly also the whole game in one home |
| A Mac without Apple's developer tools | `python3`, `git` and friends are only install stubs there and open a dialog | every push: every level and scenario with stand-in stubs that fail the test if anything calls them (`TQ_NOCLT=1`); weekly also `full` |
| A real Intel Mac | older Macs, different binaries | every push: every level and scenario on `macos-15-intel` |
| Intel code on Apple silicon (Rosetta 2) | the closest thing to an Intel Mac on a new one | weekly (`@quick`, if the runner has Rosetta) and `full` locally |
| macOS 14, 15 and 26 | each macOS version updates the BSD tools and LibreSSL a little | weekly (`compat.yml`) and by hand before a release |
| Ubuntu 22.04 and 24.04, and Linux on ARM (arm64) | older coreutils and util-linux; ARM laptops | weekly |
| The HTTPS lab's server (Level 19) | `openssl s_server` of OpenSSL binds `127.0.0.1` alone; the Mac's LibreSSL cannot, so there the game runs a small perl server | the Mac jobs use the perl server; on Linux the `full` job plays with it (`TQ_TLSLAB_PERL=1`, with `libio-socket-ssl-perl` installed), the others with `s_server` |
| Two different pseudo-terminal drivers | a bug in one driver can't hide a bug in the game | Bash driver everywhere; Python driver on Linux, every push |
| BSD `script` vs util-linux `script` | the pseudo-terminal programs behave differently (stdin, flushing) | covered by the Mac and Linux jobs |
| No colours (`NO_COLOR`, `TERM=dumb`) | accessibility, and terminals without colour | every run |
| Linux with Bash 3.2 | not something players have (Linux ships Bash 4 or 5); it helps when you develop on Linux for the Mac | locally: build GNU bash 3.2.57 and run `TQ_BASH=/path/to/bash-3.2 bash tests/run_all.sh` |

**Not automated, and why:**

- **Dragging the file into the Terminal window**, and macOS asking whether
  Terminal may open Downloads. Both are clicks in the system's own
  windows. They're checked by hand when the start steps change.
- **Typing on a real keyboard layout** (Option combinations on a German
  Mac, AltGr on Linux, Terminal's "Use Option as Meta key"). The tests
  check that the help text is right and shown at the right moment. Only
  a person can press the keys.
- **How it looks in each terminal app** (Terminal.app, iTerm2, GNOME
  Terminal, Konsole): colours and box characters. The text-width checks
  catch layout breakage. Looks are a playtest question.
- **Whether it's fun and clear.** That needs playtests by real people.
- **Other distributions in containers** (Fedora, Arch, Alpine). This is
  possible, but minimal container images leave out man pages, `nano` and
  `ps`, which a desktop always has. To get it right, each image needs
  its own package list. The Ubuntu jobs cover the GNU userland.

## Why it is fast

- **Parallel by default.** Every scenario, and every level of
  `levels_cold`, is its own job with its own home and pseudo-terminal. Up
  to one job per CPU core runs at once, and the longest jobs start first.
- **Nothing is played twice without a reason.** A cold level plays only
  its own level, not the rest of the game after it. Locally, the
  one-home `full` playthrough (which takes about 3.5 minutes on its own)
  runs only in `full`. In CI it runs in a job of its own, in parallel
  with the rest.
- **CI is a matrix.** The variants above are separate jobs that run at
  the same time, so a push is checked in about the time of its slowest
  job, a few minutes, instead of the sum of all of them. On a Mac the
  one-home playthrough takes about 8 minutes, because its games run one
  after the other. It runs weekly; every push plays the same levels in
  parallel instead.

## Pitfalls

- Never run `tools/build.sh`, and never edit a test script, while a test
  run is going. Bash reads scripts as it runs them, so a changed file
  breaks the run.
- Job control stays on (`set -m`) wherever test players and games are
  started in the background. Without it, bash starts them with Ctrl-C
  ignored. On a Mac the game and `yes` inherit that, and a runaway `yes`
  once wrote 21 GB. `autoplay.sh` refuses to run with Ctrl-C ignored, and
  `autoplay.py` switches it back on. A watchdog fails any game whose
  program runs away (Ctrl-C has no effect, or the log passes 20 MB). It
  stops the game with everything it started and cuts the log to 1 MB.
- On a Mac the Python driver runs with Apple's `/usr/bin/python3`
  (`TQ_PYTHON` overrides it). Under a Homebrew Python, macOS hides the
  network from `netstat`, which breaks a Level 11 task for a reason no
  player has.
