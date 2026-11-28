#!/usr/bin/env bash
# Checks the design rules of the game that a script can check (the R numbers
# are the maintainers' requirement numbers).
# Bash 3.2 and stock macOS tools only. Exit status 0 = all passed.
#   R1  Mac and Linux versions are up to date, each refuses the other system,
#       each README talks about its own system only
#   R3  the first tasks need no hard-to-type characters; the READMEs need
#       none to start; every hard character has typing help
#   R4/R7  the first task teaches help; help is named on every prompt line
#   R8  levels 0..N without gaps; every task has a cheatsheet entry
#   R11 every fact is short (at most 3 lines); every level has explore tips
#   R13 no example opens a file in an app (only folders), shiplog is gone
#   R21 the side quests are off the main path; every step of the agent has
#       an explanation, a verdict and a repair
#   R22 the portrait tasks only read

set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
BASH_BIN=${TQ_BASH:-bash}
SRC="$ROOT/src/daemons-apprentice.sh"
fails=0
ok()  { echo "  ok    $*"; }
bad() { echo "  FAIL  $*"; fails=$((fails + 1)); }

case "$(uname -s)" in Darwin) other=linux ;; *) other=mac ;; esac

# --- R1 -------------------------------------------------------------------
if "$BASH_BIN" "$ROOT/tools/build.sh" --check >/dev/null; then ok "R1 game/mac and game/linux are built from src"
else bad "R1 game/mac or game/linux is out of date: run bash tools/build.sh"; fi

tmp=$(mktemp -d "${TMPDIR:-/tmp}/tq-req.XXXXXX")
out=$(HOME="$tmp" "$BASH_BIN" "$ROOT/game/$other/daemons-apprentice.sh" </dev/null 2>&1); rc=$?
if [ "$rc" = 1 ] && printf '%s' "$out" | grep -q "Please use the file in the folder for"; then
  ok "R1 the $other version refuses to run here, with a plain explanation"
else bad "R1 the $other version did not refuse on this system (rc=$rc)"; fi
[ -e "$tmp/apprentice-playground" ] && bad "R1 the refused version still created the playground"
rm -rf "$tmp"

if grep -qi "linux" "$ROOT/game/mac/README.md"; then bad "R1 game/mac/README.md mentions Linux"; else ok "R1 Mac README is about the Mac only"; fi
if grep -qiE "mac|finder" "$ROOT/game/linux/README.md"; then bad "R1 game/linux/README.md mentions the Mac"; else ok "R1 Linux README is about Linux only"; fi

