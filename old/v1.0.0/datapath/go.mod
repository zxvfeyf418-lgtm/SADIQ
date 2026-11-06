module dev.shizzi/datapath

go 1.27

require gvisor.dev/gvisor v0.0.0-20260804180831-0f34e3556d62

// github.com/xtls/xray-core is deliberately not required here. Its release tags
// are v26.x but its module path has no /v26 suffix, so Go rejects the tags as
// versions. resolve-deps.sh pins it by commit and Go writes the pseudo-version.
