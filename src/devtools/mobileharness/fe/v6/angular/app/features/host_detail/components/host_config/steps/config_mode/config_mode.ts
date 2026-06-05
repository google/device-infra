import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  input,
  Output,
} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import type {
  DeviceConfigMode,
  PartStatus,
} from '../../../../../../core/models/host_config_models';

/**
 * Component for displaying the config mode of a host in the host configuration
 * workflow.
 */
@Component({
  selector: 'app-config-mode',
  standalone: true,
  templateUrl: './config_mode.ng.html',
  styleUrl: './config_mode.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatIconModule],
})
export class ConfigMode {
  readonly workflow = input<'wizard' | 'settings'>('wizard');
  readonly uiStatus = input<PartStatus>({
    visible: true,
    editability: {editable: true},
  });

  @Input() mode: DeviceConfigMode = 'PER_DEVICE';
  @Output() readonly modeChange = new EventEmitter<string>();

  ngOnInit() {}

  selectMode(mode: DeviceConfigMode) {
    this.mode = mode;
    this.modeChange.emit(mode);
  }
}
