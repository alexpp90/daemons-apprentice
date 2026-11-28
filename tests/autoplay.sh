#!/usr/bin/env bash
# shellcheck disable=SC2059,SC2016,SC2034,SC2015,SC2001,SC2086,SC2010,SC2012
# Automated player in pure Bash (3.2 compatible) for macOS and Linux.
#
# Needs nothing beyond a stock system: it gives the game a real pseudo-
# terminal with the `script` command (BSD script on macOS, util-linux script
# on Linux), feeds keystrokes through a named pipe, and reads the output from
# a log file. Every scenario uses a fresh temporary HOME, so the real home
# folder is never touched. The game runs with TQ_TEST=1 and prints a marker
# <<TQ:task_id>> before each prompt; the driver answers with the task's
# known solution, which it reads from the game itself (TQ_LIB=1).
#
# Usage: bash tests/autoplay.sh [scenario ...]     (default: all)
#        bash tests/autoplay.sh @quick | @standard | @all   (groups)
#        TQ_BASH=/bin/bash bash tests/autoplay.sh   (pick the bash to test)
# Transcripts (colour codes removed) are written to tests/out/sh-*.txt

ROOT=$(cd "$(dirname "$0")/.." && pwd)

OUT="$ROOT/tests/out${TQ_ARCH:+/$TQ_ARCH}${TQ_NOCLT:+/no-devtools}"
GBASH=${TQ_BASH:-bash}
TMPBASE=${TQ_TEST_TMP:-${TMPDIR:-/tmp}}
mkdir -p "$OUT"
trap '' PIPE
# Without job control, bash starts background jobs with Ctrl-C (SIGINT)
# ignored, and the game and every program it runs would inherit that
# (Bash 3.2 does this; then `yes` cannot be stopped). Job control on.
set -m
# If Ctrl-C was already ignored when this script started (it was started in
# the background by a shell without job control), no shell can undo that:
# Ctrl-C would stop nothing in the game and `yes` would run until the disk
# is full. Check it with a `sleep` and stop with an explanation instead:
# stopped by Ctrl-C, it ends with status 130; ignoring it, it sleeps on.
if [ "${1:-}" != --job ] &&
   ! ( set -m; sleep 2 & p=$!; kill -INT "$p"; wait "$p"; [ $? -eq 130 ] ) 2>/dev/null; then
  echo "autoplay.sh: Ctrl-C (SIGINT) is ignored in this process, so the game could not be"
  echo "  tested. It was started in the background by a script without job control."
  echo "  Start it in the foreground, or put 'set -m' before the '&' (tests/run_all.sh does)."
  exit 2
fi

case "$(uname -s)" in Darwin) OS=mac; GLANG=en_US.UTF-8 ;; *) OS=linux; GLANG=C.UTF-8 ;; esac
# The PATH the game sees. On a Mac it is the stock one by default, so the
# tests see what a player sees (macOS's own openssl, no Homebrew tools),
# whatever is installed here. TQ_PATH=... overrides it.
if [ "$OS" = mac ]; then GPATH=${TQ_PATH:-/usr/bin:/bin:/usr/sbin:/sbin}; else GPATH=${TQ_PATH:-$PATH}; fi
# TQ_ARCH=x86_64 on an Apple silicon Mac runs the game and every command it
# starts as Intel code under Rosetta 2 (the closest thing to an Intel Mac).
GRUN=$GBASH
if [ -n "${TQ_ARCH:-}" ]; then
  [ "$OS" = mac ] || { echo "TQ_ARCH works on macOS only"; exit 2; }
  arch "-$TQ_ARCH" /usr/bin/true 2>/dev/null || { echo "cannot run $TQ_ARCH code here (Rosetta 2 not installed?)"; exit 2; }
  GRUN="arch -$TQ_ARCH $GBASH"
fi
# TQ_NOCLT=1: play as a Mac without Apple's developer tools. The game is
# told so (TQ_TEST_NOCLT), and stand-ins for the tools that are only
# install stubs there come first in PATH: if anything runs one, it leaves
# a mark in the transcript and the test fails.
NOCLT_ENV=""
STUBDIR=""
if [ -n "${TQ_NOCLT:-}" ]; then
  STUBDIR="${TQ_TEST_TMP:-${TMPDIR:-/tmp}}/tq-stubs.$$"
  mkdir -p "$STUBDIR"
  for t in python3 pip3 git make cc gcc clang otool strings nm dyld_info xcrun; do
    printf '#!/bin/sh\necho "TQ-STUB-RAN: %s would open the install dialog" >&2\nexit 1\n' "$t" > "$STUBDIR/$t"
    chmod +x "$STUBDIR/$t"
  done
  GPATH="$STUBDIR:$GPATH"
  NOCLT_ENV="TQ_TEST_NOCLT=1"
fi
# The version a player on this system gets (built by tools/build.sh).
GAME="$ROOT/game/$OS/daemons-apprentice.sh"
# The level numbers in play order, as the game itself lists them.
ALL_LEVELS=$(env -i PATH="$GPATH" HOME=/nonexistent "$GBASH" -c 'TQ_LIB=1; . "$1"; echo "$TQ_LEVELS"' x "$GAME")
# The side quests (R21, R22): off the main path, played like levels.
SIDE_LEVELS=$(env -i PATH="$GPATH" HOME=/nonexistent "$GBASH" -c 'TQ_LIB=1; . "$1"; echo "$TQ_SIDE"' x "$GAME")
# "Level 4" or "Side quest S1", as the game writes it.
lname() { case "$1" in S*) echo "Side quest $1" ;; *) echo "Level $1" ;; esac; }
if script --version 2>/dev/null | grep -q util-linux; then SCRIPT_KIND=util; else SCRIPT_KIND=bsd; fi

ESC=$(printf '\033')
CR=$(printf '\r')
MARK='<<TQ:[A-Za-z0-9_]+(:q[0-9]+)?>>'

FAILS=0
SC_FAIL=0
fail() { SC_FAIL=1; echo "    FAIL: $*"; }
note() { echo "    $*"; }

# --------------------------------------------------------------- the game

