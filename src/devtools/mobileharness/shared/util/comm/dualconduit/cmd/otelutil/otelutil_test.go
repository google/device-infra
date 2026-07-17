package otelutil

import (
	"context"
	"testing"
)

func TestInitTracerProvider(t *testing.T) {
	ctx := context.Background()
	shutdown, err := InitTracerProvider(ctx, "test-service")
	if err != nil {
		t.Fatalf("InitTracerProvider failed: %v", err)
	}
	if shutdown == nil {
		t.Fatal("expected non-nil shutdown function")
	}

	if err := shutdown(ctx); err != nil {
		t.Errorf("shutdown failed: %v", err)
	}
}
