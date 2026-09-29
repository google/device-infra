package download

import (
	"context"
	"slices"
	"strings"
	"testing"
)

// TestAddNote_SeparatorIsUnambiguous pins that the notes field can be split
// back into exactly the notes that were added. Notes embed raw error text and
// paths, either of which can contain the separator's "|" or a line break.
func TestAddNote_SeparatorIsUnambiguous(t *testing.T) {
	job := &DownloadJob{DownloadStats: &Stats{}}
	job.addNote(NoteCacheWriteFailed, "first: %v", "a|b")
	job.addNote(NoteChunksIndexMoveFailed, "second:\n%s", "line two\r\nline three")

	got := strings.Split(job.Stats().Notes, "|")
	if len(got) != 2 {
		t.Fatalf("Notes %q splits on %q into %d entries, want 2", job.Stats().Notes, "|", len(got))
	}
	if strings.ContainsAny(job.Stats().Notes, "\r\n") {
		t.Errorf("Notes %q contains a line break", job.Stats().Notes)
	}
	if want := "first: a_b | second: line two line three"; job.Stats().Notes != want {
		t.Errorf("Notes = %q, want %q", job.Stats().Notes, want)
	}
}

// TestDoDownload_SeededNotesAreSanitized covers notes the caller hands in
// through DownloadJob.Notes, which reach the stats without passing through
// addNote.
func TestDoDownload_SeededNotesAreSanitized(t *testing.T) {
	// An unparseable digest fails DoDownload straight after the stats are
	// seeded, which is all this test needs.
	job := &DownloadJob{
		Digest: "INVALID_DIGEST",
		Notes: []Note{
			{Reason: NoteCacheSetupFailed, Message: "cache at /a|b unusable"},
			{Reason: NoteCacheSetupFailed, Message: "second\nline"},
		},
	}
	_ = job.DoDownload(context.Background())

	if want := "cache at /a_b unusable | second line"; job.Stats().Notes != want {
		t.Errorf("Notes = %q, want %q", job.Stats().Notes, want)
	}
}

// TestAddNote_RecordsReasonsInOrder pins that every note carries its reason,
// in the order the notes were added, so NoteReasons lines up with Notes.
func TestAddNote_RecordsReasonsInOrder(t *testing.T) {
	job := &DownloadJob{DownloadStats: &Stats{}}
	job.addNote(NoteProxyOverreportClamped, "clamped")
	job.addNote(NoteCacheWriteFailed, "could not cache")
	job.addNote(NoteProxyOverreportClamped, "clamped again")

	want := []NoteReason{NoteProxyOverreportClamped, NoteCacheWriteFailed, NoteProxyOverreportClamped}
	if got := job.Stats().NoteReasons; !slices.Equal(got, want) {
		t.Errorf("NoteReasons = %q, want %q", got, want)
	}
}

// TestDoDownload_SeededNotesCarryReasons covers the other way a note reaches
// the stats. Seeded notes skip addNote, so without this they could reach the
// notes text but not the metric.
func TestDoDownload_SeededNotesCarryReasons(t *testing.T) {
	job := &DownloadJob{
		Digest: "INVALID_DIGEST",
		Notes:  []Note{{Reason: NoteCacheSetupFailed, Message: "cache unusable"}},
	}
	_ = job.DoDownload(context.Background())

	want := []NoteReason{NoteCacheSetupFailed}
	if got := job.Stats().NoteReasons; !slices.Equal(got, want) {
		t.Errorf("NoteReasons = %q, want %q", got, want)
	}
}

// TestDoDownload_RetryDoesNotCarryOverReasons pins that each attempt starts
// from the seeded notes only. A fallback attempt replaces the stats, so a
// reason from the failed attempt must not leak into the one that is reported.
func TestDoDownload_RetryDoesNotCarryOverReasons(t *testing.T) {
	job := &DownloadJob{
		Digest: "INVALID_DIGEST",
		Notes:  []Note{{Reason: NoteCacheSetupFailed, Message: "cache unusable"}},
	}
	_ = job.DoDownload(context.Background())
	job.addNote(NoteCacheWriteFailed, "only on the first attempt")
	_ = job.DoDownload(context.Background())

	want := []NoteReason{NoteCacheSetupFailed}
	if got := job.Stats().NoteReasons; !slices.Equal(got, want) {
		t.Errorf("NoteReasons = %q, want %q", got, want)
	}
}

func TestSanitizeNote(t *testing.T) {
	tests := []struct {
		in, want string
	}{
		{in: "plain", want: "plain"},
		{in: "a|b||c", want: "a_b__c"},
		{in: "a\nb", want: "a b"},
		{in: "a\r\nb", want: "a b"},
		{in: "a\rb", want: "a b"},
	}
	for _, tc := range tests {
		if got := sanitizeNote(tc.in); got != tc.want {
			t.Errorf("sanitizeNote(%q) = %q, want %q", tc.in, got, tc.want)
		}
	}
}