# start_game NAME [VAR=value ...]   (uses $HOMEDIR, set by new_home)
start_game() {
  NAME=$1; shift
  LOG="$OUT/sh-$NAME.raw"
  FIFO="$HOMEDIR/.driver-in"
  rm -f "$LOG" "$FIFO"; : > "$LOG"
  mkfifo "$FIFO" || { echo "mkfifo failed"; exit 2; }
  local inner="stty columns 80 rows 30 2>/dev/null; exec $GRUN '$GAME'"
  # The keys go through `cat` and a pipe, not straight from the FIFO:
  # BSD script on macOS quits when its stdin is a FIFO ("tcgetattr/ioctl:
  # Operation not supported on socket"); a pipe is fine everywhere.
  cat "$FIFO" | (
    cd "$HOMEDIR" || exit 1
    if [ "$SCRIPT_KIND" = util ]; then
      exec env -i HOME="$HOMEDIR" PATH="$GPATH" TERM=xterm-256color LANG="$GLANG" \
        USER="${USER:-player}" TMPDIR="$HOMEDIR" TQ_TEST=1 $NOCLT_ENV ${BASH_COMPAT:+BASH_COMPAT=$BASH_COMPAT} ${TQ_TLSLAB_PERL:+TQ_TLSLAB_PERL=1} "$@" \
        script -q -f -c "$GBASH -c \"$inner\"" /dev/null
    else
      exec env -i HOME="$HOMEDIR" PATH="$GPATH" TERM=xterm-256color LANG="$GLANG" \
        USER="${USER:-player}" TMPDIR="$HOMEDIR" TQ_TEST=1 $NOCLT_ENV ${BASH_COMPAT:+BASH_COMPAT=$BASH_COMPAT} ${TQ_TLSLAB_PERL:+TQ_TLSLAB_PERL=1} "$@" \
        script -q /dev/null $GRUN -c "$inner"
    fi
  ) > "$LOG" 2>&1 &
  GPID=$!
  exec 7> "$FIFO"
  POS=0
}

alive() { kill -0 "$GPID" 2>/dev/null; }

# Stop the game at once with everything it started (a runaway `yes` too):
# script, the game and its programs, found through their parent PIDs
# before any of them is stopped.
kill_game() {
  local all=$GPID kids=$GPID
  while [ -n "$kids" ]; do
    kids=$(ps -A -o pid= -o ppid= | awk -v l=" $kids " 'index(l, " " $2 " ") {print $1}' | tr '\n' ' ')
    kids=${kids% }
    all="$all $kids"
  done
  # shellcheck disable=SC2086  # $all is a list of PIDs
  kill -9 $all 2>/dev/null
  # The keys now go nowhere: the `cat` that fed them gets end-of-file and
  # ends, so the wait (for the whole pipeline) returns, and alive() says no.
  exec 7>/dev/null
  wait "$GPID" 2>/dev/null
}

# A program ran away (Ctrl-C did not stop it, or the log grew too big):
# fail, stop it, and keep only the first megabyte of its log, so neither
# the disk nor the searches through the log are swamped.
RUNAWAY_MAX=20000000
runaway() {
  fail "$1"
  kill_game
  head -c 1000000 "$LOG" > "$LOG.cut" 2>/dev/null
  printf '\n[autoplay: log cut here, a program ran away]\n' >> "$LOG.cut"
  mv -f "$LOG.cut" "$LOG"
  POS=$(logsize)
}

stop_game() {
  sleep 0.5
  exec 7>&- 2>/dev/null
  sleep 0.3
  kill "$GPID" 2>/dev/null
  wait "$GPID" 2>/dev/null
  sed -e "s/${ESC}\[[0-9;?]*[A-Za-z]//g" -e "s/${ESC}[()][A-Z0-9]//g" -e "s/${ESC}[=>]//g" -e "s/$CR//g" \
    "$LOG" > "$OUT/sh-$NAME.txt" 2>/dev/null
  # TQ_KEEP_RAW=1 keeps the raw output with colours (tools/screenshots/).
  [ -n "${TQ_KEEP_RAW:-}" ] && cp "$LOG" "$OUT/sh-$NAME.ansi"
  rm -f "$LOG" "$FIFO"
}

# Writes run in a subshell: if the game has died, Bash 3.2 keeps the failed
# printf output buffered and leaks it into the next command substitution.
send()  { ( printf '%s\r' "$1" ) >&7 2>/dev/null; }    # a line, then Enter

logsize() { wc -c < "$LOG" 2>/dev/null | tr -d ' '; }
# Press Ctrl-C until the output stops growing (a program like `yes` may
# start a moment after Enter; a Ctrl-C before that would hit the game).
ctrl_c_until_quiet() {
  local i a b
  for i in 1 2 3 4 5 6; do
    keys '\003'; sleep 0.6
    a=$(logsize); sleep 0.4; b=$(logsize)
    [ $((b - a)) -lt 2000 ] && return 0
    [ "$b" -gt "$RUNAWAY_MAX" ] && break
  done
  runaway "Ctrl-C did not stop the running program"
}
keys()  { ( printf "$1" ) >&7 2>/dev/null; }         # raw keys, printf escapes

# expect ERE [TIMEOUT]: wait for the pattern after the read position.
# Sets M to the matched text. Returns 0 found, 1 timeout, 2 game ended.
expect() {
  local end r t=${2:-20}
  end=$(( $(date +%s) + t ))
  while :; do
    r=$(LC_ALL=C tail -c +$((POS + 1)) "$LOG" 2>/dev/null | PAT=$1 LC_ALL=C awk '
      { if (match($0, ENVIRON["PAT"])) { print off + RSTART - 1 + RLENGTH; print substr($0, RSTART, RLENGTH); exit }
        off += length($0) + 1 }')
    case ${r%%
*} in ''|*[!0-9]*) r="" ;; esac
    if [ -n "$r" ]; then
      POS=$((POS + ${r%%
*}))
      M=${r#*
}
      return 0
    fi
    if ! alive; then
      # one more look after the game ended
      r=$(LC_ALL=C tail -c +$((POS + 1)) "$LOG" 2>/dev/null | PAT=$1 LC_ALL=C awk '{ if (match($0, ENVIRON["PAT"])) { print "x"; exit } }')
      [ -n "$r" ] && { M=$1; return 0; }
      return 2
    fi
    # Safety net: never let a runaway program fill the disk. Checked before
    # the timeout, which a slow search through a big log could hit first.
    if [ "$(logsize)" -gt "$RUNAWAY_MAX" ]; then
      runaway "log over 20 MB, a program ran away; stopping this game"
      return 2
    fi
    [ "$(date +%s)" -ge "$end" ] && return 1
    sleep 0.1
  done
}

# The whole transcript so far, without colours (for content checks).
transcript() { sed -e "s/${ESC}\[[0-9;?]*[A-Za-z]//g" -e "s/$CR//g" "$LOG" 2>/dev/null; }
has_text() { transcript | grep -F -q -- "$1"; }

