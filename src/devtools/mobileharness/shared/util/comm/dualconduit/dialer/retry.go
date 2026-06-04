package dialer

import (
	"context"
	"fmt"
	"log/slog"
	"math"
	"time"
)

// RetryStrategy defines the interface for connection retry backoff.
type RetryStrategy interface {
	// Backoff returns the duration to wait before the next attempt.
	// attempt is 0-indexed (0 for the first retry).
	Backoff(attempt int) time.Duration
}

// ExponentialBackoff increases the delay exponentially between retries.
type ExponentialBackoff struct {
	InitialInterval time.Duration
	MaxInterval     time.Duration
	Multiplier      float64
}

// Backoff calculates the exponential delay.
func (b *ExponentialBackoff) Backoff(attempt int) time.Duration {
	if attempt == 0 {
		return b.InitialInterval
	}
	fDelay := float64(b.InitialInterval) * math.Pow(b.Multiplier, float64(attempt))
	if fDelay >= float64(b.MaxInterval) {
		return b.MaxInterval
	}
	return time.Duration(fDelay)
}

// ConstantBackoff waits a fixed amount of time between retries.
type ConstantBackoff struct {
	Interval time.Duration
}

// Backoff returns the constant delay.
func (b *ConstantBackoff) Backoff(attempt int) time.Duration {
	return b.Interval
}

// RetryPolicy defines when and how to retry.
type RetryPolicy struct {
	Strategy    RetryStrategy
	MaxAttempts int // 0 for infinite retries (restricted only by context)
}

// Retry executes the operation 'op' and retries it according to the 'policy'.
// It respects both MaxAttempts in the policy and Context cancellation/timeout.
func Retry(ctx context.Context, policy RetryPolicy, op func(ctx context.Context) error) error {
	var lastErr error
	attempts := 0
	for {
		err := op(ctx)
		if err == nil {
			return nil
		}
		lastErr = err
		attempts++

		// 1. Cutoff by Max Attempts
		if policy.MaxAttempts > 0 && attempts >= policy.MaxAttempts {
			break
		}

		backoff := policy.Strategy.Backoff(attempts - 1)
		slog.Info("Operation failed, retrying", "attempt", attempts, "backoff", backoff, "error", err)

		// 2. Cutoff by Total Timeout (via ctx.Done())
		select {
		case <-ctx.Done():
			return ctx.Err() // Exits immediately if context timeout/deadline is reached
		case <-time.After(backoff):
		}
	}
	return fmt.Errorf("failed after %d attempts: %w", attempts, lastErr)
}
