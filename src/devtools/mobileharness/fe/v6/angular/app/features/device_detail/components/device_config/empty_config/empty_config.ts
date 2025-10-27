import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import type {DeviceConfig} from '../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {Dialog} from '../../../../../shared/components/common_config/dialog/dialog';
import {Wizard} from '../wizard/wizard';

/**
 * Component for displaying the empty configuration of a device.
 *
 * It is used to configure the device's dimensions, owners, and other properties.
 */

@Component({
  selector: 'app-empty-config',
  standalone: true,
  templateUrl: './empty_config.ng.html',
  styleUrl: './empty_config.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatDialogModule,
    MatIconModule,
    Wizard,
    Dialog,
    FormsModule,
    ReactiveFormsModule,
  ],
})
export class EmptyConfig implements OnInit {
  readonly data = inject(MAT_DIALOG_DATA);
  private readonly dialog = inject(MatDialog);
  private readonly configService = inject(CONFIG_SERVICE);

  @Input() deviceId = this.data.deviceId;
  @Input() hostName = '';
  @Input() title = this.data.title;

  hostMissing = signal(false);

  copyFromAnother = signal(false);
  anotherDeviceUuid = '';
  copyFromAnotherErrorMessage = signal('');

  ngOnInit() {}

  startNewConfig() {
    this.dialog.closeAll();
    const dialogRef = this.dialog.open(Wizard, {
      data: {
        deviceId: this.deviceId,
        source: 'new',
      },
      autoFocus: false,
    });

    dialogRef.afterClosed().subscribe((permission: string | null) => {});
  }

  openWizard(source: 'new' | 'copy', config: DeviceConfig | null) {
    this.dialog.closeAll();
    this.dialog.open(Wizard, {
      data: {
        deviceId: this.deviceId,
        source,
        config,
      },
      autoFocus: false,
    });
  }

  copyFromHost() {
    this.configService
      .getHostDefaultDeviceConfig(this.hostName)
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
