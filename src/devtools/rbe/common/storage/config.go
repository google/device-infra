package storage

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"
)

// Canonical command line flag names for the evictor settings.
//
// These exist so that every binary sharing this cache presents operators with
// the same vocabulary. A flag that means "evict below this much free space"
// should be spelled the same way whether it is passed to a long-running proxy
// or to a one-shot downloader, because the person tuning a host under disk
// pressure should not have to remember which tool they are talking to.
const (
	FlagMinFreeSpace      = "min-free-space"
	FlagTargetFreeSpace   = "target-free-space"
	FlagReservedSpaceGB   = "reserved-space-gb"
	FlagCheckInterval     = "eviction-check-interval"
	FlagSampleBuckets     = "eviction-sample-buckets"
	FlagBatchSize         = "eviction-batch-size"
	FlagBatchDelay        = "eviction-batch-delay"
	FlagLazyTouchInterval = "lazy-touch-interval"
	FlagMinBlobAge        = "min-blob-age"
)

// ConfigFields records which evictor settings the caller set explicitly,
// as opposed to having received a default.
//
// This distinction cannot be recovered from the values themselves: a flag left
// at its default is indistinguishable from one a user deliberately set to the
// same value, and treating the two alike would let a stale configuration file
// silently override an explicit command line argument.
type ConfigFields struct {
	MinFreeSpace      bool
	TargetFreeSpace   bool
	ReservedSpaceGB   bool
	CheckInterval     bool
	SampleBuckets     bool
	BatchSize         bool
	BatchDelay        bool
	LazyTouchInterval bool
	MinBlobAge        bool
}

// ConfigFieldsFromFlagNames converts a set of flag names, such as the one a
// caller builds from flag.Visit, into a ConfigFields using the canonical names
// above. Names it does not recognize are ignored, so callers can pass the
// whole set of flags they saw.
func ConfigFieldsFromFlagNames(explicit map[string]bool) ConfigFields {
	return ConfigFields{
		MinFreeSpace:      explicit[FlagMinFreeSpace],
		TargetFreeSpace:   explicit[FlagTargetFreeSpace],
		ReservedSpaceGB:   explicit[FlagReservedSpaceGB],
		CheckInterval:     explicit[FlagCheckInterval],
		SampleBuckets:     explicit[FlagSampleBuckets],
		BatchSize:         explicit[FlagBatchSize],
		BatchDelay:        explicit[FlagBatchDelay],
		LazyTouchInterval: explicit[FlagLazyTouchInterval],
		MinBlobAge:        explicit[FlagMinBlobAge],
	}
}

// LoadConfigFile applies the settings in the JSON file at path on top of cfg.
//
// Only the keys present in the file are touched, so a file may configure a
// single setting without having to restate the rest. Parsing stops at the
// first malformed duration and returns an error; cfg may have been partially
// updated at that point, and the caller is expected to discard it or fall back
// to its defaults.
func LoadConfigFile(path string, cfg *EvictorConfig) error {
	data, err := os.ReadFile(path)
	if err != nil {
		return err
	}

	var updateMap map[string]any
	if err := json.Unmarshal(data, &updateMap); err != nil {
		return fmt.Errorf("failed to parse JSON: %w", err)
	}

	if val, ok := updateMap["min_free_space"].(string); ok && val != "" {
		cfg.MinFreeSpace = val
	}
	if val, ok := updateMap["target_free_space"].(string); ok && val != "" {
		cfg.TargetFreeSpace = val
	}
	if val, ok := updateMap["reserved_space_gb"].(float64); ok {
		cfg.ReservedSpaceGB = int64(val)
	}
	if val, ok := updateMap["eviction_check_interval"].(string); ok && val != "" {
		d, err := time.ParseDuration(val)
		if err != nil {
			return fmt.Errorf("invalid eviction_check_interval: %w", err)
		}
		cfg.CheckInterval = d
	}
	if val, ok := updateMap["sample_buckets"].(float64); ok {
		cfg.SampleBuckets = int(val)
	}
	if val, ok := updateMap["batch_size"].(float64); ok {
		cfg.BatchSize = int(val)
	}
	if val, ok := updateMap["batch_delay"].(string); ok && val != "" {
		d, err := time.ParseDuration(val)
		if err != nil {
			return fmt.Errorf("invalid batch_delay: %w", err)
		}
		cfg.BatchDelay = d
	}
	if val, ok := updateMap["lazy_touch_interval"].(string); ok && val != "" {
		d, err := time.ParseDuration(val)
		if err != nil {
			return fmt.Errorf("invalid lazy_touch_interval: %w", err)
		}
		cfg.LazyTouchInterval = d
	}
	if val, ok := updateMap["min_blob_age"].(string); ok && val != "" {
		d, err := time.ParseDuration(val)
		if err != nil {
			return fmt.Errorf("invalid min_blob_age: %w", err)
		}
		cfg.MinBlobAge = d
	}
	return nil
}

