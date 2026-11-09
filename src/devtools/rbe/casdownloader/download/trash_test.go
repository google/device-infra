package download

import (
	"errors"
	"os"
	"path/filepath"
	"syscall"
	"testing"
	"time"
)

func TestRemoveChunkTrash(t *testing.T) {
	t.Run("removes every entry and keeps the directory", func(t *testing.T) {
		trashDir := filepath.Join(t.TempDir(), ChunkTrashDirName)
		for _, p := range []string{"run1/a", "run1/sub/b", "run2/c", "stray"} {
			path := filepath.Join(trashDir, p)
			if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
				t.Fatal(err)
			}
			if err := os.WriteFile(path, []byte(p), 0o400); err != nil {
				t.Fatal(err)
			}
		}
		if err := RemoveChunkTrash(trashDir); err != nil {
			t.Fatalf("RemoveChunkTrash failed: %v", err)
		}
		entries, err := os.ReadDir(trashDir)
		if err != nil {
			t.Fatalf("Trash directory is gone after RemoveChunkTrash: %v", err)
		}
		if len(entries) != 0 {
			t.Errorf("Trash directory holds %d entries after RemoveChunkTrash, want 0", len(entries))
		}
	})

	t.Run("missing directory", func(t *testing.T) {
		if err := RemoveChunkTrash(filepath.Join(t.TempDir(), ChunkTrashDirName)); err != nil {
			t.Errorf("RemoveChunkTrash of a missing directory = %v, want nil", err)
		}
	})

	t.Run("refuses other names", func(t *testing.T) {
		dir := t.TempDir()
		keep := filepath.Join(dir, "keep")
		if err := os.WriteFile(keep, []byte("keep"), 0o600); err != nil {
			t.Fatal(err)
		}
		if err := RemoveChunkTrash(dir); err == nil {
			t.Errorf("RemoveChunkTrash(%s) succeeded, want an error for a directory not named %s", dir, ChunkTrashDirName)
		}
		if _, err := os.Stat(keep); err != nil {
			t.Errorf("File in a refused directory was removed: %v", err)
		}
	})

	t.Run("serializes on advisory flock", func(t *testing.T) {
		trashDir := filepath.Join(t.TempDir(), ChunkTrashDirName)
		entry := filepath.Join(trashDir, "run1", "chunk")
		if err := os.MkdirAll(filepath.Dir(entry), 0o700); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(entry, []byte("data"), 0o400); err != nil {
			t.Fatal(err)
		}

		lockDir, err := os.Open(trashDir)
		if err != nil {
			t.Fatal(err)
		}
		defer lockDir.Close()
		if err := syscall.Flock(int(lockDir.Fd()), syscall.LOCK_EX|syscall.LOCK_NB); err != nil {
			t.Fatalf("Flock failed: %v", err)
		}

		// While the lock is held, a probe sees a removal in progress, and a
		// non-waiting removal gives up at once.
		if busy, err := ChunkTrashRemovalInProgress(trashDir); err != nil || !busy {
			t.Errorf("ChunkTrashRemovalInProgress while locked = %v, %v, want true, nil", busy, err)
		}
		if err := TryRemoveChunkTrash(trashDir); !errors.Is(err, ErrChunkTrashBusy) {
			t.Errorf("TryRemoveChunkTrash while locked = %v, want ErrChunkTrashBusy", err)
		}
		if _, err := os.Stat(entry); err != nil {
			t.Fatalf("entry removed by a non-waiting removal: %v", err)
		}

		done := make(chan error, 1)
		go func() {
			done <- RemoveChunkTrash(trashDir)
		}()

		select {
		case err := <-done:
			t.Fatalf("RemoveChunkTrash returned while flock was held: %v", err)
		case <-time.After(30 * time.Millisecond):
		}
		if _, err := os.Stat(entry); err != nil {
			t.Fatalf("entry removed while flock was held: %v", err)
		}

		if err := syscall.Flock(int(lockDir.Fd()), syscall.LOCK_UN); err != nil {
			t.Fatalf("Flock unlock failed: %v", err)
		}
		if err := <-done; err != nil {
			t.Fatalf("RemoveChunkTrash failed after unlock: %v", err)
		}
		entries, err := os.ReadDir(trashDir)
		if err != nil {
			t.Fatal(err)
		}
		if len(entries) != 0 {
			t.Errorf("Trash directory holds %d entries after RemoveChunkTrash, want 0", len(entries))
		}
		if busy, err := ChunkTrashRemovalInProgress(trashDir); err != nil || busy {
			t.Errorf("ChunkTrashRemovalInProgress after removal = %v, %v, want false, nil", busy, err)
		}
	})

	t.Run("probe of a missing directory is not in progress", func(t *testing.T) {
		busy, err := ChunkTrashRemovalInProgress(filepath.Join(t.TempDir(), ChunkTrashDirName))
		if err != nil || busy {
			t.Errorf("ChunkTrashRemovalInProgress of a missing directory = %v, %v, want false, nil", busy, err)
		}
	})

	t.Run("bounded passes", func(t *testing.T) {
		if maxTrashPasses < 1 {
			t.Fatalf("maxTrashPasses = %d, want at least 1", maxTrashPasses)
		}
		trashDir := filepath.Join(t.TempDir(), ChunkTrashDirName)
		if err := os.MkdirAll(filepath.Join(trashDir, "run1"), 0o700); err != nil {
			t.Fatal(err)
		}
		// Each pass finds a new entry; the removal must still stop after
		// maxTrashPasses rather than chasing arrivals indefinitely.
		added := 0
		origRemove := removeAllTrashEntry
		t.Cleanup(func() { removeAllTrashEntry = origRemove })
		removeAllTrashEntry = func(path string) error {
			added++
			if err := os.MkdirAll(filepath.Join(trashDir, "late"+string(rune('a'+added))), 0o700); err != nil {
				t.Fatal(err)
			}
			return origRemove(path)
		}
		if err := RemoveChunkTrash(trashDir); err != nil {
			t.Fatalf("RemoveChunkTrash failed: %v", err)
		}
		entries, err := os.ReadDir(trashDir)
		if err != nil {
			t.Fatal(err)
		}
		if len(entries) == 0 {
			t.Errorf("RemoveChunkTrash kept up with every arrival; want it to stop after %d passes and leave the latest", maxTrashPasses)
		}
		if added > maxTrashPasses*2 {
			t.Errorf("RemoveChunkTrash removed %d entries over %d passes, want it bounded", added, maxTrashPasses)
		}
	})
}

