#!/bin/sh
cd "$(dirname "$0")"
mvn package -q && echo "[Archipelago] Build OK"
