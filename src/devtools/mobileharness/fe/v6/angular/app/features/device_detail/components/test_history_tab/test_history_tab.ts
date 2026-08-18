import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';

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
  imports: [CommonModule, MatIconModule, MatTableModule],
})
export class TestHistoryTab implements OnInit {
  @Input({required: true}) deviceId!: string;
  @Input() hostName = '';

  private readonly deviceService = inject(DEVICE_SERVICE);

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

  ngOnInit() {
    this.loadPage('');
  }

  nextPage() {
    if (!this.nextToken || this.loading()) {
      return;
    }
    this.prevTokens.push(this.currentToken);
    this.canPrev.set(true);
    this.loadPage(this.nextToken);
  }

  prevPage() {
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

  /** The test detail link for a Test ID cell: http://mhfe/<test_id>. */
  linkHref(link: LinkCell): string {
    const testId = link.target?.test?.testId ?? '';
    return `http://mhfe/${testId}`;
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
   * Loads a page of device test history.
   * Calls the device service to fetch test history using the provided token and updates the component state.
   * @param token The pagination token to fetch the next/previous page.
   */
  private loadPage(token: string) {
    this.loading.set(true);
    this.error.set('');
    this.deviceService
      .getDeviceTestHistory(this.deviceId, this.hostName, token)
      .subscribe({
        next: (response) => {
          this.currentToken = token;
          this.columns.set(response.columns ?? []);
          this.rows.set(response.rows ?? []);
          this.nextToken = response.nextPageToken ?? '';
          this.hasNextPage.set(this.nextToken !== '');
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Failed to load test history.');
          this.rows.set([]);
          this.hasNextPage.set(false);
          this.loading.set(false);
        },
      });
  }
}
