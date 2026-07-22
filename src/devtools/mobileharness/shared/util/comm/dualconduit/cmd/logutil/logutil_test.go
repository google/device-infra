package logutil

import (
	"bytes"
	"context"
	"encoding/json"
	"go.opentelemetry.io/contrib/bridges/otelslog"
	"log/slog"
	"testing"
	"time"
)

func TestFlatteningHandler(t *testing.T) {
	var buf bytes.Buffer

	opts := &slog.HandlerOptions{
		ReplaceAttr: replaceAttr,
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

	// Check timestamp format
	tsStr, ok := data["timestamp"].(string)
	if !ok {
		t.Fatalf("timestamp key missing or not a string in JSON: %s", buf.String())
	}
	if _, err := time.Parse(TimestampLayout, tsStr); err != nil {
		t.Errorf("timestamp %q does not match expected layout %q: %v", tsStr, TimestampLayout, err)
	}
}

func TestReplaceAttr(t *testing.T) {
	testTime := time.Date(2026, 7, 8, 12, 34, 56, 789000000, time.FixedZone("TEST", -7*3600))
	attr := slog.Attr{
		Key:   slog.TimeKey,
		Value: slog.TimeValue(testTime),
	}

	replaced := replaceAttr(nil, attr)
	if replaced.Key != "timestamp" {
		t.Errorf("replaceAttr key = %q, want %q", replaced.Key, "timestamp")
	}
	wantVal := "2026-07-08T12:34:56.789-0700"
	if replaced.Value.String() != wantVal {
		t.Errorf("replaceAttr value = %q, want %q", replaced.Value.String(), wantVal)
	}
}

type panickingHandler struct{}

func (p *panickingHandler) Enabled(context.Context, slog.Level) bool { return true }
func (p *panickingHandler) Handle(context.Context, slog.Record) error {
	panic("simulated otel exporter crash")
}
func (p *panickingHandler) WithAttrs([]slog.Attr) slog.Handler { return p }
func (p *panickingHandler) WithGroup(string) slog.Handler      { return p }

func TestMultiHandlerWithBrokenOTelExporter(t *testing.T) {
	var buf bytes.Buffer
	jsonHandler := slog.NewJSONHandler(&buf, &slog.HandlerOptions{ReplaceAttr: replaceAttr})
	fileHandler := &flatteningHandler{Handler: jsonHandler}

	mh := &multiHandler{
		fileHandler: fileHandler,
		otelHandler: &panickingHandler{},
	}

	logger := slog.New(mh)

	// Logging should not panic, and file log should succeed
	logger.InfoContext(context.Background(), "test message", "key", "value")

	var data map[string]any
	if err := json.Unmarshal(buf.Bytes(), &data); err != nil {
		t.Fatalf("Failed to unmarshal JSON from file handler: %v", err)
	}

	msg, ok := data["message"].(string)
	if !ok || msg != "test message key=value" {
		t.Errorf("fileHandler message = %q, want %q", msg, "test message key=value")
	}
}

func TestMultiHandlerWithUnreachableSidecar(t *testing.T) {
	var buf bytes.Buffer
	jsonHandler := slog.NewJSONHandler(&buf, &slog.HandlerOptions{ReplaceAttr: replaceAttr})
	fileHandler := &flatteningHandler{Handler: jsonHandler}

	otelHandler := otelslog.NewHandler("test-service")

	mh := &multiHandler{
		fileHandler: fileHandler,
		otelHandler: otelHandler,
	}

	logger := slog.New(mh)

	// Emit multiple log records; should be fast and non-blocking
	start := time.Now()
	for i := 0; i < 100; i++ {
		logger.InfoContext(context.Background(), "log line", "index", i)
	}
	elapsed := time.Since(start)

	if elapsed > 1*time.Second {
		t.Errorf("Logging to unreachable sidecar took too long: %v", elapsed)
	}

	if buf.Len() == 0 {
		t.Errorf("File log buffer is empty, expected local logs")
	}
}
