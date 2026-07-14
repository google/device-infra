import {computed, DestroyRef, inject, Injectable, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatDialog} from '@angular/material/dialog';
import {Observable, of} from 'rxjs';
import {finalize, map, shareReplay, tap} from 'rxjs/operators';

import {
  LabServerActions,
  LifecycleActionType,
} from '../../../../core/models/host_action';
import {HostOverview} from '../../../../core/models/host_overview';
import {HOST_SERVICE} from '../../../../core/services/host/host_service';
import {ConfirmDialog} from '../../../../shared/components/confirm_dialog/confirm_dialog';
import {SnackBarService} from '../../../../shared/services/snackbar_service';
import {ActionNoPermissionContent} from './action_no_permission_content/action_no_permission_content';
import {LabServerOperationContent} from './lab_server_operation_content/lab_server_operation_content';
import {NoValidVersionsContent} from './release_dialog/no_valid_versions_content/no_valid_versions_content';
import {ReleaseDialog} from './release_dialog/release_dialog';
import {TrackingDialog} from './tracking_dialog/tracking_dialog';

/**
 * Service to handle Lab Server actions like start, stop, restart, release.
 */
@Injectable()
export class LabServerActionService {
  private readonly hostService = inject(HOST_SERVICE);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(SnackBarService);
  private readonly destroyRef = inject(DestroyRef);

  // States
  readonly isOpeningUpgrade = signal(false);
  readonly isOpeningRedeploy = signal(false); // TODO: it seems that this one is not used? right?
  readonly isOpeningRelease = signal(false);
  readonly isPreflightingStop = signal(false);
  readonly isStopping = signal(false);
  readonly isPreflightingStart = signal(false);
  readonly isStarting = signal(false);
  readonly isPreflightingRestart = signal(false);
  readonly isRestarting = signal(false);

  readonly passThroughFlags = signal<string>('');

  readonly isOpeningReleaseDialog = computed(() => {
    return (
      this.isOpeningRelease() ||
      this.isOpeningUpgrade() ||
      this.isOpeningRedeploy()
    );
  });

  isActionLoading(actionId: keyof LabServerActions): boolean {
    switch (actionId) {
      case 'release':
        return this.isOpeningReleaseDialog();
      case 'stop':
        return this.isPreflightingStop() || this.isStopping();
      case 'start':
        return this.isPreflightingStart() || this.isStarting();
      case 'restart':
        return this.isPreflightingRestart() || this.isRestarting();
      default:
        return false;
    }
  }

