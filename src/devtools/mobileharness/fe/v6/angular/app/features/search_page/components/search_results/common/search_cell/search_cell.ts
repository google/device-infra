import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import {MatTooltipModule} from '@angular/material/tooltip';
import {RouterLink} from '@angular/router';

import {Cell, Column, LinkEntry} from '../../../../../../core/models/search';
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

  /** Identifies the cell variant conforming to protobuf Cell oneof kind. */
  readonly cellKind = computed<
    'text' | 'link' | 'status' | 'chips' | 'multiLink' | 'empty'
  >(() => {
    const c = this.cell();
    if (!c) return 'empty';
    if (c.link) return 'link';
    if (c.status) return 'status';
    if (c.chips?.values && c.chips.values.length > 0) return 'chips';
    if (c.multiLink?.entries && c.multiLink.entries.length > 0) {
      return 'multiLink';
    }
    if (c.text?.value) return 'text';
    return 'empty';
  });

  /** Pre-resolved router link target for LinkCell variant. */
  readonly linkTarget = computed(() =>
    getRouterLink(this.cell()?.link?.target),
  );

  /** Pre-resolved CSS status dot class for StatusCell variant. */
  readonly statusDotClass = computed(() =>
    getStatusClass(this.cell()?.status?.indicator),
  );

  /** Pre-resolved entries with router links for MultiLinkCell variant. */
  readonly multiLinkEntries = computed<
    Array<{text: string; routerLink: string | null}>
  >(() => {
    const entries = this.cell()?.multiLink?.entries;
    if (!entries || entries.length === 0) return [];
    return entries.map((e: LinkEntry) => ({
      text: e.text,
      routerLink: getRouterLink(e.target),
    }));
  });
}
