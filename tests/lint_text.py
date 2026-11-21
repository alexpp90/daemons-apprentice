#!/usr/bin/env python3
"""Render every text of the game (cards, tasks, hints, facts, level intros
and outros) for both the macOS and the Linux variant, without colour, and
check that each line fits into 80 columns. Also prints the longest lines.

Usage: python3 tests/lint_text.py [--dump]
"""
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GAME = os.path.join(ROOT, "src", "daemons-apprentice.sh")

SNIPPET = r'''
TQ_LIB=1 TQ_NOPAUSE=1 . "$1"
TQ_OS=$2
tq_init_colors() { :; }
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
    for k in card task hint1 hint2 fact; do
      echo "@@@ $t $k"
      case $k in
        card) $t card ;;
        task) $t task | tq_fmt ;;
        hint*) $t $k | tq_fmt ;;
        fact) $t fact | sed "s/^/  * /" ;;
      esac
    done
    echo "@@@ $t solution"; $t solution | sed 's/^/    $ /'
    if [ "$($t kind 2>/dev/null)" = quiz ]; then
      n=$($t qn); i=1
      while [ $i -le $n ]; do echo "@@@ $t q$i"; $t q$i | sed -n '4,$p' | tq_fmt; $t q$i | sed -n '2p' | sed 's/^/      /'; i=$((i+1)); done
    fi
  done
done
for l in $TQ_LEVELS $TQ_SIDE; do echo "@@@ L$l explore"; tq_explore_show $l; done
tq_help
echo "@@@ keys"; tq_keys
echo "@@@ keys box"; TQ_KEYS_SHOWN=""; tq_keys_before_task "$TQ_KEY_IDS"
'''


def render(os_name):
    env = {"PATH": os.environ["PATH"], "HOME": "/home/player", "LANG": "C.UTF-8"}
    r = subprocess.run(["bash", "-c", SNIPPET, "x", GAME, os_name], env=env,
                       capture_output=True, text=True)
    return r.stdout, r.stderr


def main():
    bad = 0
    longest = []
    for os_name in ("mac", "linux"):
        out, err = render(os_name)
        if err.strip():
            print(f"[{os_name}] stderr:\n{err}")
            bad += 1
        if "--dump" in sys.argv:
            print(out)
        where = "?"
        for line in out.splitlines():
            if line.startswith("@@@ "):
                where = line[4:]
                continue
            w = len(line.expandtabs())
            longest.append((w, os_name, where, line))
            # Solution lines may be long one-liners; they are shown as commands.
            limit = 78
            if w > limit and "solution" not in where:
                print(f"[{os_name}] {where}: {w} cols: {line}")
                bad += 1
    longest.sort(reverse=True)
    print("Longest lines (non-solution):")
    for w, o, where, line in [x for x in longest if "solution" not in x[2]][:5]:
        print(f"  {w:3d} [{o}] {where}: {line.strip()[:60]}")
    print("PASS" if bad == 0 else f"FAIL ({bad})")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