new_home() {
  HOMEDIR=$(mktemp -d "$TMPBASE/tq-home.XXXXXX") || exit 2
  HOMEDIR=$(cd "$HOMEDIR" && pwd -P)
}
drop_home() { chmod -R u+rwx "$HOMEDIR" 2>/dev/null; rm -rf "$HOMEDIR"; }

# --------------------------------------------------------------- solutions

SOLFILE="$TMPBASE/tq-solutions.$$"
DRIVEFILE="$TMPBASE/tq-drive.$$"
make_solutions() {
  env -i PATH="$GPATH" HOME=/nonexistent LANG="$GLANG" $NOCLT_ENV $GRUN -c '
    TQ_LIB=1; . "$1"; tq_detect_os; tq_find_sha; tq_find_pkg; tq_has_gui(){ return 1; }
    for l in $TQ_LEVELS $TQ_SIDE; do
      for t in $(tq_L$l tasks); do
        $t solution | sed "s/^/$t	/"
        d=$($t drive 2>/dev/null); [ -n "$d" ] && echo "$t	$d" >&3
        if [ "$($t kind 2>/dev/null)" = quiz ]; then
          a=""; i=1; while [ $i -le $($t qn) ]; do a="$a $($t q$i | sed -n 3p)"; i=$((i+1)); done
          echo "$t	quiz$a" >&3
        fi
      done
    done' x "$GAME" > "$SOLFILE" 3> "$DRIVEFILE"
}
drive_of() { awk -F'	' -v id="$1" '$1==id {print $2}' "$DRIVEFILE"; }
sol_count() { awk -F'	' -v id="$1" '$1==id' "$SOLFILE" | awk 'END{print NR}'; }
sol_line()  { awk -F'	' -v id="$1" '$1==id' "$SOLFILE" | sed -n "$2p" | cut -f2-; }

# In nano: Ctrl-O, Enter (save under the same name), Ctrl-X.
nano_save_quit() { keys '\017'; sleep 0.6; keys '\r'; sleep 0.6; keys '\030'; }

# Tasks that need keystrokes rather than (or in addition to) a typed line.
# The game tags them with a "drive" sub-command; the solution says what to
# type first.
special() {
  case "$(drive_of "$1")" in
    ctrlc) send "$(sol_line "$1" 1 | awk '{print $1}')"; expect "^y$CR?\$" 10; ctrl_c_until_quiet ;;
    uparrow-fix) keys '\033[A'; sleep 0.3; keys '\033[D\033[D\033[D\033[D\033[D\033[D'; sleep 0.2
                 keys '\177\177\177'; sleep 0.2; keys 'cho\r' ;;
    man) send "$(sol_line "$1" 1 | awk '{print $1, $2}')"; sleep 1.5; keys 'q' ;;
    top) send "top"; sleep 2; keys 'q' ;;
    ctrlc-once) send "$(sol_line "$1" 1 | awk '{print $1, $2}')"; sleep 1.5; keys '\003' ;;
    nano-welcome) send "nano welcome.txt"; sleep 1.2
          keys '\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B'; sleep 0.3
          keys '\005Ada'; sleep 0.3; nano_save_quit ;;
    nano-todo) send "nano todo.txt"; sleep 1.2
          keys '\033[B\033[B'; sleep 0.2; keys '\013'; sleep 0.2
          keys '\033[B'; sleep 0.2; keys '\013'; sleep 0.3; nano_save_quit ;;
    nano-hello) send "nano hello.sh"; sleep 1.2
          keys '\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B'; sleep 0.3
          keys 'echo "Hello from my first script"'; sleep 0.3; nano_save_quit ;;
    nano-greet) send "nano greet.sh"; sleep 1.2
          keys '\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B\033[B'; sleep 0.3
          keys 'name="$1"\recho "Hello, $name! Today is $(date +%%A)."'; sleep 0.3; nano_save_quit ;;
    ctrlc-cont) send "$(sol_line "$1" 1)"; expect "$MARK" 6; sleep 0.5; keys '\003' ;;
    # Only this game's own practice process: other games run next to it.
    killpid) send 'kill $(cat ~/apprentice-playground/lab/.miner.pid)' ;;
    *) return 1 ;;
  esac
  return 0
}

# Can openssl here (same PATH, same architecture as the game) do -pbkdf2?
# The same round trip the game tries (tq_pbkdf2_ok), done independently.
pbkdf2_here() {
  if [ -z "${PBKDF2_HERE:-}" ]; then
    PBKDF2_HERE=0
    env -i PATH="$GPATH" $GRUN -c 'printf x | openssl enc -aes-256-cbc -pbkdf2 -iter 2 -pass pass:x 2>/dev/null |
      openssl enc -d -aes-256-cbc -pbkdf2 -iter 2 -pass pass:x 2>/dev/null | grep -q "^x\$"' 2>/dev/null && PBKDF2_HERE=1
  fi
  [ "$PBKDF2_HERE" = 1 ]
}

