package datapath

import (
	"context"
	"net"
)

// flowDialer is the seam between the netstack forwarders and wherever a flow
// finally leaves: a plain (optionally network-pinned) socket for hotspot
// sharing, or a custom upstream. *net.Dialer satisfies it.
type flowDialer interface {
	DialContext(ctx context.Context, network, address string) (net.Conn, error)
}
