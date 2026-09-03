import {DecimalPipe} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  signal,
} from '@angular/core';
import {MatTooltipModule} from '@angular/material/tooltip';
import {RouterLink} from '@angular/router';
import {DeviceUtilization} from '@deviceinfra/app/core/models/home';
import {UtilizationMetrics} from '@deviceinfra/app/features/home/models';
import {
  calcUtilizationMetrics,
  DrilldownRoutePipe,
} from '@deviceinfra/app/features/home/utils';

/**
 * Utilization bar component rendering segmented progress bar and interactive legends.
 * Supports 'full' mode (for summary cards) and 'mini' mode (for table rows).
 */
@Component({
  selector: 'app-utilization-bar',
  standalone: true,
  templateUrl: './utilization_bar.ng.html',
  styleUrl: './utilization_bar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, MatTooltipModule, RouterLink, DrilldownRoutePipe],
})
export class UtilizationBar {
  readonly utilization = input<DeviceUtilization>();
  readonly totalDevices = input<number>();
  readonly mode = input<'full' | 'mini'>('full');

  readonly metrics = computed<UtilizationMetrics>(() =>
    calcUtilizationMetrics(this.utilization(), this.totalDevices()),
  );

  readonly hoveredSegment = signal<'busy' | 'idle' | 'others' | null>(null);

  setHover(type: 'busy' | 'idle' | 'others' | null) {
    this.hoveredSegment.set(type);
  }
}
