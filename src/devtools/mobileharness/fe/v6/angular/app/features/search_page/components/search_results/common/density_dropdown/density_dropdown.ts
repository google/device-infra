import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';

/** Density option type for search results table rows. */
export type TableDensity = 'compact' | 'default' | 'comfortable';

/** Reusable standalone density selector dropdown component for search results. */
@Component({
  selector: 'app-density-dropdown',
  standalone: true,
  templateUrl: './density_dropdown.ng.html',
  styleUrl: './density_dropdown.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatIconModule, MatMenuModule],
})
export class DensityDropdownComponent {
  /** Active table row height density mode. */
  readonly density = input<TableDensity>('default');

  /** Event emitted when user selects a different density option. */
  readonly densityChange = output<TableDensity>();

  setDensity(mode: TableDensity) {
    this.densityChange.emit(mode);
  }
}
