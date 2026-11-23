package rxconn

// Protocol 0 support. This file holds everything a Conn does differently for
// a remote side that speaks protocol 0 (version 0.1.2 and older). It exists so
// that the dialers of an upgraded acceptor can be upgraded one by one, and it
// is deleted when acceptors stop serving protocol 0: see MinProtocolVersion
// in the version package.

// WithPeerProtocol tells the Conn which DualConduit protocol the remote side
// speaks. A remote side of protocol 0 does not know the markers: for it,
// Complete ends a direction. The Conn then sends Complete where it would send
// a FIN or RST marker. Such a remote side cannot be told about a reset, and it
// cannot be sent anything after the local peer has finished sending. Without
// this option the remote side is taken to know the markers.
func WithPeerProtocol(protocol int32) Option {
	return func(c *Conn) { c.p0.on = protocol < 1 }
}

// protocol0 is what a Conn keeps for a remote side of protocol 0. Only the
// goroutine that sends the signals of the Conn uses it.
type protocol0 struct {
	// on: the remote side speaks protocol 0.
	on bool
	// completed: Complete has been sent.
	completed bool
}

// translate returns the signal to send to the remote side in place of sig. It
// reports false if nothing is to be sent: Complete is sent once, and for a
// remote side of protocol 0 both a FIN and the end of the stream are a
// Complete.
func (p *protocol0) translate(sig outSignal) (outSignal, bool) {
	if !p.on {
		return sig, true
	}
	if sig == outFin || sig == outRst {
		sig = outComplete
	}
	if sig == outComplete {
		if p.completed {
			return sig, false
		}
		p.completed = true
	}
	return sig, true
}