# --- R3, R4, R7: ask the game itself ---------------------------------------
for os in mac linux; do
  # shellcheck disable=SC2016  # the script below is meant for the inner bash
  res=$(env -i PATH="$PATH" HOME=/nonexistent "$BASH_BIN" -c '
    TQ_LIB=1; . "$1"; TQ_OS=$2; tq_init_colors() { :; }; C_DIM= C_0= C_CMD=
    TQ_SHA="sha256sum"; tq_has_gui() { return 1; }
    # R3: Levels 0 and 1 need no hidden characters
    for t in $(tq_L0 tasks) $(tq_L1 tasks); do
      ids=$(tq_key_ids "$($t solution)")
      [ -n "$ids" ] && echo "FAIL R3 $t needs hard characters: $ids"
    done
    # R3: every hard character has a row with US and German keys
    for id in $TQ_KEY_IDS; do
      row=$(tq_key_row "$id")
      [ "$(printf "%s" "$row" | wc -w)" -ge 4 ] || echo "FAIL R3 no typing help for $id"
    done
    # R3/R4: every hard character any task needs is known to the keys help
    for l in $TQ_LEVELS $TQ_SIDE; do for t in $(tq_L$l tasks); do
      sol=$($t solution 2>/dev/null)
      case "$sol" in *"{"*|*"}"*|*"["*|*"]"*|*"|"*|*"~"*|*"\\"*|*"@"*|*"^"*)
        [ -n "$(tq_key_ids "$sol")" ] || echo "FAIL R3 $t: hard character not detected" ;;
      esac
    done; done
    # R7: the very first task is about help, and typing help solves it
    first=$(tq_L0 tasks); first=${first%% *}
    $first task | grep -q "help" || echo "FAIL R7 the first task does not teach help"
    [ "$($first gamecmd)" = help ] || echo "FAIL R7 typing help does not solve the first task"
    # ... and only that task: help must not solve any other task
    for l in $TQ_LEVELS $TQ_SIDE; do for t in $(tq_L$l tasks); do
      [ "$t" = "$first" ] && continue
      [ "$($t gamecmd 2>/dev/null)" = help ] && echo "FAIL R7 typing help solves $t"
    done; done
    # R8/R10: levels are numbered 0..N without gaps, and the count matches
    n=0; for l in $TQ_LEVELS; do [ "$l" = "$n" ] || echo "FAIL R8 level list has a gap: $TQ_LEVELS"; n=$((n+1)); done
    [ "$n" = "$TQ_NLEVELS" ] || echo "FAIL R8 TQ_NLEVELS=$TQ_NLEVELS but $n levels"
    # R39: every main-path level belongs to a rated chapter (Beginner/Intermediate/Advanced)
    for l in $TQ_LEVELS; do
      ch=$(tq_level_chapter "$l")
      [ -n "$ch" ] || echo "FAIL R39 level $l has no chapter"
      case "$(tq_chapter "$ch" rating)" in Beginner|Intermediate|Advanced) ;; *) echo "FAIL R39 chapter $ch invalid rating" ;; esac
      [ -n "$(tq_chapter "$ch" title)" ] || echo "FAIL R39 chapter $ch has no title"
    done
    # R8: every task has a cheatsheet entry (command|description)
    TQ_PKG_CMD="dpkg -s bash"
    for l in $TQ_LEVELS $TQ_SIDE; do for t in $(tq_L$l tasks); do
      case "$(tq_cheat_entry "$t")" in *?"|"?*) ;; *) echo "FAIL R8 no cheatsheet entry for $t" ;; esac
    done; done
    # R4/R7: every prompt line names help in plain words
    tq_status_line 0 1 3 | grep -q "Stuck? Type hint" || echo "FAIL R4 level 0 prompt line without hint"
    tq_status_line 4 2 8 | grep -q "Stuck? Type hint" || echo "FAIL R4 level prompt line without hint"
    tq_status_line 4 2 8 | grep -q "help" || echo "FAIL R4 level prompt line without help"
    # R11: facts are short; R17: every level ends with explore tips
    for l in $TQ_LEVELS $TQ_SIDE; do
      [ -n "$(tq_L$l explore 2>/dev/null)" ] || echo "FAIL R17 level $l has no explore tips"
      for t in $(tq_L$l tasks); do
        n=$($t fact 2>/dev/null | awk "END{print NR}")
        [ "$n" -le 3 ] || echo "FAIL R11 $t: fact has $n lines (max 3)"
        # R17: one task, one drive tag at most, and a title for each
        [ -n "$($t title)" ] || echo "FAIL $t has no title"
      done
    done
    # R21/R22: the side quests are off the main path, hang on a level and
    # have a text for the offer
    for s in $TQ_SIDE; do
      tq_has "$TQ_LEVELS" "$s" && echo "FAIL R21 side quest $s is part of the main path"
      tq_has "$TQ_LEVELS" "$(tq_L$s after)" || echo "FAIL R21 side quest $s hangs on no level"
      [ "$(tq_side_after "$(tq_L$s after)")" = "$s" ] || echo "FAIL R21 side quest $s is not offered after its level"
      [ -n "$(tq_L$s pitch)" ] || echo "FAIL R21 side quest $s has no text for the offer"
    done
    # R21: every step of the agent explains itself and has a verdict; a
    # wrong yes can be seen and repaired, with the command explained (R5)
    for t in $(tq_LS2 tasks); do
      [ "$($t kind)" = agent ] || echo "FAIL R21 $t is not an agent step"
      r=$($t right)
      case "$r" in allow|deny) ;; *) echo "FAIL R21 $t: the right call is neither allow nor deny" ;; esac
      [ "$($t solution)" = "$r" ] || echo "FAIL R21 $t: the solution is not the right call"
      for k in say cmd why verdict; do [ -n "$($t $k)" ] || echo "FAIL R21 $t has no $k"; done
      if [ "$r" = deny ] && [ "$($t walled 2>/dev/null)" != 1 ]; then
        for k in show repair fixcmd; do [ -n "$($t $k 2>/dev/null)" ] || echo "FAIL R21 $t: a wrong yes has no $k"; done
        $t repair | grep -q -F -- "$($t fixcmd)" || echo "FAIL R5 $t: the repair text does not show its command"
      fi
    done
    # R22/R41: the portrait (S1) and security checkup (S3) only read: no
    # solution writes, deletes or changes anything, and on a Mac none walks
    # through the home folder
    set -f
    for s in S1 S3; do
      for t in $(tq_L$s tasks); do
        sol=$($t solution | sed -e "s#2>/dev/null##g" -e "s#2>&1##g")
        case "$sol" in *">"*) echo "FAIL R22/R41 $t writes into a file" ;; esac
        for w in $sol; do
          case "$w" in rm|mv|cp|chmod|chown|touch|mkdir|tee|ln|dd|sudo|-delete|-exec) echo "FAIL R22/R41 $t uses $w" ;; esac
        done
        if [ "$TQ_OS" = mac ]; then
          case "$sol" in du*"~"*|find*"~"*) echo "FAIL R22/R41 $t walks through the home folder on a Mac" ;; esac
        fi
      done
    done
    set +f
    # R42: gamification trophies and ranks are well-formed
    for tid in $TQ_ALL_TROPHIES; do
      [ -n "$(tq_trophy_info "$tid" name)" ] || echo "FAIL R42 trophy $tid has no name"
      [ -n "$(tq_trophy_info "$tid" desc)" ] || echo "FAIL R42 trophy $tid has no desc"
      [ -n "$(tq_trophy_info "$tid" clue)" ] || echo "FAIL R42 trophy $tid has no clue"
    done
    for xp in 0 150 500 1200 2200 3600 5200 7200; do
      TQ_XP=$xp
      [ -n "$(tq_rank_name)" ] || echo "FAIL R42 no rank name for XP=$xp"
    done
    TQ_XP=0
    # R13/R14: the example files the player cats fit 80 columns with the
    # "  | " in front of command output
    TQ_PG=$(mktemp -d "${TMPDIR:-/tmp}/tq-pg.XXXXXX"); tq_make_timeline
    w=$(awk "length > 74" "$TQ_PG/timeline.txt" | head -n 1)
    [ -z "$w" ] || echo "FAIL R14 timeline.txt line too wide: $w"
    rm -rf "$TQ_PG"
    # R7: the help list names the terminal'"'"'s own help
    tq_help | grep -q "man ls" || echo "FAIL R7 help does not show man"
    echo done
  ' x "$SRC" "$os" 2>&1)
  if printf '%s\n' "$res" | grep -q "^FAIL"; then
    printf '%s\n' "$res" | grep "^FAIL" | sed "s/^FAIL/  FAIL  [$os]/"
    fails=$((fails + $(printf '%s\n' "$res" | grep -c "^FAIL")))
  elif printf '%s\n' "$res" | grep -q "^done$"; then
    ok "R3/R4/R7/R8 [$os] easy first tasks, typing help, help first and on every prompt, levels without gaps, a cheatsheet entry per task"
    ok "R21/R22 [$os] side quests off the main path, every agent step explained and repairable, the portrait only reads"
  else
    bad "R3/R4/R7 [$os] could not run the checks: $(printf '%s' "$res" | head -n 3)"
  fi
