#!/usr/bin/env bash
# Builds datapath.aar for Gradle: resolves Go dependencies, then gomobile bind.
# Usage: build-aar.sh <output.aar>   (run from the datapath directory by Gradle)
set -euo pipefail

out="${1:?usage: build-aar.sh <output.aar>}"
cd "$(dirname "$0")"

export PATH="$HOME/go/bin:$PATH"
if ! command -v gomobile >/dev/null 2>&1; then
  echo "gomobile not found - run: go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init" >&2
  exit 1
fi

./resolve-deps.sh
gomobile bind -target=android/arm64 -androidapi 24 -o "$out" .
