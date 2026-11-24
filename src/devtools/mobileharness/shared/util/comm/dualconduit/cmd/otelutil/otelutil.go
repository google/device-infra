// Package otelutil provides OpenTelemetry initialization utilities for DualConduit binaries.
package otelutil

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/version"
	"go.opentelemetry.io/otel/exporters/otlp/otlplog/otlploggrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/log/global"
	"go.opentelemetry.io/otel/metric"
	sdklog "go.opentelemetry.io/otel/sdk/log"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
	semconv "go.opentelemetry.io/otel/semconv/v1.26.0"
)

// registerBuildInfo registers the metric dualconduit.build_info on meter. Its
// value is always 1; its attributes name the version of this binary and the
// DualConduit protocol version it speaks. Together with the resource
// attributes of the host it shows which host runs which version.
func registerBuildInfo(meter metric.Meter) error {
	buildInfo, err := meter.Int64ObservableGauge(
		"dualconduit.build_info",
		metric.WithDescription("Always 1. The attributes name the version of the binary and the DualConduit protocol version it speaks"),
		metric.WithUnit("1"),
	)
	if err != nil {
		return err
	}
	attrs := metric.WithAttributes(
		attribute.String("version", version.Version),
		attribute.Int("protocol", version.ProtocolVersion),
	)
	_, err = meter.RegisterCallback(func(_ context.Context, obs metric.Observer) error {
		obs.ObserveInt64(buildInfo, 1, attrs)
		return nil
	}, buildInfo)
	return err
}

// newMeterProvider returns the MeterProvider of a DualConduit binary. Besides
// what the packages register, it exports dualconduit.build_info.
func newMeterProvider(ctx context.Context, res *resource.Resource, reader sdkmetric.Reader) *sdkmetric.MeterProvider {
	mp := sdkmetric.NewMeterProvider(
		sdkmetric.WithResource(res),
		sdkmetric.WithReader(reader),
	)
	if err := registerBuildInfo(mp.Meter("dualconduit")); err != nil {
		slog.ErrorContext(ctx, "Failed to register dualconduit.build_info", "error", err)
	}
	return mp
}

// InitTelemetry initializes OpenTelemetry TracerProvider and MeterProvider.
// It sets the global providers and TextMapPropagator.
// Returns a shutdown function that should be called when the service exits.
func InitTelemetry(ctx context.Context, serviceName string) (func(context.Context) error, error) {
	// 1. Create Resource
	res, err := resource.New(ctx,
		resource.WithAttributes(
			attribute.String(string(semconv.ServiceNameKey), serviceName),
		),
	)
	if err != nil {
		slog.WarnContext(ctx, "Failed to create resource, using default", "error", err)
		res = resource.Default()
	}

	// 2. Initialize Tracer
	traceExporter, err := otlptracegrpc.New(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to create otlptracegrpc exporter: %w", err)
	}
	tp := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(traceExporter),
		sdktrace.WithResource(res),
	)
	otel.SetTracerProvider(tp)

	// 3. Initialize Meter
	metricExporter, err := otlpmetricgrpc.New(ctx)
	if err != nil {
		_ = tp.Shutdown(ctx) // Clean up tracer
		return nil, fmt.Errorf("failed to create otlpmetricgrpc exporter: %w", err)
	}
	mp := newMeterProvider(ctx, res, sdkmetric.NewPeriodicReader(metricExporter))
	otel.SetMeterProvider(mp)

	// 4. Initialize Logger
	logExporter, err := otlploggrpc.New(ctx)
	if err != nil {
		_ = tp.Shutdown(ctx)
		_ = mp.Shutdown(ctx)
		return nil, fmt.Errorf("failed to create otlploggrpc exporter: %w", err)
	}
	lp := sdklog.NewLoggerProvider(
		sdklog.WithProcessor(sdklog.NewBatchProcessor(logExporter)),
		sdklog.WithResource(res),
	)
	global.SetLoggerProvider(lp)

	// 5. Set Propagator
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{},
		propagation.Baggage{},
	))

	slog.InfoContext(ctx, "OpenTelemetry Telemetry initialized successfully", "service_name", serviceName)

	return func(shutdownCtx context.Context) error {
		var shutdownErr error
		if err := tp.Shutdown(shutdownCtx); err != nil {
			shutdownErr = fmt.Errorf("failed to shutdown TracerProvider: %w", err)
		}
		if err := mp.Shutdown(shutdownCtx); err != nil {
			if shutdownErr != nil {
				shutdownErr = fmt.Errorf("%v; failed to shutdown MeterProvider: %w", shutdownErr, err)
			} else {
				shutdownErr = fmt.Errorf("failed to shutdown MeterProvider: %w", err)
			}
		}
		if err := lp.Shutdown(shutdownCtx); err != nil {
			if shutdownErr != nil {
				shutdownErr = fmt.Errorf("%v; failed to shutdown LoggerProvider: %w", shutdownErr, err)
			} else {
				shutdownErr = fmt.Errorf("failed to shutdown LoggerProvider: %w", err)
			}
		}
		return shutdownErr
	}, nil
}
