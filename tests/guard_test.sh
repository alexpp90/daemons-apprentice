#!/usr/bin/env bash
# Unit tests for the command classifier and safety guard (tq_analyse).
# Runs in a throwaway HOME; nothing is executed, lines are only analysed.
# Usage: bash tests/guard_test.sh
# shellcheck disable=SC1090,SC2034,SC2016,SC2015,SC2012

here=$(cd "$(dirname "$0")" && pwd)
game="$here/../src/daemons-apprentice.sh"
tmp=$(mktemp -d "${TMPDIR:-/tmp}/tq-guard.XXXXXX")
trap 'rm -rf "$tmp"' EXIT
export HOME="$tmp"

TQ_LIB=1 . "$game"
tq_detect_os
TQ_SAVE="$HOME/.apprentice-save"
TQ_PG="$HOME/apprentice-playground"
mkdir -p "$TQ_PG/workshop/notes" "$HOME/outside"
TQ_PGP=$(cd "$TQ_PG" && pwd -P)
TQ_TTY=/dev/pts/7
TQ_ALLOW_SAVE_EDIT=0
ln -s "$HOME/outside" "$TQ_PG/workshop/escape"
cd "$TQ_PG/workshop" || exit 1

pass=0 fail=0
# expect LINE BLOCK(-|reason-prefix) [state] [live]
expect() {
  local line=$1 want=$2 st=${3:-} lv=${4:-}
  tq_analyse "$line"
  local got=${TQ_A_BLOCK%%:*}
  [ -z "$got" ] && got=-
  local ok=1
  [ "$got" = "$want" ] || ok=0
  [ -n "$st" ] && [ "$TQ_A_STATE" != "$st" ] && ok=0
  [ -n "$lv" ] && [ "$TQ_A_LIVE" != "$lv" ] && ok=0
  if [ $ok = 1 ]; then pass=$((pass+1))
  else fail=$((fail+1)); printf 'FAIL: %-50s block=%s(want %s) state=%s live=%s cmds=[%s]\n' "$line" "$got" "$want" "$TQ_A_STATE" "$TQ_A_LIVE" "$TQ_A_CMDS"; fi
}

