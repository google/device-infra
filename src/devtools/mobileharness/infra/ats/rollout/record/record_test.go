package record

import (
	"errors"
	"testing"
	"time"

	"github.com/google/device-infra/src/devtools/mobileharness/infra/ats/rollout/store"
	"github.com/google/go-cmp/cmp"
	"github.com/google/go-cmp/cmp/cmpopts"
)

var (
	t0   = time.Date(2026, 11, 10, 3, 0, 0, 0, time.UTC)
	rev1 = Revision{Build: "asia-docker.pkg.dev/p/r/omnilab-ats@sha256:aaa", TenantConfigVersion: "20260917_163105", PoolConfigVersion: "20261002_091200"}

	// compareDocs compares parsed documents by their exported fields, and
	// revisions field by field rather than with Revision.Equal, which ignores
	// the repository part of a build.
	compareDocs = cmp.Options{
		cmpopts.IgnoreUnexported(Domain{}, Group{}, Rollout{}, Slot{}, Member{}),
		cmp.Comparer(func(a, b Revision) bool { return a == b }),
	}
)

func TestKeyConstructors(t *testing.T) {
	group := GroupKey("d1", "dda")
	rollout := RolloutKey(group, 7)
	tests := []struct {
		name string
		got  store.Key
		want string
	}{
		{name: "DomainKey(d1)", got: DomainKey("d1"), want: "domains/d1"},
		{name: "GroupKey(d1, dda)", got: group, want: "domains/d1/groups/dda"},
		{name: "RolloutKey(group, 7)", got: rollout, want: "domains/d1/groups/dda/rollouts/7"},
		{name: "SlotKey(rollout, 10)", got: SlotKey(rollout, 10), want: "domains/d1/groups/dda/rollouts/7/slots/10"},
		{name: "MemberKey(group, host.example.com)", got: MemberKey(group, "host.example.com"), want: "domains/d1/groups/dda/members/host.example.com"},
	}
	for _, tc := range tests {
		if got := tc.got.String(); got != tc.want {
			t.Errorf("%s = %q, want %q", tc.name, got, tc.want)
		}
	}
}

func TestRolloutSeq(t *testing.T) {
	group := GroupKey("d1", "dda")
	if got, err := RolloutSeq(RolloutKey(group, 7)); err != nil || got != 7 {
		t.Errorf("RolloutSeq(RolloutKey(group, 7)) = %d, %v, want 7, nil", got, err)
	}
	for _, key := range []store.Key{
		group,
		SlotKey(RolloutKey(group, 1), 3),
		group.Child(KindRollout, "0"),
		group.Child(KindRollout, "-1"),
		group.Child(KindRollout, "x"),
		group.Child(KindRollout, "007"),
		group.Child(KindRollout, "+7"),
		group.Child(KindRollout, "9223372036854775808"),
		store.NewKey("", "d1").Child(KindRollout, "7"), // Invalid ancestor.
	} {
		if got, err := RolloutSeq(key); !errors.Is(err, ErrInvalid) {
			t.Errorf("RolloutSeq(%q) = %d, %v, want error %v", key, got, err, ErrInvalid)
		}
	}
}

func TestSlotIndex(t *testing.T) {
	rollout := RolloutKey(GroupKey("d1", "dda"), 7)
	for _, want := range []int{0, 10} {
		if got, err := SlotIndex(SlotKey(rollout, want)); err != nil || got != want {
			t.Errorf("SlotIndex(SlotKey(rollout, %d)) = %d, %v, want %d, nil", want, got, err, want)
		}
	}
	for _, key := range []store.Key{
		rollout,
		rollout.Child(KindSlot, "-1"),
		rollout.Child(KindSlot, "-0"),
		rollout.Child(KindSlot, "+1"),
		rollout.Child(KindSlot, "01"),
		rollout.Child(KindSlot, "x"),
		store.NewKey(KindRollout, "a/b").Child(KindSlot, "1"), // Invalid ancestor.
	} {
		if got, err := SlotIndex(key); !errors.Is(err, ErrInvalid) {
			t.Errorf("SlotIndex(%q) = %d, %v, want error %v", key, got, err, ErrInvalid)
		}
	}
}

