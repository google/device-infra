import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, inject, OnInit} from '@angular/core';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';

import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';

import {DeviceEmpty} from './device_empty/device_empty';
import {DeviceSettings} from './device_settings/device_settings';
import {HostManaged} from './host_managed/host_managed';

/**
 * Component for displaying the device configuration dialog.
 *
 * It is used to configure the device's dimensions, owners, and other properties.
 */
@Component({
  selector: 'app-device-config',
  standalone: true,
  templateUrl: './device_config.ng.html',
  styleUrl: './device_config.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    HostManaged,
    DeviceEmpty,
    DeviceSettings,
  ],
})
export class DeviceConfig implements OnInit {
  readonly data = inject<{
    deviceId: string;
    hostName: string;
  }>(MAT_DIALOG_DATA);
  private readonly configService = inject(CONFIG_SERVICE);

  readonly configResult$ = this.configService.getDeviceConfig(
    this.data.deviceId,
  );

  ngOnInit() {}
}
