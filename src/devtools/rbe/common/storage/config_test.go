package storage

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"testing"
	"time"
)

// fullConfigJSON sets every field the loader understands, to distinguish
// "parsed correctly" from "left at its default".
const fullConfigJSON = `{
	"min_free_space": "20%",
	"target_free_space": "30%",
	"reserved_space_gb": 120,
	"eviction_check_interval": "45s",
	"sample_buckets": 32,
	"batch_size": 800,
	"batch_delay": "1500us",
	"lazy_touch_interval": "2h",
	"min_blob_age": "15m"
}`

func writeConfigFile(t *testing.T, dir, name, content string) string {
	t.Helper()
	path := filepath.Join(dir, name)
	if err := os.WriteFile(path, []byte(content), 0644); err != nil {
		t.Fatalf("Failed to write %s: %v", path, err)
	}
	return path
}

func TestLoadConfigFile(t *testing.T) {
	path := writeConfigFile(t, t.TempDir(), "config.json", fullConfigJSON)

	cfg := DefaultEvictorConfig()
	if err := LoadConfigFile(path, &cfg); err != nil {
		t.Fatalf("LoadConfigFile failed: %v", err)
	}

	if cfg.MinFreeSpace != "20%" {
		t.Errorf("MinFreeSpace = %s, want 20%%", cfg.MinFreeSpace)
	}
	if cfg.TargetFreeSpace != "30%" {
		t.Errorf("TargetFreeSpace = %s, want 30%%", cfg.TargetFreeSpace)
	}
	if cfg.ReservedSpaceGB != 120 {
		t.Errorf("ReservedSpaceGB = %d, want 120", cfg.ReservedSpaceGB)
	}
	if cfg.CheckInterval != 45*time.Second {
		t.Errorf("CheckInterval = %v, want 45s", cfg.CheckInterval)
	}
	if cfg.SampleBuckets != 32 {
		t.Errorf("SampleBuckets = %d, want 32", cfg.SampleBuckets)
	}
	if cfg.BatchSize != 800 {
		t.Errorf("BatchSize = %d, want 800", cfg.BatchSize)
	}
	if cfg.BatchDelay != 1500*time.Microsecond {
		t.Errorf("BatchDelay = %v, want 1500us", cfg.BatchDelay)
	}
	if cfg.LazyTouchInterval != 2*time.Hour {
		t.Errorf("LazyTouchInterval = %v, want 2h", cfg.LazyTouchInterval)
	}
	if cfg.MinBlobAge != 15*time.Minute {
		t.Errorf("MinBlobAge = %v, want 15m", cfg.MinBlobAge)
	}
}

// TestLoadConfigFile_PartialFileLeavesTheRestAlone is the property that lets an
// operator drop in a one-line file to tune a single setting without having to
// restate, and keep up to date, every other value.
func TestLoadConfigFile_PartialFileLeavesTheRestAlone(t *testing.T) {
	path := writeConfigFile(t, t.TempDir(), "partial.json", `{"min_free_space": "12%"}`)

	cfg := DefaultEvictorConfig()
	want := DefaultEvictorConfig()
	if err := LoadConfigFile(path, &cfg); err != nil {
		t.Fatalf("LoadConfigFile failed: %v", err)
	}

	if cfg.MinFreeSpace != "12%" {
		t.Errorf("MinFreeSpace = %s, want 12%%", cfg.MinFreeSpace)
	}
	if cfg.TargetFreeSpace != want.TargetFreeSpace {
		t.Errorf("TargetFreeSpace = %s, want the default %s", cfg.TargetFreeSpace, want.TargetFreeSpace)
	}
	if cfg.ReservedSpaceGB != want.ReservedSpaceGB {
		t.Errorf("ReservedSpaceGB = %d, want the default %d", cfg.ReservedSpaceGB, want.ReservedSpaceGB)
	}
	if cfg.MinBlobAge != want.MinBlobAge {
		t.Errorf("MinBlobAge = %v, want the default %v", cfg.MinBlobAge, want.MinBlobAge)
	}
}

