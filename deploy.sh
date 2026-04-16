#!/bin/sh
cd "$(dirname "$0")"
mkdir -p "$(pwd)/../../output/mods"
OUT="$(realpath "$(pwd)/../../output/mods")"
cp target/Archipelago-1.0-SNAPSHOT.jar "$OUT/"
echo "[Archipelago] Deployed to $OUT"
