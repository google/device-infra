package download

import (
	"context"
	"strings"
	"testing"
)

// TestAddNote_SeparatorIsUnambiguous pins that the notes field can be split
// back into exactly the notes that were added. Notes embed raw error text and
// paths, either of which can contain the separator's "|" or a line break.
func TestAddNote_SeparatorIsUnambiguous(t *testing.T) {
	job := &DownloadJob{DownloadStats: &Stats{}}
	job.addNote("first: %v", "a|b")
	job.addNote("second:\n%s", "line two\r\nline three")

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
		Notes:  []string{"cache at /a|b unusable", "second\nline"},
	}
	_ = job.DoDownload(context.Background())

	if want := "cache at /a_b unusable | second line"; job.Stats().Notes != want {
		t.Errorf("Notes = %q, want %q", job.Stats().Notes, want)
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