// TestLoadConfigFile_EmptyStringsAreIgnored guards a subtle case: a JSON field
// present but blank must not blank out a real setting, because an empty
// threshold would fail to parse and take the evictor down with it.
func TestLoadConfigFile_EmptyStringsAreIgnored(t *testing.T) {
	path := writeConfigFile(t, t.TempDir(), "blank.json", `{"min_free_space": "", "min_blob_age": ""}`)

	cfg := DefaultEvictorConfig()
	want := DefaultEvictorConfig()
	if err := LoadConfigFile(path, &cfg); err != nil {
		t.Fatalf("LoadConfigFile failed: %v", err)
	}

	if cfg.MinFreeSpace != want.MinFreeSpace {
		t.Errorf("MinFreeSpace = %q, want the default %q", cfg.MinFreeSpace, want.MinFreeSpace)
	}
	if cfg.MinBlobAge != want.MinBlobAge {
		t.Errorf("MinBlobAge = %v, want the default %v", cfg.MinBlobAge, want.MinBlobAge)
	}
}

func TestLoadConfigFile_Errors(t *testing.T) {
	dir := t.TempDir()
	cfg := DefaultEvictorConfig()

	if err := LoadConfigFile(filepath.Join(dir, "missing.json"), &cfg); err == nil {
		t.Errorf("Expected an error for a nonexistent file, got nil")
	}

	badPath := writeConfigFile(t, dir, "bad.json", "{bad")
	if err := LoadConfigFile(badPath, &cfg); err == nil {
		t.Errorf("Expected an error for malformed JSON, got nil")
	}

	for _, field := range []string{"eviction_check_interval", "batch_delay", "lazy_touch_interval", "min_blob_age"} {
		path := writeConfigFile(t, dir, field+".json", fmt.Sprintf(`{%q: "invalid"}`, field))
		if err := LoadConfigFile(path, &cfg); err == nil {
			t.Errorf("Expected an error for an unparseable %s, got nil", field)
		}
	}
}

func TestResolveConfig_NoConfigFileKeepsFlagValues(t *testing.T) {
	base := DefaultEvictorConfig()
	base.MinFreeSpace = "17%"

	got, _, err := ResolveConfig(base, ConfigFields{}, "", nil)
	if err != nil {
		t.Fatalf("ResolveConfig failed: %v", err)
	}
	if got != base {
		t.Errorf("ResolveConfig without a config file changed the configuration: got %+v, want %+v", got, base)
	}
}

func TestResolveConfig_ConfigFileBeatsFlagDefaults(t *testing.T) {
	path := writeConfigFile(t, t.TempDir(), "config.json", fullConfigJSON)

	got, _, err := ResolveConfig(DefaultEvictorConfig(), ConfigFields{}, path, nil)
	if err != nil {
		t.Fatalf("ResolveConfig failed: %v", err)
	}

	if got.MinFreeSpace != "20%" {
		t.Errorf("MinFreeSpace = %s, want 20%% from the config file", got.MinFreeSpace)
	}
	if got.ReservedSpaceGB != 120 {
		t.Errorf("ReservedSpaceGB = %d, want 120 from the config file", got.ReservedSpaceGB)
	}
	if got.CheckInterval != 45*time.Second {
		t.Errorf("CheckInterval = %v, want 45s from the config file", got.CheckInterval)
	}
}

// TestResolveConfig_ExplicitFlagsBeatConfigFile is the whole reason
// ConfigFields exists. A value that equals its own default must still win when
// the user typed it, which no amount of comparing values could tell us.
func TestResolveConfig_ExplicitFlagsBeatConfigFile(t *testing.T) {
	path := writeConfigFile(t, t.TempDir(), "config.json", fullConfigJSON)

	base := DefaultEvictorConfig()
	base.MinFreeSpace = "10%"
	base.LazyTouchInterval = 30 * time.Minute

	got, _, err := ResolveConfig(base, ConfigFields{MinFreeSpace: true, LazyTouchInterval: true}, path, nil)
	if err != nil {
		t.Fatalf("ResolveConfig failed: %v", err)
	}

	if got.MinFreeSpace != "10%" {
		t.Errorf("MinFreeSpace = %s, want 10%% from the explicit flag", got.MinFreeSpace)
	}
	if got.LazyTouchInterval != 30*time.Minute {
		t.Errorf("LazyTouchInterval = %v, want 30m from the explicit flag", got.LazyTouchInterval)
	}
	// Settings the user did not mention still come from the file.
	if got.TargetFreeSpace != "30%" {
		t.Errorf("TargetFreeSpace = %s, want 30%% from the config file", got.TargetFreeSpace)
	}
	if got.ReservedSpaceGB != 120 {
		t.Errorf("ReservedSpaceGB = %d, want 120 from the config file", got.ReservedSpaceGB)
	}
}

