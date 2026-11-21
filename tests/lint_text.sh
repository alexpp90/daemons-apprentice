#!/usr/bin/env bash
# shellcheck disable=SC2016,SC2181
# Render every text of the game (cards, tasks, hints, facts, quiz answers,
# level intros and outros, help) for both the macOS and the Linux variant,
# without colour, and check that each line fits into 78 columns.
# Solutions are exempt: they are commands and may be long one-liners.
# Pure Bash + awk, so it also runs on a stock Mac. Same job as lint_text.py.
#
# Usage: bash tests/lint_text.sh        (TQ_BASH=/path/to/bash optional)

ROOT=$(cd "$(dirname "$0")/.." && pwd)
GAME="$ROOT/src/daemons-apprentice.sh"
GBASH=${TQ_BASH:-bash}
bad=0
errf=$(mktemp "${TMPDIR:-/tmp}/tq-lint.XXXXXX")
trap 'rm -f "$errf"' EXIT

for os in mac linux; do
  out=$(env -i PATH="$PATH" HOME=/home/player LANG=C "$GBASH" -c '
    TQ_LIB=1 TQ_NOPAUSE=1 . "$1"
    TQ_OS=$2
    C_0="" C_B="" C_DIM="" C_CMD="" C_OK="" C_BAD="" C_WARN="" C_HEAD="" C_ART="" C_MAX="" TQ_COLOR=0
    TQ_SHA="sha256sum"; [ "$TQ_OS" = mac ] && TQ_SHA="shasum -a 256"
    TQ_PKG_CMD="dpkg -s bash"; [ "$TQ_OS" = mac ] && TQ_PKG_CMD="otool -L /bin/ls"
    tq_has_gui() { [ "$TQ_OS" = mac ]; }
    tq_rogue_pid() { echo 12345; }
    tq_netlab_base() { echo 18420; }
    TQ_PG="$HOME/apprentice-playground"; TQ_PG_SHOW="~/apprentice-playground"
    tq_title
    for l in $TQ_LEVELS $TQ_SIDE; do
      echo "@@@ L$l intro"; tq_L$l intro
      echo "@@@ L$l outro"; tq_L$l outro | tq_fmt
      echo "@@@ L$l pitch"; tq_L$l pitch 2>/dev/null | tq_fmt
      for t in $(tq_L$l tasks); do
        if [ "$($t kind 2>/dev/null)" = agent ]; then
          echo "@@@ $t why"; $t why | sed "s/^/  /" | tq_fmt
          echo "@@@ $t verdict"; $t verdict | tq_fmt
          echo "@@@ $t repair"; $t repair 2>/dev/null | tq_fmt
        fi
        echo "@@@ $t card";  $t card
        echo "@@@ $t task";  $t task | tq_fmt
        echo "@@@ $t hint1"; $t hint1 | tq_fmt
        echo "@@@ $t hint2"; $t hint2 | tq_fmt
        echo "@@@ $t fact";  $t fact | sed "s/^/  * /"
        echo "@@@ $t solution"; $t solution | sed "s/^/    \$ /"
        if [ "$($t kind 2>/dev/null)" = quiz ]; then
          n=$($t qn); i=1
          while [ $i -le $n ]; do echo "@@@ $t q$i"; $t q$i | sed -n "1p" | sed "s/^/    /"; $t q$i | sed -n "2p" | sed "s/^/      /"; $t q$i | sed -n "4,\$p" | tq_fmt; i=$((i+1)); done
        fi
      done
    done
    for l in $TQ_LEVELS $TQ_SIDE; do echo "@@@ L$l explore"; tq_explore_show $l; done
    echo "@@@ help"; tq_help
    echo "@@@ keys"; tq_keys
    echo "@@@ keys box"; TQ_KEYS_SHOWN=""; tq_keys_before_task "$TQ_KEY_IDS"
  ' x "$GAME" "$os" 2>"$errf")
  res=$(printf '%s\n' "$out" | awk -v os="$os" '
    /^@@@ / { where = substr($0, 5); next }
    { gsub(/\t/, "        ") }
    length($0) > 78 && where !~ /solution/ { printf "[%s] %s: %d cols: %s\n", os, where, length($0), $0; n++ }
    END { exit n > 0 }')
  if [ $? -ne 0 ]; then printf '%s\n' "$res"; bad=1; fi
  if [ -s "$errf" ]; then
    echo "[$os] errors while rendering:"; head -n 5 "$errf"
    bad=1
  fi
done
if [ "$bad" = 0 ]; then echo "text widths: PASS"; else echo "text widths: FAIL"; fi
exit "$bad"
