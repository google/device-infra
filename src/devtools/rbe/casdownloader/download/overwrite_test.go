package download

import (
	"context"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"syscall"
	"testing"
	"time"
)

// replacedNotes returns the notes of the job's last run that report replaced
// files.
func replacedNotes(job *DownloadJob) []string {
	var notes []string
	msgs := strings.Split(job.DownloadStats.Notes, noteSeparator)
	for i, r := range job.DownloadStats.NoteReasons {
		if r == NoteExistingFilesReplaced {
			notes = append(notes, msgs[i])
		}
	}
	return notes
}

// TestDoDownload_NotesReplacedFiles checks that a download into a fresh Dir
// records no note, and one that replaces files records one note with how many
// and a few of their paths.
func TestDoDownload_NotesReplacedFiles(t *testing.T) {
	for _, cv := range cacheVariants {
		t.Run(cv.name, func(t *testing.T) {
			files := map[string][]byte{
				"a.txt":     []byte("a"),
				"b.txt":     []byte("b"),
				"c.txt":     []byte("c"),
				"d.txt":     []byte("d"),
				"sub/e.txt": []byte("e"),
			}
			job := newTreeJob(t, files)
			c := cv.cache(t)

			job.Cache = c
			if err := job.DoDownload(context.Background()); err != nil {
				t.Fatalf("DoDownload into a fresh Dir failed: %v", err)
			}
			if notes := replacedNotes(job); len(notes) != 0 {
				t.Errorf("DoDownload into a fresh Dir noted %q, want no note about replaced files", notes)
			}

			// Download again over the first download, warm if there is a
			// cache, so that every file is replaced, some by a link to the
			// inode already there.
			job.Cache = c
			if err := job.DoDownload(context.Background()); err != nil {
				t.Fatalf("Second DoDownload failed: %v", err)
			}
			notes := replacedNotes(job)
			if len(notes) != 1 {
				t.Fatalf("Second DoDownload noted %q, want one note about replaced files", notes)
			}
			if !strings.HasPrefix(notes[0], "Replaced 5 existing files") {
				t.Errorf("Note = %q, want it to say 5 files were replaced", notes[0])
			}
			// Paths are sorted, and only the first few are named.
			if !strings.Contains(notes[0], "such as a.txt, b.txt, c.txt;") {
				t.Errorf("Note = %q, want it to name a.txt, b.txt and c.txt, relative to Dir, and no more", notes[0])
			}
			for path, want := range files {
				if got := readFile(t, filepath.Join(job.Dir, path)); got != string(want) {
					t.Errorf("%s = %q, want %q", path, got, want)
				}
			}
			assertNoTmpFiles(t, job.Dir)
		})
	}
}

// TestDoDownload_DisableOverwrite downloads, with overwriting disabled, a tree
// one of whose paths is taken. The download fails before it fetches anything,
// which would fail on the missing blob, and leaves Dir as it was.
func TestDoDownload_DisableOverwrite(t *testing.T) {
	for _, cv := range cacheVariants {
		t.Run(cv.name, func(t *testing.T) {
			job := newTreeJob(t, map[string][]byte{
				"a.txt":          []byte("new a"),
				"new/deep/b.txt": []byte("new b"),
				"z.txt":          []byte("never uploaded"),
			}, "z.txt")
			job.Cache = cv.cache(t)
			job.DisableOverwrite = true
			writeFile(t, filepath.Join(job.Dir, "a.txt"), "old a", 0o600)

			err := job.DoDownload(context.Background())
			if err == nil || !strings.Contains(err.Error(), "a.txt already exists") || !strings.Contains(err.Error(), "-disable-overwrite") {
				t.Fatalf("DoDownload = %v, want an error saying a.txt already exists and naming -disable-overwrite", err)
			}
			if job.RemoteFailed() {
				t.Error("RemoteFailed() = true, want false")
			}
			if got := readFile(t, filepath.Join(job.Dir, "a.txt")); got != "old a" {
				t.Errorf("a.txt = %q, want it unchanged", got)
			}
			if exists(t, filepath.Join(job.Dir, "new")) {
				t.Error("Directory new was created, want nothing written")
			}
			assertNoTmpFiles(t, job.Dir)
		})
	}
}

// TestDoDownload_DisableOverwriteFreshDir checks that disabling overwriting
// does not get in the way of a download into a fresh Dir.
func TestDoDownload_DisableOverwriteFreshDir(t *testing.T) {
	job := newTreeJob(t, map[string][]byte{"a.txt": []byte("a"), "sub/b.txt": []byte("b")})
	job.DisableOverwrite = true
	if err := job.DoDownload(context.Background()); err != nil {
		t.Fatalf("DoDownload into a fresh Dir failed: %v", err)
	}
	if got := readFile(t, filepath.Join(job.Dir, "sub", "b.txt")); got != "b" {
		t.Errorf("sub/b.txt = %q, want %q", got, "b")
	}
	if notes := replacedNotes(job); len(notes) != 0 {
		t.Errorf("Noted %q, want no note about replaced files", notes)
	}
}

// TestDoDownload_DisableOverwriteRestoredFile downloads, with overwriting
// disabled, a chunked artifact one of whose restored paths is taken. That is
// only known once the chunk data is fetched, so commit fails on it, and
// leaves the file there and nothing else.
func TestDoDownload_DisableOverwriteRestoredFile(t *testing.T) {
	tree := chunkedArtifact(t, time.Date(2020, 1, 2, 3, 4, 5, 0, time.UTC), map[string][]string{
		"a.img":     {"new a"},
		"sub/b.img": {"new b"},
	})
	job := newTreeJob(t, tree)
	job.DisableOverwrite = true
	writeFile(t, filepath.Join(job.Dir, "sub", "b.img"), "old b", 0o600)

	err := job.DoDownload(context.Background())
	if err == nil || !strings.Contains(err.Error(), "b.img already exists") {
		t.Fatalf("DoDownload = %v, want an error saying b.img already exists", err)
	}
	if got := readFile(t, filepath.Join(job.Dir, "sub", "b.img")); got != "old b" {
		t.Errorf("sub/b.img = %q, want it unchanged", got)
	}
	if exists(t, filepath.Join(job.Dir, "_chunks")) {
		t.Error("_chunks was left behind")
	}
	assertNoTmpFiles(t, job.Dir)
}

