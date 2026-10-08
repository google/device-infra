package common

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"sync"

	log "github.com/golang/glog"
)

// SignalError is the cause of a context that CancelOnSignal cancelled. It
// wraps context.Canceled.
type SignalError struct {
	Signal os.Signal
}

func (e *SignalError) Error() string {
	return fmt.Sprintf("received %v", e.Signal)
}

// Unwrap returns context.Canceled, so that errors.Is(err, context.Canceled)
// holds.
func (e *SignalError) Unwrap() error {
	return context.Canceled
}

// CancelOnSignal returns a copy of parent that is cancelled, with a
// *SignalError as its cause, on the first of sigs the process receives, so
// that the work under it can stop and clean up rather than be killed part way.
//
// Only the first signal is caught. A second one gets the default handling,
// which for SIGINT and SIGTERM kills the process, for when cleaning up takes
// too long.
//
// Calling stop releases the resources and stops catching sigs, and cancels
// the context if no signal has.
func CancelOnSignal(parent context.Context, sigs ...os.Signal) (ctx context.Context, stop func()) {
	ctx, cancel := context.WithCancelCause(parent)
	ch := make(chan os.Signal, 1)
	signal.Notify(ch, sigs...)
	done := make(chan struct{})
	exited := make(chan struct{})
	go func() {
		defer close(exited)
		select {
		case sig := <-ch:
			// Restore the default handling first, so that a second signal
			// is not swallowed.
			signal.Stop(ch)
			log.Warningf("Received %v; stopping and cleaning up. Send it again to exit immediately.", sig)
			cancel(&SignalError{Signal: sig})
		case <-done:
			signal.Stop(ch)
		}
	}()
	var once sync.Once
	return ctx, func() {
		once.Do(func() {
			close(done)
			<-exited
			cancel(context.Canceled)
		})
	}
}
