import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Output,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import {rxResource} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltip, MatTooltipModule} from '@angular/material/tooltip';

import {Observable, of, throwError} from 'rxjs';
import {catchError} from 'rxjs/operators';

import {FileExplorer, FileInfo} from '../../../core/models/common_models';
import {useCopyToClipboard} from '../../../shared/composables/copy';
import {SnackBarService} from '../../../shared/services/snackbar_service';

/** A structure representing a hierarchical directory or checkable file inside the file explorer. */
export interface FileNode {
  name: string;
  type: 'dir' | 'file';
  path: string; // unique hierarchical path, e.g. "google3/java/com"
  size?: number;
  depth: number;
  children: FileNode[];
}

/**
 * Component for rendering the files tab content.
 * Reusable across jobs, tests, and sessions.
 */
@Component({
  selector: 'app-files-tab',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, MatTooltipModule],
  templateUrl: './files_tab.ng.html',
  styleUrl: './files_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FilesTab {
  readonly id = input.required<string>();
  readonly fileExplorer = input.required<FileExplorer>();
  readonly getFileContent =
    input.required<(path: string) => Observable<string>>();
  readonly emptyMessage = input<string>('No files associated.');

  @Output() readonly download = new EventEmitter<{
    path: string;
    event: Event;
  }>();
  @Output() readonly downloadAll = new EventEmitter<void>();

  readonly copyToClipboard = useCopyToClipboard();
  private readonly snackBar = inject(SnackBarService);

  readonly viewMode = signal<'tree' | 'flat'>('flat');
  readonly searchTerm = signal<string>('');

  readonly activeFile = signal<FileInfo | null>(null);

  private readonly MAX_PREVIEW_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
  private readonly collator = new Intl.Collator(undefined, {
    numeric: true,
    sensitivity: 'base',
  });

  readonly isFileTooLarge = computed(() => {
    const file = this.activeFile();
    if (!file) return false;
    return !!file.size && file.size > this.MAX_PREVIEW_SIZE_BYTES;
  });

  private shouldPreviewFileContent(file: FileInfo): boolean {
    if (file.size && file.size > this.MAX_PREVIEW_SIZE_BYTES) {
      return false;
    }
    if (this.isImageFile(file)) {
      return false;
    }
    return this.isPreviewable(file);
  }

  /**
   * RxResource managing the lifecycle and auto-unsubscribing when parameters change.
   */
  readonly fileContentResource = rxResource({
    params: () => {
      const file = this.activeFile();
      if (!file || !this.shouldPreviewFileContent(file)) {
        return null;
      }
      return {path: file.path};
    },
    stream: ({params}) => {
      if (!params) return of('');
      return this.getFileContent()(params.path).pipe(
        catchError((err: unknown) => {
          console.error(err);
          const e = err as {status?: number; message?: string};
          if (
            e?.status === 413 ||
            (e?.message && e.message.toLowerCase().includes('too large'))
          ) {
            return throwError(() => new Error('TOO_LARGE'));
          }
          return of(`Error loading file: ${e?.message || err}`);
        }),
      );
    },
  });

  readonly loadingContent = computed(() =>
    this.fileContentResource.isLoading(),
  );

  readonly activeFileContent = computed(() => {
    const file = this.activeFile();
    if (!file || this.isFileTooLarge() || this.isImageFile(file)) return '';
    const error = this.fileContentResource.error() as Error | undefined;
    if (error?.message === 'TOO_LARGE') return '';
    const value = this.fileContentResource.value();
    return typeof value === 'string' ? value : '';
  });

  readonly sizeLoadError = computed(() => {
    const error = this.fileContentResource.error() as Error | undefined;
    return error?.message === 'TOO_LARGE';
  });

  readonly expandedFolders = signal<Record<string, boolean>>({});
  readonly copiedCnsPath = signal<boolean>(false);

  readonly hasFiles = computed(() => {
    const list = this.fileExplorer()?.files;
    return !!list && list.length > 0;
  });

  // Cached file tree structure
  private readonly fileTree = computed(() => {
    const rawFiles = this.fileExplorer()?.files || [];
    return this.buildTree(rawFiles);
  });

  // Flat list of files for flat view, filtered by query
  readonly filteredFlatFiles = computed(() => {
    const rawFiles = this.fileExplorer()?.files || [];
    const query = this.searchTerm().toLowerCase().trim();
    if (!query) return rawFiles;
    return rawFiles.filter((f) => f.path.toLowerCase().includes(query));
  });

  readonly isSplitView = computed(() => !!this.activeFile());

  readonly isUnsupported = computed(() => {
    const file = this.activeFile();
    if (!file) return false;
    return !this.isPreviewable(file) && !this.isImageFile(file);
  });

  // Visible nodes in the tree view (filtered by expansion)
  readonly visibleTreeNodes = computed(() => {
    const tree = this.fileTree();
    const result: FileNode[] = [];
    const expanded = this.expandedFolders();

    const traverse = (nodes: FileNode[]) => {
      for (const n of nodes) {
        result.push(n);
        const isExpandedDir = n.type === 'dir' && expanded[n.path] !== false;
        if (isExpandedDir) {
          traverse(n.children);
        }
      }
    };
    traverse(tree);
    return result;
  });

  setViewMode(mode: 'tree' | 'flat') {
    this.searchTerm.set('');
    this.viewMode.set(mode);
  }

  onSearchTermChange(term: string) {
    this.searchTerm.set(term);
    if (term.trim()) {
      // Search forces flat view for best user experience
      this.viewMode.set('flat');
    }
  }

  toggleFolder(node: FileNode, event: Event) {
    event.stopPropagation();
    const current = this.expandedFolders()[node.path] !== false;
    this.expandedFolders.update((map) => ({
      ...map,
      [node.path]: !current,
    }));
  }

  selectFile(file: FileInfo) {
    this.activeFile.set(file);
  }

  closeFileViewer() {
    this.activeFile.set(null);
  }

  formatBytes(bytes?: number): string {
    if (bytes === undefined || bytes === null) return 'unknown size';
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${Number((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
  }

  copyCnsPath(cnsPath: string) {
    this.copyToClipboard(cnsPath, 'CNS path copied to clipboard!');
    this.copiedCnsPath.set(true);
    setTimeout(() => {
      this.copiedCnsPath.set(false);
    }, 2000);
  }

  downloadFile(file: FileInfo, event: Event) {
    event.stopPropagation();
    event.preventDefault();
    if (this.download.observed) {
      this.download.emit({path: file.path, event});
    } else {
      this.snackBar.showSuccess(`Downloading ${file.path}...`);
    }
  }

  downloadAllZip() {
    if (this.downloadAll.observed) {
      this.downloadAll.emit();
    } else {
      this.snackBar.showSuccess('Downloading all files as ZIP...');
    }
  }

  getFileIcon(fileName?: string): string {
    const name = (fileName || '').toLowerCase();
    if (/\.(png|jpe?g|gif|webp|bmp|svg)$/.test(name)) {
      return 'image';
    }
    if (/\.(ya?ml|json|txt|config|conf|ini|textproto|pbtxt)$/.test(name)) {
      return 'description';
    }
    if (/\.log$/.test(name)) {
      return 'article';
    }
    if (/\.(zip|tar|gz|jar)$/.test(name)) {
      return 'archive';
    }
    return 'insert_drive_file';
  }

  isPreviewable(file: FileInfo): boolean {
    return !!file.viewable;
  }

  getFileMetaString(size?: number): string | null {
    if (size === undefined || size === null) {
      return null;
    }
    return this.formatBytes(size);
  }

  checkOverflowTooltip(element: HTMLElement, tooltip: MatTooltip) {
    tooltip.disabled = element.scrollWidth <= element.clientWidth;
  }

  isImageFile(file: FileInfo): boolean {
    return /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(file.path);
  }

  private buildTree(files: FileInfo[]): FileNode[] {
    const nodesMap = new Map<string, FileNode>();
    const rootNodes: FileNode[] = [];

    const getOrCreateDir = (path: string): FileNode => {
      const existing = nodesMap.get(path);
      if (existing) {
        return existing;
      }

      const lastSlash = path.lastIndexOf('/');
      const name = lastSlash === -1 ? path : path.substring(lastSlash + 1);

      let depth = 0;
      let parentNode: FileNode | null = null;

      if (lastSlash !== -1) {
        const parentPath = path.substring(0, lastSlash);
        parentNode = getOrCreateDir(parentPath);
        depth = parentNode.depth + 1;
      }

      const dirNode: FileNode = {
        name,
        type: 'dir',
        path,
        depth,
        children: [],
      };

      nodesMap.set(path, dirNode);

      if (parentNode) {
        parentNode.children.push(dirNode);
      } else {
        rootNodes.push(dirNode);
      }

      return dirNode;
    };

    for (const file of files) {
      const lastSlash = file.path.lastIndexOf('/');
      const name =
        lastSlash === -1 ? file.path : file.path.substring(lastSlash + 1);
      const depth = lastSlash === -1 ? 0 : file.path.split('/').length - 1;

      const fileNode: FileNode = {
        name,
        type: 'file',
        path: file.path,
        size: file.size,
        depth,
        children: [],
      };

      if (lastSlash === -1) {
        rootNodes.push(fileNode);
      } else {
        const parentPath = file.path.substring(0, lastSlash);
        const parentDir = getOrCreateDir(parentPath);
        parentDir.children.push(fileNode);
      }
    }

    const sortTree = (nodes: FileNode[]) => {
      nodes.sort((a, b) => {
        if (a.type !== b.type) {
          return a.type === 'dir' ? -1 : 1;
        }
        return this.collator.compare(a.name, b.name);
      });
      for (const node of nodes) {
        if (node.type === 'dir') {
          sortTree(node.children);
        }
      }
    };

    sortTree(rootNodes);
    return rootNodes;
  }
}