done

# R13: the ship log is gone; no task opens a file (only folders) in an app.
if grep -q shiplog "$SRC"; then bad "R13 the ship log example is still in the game"
else ok "R13 the ship log example is gone"; fi
if grep -nE '(xdg-)?open\)? [A-Za-z0-9_.-]+\.(txt|log|md)' "$SRC" | grep -v '^ *#' | grep -q .; then
  bad "R13 a task opens a file in an app (depends on the player's settings): $(grep -nE '(xdg-)?open\)? [A-Za-z0-9_.-]+\.(txt|log|md)' "$SRC" | head -n 1)"
else ok "R13 openers only open folders"; fi

# R15: --log is documented in both READMEs.
for os in mac linux; do
  if grep -q -- "--log" "$ROOT/game/$os/README.md"; then ok "R15 game/$os/README.md mentions --log"
  else bad "R15 game/$os/README.md does not mention --log"; fi
done

# R3: starting the game needs no hard characters (the "Start" section).
for os in mac linux; do
  start=$(awk '/^## Start/{f=1; next} /^## /{f=0} f' "$ROOT/game/$os/README.md")
  if printf '%s' "$start" | grep -q '[~{}|\\@^]'; then bad "R3 game/$os/README.md start needs a hard character"
  else ok "R3 game/$os/README.md: starting needs no hard characters"; fi
done

if [ "$fails" = 0 ]; then echo "requirements: PASS"; else echo "requirements: FAIL ($fails)"; fi
[ "$fails" = 0 ]
