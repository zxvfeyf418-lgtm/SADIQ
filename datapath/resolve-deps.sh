#!/usr/bin/env bash
# Resolves the Go dependencies and generates go.sum.
#
# gomobile bind needs golang.org/x/mobile/bind recorded in go.mod; tools.go keeps
# it there so `go mod tidy` does not drop it.
set -euo pipefail

cd "$(dirname "$0")"
go get golang.org/x/mobile/bind
go mod tidy