# play [STOP_ID]: answer every prompt with solutions until the main menu
# appears (PLAY_END=menu) or task STOP_ID comes up (PLAY_END=STOP_ID).
play() {
  local stop=${1:-} last="" id q k n steps=0 vars t p0
  vars=""
  PLAY_END=""
  while [ "$steps" -lt 3000 ]; do
    steps=$((steps + 1))
    # A task tagged "slow" walks through real folders (R22): give it time.
    t=30; [ -n "$last" ] && [ "$(drive_of "$last")" = slow ] && t=240
    p0=$POS
    expect "$MARK|\\[ press Enter \\]|Your choice:|\\[y/n\\]" "$t"
    case $? in
      1) fail "timeout (last task $last)"; PLAY_END=timeout; return ;;
      2) PLAY_END=eof; return ;;
    esac
    case "$M" in
      "[ press Enter ]") keys '\r'; continue ;;
      "Your choice:") PLAY_END=menu; return ;;
      "[y/n]") send "${PLAY_YN:-y}"; continue ;;
    esac
    id=${M#<<TQ:}; id=${id%>>}
    q=""
    case "$id" in *:q*) q=${id#*:q}; id=${id%%:*} ;; esac
    if [ -n "$q" ]; then
      sleep 0.1
      # shellcheck disable=SC2046  # the answers are words on purpose
      set -- $(drive_of "$id")
      shift "$q"; send "$1"
      continue
    fi
    if [ -n "$stop" ] && [ "$id" = "$stop" ] && [ "$id" != "$last" ]; then PLAY_END=$id; return; fi
    # PLAY_LEVEL=N: stop as soon as a task of another level comes up.
    if [ -n "${PLAY_LEVEL:-}" ]; then
      k=${id#t}; k=${k%%_*}
      [ "$k" != "$PLAY_LEVEL" ] && { PLAY_END=nextlevel; return; }
    fi
    last=$id
    sleep 0.15
    eval "k=\${SENT_$id:-0}"
    n=$(sol_count "$id")
    case "$(drive_of "$id")" in ''|quiz*) ;; *) n=1 ;; esac
    # A lab the game says cannot run on this computer: skip it, as the game
    # asks the player to, but only if openssl here really cannot do -pbkdf2
    # (as on macOS 27 under Rosetta 2, where Intel openssl crashes on it).
    if LC_ALL=C tail -c +$((p0 + 1)) "$LOG" 2>/dev/null | head -c $((POS - p0)) |
         grep -q 'this lab cannot run on this computer'; then
      if pbkdf2_here; then
        fail "$id: the game says this lab cannot run, but openssl here can do -pbkdf2"
      else
        note "$id: openssl here cannot do -pbkdf2, the game says so; skipped as it asks"
        LAB_SKIPS=$((${LAB_SKIPS:-0} + 1))
      fi
      send skip
      continue
    fi
    if [ "$k" -ge "$n" ]; then
      fail "$id: not solved by its solution; skipping"
      send skip
      eval "SENT_$id=\$((k + 1))"
      continue
    fi
    if ! special "$id"; then
      send "$(sol_line "$id" $((k + 1)))"
    fi
    eval "SENT_$id=\$((k + 1))"
    vars="$vars SENT_$id"
  done
  fail "too many steps"
}
reset_sent() { local v; for v in $(set | sed -n 's/^\(SENT_[A-Za-z0-9_]*\)=.*/\1/p'); do unset "$v"; done; }

menu_level() {   # at the main menu: choose level $1
  expect "Your choice:" 15 || { fail "no main menu"; return 1; }
  send l
  expect "Level number" 10 || { fail "no level prompt"; return 1; }
  send "$1"
}

# --------------------------------------------------------------- scenarios

