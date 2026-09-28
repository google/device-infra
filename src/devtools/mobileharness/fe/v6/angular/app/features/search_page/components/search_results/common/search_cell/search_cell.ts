import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import {MatTooltipModule} from '@angular/material/tooltip';

import {
  Cell,
  Column,
  LinkEntry,
  NavTarget,
} from '../../../../../../core/models/search';
import {
  NavLink,
  NavLinkConfig,
} from '../../../../../../shared/components/nav_link/nav_link';
import {OverflowChipListComponent} from '../../../../../../shared/components/overflow_chip_list/overflow_chip_list';
import {TooltipIfTruncatedDirective} from '../../../../../../shared/directives/tooltip_if_truncated/tooltip_if_truncated';
import {getStatusClass} from '../../../../utils';

/** Standalone component for rendering a single search table cell generically based on Proto Cell type. */
@Component({
  selector: 'app-search-cell',
  standalone: true,
  templateUrl: './search_cell.ng.html',
  styleUrl: './search_cell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NavLink,
    MatTooltipModule,
    TooltipIfTruncatedDirective,
    OverflowChipListComponent,
  ],
})
export class SearchCellComponent {
  readonly cell = input<Cell | null | undefined>();
  readonly column = input<Column | undefined>();

  readonly getStatusClass = getStatusClass;
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

  /** Pre-resolved nav link config for LinkCell variant. */
  readonly navLinkConfig = computed(() =>
    getNavLinkConfig(this.cell()?.link?.target),
  );

  /** Pre-resolved CSS status dot class for StatusCell variant. */
  readonly statusDotClass = computed(() =>
    getStatusClass(this.cell()?.status?.indicator),
  );

  /** Pre-resolved entries with nav link configs for MultiLinkCell variant. */
  readonly multiLinkEntries = computed<
    Array<{text: string; navConfig: NavLinkConfig | null}>
  >(() => {
    const entries = this.cell()?.multiLink?.entries;
    if (!entries || entries.length === 0) return [];
    return entries.map((e: LinkEntry) => ({
      text: e.text,
      navConfig: getNavLinkConfig(e.target),
    }));
  });
}

/** Converts a generic Proto NavTarget into a NavLinkConfig for NavLink component. */
export function getNavLinkConfig(
  target: NavTarget | undefined,
): NavLinkConfig | null {
  if (!target) return null;

  if (target.device?.id) {
    return {
      type: 'device',
      deviceId: target.device.id,
      hostName: target.device.hostName || '',
      hostIp: target.device.hostIp || '',
      universe: target.device.universe,
    };
  }
  if (target.host?.hostName) {
    return {
      type: 'host',
      hostName: target.host.hostName,
      hostIp: target.host.hostIp || '',
      universe: target.host.universe,
    };
  }
  if (target.job?.jobId) {
    return {
      type: 'job',
      jobId: target.job.jobId,
    };
  }
  if (target.session?.sessionId) {
    return {
      type: 'session',
      sessionId: target.session.sessionId,
    };
  }
  if (target.test?.testId) {
    return {
      type: 'test',
      jobId: target.test.jobId || '',
      testId: target.test.testId,
    };
  }

  return null;
}
