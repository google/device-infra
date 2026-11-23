// Package version defines the version of the DualConduit project.
package version

import (
	"fmt"
	"strings"
)

// Version is the current semantic version of DualConduit (acceptor, dialer, etc.).
const Version = "0.2.0"

// ProtocolVersion names the protocol between a dialer and an acceptor. It
// goes up only when a change makes the two sides incompatible. The dialer
// sends it in the SETUP frame, and the acceptor compares it with its own.
//
//	0: version 0.1.2 and older. No markers; Complete ends a direction.
//	1: version 0.2.0 and newer. Stream state machine with FIN and RST markers.
//
// An acceptor serves every dialer from MinProtocolVersion to ProtocolVersion,
// each in the protocol the dialer speaks, and logs a warning for one that is
// older than itself. Any other dialer is refused with MismatchError, and
// exits. An acceptor that raises ProtocolVersion keeps serving the version
// before it, so that dialers can be upgraded one by one.
const ProtocolVersion = 1

// MinProtocolVersion is the oldest protocol an acceptor still serves.
//
// Protocol 0 support is temporary. It lets the dialers of an acceptor that
// has been upgraded to 0.2.x be upgraded one by one. A dialer of protocol 0
// must not be refused while it runs: it does not take a refusal as final and
// sets its conduits up again at once, without end. An acceptor logs a warning
// for every dialer it serves in an older protocol. Once no acceptor logs it
// any more, the support is removed:
//
//  1. Set MinProtocolVersion to 1.
//  2. Delete protocol0.go and protocol0_test.go in rxconn.
//  3. Delete the code marked "Protocol 0 support" in rxconn, conduit and
//     acceptor, and change the tests marked with it to expect a refusal.
const MinProtocolVersion = 0

// mismatchMarker starts the message of every MismatchError. A dialer finds
// it in the error with which its connection was closed.
const mismatchMarker = "DualConduit protocol mismatch"

// MismatchError returns the error with which an acceptor refuses a dialer
// that speaks dialerProtocol.
func MismatchError(dialerProtocol int32) error {
	action := "upgrade the dialer"
	if dialerProtocol > ProtocolVersion {
		action = "upgrade the acceptor"
	}
	return fmt.Errorf("%s: dialer speaks protocol %d, this acceptor (version %s) speaks %d: %s", mismatchMarker, dialerProtocol, Version, ProtocolVersion, action)
}

// IsMismatch reports whether err says that an acceptor refused a dialer
// because of its protocol version. The error may have crossed the connection,
// so it is recognised by its message.
func IsMismatch(err error) bool {
	return err != nil && strings.Contains(err.Error(), mismatchMarker)
}

// Banner is the ASCII art banner for DualConduit.
const Banner = `
 ____   _   _     _     _         ____   ___   _   _  ____   _   _  ___  _____ 
|  _ \ | | | |   / \   | |       / ___| / _ \ | \ | ||  _ \ | | | ||_ _||_   _|
| | | || | | |  / _ \  | |      | |    | | | ||  \| || | | || | | | | |   | |  
| |_| || |_| | / ___ \ | |___   | |___ | |_| || |\  || |_| || |_| | | |   | |  
|____/  \___/ /_/   \_\|_____|   \____| \___/ |_| \_||____/  \___/ |___|  |_|  `

// PrintBanner prints the ASCII banner and version information to stdout.
func PrintBanner() {
	fmt.Printf("%s\n\nDualConduit Version: %s\n\n", Banner, Version)
}
