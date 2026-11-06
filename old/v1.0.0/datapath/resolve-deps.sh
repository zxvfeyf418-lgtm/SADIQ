#!/usr/bin/env bash
# Resolves the embedded Xray core and generates go.sum.
#
# Xray's tags (v26.x) are not valid Go module versions (no /v26 in the module
# path), so it is pinned by the commit of tag v26.10.31 and Go turns that into a
# pseudo-version.
set -euo pipefail

XRAY_COMMIT="a4a3dff5b20c2faae567b742c172c7b95e75c083"

cd "$(dirname "$0")"
go get "github.com/xtls/xray-core@${XRAY_COMMIT}"
# gomobile bind needs golang.org/x/mobile/bind in go.mod (kept by tools.go).
go get golang.org/x/mobile/bind
go mod tidy
