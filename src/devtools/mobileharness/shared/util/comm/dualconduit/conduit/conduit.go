// Package conduit provides the control and data planes for Conduit.
package conduit

import (
	"context"
	"log"
	"sync"

	"github.com/rsocket/rsocket-go"

	dconpb "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/proto/dconpb"
)

// Conduit represents a managed multiplexed tunnel. It bridges the IO pump (Go context)
// and the RSocket connection. Closing either the IO pump or the RSocket connection
// should close the entire Conduit.
type Conduit struct {
	ID          string
	metadata    *dconpb.EstablishConduitRequest
	rsocket     rsocket.CloseableRSocket
	pumpCtx     context.Context    // Represents the active IO pump lifecycle. While Go style generally discourages storing context in structs, Conduit is a connection object where the context models its lifetime. External streams bind to this to know when the tunnel drops.
	cancel      context.CancelFunc // Cancels the IO pump lifecycle.
	closeOnce   sync.Once
	onRemove    func() // Callback to tell the Manager to drop this Conduit
	beforeClose func() // Callback to handle signaling before close
	// Tracks active logical connections in this conduit. For each accepted TCP
	// connection or incoming RSocket channel, one connection is added.
	activeConnections sync.WaitGroup
}

// Context exposes the Conduit's lifecycle so streams can bind to it.
func (c *Conduit) Context() context.Context {
	return c.pumpCtx
}

// New creates the tunnel and sets up the bidirectional lifecycle bridge.
func New(ctx context.Context, id string, meta *dconpb.EstablishConduitRequest, rs rsocket.CloseableRSocket, onRemove func(), beforeClose func()) *Conduit {
	pumpCtx, cancel := context.WithCancel(ctx)
	c := &Conduit{
		ID:          id,
		metadata:    meta,
		rsocket:     rs,
		pumpCtx:     pumpCtx,
		cancel:      cancel,
		onRemove:    onRemove,
		beforeClose: beforeClose,
	}

	// 1. Context -> RSocket: If the IO pump context is canceled (e.g., when the parent
	// context is canceled or someone calls cancel), we must ensure the RSocket is closed.
	// This background watcher is necessary to bridge context cancellation to network closure.
	go func() {
		<-pumpCtx.Done()
		c.Close()
	}()

	if rs != nil {
		// 2. RSocket -> Context: If the RSocket connection is closed by the peer or network,
		// we must cancel the IO pump context in a goroutine to prevent deadlocks during synchronous Close().
		rs.OnClose(func(err error) {
			log.Printf("Conduit %q underlying socket closed: %v", id, err)
			go c.Close()
		})
	}

	return c
}

// Close deterministically tears down the Conduit, no matter who triggers it.
// sync.Once guarantees this teardown logic only ever runs exactly one time.
func (c *Conduit) Close() error {
	c.closeOnce.Do(func() {
		// 1. Call beforeClose callback
		if c.beforeClose != nil {
			c.beforeClose()
		}
		// 2. Cancel the Go context (notifies all IO pumps to stop)
		c.cancel()

		// Wait for all registered stream goroutines to finish.
		c.activeConnections.Wait()

		// 3. Close the physical transport synchronously
		if c.rsocket != nil {
			if err := c.rsocket.Close(); err != nil {
				log.Printf("Conduit %q failed to close underlying RSocket: %v", c.ID, err)
			}
		}
		// 4. Notify the Manager to remove this Conduit from its map
		if c.onRemove != nil {
			c.onRemove()
		}
	})
	return nil
}