func TestResolveConfig_ExplicitFlagEqualToItsDefaultStillWins(t *testing.T) {
	path := writeConfigFile(t, t.TempDir(), "config.json", `{"min_free_space": "20%"}`)

	base := DefaultEvictorConfig() // MinFreeSpace is "15%", the default.
	got, _, err := ResolveConfig(base, ConfigFields{MinFreeSpace: true}, path, nil)
	if err != nil {
		t.Fatalf("ResolveConfig failed: %v", err)
	}
	if got.MinFreeSpace != base.MinFreeSpace {
		t.Errorf("MinFreeSpace = %s, want %s: typing a flag must win even when the value matches the default",
			got.MinFreeSpace, base.MinFreeSpace)
	}
}

// TestResolveConfig_ReturnsAUsableConfigOnFileError pins the decision not to
// let a malformed tuning file stop a cache from running.
func TestResolveConfig_ReturnsAUsableConfigOnFileError(t *testing.T) {
	base := DefaultEvictorConfig()

	got, _, err := ResolveConfig(base, ConfigFields{}, filepath.Join(t.TempDir(), "missing.json"), nil)
	if err == nil {
		t.Errorf("Expected the file error to be reported, got nil")
	}
	if got != base {
		t.Errorf("Expected the flag values to survive a config file error: got %+v, want %+v", got, base)
	}
}

// TestResolveConfig_DiscardsPartiallyAppliedConfigOnError covers the harder
// half of the same promise. A file that is well-formed right up to the point
// where it isn't gets several settings applied before the loader gives up, and
// those settings must not leak into the configuration the caller goes on to
// use: a file we have rejected should have no influence at all.
func TestResolveConfig_DiscardsPartiallyAppliedConfigOnError(t *testing.T) {
	// min_free_space and reserved_space_gb parse and are applied; the loader
	// then fails on the duration and returns.
	path := writeConfigFile(t, t.TempDir(), "truncated.json", `{
		"min_free_space": "20%",
		"reserved_space_gb": 120,
		"eviction_check_interval": "not-a-duration"
	}`)

	base := DefaultEvictorConfig()

	got, _, err := ResolveConfig(base, ConfigFields{}, path, nil)
	if err == nil {
		t.Errorf("Expected the parse error to be reported, got nil")
	}
	if got != base {
		t.Errorf("Settings from the rejected file leaked into the result: got %+v, want %+v", got, base)
	}
}

// TestResolveConfig_EveryExplicitFlagBeatsTheConfigFile walks every setting
// rather than a representative few, because the precedence rule is nine
// near-identical assignments and the failure mode to guard against is one of
// them naming the wrong field. Comparing whole structs catches that: a
// misdirected assignment shows up as the sentinel appearing in the wrong place.
func TestResolveConfig_EveryExplicitFlagBeatsTheConfigFile(t *testing.T) {
	path := writeConfigFile(t, t.TempDir(), "config.json", fullConfigJSON)

	// What the caller gets when it defers to the file on everything.
	fileOnly, _, err := ResolveConfig(DefaultEvictorConfig(), ConfigFields{}, path, nil)
	if err != nil {
		t.Fatalf("ResolveConfig failed: %v", err)
	}

	// The sentinels differ from both the defaults and the values in
	// fullConfigJSON, so "the flag won" is distinguishable from either.
	tests := []struct {
		name     string
		explicit ConfigFields
		set      func(*EvictorConfig)
	}{
		{FlagMinFreeSpace, ConfigFields{MinFreeSpace: true}, func(c *EvictorConfig) { c.MinFreeSpace = "7%" }},
		{FlagTargetFreeSpace, ConfigFields{TargetFreeSpace: true}, func(c *EvictorConfig) { c.TargetFreeSpace = "77%" }},
		{FlagReservedSpaceGB, ConfigFields{ReservedSpaceGB: true}, func(c *EvictorConfig) { c.ReservedSpaceGB = 7 }},
		{FlagCheckInterval, ConfigFields{CheckInterval: true}, func(c *EvictorConfig) { c.CheckInterval = 7 * time.Second }},
		{FlagSampleBuckets, ConfigFields{SampleBuckets: true}, func(c *EvictorConfig) { c.SampleBuckets = 7 }},
		{FlagBatchSize, ConfigFields{BatchSize: true}, func(c *EvictorConfig) { c.BatchSize = 7 }},
		{FlagBatchDelay, ConfigFields{BatchDelay: true}, func(c *EvictorConfig) { c.BatchDelay = 7 * time.Millisecond }},
		{FlagLazyTouchInterval, ConfigFields{LazyTouchInterval: true}, func(c *EvictorConfig) { c.LazyTouchInterval = 7 * time.Hour }},
		{FlagMinBlobAge, ConfigFields{MinBlobAge: true}, func(c *EvictorConfig) { c.MinBlobAge = 7 * time.Minute }},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			base := DefaultEvictorConfig()
			tc.set(&base)

			// Exactly one setting should move away from the all-from-file
			// result, and it should be the one the user typed.
			want := fileOnly
			tc.set(&want)

			got, _, err := ResolveConfig(base, tc.explicit, path, nil)
			if err != nil {
				t.Fatalf("ResolveConfig failed: %v", err)
			}
			if got != want {
				t.Errorf("ResolveConfig with an explicit -%s = %+v, want %+v", tc.name, got, want)
			}
		})
	}
}

