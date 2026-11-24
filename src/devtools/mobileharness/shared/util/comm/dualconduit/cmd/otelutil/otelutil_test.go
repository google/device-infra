package otelutil

import (
	"context"
	"strings"
	"testing"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/version"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/metric/metricdata"
	"go.opentelemetry.io/otel/sdk/resource"
)

// TestNewMeterProvider_ExportsBuildInfo checks the metric that shows which
// version a binary runs. The MeterProvider of a binary exports it without
// further setup: one series with the value 1, named by the version of the
// binary and the protocol version it speaks.
func TestNewMeterProvider_ExportsBuildInfo(t *testing.T) {
	reader := sdkmetric.NewManualReader()
	provider := newMeterProvider(t.Context(), resource.Default(), reader)
	defer provider.Shutdown(t.Context())

	var got metricdata.ResourceMetrics
	if err := reader.Collect(t.Context(), &got); err != nil {
		t.Fatalf("reader.Collect() got unexpected error: %v", err)
	}
	var points []metricdata.DataPoint[int64]
	for _, sm := range got.ScopeMetrics {
		for _, m := range sm.Metrics {
			if m.Name != "dualconduit.build_info" {
				continue
			}
			gauge, ok := m.Data.(metricdata.Gauge[int64])
			if !ok {
				t.Fatalf("dualconduit.build_info has data of type %T, want metricdata.Gauge[int64]", m.Data)
			}
			points = append(points, gauge.DataPoints...)
		}
	}
	if len(points) != 1 {
		t.Fatalf("dualconduit.build_info has %d data points, want 1", len(points))
	}
	point := points[0]
	if point.Value != 1 {
		t.Errorf("dualconduit.build_info value = %d, want 1", point.Value)
	}
	if v, ok := point.Attributes.Value("version"); !ok || v.AsString() != version.Version {
		t.Errorf("dualconduit.build_info attribute version = %q (present: %t), want %q", v.AsString(), ok, version.Version)
	}
	if v, ok := point.Attributes.Value("protocol"); !ok || v.AsInt64() != int64(version.ProtocolVersion) {
		t.Errorf("dualconduit.build_info attribute protocol = %d (present: %t), want %d", v.AsInt64(), ok, version.ProtocolVersion)
	}
}

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
