import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, inject, Input, OnInit} from '@angular/core';
import {MatDialogModule} from '@angular/material/dialog';

import type {DeviceConfig} from '../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {Dialog} from '../../../../../shared/components/config_common/dialog/dialog';

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
  private readonly configService = inject(CONFIG_SERVICE);

  @Input() deviceId = '';
  @Input() hostName = '';

  hostDefaultConfig: DeviceConfig = {
    permissions: {
      owners: [],
      executors: [],
    },
    wifi: {type: 'none', ssid: 'GoogleGuest', psk: '', scanSsid: false},
    dimensions: {supported: [], required: []},
    settings: {
      maxConsecutiveFail: 0,
      maxConsecutiveTest: 0,
    },
  };

  ngOnInit() {
    this.configService.getHostDefaultDeviceConfig(this.hostName)
        .subscribe((defaultConfig) => {
          if (!defaultConfig) {
            return;
          }

          this.hostDefaultConfig = defaultConfig;
        });
  }
}
