#!/usr/bin/env bash
# The test suite in one command, on macOS or Linux. Pick the level that
# fits the moment (R24):
#
#     bash tests/run_all.sh quick      static checks + a smoke playthrough
#                                      (well under a minute)
#     bash tests/run_all.sh            standard: static checks + every
#                                      level from a fresh home + every
#                                      scenario (about 2 minutes on 8 cores)
#     bash tests/run_all.sh full       everything: also the whole game in
#                                      one home, the Python driver, and on a
#                                      Mac the runs without developer tools
#                                      and as Intel code (about 4 minutes)
#     bash tests/run_all.sh static     only the checks that play nothing
#
# GitHub Actions runs the same pieces as parallel jobs on Linux and on real
# Macs; see tests/README.md for every variant and where it runs.
#
# Needs only what a stock system has (bash, awk, sed, grep, script). On a
# Mac it tests the system's own /bin/bash 3.2 by default. Optional extras
# run when available: shellcheck, and the Python driver (full).
# The result is printed and saved in tests/out/report.txt.
#
# Options (environment):
#   TQ_BASH=/path/to/bash   the bash that runs the game (default: /bin/bash
#                           on macOS, bash from PATH elsewhere)
#   TQ_JOBS=N               games played at the same time
#                           (default: the CPU cores, at most 8)
#   TQ_PATH=...             the PATH the game gets (default on a Mac: the
#                           stock /usr/bin:/bin:/usr/sbin:/sbin, so Homebrew
#                           tools do not hide what a player's Mac lacks)
#   TQ_COLD_LEVELS="8 9"    play only these levels from a fresh home
#
# The tests use temporary home folders; your real home is never touched.
# shellcheck disable=SC2016,SC1091

TIER=${1:-standard}
case "$TIER" in
  quick|standard|full|static) ;;
  -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
  *) echo "unknown test level: $TIER (quick, standard, full or static)"; exit 2 ;;
esac

cd "$(dirname "$0")/.." || exit 1
ROOT=$(pwd)
mkdir -p tests/out
REPORT="$ROOT/tests/out/report.txt"

case "$(uname -s)" in
  Darwin) OS=macOS; : "${TQ_BASH:=/bin/bash}" ;;
  *)      OS=$(uname -s); : "${TQ_BASH:=bash}" ;;
esac
export TQ_BASH
export TQ_TEST_TMP=${TQ_TEST_TMP:-${TMPDIR:-/tmp}}

results=""
rc=0
record() {   # NAME STATUS
  results="$results$(printf '%-44s %s' "$1" "$2")
"
  [ "$2" = FAIL ] && rc=1
}
step() { printf '\n== %s\n' "$1"; }
start=$(date +%s)

