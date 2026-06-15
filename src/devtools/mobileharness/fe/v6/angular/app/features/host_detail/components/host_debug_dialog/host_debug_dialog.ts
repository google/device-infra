/**
 * @fileoverview Dialog component displaying detailed host debug diagnostics.
 *
 * Renders a two-pane interactive overlay:
 * - Left: Sidebar with quick-links and error indicators for each command.
 * - Right: Main scrollable container with expandable accordion panels showing
 *   stdout/stderr console tabs for each run command.
 * Integrates the shared timezone-aware datetime trigger in the subtitle.
 */

import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  OnInit,
  QueryList,
  signal,
  ViewChild,
  ViewChildren,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatExpansionModule} from '@angular/material/expansion';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {GetHostDebugInfoResponse} from '@deviceinfra/app/core/models/host_action';
import {HOST_SERVICE} from '@deviceinfra/app/core/services/host/host_service';
import {DatetimeTrigger} from '@deviceinfra/app/shared/components/datetime_trigger/datetime_trigger';
import {TooltipIfTruncatedDirective} from '@deviceinfra/app/shared/directives/tooltip_if_truncated/tooltip_if_truncated';
import {ClipboardService} from '@deviceinfra/app/shared/services/clipboard_service';
import {SnackBarService} from '@deviceinfra/app/shared/services/snackbar_service';
import {take} from 'rxjs/operators';

/** Data for HostDebugDialog. */
export interface HostDebugDialogData {
  hostName: string;
}

/**
 * Dialog for displaying host debug information.
 */
@Component({
  selector: 'app-host-debug-dialog',
  standalone: true,
  templateUrl: './host_debug_dialog.ng.html',
  styleUrl: './host_debug_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatButtonModule,
    MatExpansionModule,
    MatIconModule,
    MatDialogModule,
    MatTooltipModule,
    DatetimeTrigger,
    TooltipIfTruncatedDirective,
  ],
})
export class HostDebugDialog implements OnInit {
  @ViewChild('mainContent') mainContent?: ElementRef<HTMLElement>;
  @ViewChildren('commandPanel', {read: ElementRef}) commandPanels!: QueryList<
    ElementRef<HTMLElement>
  >;