  // --- Preflight Handler ---
  private handlePreflightLifecycle(
    host: HostOverview,
    actionType: LifecycleActionType,
    callbacks: {
      onSuccess: () => void;
      onActionUnavailable: (actualActivity: string) => void;
    },
  ) {
    const isPreflightingSignal = this.getPreflightSignal(actionType);
    isPreflightingSignal.set(true);

    this.hostService
      .preflightLabServerLifecycle(host.hostName, actionType)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          isPreflightingSignal.set(false);
        }),
      )
      .subscribe({
        next: (response) => {
          if (response.permissionDenied) {
            this.showNoAccessDialog(host.hostName);
            return;
          }

          if (response.actionUnavailable) {
            this.showActionUnavailableDialog(
              response.actionUnavailable.actualActivity,
            );
            callbacks.onActionUnavailable(
              response.actionUnavailable.actualActivity,
            );
            return;
          }

          if (response.versionIssue) {
            // Open release dialog
            this.dialog.open(ReleaseDialog, {
              data: {
                hostName: host.hostName,
                releaseConfigs: response.versionIssue.availableVersions,
                passThroughFlags: this.passThroughFlags, // Use service's flags
              },
              panelClass: 'release-dialog-panel',
              disableClose: true,
            });

            this.snackBar.showWarning(
              `The current version is no longer supported or recognized: ${response.versionIssue.type}`,
            );
            return;
          }

          if (response.ready) {
            callbacks.onSuccess();
          }
        },
        error: (err) => {
          this.snackBar.showError(`Preflight failed: ${err.message}`);
        },
      });
  }

  private getPreflightSignal(actionType: LifecycleActionType) {
    switch (actionType) {
      case 'STOP':
        return this.isPreflightingStop;
      case 'START':
        return this.isPreflightingStart;
      case 'RESTART':
        return this.isPreflightingRestart;
      default:
        throw new Error(`Unknown action type: ${actionType}`);
    }
  }

  // --- Public Action Methods ---

  start(
    host: HostOverview,
    callbacks: {onActionUnavailable: (activity: string) => void},
  ) {
    this.handlePreflightLifecycle(host, 'START', {
      onActionUnavailable: callbacks.onActionUnavailable,
      onSuccess: () => {
        this.dialog.open(ConfirmDialog, {
          data: {
            title: 'Start Server?',
            contentComponent: LabServerOperationContent,
            contentComponentInputs: {
              'hostName': host.hostName,
              'status': host.labServer.activity?.state ?? 'UNKNOWN',
              'operation': 'START',
            },
            type: 'info',
            customIcon: 'play_circle',
            primaryButtonLabel: 'Start',
            secondaryButtonLabel: 'Cancel',
            onConfirm: () => {
              this.executeStart(host);
              return of(undefined);
            },
          },
          panelClass: 'confirm-dialog-panel',
        });
      },
    });
  }

  private executeStart(host: HostOverview) {
    this.isStarting.set(true);
    const response$ = this.hostService.startLabServer(host.hostName).pipe(
      finalize(() => {
        this.isStarting.set(false);
      }),
      shareReplay({bufferSize: 1, refCount: true}),
    );

    this.dialog.open(TrackingDialog, {
      data: {
        hostName: host.hostName,
        version: host.labServer.version || 'Unknown',
        response$,
        operation: 'START',
      },
      panelClass: 'tracking-dialog-panel',
      autoFocus: false,
    });
  }

  stop(
    host: HostOverview,
    callbacks: {onActionUnavailable: (activity: string) => void},
  ) {
    this.handlePreflightLifecycle(host, 'STOP', {
      onActionUnavailable: callbacks.onActionUnavailable,
      onSuccess: () => {
        this.dialog.open(ConfirmDialog, {
          data: {
            title: 'Stop Server?',
            contentComponent: LabServerOperationContent,
            contentComponentInputs: {
              'hostName': host.hostName,
              'status': host.labServer.activity?.state ?? 'UNKNOWN',
              'operation': 'STOP',
            },
            type: 'error',
            customIcon: 'stop_circle',
            primaryButtonLabel: 'Drain & Stop',
            secondaryButtonLabel: 'Cancel',
            onConfirm: () => {
              this.executeStop(host);
              return of(undefined);
            },
          },
          panelClass: 'confirm-dialog-panel',
        });
      },
    });
  }

  private executeStop(host: HostOverview) {
    this.isStopping.set(true);
    const response$ = this.hostService.stopLabServer(host.hostName).pipe(
      finalize(() => {
        this.isStopping.set(false);
      }),
      shareReplay({bufferSize: 1, refCount: true}),
    );

    this.dialog.open(TrackingDialog, {
      data: {
        hostName: host.hostName,
        version: host.labServer.version || 'Unknown',
        response$,
        operation: 'STOP',
      },
      panelClass: 'tracking-dialog-panel',
      autoFocus: false,
    });
  }

  restart(
    host: HostOverview,
    callbacks: {onActionUnavailable: (activity: string) => void},
  ) {
    this.handlePreflightLifecycle(host, 'RESTART', {
      onActionUnavailable: callbacks.onActionUnavailable,
      onSuccess: () => {
        this.dialog.open(ConfirmDialog, {
          data: {
            title: 'Restart Server?',
            contentComponent: LabServerOperationContent,
            contentComponentInputs: {
              'hostName': host.hostName,
              'status': host.labServer.activity?.state ?? 'UNKNOWN',
              'operation': 'RESTART',
            },
            type: 'warning',
            customIcon: 'restart_alt',
            primaryButtonLabel: 'Restart',
            secondaryButtonLabel: 'Cancel',
            onConfirm: () => {
              this.executeRestart(host);
              return of(undefined);
            },
          },
          panelClass: 'confirm-dialog-panel',
        });
      },
    });
  }

  private executeRestart(host: HostOverview) {
    this.isRestarting.set(true);
    const response$ = this.hostService.restartLabServer(host.hostName).pipe(
      finalize(() => {
        this.isRestarting.set(false);
      }),
      shareReplay({bufferSize: 1, refCount: true}),
    );

    this.dialog.open(TrackingDialog, {
      data: {
        hostName: host.hostName,
        version: host.labServer.version || 'Unknown',
        response$,
        operation: 'RESTART',
      },
      panelClass: 'tracking-dialog-panel',
      autoFocus: false,
    });
  }

  // --- Helper Dialogs ---

  private showNoAccessDialog(hostName: string) {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'No Access',
        contentComponent: ActionNoPermissionContent,
        contentComponentInputs: {
          'hostName': hostName,
        },
        type: 'error',
        customIcon: 'lock_outline',
        primaryButtonLabel: 'Close',
      },
      panelClass: 'confirm-dialog-panel',
    });
  }

  private showActionUnavailableDialog(actualActivity: string) {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Action Unavailable',
        content: `The Lab Server state has changed to ${actualActivity}. The page is refreshing in the background.`,
        type: 'warning',
        primaryButtonLabel: 'Close',
      },
    });
  }

  // --- Release Preflight ---
  preflightAndOpenRelease(
    host: HostOverview,
    options: {preSelectLatest?: boolean; preSelectCurrent?: boolean} = {},
  ): Observable<void> {
    const preSelectLatest = options.preSelectLatest ?? false;
    const preSelectCurrent = options.preSelectCurrent ?? false;
    if (preSelectLatest) {
      this.isOpeningUpgrade.set(true);
    } else if (preSelectCurrent) {
      this.isOpeningRedeploy.set(true);
    } else {
      this.isOpeningRelease.set(true);
    }
    return this.hostService.preflightLabServerRelease(host.hostName).pipe(
      takeUntilDestroyed(this.destroyRef),
      tap({
        next: (response) => {
          this.isOpeningRelease.set(false);
          this.isOpeningUpgrade.set(false);
          this.isOpeningRedeploy.set(false);
          if (response.permissionDenied) {
            this.showNoAccessDialog(host.hostName);
            return;
          }

          const versions = response.ready?.versions;
          if (!versions || versions.length === 0) {
            this.dialog.open(ConfirmDialog, {
              data: {
                title: 'No Valid Versions',
                contentComponent: NoValidVersionsContent,
                contentComponentInputs: {
                  'hostName': host.hostName,
                },
                type: 'warning',
                customIcon: 'warning_amber',
                primaryButtonLabel: 'Close',
              },
              panelClass: 'confirm-dialog-panel',
            });
            return;
          }

          this.dialog.open(ReleaseDialog, {
            data: {
              hostName: host.hostName,
              releaseConfigs: versions,
              passThroughFlags: this.passThroughFlags,
              preSelectLatest,
              preSelectCurrent,
            },
            autoFocus: false,
          });
        },
        error: (err) => {
          this.isOpeningRelease.set(false);
          this.isOpeningUpgrade.set(false);
          this.isOpeningRedeploy.set(false);
          this.snackBar.showError(
            `Failed to load release info: ${err.message}`,
          );
        },
      }),
      map(() => {}),
    );
  }
}
