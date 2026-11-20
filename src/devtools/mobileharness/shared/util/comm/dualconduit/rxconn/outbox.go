package rxconn

import "sync"

// latch is a signal that happens once and that any number of goroutines can
// wait for.
type latch struct {
	ch   chan struct{}
	once sync.Once
}

func newLatch() *latch { return &latch{ch: make(chan struct{})} }

// Close makes the signal happen. It may be called more than once.
func (l *latch) Close() { l.once.Do(func() { close(l.ch) }) }

// Wait returns a channel that is closed once the signal has happened.
func (l *latch) Wait() <-chan struct{} { return l.ch }

// IsClosed reports whether the signal has happened.
func (l *latch) IsClosed() bool {
	select {
	case <-l.ch:
		return true
	default:
		return false
	}
}

// outCmd is a signal that waits to be sent to the remote side.
type outCmd struct {
	sig  outSignal
	data []byte
}

// outbox holds the signals that wait to be sent to the remote side. Silencing
// it drops them and wakes everything that waits to send, in one step.
type outbox struct {
	mu       sync.Mutex
	q        []outCmd
	silenced *latch
}

func newOutbox() *outbox { return &outbox{silenced: newLatch()} }

// Push queues cmds, unless the outbox is silenced.
func (o *outbox) Push(cmds ...outCmd) {
	o.mu.Lock()
	defer o.mu.Unlock()
	if !o.silenced.IsClosed() {
		o.q = append(o.q, cmds...)
	}
}

// Pop removes and returns the oldest queued signal.
func (o *outbox) Pop() (cmd outCmd, ok bool) {
	o.mu.Lock()
	defer o.mu.Unlock()
	if len(o.q) == 0 {
		return outCmd{}, false
	}
	cmd = o.q[0]
	// The slot is cleared so that the queue does not keep the bytes alive.
	o.q[0] = outCmd{}
	o.q = o.q[1:]
	if len(o.q) == 0 {
		o.q = nil
	}
	return cmd, true
}

// Len returns the number of queued signals.
func (o *outbox) Len() int {
	o.mu.Lock()
	defer o.mu.Unlock()
	return len(o.q)
}

// Silence drops the queued signals and refuses new ones.
func (o *outbox) Silence() {
	o.mu.Lock()
	defer o.mu.Unlock()
	o.q = nil
	o.silenced.Close()
}

// Silenced returns a channel that is closed once the outbox is silenced.
func (o *outbox) Silenced() <-chan struct{} { return o.silenced.Wait() }
