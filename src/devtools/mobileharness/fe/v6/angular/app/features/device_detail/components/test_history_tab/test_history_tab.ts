import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Input,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatTableModule} from '@angular/material/table';
import {EMPTY, Observable, Subject} from 'rxjs';
import {catchError, map, switchMap} from 'rxjs/operators';

import {
  Indicator,
  LinkCell,
  TableCell,
  TableColumn,
  TableRow,
} from '../../../../core/models/device_test_history';
import {DEVICE_SERVICE} from '../../../../core/services/device/device_service';

/**
 * Device Detail "Test history" tab.
 *
 * Renders the tests that ran on the current device as the generic test search
 * result table (GetDeviceTestHistory). The backend supplies the columns and the
 * typed cells for each row; this component only maps cell kinds to Material 3
 * table elements and formats the raw time/duration values for display. Paging is
 * cursor based (Next/Previous), following the backend's page tokens.
 */
@Component({
  selector: 'app-test-history-tab',
  standalone: true,
  templateUrl: './test_history_tab.ng.html',
  styleUrl: './test_history_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatIconModule, MatProgressBarModule, MatTableModule],
})
export class TestHistoryTab implements OnInit {
  /** The device ID to fetch test history for. */
  @Input({required: true}) deviceId!: string;
  /** Optional observable trigger to reload the first page of test history. */
  @Input() refreshTrigger$?: Observable<void>;

  private readonly deviceService = inject(DEVICE_SERVICE);
  private readonly destroyRef = inject(DestroyRef);
  private readonly loadTrigger$ = new Subject<string>();

  readonly columns = signal<TableColumn[]>([]);
  readonly rows = signal<TableRow[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly hasNextPage = signal(false);
  readonly canPrev = signal(false);

  readonly displayedColumns = computed(() =>
    this.columns().map((column) => column.key ?? ''),
  );

  private currentToken = '';
  private nextToken = '';
  private readonly prevTokens: string[] = [];

  /**
   * Initializes the component by loading the first page of test history and
   * subscribing to refresh triggers if provided.
   */
  ngOnInit(): void {
    this.loadTrigger$
      .pipe(
        switchMap((token) => {
          this.loading.set(true);
          this.error.set('');
          if (token === '') {
            this.prevTokens.length = 0;
            this.canPrev.set(false);
          }
          return this.deviceService
            .getDeviceTestHistory(this.deviceId, token)
            .pipe(
              map((response) => ({token, response})),
              catchError(() => {
                this.error.set('Failed to load test history.');
                this.rows.set([]);
                this.hasNextPage.set(false);
                this.loading.set(false);
                return EMPTY;
              }),
            );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({token, response}) => {
        this.currentToken = token;
        this.columns.set(response.columns ?? []);
        this.rows.set(response.rows ?? []);
        this.nextToken = response.nextPageToken ?? '';
        this.hasNextPage.set(this.nextToken !== '');
        this.loading.set(false);
      });

    this.loadPage('');

    if (this.refreshTrigger$) {
      this.refreshTrigger$
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => {
          this.loadPage('');
        });
    }
  }

  /**
   * Navigates to the next page of test history using the stored next token.
   */
  nextPage(): void {
    if (!this.nextToken || this.loading()) {
      return;
    }
    this.prevTokens.push(this.currentToken);
    this.canPrev.set(true);
    this.loadPage(this.nextToken);
  }

  /**
   * Navigates to the previous page of test history from the token stack.
   */
  prevPage(): void {
    if (this.prevTokens.length === 0 || this.loading()) {
      return;
    }
    const token = this.prevTokens.pop() ?? '';
    this.canPrev.set(this.prevTokens.length > 0);
    this.loadPage(token);
  }

  /** The cell for a given column index in a row (cells are parallel to columns). */
  cellAt(row: TableRow, index: number): TableCell | undefined {
    return row.cells?.[index];
  }

  /**
   * Generates the URL for the test detail page in legacy MHFE.
   *
   * @param link The LinkCell containing the test and job IDs.
   * @return The absolute URL to the legacy MHFE test detail view.
   *
   * Explanation: This function extracts jobId and testId from the LinkCell target
   * to construct a direct link to the test detail page (hosted in legacy UI).
   * If either ID is missing, it logs an error to the console.
   */
  linkHref(link: LinkCell): string {
    const testId = link.target?.test?.testId ?? '';
    const jobId = link.target?.test?.jobId ?? '';
    if (!testId || !jobId) {
      console.error('Missing testId or jobId for link:', link);
    }
    return `http://mhfe/testdetailview/${jobId}/${testId}`;
  }

  /** Maps a semantic indicator to a chip style class. */
  indicatorClass(indicator: Indicator | undefined): string {
    switch (indicator) {
      case 'INDICATOR_OK':
        return 'status-chip status-ok';
      case 'INDICATOR_ACTIVE':
        return 'status-chip status-active';
      case 'INDICATOR_ERROR':
        return 'status-chip status-error';
      default:
        return 'status-chip status-neutral';
    }
  }

  /** Formats a plain text cell, formatting time and duration columns. */
  formatText(columnKey: string | undefined, value: string | undefined): string {
    if (value === undefined || value === '') {
      return '-';
    }
    if (columnKey === 'start_time') {
      const ms = Number(value);
      return Number.isNaN(ms) ? '-' : new Date(ms).toLocaleString();
    }
    if (columnKey === 'duration') {
      return this.formatDuration(Number(value));
    }
    return value;
  }

  /**
   * Formats a duration in milliseconds into a human-readable string (e.g., "2m 5s").
   *
   * @param ms Duration in milliseconds.
   * @return Formatted duration string or '-' if invalid/non-positive.
   */
  private formatDuration(ms: number): string {
    if (Number.isNaN(ms) || ms <= 0) {
      return '-';
    }
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return minutes > 0 ? `${minutes}m ${seconds}s` : `${seconds}s`;
  }

  /**
   * Loads a page of test history for the current device.
   *
   * @param token The pagination token for the requested page (empty for first page).
   */
  private loadPage(token: string): void {
    this.loadTrigger$.next(token);
  }
}
