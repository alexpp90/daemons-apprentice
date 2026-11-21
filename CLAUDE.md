# The Daemon's Apprentice

A single-file Bash game that teaches the terminal on macOS and Linux.

- **Maintainers:** if `private/` exists (the private workshop repository,
  checked out here and ignored by git), read `private/CLAUDE.md` first. It
  holds the binding requirements and the rules for working on the game.
- Edit `src/daemons-apprentice.sh`, then run `bash tools/build.sh`. Never
  edit `game/` by hand.
- Tests: `bash tests/run_all.sh quick` while editing, `bash tests/run_all.sh`
  before a commit (see `tests/README.md`). Never rebuild or edit a test
  script while a test run is going: bash reads scripts as it runs them.
- The game must run on the Mac's `/bin/bash` 3.2 with stock BSD tools: no
  Bash 4+ features, no GNU-only flags (the static checks scan for them).
- Releases: push a tag `vX.Y` that matches `TQ_VERSION`. CI tests it, packs
  it (`tools/package.sh`) and publishes it. `CHANGELOG.md` needs a
  `## X.Y` section.
