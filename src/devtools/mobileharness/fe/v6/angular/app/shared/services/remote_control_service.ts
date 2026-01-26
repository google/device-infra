import {Injectable, inject} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';
import {
  CheckRemoteControlEligibilityResponse,
  DeviceProxyType,
  DeviceSummary,
  EligibilityStatus,
  RemoteControlDevicesRequest,
} from 'app/core/models/host_overview';
import {HOST_SERVICE} from 'app/core/services/host/host_service';
import {ConfirmDialog} from 'app/shared/components/confirm_dialog/confirm_dialog';
import {SnackBarService} from 'app/shared/services/snackbar_service';
import {openInNewTab} from 'app/shared/utils/safe_dom';
import {RemoteControlDialog} from '../components/remote_control/dialog/remote_control_dialog';
import {AccessDeniedContent} from '../components/remote_control/feedback/access_denied_content';
import {ConnectionErrorContent} from '../components/remote_control/feedback/connection_error_content';
import {IncompatibleDevicesContent} from '../components/remote_control/feedback/incompatible_devices_content';

/** Labels for DeviceProxyType enum values. */
export const PROXY_TYPE_LABELS: Record<number, string> = {
  0: 'Auto (Default)',
  1: 'ADB & Video',
  2: 'ADB Console',
  3: 'USB-over-IP',
  4: 'SSH',
  5: 'Video Only',
};

/** Service for managing remote control sessions. */
@Injectable({providedIn: 'root'})
export class RemoteControlService {
  private readonly dialog = inject(MatDialog);
  private readonly hostService = inject(HOST_SERVICE);
  private readonly snackBar = inject(SnackBarService);

  startRemoteControl(hostName: string, selectedDevices: DeviceSummary[]) {
    // 1. Quantity Check
    if (selectedDevices.length === 0) {
      this.snackBar.showError('Please select at least one device.');
      return;
    }
    if (selectedDevices.length > 3) {
      this.snackBar.showError(
        'Can not remote control more than 3 devices at the same time.',
      );
      return;
    }

    const deviceControlIds = selectedDevices.map((d) => d.id);
    this.hostService
      .checkRemoteControlEligibility(hostName, deviceControlIds)
      .subscribe({
        next: (response: CheckRemoteControlEligibilityResponse) => {
          this.handleEligibilityResponse(response, selectedDevices, hostName);
        },
        error: (err: unknown) => {
          console.error(err);
          const errorMessage =
            err instanceof Error ? err.message : 'Unknown error';
          this.snackBar.showError(
            `Failed to check proxy compatibility: ${errorMessage}`,
          );
        },
      });
  }

  private handleEligibilityResponse(
    response: CheckRemoteControlEligibilityResponse,
    selectedDevices: DeviceSummary[],
    hostName: string,
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
              invalidDevices,
              isSingleDevice: selectedDevices.length === 1,
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
                device: {id: device.id},
                isTestbed: this.isTestbed(device),
              },
              type: 'warning',
              primaryButtonLabel: 'Got it',
            },
          });
        } else {
          const capabilitiesList = response.results.map((d) => {
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
              contentComponentInputs: {capabilitiesList},
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
              devices: selectedDevices.map((d) => ({id: d.id})),
            },
            type: 'error',
            primaryButtonLabel: 'Got it',
          },
        });
        break;
      }
      case EligibilityStatus.READY: {
        this.openRemoteControlDialog(selectedDevices, response, hostName);
        break;
      }
      default:
        break;
    }
  }

  private openRemoteControlDialog(
    selectedDevices: DeviceSummary[],
    eligibilityResponse: CheckRemoteControlEligibilityResponse,
    hostName: string,
  ) {
    const dialogRef = this.dialog.open(RemoteControlDialog, {
      panelClass: 'remote-control-dialog-panel',
      width: '672px',
      maxHeight: '90vh',
      data: {
        devices: selectedDevices,
        eligibilityResults: eligibilityResponse.results,
        sessionOptions: eligibilityResponse.sessionOptions!,
      },
      disableClose: true,
    });

    dialogRef
      .afterClosed()
      .subscribe((req: RemoteControlDevicesRequest | undefined) => {
        if (req) {
          this.startRemoteControlSessions(req, hostName);
        }
      });
  }

  private startRemoteControlSessions(
    req: RemoteControlDevicesRequest,
    hostName: string,
  ) {
    this.snackBar.showSuccess(
      `Starting remote control for ${req.deviceConfigs.length} devices...`,
    );

    this.hostService.remoteControlDevices(hostName, req).subscribe({
      next: (res) => {
        res.sessions.forEach((session) => {
          if (session.sessionUrl) {
            openInNewTab(session.sessionUrl);
          } else {
            this.snackBar.showError(
              `Invalid session URL for device ${session.deviceId}`,
            );
          }
        });
      },
      error: (err: unknown) => {
        const errorMessage =
          err instanceof Error ? err.message : 'Unknown error';
        this.snackBar.showError(
          `Failed to start remote control: ${errorMessage}`,
        );
      },
    });
  }

  isTestbed(element: DeviceSummary): boolean {
    return (
      element.types.some((t) => t.type === 'TestbedDevice') &&
      !!element.subDevices &&
      element.subDevices.length > 0
    );
  }
}
