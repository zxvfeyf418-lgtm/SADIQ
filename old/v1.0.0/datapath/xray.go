package datapath

import (
	"context"
	"errors"
	"fmt"
	"net"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"

	xnet "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/infra/conf/serial"
	"github.com/xtls/xray-core/transport/internet"

	// Registers every protocol, transport and feature the config may name.
	_ "github.com/xtls/xray-core/main/distro/all"
)

// Protector exempts a socket from the VPN so Xray's own connection to the
// server does not loop back into the tunnel. Implemented in Kotlin by
// VpnService.protect.
type Protector interface {
	Protect(fd int) bool
}

var (
	protector     atomic.Pointer[protectorHolder]
	registerOnce  sync.Once
	registerError error

	xrayMu       sync.Mutex
	xrayInstance *core.Instance
)

type protectorHolder struct{ Protector }

// SetProtector installs the socket protector. Call before StartXray.
func SetProtector(p Protector) error {
	if p == nil {
		return errors.New("SetProtector: nil protector")
	}
	protector.Store(&protectorHolder{p})

	registerOnce.Do(func() {
		control := func(network, address string, conn syscall.RawConn) error {
			holder := protector.Load()
			if holder == nil {
				return errors.New("xray dial: no protector installed")
			}
			p := holder.Protector
			var ok bool
			if err := conn.Control(func(fd uintptr) { ok = p.Protect(int(fd)) }); err != nil {
				return err
			}
			if !ok {
				return fmt.Errorf("xray dial: VpnService.protect failed for %s %s", network, address)
			}
			return nil
		}
		// Controllers are appended globally, hence once per process. A failed
		// protect fails the dial: fail closed rather than loop into the tunnel.
		registerError = errors.Join(
			internet.RegisterDialerController(control),
			internet.RegisterListenerController(control),
		)
	})
	return registerError
}

// StartXray boots the embedded Xray core from a JSON config. Replaces a running one.
func StartXray(configJSON string) error {
	xrayMu.Lock()
	defer xrayMu.Unlock()

	stopXrayLocked()

	config, err := serial.LoadJSONConfig(strings.NewReader(configJSON))
	if err != nil {
		return fmt.Errorf("StartXray: config: %w", err)
	}
	instance, err := core.New(config)
	if err != nil {
		return fmt.Errorf("StartXray: create: %w", err)
	}
	if err := instance.Start(); err != nil {
		return fmt.Errorf("StartXray: start: %w", err)
	}
	xrayInstance = instance
	return nil
}

// StopXray stops the core; safe to call when none is running.
func StopXray() {
	xrayMu.Lock()
	defer xrayMu.Unlock()
	stopXrayLocked()
}

func stopXrayLocked() {
	if xrayInstance != nil {
		_ = xrayInstance.Close()
		xrayInstance = nil
	}
}

// XrayVersion reports the embedded core's version.
func XrayVersion() string {
	return core.Version()
}

// StartVpn attaches a netstack to the VPN's TUN and dials every flow through
// the running Xray core. StartXray must have succeeded first.
func StartVpn(tunFD int, mtu int) (*Session, error) {
	xrayMu.Lock()
	instance := xrayInstance
	xrayMu.Unlock()
	if instance == nil {
		return nil, errors.New("StartVpn: xray is not running")
	}

	netStack, err := buildStack(tunFD, mtu)
	if err != nil {
		return nil, err
	}
	installForwarders(netStack, &xrayDialer{instance: instance})
	return &Session{stack: netStack}, nil
}

// xrayDialer opens flows through the Xray dispatcher (routing + outbounds).
type xrayDialer struct {
	instance *core.Instance
}

func (d *xrayDialer) DialContext(ctx context.Context, network, address string) (net.Conn, error) {
	destination, err := parseDestination(network, address)
	if err != nil {
		return nil, err
	}
	return core.Dial(ctx, d.instance, destination)
}

// parseDestination converts a forwarder's "host:port" into an Xray destination.
func parseDestination(network, address string) (xnet.Destination, error) {
	host, portText, err := net.SplitHostPort(address)
	if err != nil {
		return xnet.Destination{}, fmt.Errorf("destination %q: %w", address, err)
	}
	port, err := strconv.ParseUint(portText, 10, 16)
	if err != nil {
		return xnet.Destination{}, fmt.Errorf("destination %q: bad port: %w", address, err)
	}

	switch {
	case strings.HasPrefix(network, "tcp"):
		return xnet.TCPDestination(xnet.ParseAddress(host), xnet.Port(port)), nil
	case strings.HasPrefix(network, "udp"):
		return xnet.UDPDestination(xnet.ParseAddress(host), xnet.Port(port)), nil
	}
	return xnet.Destination{}, fmt.Errorf("unsupported network %q", network)
}
