#!/bin/sh
cd "$(dirname "$0")"
mkdir -p "$(pwd)/../../output/mods"
OUT="$(realpath "$(pwd)/../../output/mods")"
cp target/Archipelago-0.1.jar "$OUT/"
echo "[Archipelago] Deployed to $OUT"