sc_full() {
  new_home; start_game full; reset_sent
  expect "Your choice:" 15 || fail "no main menu"
  send n
  play
  [ "$PLAY_END" = menu ] || fail "did not return to the menu at the end ($PLAY_END)"
  local last=${ALL_LEVELS##* }
  transcript | grep -q "Level $last: \([0-9]*\) of \1 tasks solved" || fail "Level $last not fully solved"
  for t in "CERTIFICATE OF MASTERY" \
           "FLAG CAPTURED: FLAG{dots-hide-things}" "FLAG CAPTURED: FLAG{defender-certified}" \
           "FLAG CAPTURED: FLAG{quarantine-cleared}" \
           "FLAG CAPTURED: FLAG{walls-within-walls}" "You judged 6 of 6 correctly" \
           "FLAG CAPTURED: FLAG{magic-bytes-never-lie}" \
           "FLAG CAPTURED: FLAG{inodes-entropy-and-diffs}" \
           "FLAG CAPTURED: FLAG{look-37-percent-then-leap}" \
           "FLAG CAPTURED: FLAG{algorithms-to-live-by}" \
           "FLAG CAPTURED: FLAG{caught-the-midnight-breach}" \
           "FLAG CAPTURED: FLAG{blue-team-responder}" \
           "A side quest is open: Portrait of Your Computer" "Side quest S1: 13 of 13 tasks solved" \
           "FLAG CAPTURED: FLAG{know-your-machine}" \
           "A side quest is open: The Supervisor" "Side quest S2: 11 of 11 tasks solved" \
           "Your record: 11 of 11" "FLAG CAPTURED: FLAG{read-first-then-allow}" \
           "A side quest is open: Security Checkup of Your Machine" \
           "Side quest S3: 8 of 8 tasks solved" "FLAG CAPTURED: FLAG{know-your-defenses}" \
           "All 3 side quests done, too."; do
    has_text "$t" || fail "transcript lacks: $t"
  done
  send q; expect "Progress saved" 8
  stop_game
  local save="$HOMEDIR/.apprentice-save"
  grep -q "^levels_done=$ALL_LEVELS\$" "$save" || fail "save does not list all levels: $(grep levels_done "$save")"
  grep -q "^side_done=$SIDE_LEVELS\$" "$save" || fail "save does not list the side quests: $(grep side_done "$save")"
  grep -q "^back=" "$save" && fail "save still has a way back from a side quest: $(grep '^back' "$save")"
  [ -e "$HOMEDIR/.bashrc" ] || [ -e "$HOMEDIR/.zshrc" ] && fail "the agent wrote into the home folder"
  grep -q "Awake for: " "$HOMEDIR/apprentice-playground/portrait/my-computer.txt" 2>/dev/null || fail "my-computer.txt has no answers"
  grep -q "Account & privilege:" "$HOMEDIR/apprentice-playground/checkup/security-report.txt" 2>/dev/null || fail "security-report.txt has no answers"
  [ -s "$HOMEDIR/apprentice-playground/CHEATSHEET.md" ] || fail "CHEATSHEET.md missing or empty"
  [ -s "$HOMEDIR/apprentice-playground/TROPHIES.md" ] || fail "TROPHIES.md missing or empty"
  [ -s "$HOMEDIR/apprentice-playground/CERTIFICATE.txt" ] || fail "CERTIFICATE.txt missing or empty"
  note "tasks recorded as solved: $(sed -n 's/^solved=//p' "$save" | wc -w | tr -d ' ')"
  note "flags: $(sed -n 's/^flags=//p' "$save" | wc -w | tr -d ' ')"
  drop_home
}

# Every level from the level menu in a fresh home: one job per level
# (sc_level N), so the jobs can run at the same time. Each plays its own
# level only, up to the next level's first task.
sc_levels_cold() {
  local l
  # TQ_COLD_LEVELS="8 9" plays only those levels (quicker while editing).
  for l in ${TQ_COLD_LEVELS:-$ALL_LEVELS $SIDE_LEVELS}; do sc_level "$l"; done
}
sc_level() {
  local l=$1
  new_home; start_game "level${l}_cold"; reset_sent
  menu_level "$l"
  LAB_SKIPS=0
  PLAY_LEVEL=$l play
  if [ "$LAB_SKIPS" -gt 0 ]; then   # solved = all but the labs the game said to skip
    transcript | grep -E "$(lname "$l"): [0-9]+ of [0-9]+ tasks solved" | tail -n 1 |
      awk -v s="$LAB_SKIPS" '{ for (i = 2; i < NF; i++) if ($i == "of") { x = $(i-1); y = $(i+1) } } END { exit !(y - x == s) }' ||
      fail "level $l: not all tasks solved cold but the $LAB_SKIPS skipped labs ($PLAY_END)"
  else
    transcript | grep -q "$(lname "$l"): \([0-9]*\) of \1 tasks solved" || fail "level $l: not all tasks solved cold ($PLAY_END)"
  fi
  if [ "$PLAY_END" = menu ]; then send q; else send quit; fi
  expect "Progress saved" 8
  stop_game; drop_home
}

sc_save_continue() {
  new_home; start_game save_continue_1; reset_sent
  menu_level 2
  play t2_4
  [ "$PLAY_END" = t2_4 ] || fail "did not reach t2_4 ($PLAY_END)"
  sleep 0.2; send quit
  expect "Progress saved" 8 || fail "no 'Progress saved' on quit"
  stop_game
  grep -q '^level=2$' "$HOMEDIR/.apprentice-save" && grep -q '^task=4$' "$HOMEDIR/.apprentice-save" \
    || fail "save not at level 2 task 4"
  start_game save_continue_2
  expect "Welcome back! Saved progress: level 2, task 4" 10 || fail "no welcome-back line"
  expect "Your choice:" 10; send c
  expect "continuing at task 4" 10 || fail "no 'continuing at task' screen"
  expect "\\[ press Enter \\]" 8; keys '\r'
  expect "$MARK" 10; [ "$M" = "<<TQ:t2_4>>" ] || fail "continue did not resume at t2_4 (got $M)"
  sleep 0.2; send "cat ~/.apprentice-save"
  expect "level=2" 6 || fail "cat of the save file did not show level=2"
  expect "$MARK" 6; sleep 0.3
  keys '\004'
  expect "Progress saved" 6 || fail "Ctrl-D did not quit with 'Progress saved'"
  stop_game; drop_home
}

sc_reset() {
  new_home; start_game reset; reset_sent
  menu_level 3
  play t3_3
  sleep 0.2; send menu
  expect "Your choice:" 8; send r
  expect "\\[y/n\\]" 8; send y
  expect "Clean slate" 6 || fail "no reset confirmation"
  sleep 0.3
  [ -f "$HOMEDIR/.apprentice-save" ] && fail "save file still exists after reset"
  [ -d "$HOMEDIR/apprentice-playground" ] || fail "playground not rebuilt"
  [ -e "$HOMEDIR/apprentice-playground/workshop" ] && fail "playground not emptied"
  expect "New game" 6 || fail "menu after reset does not offer a new game"
  send q; stop_game; drop_home
}

# Press Enter at every pause (intro chunks, cards) until a task prompt.
until_mark() {
  local i=0
  while [ "$i" -lt 12 ]; do
    i=$((i + 1))
    expect "$MARK|\\[ press Enter \\]" 10 || { fail "no task prompt"; return 1; }
    [ "$M" = "[ press Enter ]" ] || return 0
    keys '\r'
  done
  fail "too many pauses"
}

# After a solved (or skipped) task: press Enter at the pause, then the
# next task's marker must be ID.
next_task() {
  expect "\\[ press Enter \\]" 8 || fail "no pause after $2"
  keys '\r'
  expect "$MARK" 8; [ "$M" = "<<TQ:$1>>" ] || fail "$2: expected $1, got $M"
}

sc_wrong_hints_ctrlc() {
  new_home; start_game wrong_hints_ctrlc; reset_sent
  menu_level 0
  until_mark; sleep 0.2
  [ "$M" = "<<TQ:t0_1>>" ] || fail "level 0 does not start with t0_1 ($M)"
  send help
  next_task t0_2 "help solves t0_1"
  sleep 0.2
  step() {   # LINE PATTERN LABEL
    send "$1"
    expect "$2" 8 || fail "$3: expected /$2/"
    expect "$MARK" 8; sleep 0.1
  }
  step hello "So close" "wrong answer feedback"
  step hint "Hint 1 of 3" "hint 1"
  step hint "Hint 2 of 3" "hint 2"
  step hint "Solution:" "hint 3"
  step explain "argument" "explain shows the task"
  step status "Your progress" "status"
  step help "Help in this game" "help"
  [ "$M" = "<<TQ:t0_2>>" ] || fail "help solved a task other than the first one ($M)"
  step keys "German keyboard" "keys"
  step cheat "Your Growing Cheatsheet" "cheatsheet command"
  step trophies "Trophy Cabinet" "trophies command"
  step "ls /definitely-not-here" "reported a problem" "error feedback"
  step "ehco hi" "bash: ehco: command not found" "shell-like error message"
  step "if then" "syntax error" "syntax error message"
  step "  | completes it. If several names fit" "text from the game" "pasted game text is not run"
  step "$ whoami" "Leave out the" "a pasted prompt is explained"
  step "pwd" "Good look around" "looking around is not called wrong"
  # Ctrl-C at the prompt drops the half-typed line, like a real shell.
  keys 'ech\003'; sleep 0.3
  expect "ech\\^C" 4 || fail "Ctrl-C at the prompt did not show ^C"
  send 'echo hello'
  next_task t0_3 "Ctrl-C at the prompt"
  sleep 0.2; send "sleep 30"; sleep 1.5; keys '\003'
  if ! expect "Stopped with Ctrl-C" 4; then keys '\003'; expect "Stopped with Ctrl-C" 4 || fail "Ctrl-C during sleep: no feedback"; fi
  expect "$MARK" 6 || fail "game did not survive Ctrl-C during a command"
  sleep 0.2; send "sleep 2"; sleep 0.5; keys '\032'
  expect "$MARK" 8 || fail "Ctrl-Z suspended or killed the game"
  sleep 0.2; send "for i in 1 2"
  sleep 0.3; send "do echo round-\$i"
  sleep 0.3; send "done"
  expect "round-2" 6 || fail "multi-line loop with > continuation did not run"
  expect "$MARK" 6; sleep 0.2
  send 'echo "half'; expect "$MARK" 6 || fail "no > for an open quote"
  sleep 0.3; keys '\003'
  expect "unfinished command is dropped" 6 || fail "Ctrl-C at > did not drop the command"
  expect "$MARK" 6; sleep 0.2
  send skip
  next_task t0_4 "skip"
  sleep 0.2; send quit
  expect "Progress saved" 6 || fail "quit mid-level did not save"
  stop_game
  grep -q '^skipped=t0_3$' "$HOMEDIR/.apprentice-save" && grep -q '^task=4$' "$HOMEDIR/.apprentice-save" \
    || fail "save after skip/quit is wrong"
  drop_home
}

sc_guard_live() {
  new_home; start_game guard_live; reset_sent
  menu_level 3
  until_mark; sleep 0.2
  blocked() {
    send "$1"
    expect "$2" 6 || fail "not blocked as expected: $1"
    expect "$MARK" 6; sleep 0.1
  }
  blocked 'rm -rf ~' "outside the playground"
  blocked 'sudo ls' "sudo"
  blocked 'echo hi > ~/oops.txt' "outside the playground"
  blocked 'rm -r ../../apprentice-playground' "outside the playground"
  blocked 'kill -9 -1' "kill -1"
  blocked 'exec bash' "exec"
  blocked 'curl -fsSL https://example.com/x.sh | bash' "piping it straight"
  blocked 'for f in ~/*; do rm "$f"; done' "cannot tell in advance"
  blocked 'set -e' "how the game itself runs"
  blocked 'kill $$' "the game itself"
  [ -e "$HOMEDIR/oops.txt" ] && fail "a write outside the playground happened"
  send 'cd .. && pwd'
  expect "apprentice-playground$CR" 6 || fail "cd .. && pwd did not land in the playground"
  expect "$MARK" 6; sleep 0.1
  send 'export GREETING=hi; echo "$GREETING there"'; expect "hi there" 6; expect "$MARK" 6; sleep 0.1
  send 'echo $GREETING-again'
  expect "hi-again" 6 || fail "exported variable did not persist"
  expect "$MARK" 6; sleep 0.1
  send 'ls nothing-here; echo "code $?"'
  expect "code [12]" 6 || fail "\$? not available in the same line"
  expect "$MARK" 6; sleep 0.1
  send false; expect "$MARK" 6; sleep 0.1
  send 'echo "last $?"'
  expect "last 1" 6 || fail "\$? from the previous line not kept"
  expect "$MARK" 6; sleep 0.1
  send quit; expect "Progress saved" 6
  stop_game; drop_home
}

# The side quests (R21, R22): the offer after a level and saying no, the
# level menu, quitting inside a side quest, and the way back to the main
# path when it is done.
sc_side_quests() {
  local save pg
  new_home; start_game side_quests_1; reset_sent
  save="$HOMEDIR/.apprentice-save"; pg="$HOMEDIR/apprentice-playground"
  menu_level 6
  PLAY_YN=n PLAY_LEVEL=6 play
  [ "$PLAY_END" = nextlevel ] && [ "$M" = "<<TQ:t7_1>>" ] || fail "after 'no' the game did not go on with Level 7 ($PLAY_END, $M)"
  has_text "A side quest is open: Portrait of Your Computer" || fail "no side quest offer after Level 6"
  has_text "It waits in the level menu" || fail "saying no to the offer is not acknowledged"
  sleep 0.2; send menu
  expect "Your choice:" 8 || fail "no main menu"
  send l
  expect "Side quests" 8 || fail "the level menu does not list the side quests"
  expect "Level number" 8; send s1
  PLAY_LEVEL=S1 play tS1_3
  [ "$PLAY_END" = tS1_3 ] || fail "did not reach tS1_3 ($PLAY_END)"
  sleep 0.2; send status
  expect "Now in side quest S1" 6 || fail "status does not name the side quest"
  expect "$MARK" 6; sleep 0.2; send quit
  expect "Progress saved" 8 || fail "no 'Progress saved' on quit"
  stop_game
  grep -q '^level=S1$' "$save" && grep -q '^task=3$' "$save" || fail "save not at side quest S1 task 3"
  grep -q '^back=7$' "$save" && grep -q '^back_task=1$' "$save" || fail "save does not remember the main path (Level 7)"
  grep -q "Programs running: [0-9]" "$pg/portrait/my-computer.txt" 2>/dev/null || fail "my-computer.txt lacks the answer of task 2"
  start_game side_quests_2
  expect "Welcome back! Saved progress: side quest S1, task 3" 10 || fail "no welcome-back line for the side quest"
  expect "Your choice:" 10; send c
  PLAY_LEVEL=S1 play
  [ "$PLAY_END" = nextlevel ] && [ "$M" = "<<TQ:t7_1>>" ] || fail "after the side quest the game did not return to Level 7 ($PLAY_END, $M)"
  has_text "Side quest S1: 13 of 13 tasks solved" || fail "side quest S1 not fully solved"
  has_text "FLAG CAPTURED: FLAG{know-your-machine}" || fail "no flag for the portrait"
  sleep 0.2; send quit; expect "Progress saved" 8
  stop_game
  grep -q '^side_done=S1$' "$save" || fail "save does not list S1 as done"
  grep -q '^level=7$' "$save" || fail "save is not back at Level 7"
  grep -q '^back=' "$save" && fail "save still has a way back"
  grep -q '^levels_done=6$' "$save" || fail "the side quest changed levels_done: $(grep levels_done "$save")"
  for t in "Awake for: " "Hungriest program: " '    $ uptime' "Files "; do
    grep -q -F -- "$t" "$pg/portrait/my-computer.txt" || fail "my-computer.txt lacks: $t"
  done
  awk 'length > 74' "$pg/portrait/my-computer.txt" | grep -q . && fail "my-computer.txt has lines wider than 74 columns"
  drop_home
}

# The Supervisor (R21) with wrong calls: a yes too many really runs and is
# repaired, a no too many, the guard as the second wall, and the safety
# net when the project folder is not the real one.
sc_supervisor() {
  local site p
  new_home; start_game supervisor; reset_sent
  site="$HOMEDIR/apprentice-playground/mission/site"
  menu_level S2
  until_mark; sleep 0.2
  [ "$M" = "<<TQ:tS2_1>>" ] || fail "the side quest does not start with tS2_1 ($M)"
  step() {   # LINE PATTERN LABEL
    send "$1"
    expect "$2" 8 || fail "$3: expected /$2/"
    expect "$MARK" 8; sleep 0.15
  }
  call() {   # ANSWER PATTERN LABEL: a decision that ends the step
    send "$1"
    expect "$2" 8 || fail "$3: expected /$2/"
  }
  ouch() {   # allow something bad: the damage is shown, then the prompt
    send allow
    expect "one yes too many" 8 || fail "$1: a wrong yes is not called one"
    expect "\\[ press Enter \\]" 8; keys '\r'
    expect "Look at the damage" 8 || fail "$1: the damage is not shown"
    expect "$MARK" 8; sleep 0.2
  }
  step why "The agent explains" "why"
  step ls "You looked around" "looking around"
  step "sudo ls" "Blocked: sudo" "the guard while looking around"
  step hint "Hint 1 of 3" "hint 1"
  call allow "Right call" "allow a harmless command"
  next_task tS2_2 "tS2_1"; sleep 0.2
  call deny "this one was fine" "a no too many"
  expect "cache-1.tmp" 8 || fail "after a needless no the command did not run"
  next_task tS2_3 "tS2_2"; sleep 0.2
  ouch "rm -rf tmp/ *"
  [ -e "$site/index.html" ] && fail "allowed rm did not really run"
  [ -f "$site/.env" ] || fail "rm took the hidden file too"
  [ -d "$HOMEDIR/apprentice-playground/mission/backup/photos" ] || fail "rm reached the backup"
  step allow "First the repair" "allow during a repair"
  step hint "Hint:" "repair hint"
  step hint "Solution:" "repair solution"
  step ls "Not repaired yet" "a look during the repair"
  call 'cp -R ../backup/. .' "Repaired" "the repair"
  expect 'rm tmp/\*\.tmp' 8 || fail "after the repair the agent did not run the corrected command"
  next_task tS2_4 "tS2_3"; sleep 0.2
  [ -f "$site/index.html" ] && [ -f "$site/tmp/keep.txt" ] || fail "the project is not restored"
  [ -e "$site/tmp/cache-1.tmp" ] && fail "the corrected rm did not delete the .tmp files"
  # The project folder is swapped for a link to somewhere else: nothing
  # may run there, and the next step rebuilds the real folder.
  mkdir "$HOMEDIR/elsewhere"; echo precious > "$HOMEDIR/elsewhere/keep"
  mv "$site" "$site.real" && ln -s "$HOMEDIR/elsewhere" "$site"
  call allow "not where it should be" "the folder check"
  next_task tS2_5 "tS2_4"; sleep 0.2
  [ -L "$site" ] && fail "the link was not replaced by the real folder"
  [ "$(cat "$HOMEDIR/elsewhere/keep" 2>/dev/null)" = precious ] && [ "$(ls "$HOMEDIR/elsewhere" | wc -l | tr -d ' ')" = 1 ] \
    || fail "something happened in the folder the link pointed to"
  call deny "Right call" "deny what nobody asked for"
  expect "drops the idea" 8 || fail "no word from the agent after a right no"
  next_task tS2_6 "tS2_5"; sleep 0.2
  [ -f "$site/logs/access.log" ] || fail "the logs are gone although the rm was denied"
  ouch "chmod -R 777 ."
  p=$(ls -ld "$site/.env" | cut -c1-10)
  [ "$p" = "-rwxrwxrwx" ] || fail "allowed chmod did not really run ($p)"
  call 'chmod -R go-rwx .' "Repaired" "the chmod repair"
  next_task tS2_7 "tS2_6"; sleep 0.2
  p=$(ls -ld "$site/.env" | cut -c1-10)
  case "$p" in "-rw"?"------") ;; *) fail ".env is not private again ($p)" ;; esac
  [ -x "$site/deploy.sh" ] || fail "deploy.sh is not runnable after the corrected chmod"
  step 'cd ~' "You looked around" "walking away"
  call allow "The game moved you" "the agent goes back to its folder"
  expect "Done. The site is live." 8 || fail "the script did not run in the project folder"
  next_task tS2_8 "tS2_7"; sleep 0.2
  call allow "To AI agents working here" "reading the notes"
  next_task tS2_9 "tS2_8"; sleep 0.2
  ouch "cat .env >> index.html"
  grep -q API_KEY "$site/index.html" || fail "the allowed >> did not really run"
  call 'cp ../backup/index.html .' "Repaired" "the page repair"
  next_task tS2_10 "tS2_9"; sleep 0.2
  grep -q API_KEY "$site/index.html" && fail "the key is still on the page"
  call allow "Right call" "the report"
  next_task tS2_11 "tS2_10"; sleep 0.2
  [ -s "$site/report.txt" ] || fail "report.txt was not written"
  call allow "outside the playground" "the guard as the second wall"
  expect "You said yes. The game said no" 8 || fail "no word about the guard"
  expect "FLAG CAPTURED: FLAG\\{read-first-then-allow\\}" 8 || fail "no flag at the end"
  [ -e "$HOMEDIR/.bashrc" ] || [ -e "$HOMEDIR/.zshrc" ] && fail "the agent wrote into the home folder"
  PLAY_LEVEL=S2 play
  has_text "Side quest S2: 11 of 11 tasks solved" || fail "side quest S2 not complete"
  has_text "Your record: 6 of 11" || fail "the record does not count 6 right calls"
  has_text "A yes too many: 4." || fail "the record does not count 4 wrong yes"
  has_text "A no too many: 1." || fail "the record does not count 1 wrong no"
  sleep 0.2; send quit; expect "Progress saved" 8
  stop_game
  grep -q '^side_done=S2$' "$HOMEDIR/.apprentice-save" || fail "save does not list S2 as done"
  drop_home
}

