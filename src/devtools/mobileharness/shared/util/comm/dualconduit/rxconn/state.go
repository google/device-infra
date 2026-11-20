package rxconn

// This file holds the state transition table of a Conn. It has no goroutines,
// no locks and no I/O.

// State is the state of a Conn. A Conn started with Open begins in StateInit,
// one started with Accept in StateEstablished.
type State int32

// The states of a Conn.
const (
	// StateInit: the client has connected and has sent nothing. The stream
	// does not exist yet, neither in rsocket-go nor on the peer.
	StateInit State = iota
	// StateEstablished: both directions are open.
	StateEstablished
	// StateHalfClosedLocal: the local peer finished sending.
	StateHalfClosedLocal
	// StateHalfClosedRemote: the remote side finished sending.
	StateHalfClosedRemote
	// StateClosed: both directions have ended, cleanly or not.
	StateClosed
	numStates
)

func (s State) String() string {
	switch s {
	case StateInit:
		return "INIT"
	case StateEstablished:
		return "ESTABLISHED"
	case StateHalfClosedLocal:
		return "HALF_CLOSED_LOCAL"
	case StateHalfClosedRemote:
		return "HALF_CLOSED_REMOTE"
	case StateClosed:
		return "CLOSED"
	}
	return "UNKNOWN"
}

// event is something that happens to a Conn. The prefix gives the source:
// l for the local TCP connection, r for the remote side, c for the conduit.
type event int

const (
	evLData  event = iota // bytes were read from the local socket
	evLFin                // the local peer finished sending
	evLRst                // the local connection was reset or failed
	evRData               // a data payload arrived
	evRFin                // the FIN marker arrived
	evRRst                // the RST marker arrived
	evRFail               // the inbound Flux failed
	evCClose              // the stream's context ended
	numEvents
)

func (e event) String() string {
	switch e {
	case evLData:
		return "L_DATA"
	case evLFin:
		return "L_FIN"
	case evLRst:
		return "L_RST"
	case evRData:
		return "R_DATA"
	case evRFin:
		return "R_FIN"
	case evRRst:
		return "R_RST"
	case evRFail:
		return "R_FAIL"
	case evCClose:
		return "C_CLOSE"
	}
	return "UNKNOWN"
}

// outSignal is a signal a transition sends to the remote side.
type outSignal int

const (
	outNone outSignal = iota
	outData
	outFin
	outRst
	outComplete
)

// localOp is what a transition does to the local socket.
type localOp int

const (
	localNone      localOp = iota
	localWrite             // write the payload to the local socket
	localShutWrite         // shut down the write side: the local peer sees a FIN
	localClose             // normal close
	localReset             // close with a reset
)

// step is one cell of the state transition table.
type step struct {
	// valid is false for an event that cannot occur in the state.
	valid bool
	next  State
	out   outSignal
	local localOp
	// complete: send Complete after out, as the last frame.
	complete bool
	// silent: send nothing more, and drop what has not been sent.
	silent bool
}

// transitions is the transition table: transitions[state][event].
var transitions = [numStates][numEvents]step{
	// INIT exists only on the side that opens the stream. Its first bytes
	// are queued here and create the stream.
	StateInit: {
		evLData:  {valid: true, next: StateEstablished, out: outData},
		evLFin:   {valid: true, next: StateClosed, local: localClose},
		evLRst:   {valid: true, next: StateClosed, local: localClose},
		evCClose: {valid: true, next: StateClosed, silent: true, local: localReset},
	},
	StateEstablished: {
		evLData:  {valid: true, next: StateEstablished, out: outData},
		evLFin:   {valid: true, next: StateHalfClosedLocal, out: outFin},
		evLRst:   {valid: true, next: StateClosed, out: outRst, complete: true, local: localClose},
		evRData:  {valid: true, next: StateEstablished, local: localWrite},
		evRFin:   {valid: true, next: StateHalfClosedRemote, local: localShutWrite},
		evRRst:   {valid: true, next: StateClosed, complete: true, local: localReset},
		evRFail:  {valid: true, next: StateClosed, silent: true, local: localReset},
		evCClose: {valid: true, next: StateClosed, silent: true, local: localReset},
	},
	StateHalfClosedLocal: {
		evLRst:   {valid: true, next: StateClosed, out: outRst, complete: true, local: localClose},
		evRData:  {valid: true, next: StateHalfClosedLocal, local: localWrite},
		evRFin:   {valid: true, next: StateClosed, complete: true, local: localClose},
		evRRst:   {valid: true, next: StateClosed, complete: true, local: localReset},
		evRFail:  {valid: true, next: StateClosed, silent: true, local: localReset},
		evCClose: {valid: true, next: StateClosed, silent: true, local: localReset},
	},
	StateHalfClosedRemote: {
		evLData: {valid: true, next: StateHalfClosedRemote, out: outData},
		evLFin:  {valid: true, next: StateClosed, out: outFin, complete: true, local: localClose},
		evLRst:  {valid: true, next: StateClosed, out: outRst, complete: true, local: localClose},
		evRRst:  {valid: true, next: StateClosed, complete: true, local: localReset},
		// The remote side had finished cleanly, so the local peer has been
		// sent everything. A reset would discard what it has not read yet.
		evRFail:  {valid: true, next: StateClosed, silent: true, local: localClose},
		evCClose: {valid: true, next: StateClosed, silent: true, local: localClose},
	},
	// Events reach a Conn from several goroutines, so one can arrive after
	// another has closed the Conn. A read fails because the Conn itself has
	// reset the socket, or the conduit closes right after the local peer
	// failed. Those are ignored, and R_FAIL or C_CLOSE still drops anything
	// not yet sent.
	StateClosed: {
		evLData:  {valid: true, next: StateClosed},
		evLFin:   {valid: true, next: StateClosed},
		evLRst:   {valid: true, next: StateClosed},
		evRData:  {valid: true, next: StateClosed},
		evRFin:   {valid: true, next: StateClosed},
		evRRst:   {valid: true, next: StateClosed},
		evRFail:  {valid: true, next: StateClosed, silent: true},
		evCClose: {valid: true, next: StateClosed, silent: true},
	},
}

// signals returns what st sends to the remote side, in order.
func (st step) signals(data []byte) []outCmd {
	var cmds []outCmd
	if st.out != outNone {
		cmds = append(cmds, outCmd{sig: st.out, data: data})
	}
	if st.complete {
		cmds = append(cmds, outCmd{sig: outComplete})
	}
	return cmds
}
