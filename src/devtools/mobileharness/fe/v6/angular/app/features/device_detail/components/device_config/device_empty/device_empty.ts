import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, inject, Input, OnInit, signal} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';

import type {DeviceConfig} from '../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {Dialog} from '../../../../../shared/components/config_common/dialog/dialog';

/**
 * Component for displaying the empty configuration of a device.
 *
 * It is used to configure the device's dimensions, owners, and other
 * properties.
 */

@Component({
  selector: 'app-device-empty',
  standalone: true,
  templateUrl: './device_empty.ng.html',
  styleUrl: './device_empty.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatIconModule,
    MatInputModule,
    MatDialogModule,
    MatFormFieldModule,
    Dialog,
  ],
})
export class DeviceEmpty implements OnInit {
  readonly data = inject<{deviceId: string; hostName: string; title: string;}>(
      MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<DeviceEmpty>);

  private readonly configService = inject(CONFIG_SERVICE);

  @Input() deviceId = this.data.deviceId;
  @Input() hostName = this.data.hostName;
  @Input() title = this.data.title;

  hostMissing = signal(false);

  copyFromAnother = signal(false);
  anotherDeviceUuid = '';
  copyFromAnotherErrorMessage = signal('');

  ngOnInit() {}

  startNewConfig() {
    this.openWizard('new', null);
  }

  openWizard(source: 'new'|'copy', config: DeviceConfig|null) {
    this.dialogRef.close(
        {action: source, deviceId: this.data.deviceId, config});
  }

  copyFromHost() {
    this.configService.getHostDefaultDeviceConfig(this.hostName)
        .subscribe((defaultConfig) => {
          if (!defaultConfig) {
            this.hostMissing.set(true);
            return;
          }

          this.openWizard('copy', defaultConfig);
        });
  }

  copyFromAnotherDevice() {
    this.copyFromAnother.set(true);
  }

  loadAndReviewConfig() {
    if (!this.anotherDeviceUuid.trim()) {
      this.copyFromAnotherErrorMessage.set('Please enter a device UUID.');
      return;
    }

    this.configService.getDeviceConfig(this.anotherDeviceUuid).subscribe({
      next: (config) => {
        if (!config) {
          this.copyFromAnotherErrorMessage.set(
              `Device UUID not found or has no configuration.`,
          );
          return;
        }

        this.openWizard('copy', config.deviceConfig);
      },
      error: (error) => {
        this.copyFromAnotherErrorMessage.set(error.message);
      },
    });
  }
}