// flexibleCheck checks watermarks against FlexibleWatermarkBounds, as casproxy
// does. The test configurations use percentages only, so it measures nothing.
func flexibleCheck(cfg EvictorConfig) error {
	_, err := CheckWatermarks(cfg, "", nil, FlexibleWatermarkBounds)
	return err
}

// TestResolveConfig_DropsOnlyTheBadWatermarks is what keeps casproxy from
// crash-looping on a saved file with bad watermarks without losing the rest
// of the file: each watermark dropped is replaced by its flag value and
// reported, and the file's other settings still apply.
func TestResolveConfig_DropsOnlyTheBadWatermarks(t *testing.T) {
	const minKey, targetKey = "min_free_space", "target_free_space"
	// The flag values are the defaults, 15% and 25%.
	for _, tc := range []struct {
		name                string
		min, target         string // In the file.
		wantMin, wantTarget string
		wantIgnored         []string // Keys, in the order reported.
	}{
		{"minimum of 0%", "0%", "30%", "15%", "30%", []string{minKey}},
		{"unparsable minimum", "twenty", "30%", "15%", "30%", []string{minKey}},
		{"target of 100%", "20%", "100%", "20%", "25%", []string{targetKey}},
		{"unparsable target", "20%", "lots", "20%", "25%", []string{targetKey}},
		{"both out of range", "51%", "61%", "15%", "25%", []string{minKey, targetKey}},
		// A pair with no bad value has no single culprit: both go.
		{"gap under 1 point", "20%", "20.5%", "15%", "25%", []string{minKey, targetKey}},
		{"target below minimum", "40%", "30%", "15%", "25%", []string{minKey, targetKey}},
		// A file value equal to the flag's is the flag's, and only the
		// other one is reported.
		{"gap under 1 point from the flag's minimum", "15%", "15.5%", "15%", "25%", []string{targetKey}},
		// One bad value, and the other then too close to the flag value
		// that replaces it: both go, and both flags are used.
		{"bad minimum, target under 1 point above the flag's", "0%", "15.5%", "15%", "25%", []string{minKey, targetKey}},
		{"bad minimum, target below the flag's", "0%", "10%", "15%", "25%", []string{minKey, targetKey}},
		{"bad target, minimum above the flag's", "30%", "100%", "15%", "25%", []string{targetKey, minKey}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			path := writeConfigFile(t, t.TempDir(), "config.json", fmt.Sprintf(`{
				"min_free_space": %q,
				"target_free_space": %q,
				"reserved_space_gb": 120,
				"eviction_check_interval": "45s"
			}`, tc.min, tc.target))
			base := DefaultEvictorConfig()

			got, ignored, err := ResolveConfig(base, ConfigFields{}, path, flexibleCheck)
			if err != nil {
				t.Fatalf("ResolveConfig with watermarks %q, %q failed: %v", tc.min, tc.target, err)
			}
			want := base
			want.MinFreeSpace, want.TargetFreeSpace = tc.wantMin, tc.wantTarget
			want.ReservedSpaceGB, want.CheckInterval = 120, 45*time.Second
			if got != want {
				t.Errorf("ResolveConfig with watermarks %q, %q = %+v, want %+v", tc.min, tc.target, got, want)
			}
			if len(ignored) != len(tc.wantIgnored) {
				t.Fatalf("ResolveConfig with watermarks %q, %q ignored %+v, want %q", tc.min, tc.target, ignored, tc.wantIgnored)
			}
			fileValue := map[string]string{minKey: tc.min, targetKey: tc.target}
			flagValue := map[string]string{minKey: base.MinFreeSpace, targetKey: base.TargetFreeSpace}
			for i, w := range ignored {
				key := tc.wantIgnored[i]
				if w.Field != key || w.Value != fileValue[key] || w.Used != flagValue[key] || w.Reason == nil {
					t.Errorf("ignored[%d] = %+v, want %s %q replaced by %q, with a reason", i, w, key, fileValue[key], flagValue[key])
				}
			}
		})
	}
}