func TestDomainID(t *testing.T) {
	tests := []struct {
		in, want string
	}{
		{in: "dcon-acceptor.ig.endpoints.proj.cloud.goog", want: "dcon-acceptor.ig.endpoints.proj.cloud.goog"},
		{in: "wss://dcon-acceptor.ig.endpoints.proj.cloud.goog", want: "dcon-acceptor.ig.endpoints.proj.cloud.goog"},
		{in: "wss://DCON-Acceptor.Proj.cloud.goog:443/path?q=1", want: "dcon-acceptor.proj.cloud.goog"},
		{in: " acceptor.example.com. ", want: "acceptor.example.com"},
		{in: "acceptor.example.com:8443/x", want: "acceptor.example.com"},
		{in: "acceptor.example.com?q=1", want: "acceptor.example.com"},
		{in: "user@Acceptor.example.com", want: "acceptor.example.com"},
		{in: "wss://user@acceptor.example.com", want: "acceptor.example.com"},
		// The URL form and the bare form of one address give one ID.
		{in: "[::1]:443", want: "::1"},
		{in: "wss://[::1]:443", want: "::1"},
	}
	for _, tc := range tests {
		if got, err := DomainID(tc.in); err != nil || got != tc.want {
			t.Errorf("DomainID(%q) = %q, %v, want %q, nil", tc.in, got, err, tc.want)
		}
	}
	for _, in := range []string{"", "   ", "wss://", "wss:/acceptor.example.com", "wss://%zz", "acceptor example.com", "/path"} {
		if got, err := DomainID(in); !errors.Is(err, ErrInvalid) {
			t.Errorf("DomainID(%q) = %q, %v, want error %v", in, got, err, ErrInvalid)
		}
	}
}

func TestAgentID(t *testing.T) {
	tests := []struct {
		in, want string
	}{
		{in: "Worker-01", want: "worker-01"},
		{in: " worker-01.lab.example.com.\n", want: "worker-01.lab.example.com"},
	}
	for _, tc := range tests {
		if got, err := AgentID(tc.in); err != nil || got != tc.want {
			t.Errorf("AgentID(%q) = %q, %v, want %q, nil", tc.in, got, err, tc.want)
		}
	}
	for _, in := range []string{"", " \n", ".", "..", "a/b"} {
		if got, err := AgentID(in); !errors.Is(err, ErrInvalid) {
			t.Errorf("AgentID(%q) = %q, %v, want error %v", in, got, err, ErrInvalid)
		}
	}
}

func TestRevisionEqual(t *testing.T) {
	withBuild := func(build string) Revision {
		return Revision{Build: build, TenantConfigVersion: rev1.TenantConfigVersion, PoolConfigVersion: rev1.PoolConfigVersion}
	}
	tests := []struct {
		name string
		a, b Revision
		want bool
	}{
		{name: "same", a: rev1, b: rev1, want: true},
		{name: "other_repository_same_digest", a: rev1, b: withBuild("other.repo/x@sha256:aaa"), want: true},
		{name: "other_digest", a: rev1, b: withBuild("asia-docker.pkg.dev/p/r/omnilab-ats@sha256:bbb"), want: false},
		{name: "other_tenant_config", a: rev1, b: Revision{Build: rev1.Build, TenantConfigVersion: "x", PoolConfigVersion: rev1.PoolConfigVersion}, want: false},
		{name: "other_pool_config", a: rev1, b: Revision{Build: rev1.Build, TenantConfigVersion: rev1.TenantConfigVersion, PoolConfigVersion: "x"}, want: false},
		{name: "controller_build_only", a: Revision{Build: "r@sha256:aaa"}, b: Revision{Build: "q@sha256:aaa"}, want: true},
		{name: "build_only_vs_configs", a: Revision{Build: "r@sha256:aaa"}, b: withBuild("r@sha256:aaa"), want: false},
		{name: "no_digest_same_build", a: Revision{Build: "r:latest"}, b: Revision{Build: "r:latest"}, want: true},
		{name: "no_digest_other_build", a: Revision{Build: "r:latest"}, b: Revision{Build: "q:latest"}, want: false},
		{name: "empty_digest_vs_zero", a: Revision{Build: "r@"}, b: Revision{}, want: false},
		{name: "both_zero", a: Revision{}, b: Revision{}, want: true},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := tc.a.Equal(tc.b); got != tc.want {
				t.Errorf("Revision(%+v).Equal(%+v) = %v, want %v", tc.a, tc.b, got, tc.want)
			}
		})
	}
}

func TestDomainRoundTrip(t *testing.T) {
	src := store.Record{"paused": true, "note": "kept"}
	d, err := ParseDomain(src)
	if err != nil {
		t.Fatalf("ParseDomain(%v) failed: %v", src, err)
	}
	if diff := cmp.Diff(Domain{Paused: true}, d, compareDocs); diff != "" {
		t.Errorf("ParseDomain(%v) diff (-want +got):\n%s", src, diff)
	}

	d.Paused = false
	want := store.Record{"note": "kept"}
	if diff := cmp.Diff(want, d.Record()); diff != "" {
		t.Errorf("Domain.Record() after clearing Paused diff (-want +got):\n%s", diff)
	}
}

func TestDomainInvalid(t *testing.T) {
	for name, r := range map[string]store.Record{
		"paused_string": {"paused": "yes"},
		"paused_nil":    {"paused": nil},
	} {
		if got, err := ParseDomain(r); !errors.Is(err, ErrInvalid) {
			t.Errorf("%s: ParseDomain(%v) = %+v, %v, want error %v", name, r, got, err, ErrInvalid)
		}
	}
}

