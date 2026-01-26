import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';

/**
 * Component to display incompatible devices info.
 */
@Component({
  selector: 'app-incompatible-devices-content',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './incompatible_devices_content.ng.html',
  styleUrls: ['./incompatible_devices_content.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IncompatibleDevicesContent {
  @Input() invalidDevices: Array<{id: string; reason: string}> = [];
  @Input() isSingleDevice = false;
}
