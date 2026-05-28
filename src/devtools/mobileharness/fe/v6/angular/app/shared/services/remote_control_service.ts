import {Injectable, inject} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';
import {Observable, of, throwError} from 'rxjs';
import {catchError, filter, switchMap, tap} from 'rxjs/operators';
import {
  CheckRemoteControlEligibilityResponse,
  DeviceProxyType,
  DeviceTarget,
  EligibilityStatus,
  RemoteControlDevicesRequest,
} from '@deviceinfra/app/core/models/host_overview';
import {HOST_SERVICE} from '@deviceinfra/app/core/services/host/host_service';
import {ConfirmDialog} from '@deviceinfra/app/shared/components/confirm_dialog/confirm_dialog';
import {SnackBarService} from '@deviceinfra/app/shared/services/snackbar_service';
import {openInNewTab} from '@deviceinfra/app/shared/utils/safe_dom';
import {RemoteControlDialog} from '../components/remote_control/dialog/remote_control_dialog';
import {AccessDeniedContent} from '../components/remote_control/feedback/access_denied_content';
import {ConnectionErrorContent} from '../components/remote_control/feedback/connection_error_content';
import {IncompatibleDevicesContent} from '../components/remote_control/feedback/incompatible_devices_content';
import {
  PROXY_TYPE_LABELS,
  RemoteControlDeviceInfo,
} from '../components/remote_control/remote_control.types';

/** Service for managing remote control sessions. */
@Injectable({providedIn: 'root'})
export class RemoteControlService {
  private readonly dialog = inject(MatDialog);
  private readonly hostService = inject(HOST_SERVICE);
  private readonly snackBar = inject(SnackBarService);

  startRemoteControl(
    hostName: string,
    selectedDevices: RemoteControlDeviceInfo[],
    isSubDevice = false,
  ): Observable<unknown> {
    // 1. Quantity Check
    if (selectedDevices.length === 0) {
      this.snackBar.showError('Please select at least one device.');
      return of(undefined);
    }
    if (selectedDevices.length > 3) {
      this.snackBar.showError(
        'Can not remote control more than 3 devices at the same time.',
      );
      return of(undefined);
    }

    const targets: DeviceTarget[] = selectedDevices.map((d) => ({
      deviceId: d.id,
      subDeviceId: isSubDevice ? d.subDevices?.[0]?.id : undefined,
    }));

    return this.hostService
      .checkRemoteControlEligibility(hostName, targets)
      .pipe(
        switchMap((response) => {
          if (response.status !== EligibilityStatus.READY) {
            this.showIneligibilityDialog(response, selectedDevices);
            return of(undefined);
          }

          const dialogRef = this.dialog.open(RemoteControlDialog, {
            panelClass: 'remote-control-dialog-panel',
            width: '672px',
            maxHeight: '90vh',
            data: {
              devices: selectedDevices,
              eligibilityResults: response.results,
              sessionOptions: response.sessionOptions!,
              isSubDevice,
            },
            disableClose: true,
          });

          return dialogRef.afterClosed().pipe(
            filter((req): req is RemoteControlDevicesRequest => !!req),
            tap((req) => {
              this.snackBar.showSuccess(
                `Starting remote control for ${req.deviceConfigs.length} devices...`,
              );
            }),
            switchMap((req) =>
              this.hostService.remoteControlDevices(hostName, req).pipe(
                tap((res) => {
                  if (!res?.sessions || res.sessions.length === 0) {
                    this.snackBar.showError(
                      'No sessions found in the response.',
                    );
                    return;
                  }
                  for (const session of res.sessions) {
                    if (session.sessionUrl) {
                      console.log('acid url: ', session.sessionUrl);
                      openInNewTab(session.sessionUrl);
                    } else {
                      this.snackBar.showError(
                        `Invalid session URL for device ${session.deviceId}`,
                      );
                    }
                  }
                }),
                catchError((err: unknown) => {
                  const errorMessage =
                    err instanceof Error ? err.message : 'Unknown error';
                  this.snackBar.showError(
                    `Failed to start remote control: ${errorMessage}`,
                  );
                  return throwError(() => err);
                }),
              ),
            ),
          );
        }),
        catchError((err: unknown) => {
          console.error(err);
          const errorMessage =
            err instanceof Error ? err.message : 'Unknown error';
          this.snackBar.showError(
            `Failed to check proxy compatibility: ${errorMessage}`,
          );
          return throwError(() => err);
        }),
      );
  }

  private showIneligibilityDialog(
    response: CheckRemoteControlEligibilityResponse,
    selectedDevices: RemoteControlDeviceInfo[],
  ) {
    switch (response.status) {
      case EligibilityStatus.BLOCK_DEVICES_INELIGIBLE: {
        const invalidDevices = response.results
          .filter((d) => !d.isEligible)
          .map((d) => ({
            id: d.deviceId,
            reason: d.ineligibilityReason?.message || 'Unknown reason',
          }));
        this.dialog.open(ConfirmDialog, {
          panelClass: 'confirm-dialog-panel',
          data: {
            title: 'Unable to Start Remote Control',
            contentComponent: IncompatibleDevicesContent,
            contentComponentInputs: {
              'invalidDevices': invalidDevices,
              'isSingleDevice': selectedDevices.length === 1,
            },
            type: 'warning',
            primaryButtonLabel: 'Got it',
          },
        });
        break;
      }
      case EligibilityStatus.BLOCK_NO_COMMON_PROXY: {
        if (selectedDevices.length === 1) {
          const device = selectedDevices[0];
          this.dialog.open(ConfirmDialog, {
            panelClass: 'confirm-dialog-panel',
            data: {
              title: 'Connection Error',
              contentComponent: ConnectionErrorContent,
              contentComponentInputs: {
                'device': {id: device.id},
                'isTestbed': device.isTestbed,
              },
              type: 'warning',
              primaryButtonLabel: 'Got it',
            },
          });
        } else {
          const capabilitiesList = response.results
            .filter((d) => d.isEligible)
            .map((d) => {
              const modes = d.supportedProxyTypes
                .map((m: DeviceProxyType) => PROXY_TYPE_LABELS[m] || 'Unknown')
                .join(', ');
              return {id: d.deviceId, modes: modes || 'None'};
            });

          this.dialog.open(ConfirmDialog, {
            panelClass: 'confirm-dialog-panel',
            data: {
              title: 'Connection Error',
              contentComponent: ConnectionErrorContent,
              contentComponentInputs: {'capabilitiesList': capabilitiesList},
              type: 'warning',
              primaryButtonLabel: 'Got it',
            },
          });
        }
        break;
      }
      case EligibilityStatus.BLOCK_ALL_PERMISSION_DENIED: {
        this.dialog.open(ConfirmDialog, {
          panelClass: 'confirm-dialog-panel',
          data: {
            title: 'Access Denied',
            contentComponent: AccessDeniedContent,
            contentComponentInputs: {
              'devices': selectedDevices.map((d) => ({id: d.id})),
            },
            type: 'error',
            primaryButtonLabel: 'Got it',
          },
        });
        break;
      }
      default:
        break;
    }
  }
}