  readonly data = inject<HostDebugDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<HostDebugDialog>);
  private readonly hostService = inject(HOST_SERVICE);
  private readonly snackBar = inject(SnackBarService);
  private readonly clipboardService = inject(ClipboardService);

  /** Signal tracking the loading state of the debug info request. */
  readonly isLoading = signal(false);
  /** Signal holding the debug information response once fetched. */
  readonly debugInfo = signal<GetHostDebugInfoResponse | undefined>(undefined);
  /** Signal tracking which command sections are expanded (true) or collapsed (false). */
  readonly expandedCommands = signal<Record<string, boolean>>({});
  /** Signal tracking the active tab ('stdout' or 'stderr') for each command. */
  readonly activeTabs = signal<Record<string, 'stdout' | 'stderr'>>({});

  /** Computed signal that returns true only when all command sections are expanded. */
  readonly allExpanded = computed(() => {
    const expanded = this.expandedCommands();
    const keys = Object.keys(expanded);
    if (keys.length === 0) return false;
    return keys.every((key) => expanded[key]);
  });

  ngOnInit() {
    this.fetchDebugInfo();
  }

  /** Fetches debug information for the host and initializes component state. */
  fetchDebugInfo(isRefresh = false) {
    this.isLoading.set(true);
    this.hostService
      .getHostDebugInfo(this.data.hostName)
      .pipe(take(1))
      .subscribe({
        next: (response) => {
          this.debugInfo.set(response);
          this.isLoading.set(false);

          if (isRefresh) {
            this.snackBar.showSuccess('Debug info refreshed successfully');
          }

          // Initialize all commands as expanded by default, and set default active tabs.
          const initialExpanded: Record<string, boolean> = {};
          const initialTabs: Record<string, 'stdout' | 'stderr'> = {};
          for (const res of response.results) {
            initialExpanded[res.command] = true;
            // Default to stdout if available, otherwise stderr
            if (res.stdout) {
              initialTabs[res.command] = 'stdout';
            } else if (res.stderr) {
              initialTabs[res.command] = 'stderr';
            }
          }
          this.expandedCommands.set(initialExpanded);
          this.activeTabs.set(initialTabs);
        },
        error: (error: {message?: string}) => {
          const action = isRefresh ? 'refresh' : 'get';
          this.snackBar.showError(
            `Failed to ${action} debug info for host ${this.data.hostName}: ${
              error.message || 'Unknown error'
            }`,
          );
          this.isLoading.set(false);

          // if we failed to get the debug info for the first time, close the dialog
          // as there is nothing to show.
          if (!isRefresh) {
            this.dialogRef.close();
          }
        },
      });
  }

  setExpandedState(command: string, expanded: boolean) {
    this.expandedCommands.update((prev) => ({
      ...prev,
      [command]: expanded,
    }));
  }

  /**
   * Navigates to a specific command section in the viewport.
   *
   * Steps:
   * 1. Forces the target command panel to be expanded (necessary so it has a
   *    valid height/offsetTop).
   * 2. Waits 100ms for Angular to render the expanded DOM.
   * 3. Finds the element via ViewChildren query and scrolls the main content
   *    viewport to its offset.
   * 4. Triggers a temporary 'highlight-flash' CSS keyframe animation on the
   *    panel header to visually guide the user.
   *
   * @param command The raw command string identifying the panel.
   */
  navigateToCommand(command: string) {
    // Ensure the command section is expanded so it can be scrolled to
    this.expandedCommands.update((prev) => ({
      ...prev,
      [command]: true,
    }));

    // Wait for Angular to render the expanded content before scrolling
    setTimeout(() => {
      const targetId = 'cmd-' + this.getSafeId(command);
      const panelRef = this.commandPanels.find(
        (panel) => panel.nativeElement.id === targetId,
      );
      const mainContentEl = this.mainContent?.nativeElement;

      if (panelRef && mainContentEl) {
        const element = panelRef.nativeElement;
        const offsetTop = element.offsetTop;
        // Scroll the main content container to the command section
        mainContentEl.scrollTop = offsetTop - 16; // 16px padding offset

        // Trigger highlight flash animation.
        // We remove the class, trigger a reflow by reading offsetWidth in the condition,
        // and then re-add the class. The if condition ensures no linter warnings.
        element.classList.remove('highlight-flash');
        if (element.offsetWidth >= 0) {
          element.classList.add('highlight-flash');
        }
      }
    }, 100);
  }

  switchTab(command: string, tab: 'stdout' | 'stderr') {
    this.activeTabs.update((prev) => ({
      ...prev,
      [command]: tab,
    }));
  }

  expandAll() {
    this.expandedCommands.update((prev) => {
      const updated = {...prev};
      for (const key of Object.keys(updated)) {
        updated[key] = true;
      }
      return updated;
    });
  }

  collapseAll() {
    this.expandedCommands.update((prev) => {
      const updated = {...prev};
      for (const key of Object.keys(updated)) {
        updated[key] = false;
      }
      return updated;
    });
  }

  toggleExpandAll() {
    if (this.allExpanded()) {
      this.collapseAll();
    } else {
      this.expandAll();
    }
  }

  copyToClipboard(text: string, message: string) {
    if (this.clipboardService.copyToClipboard(text)) {
      this.snackBar.showInfo(message);
    } else {
      this.snackBar.showError('Failed to copy to clipboard.');
    }
  }

  copyAll() {
    const info = this.debugInfo();
    if (!info) return;
    const text = info.results
      .map(
        (res) =>
          `=== ${res.command} ===\n\nSTDOUT:\n${res.stdout || ''}\n\nSTDERR:\n${res.stderr || ''}`,
      )
      .join('\n\n');
    this.copyToClipboard(text, 'All debug info copied');
  }

  onClose() {
    this.dialogRef.close();
  }

  /**
   * Normalizes a raw shell command string (which contains spaces/symbols)
   * into a safe alphanumeric hyphenated format for HTML ID attributes.
   * E.g. "adb devices -l" becomes "adb-devices--l"
   */
  getSafeId(command: string): string {
    return command.replace(/[^a-zA-Z0-9]/g, '-').toLowerCase();
  }
}
