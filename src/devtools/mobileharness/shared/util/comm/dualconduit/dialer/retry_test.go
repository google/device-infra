package dialer

import (
	"context"
	"errors"
	"testing"
	"time"
)

func TestExponentialBackoff(t *testing.T) {
	b := &ExponentialBackoff{
		InitialInterval: 10 * time.Millisecond,
		MaxInterval:     50 * time.Millisecond,
		Multiplier:      2.0,
	}

	tests := []struct {
		attempt  int
		expected time.Duration
	}{
		{0, 10 * time.Millisecond},
		{1, 20 * time.Millisecond},
		{2, 40 * time.Millisecond},
		{3, 50 * time.Millisecond}, // Capped at MaxInterval
		{4, 50 * time.Millisecond}, // Capped at MaxInterval
	}

	for _, tc := range tests {
		got := b.Backoff(tc.attempt)
		if got != tc.expected {
			t.Errorf("Backoff(%d) = %v, want %v", tc.attempt, got, tc.expected)
		}
	}
}

func TestConstantBackoff(t *testing.T) {
	b := &ConstantBackoff{Interval: 10 * time.Millisecond}
	for i := 0; i < 5; i++ {
		got := b.Backoff(i)
		if got != 10*time.Millisecond {
			t.Errorf("Backoff(%d) = %v, want 10ms", i, got)
		}
	}
}

func TestRetry_SuccessFirstTry(t *testing.T) {
	policy := RetryPolicy{
		Strategy:    &ConstantBackoff{Interval: 1 * time.Millisecond},
		MaxAttempts: 3,
	}

	calls := 0
	err := Retry(context.Background(), policy, func(ctx context.Context) error {
		calls++
		return nil
	})

	if err != nil {
		t.Errorf("Retry() got error %v, want no error", err)
	}
	if calls != 1 {
		t.Errorf("Retry() made %d calls, want 1", calls)
	}
}

func TestRetry_SuccessEventually(t *testing.T) {
	policy := RetryPolicy{
		Strategy:    &ConstantBackoff{Interval: 1 * time.Millisecond},
		MaxAttempts: 3,
	}

	calls := 0
	err := Retry(context.Background(), policy, func(ctx context.Context) error {
		calls++
		if calls < 3 {
			return errors.New("temporary error")
		}
		return nil
	})

	if err != nil {
		t.Errorf("Retry() got error %v, want no error", err)
	}
	if calls != 3 {
		t.Errorf("Retry() made %d calls, want 3", calls)
	}
}

func TestRetry_FailMaxAttempts(t *testing.T) {
	policy := RetryPolicy{
		Strategy:    &ConstantBackoff{Interval: 1 * time.Millisecond},
		MaxAttempts: 3,
	}

	calls := 0
	expectedErr := errors.New("persistent error")
	err := Retry(context.Background(), policy, func(ctx context.Context) error {
		calls++
		return expectedErr
	})

	if err == nil {
		t.Error("Retry() got nil error, want non-nil")
	}
	if !errors.Is(err, expectedErr) {
		t.Errorf("Retry() got error %v, want error %v", err, expectedErr)
	}
	if calls != 3 {
		t.Errorf("Retry() made %d calls, want 3", calls)
	}
}

func TestRetry_ContextCanceled(t *testing.T) {
	policy := RetryPolicy{
		Strategy:    &ConstantBackoff{Interval: 100 * time.Millisecond}, // Long backoff
		MaxAttempts: 5,
	}

	ctx, cancel := context.WithCancel(context.Background())

	calls := 0
	go func() {
		// Cancel context after first call
		time.Sleep(10 * time.Millisecond)
		cancel()
	}()

	err := Retry(ctx, policy, func(ctx context.Context) error {
		calls++
		return errors.New("error")
	})

	if !errors.Is(err, context.Canceled) {
		t.Errorf("Retry() got error %v, want %v", err, context.Canceled)
	}
	// It should fail on first attempt, then wait for backoff, but exit early because context is canceled.
	if calls != 1 {
		t.Errorf("Retry() made %d calls, want 1", calls)
	}
}

func TestRetry_ContextTimeout(t *testing.T) {
	policy := RetryPolicy{
		Strategy:    &ConstantBackoff{Interval: 50 * time.Millisecond},
		MaxAttempts: 5,
	}

	// Total timeout 20ms, should timeout during first backoff (50ms)
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Millisecond)
	defer cancel()

	calls := 0
	err := Retry(ctx, policy, func(ctx context.Context) error {
		calls++
		return errors.New("error")
	})

	if !errors.Is(err, context.DeadlineExceeded) {
		t.Errorf("Retry() got error %v, want %v", err, context.DeadlineExceeded)
	}
	if calls != 1 {
		t.Errorf("Retry() made %d calls, want 1", calls)
	}
}
