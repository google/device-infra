import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  Input,
  signal,
  WritableSignal,
} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatTooltipModule} from '@angular/material/tooltip';
import {Observable} from 'rxjs';
import {finalize} from 'rxjs/operators';
import {
  ActionButtonState,
  DeviceActions,
  LogcatDialogData,
  QuarantineDialogData,
  ScreenshotDialogData,
} from '../../../../core/models/device_action';
import type {DeviceOverviewPageData} from '../../../../core/models/device_overview';
import {DeviceSummary} from '../../../../core/models/host_overview';
import {DEVICE_SERVICE} from '../../../../core/services/device/device_service';
import {ConfirmDialog} from '../../../../shared/components/confirm_dialog/confirm_dialog';
import {RemoteControlService} from '../../../../shared/services/remote_control_service';
import {SnackBarService} from '../../../../shared/services/snackbar_service';
import {DeviceConfig} from '../device_config/device_config';
import {DeviceEmpty} from '../device_config/device_empty/device_empty';

import {DeviceSettings} from '../device_config/device_settings/device_settings';
// deviceinfra:google3-replace-end
import {FlashDialog} from '../flash_dialog/flash_dialog';
import {LogcatDialog} from '../logcat_dialog/logcat_dialog';
import {QuarantineDialog} from '../quarantine_dialog/quarantine_dialog';
import {ScreenshotDialog} from '../screenshot_dialog/screenshot_dialog';

/**
 * Component for the action bar in the device detail page header.
 */
