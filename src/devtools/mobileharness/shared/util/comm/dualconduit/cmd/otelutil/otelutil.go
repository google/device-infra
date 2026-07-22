// Package otelutil provides OpenTelemetry initialization utilities for DualConduit binaries.
package otelutil

import (
	"context"
	"fmt"
	"log/slog"

	"go.opentelemetry.io/otel/exporters/otlp/otlplog/otlploggrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/log/global"
	sdklog "go.opentelemetry.io/otel/sdk/log"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
	semconv "go.opentelemetry.io/otel/semconv/v1.26.0"
)

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
	mp := sdkmetric.NewMeterProvider(
		sdkmetric.WithResource(res),
		sdkmetric.WithReader(sdkmetric.NewPeriodicReader(metricExporter)),
	)
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
