# 2n5a architecture

2n5a is Shizzi v0.5.0 plus a V2Ray/Xray VPN and an Arabic RTL interface.

```
Kotlin (app/src/main/java/dev/shizzi)
  vpn/ConfigParser      vless/vmess/trojan/ss link  -> ProxyConfig
  vpn/XrayConfigBuilder ProxyConfig                 -> Xray JSON (outbounds only, no inbound)
  vpn/XrayVpnService    VpnService: TUN + protect() + lifecycle
        |  Datapath.setProtector / startXray / startVpn   (gomobile AAR)
Go (datapath/)
  xray.go               embeds Xray core; core.Dial per flow; socket protector
  datapath.go           buildStack: gVisor netstack over a TUN fd (shared)
  forward.go            TCP/UDP forwarders -> flowDialer
    hotspot session:  net.Dialer pinned to the VPN network (Shizzi, unchanged)
    VPN session:      xrayDialer -> Xray dispatcher -> proxy outbound
```

The VPN creates a real `TRANSPORT_VPN` network, so Shizzi's "follow VPN" logic
finds it and pins hotspot traffic to it without further changes.

Xray's own sockets are exempted with `VpnService.protect` (`SetProtector`), and a
failed protect fails the dial so traffic never loops into the tunnel.
