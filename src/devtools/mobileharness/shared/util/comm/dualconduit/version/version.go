// Package version defines the version of the DualConduit project.
package version

import "fmt"

// Version is the current semantic version of DualConduit (acceptor, dialer, etc.).
const Version = "0.1.0"

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