// ResolveConfig combines flag values with a configuration file using the
// conventional command line precedence:
//
//	explicit flags > configuration file > flag defaults
//
// base holds the caller's flag values, defaults included. explicit says which
// of those the user actually passed. configFilePath may be empty, in which
// case base is returned unchanged.
//
// A failure to read or parse the configuration file is returned alongside a
// usable configuration rather than instead of one: the returned config is base,
// with nothing from the rejected file applied to it. Refusing to start because
// a tuning file is malformed would be a poor trade for a cache: callers are
// expected to log the error and carry on with their flag values.
func ResolveConfig(base EvictorConfig, explicit ConfigFields, configFilePath string) (EvictorConfig, error) {
	cfg := base

	var loadErr error
	if configFilePath != "" {
		if err := LoadConfigFile(configFilePath, &cfg); err != nil {
			loadErr = err
			// LoadConfigFile applies settings as it parses them, so a failure
			// partway through leaves cfg holding whatever it managed to apply
			// before giving up. Those values come from a file we have just
			// judged malformed, and the explicit-flag pass below only restores
			// the fields the user actually typed, so the rest would silently
			// survive. Start over from the flag values instead.
			cfg = base
		}
	}

	if explicit.MinFreeSpace {
		cfg.MinFreeSpace = base.MinFreeSpace
	}
	if explicit.TargetFreeSpace {
		cfg.TargetFreeSpace = base.TargetFreeSpace
	}
	if explicit.ReservedSpaceGB {
		cfg.ReservedSpaceGB = base.ReservedSpaceGB
	}
	if explicit.CheckInterval {
		cfg.CheckInterval = base.CheckInterval
	}
	if explicit.SampleBuckets {
		cfg.SampleBuckets = base.SampleBuckets
	}
	if explicit.BatchSize {
		cfg.BatchSize = base.BatchSize
	}
	if explicit.BatchDelay {
		cfg.BatchDelay = base.BatchDelay
	}
	if explicit.LazyTouchInterval {
		cfg.LazyTouchInterval = base.LazyTouchInterval
	}
	if explicit.MinBlobAge {
		cfg.MinBlobAge = base.MinBlobAge
	}

	return cfg, loadErr
}

// ConfigFileName is the conventional name of the evictor configuration file
// kept alongside the cache it configures.
const ConfigFileName = "config.json"

// ResolveConfigFilePath determines which configuration file to use.
//
// An empty flag means "the one that belongs to this cache", which keeps the
// configuration with the data it describes rather than at some absolute path
// that will not follow the cache when it moves. The sentinel values "none" and
// "/dev/null" return the empty string, disabling configuration file support
// entirely for callers that want their flags to be the only source of truth.
func ResolveConfigFilePath(configFileFlag, cacheDir string) string {
	if configFileFlag == "none" || configFileFlag == "/dev/null" {
		return ""
	}
	if configFileFlag != "" {
		return configFileFlag
	}
	return filepath.Join(cacheDir, ConfigFileName)
}
