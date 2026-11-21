#!/usr/bin/env bash
# Packs a release into dist/ (used by .github/workflows/release.yml):
#   daemons-apprentice-mac.zip, daemons-apprentice-linux.zip   (folder: game + README + LICENSE)
#   daemons-apprentice-mac.sh, daemons-apprentice-linux.sh   (the game file alone)
#   SHA256SUMS.txt, NOTES.md (this version's part of CHANGELOG.md)
#
#   bash tools/package.sh v1.3
# The tag must match TQ_VERSION in the game, and game/ must be up to date.

set -eu
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
tag=${1:?usage: tools/package.sh vX.Y}
ver=${tag#v}
game_ver=$(sed -n 's/^TQ_VERSION="\(.*\)"$/\1/p' src/daemons-apprentice.sh)
if [ "$ver" != "$game_ver" ]; then
  echo "package: tag $tag does not match the game's version $game_ver" >&2; exit 1
fi
bash tools/build.sh --check

rm -rf dist; mkdir -p dist/stage
for os in mac linux; do
  d="dist/stage/daemons-apprentice-$os"
  mkdir -p "$d"
  cp "game/$os/daemons-apprentice.sh" "game/$os/README.md" LICENSE "$d/"
  chmod 755 "$d/daemons-apprentice.sh"
  (cd dist/stage && zip -qrX "../daemons-apprentice-$os.zip" "daemons-apprentice-$os")
  cp "game/$os/daemons-apprentice.sh" "dist/daemons-apprentice-$os.sh"
done
rm -rf dist/stage
(cd dist && { sha256sum ./*.zip ./*.sh 2>/dev/null || shasum -a 256 ./*.zip ./*.sh; } | sed 's#  \./#  #' > SHA256SUMS.txt)

# Release notes: this version's section of CHANGELOG.md, then the downloads.
awk -v v="$ver" '
  /^## / { on = ($2 == v) ; if (on) next }
  on' CHANGELOG.md > dist/NOTES.md
[ -s dist/NOTES.md ] || { echo "package: CHANGELOG.md has no section \"## $ver\"" >&2; exit 1; }
cat >> dist/NOTES.md <<NOTES

### Download

| Your computer | Download |
|---|---|
| **macOS** (Apple silicon and Intel) | [daemons-apprentice-mac.zip](https://github.com/alexpp90/daemons-apprentice/releases/download/$tag/daemons-apprentice-mac.zip) |
| **Linux** | [daemons-apprentice-linux.zip](https://github.com/alexpp90/daemons-apprentice/releases/download/$tag/daemons-apprentice-linux.zip) |

Open the folder and follow its README: four steps, about a minute.
NOTES
ls -l dist
