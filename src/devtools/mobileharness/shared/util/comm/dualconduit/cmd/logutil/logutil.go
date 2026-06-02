// Package logutil provides shared logging utilities for DualConduit command line applications.
package logutil

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"os"

	"gopkg.in/natefinch/lumberjack.v2"
)

type flatteningHandler struct {
	slog.Handler
}

// Handle flattens all structured attributes into the message string
// and clears them from the record to ensure they are not printed as separate JSON fields.
func (h *flatteningHandler) Handle(ctx context.Context, r slog.Record) error {
	var attrs string
	r.Attrs(func(a slog.Attr) bool {
		attrs += fmt.Sprintf(" %s=%v", a.Key, a.Value.Any())
		return true
	})

	newMsg := r.Message
	if attrs != "" {
		newMsg += attrs
	}

	newRecord := slog.NewRecord(r.Time, r.Level, newMsg, r.PC)
	return h.Handler.Handle(ctx, newRecord)
}

// Setup configures the default slog logger to write to both os.Stderr
// and a rotating file at the specified path.
func Setup(logPath string) {
	rotator := &lumberjack.Logger{
		Filename:   logPath,
		MaxSize:    10, // megabytes
		MaxBackups: 3,
		MaxAge:     28, // days
		Compress:   true,
	}

	mw := io.MultiWriter(os.Stderr, rotator)

	opts := &slog.HandlerOptions{
		ReplaceAttr: func(groups []string, a slog.Attr) slog.Attr {
			switch a.Key {
			case slog.LevelKey:
				if level, ok := a.Value.Any().(slog.Level); ok {
					levelStr := level.String()
					if level == slog.LevelWarn {
						levelStr = "WARNING"
					}
					return slog.Attr{
						Key:   "severity",
						Value: slog.StringValue(levelStr),
					}
				}
				return slog.Attr{
					Key:   "severity",
					Value: a.Value,
				}
			case slog.MessageKey:
				return slog.Attr{
					Key:   "message",
					Value: a.Value,
				}
			case slog.TimeKey:
				return slog.Attr{
					Key:   "timestamp",
					Value: a.Value,
				}
			default:
				return a
			}
		},
	}

	jsonHandler := slog.NewJSONHandler(mw, opts)
	handler := &flatteningHandler{Handler: jsonHandler}
	logger := slog.New(handler)
	slog.SetDefault(logger)
}