func TestParseCanonicalizesTimes(t *testing.T) {
	local := time.Date(2026, 11, 10, 11, 0, 0, 123456789, time.FixedZone("CST", 8*60*60))
	r := store.Record{"status": "ACTIVE", "heartbeat_at": local}
	m, err := ParseMember(r)
	if err != nil {
		t.Fatalf("ParseMember(%v) failed: %v", r, err)
	}

	// The parsed time is the time the store would return: UTC, microseconds.
	want := time.Date(2026, 11, 10, 3, 0, 0, 123456000, time.UTC)
	if !m.HeartbeatAt.Equal(want) || m.HeartbeatAt.Location() != time.UTC {
		t.Errorf("ParseMember(%v).HeartbeatAt = %v, want %v in UTC", r, m.HeartbeatAt, want)
	}
	if got := m.Record()["heartbeat_at"]; got != want {
		t.Errorf("Member.Record()[heartbeat_at] = %v, want %v", got, want)
	}
}

// groupRecord returns a group as a human and an earlier manager left it,
// with fields at every level that this package does not know.
func groupRecord() store.Record {
	return store.Record{
		"image": "asia-docker.pkg.dev/p/r/omnilab-ats",
		"tag":   "latest",
		"policy": map[string]any{
			"waves":         []any{int64(1), "3", "5%"},
			"max_in_flight": int64(3),
			"max_failed":    int64(0),
			"soak":          "30m",
		},
		"cancel": true,
		"last_rollout": map[string]any{
			"seq":         int64(3),
			"revision":    map[string]any{"build": "r@sha256:old", "signer": "kept"},
			"outcome":     "FAILED",
			"summary":     map[string]any{"h1": "FAILED"},
			"finished_at": t0,
			"archived_by": "kept",
		},
		"operator": "kept",
	}
}

func TestParseGroup(t *testing.T) {
	got, err := ParseGroup(groupRecord())
	if err != nil {
		t.Fatalf("ParseGroup(%v) failed: %v", groupRecord(), err)
	}
	want := Group{
		Image:  "asia-docker.pkg.dev/p/r/omnilab-ats",
		Tag:    "latest",
		Policy: Policy{Waves: []Wave{{Value: 1}, {Value: 3}, {Value: 5, Percent: true}}, MaxInFlight: 3},
		Cancel: true,
		LastRollout: &LastRollout{
			Seq:        3,
			Revision:   Revision{Build: "r@sha256:old"},
			Outcome:    OutcomeFailed,
			Summary:    map[string]SummaryEntry{"h1": SummaryEntry(TaskFailed)},
			FinishedAt: t0,
		},
	}
	if diff := cmp.Diff(want, got, compareDocs); diff != "" {
		t.Errorf("ParseGroup(%v) diff (-want +got):\n%s", groupRecord(), diff)
	}
}

func TestGroupRecordKeepsWhatItRead(t *testing.T) {
	g, err := ParseGroup(groupRecord())
	if err != nil {
		t.Fatalf("ParseGroup(%v) failed: %v", groupRecord(), err)
	}

	// The manager clears a request. Everything else stays exactly as read:
	// unknown fields at every level, the waves as the human wrote them, and
	// the explicit max_failed of 0.
	g.Cancel = false
	want := groupRecord()
	delete(want, "cancel")
	if diff := cmp.Diff(want, g.Record()); diff != "" {
		t.Errorf("Group.Record() after clearing Cancel diff (-want +got):\n%s", diff)
	}
}

func TestGroupArchive(t *testing.T) {
	g, err := ParseGroup(groupRecord())
	if err != nil {
		t.Fatalf("ParseGroup(%v) failed: %v", groupRecord(), err)
	}

	// Archiving a new rollout replaces last_rollout: unknown keys that
	// described the previous rollout and its revision do not carry over.
	g.Cancel = false
	g.LastRollout = &LastRollout{
		Seq:        4,
		Revision:   rev1,
		Outcome:    OutcomeSucceeded,
		Summary:    map[string]SummaryEntry{"h1": SummaryEntry(TaskDone), "h2": SummaryOffline},
		FinishedAt: t0.Add(time.Hour),
	}
	got := g.Record()
	want := groupRecord()
	delete(want, "cancel")
	want["last_rollout"] = map[string]any{
		"seq": int64(4),
		"revision": map[string]any{
			"build":                 rev1.Build,
			"tenant_config_version": rev1.TenantConfigVersion,
			"pool_config_version":   rev1.PoolConfigVersion,
		},
		"outcome":     "SUCCEEDED",
		"summary":     map[string]any{"h1": "DONE", "h2": "OFFLINE"},
		"finished_at": t0.Add(time.Hour),
	}
	if diff := cmp.Diff(want, got); diff != "" {
		t.Errorf("Group.Record() after archiving diff (-want +got):\n%s", diff)
	}

	back, err := ParseGroup(got)
	if err != nil {
		t.Fatalf("ParseGroup(Group.Record()) failed: %v", err)
	}
	if diff := cmp.Diff(g, back, compareDocs); diff != "" {
		t.Errorf("ParseGroup(Group.Record()) diff (-want +got):\n%s", diff)
	}
}

