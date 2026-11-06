//go:build tools

package datapath

// gomobile bind needs golang.org/x/mobile/bind recorded in go.mod. Nothing in
// the package imports it, so without this blank import `go mod tidy` drops the
// requirement and gomobile fails with "missing golang.org/x/mobile dependency".
import _ "golang.org/x/mobile/bind"
