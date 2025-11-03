import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
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
export class Stability implements OnInit {
  @Input() type: 'device'|'host' = 'device';
  @Input()
  uiStatus: PartStatus = {
    visible: true,
    editability: {editable: true},
  };

  @Input() title = 'Stability & Reboot';
  @Input() hostTooltipText = '';

  @Input()
  settings: {maxConsecutiveFail: number; maxConsecutiveTest: number;} = {
    maxConsecutiveFail: 5,
    maxConsecutiveTest: 10000,
  };
  @Output()
  readonly settingsChange = new EventEmitter<
      {maxConsecutiveFail: number; maxConsecutiveTest: number;}>();

  ngOnInit() {}
}