func TestGroupRearchiveKeepsLastRolloutExtras(t *testing.T) {
	g, err := ParseGroup(groupRecord())
	if err != nil {
		t.Fatalf("ParseGroup(%v) failed: %v", groupRecord(), err)
	}

	// A repeated archive of the same rollout (after a crash between the two
	// archive steps) writes the same values, so unknown keys are kept.
	same := *g.LastRollout
	g.LastRollout = &same
	if diff := cmp.Diff(groupRecord(), g.Record()); diff != "" {
		t.Errorf("Group.Record() after re-archiving the same rollout diff (-want +got):\n%s", diff)
	}
}

func TestGroupFromLiteral(t *testing.T) {
	g := Group{
		Image:  "i",
		Tag:    "t",
		Policy: Policy{Waves: []Wave{{Value: 1}, {Value: 50, Percent: true}}, MaxFailed: 2},
	}
	got := g.Record()
	want := store.Record{
		"image":  "i",
		"tag":    "t",
		"policy": map[string]any{"waves": []any{int64(1), "50%"}, "max_failed": int64(2)},
	}
	if diff := cmp.Diff(want, got); diff != "" {
		t.Errorf("Group.Record() of a literal diff (-want +got):\n%s", diff)
	}

	back, err := ParseGroup(got)
	if err != nil {
		t.Fatalf("ParseGroup(Group.Record()) failed: %v", err)
	}
	if diff := cmp.Diff(g, back, compareDocs); diff != "" {
		t.Errorf("ParseGroup(Group.Record()) diff (-want +got):\n%s", diff)
	}
}

func TestGroupInvalid(t *testing.T) {
	withPolicy := func(policy any) store.Record {
		return store.Record{"image": "i", "tag": "t", "policy": policy}
	}
	withLastRollout := func(last any) store.Record {
		r := withPolicy(map[string]any{"waves": []any{int64(1)}})
		r["last_rollout"] = last
		return r
	}
	// validLast returns a valid last_rollout map with one field changed; a
	// nil value removes the field.
	validLast := func(key string, value any) map[string]any {
		m := map[string]any{"seq": int64(1), "revision": map[string]any{"build": "r@sha256:aaa"}, "outcome": "FAILED", "finished_at": t0}
		if value == nil {
			delete(m, key)
		} else {
			m[key] = value
		}
		return m
	}
	tests := map[string]store.Record{
		"missing_image":        {"tag": "t", "policy": map[string]any{"waves": []any{int64(1)}}},
		"missing_tag":          {"image": "i", "policy": map[string]any{"waves": []any{int64(1)}}},
		"missing_policy":       {"image": "i", "tag": "t"},
		"image_not_string":     {"image": int64(1), "tag": "t", "policy": map[string]any{"waves": []any{int64(1)}}},
		"cancel_nil":           {"image": "i", "tag": "t", "policy": map[string]any{"waves": []any{int64(1)}}, "cancel": nil},
		"policy_not_map":       withPolicy("x"),
		"policy_nil":           withPolicy(nil),
		"no_waves":             withPolicy(map[string]any{}),
		"empty_waves":          withPolicy(map[string]any{"waves": []any{}}),
		"wave_zero":            withPolicy(map[string]any{"waves": []any{int64(0)}}),
		"wave_zero_percent":    withPolicy(map[string]any{"waves": []any{"0%"}}),
		"wave_bad_string":      withPolicy(map[string]any{"waves": []any{"x%"}}),
		"wave_over_100":        withPolicy(map[string]any{"waves": []any{"101%"}}),
		"wave_bool":            withPolicy(map[string]any{"waves": []any{true}}),
		"max_in_flight_zero":   withPolicy(map[string]any{"waves": []any{int64(1)}, "max_in_flight": int64(0)}),
		"max_in_flight_minus":  withPolicy(map[string]any{"waves": []any{int64(1)}, "max_in_flight": int64(-1)}),
		"max_in_flight_string": withPolicy(map[string]any{"waves": []any{int64(1)}, "max_in_flight": "3"}),
		"max_in_flight_nil":    withPolicy(map[string]any{"waves": []any{int64(1)}, "max_in_flight": nil}),
		"negative_max_failed":  withPolicy(map[string]any{"waves": []any{int64(1)}, "max_failed": int64(-1)}),
		"last_not_map":         withLastRollout("x"),
		"last_seq_zero":        withLastRollout(validLast("seq", int64(0))),
		"last_no_seq":          withLastRollout(validLast("seq", nil)),
		"last_no_revision":     withLastRollout(validLast("revision", nil)),
		"last_empty_revision":  withLastRollout(validLast("revision", map[string]any{})),
		"last_no_outcome":      withLastRollout(validLast("outcome", nil)),
		"last_no_finished_at":  withLastRollout(validLast("finished_at", nil)),
		"last_bad_outcome":     withLastRollout(validLast("outcome", "MAYBE")),
		"last_empty_outcome":   withLastRollout(validLast("outcome", "")),
		"last_bad_summary":     withLastRollout(validLast("summary", map[string]any{"h1": "GONE"})),
		"last_empty_summary":   withLastRollout(validLast("summary", map[string]any{"h1": ""})),
		"last_running_summary": withLastRollout(validLast("summary", map[string]any{"h1": "RUNNING"})),
	}
	for name, r := range tests {
		t.Run(name, func(t *testing.T) {
			if got, err := ParseGroup(r); !errors.Is(err, ErrInvalid) {
				t.Errorf("ParseGroup(%v) = %+v, %v, want error %v", r, got, err, ErrInvalid)
			}
		})
	}
}