sc_placement() {
  local answers want a
  for spec in "16:a b a b b a a b b b a b" "11:a b a b b a a b b b c" "3:a b b" "0:c"; do
    want=${spec%%:*}; answers=${spec#*:}
    new_home; start_game "placement_$want"
    expect "Your choice:" 10
    sleep 0.2; keys '\003'; sleep 0.3
    alive || fail "Ctrl-C at the main menu ended the game"
    send p
    for a in $answers; do expect "  > " 6; send "$a"; done
    expect "Suggested start: Level $want" 6 || fail "placement '$answers' did not suggest level $want"
    expect "\\[y/n\\]" 6; send y
    expect "$MARK|\\[ press Enter \\]" 8
    while [ "$M" = "[ press Enter ]" ]; do keys '\r'; expect "$MARK|\\[ press Enter \\]" 8 || break; done
    case "$M" in "<<TQ:t${want}_"*) ;; *) fail "placement did not start level $want (got $M)" ;; esac
    sleep 0.2; send quit; expect "Progress saved" 6
    stop_game; drop_home
  done
}

sc_nocolor() {
  local spec name
  for spec in "nocolor:NO_COLOR=1" "dumbterm:TERM=dumb"; do
    name=${spec%%:*}
    new_home; start_game "$name" "${spec#*:}"; reset_sent
    menu_level 4
    play t4_3
    sleep 0.2; send quit; expect "Progress saved" 6
    if LC_ALL=C grep -q "${ESC}\[[0-9;]*m" "$LOG"; then fail "$name: colour escape codes present"; fi
    stop_game; drop_home
  done
}

