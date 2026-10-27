import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatDialog} from '@angular/material/dialog';
import {MatExpansionModule} from '@angular/material/expansion';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTooltipModule} from '@angular/material/tooltip';
import {RouterLink} from '@angular/router';

import {
  OutboundLink,
  ToolExecution,
  Turn,
  TurnStatus,
} from '../../../../core/models/assistant';
import {MarkdownViewerComponent} from '../../../../shared/components/markdown_viewer/markdown_viewer';
import {ToolInspectionDialogComponent} from './tool_inspection_dialog';

/** Parsed target link structure for Angular Router or external anchor navigation. */
export interface ParsedLinkTarget {
  readonly path: string;
  readonly queryParams: Record<string, string>;
  readonly isExternal: boolean;
}

/** Parses a relative or absolute URL into router path and query parameters. */
export function parseLinkTarget(url: string): ParsedLinkTarget {
  if (!url) {
    return {path: '/', queryParams: {}, isExternal: false};
  }
  if (/^https?:\/\//i.test(url)) {
    return {path: url, queryParams: {}, isExternal: true};
  }
  const [pathPart, queryPart] = url.split('?');
  const queryParams: Record<string, string> = {};
  if (queryPart) {
    const searchParams = new URLSearchParams(queryPart);
    searchParams.forEach((value, key) => {
      queryParams[key] = value;
    });
  }
  return {
    path: pathPart || '/',
    queryParams,
    isExternal: false,
  };
}

/** Calculates formatted elapsed reasoning duration from start and end ISO timestamps. */
export function formatElapsedDuration(
  startTime?: string,
  endTime?: string,
): string | null {
  if (!startTime || !endTime) return null;
  const startMs = new Date(startTime).getTime();
  const endMs = new Date(endTime).getTime();
  if (isNaN(startMs) || isNaN(endMs) || endMs < startMs) {
    return null;
  }
  const seconds = ((endMs - startMs) / 1000).toFixed(1);
  return `${seconds}s`;
}

/**
 * TurnViewComponent renders a single conversational turn including:
 * - User prompt card
 * - Collapsible Thought Tray (auto-expands while RUNNING, auto-collapses on COMPLETED)
 * - Tool Execution ReAct Chips (clickable to inspect arguments & JSON outputs)
 * - Assistant Markdown Response
 * - Pinned Outbound Deep-Link Tray
 */
@Component({
  selector: 'app-turn-view',
  standalone: true,
  imports: [
    CommonModule,
    MarkdownViewerComponent,
    MatButtonModule,
    MatExpansionModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    RouterLink,
  ],
  templateUrl: './turn_view.ng.html',
  styleUrl: './turn_view.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TurnViewComponent {
  private readonly dialog = inject(MatDialog);

  readonly TurnStatus = TurnStatus;

  /** The turn record to display. */
  readonly turn = input.required<Turn>();

  /** Tracks whether the user explicitly toggled the thought tray. */
  readonly manualExpandOverride = signal<boolean | null>(null);

  /**
   * Auto-expands while the turn is RUNNING or QUEUED,
   * and auto-collapses when COMPLETED/FAILED/CANCELLED unless manually overridden.
   */
  readonly isThoughtExpanded = computed(() => {
    const override = this.manualExpandOverride();
    if (override !== null) {
      return override;
    }
    const status = this.turn().status;
    return status === TurnStatus.RUNNING || status === TurnStatus.QUEUED;
  });

  /** Indicates whether the turn is currently actively streaming. */
  readonly isStreaming = computed(() => {
    const status = this.turn().status;
    return status === TurnStatus.RUNNING || status === TurnStatus.QUEUED;
  });

  /** Dynamic header label for the collapsible Thought Tray. */
  readonly thoughtHeaderLabel = computed(() => {
    const t = this.turn();
    if (t.status === TurnStatus.RUNNING || t.status === TurnStatus.QUEUED) {
      return 'Thinking & executing tools...';
    }
    const elapsed = formatElapsedDuration(t.startTime, t.endTime);
    if (elapsed) {
      return `Thought for ${elapsed}`;
    }
    return 'Reasoning & Tool Trace';
  });

  /** Checks whether the thought tray or tool chips should be displayed. */
  readonly hasReasoningOrTools = computed(() => {
    const t = this.turn();
    return Boolean(
      t.thoughts ||
        (t.toolExecutions && t.toolExecutions.length > 0) ||
        t.status === TurnStatus.RUNNING ||
        t.status === TurnStatus.QUEUED,
    );
  });

  onThoughtToggle(expanded: boolean): void {
    this.manualExpandOverride.set(expanded);
  }

  openToolDialog(tool: ToolExecution): void {
    this.dialog.open(ToolInspectionDialogComponent, {
      data: tool,
      width: '640px',
      maxWidth: '90vw',
    });
  }

  parseLink(link: OutboundLink): ParsedLinkTarget {
    return parseLinkTarget(link.url);
  }
}