func TestRolloutRoundTrip(t *testing.T) {
	src := store.Record{
		"revision":   map[string]any{"build": "r@sha256:aaa", "signer": "kept"},
		"waves":      []any{int64(1), int64(3), int64(6)},
		"created_at": t0,
		"extra":      "kept",
	}
	ro, err := ParseRollout(src)
	if err != nil {
		t.Fatalf("ParseRollout(%v) failed: %v", src, err)
	}
	want := Rollout{Revision: Revision{Build: "r@sha256:aaa"}, Waves: []int64{1, 3, 6}, CreatedAt: t0}
	if diff := cmp.Diff(want, ro, compareDocs); diff != "" {
		t.Errorf("ParseRollout(%v) diff (-want +got):\n%s", src, diff)
	}

	// The manager stops the rollout on its last wave; the orchestrator
	// concludes it.
	ro.StopRequest = StopSuperseded
	ro.WaveIndex = 2
	ro.Outcome = OutcomeSuperseded
	ro.Summary = map[string]SummaryEntry{"h1": SummaryEntry(TaskDone), "h2": SummaryNotStarted}
	ro.FinishedAt = t0.Add(time.Hour)
	got := ro.Record()
	wantRecord := store.Record{
		"revision":     map[string]any{"build": "r@sha256:aaa", "signer": "kept"},
		"waves":        []any{int64(1), int64(3), int64(6)},
		"created_at":   t0,
		"stop_request": "SUPERSEDED",
		"wave_index":   int64(2),
		"outcome":      "SUPERSEDED",
		"summary":      map[string]any{"h1": "DONE", "h2": "NOT_STARTED"},
		"finished_at":  t0.Add(time.Hour),
		"extra":        "kept",
	}
	if diff := cmp.Diff(wantRecord, got); diff != "" {
		t.Errorf("Rollout.Record() after concluding diff (-want +got):\n%s", diff)
	}

	back, err := ParseRollout(got)
	if err != nil {
		t.Fatalf("ParseRollout(Rollout.Record()) failed: %v", err)
	}
	if diff := cmp.Diff(ro, back, compareDocs); diff != "" {
		t.Errorf("ParseRollout(Rollout.Record()) diff (-want +got):\n%s", diff)
	}
}

func TestRolloutEmptySummaryIsAbsent(t *testing.T) {
	r := store.Record{
		"revision":    map[string]any{"build": "r@sha256:aaa"},
		"waves":       []any{int64(1)},
		"outcome":     "CANCELLED",
		"finished_at": t0,
		"summary":     map[string]any{},
	}
	ro, err := ParseRollout(r)
	if err != nil {
		t.Fatalf("ParseRollout(%v) failed: %v", r, err)
	}
	if ro.Summary != nil {
		t.Errorf("ParseRollout(%v).Summary = %v, want nil for an empty summary", r, ro.Summary)
	}
	ro.Summary = map[string]SummaryEntry{}
	if got := ro.Record(); got["summary"] != nil {
		t.Errorf("Rollout.Record() with an empty Summary has summary = %v, want the field removed", got["summary"])
	}
}