{
  step "System"
  echo "date:   $(date '+%Y-%m-%d %H:%M')"
  if [ "$OS" = macOS ]; then
    echo "system: macOS $(sw_vers -productVersion 2>/dev/null) ($(uname -m), $(sysctl -n machdep.cpu.brand_string 2>/dev/null))"
    echo "devtools: $(xcode-select -p 2>/dev/null || echo 'not installed')"
    echo "openssl: $(PATH=/usr/bin:/bin openssl version 2>/dev/null)"
  else
    echo "system: $( (. /etc/os-release 2>/dev/null; echo "${PRETTY_NAME:-$(uname -sr)}") ) ($(uname -m))"
  fi
  echo "bash:   $TQ_BASH -> $("$TQ_BASH" -c 'echo $BASH_VERSION')"
  if script --version >/dev/null 2>&1; then echo "script: $(script --version 2>/dev/null | head -n 1)"
  else echo "script: BSD script"; fi
  echo "sha256: $(env -i PATH="$PATH" HOME=/nonexistent "$TQ_BASH" -c 'TQ_LIB=1 . "$1"; tq_find_sha; echo "$TQ_SHA"' x src/daemons-apprentice.sh)"

  step "Syntax check with the chosen bash"
  if "$TQ_BASH" -n src/daemons-apprentice.sh; then echo ok; record "syntax ($TQ_BASH -n)" PASS
  else record "syntax ($TQ_BASH -n)" FAIL; fi

  step "Bash 4+ constructs and GNU-only flags (must find nothing)"
  if grep -nE 'declare -A|local -A|mapfile|readarray|\$\{[A-Za-z_]+(,,|\^\^)\}|;;&|\|&|&>>|coproc|declare -n|local -n|wait -n|\$BASHPID|EPOCHSECONDS|read -[a-zA-Z]*[iN] |globstar|\[-1\]|\{0[0-9]+\.\.|=~|declare -g|<<<|sed -i|sed -r|grep -P|readlink -f|stat -c|date -d|xargs -r' src/daemons-apprentice.sh; then
    record "construct scan" FAIL
  else
    echo "nothing found"; record "construct scan" PASS
  fi

  step "shellcheck"
  if command -v shellcheck >/dev/null 2>&1; then
    if shellcheck -s bash src/daemons-apprentice.sh tools/*.sh tests/*.sh; then
      echo clean; record "shellcheck" PASS
    else record "shellcheck" FAIL; fi
  else
    echo "not installed, skipped"; record "shellcheck" "skipped (not installed)"
  fi

  step "Design rules: Mac/Linux versions, easy start, help"
  if "$TQ_BASH" tests/rules_test.sh; then record "design rules" PASS; else record "design rules" FAIL; fi

  step "Safety guard unit tests"
  if "$TQ_BASH" tests/guard_test.sh; then record "guard unit tests" PASS; else record "guard unit tests" FAIL; fi

  step "Text widths (every text, macOS and Linux variants)"
  if "$TQ_BASH" tests/lint_text.sh; then record "text widths" PASS; else record "text widths" FAIL; fi

  if [ "$TIER" != static ]; then
  step "Automated playthroughs ($TIER), all at the same time"
  # The Bash driver, and for "full" the Python driver and (on a Mac) the
  # runs without the developer tools and as Intel code start together;
  # each one plays its scenarios as parallel jobs too (TQ_JOBS=1: one at a
  # time). Their reports are printed one after the other when all are done.
  case "$TIER" in quick) group=@quick ;; standard) group=@standard ;; *) group=@all ;; esac
  bg_ids="" bg_pids=""
  bg() {   # ID: start that run in the background, its report in tests/out
    # Job control on while starting it: without it, bash starts background
    # jobs with Ctrl-C (SIGINT) ignored, the run inherits that and cannot
    # undo it, and on a Mac (BSD script) the game and `yes` inherit it too:
    # Ctrl-C stops nothing and `yes` fills the disk. Off again afterwards,
    # so no job notices end up in the report.
    set -m
    case "$1" in
      sh)    "$TQ_BASH" tests/autoplay.sh "$group" ;;
      noclt) TQ_NOCLT=1 "$TQ_BASH" tests/autoplay.sh full ;;
      intel) TQ_ARCH=x86_64 "$TQ_BASH" tests/autoplay.sh full ;;
      py)    "$PY" tests/autoplay.py ;;
    esac > "tests/out/run-$1.log" 2>&1 &
    bg_ids="$bg_ids $1"; bg_pids="$bg_pids $!"
    set +m
  }
  label() {
    case "$1" in
      sh) echo "playthroughs (autoplay.sh $group)" ;; py) echo "playthroughs (autoplay.py)" ;;
      noclt) echo "playthrough, no developer tools" ;; intel) echo "playthrough, Intel (x86_64, Rosetta)" ;;
    esac
  }
  bg sh
  if [ "$TIER" = full ] && [ "$OS" = macOS ]; then
    bg noclt
    if [ "$(uname -m)" = x86_64 ] && [ "$(sysctl -n sysctl.proc_translated 2>/dev/null)" != 1 ]; then
      intel_note="this is an Intel Mac: the playthroughs already run as Intel code"
      record "playthrough, Intel (x86_64)" "PASS (native Intel)"
    elif arch -x86_64 /usr/bin/true 2>/dev/null; then
      bg intel
    else
      intel_note="skipped: Rosetta 2 is not installed (softwareupdate --install-rosetta adds it)"
      record "playthrough, Intel (x86_64)" "skipped (no Rosetta 2)"
    fi
  fi
  # On a Mac, Apple's python3 (from the Command Line Tools), not one from
  # Homebrew: macOS hides the network from programs a Homebrew Python
  # starts (`netstat -an` lists no ports there), so Level 11's port task
  # would fail for a reason no player has. TQ_PYTHON=... overrides it.
  PY=${TQ_PYTHON:-python3}
  [ "$OS" = macOS ] && PY=${TQ_PYTHON:-/usr/bin/python3}
  if [ "$TIER" != full ]; then
    :
  elif [ "$OS" = macOS ] && [ -z "${TQ_PYTHON:-}" ] && ! xcode-select -p >/dev/null 2>&1; then
    py_note="skipped: no Command Line Tools (python3 is only a stub); the Bash driver covers the same"
    record "playthroughs (autoplay.py)" "skipped (no python3)"
  elif command -v "$PY" >/dev/null 2>&1; then
    bg py
  else
    py_note="python3 not installed, skipped"; record "playthroughs (autoplay.py)" "skipped (no python3)"
  fi
  t_play=$(date +%s)
  # shellcheck disable=SC2086
  set -- $bg_pids
  for id in $bg_ids; do
    if wait "$1"; then r=PASS; else r=FAIL; fi
    shift
    step "$(label "$id")"
    cat "tests/out/run-$id.log"
    record "$(label "$id")" "$r"
  done
  [ -n "${intel_note:-}" ] && { step "Intel (x86_64)"; echo "$intel_note"; }
  [ -n "${py_note:-}" ] && { step "Python driver"; echo "$py_note"; }
  echo
  echo "(all playthroughs: $(( $(date +%s) - t_play ))s)"
  fi

  step "Summary"
  printf '%s' "$results"
  el=$(( $(date +%s) - start ))
  echo "level: $TIER, time: $((el / 60)) min $((el % 60)) s"
  if [ "$rc" = 0 ]; then echo "ALL CHECKS PASSED"; else echo "SOME CHECKS FAILED"; fi
} 2>&1 | tee "$REPORT"

echo
echo "Report saved: tests/out/report.txt (transcripts of every playthrough are in tests/out/)"
grep -q "ALL CHECKS PASSED" "$REPORT"
