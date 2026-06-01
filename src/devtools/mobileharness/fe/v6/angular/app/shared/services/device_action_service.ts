import {Injectable, inject} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';
import {Observable, throwError} from 'rxjs';
import {catchError, filter, switchMap, tap} from 'rxjs/operators';

import {
  GetLogcatResponse,
  QuarantineDialogData,
  ScreenshotDialogData,
  TakeScreenshotResponse,
} from '../../core/models/device_action';
import {DEVICE_SERVICE} from '../../core/services/device/device_service';
import {FlashDialog} from '../../features/device_detail/components/flash_dialog/flash_dialog';
import {LogcatLinkDialog} from '../../features/device_detail/components/logcat_link_dialog/logcat_link_dialog';
import {QuarantineDialog} from '../../features/device_detail/components/quarantine_dialog/quarantine_dialog';
import {ScreenshotDialog} from '../../features/device_detail/components/screenshot_dialog/screenshot_dialog';
import {ConfirmDialog} from '../components/confirm_dialog/confirm_dialog';
import {openInNewTab} from '../utils/safe_dom';
import {SnackBarService} from './snackbar_service';

/**
 * Service to encapsulate shared UI action orchestration for devices (Screenshot, Logcat, Flash, Quarantine).
 * Resourced and shared between DeviceActionBar and HostOverview.
 */
@Injectable({providedIn: 'root'})
export class DeviceActionService {
  private readonly deviceService = inject(DEVICE_SERVICE);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(SnackBarService);

  takeScreenshot(deviceId: string): Observable<TakeScreenshotResponse> {
    this.snackBar.showInfo('Taking screenshot...');
    return this.deviceService.takeScreenshot(deviceId).pipe(
      tap((response) => {
        this.snackBar.showSuccess('Screenshot taken successfully.');
        this.dialog.open(ScreenshotDialog, {
          data: {
            deviceId,
            screenshotUrl: response.screenshotUrl,
            capturedAt: response.capturedAt,
          } as ScreenshotDialogData,
        });
      }),
      catchError((err) => {
        this.snackBar.showError('Failed to take screenshot.');
        console.error(err);
        return throwError(() => err);
      }),
    );
  }

  getLogcat(deviceId: string): Observable<GetLogcatResponse> {
    this.snackBar.showInfo('Getting logcat...');
    return this.deviceService.getLogcat(deviceId).pipe(
      tap((response) => {
        this.snackBar.showSuccess(
          'Logcat retrieved successfully. And opened in a new browser tab.',
        );
        openInNewTab(response.logUrl);

        // Use dedicated LogcatLinkDialog to show the link prominently and bypass popup blockers.
        this.dialog.open(LogcatLinkDialog, {
          data: {
            logUrl: response.logUrl,
          },
        });
      }),
      catchError((err) => {
        this.snackBar.showError('Failed to get logcat.');
        console.error(err);
        return throwError(() => err);
      }),
    );
  }

  flashDevice(
    deviceId: string,
    hostName: string,
    params?: {deviceType?: string; requiredDimensions?: string},
  ): void {
    this.dialog.open(FlashDialog, {
      data: {
        deviceId,
        hostName,
        deviceType: params?.deviceType || '',
        requiredDimensions: params?.requiredDimensions || '',
      },
    });
  }

  quarantineDevice(
    deviceId: string,
    options?: {
      quarantineInfo?: {isQuarantined: boolean; expiry?: string};
    },
  ): Observable<unknown> {
    const handleQuarantineFlow$ = (
      isQuarantined: boolean,
    ): Observable<unknown> => {
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
        return unquarantineDialogRef.afterClosed().pipe(
          filter((result) => result === 'primary'),
          tap(() => {
            this.snackBar.showInfo('Unquarantining device...');
          }),
          switchMap(() => this.deviceService.unquarantineDevice(deviceId)),
          tap(() => {
            this.snackBar.showSuccess(
              'Device unquarantined successfully.\nIt may take a few minutes to take effect at the UI side.',
            );
          }),
          catchError((err) => {
            this.snackBar.showError('Failed to unquarantine device.');
            console.error(err);
            return throwError(() => err);
          }),
        );
      } else {
        const dialogRef = this.dialog.open(QuarantineDialog, {
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
        return dialogRef.afterClosed();
      }
    };

    if (options?.quarantineInfo) {
      return handleQuarantineFlow$(options.quarantineInfo.isQuarantined);
    } else {
      this.snackBar.showInfo('Checking device quarantine status...');
      return this.deviceService.getDeviceHeaderInfo(deviceId).pipe(
        switchMap((headerInfo) => {
          const isQuarantined = headerInfo.quarantine?.isQuarantined ?? false;
          return handleQuarantineFlow$(isQuarantined);
        }),
        catchError((err) => {
          this.snackBar.showError('Failed to fetch device quarantine status.');
          console.error(err);
          return throwError(() => err);
        }),
      );
    }
  }

  changeQuarantine(deviceId: string, expiry?: string): Observable<unknown> {
    const dialogRef = this.dialog.open(QuarantineDialog, {
      data: {
        deviceId,
        isUpdate: true,
        currentExpiry: expiry || '',
        title: `Update Quarantine Duration - ${deviceId}`,
        description: `Please specify a new duration for how long <strong>${deviceId}</strong> should remain quarantined, starting from now.`,
        confirmText: 'Update',
      } as QuarantineDialogData,
    });
    return dialogRef.afterClosed();
  }
}