func TestRolloutInvalid(t *testing.T) {
	// with returns a valid rollout with two waves and one field changed; a nil
	// value removes the field.
	with := func(key string, value any) store.Record {
		r := store.Record{"revision": map[string]any{"build": "r@sha256:aaa"}, "waves": []any{int64(1), int64(3)}}
		if value == nil {
			delete(r, key)
		} else {
			r[key] = value
		}
		return r
	}
	concluded := func(key string, value any) store.Record {
		r := with("outcome", "FAILED")
		r["finished_at"] = t0
		if value == nil {
			delete(r, key)
		} else {
			r[key] = value
		}
		return r
	}
	tests := map[string]store.Record{
		"missing_revision":            with("revision", nil),
		"revision_not_map":            with("revision", "x"),
		"revision_build_int":          with("revision", map[string]any{"build": int64(1)}),
		"revision_without_build":      with("revision", map[string]any{"tenant_config_version": "v1"}),
		"missing_waves":               with("waves", nil),
		"empty_waves":                 with("waves", []any{}),
		"waves_not_increasing":        with("waves", []any{int64(3), int64(3)}),
		"waves_string":                with("waves", []any{"1"}),
		"negative_wave_index":         with("wave_index", int64(-1)),
		"wave_index_too_large":        with("wave_index", int64(2)),
		"bad_stop_request":            with("stop_request", "PAUSED"),
		"empty_stop_request":          with("stop_request", ""),
		"bad_outcome":                 concluded("outcome", "DONE"),
		"empty_summary_entry":         concluded("summary", map[string]any{"h1": ""}),
		"outcome_without_finished_at": concluded("finished_at", nil),
		"finished_at_without_outcome": concluded("outcome", nil),
		"finished_without_outcome":    with("finished", true),
		"summary_without_outcome":     with("summary", map[string]any{"h1": "DONE"}),
		"revision_empty_map":          with("revision", map[string]any{}),
		"created_at_nil":              {"revision": map[string]any{"build": "r@sha256:aaa"}, "waves": []any{int64(1)}, "created_at": nil},
	}
	for name, r := range tests {
		t.Run(name, func(t *testing.T) {
			if got, err := ParseRollout(r); !errors.Is(err, ErrInvalid) {
				t.Errorf("ParseRollout(%v) = %+v, %v, want error %v", r, got, err, ErrInvalid)
			}
		})
	}
}

func TestSlotRoundTrip(t *testing.T) {
	src := store.Record{"state": "OPEN", "extra": "kept"}
	s, err := ParseSlot(src)
	if err != nil {
		t.Fatalf("ParseSlot(%v) failed: %v", src, err)
	}
	if diff := cmp.Diff(Slot{State: SlotOpen}, s, compareDocs); diff != "" {
		t.Errorf("ParseSlot(%v) diff (-want +got):\n%s", src, diff)
	}

	// The agent claims the slot; the claim carries the task's first result.
	s.State = SlotClaimed
	s.AgentID = "h1"
	s.ClaimedAt = t0
	s.RenewedAt = t0
	s.Deadline = t0.Add(time.Hour)
	s.Result = TaskRunning
	got := s.Record()
	want := store.Record{
		"state":      "CLAIMED",
		"agent_id":   "h1",
		"claimed_at": t0,
		"renewed_at": t0,
		"deadline":   t0.Add(time.Hour),
		"result":     "RUNNING",
		"extra":      "kept",
	}
	if diff := cmp.Diff(want, got); diff != "" {
		t.Errorf("Slot.Record() after claiming diff (-want +got):\n%s", diff)
	}

	back, err := ParseSlot(got)
	if err != nil {
		t.Fatalf("ParseSlot(Slot.Record()) failed: %v", err)
	}
	if diff := cmp.Diff(s, back, compareDocs); diff != "" {
		t.Errorf("ParseSlot(Slot.Record()) diff (-want +got):\n%s", diff)
	}
}

func TestSlotInvalid(t *testing.T) {
	// claimed returns a valid claimed slot with one field changed; a nil value
	// removes the field.
	claimed := func(key string, value any) store.Record {
		r := store.Record{"state": "CLAIMED", "agent_id": "h1", "claimed_at": t0, "renewed_at": t0, "result": "FAILED", "reason": ReasonCoreDown}
		if value == nil {
			delete(r, key)
		} else {
			r[key] = value
		}
		return r
	}
	tests := map[string]store.Record{
		"missing_state":              claimed("state", nil),
		"empty_state":                claimed("state", ""),
		"nil_state":                  {"state": nil},
		"bad_state":                  claimed("state", "HALF"),
		"claimed_without_agent":      claimed("agent_id", nil),
		"claimed_without_result":     claimed("result", nil),
		"claimed_without_claimed_at": claimed("claimed_at", nil),
		"claimed_without_renewed_at": claimed("renewed_at", nil),
		"open_with_agent":            {"state": "OPEN", "agent_id": "h1"},
		"open_with_result":           {"state": "OPEN", "result": "DONE"},
		"open_with_claimed_at":       {"state": "OPEN", "claimed_at": t0},
		"open_with_deadline":         {"state": "OPEN", "deadline": t0},
		"open_with_reason":           {"state": "OPEN", "reason": ReasonCoreDown},
		"closed_with_result":         {"state": "CLOSED", "result": "LOST"},
		"closed_with_renewed_at":     {"state": "CLOSED", "renewed_at": t0},
		"bad_result":                 claimed("result", "MAYBE"),
		"claimed_at_not_time":        claimed("claimed_at", "yesterday"),
		"reason_on_done":             {"state": "CLAIMED", "agent_id": "h1", "claimed_at": t0, "renewed_at": t0, "result": "DONE", "reason": ReasonCoreDown},
		"reason_on_running":          {"state": "CLAIMED", "agent_id": "h1", "claimed_at": t0, "renewed_at": t0, "result": "RUNNING", "reason": ReasonNotConverged},
		"down_after_done_not_time":   claimed("down_after_done_at", "x"),
		"down_after_done_on_failed":  claimed("down_after_done_at", t0),
	}
	for name, r := range tests {
		t.Run(name, func(t *testing.T) {
			if got, err := ParseSlot(r); !errors.Is(err, ErrInvalid) {
				t.Errorf("ParseSlot(%v) = %+v, %v, want error %v", r, got, err, ErrInvalid)
			}
		})
	}
}

