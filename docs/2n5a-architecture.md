# 2n5a architecture

2n5a is Shizzi v0.5.0 plus an Arabic RTL interface and an on-device WARP config generator.

```
Kotlin (app/src/main/java/dev/shizzi)
  warp/X25519          RFC 7748 key generation (no native code)
  warp/WarpProvisioner POST api.devices.cloudflare.com/v0a4005/reg -> WarpProfile
  warp/WarpProfile     AmneziaWG / WireGuard .conf text
  warp/WarpViewModel   UI state, save to Downloads (MediaStore)
  warp/TunnelNames     tunnel-name rule [a-zA-Z0-9_=+.-]{1,15}: sanitize / unique (pure)
  warp/ConfigStore     saved configs, one filesDir/tunnels/<name>.conf each (pure java.io)
  warp/TunnelController  owns the AmneziaWG GoBackend (process singleton), StateFlow<TunnelStatus>
  warp/TunnelViewModel   saved list, selection, .conf import, connect / disconnect
  ui/twon5a/           Home, Settings, WarpScreen, WelcomeIntro
Go (datapath/)
  datapath.go          buildStack: gVisor netstack over a TUN fd
  forward.go           TCP/UDP forwarders -> flowDialer
    hotspot session:   net.Dialer pinned to the active VPN network
```

## In-app connection

The VPN runs inside 2n5a through the AmneziaWG library
(`com.zaneschepke:amneziawg-android:2.3.7`, package `org.amnezia.awg`, native `libam-go`).

1. A config comes from the generator («تشغيل داخل التطبيق») or from an imported `.conf`
   (`ActivityResultContracts.OpenDocument`). `ConfigSanity` rejects obviously wrong files first.
2. `MainActivity.connectTunnel` calls `VpnService.prepare()`; the consent dialog result starts
   `TunnelViewModel.connect`.
3. `TunnelController` parses the text with `Config.parse`, then `GoBackend.setState(UP)` on
   `Dispatchers.IO`. The library's own `AbstractBackend$VpnService` is merged into the manifest.
4. While up, `getStatistics` is polled every 2 s. Received bytes > 0 means the server answered
   (handshake), which is what flips the label from «تم التشغيل» to «متصل».

The backend lives in a singleton because it remembers which tunnel is up; the UI state
(`ConnectionUiState`) is derived from it, so rotating the screen or leaving it never drops the VPN.

Hotspot sharing needs no extra wiring: the in-app tunnel is a normal system VPN, so Shizzi's
"follow VPN" mode (`VpnMode`) pins hotspot traffic to it like any other VPN.

AmneziaWG lines (`Jc`, `Jmin`, `Jmax`, `I1`) are parsed by the library (`Interface` handles `i1`).
If the AmneziaWG variant fails to start, the error suggests the plain WireGuard variant.
