# The Daemon's Apprentice for Android

The terminal game as an Android app: the player's commands run for real in
the phone's own shell (Android's `sh` and toybox), inside the app's
sandbox. Nothing else to install, no permissions, no network, no ads, no
account. Android 10 and newer, with touch alone or with a keyboard alone.

**Status: Levels 0 to 15** ("Hello, Terminal" to "Life and Death of an
App") are playable; the side quest "Portrait of Your Phone" follows.

## Build

JDK 17 and the Android SDK (with NDK 29) are needed.

    ./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
    ./gradlew assembleRelease      # shrunk; signed with the debug key unless a release key is configured

## Tests

    ./gradlew testDebugUnitTest                  # engine, guard, level rules, every level played on the JVM (bash stands in for the phone's shell)
    ./gradlew assembleDebug assembleDebugAndroidTest
    tools/device-tests.sh emulator-5554          # on one emulator: the real shell, every level by touch and by keyboard

`tools/device-tests.sh` refuses to run on anything but an emulator.
`PlayTest` plays every level from a fresh install twice: with taps alone
(chips, Run, Next, Ctrl-C, the editor's Done; from Level 2 on the chips come
after `hint`) and with a hardware keyboard alone (typing, Enter, Tab, the
arrows, Ctrl-C, Esc in the editor). The keyboard test switches the emulator's soft
keyboards off for its run and back on afterwards, because Android treats an
emulator's built-in keyboard as not external and would still show one.
Level 15's trip to the home screen is played for real: Home, eight seconds,
then the game's own task is brought back to the front (as from the recent
apps). To check one late level quickly, start there from a saved game:
`adb shell am instrument -w -e fromLevel 15 -e class
io.github.alexpp90.daemonsapprentice.PlayTest
io.github.alexpp90.daemonsapprentice.test/androidx.test.runner.AndroidJUnitRunner`.

## How it works

- `engine/ShellSession.kt`: one interactive `/system/bin/sh` on a
  pseudo-terminal (`cpp/pty.c`), echo and line editing off. The prompts
  carry markers with the exit status and the folder (`MarkerParser.kt`), so
  an unfinished command (an open quote) shows the shell's `>` prompt, and
  Ctrl-C reaches the running command, not the game. `pm`, `settings`,
  `cmd`, `am` and `dumpsys` are wrapped in the shell's start file: the
  system refuses to use an app's terminal, so they get `/dev/null` and a
  pipe instead.
- `engine/Guard.kt`: stops what would break the game itself (its own
  folder, its save file, its shell and app process for `kill`); it checks
  every command of a line, loops and multi-line commands included.
  Android's sandbox does the rest.
- `game/`: the levels as data (`Level0.kt` to `Level15.kt`, listed in
  `Levels.kt`), the flow (`GameEngine.kt`), the chips (`Chips.kt`).
  `edit file` is the game's own command: the phone has no `nano`.
- `ui/`: the guided console (Compose), the command line (`CommandInput.kt`:
  no suggestions, no autocorrect, no learning; Tab, arrows, Ctrl-C), the key
  bar and the chips; the editor (`EditorScreen.kt`: Done, Back, Esc or
  Ctrl+S save and return).

Licence: GPL-3.0, like the game (see `../LICENSE`).
