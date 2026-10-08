package common

import (
	"context"
	"errors"
	"os"
	"os/signal"
	"syscall"
	"testing"
	"time"
)

// SIGUSR1 stands in for SIGINT and SIGTERM, which would disturb the test
// runner if they escaped. Each test that sends it keeps it caught throughout
// with a channel of its own, so that it never gets the default handling,
// which would kill the test.
func catchSIGUSR1(t *testing.T) chan os.Signal {
	t.Helper()
	ch := make(chan os.Signal, 2)
	signal.Notify(ch, syscall.SIGUSR1)
	t.Cleanup(func() { signal.Stop(ch) })
	return ch
}

func sendSIGUSR1(t *testing.T) {
	t.Helper()
	if err := syscall.Kill(os.Getpid(), syscall.SIGUSR1); err != nil {
		t.Fatal(err)
	}
}

func TestCancelOnSignal_CancelsWithSignalCause(t *testing.T) {
	catchSIGUSR1(t)
	ctx, stop := CancelOnSignal(context.Background(), syscall.SIGUSR1)
	defer stop()

	sendSIGUSR1(t)
	select {
	case <-ctx.Done():
	case <-time.After(10 * time.Second):
		t.Fatal("Context not cancelled after the signal")
	}
	cause := context.Cause(ctx)
	var sigErr *SignalError
	if !errors.As(cause, &sigErr) || sigErr.Signal != syscall.SIGUSR1 {
		t.Errorf("Cause = %v, want a *SignalError for SIGUSR1", cause)
	}
	if !errors.Is(cause, context.Canceled) {
		t.Errorf("Cause = %v, want it to wrap context.Canceled", cause)
	}
	if got, want := cause.Error(), "received user defined signal 1"; got != want {
		t.Errorf("Cause = %q, want %q", got, want)
	}
}

// TestCancelOnSignal_Stop checks that stop cancels the context without a
// signal cause, can be called again, and stops catching the signal.
func TestCancelOnSignal_Stop(t *testing.T) {
	ch := catchSIGUSR1(t)
	ctx, stop := CancelOnSignal(context.Background(), syscall.SIGUSR1)
	stop()
	stop()

	if !errors.Is(ctx.Err(), context.Canceled) {
		t.Fatalf("ctx.Err() = %v after stop, want context.Canceled", ctx.Err())
	}
	sendSIGUSR1(t)
	select {
	case <-ch:
	case <-time.After(10 * time.Second):
		t.Fatal("The test's own channel did not receive the signal")
	}
	var sigErr *SignalError
	if cause := context.Cause(ctx); errors.As(cause, &sigErr) {
		t.Errorf("Cause = %v after stop and a later signal, want the signal not to be caught", cause)
	}
}

func TestCancelOnSignal_ParentCancelled(t *testing.T) {
	parent, cancel := context.WithCancel(context.Background())
	ctx, stop := CancelOnSignal(parent, syscall.SIGUSR1)
	defer stop()
	cancel()
	select {
	case <-ctx.Done():
	case <-time.After(10 * time.Second):
		t.Fatal("Context not cancelled with its parent")
	}
}
