#!/usr/bin/env bash
# Writes the Mac and the Linux version of the game from the one source:
#   src/daemons-apprentice.sh  ->  game/mac/daemons-apprentice.sh
#                                  game/linux/daemons-apprentice.sh
# The two differ only in the TQ_BUILD_OS line, which fixes the system the
# texts are written for (and makes each version refuse the other system).
#
#   bash tools/build.sh           build both
#   bash tools/build.sh --check   only check that both are up to date
# Works with Bash 3.2 and stock macOS tools.

set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SRC="$ROOT/src/daemons-apprentice.sh"
check=0
[ "${1:-}" = --check ] && check=1

n=$(grep -c '^TQ_BUILD_OS=""$' "$SRC")
if [ "$n" != 1 ]; then
  echo "build: expected exactly one line TQ_BUILD_OS=\"\" in $SRC, found $n" >&2
  exit 2
fi

rc=0
for os in mac linux; do
  out="$ROOT/game/$os/daemons-apprentice.sh"
  tmp="$out.tmp.$$"
  mkdir -p "$ROOT/game/$os"
  sed "s|^TQ_BUILD_OS=\"\"\$|TQ_BUILD_OS=\"$os\"   # built by tools/build.sh, edit src/ instead|" "$SRC" > "$tmp"
  if [ "$check" = 1 ]; then
    if cmp -s "$tmp" "$out"; then echo "up to date: game/$os/daemons-apprentice.sh"
    else echo "OUT OF DATE: game/$os/daemons-apprentice.sh (run bash tools/build.sh)"; rc=1; fi
    rm -f "$tmp"
  else
    mv -f "$tmp" "$out"
    echo "built: game/$os/daemons-apprentice.sh"
  fi
done
exit $rc