width_check() {
  # Lines the game draws itself (status lines, banners) must fit 80. Cards
  # are checked by lint_text; "  | " is also the prefix of command output.
  local bad
  bad=$(cat "$OUT"/sh-*.txt 2>/dev/null | grep -v '<<TQ:' |
    grep -E "^  (L[0-9] \[|S[0-9] \[|Level [0-9]|Side quest S|\.-|'-|\+-- )|^   [.'|]" | awk 'length > 80' | head -n 5)
  if [ -n "$bad" ]; then echo "[FAIL] width:"; echo "$bad" | sed 's/^/    /'; FAILS=$((FAILS + 1))
  else echo "[PASS] width: all game-drawn lines fit 80 columns"; fi
}

# Messages that mean something on this system broke, wherever they show up.
noise_check() {
  local bad
  # (The wrong-answer and guard scenarios type unknown commands on purpose.)
  bad=$( { cat "$OUT"/sh-*.txt 2>/dev/null | grep -E 'Illegal byte sequence|xcrun: error|TQ-STUB-RAN|No developer tools'
           ls "$OUT"/sh-*.txt 2>/dev/null | grep -v -E 'wrong_hints|guard_live' | while read -r f; do grep 'command not found' "$f"; done
         } | grep -v -E '^  |ehco: command not found' | sort | uniq -c | head -n 8)
  if [ -n "$bad" ]; then echo "[FAIL] noise: error messages from the system in the transcripts:"; echo "$bad" | sed 's/^/    /'; FAILS=$((FAILS + 1))
  else echo "[PASS] noise: no encoding, developer-tool or missing-command errors"; fi
}

