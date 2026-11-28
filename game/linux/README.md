# The Daemon's Apprentice (for Linux)

A game that teaches you the terminal: the text window that AI assistants
use to work on your computer. No experience needed. About 3 hours, and you
can stop at any time.

## Start the game

1. **Open a terminal.** Press **Ctrl + Alt + T**. If nothing happens, open
   your list of applications and search for **Terminal**.

2. **Type `bash` and one space.** Don't press Enter yet.
   `bash` is the program that reads the game file and runs it.

3. **Drag the file `daemons-apprentice.sh` from your file manager into the
   terminal window.** Its location appears after `bash `. Dragging types
   it for you.

4. **Press Enter.** The game starts.

## Inside the game

- Stuck? Type `help` and press Enter. It shows everything that helps.
- Can't find a key like `{` or `|`? Type `keys`.
- Every task you solve adds its command to your own cheatsheet, the file
  `CHEATSHEET.md` in the folder `apprentice-playground`. Type `cheat` to
  see it.
- Type `trophies` anytime to see your rank, XP, clean-solve streak, and
  unlocked trophies (also saved in `TROPHIES.md`, with a signed
  `CERTIFICATE.txt` at the finish).
- Three times along the way the game offers an optional side quest: a
  portrait of your own computer, keeping an eye on an AI agent, and a
  read-only security checkup of your computer. Say yes or no; all three
  stay in the level menu.
- The game explains every command before you type it. Make that your
  habit everywhere: never run a command you do not understand.

## Later

- **Continue:** start the game the same way. Choose **c**.
- **Start over:** choose **r** in the game's menu.
- **Remove it:** choose **r** in the menu, then delete the file and the
  folder `apprentice-playground` in your home folder.

## What the game does on your computer

It runs the commands you type, for real. It only writes into the folder
`apprentice-playground` in your home folder, plus a small save file
(`.apprentice-save`). It never asks for your password, never uses
administrator rights (`sudo`), and nothing goes over the internet.

## Problems?

- **"No such file or directory":** start again from step 2 and drag the
  file into the window instead of typing its name.
- **Dragging does nothing:** in step 2, type `bash` and a space, then the
  folder and the name instead, for example `Downloads/daemons-apprentice.sh`
  if the file is in Downloads.
- **Strange characters instead of colours:** type `NO_COLOR=1` and a
  space before `bash` in step 2.

## For testers: record a session

Add `--log` after the file name in step 3 (a space, two minus signs,
`log`). The game then saves everything on the screen, including what you
type, as a text file in your home folder. It tells you the file name at
the start and at the end.
