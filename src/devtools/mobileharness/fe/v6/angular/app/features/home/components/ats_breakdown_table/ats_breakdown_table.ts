import {DecimalPipe} from '@angular/common';
import {ChangeDetectionStrategy, Component, input, model} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTableModule} from '@angular/material/table';
import {MatTooltipModule} from '@angular/material/tooltip';
import {RouterLink} from '@angular/router';
import {PartnerAtsLab} from '@deviceinfra/app/core/models/home';
import {UtilizationBar} from '@deviceinfra/app/features/home/components/utilization_bar';
import {DrilldownRoutePipe} from '@deviceinfra/app/features/home/utils';

/**
 * Component rendering the expandable breakdown table of ATS partner labs
 * using Angular Material Table.
 */
@Component({
  selector: 'app-ats-breakdown-table',
  standalone: true,
  templateUrl: './ats_breakdown_table.ng.html',
  styleUrl: './ats_breakdown_table.scss',
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
export class AtsBreakdownTable {
  readonly labs = input<
    readonly PartnerAtsLab[],
    readonly PartnerAtsLab[] | undefined
  >([], {
    transform: (value: readonly PartnerAtsLab[] | undefined) => value ?? [],
  });
  readonly expanded = model<boolean>(false);

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