# --------------------------------------------------------------- main
#
# Every scenario runs as its own job: a child process of this script with
# its own home, its own pseudo-terminal and its own transcript (R19). Up
# to TQ_JOBS jobs run at the same time (default: the number of CPU cores,
# at most 8; TQ_JOBS=1 runs them one after the other). "levels_cold"
# becomes one job per level. The longest jobs start first.

if [ "${1:-}" = --job ]; then        # a child: run one scenario
  shift
  SOLFILE=$TQ_SOLFILE DRIVEFILE=$TQ_DRIVEFILE
  SC_FAIL=0
  t0=$(date +%s)
  # shellcheck disable=SC2068
  "sc_$1" ${2:+"$2"}
  if [ "$SC_FAIL" = 0 ]; then r=PASS; else r=FAIL; fi
  echo "[$r] $1${2:+ $2} ($(( $(date +%s) - t0 ))s)"
  [ -n "$STUBDIR" ] && rm -rf "$STUBDIR"
  [ "$SC_FAIL" = 0 ]
  exit
fi

# Groups (R24): @quick (seconds-long smoke test while editing), @standard
# (everything except the long one-home playthrough "full"), @all.
STANDARD="levels_cold save_continue reset wrong_hints_ctrlc guard_live placement nocolor side_quests supervisor"
ALL="full $STANDARD"
if [ $# -gt 0 ]; then
  case "$*" in
    @all)      ;;
    @standard) ALL=$STANDARD ;;
    @quick)    ALL="levels_cold save_continue nocolor guard_live"; TQ_COLD_LEVELS=${TQ_COLD_LEVELS:-0 1} ;;
    @*)        echo "unknown group: $* (groups: @quick @standard @all)"; exit 2 ;;
    *)         ALL="$*" ;;
  esac
fi
cores=$(getconf _NPROCESSORS_ONLN 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 2)
case "$cores" in ''|*[!0-9]*) cores=2 ;; esac
[ "$cores" -gt 8 ] && cores=8
JOBS=${TQ_JOBS:-$cores}
case "$JOBS" in ''|*[!0-9]*|0) JOBS=1 ;; esac
echo "autoplay.sh: $($GRUN -c 'echo bash $BASH_VERSION, $(uname -m)'), $OS, script: $SCRIPT_KIND, $JOBS jobs at a time, PATH=$GPATH${TQ_NOCLT:+, as a Mac without developer tools}"
rm -f "$OUT"/sh-*.txt "$OUT"/job-*.log
make_solutions
[ -s "$SOLFILE" ] || { echo "could not read the solutions from the game"; exit 2; }
export TQ_SOLFILE=$SOLFILE TQ_DRIVEFILE=$DRIVEFILE

# The job list: "full" first (the longest), then the levels, biggest first.
joblist=""
for sc in $ALL; do
  case "$sc" in
    levels_cold)
      for lv in $(for l in ${TQ_COLD_LEVELS:-$ALL_LEVELS $SIDE_LEVELS}; do
                    printf '%s %s\n' "$(awk -F'	' -v p="t${l}_" 'index($1, p) == 1' "$SOLFILE" | wc -l)" "$l"
                  done | sort -rn | awk '{print $2}'); do
        joblist="$joblist level:$lv"
      done ;;
    *) joblist="$joblist $sc" ;;
  esac
done
# (Moved, not copied: with "full" alone, a copy would play it twice at the
# same time into one transcript.)
case " $joblist " in *" full "*)
  rest=""; for j in $joblist; do [ "$j" = full ] || rest="$rest $j"; done
  joblist="full$rest" ;;
esac

pids="" names=""
start_job() {   # NAME (scenario, or level:N)
  local sc=${1%%:*} arg=""
  case "$1" in *:*) arg=${1#*:} ;; esac
  "$GBASH" "$0" --job "$sc" $arg > "$OUT/job-${1/:/-}.log" 2>&1 &
  pids="$pids $!"; names="$names $1"
}
running() {     # how many jobs are still running
  local p n=0
  for p in $pids; do kill -0 "$p" 2>/dev/null && n=$((n + 1)); done
  echo "$n"
}
t_all=$(date +%s)
for j in $joblist; do
  while [ "$(running)" -ge "$JOBS" ]; do sleep 0.5; done
  start_job "$j"
done
# Collect: print each job's report in the order of the list.
set -- $pids
for j in $names; do
  if wait "$1"; then :; else FAILS=$((FAILS + 1)); fi
  cat "$OUT/job-${j/:/-}.log"
  shift
done
echo "    (all jobs: $(( $(date +%s) - t_all ))s)"
width_check
noise_check
rm -f "$SOLFILE" "$DRIVEFILE"
[ -n "$STUBDIR" ] && rm -rf "$STUBDIR"
[ "$FAILS" = 0 ]
