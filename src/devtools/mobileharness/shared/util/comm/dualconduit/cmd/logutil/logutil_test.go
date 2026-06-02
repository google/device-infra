package logutil

import (
	"bytes"
	"context"
	"encoding/json"
	"log/slog"
	"testing"
)

func TestFlatteningHandler(t *testing.T) {
	var buf bytes.Buffer

	opts := &slog.HandlerOptions{
		ReplaceAttr: func(groups []string, a slog.Attr) slog.Attr {
			if a.Key == slog.MessageKey {
				return slog.Attr{
					Key:   "message",
					Value: a.Value,
				}
			}
			return a
		},
	}

	jsonHandler := slog.NewJSONHandler(&buf, opts)
	handler := &flatteningHandler{Handler: jsonHandler}
	logger := slog.New(handler)

	logger.InfoContext(context.Background(), "hello", "a", 1, "b", "two")

	var data map[string]any
	if err := json.Unmarshal(buf.Bytes(), &data); err != nil {
		t.Fatalf("Failed to unmarshal JSON: %v", err)
	}

	// Check message
	msg, ok := data["message"].(string)
	if !ok {
		t.Fatalf("message key missing or not a string in JSON: %s", buf.String())
	}

	wantMsg := "hello a=1 b=two"
	if msg != wantMsg {
		t.Errorf("flatteningHandler.Handle(ctx, Record{Message: \"hello\", Attrs: [a=1, b=two]}) message = %q, want %q", msg, wantMsg)
	}

	// Check that structured fields are NOT present
	if _, exists := data["a"]; exists {
		t.Errorf("flatteningHandler.Handle(ctx, Record{Message: \"hello\", Attrs: [a=1, b=two]}) has key %q = true, want false", "a")
	}
	if _, exists := data["b"]; exists {
		t.Errorf("flatteningHandler.Handle(ctx, Record{Message: \"hello\", Attrs: [a=1, b=two]}) has key %q = true, want false", "b")
	}
}
