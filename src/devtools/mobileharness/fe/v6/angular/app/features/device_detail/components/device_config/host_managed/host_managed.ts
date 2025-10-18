import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, Input, OnInit} from '@angular/core';
import {MatDialogModule} from '@angular/material/dialog';
import {Dialog} from '../../../../../shared/components/common_config/dialog/dialog';

/**
 * Component for displaying the host-managed configuration of a device.
 *
 * It is used to configure the device's host-managed properties, such as
 * host-side device settings and device-side settings.
 */

@Component({
  selector: 'app-host-managed',
  standalone: true,
  templateUrl: './host_managed.ng.html',
  styleUrl: './host_managed.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatDialogModule, Dialog],
})
export class HostManaged implements OnInit {
  @Input() deviceId = '';

  ngOnInit() {}
}
