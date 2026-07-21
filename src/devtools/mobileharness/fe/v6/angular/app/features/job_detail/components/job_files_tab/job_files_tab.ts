import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import {rxResource} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltip, MatTooltipModule} from '@angular/material/tooltip';
import {of, throwError} from 'rxjs';
import {catchError, map} from 'rxjs/operators';

import {useCopyToClipboard} from '@deviceinfra/app/shared/composables/copy';
import {FileExplorer, JobFile} from '../../../../core/models/job_overview';
import {JOB_SERVICE} from '../../../../core/services/job/job_service';
import {SnackBarService} from '../../../../shared/services/snackbar_service';

/** A structure representing a hierarchical directory or checkable file inside the job explorer. */
export interface FileNode {
  name: string;
  type: 'dir' | 'file';
  path: string; // unique hierarchical path, e.g. "google3/java/com"
  size?: number;
  typeStr?: string;
  depth: number;
  children: FileNode[];
}

/** Component for rendering the job files tab content. */
@Component({
  selector: 'app-job-files-tab',
  standalone: true,
  imports: [FormsModule, MatIconModule, MatTooltipModule],
  templateUrl: './job_files_tab.ng.html',
  styleUrl: './job_files_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JobFilesTab {
  readonly jobId = input.required<string>();
  readonly fileExplorer = input.required<FileExplorer>();

  private readonly jobService = inject(JOB_SERVICE);
  readonly copyToClipboard = useCopyToClipboard();
  private readonly snackBar = inject(SnackBarService);

  readonly viewMode = signal<'tree' | 'flat'>('flat');
  readonly searchTerm = signal<string>('');

  readonly activeFile = signal<JobFile | null>(null);

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

  private shouldPreviewFileContent(file: JobFile): boolean {
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
      const jobId = this.jobId();
      if (!file || !this.shouldPreviewFileContent(file)) {
        return null;
      }
      return {jobId, path: file.path};
    },
    stream: ({params}) => {
      if (!params) return of('');
      return this.jobService.getJobFile(params.jobId, params.path).pipe(
        map((resp) => resp.content || ''),
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
    return typeof this.fileContentResource.value() === 'string'
      ? this.fileContentResource.value()!
      : '';
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

  selectFile(file: JobFile) {
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

  downloadFile(file: JobFile, event: Event) {
    event.stopPropagation();
    event.preventDefault();
    // TODO: Implement downloadFile.
    this.snackBar.showSuccess(`Downloading ${file.path}...`);
  }

  downloadAllZip() {
    // TODO: Implement downloadAllZip.
    this.snackBar.showSuccess('Downloading all files as ZIP...');
  }

  getFileIcon(typeStr?: string): string {
    if (!typeStr) return 'insert_drive_file';
    const type = typeStr.toLowerCase();
    if (
      type.includes('png') ||
      type.includes('jpg') ||
      type.includes('jpeg') ||
      type.includes('image')
    ) {
      return 'image';
    }
    if (
      type.includes('yaml') ||
      type.includes('yml') ||
      type.includes('json') ||
      type.includes('text/plain') ||
      type.includes('config')
    ) {
      return 'description';
    }
    if (type.includes('log')) {
      return 'article';
    }
    if (type.includes('zip') || type.includes('archive')) {
      return 'archive';
    }
    return 'insert_drive_file';
  }

  isPreviewable(file: JobFile): boolean {
    const type = file.type?.toLowerCase() || '';
    return (
      type.includes('yaml') ||
      type.includes('yml') ||
      type.includes('json') ||
      type.includes('text/plain') ||
      type.includes('log') ||
      type.includes('config') ||
      type.includes('image') ||
      type.includes('png') ||
      type.includes('jpg') ||
      type.includes('jpeg')
    );
  }

  getFileMetaString(typeStr?: string, size?: number): string | null {
    const parts: string[] = [];
    const trimmedType = typeStr?.trim();
    if (trimmedType) {
      parts.push(trimmedType);
    }
    if (size !== undefined && size !== null) {
      parts.push(this.formatBytes(size));
    }
    return parts.length ? parts.join(' • ') : null;
  }

  checkOverflowTooltip(element: HTMLElement, tooltip: MatTooltip) {
    tooltip.disabled = element.scrollWidth <= element.clientWidth;
  }

  isImageFile(file: JobFile): boolean {
    const type = file.type?.toLowerCase() || '';
    return (
      type.includes('image') ||
      type.includes('png') ||
      type.includes('jpg') ||
      type.includes('jpeg')
    );
  }

  private buildTree(files: JobFile[]): FileNode[] {
    const nodesMap = new Map<string, FileNode>();
    const rootNodes: FileNode[] = [];

    const getOrCreateDir = (path: string): FileNode => {
      if (nodesMap.has(path)) {
        return nodesMap.get(path)!;
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
        typeStr: file.type || '',
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