func TestSlotFinalAndFailed(t *testing.T) {
	tests := []struct {
		name                  string
		slot                  Slot
		wantFinal, wantFailed bool
	}{
		{name: "open", slot: Slot{State: SlotOpen}},
		{name: "running", slot: Slot{State: SlotClaimed, Result: TaskRunning}},
		{name: "done", slot: Slot{State: SlotClaimed, Result: TaskDone}, wantFinal: true},
		{name: "done_then_down", slot: Slot{State: SlotClaimed, Result: TaskDone, DownAfterDoneAt: t0}, wantFinal: true, wantFailed: true},
		{name: "failed", slot: Slot{State: SlotClaimed, Result: TaskFailed}, wantFinal: true, wantFailed: true},
		{name: "skipped", slot: Slot{State: SlotClaimed, Result: TaskSkipped}, wantFinal: true},
		{name: "lost", slot: Slot{State: SlotClaimed, Result: TaskLost}, wantFinal: true},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := tc.slot.Final(); got != tc.wantFinal {
				t.Errorf("Slot(%+v).Final() = %v, want %v", tc.slot, got, tc.wantFinal)
			}
			if got := tc.slot.Failed(); got != tc.wantFailed {
				t.Errorf("Slot(%+v).Failed() = %v, want %v", tc.slot, got, tc.wantFailed)
			}
		})
	}
}

func TestMemberRoundTrip(t *testing.T) {
	src := store.Record{
		"status":           "OPTED_OUT",
		"heartbeat_at":     t0,
		"running_revision": map[string]any{"build": "r@sha256:old", "pool_config_version": "p1", "signer": "kept"},
		"note":             "kept",
	}
	m, err := ParseMember(src)
	if err != nil {
		t.Fatalf("ParseMember(%v) failed: %v", src, err)
	}
	want := Member{Status: MemberOptedOut, HeartbeatAt: t0, RunningRevision: Revision{Build: "r@sha256:old", PoolConfigVersion: "p1"}}
	if diff := cmp.Diff(want, m, compareDocs); diff != "" {
		t.Errorf("ParseMember(%v) diff (-want +got):\n%s", src, diff)
	}

	// A heartbeat with the same revision keeps the revision map's unknown
	// keys.
	m.Status = MemberActive
	m.HeartbeatAt = t0.Add(time.Minute)
	got := m.Record()
	wantRecord := store.Record{
		"status":           "ACTIVE",
		"heartbeat_at":     t0.Add(time.Minute),
		"running_revision": map[string]any{"build": "r@sha256:old", "pool_config_version": "p1", "signer": "kept"},
		"note":             "kept",
	}
	if diff := cmp.Diff(wantRecord, got); diff != "" {
		t.Errorf("Member.Record() after a heartbeat diff (-want +got):\n%s", diff)
	}

	// After an upgrade the host reports a new revision; what described the
	// old one does not carry over.
	m.RunningRevision = Revision{Build: "r@sha256:aaa"}
	got = m.Record()
	wantRecord["running_revision"] = map[string]any{"build": "r@sha256:aaa"}
	if diff := cmp.Diff(wantRecord, got); diff != "" {
		t.Errorf("Member.Record() after an upgrade diff (-want +got):\n%s", diff)
	}

	back, err := ParseMember(got)
	if err != nil {
		t.Fatalf("ParseMember(Member.Record()) failed: %v", err)
	}
	if diff := cmp.Diff(m, back, compareDocs); diff != "" {
		t.Errorf("ParseMember(Member.Record()) diff (-want +got):\n%s", diff)
	}

	// A host that reports no revision removes the field.
	m.RunningRevision = Revision{}
	if got := m.Record(); got["running_revision"] != nil {
		t.Errorf("Member.Record() with a zero RunningRevision has running_revision = %v, want the field removed", got["running_revision"])
	}
}

