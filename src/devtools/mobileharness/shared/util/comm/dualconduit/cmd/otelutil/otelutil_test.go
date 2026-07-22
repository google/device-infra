package otelutil

import (
	"context"
	"strings"
	"testing"
)

func TestInitTelemetry(t *testing.T) {
	ctx := context.Background()
	shutdown, err := InitTelemetry(ctx, "test-service")
	if err != nil {
		t.Fatalf("InitTelemetry failed: %v", err)
	}
	if shutdown == nil {
		t.Fatal("expected non-nil shutdown function")
	}

	if err := shutdown(ctx); err != nil {
		// Ignore connection refused/unavailable/deadline errors which are expected
		// in sandboxed tests where no local OTLP collector is running.
		errStr := err.Error()
		if strings.Contains(errStr, "connection refused") || strings.Contains(errStr, "Unavailable") || strings.Contains(errStr, "context deadline exceeded") {
			t.Logf("Ignored expected connection error during shutdown: %v", err)
		} else {
			t.Errorf("shutdown failed: %v", err)
		}
	}
}
