import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  Input,
  OnInit,
  signal,
} from '@angular/core';
import {MatDialogModule} from '@angular/material/dialog';
import {RouterModule} from '@angular/router';
import {DeviceConfig} from '../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {Dialog} from '../../../../../shared/components/dialog/dialog';
import {NavLink} from '../../../../../shared/components/nav_link/nav_link';

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
  imports: [CommonModule, MatDialogModule, RouterModule, NavLink, Dialog],
})
export class HostManaged implements OnInit {
  private readonly configService = inject(CONFIG_SERVICE);

  @Input() deviceId = '';
  @Input() hostName = '';
  @Input() hostIp = '';

  readonly hostDefaultConfig = signal<DeviceConfig>({});

  ngOnInit() {
    this.configService
      .getHostDefaultDeviceConfig(this.hostName)
      .subscribe((result) => {
        if (!result) {
          return;
        }

        this.hostDefaultConfig.set(result);
      });
  }
}