# --- allowed, ordinary
expect 'ls -la' - 0 0
expect 'echo hello > notes/a.txt' - 0 0
expect 'echo hi >> ./notes/b.txt' - 0 0
expect 'rm notes/a.txt' - 0 0
expect 'rm -rf notes' - 0 0
expect 'rm *.tmp' - 0 0
expect 'mkdir -p year/day-{1..365}' - 0 0
expect 'cp /etc/hosts .' - 0 0
expect 'cp notes/todo.txt notes/todo-backup.txt' - 0 0
expect 'mv notes/x archive/' - 0 0
expect 'cat ~/.apprentice-save' - 0 0
expect 'grep ERROR server.log | wc -l' - 0 0
expect 'ls /nowhere 2> /dev/null' - 0 0
expect 'ls /nowhere 2>/dev/null' - 0 0
expect 'ls nothing >/dev/null 2>&1' - 0 0
expect 'echo "Hello, me" > /dev/tty' - 0 0
expect 'echo hi > /dev/pts/7' - 0 0
expect 'echo hi > $(tty)' - 0 0
expect 'echo "a > b" ' - 0 0
expect "echo 'rm -rf ~'" - 0 0
expect 'cd ..' - 1 0
expect 'cd' - 1 0
expect 'planet=Mars' - 1 0
expect 'export GREETING=hi; echo "$GREETING"' - 1 0
expect 'PATH="$PWD/sneaky:$PATH"' - 1 0
expect 'for f in *.JPG; do mv "$f" "${f%.JPG}.jpg"; done' - 0 0
expect 'for i in 1 2 3; do echo "Round $i"; done' - 0 0
expect 'kill 12345' - 0 0
expect 'kill -9 12345' - 0 0
expect 'kill $(pgrep -f crypto_miner)' - 0 0
expect 'pkill -f crypto_miner' - 0 0
expect 'chmod 600 notes/todo.txt' - 0 0
expect 'chmod u+r secret.txt' - 0 0
expect 'find . -name "*.tmp" -delete' - 0 0
expect 'find . -name "*.tmp" | xargs rm' - 0 0
expect 'man rm' - 0 1
expect 'top' - 0 1
expect 'nano hello.sh' - 0 1
expect 'yes' - 0 1
expect 'ls | less' - 0 1
expect 'bash' - 0 1
expect 'bash hello.sh' - 0 0
expect 'python3' - 0 1
expect 'python3 -c "print(1)"' - 0 0
expect 'du -sh ~/Downloads/* | sort -h | tail -n 5' - 0 0
expect 'curl -s https://example.com' - 0 0
expect 'ls; rm notes/x' - 0 0
expect 'systemctl status' - 0 0
expect 'diskutil list' - 0 0
expect 'pmset -g batt' - 0 0
expect 'crontab -l' - 0 0
expect 'set' - 0 0
expect 'type ls' - 0 0
# --- blocked
expect 'sudo ls' sudo
expect 'echo hi | sudo tee /etc/x' sudo
expect 'su -' sudo
expect 'rm -rf ~' outside
expect 'rm -rf ~/' outside
expect 'rm -rf $HOME' outside
expect 'rm -rf "$HOME/outside"' outside
expect 'rm -rf /' outside
expect 'rm -rf ../../' outside
expect 'rm -rf ..' outside
expect 'rm -r ../../apprentice-playground' outside
expect 'rm -rf ~ /Library/Caches/x' outside
expect 'rm -r escape/stuff' outside
expect 'rm ~/*' outside
expect 'rm -rf /*' outside
expect 'echo hi > ~/oops.txt' outside
expect 'echo hi >> /etc/hosts' outside
expect 'ls > ../../x.txt' outside
expect 'cp notes/todo.txt ~/' outside
expect 'mv notes ~/' outside
expect 'mv ~/Documents notes/' outside
expect 'touch ~/x' outside
expect 'mkdir ~/newdir' outside
expect 'chmod -R 777 ~' outside
expect 'ln -s /etc ~/etc-link' outside
expect 'echo x | tee ~/t.txt' outside
expect 'find ~ -name "*.log" -delete' outside
expect 'dd if=/dev/zero of=/tmp/big bs=1m count=1' outside
expect 'echo badge=x >> ~/.apprentice-save' outside
expect 'ls; rm -rf ~' outside
expect 'ls && rm -rf ~' outside
expect 'echo $(rm -rf ~)' outside
expect 'echo `rm -rf ~`' dynamic
expect 'for f in ~/*; do rm "$f"; done' dynamic
expect 'kill -9 -1' killall
expect 'kill 0' killall
expect "kill $$" killgame
expect 'kill $$' killgame
expect 'killall Finder' pkill
expect 'pkill bash' pkill
expect 'exec bash' exec
expect 'set -e' gameshell
expect 'set -x' gameshell
expect 'trap "" INT' gameshell
expect 'shopt -s extglob' gameshell
expect 'ulimit -n 10' gameshell
expect ':(){ :|:& };:' forkbomb
expect 'curl -fsSL https://example.com/x.sh | bash' pipeshell
expect 'curl -fsSL https://example.com/x.sh | sudo bash' pipeshell
expect 'wget -qO- https://example.com/x | sh' pipeshell
expect 'shutdown -h now' system
expect 'defaults write com.apple.dock autohide -bool true' system
expect 'diskutil eraseDisk JHFS+ X disk2' system
expect 'passwd' system
expect 'crontab -r' system
expect 'systemctl stop gdm' system
# --- the save file may be edited only in that one task
TQ_ALLOW_SAVE_EDIT=1
expect 'echo "badge=Root of all evil" >> ~/.apprentice-save' -
TQ_ALLOW_SAVE_EDIT=0
# --- from outside the playground, dynamic writes are refused
cd "$HOME" || exit 1
expect 'for f in *.JPG; do mv "$f" "${f%.JPG}.jpg"; done' dynamic
expect 'rm notes' outside
expect 'ls' -