// TestResolveConfig_BadFlagsAreNotBlamedOnTheFile covers watermark flags that
// are bad themselves: dropping the file's watermarks cannot help, and the
// caller's check of the flags is what reports the problem. So nothing is
// reported as ignored, and the flag watermarks are returned for that check
// to fail on.
func TestResolveConfig_BadFlagsAreNotBlamedOnTheFile(t *testing.T) {
	for _, tc := range []struct {
		name       string
		fileTarget string
		flagMin    string
	}{
		{name: "bad explicit flag, good file", fileTarget: "30%", flagMin: "fifteen"},
		{name: "bad explicit flag, bad file", fileTarget: "100%", flagMin: "fifteen"},
		{name: "flags out of order, file target too close", fileTarget: "30.5%", flagMin: "30%"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			path := writeConfigFile(t, t.TempDir(), "config.json", fmt.Sprintf(`{
				"min_free_space": "20%%",
				"target_free_space": %q,
				"reserved_space_gb": 120
			}`, tc.fileTarget))
			base := DefaultEvictorConfig()
			base.MinFreeSpace = tc.flagMin

			got, ignored, err := ResolveConfig(base, ConfigFields{MinFreeSpace: true}, path, flexibleCheck)
			if err != nil {
				t.Fatalf("ResolveConfig failed: %v", err)
			}
			if len(ignored) != 0 {
				t.Errorf("ResolveConfig with -min-free-space=%q ignored %+v, want nothing blamed on the file", tc.flagMin, ignored)
			}
			want := base
			want.ReservedSpaceGB = 120
			if got != want {
				t.Errorf("ResolveConfig with -min-free-space=%q = %+v, want the flag watermarks %+v", tc.flagMin, got, want)
			}
		})
	}
}

// TestResolveConfig_CheckSeesTheResolvedConfig pins what is checked: the file
// with the explicit flags applied over it, which is what the caller would use.
// A bad value the user overrode on the command line is not reported.
func TestResolveConfig_CheckSeesTheResolvedConfig(t *testing.T) {
	path := writeConfigFile(t, t.TempDir(), "config.json", `{
		"min_free_space": "0%",
		"target_free_space": "30%",
		"reserved_space_gb": 120
	}`)
	base := DefaultEvictorConfig()
	base.MinFreeSpace = "10%"

	got, ignored, err := ResolveConfig(base, ConfigFields{MinFreeSpace: true}, path, flexibleCheck)
	if err != nil {
		t.Fatalf("ResolveConfig failed: %v", err)
	}
	if len(ignored) != 0 {
		t.Errorf("ResolveConfig ignored %+v, want nothing", ignored)
	}
	want := base
	want.TargetFreeSpace = "30%"
	want.ReservedSpaceGB = 120
	if got != want {
		t.Errorf("ResolveConfig = %+v, want %+v", got, want)
	}
}

