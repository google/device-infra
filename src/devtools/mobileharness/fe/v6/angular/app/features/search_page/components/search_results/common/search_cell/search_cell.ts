import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, input} from '@angular/core';
import {RouterLink} from '@angular/router';

import {Cell, Column} from '../../../../../../core/models/search';
import {OverflowChipListComponent} from '../../../../../../shared/components/overflow_chip_list/overflow_chip_list';
import {dateUtils} from '../../../../../../shared/utils/date_utils';
import {
  formatTime,
  getCellType,
  getRouterLink,
  getStatusClass,
  getTextValue,
  isTimeColumn,
} from '../../../../utils';

/** Standalone component for rendering a single search table cell generically based on Proto Cell type. */
@Component({
  selector: 'app-search-cell',
  standalone: true,
  templateUrl: './search_cell.ng.html',
  styleUrl: './search_cell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink, OverflowChipListComponent],
})
export class SearchCellComponent {
  readonly cell = input<Cell | null | undefined>();
  readonly column = input<Column | undefined>();
  readonly entity = input<string>('devices');

  readonly getTextValue = getTextValue;
  readonly getCellType = getCellType;
  readonly getStatusClass = getStatusClass;
  readonly getRouterLink = getRouterLink;
  readonly dateUtils = dateUtils;
  readonly formatTime = formatTime;

  /** Computes the final text string for plain text or timestamp cells to flatten template logic. */
  renderTextValue(
    cell: Cell | null | undefined,
    colKey: string | undefined,
  ): string | null {
    if (!cell) return null;
    const txt = getTextValue(cell);
    if (!txt) return null;
    if (isTimeColumn(colKey)) {
      return formatTime(txt) || txt;
    }
    return txt;
  }
}