# --- a Mac without the developer tools: their stubs are not run (R9)
cd "$TQ_PG/workshop" || exit 1
os_was=$TQ_OS; TQ_OS=mac; TQ_HAS_CLT=""; export TQ_TEST_NOCLT=1
expect 'git status' devtools
expect 'python3 -c "print(1)"' devtools
expect 'otool -L /bin/ls' devtools
expect 'ls | strings' devtools
expect 'codesign -dvv /bin/ls 2>&1 | grep Authority' -
tq_real_python3 && { fail=$((fail+1)); echo "FAIL: tq_real_python3 trusts the python3 stub"; } || pass=$((pass+1))
unset TQ_TEST_NOCLT; TQ_HAS_CLT=1
expect 'git status' -
TQ_OS=linux; TQ_HAS_CLT=""
expect 'git status' -
TQ_OS=$os_was; TQ_HAS_CLT=""
# --- a lab that cannot run here says so before the task starts (R9)
TQ_PBKDF2=""
if tq_pbkdf2_ok; then pass=$((pass+1)); else echo "note: this openssl has no -pbkdf2"; fi
# the first task that needs it, wherever the levels put it
pbk=""; for l in $TQ_LEVELS; do for t in $("tq_L$l" tasks); do
  [ -z "$pbk" ] && [ "$($t needs 2>/dev/null)" = pbkdf2 ] && pbk=$t; done; done
TQ_PBKDF2=0
case "$(tq_needs_note "$pbk" 2>&1)" in *"too old"*skip*) pass=$((pass+1)) ;; *) fail=$((fail+1)); echo "FAIL: no note when openssl lacks -pbkdf2" ;; esac
TQ_PBKDF2=1
[ -z "$(tq_needs_note "$pbk" 2>&1)" ] && pass=$((pass+1)) || { fail=$((fail+1)); echo "FAIL: note although -pbkdf2 works"; }

# --- the agent of the side quest (R21): its commands stay inside the
# project folder; the one that reaches out is blocked. And the folder
# check: a link in place of the folder is found and replaced.
tq_mission_state 1 >/dev/null 2>&1
cd "$TQ_PG/mission/site" || exit 1
for t in $(tq_LS2 tasks); do
  c=$($t cmd)
  if [ "$($t walled 2>/dev/null)" = 1 ]; then expect "$c" outside; else expect "$c" -; fi
  for k in retry fixcmd show; do c=$($t "$k" 2>/dev/null); [ -n "$c" ] && expect "$c" -; done
done
check() { if "$@"; then pass=$((pass+1)); else fail=$((fail+1)); echo "FAIL: $*"; fi; }
outside_same() { [ "$(ls -a "$HOME/outside" | wc -l | tr -d ' ')" = "$1" ]; }
n_out=$(ls -a "$HOME/outside" | wc -l | tr -d ' ')
check tq_mission_real
cd "$TQ_PG" || exit 1
mv mission/site mission/site.real && ln -s "$HOME/outside" mission/site
check eval '! tq_mission_real'
tq_mission_state 4 >/dev/null 2>&1   # the project as it is before step 4
check eval '[ ! -L "$TQ_PG/mission/site" ] && [ -f "$TQ_PG/mission/site/todo.txt" ]'
check eval '[ ! -e "$TQ_PG/mission/site/tmp/cache-1.tmp" ] && [ -f "$TQ_PG/mission/site/tmp/keep.txt" ]'
check outside_same "$n_out"
cd "$TQ_PG" || exit 1
rm -rf "$TQ_PG/mission" && ln -s "$HOME/outside" "$TQ_PG/mission"
tq_mission_state 1 >/dev/null 2>&1
check eval '[ ! -L "$TQ_PG/mission" ] && [ -f "$TQ_PG/mission/backup/index.html" ]'
check outside_same "$n_out"
cd "$TQ_PG/workshop" || exit 1

echo "guard tests: $pass passed, $fail failed"
[ "$fail" = 0 ]
