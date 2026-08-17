#!/usr/bin/env bash
# Build BOTH apps in this repo:
#   build-music.sh  -> dist/TheMusicLivesOn8-v1.0.apk  (shared-library host)
#   build-lyric.sh  -> dist/TheLyricLivesOn8-v1.0.apk  (shared-library client)
# Run from the repository root. Both are signed with the same release.jks.
set -euo pipefail

cd "$(dirname "$0")"
bash build-music.sh
bash build-lyric.sh
echo "==> ALL DONE"
ls -la dist/*.apk
