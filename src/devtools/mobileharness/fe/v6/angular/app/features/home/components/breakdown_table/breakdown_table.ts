import {DecimalPipe} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  model,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {MatTooltipModule} from '@angular/material/tooltip';
import {RouterLink} from '@angular/router';
import {UtilizationBar} from '@deviceinfra/app/features/home/components/utilization_bar';
import {BreakdownTableRow} from '@deviceinfra/app/features/home/models';
import {DrilldownRoutePipe} from '@deviceinfra/app/features/home/utils';

/**
 * Reusable component rendering an expandable breakdown table of fleet subsets
 * (Internal lab types or Partner ATS labs) using Angular Material Table.
 */
@Component({
  selector: 'app-breakdown-table',
  standalone: true,
  templateUrl: './breakdown_table.ng.html',
  styleUrl: './breakdown_table.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatTooltipModule,
    RouterLink,
    UtilizationBar,
    DrilldownRoutePipe,
  ],
})
export class BreakdownTable {
  readonly rows = input<
    readonly BreakdownTableRow[],
    readonly BreakdownTableRow[] | undefined
  >([], {
    transform: (value: readonly BreakdownTableRow[] | undefined) => value ?? [],
  });

  readonly toggleLabel = input<string>('Breakdown');
  readonly nameColumnHeader = input<string>('Name');

  readonly expanded = model<boolean>(false);

  readonly toggleButtonText = computed(
    () => (this.expanded() ? 'Hide ' : 'View ') + this.toggleLabel(),
  );

  readonly displayedColumns: readonly string[] = [
    'name',
    'hosts',
    'devices',
    'utilization',
  ];

  toggleExpanded() {
    this.expanded.update((val) => !val);
  }
}
