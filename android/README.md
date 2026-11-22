# The Daemon's Apprentice for Android

The terminal game as an Android app: the player's commands run for real in
the phone's own shell (Android's `sh` and toybox), inside the app's
sandbox. Nothing else to install, no permissions, no network, no ads, no
account. Android 10 and newer, with touch alone or with a keyboard alone.

**Status: a first slice.** The engine and Level 0 ("Hello, Terminal") are
playable; the other levels follow.

## Build

JDK 17 and the Android SDK (with NDK 29) are needed.

    ./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
    ./gradlew assembleRelease      # shrunk; signed with the debug key unless a release key is configured

## Tests

    ./gradlew testDebugUnitTest                  # engine, guard, level rules, Level 0 played on the JVM (seconds)
    ./gradlew assembleDebug assembleDebugAndroidTest
    tools/device-tests.sh emulator-5554          # on one emulator: the real shell, Level 0 by touch and by keyboard

`tools/device-tests.sh` refuses to run on anything but an emulator.
`Level0PlayTest` plays Level 0 from a fresh install twice: with taps alone
(chips, Run, Next) and with a hardware keyboard alone (typing, Enter, Tab,
the Up arrow, Ctrl-C). The keyboard test switches the emulator's soft
keyboards off for its run and back on afterwards, because Android treats an
emulator's built-in keyboard as not external and would still show one.

## How it works

- `engine/ShellSession.kt`: one interactive `/system/bin/sh` on a
  pseudo-terminal (`cpp/pty.c`), echo and line editing off. The prompts
  carry markers with the exit status and the folder (`MarkerParser.kt`), so
  an unfinished command (an open quote) shows the shell's `>` prompt, and
  Ctrl-C reaches the running command, not the game.
- `engine/Guard.kt`: stops what would break the game itself (its own
  folder, its shell); Android's sandbox does the rest.
- `game/`: the levels as data (`Level0.kt`), the flow (`GameEngine.kt`).
- `ui/`: the guided console (Compose), the command line (`CommandInput.kt`:
  no suggestions, no autocorrect, no learning; Tab, arrows, Ctrl-C), the key
  bar and the chips.

Licence: GPL-3.0, like the game (see `../LICENSE`).
