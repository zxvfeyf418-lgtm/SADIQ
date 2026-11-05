package datapath

import "testing"

func TestParseDestination(t *testing.T) {
	tests := []struct {
		network, address string
		wantErr          bool
	}{
		{"tcp", "93.184.216.34:443", false},
		{"udp", "1.1.1.1:53", false},
		{"tcp", "[2001:db8::1]:443", false},
		{"icmp", "1.1.1.1:53", true},
		{"tcp", "no-port", true},
		{"tcp", "1.1.1.1:99999", true},
	}
	for _, test := range tests {
		_, err := parseDestination(test.network, test.address)
		if (err != nil) != test.wantErr {
			t.Errorf("parseDestination(%q, %q): err=%v, wantErr=%v", test.network, test.address, err, test.wantErr)
		}
	}
}

func TestStartVpnRequiresXray(t *testing.T) {
	StopXray()
	if _, err := StartVpn(-1, 1500); err == nil {
		t.Fatal("StartVpn without a running core must fail")
	}
}