// TestResolveConfig_CheckErrorOfAnotherKindDropsBothWatermarks covers a check
// that does not say which watermark is at fault: it counts as a pair error.
func TestResolveConfig_CheckErrorOfAnotherKindDropsBothWatermarks(t *testing.T) {
	path := writeConfigFile(t, t.TempDir(), "config.json", `{"min_free_space": "20%", "target_free_space": "30%"}`)
	base := DefaultEvictorConfig()
	rejectFile := func(cfg EvictorConfig) error {
		if cfg.MinFreeSpace != base.MinFreeSpace || cfg.TargetFreeSpace != base.TargetFreeSpace {
			return errors.New("not the flags")
		}
		return nil
	}
	got, ignored, err := ResolveConfig(base, ConfigFields{}, path, rejectFile)
	if err != nil {
		t.Fatalf("ResolveConfig failed: %v", err)
	}
	if got != base || len(ignored) != 2 {
		t.Errorf("ResolveConfig = %+v, ignoring %+v, want the flag values %+v, ignoring both watermarks", got, ignored, base)
	}
}

// TestResolveConfig_CheckIsOnlyForTheFile leaves the flag values to the
// caller, which decides what a bad flag means.
func TestResolveConfig_CheckIsOnlyForTheFile(t *testing.T) {
	failing := func(EvictorConfig) error {
		t.Error("ResolveConfig called check without watermarks from a configuration file to vet")
		return fmt.Errorf("check called")
	}
	base := DefaultEvictorConfig()
	if _, _, err := ResolveConfig(base, ConfigFields{}, "", failing); err != nil {
		t.Errorf("ResolveConfig without a file failed: %v", err)
	}
	// A file that does not load is already rejected; check has nothing to vet.
	if _, _, err := ResolveConfig(base, ConfigFields{}, filepath.Join(t.TempDir(), "missing.json"), failing); err == nil {
		t.Errorf("ResolveConfig with a missing file succeeded, want an error")
	}
	// Nor does a file that sets no watermarks.
	path := writeConfigFile(t, t.TempDir(), "config.json", `{"reserved_space_gb": 120}`)
	if _, _, err := ResolveConfig(base, ConfigFields{}, path, failing); err != nil {
		t.Errorf("ResolveConfig with a file without watermarks failed: %v", err)
	}
}

func TestResolveConfig_FileThatPassesCheckIsApplied(t *testing.T) {
	path := writeConfigFile(t, t.TempDir(), "config.json", fullConfigJSON)
	withCheck, ignored, err := ResolveConfig(DefaultEvictorConfig(), ConfigFields{}, path, flexibleCheck)
	if err != nil {
		t.Fatalf("ResolveConfig failed: %v", err)
	}
	withoutCheck, _, err := ResolveConfig(DefaultEvictorConfig(), ConfigFields{}, path, nil)
	if err != nil {
		t.Fatalf("ResolveConfig failed: %v", err)
	}
	if withCheck != withoutCheck || len(ignored) != 0 {
		t.Errorf("ResolveConfig with a passing check = %+v, ignoring %+v, want %+v as without one, ignoring nothing", withCheck, ignored, withoutCheck)
	}
}

func TestConfigFieldsFromFlagNames(t *testing.T) {
	got := ConfigFieldsFromFlagNames(map[string]bool{
		FlagMinFreeSpace:      true,
		FlagBatchDelay:        true,
		"some-unrelated-flag": true,
		FlagMinBlobAge:        false,
	})

	want := ConfigFields{MinFreeSpace: true, BatchDelay: true}
	if got != want {
		t.Errorf("ConfigFieldsFromFlagNames = %+v, want %+v", got, want)
	}
}

func TestResolveConfigFilePath(t *testing.T) {
	tests := []struct {
		name     string
		flag     string
		cacheDir string
		want     string
	}{
		{"defaults alongside the cache", "", "/var/cache/cas", "/var/cache/cas/" + ConfigFileName},
		{"explicit path is honored", "/etc/cas/tuning.json", "/var/cache/cas", "/etc/cas/tuning.json"},
		{"none disables the file", "none", "/var/cache/cas", ""},
		{"dev null disables the file", "/dev/null", "/var/cache/cas", ""},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := ResolveConfigFilePath(tc.flag, tc.cacheDir); got != tc.want {
				t.Errorf("ResolveConfigFilePath(%q, %q) = %q, want %q", tc.flag, tc.cacheDir, got, tc.want)
			}
		})
	}
}
