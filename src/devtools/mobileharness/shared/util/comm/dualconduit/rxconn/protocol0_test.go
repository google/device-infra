package rxconn

// Protocol 0 support: this file is deleted together with protocol0.go.

import (
	"net"
	"testing"

	"github.com/rsocket/rsocket-go/rx"
)

// TestPeerProtocol0 covers a remote side of protocol 0 (version 0.1.2 and
// older), which does not know the markers: the Conn must send Complete where
// it would send a marker, and send it once.
func TestPeerProtocol0(t *testing.T) {
	start := func(t *testing.T) (*Conn, *net.TCPConn, *fakePeer) {
		t.Helper()
		local, sock := tcpPair(t)
		p := newFakePeer(t)
		conn, out, err := Accept(t.Context(), sock, p.incoming(), WithPeerProtocol(0))
		if err != nil {
			t.Fatalf("Accept() unexpected error: %v", err)
		}
		p.subscribe(out, rx.RequestMax)
		return conn, local, p
	}
	// startRequester returns a Conn of the side that opens the stream, as the
	// acceptor is on a reverse conduit, with its first bytes sent and the
	// stream confirmed.
	startRequester := func(t *testing.T) (*Conn, *net.TCPConn, *fakePeer) {
		t.Helper()
		local, sock := tcpPair(t)
		p := newFakePeer(t)
		conn, err := Open(t.Context(), sock, p.open, WithPeerProtocol(0))
		if err != nil {
			t.Fatalf("Open() unexpected error: %v", err)
		}
		if _, err := local.Write([]byte("req")); err != nil {
			t.Fatalf("local.Write() unexpected error: %v", err)
		}
		p.mustReceive("data:req")
		p.confirm()
		return conn, local, p
	}

	t.Run("requester_local_fin_sends_complete", func(t *testing.T) {
		t.Parallel()
		conn, local, p := startRequester(t)
		if err := local.CloseWrite(); err != nil {
			t.Fatalf("local.CloseWrite() unexpected error: %v", err)
		}

		p.mustReceive("complete")
		if got := waitState(conn, StateHalfClosedLocal); got != StateHalfClosedLocal {
			t.Errorf("State() = %v, want %v", got, StateHalfClosedLocal)
		}

		// The remote side answers, and ends its direction with Complete.
		p.send("resp")
		if gotData, _, err := readLocal(local, len("resp"), false); err != nil || gotData != "resp" {
			t.Errorf("readLocal() = (%q, %v), want (\"resp\", nil)", gotData, err)
		}
		p.in.Complete()
		if _, gotEnd, err := readLocal(local, 0, true); err != nil || gotEnd != "eof" {
			t.Errorf("readLocal() end = (%q, %v), want (\"eof\", nil)", gotEnd, err)
		}
		if !waitReturns(conn, waitTime) {
			t.Errorf("Wait() did not return within %v", waitTime)
		}
		p.mustReceive()
	})

	t.Run("requester_remote_complete_then_local_fin", func(t *testing.T) {
		t.Parallel()
		conn, local, p := startRequester(t)
		p.send("resp")
		p.in.Complete()
		if gotData, gotEnd, err := readLocal(local, 0, true); err != nil || gotData != "resp" || gotEnd != "eof" {
			t.Errorf("readLocal() = (%q, %q, %v), want (\"resp\", \"eof\", nil)", gotData, gotEnd, err)
		}
		if got := waitState(conn, StateHalfClosedRemote); got != StateHalfClosedRemote {
			t.Fatalf("State() = %v, want %v", got, StateHalfClosedRemote)
		}
		if err := local.CloseWrite(); err != nil {
			t.Fatalf("local.CloseWrite() unexpected error: %v", err)
		}

		p.mustReceive("complete")
		if !waitReturns(conn, waitTime) {
			t.Errorf("Wait() did not return within %v", waitTime)
		}
		p.mustReceive()
	})

	t.Run("local_fin_sends_complete", func(t *testing.T) {
		t.Parallel()
		conn, local, p := start(t)
		if err := local.CloseWrite(); err != nil {
			t.Fatalf("local.CloseWrite() unexpected error: %v", err)
		}

		p.mustReceive("complete")
		if got := waitState(conn, StateHalfClosedLocal); got != StateHalfClosedLocal {
			t.Errorf("State() = %v, want %v", got, StateHalfClosedLocal)
		}

		// The remote side can still send, and ends its direction with Complete.
		p.send("resp")
		if gotData, _, err := readLocal(local, len("resp"), false); err != nil || gotData != "resp" {
			t.Errorf("readLocal() = (%q, %v), want (\"resp\", nil)", gotData, err)
		}
		p.in.Complete()
		if _, gotEnd, err := readLocal(local, 0, true); err != nil || gotEnd != "eof" {
			t.Errorf("readLocal() end = (%q, %v), want (\"eof\", nil)", gotEnd, err)
		}
		if !waitReturns(conn, waitTime) {
			t.Errorf("Wait() did not return within %v", waitTime)
		}
		p.mustReceive()
	})

	t.Run("local_rst_sends_complete_once", func(t *testing.T) {
		t.Parallel()
		conn, local, p := start(t)
		if err := local.SetLinger(0); err != nil {
			t.Fatalf("local.SetLinger(0) unexpected error: %v", err)
		}
		local.Close()

		p.mustReceive("complete")
		p.in.Complete()
		if !waitReturns(conn, waitTime) {
			t.Errorf("Wait() did not return within %v", waitTime)
		}
		p.mustReceive()
	})

	t.Run("remote_complete_then_local_fin", func(t *testing.T) {
		t.Parallel()
		conn, local, p := start(t)
		p.in.Complete()
		if got := waitState(conn, StateHalfClosedRemote); got != StateHalfClosedRemote {
			t.Fatalf("State() = %v, want %v", got, StateHalfClosedRemote)
		}
		if err := local.CloseWrite(); err != nil {
			t.Fatalf("local.CloseWrite() unexpected error: %v", err)
		}

		p.mustReceive("complete")
		if !waitReturns(conn, waitTime) {
			t.Errorf("Wait() did not return within %v", waitTime)
		}
		p.mustReceive()
	})
}
