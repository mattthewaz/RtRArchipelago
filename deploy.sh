#!/bin/sh
cd "$(dirname "$0")"
OUT="$(realpath "$(pwd)/../../RtR/mods")"
cp target/Archipelago.jar "$OUT/"
echo "[Archipelago] Deployed to $OUT"