func TestMemberInvalid(t *testing.T) {
	tests := map[string]store.Record{
		"missing_status":      {},
		"empty_status":        {"status": ""},
		"bad_status":          {"status": "ASLEEP"},
		"status_not_string":   {"status": true},
		"heartbeat_not_time":  {"status": "ACTIVE", "heartbeat_at": int64(1)},
		"revision_not_map":    {"status": "ACTIVE", "running_revision": "r@sha256:aaa"},
		"revision_build_bool": {"status": "ACTIVE", "running_revision": map[string]any{"build": true}},
		"revision_empty":      {"status": "ACTIVE", "running_revision": map[string]any{}},
		"revision_no_build":   {"status": "ACTIVE", "running_revision": map[string]any{"tenant_config_version": "v1"}},
		"revision_nil":        {"status": "ACTIVE", "running_revision": nil},
		"heartbeat_nil":       {"status": "ACTIVE", "heartbeat_at": nil},
	}
	for name, r := range tests {
		t.Run(name, func(t *testing.T) {
			if got, err := ParseMember(r); !errors.Is(err, ErrInvalid) {
				t.Errorf("ParseMember(%v) = %+v, %v, want error %v", r, got, err, ErrInvalid)
			}
		})
	}
}

func TestMemberStatusEligible(t *testing.T) {
	tests := []struct {
		status MemberStatus
		want   bool
	}{
		{status: MemberActive, want: true},
		{status: MemberDown, want: true},
		{status: MemberOptedOut, want: false},
		{status: MemberOffline, want: false},
		{status: "", want: false},
	}
	for _, tc := range tests {
		if got := tc.status.Eligible(); got != tc.want {
			t.Errorf("MemberStatus(%q).Eligible() = %v, want %v", tc.status, got, tc.want)
		}
	}
}

// TestRecordIsValidForTheStore checks that documents built as literals, with
// every field set, give records that the store accepts.
func TestRecordIsValidForTheStore(t *testing.T) {
	summary := map[string]SummaryEntry{"h1": SummaryEntry(TaskDone)}
	records := map[string]store.Record{
		"Domain": Domain{Paused: true}.Record(),
		"Group": Group{
			Image:       "i",
			Tag:         "t",
			Policy:      Policy{Waves: []Wave{{Value: 1}, {Value: 100, Percent: true}}, MaxInFlight: 3, MaxFailed: 1},
			Cancel:      true,
			Rerun:       true,
			LastRollout: &LastRollout{Seq: 1, Revision: rev1, Outcome: OutcomeSucceeded, Summary: summary, FinishedAt: t0},
		}.Record(),
		"Rollout": Rollout{
			Revision:    rev1,
			Waves:       []int64{1, 3},
			CreatedAt:   t0,
			StopRequest: StopCancelled,
			WaveIndex:   1,
			Outcome:     OutcomeCancelled,
			Summary:     summary,
			FinishedAt:  t0,
			Finished:    true,
		}.Record(),
		"Slot_done_then_down": Slot{
			State:           SlotClaimed,
			AgentID:         "h1",
			ClaimedAt:       t0,
			RenewedAt:       t0,
			Deadline:        t0,
			Result:          TaskDone,
			DownAfterDoneAt: t0,
		}.Record(),
		"Slot_failed": Slot{
			State:     SlotClaimed,
			AgentID:   "h1",
			ClaimedAt: t0,
			RenewedAt: t0,
			Result:    TaskFailed,
			Reason:    ReasonNotConverged,
		}.Record(),
		"Member": Member{Status: MemberActive, HeartbeatAt: t0, RunningRevision: rev1}.Record(),
	}
	for name, r := range records {
		if err := r.Validate(); err != nil {
			t.Errorf("%s.Record() = %v, which the store rejects: %v", name, r, err)
		}
	}
	// Every literal above also parses back, so the invariants the parsers
	// check are satisfiable by the typed API.
	parsers := map[string]func(store.Record) error{
		"Domain":              func(r store.Record) error { _, err := ParseDomain(r); return err },
		"Group":               func(r store.Record) error { _, err := ParseGroup(r); return err },
		"Rollout":             func(r store.Record) error { _, err := ParseRollout(r); return err },
		"Slot_done_then_down": func(r store.Record) error { _, err := ParseSlot(r); return err },
		"Slot_failed":         func(r store.Record) error { _, err := ParseSlot(r); return err },
		"Member":              func(r store.Record) error { _, err := ParseMember(r); return err },
	}
	for name, parse := range parsers {
		if err := parse(records[name]); err != nil {
			t.Errorf("parsing %s.Record() = %v failed: %v", name, records[name], err)
		}
	}
}

func TestParseCopiesTheRecord(t *testing.T) {
	src := store.Record{"status": "ACTIVE", "extra": map[string]any{"k": "v"}}
	m, err := ParseMember(src)
	if err != nil {
		t.Fatalf("ParseMember(%v) failed: %v", src, err)
	}

	// Neither the record that was parsed nor a record that was returned
	// shares maps with the document.
	src["extra"].(map[string]any)["k"] = "changed after ParseMember"
	m.Record()["extra"].(map[string]any)["k"] = "changed after Record"

	want := store.Record{"status": "ACTIVE", "extra": map[string]any{"k": "v"}}
	if diff := cmp.Diff(want, m.Record()); diff != "" {
		t.Errorf("Member.Record() after changing the parsed and a returned record diff (-want +got):\n%s", diff)
	}
}
