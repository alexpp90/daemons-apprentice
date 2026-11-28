# The Daemon's Apprentice (for Mac)

A game that teaches you the Terminal: the text window that AI assistants
use to work on your computer. No experience needed. About 3 hours, and you
can stop at any time.

## Start the game

1. **Open the Terminal app.** Press **Command + Space**, type `Terminal`,
   press **return**. A window with a blinking cursor appears.

2. **Type `bash` and one space.** Don't press return yet.
   `bash` is the program that reads the game file and runs it.

3. **Drag the file `daemons-apprentice.sh` from Finder into the Terminal
   window.** Its location appears after `bash `. Dragging types it for you.

4. **Press return.** The game starts.

If your Mac asks whether Terminal may access a folder (for example
Downloads), click **Allow**: Terminal needs it to read the game file.

## Inside the game

- Stuck? Type `help` and press return. It shows everything that helps.
- Can't find a key like `{` or `|`? Type `keys`.
- Every task you solve adds its command to your own cheatsheet, the file
  `CHEATSHEET.md` in the folder `apprentice-playground`. Type `cheat` to
  see it.
- Type `trophies` anytime to see your rank, XP, clean-solve streak, and
  unlocked trophies (also saved in `TROPHIES.md`, with a signed
  `CERTIFICATE.txt` at the finish).
- Three times along the way the game offers an optional side quest: a
  portrait of your own computer, keeping an eye on an AI agent, and a
  read-only security checkup of your Mac. Say yes or no; all three stay in
  the level menu.
- The game explains every command before you type it. Make that your
  habit everywhere: never run a command you do not understand.

## Later

- **Continue:** start the game the same way. Choose **c**.
- **Start over:** choose **r** in the game's menu.
- **Remove it:** choose **r** in the menu, then delete the file and the
  folder `apprentice-playground` in your home folder.

## What the game does on your Mac

It runs the commands you type, for real. It only writes into the folder
`apprentice-playground` in your home folder, plus a small save file
(`.apprentice-save`). It never asks for your password, never uses
administrator rights, and nothing goes over the internet.

## Problems?

- **"No such file or directory":** start again from step 2 and drag the
  file into the window instead of typing its name.
- **Strange characters instead of colours:** type `NO_COLOR=1` and a
  space before `bash` in step 2.

## For testers: record a session

Add `--log` after the file name in step 3 (a space, two minus signs,
`log`). The game then saves everything on the screen, including what you
type, as a text file in your home folder. It tells you the file name at
the start and at the end.
