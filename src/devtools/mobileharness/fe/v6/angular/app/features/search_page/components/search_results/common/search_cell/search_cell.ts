import {ChangeDetectionStrategy, Component, input} from '@angular/core';
import {MatTooltipModule} from '@angular/material/tooltip';
import {RouterLink} from '@angular/router';

import {Cell, Column} from '../../../../../../core/models/search';
import {OverflowChipListComponent} from '../../../../../../shared/components/overflow_chip_list/overflow_chip_list';
import {TooltipIfTruncatedDirective} from '../../../../../../shared/directives/tooltip_if_truncated/tooltip_if_truncated';
import {getRouterLink, getStatusClass} from '../../../../utils';

/** Standalone component for rendering a single search table cell generically based on Proto Cell type. */
@Component({
  selector: 'app-search-cell',
  standalone: true,
  templateUrl: './search_cell.ng.html',
  styleUrl: './search_cell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    MatTooltipModule,
    TooltipIfTruncatedDirective,
    OverflowChipListComponent,
  ],
})
export class SearchCellComponent {
  readonly cell = input<Cell | null | undefined>();
  readonly column = input<Column | undefined>();

  readonly getStatusClass = getStatusClass;
  readonly getRouterLink = getRouterLink;
}
