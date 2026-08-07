import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  output,
  signal,
} from '@angular/core';
import {rxResource} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';

import {Observable, of, throwError} from 'rxjs';
import {catchError} from 'rxjs/operators';

import {FileExplorer, FileInfo} from '../../../core/models/common_models';
import {useCopyToClipboard} from '../../../shared/composables/copy';
import {SnackBarService} from '../../../shared/services/snackbar_service';
import {openInNewTab} from '../../utils/safe_dom';

import {TooltipIfTruncatedDirective} from '../../directives/tooltip_if_truncated/tooltip_if_truncated';

/** A structure representing a hierarchical directory or checkable file inside the file explorer. */
export interface FileNode {
  name: string;
  type: 'dir' | 'file';
  path: string; // unique hierarchical path, e.g. "google3/java/com"
  size?: number;
  viewable?: boolean;
  depth: number;
  children: FileNode[];
}

/** Error detail class for file content loading failures. */
export class FileContentError extends Error {
  type: 'TOO_LARGE' | 'NOT_FOUND' | 'NETWORK_ERROR' | 'UNKNOWN';
  status?: number;

  constructor(
    type: 'TOO_LARGE' | 'NOT_FOUND' | 'NETWORK_ERROR' | 'UNKNOWN',
    message: string,
    status?: number,
  ) {
    super(message);
    this.type = type;
    this.status = status;
  }
}

/**
 * Component for rendering the files tab content.
 * Reusable across jobs, tests, and sessions.
 */
@Component({
  selector: 'app-files-tab',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
    MatTooltipModule,
    TooltipIfTruncatedDirective,
  ],
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

  readonly download = output<{
    path: string;
    event: Event;
  }>();

  readonly copyToClipboard = useCopyToClipboard();
  private readonly snackBar = inject(SnackBarService);

  readonly searchTerm = signal<string>('');

  readonly viewMode = linkedSignal<string, 'tree' | 'flat'>({
    source: () => this.searchTerm(),
    computation: (term, previous) => {
      if (term.trim()) {
        return 'flat';
      }
      return previous?.value ?? 'flat';
    },
  });

  readonly activeFile = linkedSignal<FileInfo | null>(() => {
    this.fileExplorer();
    return null;
  });

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

  /**
   * RxResource managing the lifecycle and auto-unsubscribing when parameters change.
   */
  readonly fileContentResource = rxResource({
    params: () => {
      const file = this.activeFile();
      if (!file || !this.isViewable(file)) {
        return null;
      }
      return {path: file.path};
    },
    stream: ({params}) => {
      if (!params) return of('');
      const getContentFn = this.getFileContent();
      if (!getContentFn) return of('');
      return getContentFn(params.path).pipe(
        catchError((err: unknown) => {
          console.error('Failed to fetch file content:', err);
          const e = err as {status?: number; message?: string};

          let errorObj: FileContentError;
          if (
            e?.status === 413 ||
            (e?.message &&
              (e.message.toLowerCase().includes('too large') ||
                e.message.toLowerCase().includes('larger than')))
          ) {
            errorObj = new FileContentError(
              'TOO_LARGE',
              'File exceeds maximum preview limit (10MB)',
              413,
            );
          } else if (e?.status === 404) {
            errorObj = new FileContentError(
              'NOT_FOUND',
              'File not found on server',
              404,
            );
          } else if (e?.status === 0 || !navigator.onLine) {
            errorObj = new FileContentError(
              'NETWORK_ERROR',
              'Network disconnected or request timed out',
              0,
            );
          } else {
            errorObj = new FileContentError(
              'UNKNOWN',
              e?.message ||
                (typeof err === 'string'
                  ? err
                  : 'An error occurred while loading file content.'),
              e?.status,
            );
          }

          return throwError(() => errorObj);
        }),
      );
    },
  });

  readonly loadingContent = computed(() =>
    this.fileContentResource.isLoading(),
  );

  readonly contentError = computed(() => {
    return this.fileContentResource.error() as FileContentError | undefined;
  });

  readonly sizeLoadError = computed(
    () => this.contentError()?.type === 'TOO_LARGE',
  );

  readonly isContentLoadError = computed(() => {
    const err = this.contentError();
    return !!err && err.type !== 'TOO_LARGE';
  });

  readonly errorMessage = computed(() => {
    return this.contentError()?.message || 'Failed to load file content.';
  });

  readonly activeFileContent = computed(() => {
    if (!this.activeFile() || this.cannotPreviewInline()) {
      return '';
    }
    const value = this.fileContentResource.value();
    return typeof value === 'string' ? value : '';
  });

  retryLoadContent() {
    this.fileContentResource.reload();
  }

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

  /**
   * Whether a file is a text/log file that can be previewed inline in FE v6.
   * Computed by backend (GenFileViewability: size <= 10MB and non-binary type).
   */
  isViewable(file: {viewable?: boolean} | null | undefined): boolean {
    return !!file?.viewable;
  }

  /**
   * Whether a file is an image file based on extension.
   */
  isImageFile(file: {path: string}): boolean {
    return /\.(png|jpe?g|gif|webp)$/i.test(file.path);
  }

  /**
   * Whether the active file is unsupported for inline preview in FE v6 console.
   */
  readonly isUnsupported = computed(() => {
    const file = this.activeFile();
    if (!file) return false;
    return !this.isViewable(file);
  });

  /**
   * Whether the active file cannot be previewed inline in the viewport.
   */
  readonly cannotPreviewInline = computed(() => {
    return this.isUnsupported() || !!this.contentError();
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

  getFileMetaString(size?: number): string | null {
    if (size !== undefined && size !== null) {
      return this.formatBytes(size);
    }
    return null;
  }

  getFileIcon(path: string): string {
    if (this.isImageFile({path})) {
      return 'image';
    }
    if (/\.(ya?ml|json|txt|config|conf|ini|textproto|pbtxt)$/i.test(path)) {
      return 'description';
    }
    if (/\.log$/i.test(path)) {
      return 'article';
    }
    if (/\.(zip|tar|gz|jar)$/i.test(path)) {
      return 'archive';
    }
    return 'insert_drive_file';
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
        viewable: file.viewable,
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
