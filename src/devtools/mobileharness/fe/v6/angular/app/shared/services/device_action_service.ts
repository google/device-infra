import {Injectable, inject} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';
import {Observable, throwError} from 'rxjs';
import {
  catchError,
  filter,
  finalize,
  map,
  switchMap,
  take,
  tap,
} from 'rxjs/operators';

import {
  GetLogcatResponse,
  QuarantineDialogData,
  ScreenshotDialogData,
  TakeScreenshotResponse,
} from '../../core/models/device_action';
import {DeviceConfig as DeviceConfigModel} from '../../core/models/device_config_models';
import {DEVICE_SERVICE} from '../../core/services/device/device_service';
import {Environment} from '../../core/services/environment';
import {DeviceConfig} from '../../features/device_detail/components/device_config/device_config';
import {DeviceEmpty} from '../../features/device_detail/components/device_config/device_empty/device_empty';
import {DeviceSettings} from '../../features/device_detail/components/device_config/device_settings/device_settings';
import {DeviceWizard} from '../../features/device_detail/components/device_config/device_wizard/device_wizard';
import {FlashDialog} from '../../features/device_detail/components/flash_dialog/flash_dialog';
import {LogcatLinkDialog} from '../../features/device_detail/components/logcat_link_dialog/logcat_link_dialog';
import {QuarantineDialog} from '../../features/device_detail/components/quarantine_dialog/quarantine_dialog';
import {ScreenshotDialog} from '../../features/device_detail/components/screenshot_dialog/screenshot_dialog';
import {ActionErrorContent} from '../components/action_error_content/action_error_content';
import {ConfirmDialog} from '../components/confirm_dialog/confirm_dialog';
import {AccessDeniedContent} from '../components/remote_control/feedback/access_denied_content';
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
  private readonly environment = inject(Environment);

  takeScreenshot(deviceId: string): Observable<TakeScreenshotResponse> {
    const snackBarRef = this.snackBar.showInProgress('Taking screenshot...');
    return this.deviceService.takeScreenshot(deviceId).pipe(
      map(throwBackendErrorIfPresent),
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
      catchError((err) =>
        this.handleActionError(
          err,
          deviceId,
          'take screenshot of',
          'Failed to take screenshot',
        ),
      ),
      finalize(() => {
        snackBarRef.dismiss();
      }),
    );
  }

  getLogcat(deviceId: string): Observable<GetLogcatResponse> {
    const snackBarRef = this.snackBar.showInProgress('Getting logcat...');
    return this.deviceService.getLogcat(deviceId).pipe(
      map(throwBackendErrorIfPresent),
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
      catchError((err) =>
        this.handleActionError(
          err,
          deviceId,
          'get logcat of',
          'Failed to get logcat',
        ),
      ),
      finalize(() => {
        snackBarRef.dismiss();
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

  configureDevice(
    deviceId: string,
    hostName: string,
    hostIp: string,
    universe?: string,
  ): void {
    const dialogRef = this.dialog.open(DeviceConfig, {
      data: {deviceId, hostName, hostIp, universe},
      autoFocus: false,
    });

    dialogRef
      .afterClosed()
      .pipe(take(1))
      .subscribe((result) => {
        if (!result) {
          return;
        }

        if (result.action === 'reset') {
          this.resetConfiguration(
            result.deviceId,
            hostName,
            hostIp,
            result.universe,
          );
          return;
        }

        this.createOrCopyConfiguration(
          result.action,
          result.deviceId,
          result.config,
          hostName,
          hostIp,
          result.universe,
        );
      });
  }

  resetConfiguration(
    deviceId: string,
    hostName: string,
    hostIp: string,
    universe?: string,
  ) {
    this.dialog
      .open(DeviceEmpty, {
        data: {
          deviceId,
          hostName,
          hostIp,
          universe,
          title:
            'You are about to clear the existing configuration for this device. Your current settings will be discarded. Please choose how you want to proceed.',
        },
        autoFocus: false,
      })
      .afterClosed()
      .pipe(take(1))
      .subscribe((result) => {
        if (!result) {
          return;
        }

        this.createOrCopyConfiguration(
          result.action,
          result.deviceId,
          result.config,
          hostName,
          hostIp,
          result.universe,
        );
      });
  }

  createOrCopyConfiguration(
    action: string,
    deviceId: string,
    config: DeviceConfigModel | null,
    hostName: string,
    hostIp: string,
    universe?: string,
  ) {
    if (
      (this.environment.isGoogleInternal() && action === 'new') ||
      action === 'copy'
    ) {
      this.openDeviceWizard(action, deviceId, config, universe);
    }

    if (!this.environment.isGoogleInternal() && action === 'new') {
      this.openDeviceSettings(deviceId, config, hostName, hostIp, universe);
    }
  }

  openDeviceWizard(
    action: string,
    deviceId: string,
    config: DeviceConfigModel | null,
    universe?: string,
  ) {
    this.dialog.open(DeviceWizard, {
      data: {source: action, deviceId, config, universe},
      autoFocus: false,
    });
  }

  openDeviceSettings(
    deviceId: string,
    config: DeviceConfigModel | null,
    hostName: string,
    hostIp: string,
    universe?: string,
  ) {
    const dialogRef = this.dialog.open(DeviceSettings, {
      data: {deviceId, config, universe},
      autoFocus: false,
    });

    dialogRef
      .afterClosed()
      .pipe(take(1))
      .subscribe((result) => {
        if (!result) {
          return;
        }
        if (result.action === 'reset') {
          this.resetConfiguration(
            result.deviceId,
            hostName,
            hostIp,
            result.universe,
          );
        }
      });
  }

  private handleActionError(
    err: unknown,
    deviceId: string,
    actionDesc: string,
    failedActionMsg: string,
  ): Observable<never> {
    if (err && typeof err === 'object' && 'errorType' in err) {
      // Logical error from backend
      const logicalErr = err as {
        errorType: string;
        errorMessage?: string;
      };
      if (logicalErr.errorType === 'PERMISSION_DENIED') {
        this.dialog.open(ConfirmDialog, {
          data: {
            title: 'Access Denied',
            contentComponent: AccessDeniedContent,
            contentComponentInputs: {
              devices: [{id: deviceId}],
              action: actionDesc,
            },
            type: 'error',
            primaryButtonLabel: 'Close',
          },
        });
      } else {
        this.dialog.open(ActionErrorContent, {
          data: {
            errorMessage: logicalErr.errorMessage || 'Unknown error',
            errorDetails: `Error Type: ${logicalErr.errorType}`,
          },
        });
      }
    } else {
      // RPC or other unexpected error
      this.snackBar.showError(`${failedActionMsg}.`);
      console.error(err);
      this.dialog.open(ActionErrorContent, {
        data: {
          errorMessage: (err as {message?: string})?.message || failedActionMsg,
          errorDetails: formatErrorDetails(err),
        },
      });
    }
    return throwError(() => err);
  }
}

interface BackendError extends Error {
  errorType: string;
  errorMessage?: string;
}

function throwBackendErrorIfPresent<
  T extends {errorType?: string; errorMessage?: string},
>(response: T): T {
  if (response.errorType) {
    const error = new Error(
      response.errorMessage || 'Unknown error',
    ) as BackendError;
    error.errorType = response.errorType;
    error.errorMessage = response.errorMessage;
    throw error;
  }
  return response;
}

function formatErrorDetails(err: unknown): string {
  if (!err) {
    return '';
  }
  if (err instanceof Error && err.stack) {
    return err.stack;
  }
  if (typeof err === 'object' && err !== null) {
    const errObj = err as Record<string, unknown>;
    if ('error' in errObj && errObj['error']) {
      return typeof errObj['error'] === 'object'
        ? safeJsonStringify(errObj['error'])
        : String(errObj['error']);
    }
    return safeJsonStringify(err);
  }
  return String(err);
}

function safeJsonStringify(obj: unknown): string {
  try {
    return JSON.stringify(obj, null, 2);
  } catch (e) {
    return String(obj);
  }
}
