// Package fuse provides a FUSE filesystem for FastCDC.
package fuse

import (
	"context"
	"io"
	"os"
	"path/filepath"
	"strings"
	"syscall"
	"time"

	"google3/third_party/golang/gofuse/fs/fs"
	"google3/third_party/golang/gofuse/fuse/fuse"

	"github.com/google/device-infra/src/devtools/rbe/casviewer/chunkstore"
)

// FastCDCFS represents a FUSE filesystem for FastCDC.
type FastCDCFS struct {
	fs.Inode
	store     *chunkstore.ChunkStore
	mountTime time.Time // Store the mount time for consistent timestamps
}

// Ensure FastCDCFS itself implements NodeGetattrer for the root directory attributes
var _ = (fs.NodeGetattrer)((*FastCDCFS)(nil))

// NewFastCDCFS creates a new FastCDCFS.
func NewFastCDCFS(store *chunkstore.ChunkStore) *FastCDCFS {
	return &FastCDCFS{
		store:     store,
		mountTime: time.Now(), // Set mount time when the FS is created
	}
}

// Getattr for the root directory node itself (FastCDCFS)
func (f *FastCDCFS) Getattr(ctx context.Context, fh fs.FileHandle, out *fuse.AttrOut) syscall.Errno {
	out.Mode = fuse.S_IFDIR | 0755 // Directory with rwxr-xr-x
	out.Nlink = 2
	out.Uid = uint32(os.Getuid())
	out.Gid = uint32(os.Getgid())

	// Use the stored mountTime for consistency
	mt := uint64(f.mountTime.Unix())
	out.Atime = mt
	out.Mtime = mt
	out.Ctime = mt
	// out.Size = 0 // Or let go-fuse handle it.
	return 0
}

// Mount creates a new FUSE server for the FastCDCFS filesystem.
func (f *FastCDCFS) Mount(mountPoint string) (*fuse.Server, error) {
	attrTimeout := time.Second
	entryTimeout := time.Second

	opts := &fs.Options{
		AttrTimeout:     &attrTimeout,
		EntryTimeout:    &entryTimeout,
		NullPermissions: false, // since we are setting permissions
	}

	server, err := fs.Mount(mountPoint, f, opts)
	if err != nil {
		return nil, err
	}

	return server, nil
}

func splitPath(path string) []string {
	cleaned := filepath.Clean(path)
	if cleaned == "." || cleaned == "" { // Handle root or empty path case
		return []string{}
	}
	return strings.Split(cleaned, string(filepath.Separator))
}

// OnAdd is called when the filesystem is mounted.
func (f *FastCDCFS) OnAdd(ctx context.Context) {
	// Build the directory structure from the store
	for _, file := range f.store.GetFiles() {
		// The `file.Path` from store is the full path within the virtual FS.
		components := splitPath(file.Path)
		parent := &f.Inode // This is the root Inode of FastCDCFS.
		name := ""

		// Traverse or create parent directories
		for i, component := range components {
			if component == "" {
				continue
			}

			if i == len(components)-1 { // Last component is the file/dir name itself
				name = component
				break // Parent is found
			}

			child := parent.GetChild(component)
			if child == nil {
				childInode := parent.NewPersistentInode(
					ctx,
					&fs.Inode{}, // Generic inode for a directory
					fs.StableAttr{Mode: fuse.S_IFDIR},
				)
				parent.AddChild(component, childInode, false)
				child = childInode
			}
			parent = child
		}

		// Skip root-like paths
		if file.Path == "" || name == "" {
			continue
		}

		// Check if child already exists (e.g. if a dir was implicitly created)
		if existingChild := parent.GetChild(name); existingChild != nil {
			continue
		}

		// Create file inode
		fileNode := &FastCDCFile{
			store: f.store,
			path:  file.Path,
		}

		// The StableAttr here is for the *file node*, its Getattr will provide full details.
		childInode := parent.NewPersistentInode(
			ctx,
			fileNode,
			fs.StableAttr{Mode: uint32(file.Mode)}, // Set initial type for lookup
		)
		parent.AddChild(name, childInode, false)
	}
}

// FastCDCFile represents a file in the FastCDCFS filesystem.
type FastCDCFile struct {
	fs.Inode
	store *chunkstore.ChunkStore
	path  string
}

var _ = (fs.NodeOpener)((*FastCDCFile)(nil))
var _ = (fs.NodeGetattrer)((*FastCDCFile)(nil))
var _ = (fs.NodeReader)((*FastCDCFile)(nil))

// Getattr for a file node
func (f *FastCDCFile) Getattr(ctx context.Context, fh fs.FileHandle, out *fuse.AttrOut) syscall.Errno {
	file, err := f.store.GetFile(f.path)
	if err != nil {
		return syscall.ENOENT
	}

	out.Mode = uint32(file.Mode)
	out.Size = uint64(file.Size)
	out.Mtime = uint64(file.ModTime.Unix())
	out.Atime = out.Mtime
	out.Ctime = out.Mtime
	out.Uid = uint32(os.Getuid())
	out.Gid = uint32(os.Getgid())
	return 0
}

// Open for a file node
func (f *FastCDCFile) Open(ctx context.Context, flags uint32) (fh fs.FileHandle, fuseFlags uint32, errno syscall.Errno) {
	// Only allow read access
	if flags&(syscall.O_WRONLY|syscall.O_RDWR|syscall.O_CREAT|syscall.O_TRUNC) != 0 {
		return nil, 0, syscall.EROFS
	}
	return nil, fuse.FOPEN_KEEP_CACHE, 0 // Beneficial for read-only.
}

// Read for a file node
func (f *FastCDCFile) Read(ctx context.Context, fh fs.FileHandle, dest []byte, off int64) (fuse.ReadResult, syscall.Errno) {
	if len(dest) == 0 {
		return fuse.ReadResultData(nil), 0
	}
	n, err := f.store.ReadFileToDest(f.path, dest, off)
	if err != nil && err != io.EOF {
		return nil, syscall.EIO
	}
	return fuse.ReadResultData(dest[:n]), 0
}