@Component({
  selector: 'app-device-action-bar',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatMenuModule, MatTooltipModule],
  templateUrl: './device_action_bar.ng.html',
  styleUrl: './device_action_bar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeviceActionBar {
  private readonly deviceService = inject(DEVICE_SERVICE);
  private readonly remoteControlService = inject(RemoteControlService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(SnackBarService);

  @Input({required: true}) pageData!: DeviceOverviewPageData;

  takingScreenshot = signal(false);
  gettingLogcat = signal(false);
  unquarantining = signal(false);
  checkingRemoteControl = signal(false);

  get actions() {
    return this.pageData.headerInfo.actions;
  }

  get quarantineInfo() {
    return this.pageData.headerInfo.quarantine;
  }

  get deviceId() {
    return this.pageData.overview.id;
  }

  get hostName() {
    return this.pageData.overview.host.name;
  }

  readonly onConfiguration = this.openConfiguration.bind(this);

  readonly onScreenshot = this.takeScreenshot.bind(this);

  readonly onRemoteControl = this.remoteControl.bind(this);

  readonly onFlash = this.flashDevice.bind(this);

  readonly onLogcat = this.getLogcat.bind(this);

  readonly onQuarantine = this.quarantineDevice.bind(this);

  readonly onChangeQuarantine = this.changeQuarantine.bind(this);

  getAction(key: keyof DeviceActions): ActionButtonState | undefined {
    return (this.actions as unknown as Record<string, ActionButtonState>)?.[
      key
    ];
  }

  openConfiguration(): void {
    const deviceId = this.deviceId;
    const hostName = this.hostName;
    const dialogRef = this.dialog.open(DeviceConfig, {
      data: {deviceId, hostName},
      autoFocus: false,
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (!result) {
        return;
      }

      if (result.action === 'reset') {
        this.resetConfiguration(result.deviceId, hostName);
        return;
      }

      if (result.action === 'new' || result.action === 'copy') {
        this.createorcopyConfiguration(result.action, deviceId, result.config);
      }
    });
  }

  resetConfiguration(deviceId: string, hostName: string) {
    this.dialog
      .open(DeviceEmpty, {
        data: {
          deviceId,
          hostName,
          title:
            'You are about to clear the existing configuration for this device. Your current settings will be discarded. Please choose how you want to proceed.',
        },
        autoFocus: false,
      })
      .afterClosed()
      .subscribe((result) => {
        if (!result) {
          return;
        }

        this.createorcopyConfiguration(
          result.action,
          result.deviceId,
          result.config,
        );
      });
  }

  createorcopyConfiguration(
    action: string,
    deviceId: string,
    config: DeviceConfig | null,
  ) {
    this.dialog.open(DeviceSettings, {
      data: {deviceId, config},
      autoFocus: false,
    });
  }

  takeScreenshot(): void {
    const deviceId = this.deviceId;
    this.handleAsyncAction(
      this.takingScreenshot,
      'Taking screenshot...',
      this.deviceService.takeScreenshot(deviceId),
      (response) => {
        this.snackBar.showSuccess('Screenshot taken successfully.');
        this.dialog.open(ScreenshotDialog, {
          data: {
            deviceId,
            screenshotUrl: response.screenshotUrl,
            capturedAt: response.capturedAt,
          } as ScreenshotDialogData,
        });
      },
      'Failed to take screenshot.',
    );
  }

  remoteControl(): void {
    const overview = this.pageData.overview;
    const deviceSummary: DeviceSummary = {
      id: overview.id,
      healthState: {
        health: overview.healthAndActivity.state,
        title: overview.healthAndActivity.title,
        tooltip: overview.healthAndActivity.subtitle || '',
      },
      types: overview.healthAndActivity.deviceTypes,
      deviceStatus: overview.healthAndActivity.deviceStatus,
      label: '',
      requiredDims: '',
      model: overview.basicInfo.model || '',
      version: overview.basicInfo.version || '',
      subDevices: overview.subDevices,
    };
    this.remoteControlService.startRemoteControl(this.hostName, [
      deviceSummary,
    ]);
  }

  flashDevice(): void {
    const params = this.actions?.flash?.params;
    this.dialog.open(FlashDialog, {
      data: {
        deviceId: this.deviceId,
        hostName: this.hostName,
        deviceType: params?.deviceType || '',
        requiredDimensions: params?.requiredDimensions || '',
      },
    });
  }

  getLogcat(): void {
    const deviceId = this.deviceId;
    this.handleAsyncAction(
      this.gettingLogcat,
      'Getting logcat...',
      this.deviceService.getLogcat(deviceId),
      (response) => {
        this.snackBar.showSuccess('Logcat retrieved successfully.');
        fetch(response.logUrl)
          .then((res) => res.text())
          .then((logContent) => {
            this.dialog.open(LogcatDialog, {
              data: {
                deviceId,
                logContent,
                capturedAt: response.capturedAt,
                logUrl: response.logUrl,
              } as LogcatDialogData,
            });
          })
          .catch((err) => {
            this.snackBar.showError('Failed to fetch log content.');
            console.error('Failed to fetch log content:', err);
          });
      },
      'Failed to get logcat.',
    );
  }

  quarantineDevice(): void {
    const deviceId = this.deviceId;
    const {isQuarantined} = this.quarantineInfo ?? {
      isQuarantined: false,
      expiry: '',
    };
    if (isQuarantined) {
      const unquarantineDialogRef = this.dialog.open(ConfirmDialog, {
        data: {
          title: `Unquarantine Device ${deviceId}?`,
          content:
            'This action will make the device available for test allocation immediately.',
          type: 'info',
          primaryButtonLabel: 'Unquarantine',
          secondaryButtonLabel: 'Cancel',
        },
      });
      unquarantineDialogRef.afterClosed().subscribe((result) => {
        if (result === 'primary') {
          this.handleAsyncAction(
            this.unquarantining,
            'Unquarantining device...',
            this.deviceService.unquarantineDevice(deviceId),
            () => {
              this.snackBar.showSuccess('Device unquarantined successfully.');
              // TODO: refresh device header info or page data.
            },
            'Failed to unquarantine device.',
          );
        }
      });
    } else {
      this.dialog.open(QuarantineDialog, {
        data: {
          deviceId,
          isUpdate: false,
          title: `Quarantine Device - ${deviceId}`,
          description: `Quarantining device <strong>${deviceId}</strong> will make it <strong>unavailable</strong> for new test allocations for the specified duration.
             <ul class="list-disc list-inside text-xs text-gray-600 mt-2 space-y-1">
                 <li>If a test is currently running, quarantine will take effect after it finishes.</li>
                 <li>The device will be automatically unquarantined when the duration expires.</li>
                 <li>You can manually unquarantine or change the duration at any time after the device is quarantined.</li>
             </ul>`,
          confirmText: 'Quarantine',
        } as QuarantineDialogData,
      });
    }
  }

  changeQuarantine(): void {
    const deviceId = this.deviceId;
    const {expiry} = this.quarantineInfo ?? {
      isQuarantined: false,
      expiry: '',
    };
    this.dialog.open(QuarantineDialog, {
      data: {
        deviceId,
        isUpdate: true,
        currentExpiry: expiry,
        title: `Update Quarantine Duration - ${deviceId}`,
        description: `Please specify a new duration for how long <strong>${deviceId}</strong> should remain quarantined, starting from now.`,
        confirmText: 'Update',
      } as QuarantineDialogData,
    });
  }

  private handleAsyncAction<T>(
    loadingSignal: WritableSignal<boolean>,
    progressMessage: string,
    actionObservable: Observable<T>,
    successCallback: (result: T) => void,
    errorMessage: string,
  ) {
    loadingSignal.set(true);
    const snackBarRef = this.snackBar.showInProgress(progressMessage);

    actionObservable
      .pipe(
        finalize(() => {
          loadingSignal.set(false);
          snackBarRef.dismiss();
        }),
      )
      .subscribe({
        next: successCallback,
        error: (err) => {
          this.snackBar.showError(errorMessage);
          console.error(errorMessage, err);
        },
      });
  }
}
