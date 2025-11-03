import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnInit,
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
export class ConfigMode implements OnInit {
  @Input() workflow: 'wizard' | 'settings' = 'wizard';
  @Input() uiStatus: PartStatus = {
    visible: true,
    editability: {editable: true},
  };

  @Input() mode: DeviceConfigMode = 'PER_DEVICE';
  @Output() readonly modeChange = new EventEmitter<string>();

  ngOnInit() {}

  selectMode(mode: DeviceConfigMode) {
    this.mode = mode;
    this.modeChange.emit(mode);
  }
}
