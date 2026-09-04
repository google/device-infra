import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';
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
  /** Pre-formatted semantic range text provided directly by BFF/Store (e.g. "1 – 25 of 1,250", "showing 1–25", "1–25 of 142 groups"). */
  readonly rangeText = input<string>('');

  /** Whether a preceding page of results is available. */
  readonly hasPrev = input<boolean, boolean | null | undefined>(false, {
    transform: (v) => !!v,
  });

  /** Whether a subsequent page of results is available. */
  readonly hasNext = input<boolean, boolean | null | undefined>(false, {
    transform: (v) => !!v,
  });

  /** Active page size count. When provided, the 'Rows per page' selector is shown. */
  readonly pageSize = input<number | undefined>(undefined);

  /** Available options for page size selection dropdown. */
  readonly pageSizeOptions = input<number[]>([10, 25, 50, 100]);

  /** Whether to render in compact height mode (e.g. for nested card pagination). */
  readonly compact = input<boolean>(false);

  /** Event emitted when user clicks the previous page button. */
  readonly prev = output<void>();

  /** Event emitted when user clicks the next page button. */
  readonly next = output<void>();

  /** Event emitted when user changes the page size selection. */
  readonly pageSizeChange = output<number>();
}
