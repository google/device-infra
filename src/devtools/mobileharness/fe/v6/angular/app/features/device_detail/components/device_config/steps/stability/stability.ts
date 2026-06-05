import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  input,
  Output,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatTooltipModule} from '@angular/material/tooltip';

import type {PartStatus} from '../../../../../../core/models/host_config_models';

/**
 * Component for displaying the stability settings of a device in the device
 * configuration workflow.
 */
@Component({
  selector: 'app-stability',
  standalone: true,
  templateUrl: './stability.ng.html',
  styleUrl: './stability.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatInputModule,
    MatIconModule,
    MatTooltipModule,
  ],
})
export class Stability {
  readonly type = input<'device' | 'host'>('device');
  readonly uiStatus = input<PartStatus>({
    visible: true,
    editability: {editable: true},
  });

  readonly title = input<string>('Stability & Reboot');
  readonly hostTooltipText = input<string>('');

  @Input()
  settings: {maxConsecutiveFail: number; maxConsecutiveTest: number} = {
    maxConsecutiveFail: 5,
    maxConsecutiveTest: 10000,
  };
  @Output()
  readonly settingsChange = new EventEmitter<{
    maxConsecutiveFail: number;
    maxConsecutiveTest: number;
  }>();
}