func TestChunkTrashBacklog(t *testing.T) {
	t.Run("missing directory", func(t *testing.T) {
		count, oldest, err := ChunkTrashBacklog(filepath.Join(t.TempDir(), ChunkTrashDirName))
		if err != nil || count != 0 || oldest != 0 {
			t.Errorf("ChunkTrashBacklog of a missing directory = %d, %v, %v, want 0, 0, nil", count, oldest, err)
		}
	})

	t.Run("counts entries and ages the oldest", func(t *testing.T) {
		trashDir := filepath.Join(t.TempDir(), ChunkTrashDirName)
		for _, name := range []string{"run1", "run2", "run3"} {
			if err := os.MkdirAll(filepath.Join(trashDir, name), 0o700); err != nil {
				t.Fatal(err)
			}
		}
		old := time.Now().Add(-3 * time.Hour)
		if err := os.Chtimes(filepath.Join(trashDir, "run2"), old, old); err != nil {
			t.Fatal(err)
		}
		count, oldest, err := ChunkTrashBacklog(trashDir)
		if err != nil {
			t.Fatalf("ChunkTrashBacklog failed: %v", err)
		}
		if count != 3 {
			t.Errorf("count = %d, want 3", count)
		}
		if oldest < 3*time.Hour-time.Minute || oldest > 3*time.Hour+time.Minute {
			t.Errorf("oldest = %v, want about 3h", oldest)
		}
	})
}
