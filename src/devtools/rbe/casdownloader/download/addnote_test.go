package download

import (
	"strings"
	"testing"
)

// TestAddNote_Accumulates pins the property that makes the notes field usable
// by more than one caller.
//
// It used to have a single writer that assigned to it. A second writer that did
// the same would erase the first, and the loss would be silent and dependent on
// ordering: the run still succeeds, the note is still present, it is just not
// the whole story. Anything reported this way is by definition not important
// enough to fail the run, which is exactly why it cannot afford to be dropped.
func TestAddNote_Accumulates(t *testing.T) {
	d := &DownloadJob{DownloadStats: &Stats{}}

	d.addNote(NoteCacheWriteFailed, "first thing worth saying")
	d.addNote(NoteCacheWriteFailed, "second thing, about %d files", 3)

	got := d.DownloadStats.Notes
	for _, want := range []string{"first thing worth saying", "second thing, about 3 files"} {
		if !strings.Contains(got, want) {
			t.Errorf("Notes = %q, want it to contain %q", got, want)
		}
	}
}

// TestAddNote_ToleratesMissingStats keeps the reporting path from becoming a
// way to crash. A note is never the reason a run matters, so failing to have
// somewhere to put one must not take the run down with it.
func TestAddNote_ToleratesMissingStats(t *testing.T) {
	(&DownloadJob{}).addNote(NoteCacheWriteFailed, "nowhere to record this")
}
