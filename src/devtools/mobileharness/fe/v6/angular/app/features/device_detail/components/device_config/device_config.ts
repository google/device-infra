import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnInit,
} from '@angular/core';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {tap} from 'rxjs/operators';

import {DeviceConfigUiStatus} from '../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';
import {DeviceConfigStateService} from '../../../../core/services/config/device_config_state_service';
import {Environment} from '../../../../core/services/environment';

import {DeviceEmpty} from './device_empty/device_empty';
import {DeviceSettings} from './device_settings/device_settings';
import {HostManaged} from './host_managed/host_managed';

const ATS_DEVICE_CONFIG_UI_STATUS: DeviceConfigUiStatus = {
  permissions: {visible: false, editability: {editable: false}},
  wifi: {visible: false, editability: {editable: false}},
  dimensions: {visible: true, editability: {editable: true}},
  settings: {visible: false, editability: {editable: false}},
};

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
  imports: [CommonModule, HostManaged, DeviceEmpty, DeviceSettings],
})
export class DeviceConfig implements OnInit {
  readonly data = inject<{
    deviceId: string;
    hostName: string;
    hostIp: string;
    universe?: string;
  }>(MAT_DIALOG_DATA);
  private readonly configService = inject(CONFIG_SERVICE);
  private readonly deviceConfigStateService = inject(DeviceConfigStateService);
  private readonly environment = inject(Environment);

  get isAts(): boolean {
    return !this.environment.isGoogleInternal();
  }

  readonly configResult$ = this.configService
    .getDeviceConfig(this.data.deviceId, this.data.hostName)
    .pipe(
      tap((result) => {
        if (result && result.uiStatus) {
          this.deviceConfigStateService.setUiStatus(
            this.data.deviceId,
            result.uiStatus,
          );
        } else if (result && this.isAts) {
          this.deviceConfigStateService.setUiStatus(
            this.data.deviceId,
            ATS_DEVICE_CONFIG_UI_STATUS,
          );
        }
      }),
    );

  ngOnInit() {}
}