// newCommitTest returns a staging on a new Dir with overwriting disabled or
// not, and one file staged to dir/name with content "new".
func newCommitTest(t *testing.T, disableOverwrite bool, name string) (s *staging, tmp, final string) {
	t.Helper()
	s, err := newStaging(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	s.disableOverwrite = disableOverwrite
	final = filepath.Join(s.dir, name)
	tmp = s.tmpName(final)
	writeFile(t, tmp, "new", 0o600)
	s.add(tmp, final)
	return s, tmp, final
}

// TestStaging_CommitDetectsLateConflict puts a file at a final path after
// staging, as a concurrent writer could after the pre-fetch check. With
// overwriting disabled commit fails on it and keeps both files, for abort to
// remove the staged one; otherwise commit replaces it and records that.
func TestStaging_CommitDetectsLateConflict(t *testing.T) {
	t.Run("disabled", func(t *testing.T) {
		s, tmp, final := newCommitTest(t, true, "a.txt")
		writeFile(t, final, "old", 0o600)
		if err := s.commit(); err == nil || !strings.Contains(err.Error(), "already exists") {
			t.Fatalf("commit = %v, want an error saying a.txt already exists", err)
		}
		if got := readFile(t, final); got != "old" {
			t.Errorf("a.txt = %q, want it unchanged", got)
		}
		if len(s.replaced) != 0 {
			t.Errorf("replaced = %q, want none", s.replaced)
		}
		s.abort()
		if exists(t, tmp) {
			t.Error("abort left the staged file behind")
		}
	})
	t.Run("enabled", func(t *testing.T) {
		s, tmp, final := newCommitTest(t, false, "a.txt")
		writeFile(t, final, "old", 0o600)
		if err := s.commit(); err != nil {
			t.Fatalf("commit failed: %v", err)
		}
		if got := readFile(t, final); got != "new" {
			t.Errorf("a.txt = %q, want %q", got, "new")
		}
		if !slices.Equal(s.replaced, []string{final}) {
			t.Errorf("replaced = %q, want [%q]", s.replaced, final)
		}
		if exists(t, tmp) {
			t.Error("commit left the staged file behind")
		}
	})
}

// TestStaging_CommitSymlinks commits staged symlinks, one of them dangling,
// to free paths. They are moved as symlinks, not followed.
func TestStaging_CommitSymlinks(t *testing.T) {
	s, err := newStaging(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	s.disableOverwrite = true
	writeFile(t, filepath.Join(s.dir, "target"), "target", 0o600)
	for name, target := range map[string]string{"link": "target", "dangling": "missing"} {
		final := filepath.Join(s.dir, name)
		tmp := s.tmpName(final)
		if err := os.Symlink(target, tmp); err != nil {
			t.Fatal(err)
		}
		s.add(tmp, final)
	}
	if err := s.commit(); err != nil {
		t.Fatalf("commit failed: %v", err)
	}
	for name, want := range map[string]string{"link": "target", "dangling": "missing"} {
		if got, err := os.Readlink(filepath.Join(s.dir, name)); err != nil || got != want {
			t.Errorf("Readlink(%s) = %q, %v, want %q", name, got, err, want)
		}
	}
	if len(s.replaced) != 0 {
		t.Errorf("replaced = %q, want none", s.replaced)
	}
	assertNoTmpFiles(t, s.dir)
}

// TestStaging_CommitWithoutHardLinks commits where hard linking fails, as on
// a filesystem without hard links, so commit falls back to checking whether
// the final path is taken before renaming.
func TestStaging_CommitWithoutHardLinks(t *testing.T) {
	orig := linkNoReplace
	t.Cleanup(func() { linkNoReplace = orig })
	linkNoReplace = func(oldname, newname string) error {
		return &os.LinkError{Op: "link", Old: oldname, New: newname, Err: syscall.EPERM}
	}

	for _, tc := range []struct {
		name             string
		disableOverwrite bool
		existing         bool
		wantErr          bool
		wantContent      string
		wantReplaced     bool
	}{
		{name: "free path", wantContent: "new"},
		{name: "free path, overwrite disabled", disableOverwrite: true, wantContent: "new"},
		{name: "taken path", existing: true, wantContent: "new", wantReplaced: true},
		{name: "taken path, overwrite disabled", disableOverwrite: true, existing: true, wantErr: true, wantContent: "old"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			s, tmp, final := newCommitTest(t, tc.disableOverwrite, "a.txt")
			if tc.existing {
				writeFile(t, final, "old", 0o600)
			}
			err := s.commit()
			if gotErr := err != nil; gotErr != tc.wantErr {
				t.Fatalf("commit = %v, want error: %v", err, tc.wantErr)
			}
			if got := readFile(t, final); got != tc.wantContent {
				t.Errorf("a.txt = %q, want %q", got, tc.wantContent)
			}
			if got := len(s.replaced) > 0; got != tc.wantReplaced {
				t.Errorf("replaced = %q, want replaced: %v", s.replaced, tc.wantReplaced)
			}
			if !tc.wantErr && exists(t, tmp) {
				t.Error("commit left the staged file behind")
			}
		})
	}
}
