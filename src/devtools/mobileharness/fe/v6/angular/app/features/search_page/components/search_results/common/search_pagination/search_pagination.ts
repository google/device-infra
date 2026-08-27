import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {MatSelectModule} from '@angular/material/select';

/** Reusable standalone pagination footer controls component for search results. */
@Component({
  selector: 'app-search-pagination',
  standalone: true,
  templateUrl: './search_pagination.ng.html',
  styleUrl: './search_pagination.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatIconModule, MatSelectModule],
})
export class SearchPaginationComponent {
  /** Current zero-indexed page number. */
  readonly pageIndex = input<number>(0);

  /** Active page size count. */
  readonly pageSize = input<number>(25);

  /** Number of rows returned on the current page. */
  readonly currentRowCount = input<number>(0);

  /** Optional explicit total record count. */
  readonly totalCount = input<number | null | undefined>(undefined);

  /** Explicit start record index for current page range display. */
  readonly rangeStart = input<number | null | undefined>(undefined);

  /** Explicit end record index for current page range display. */
  readonly rangeEnd = input<number | null | undefined>(undefined);

  /** Optional custom text override for the page range label. */
  readonly customRangeText = input<string | null | undefined>(undefined);

  /** Optional explicit indicator of whether a previous page exists. */
  readonly hasPrevPage = input<boolean | null | undefined>(undefined);

  /** Whether a subsequent page of results is available. */
  readonly hasNextPage = input<boolean>(false);

  /** Controls display of the 'Rows per page' dropdown selector. */
  readonly showPageSize = input<boolean>(false);

  /** Available options for page size selection dropdown. */
  readonly pageSizeOptions = input<number[]>([10, 25, 50, 100]);

  /** Event emitted when user clicks the previous page button. */
  readonly prev = output<void>();

  /** Event emitted when user clicks the next page button. */
  readonly next = output<void>();

  /** Event emitted when user changes the page size selection. */
  readonly pageSizeChange = output<number>();

  /** Computes formatted page range display label string. */
  readonly getRangeLabel = computed(() => {
    if (this.customRangeText()) {
      return this.customRangeText()!;
    }
    const start =
      this.rangeStart() ?? this.pageIndex() * this.pageSize() + 1;
    const end =
      this.rangeEnd() ??
      this.pageIndex() * this.pageSize() + this.currentRowCount();
    if (this.totalCount() != null) {
      return `${start.toLocaleString()}–${end.toLocaleString()} of ${this.totalCount()!.toLocaleString()}`;
    }
    return `showing ${start.toLocaleString()}–${end.toLocaleString()}`;
  });

  /** Computes whether previous page button is enabled. */
  readonly canGoPrev = computed(() => {
    if (this.hasPrevPage() != null) {
      return this.hasPrevPage()!;
    }
    return this.pageIndex() > 0;
  });

  /** Computes whether next page button is enabled. */
  readonly canGoNext = computed(() => this.hasNextPage());
}
