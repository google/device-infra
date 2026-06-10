import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatTooltipModule} from '@angular/material/tooltip';
import {ActivatedRoute, Router} from '@angular/router';
import {UrlService} from '@deviceinfra/app/core/services/url_service';
import {throwError} from 'rxjs';
import {catchError, take, tap} from 'rxjs/operators';
import {HOST_SERVICE} from '../../../../core/services/host/host_service';
import {ConfirmDialog} from '../../../../shared/components/confirm_dialog/confirm_dialog';
import {DecommissionContent} from '../../../../shared/components/decommission_content/decommission_content';
import {SnackBarService} from '../../../../shared/services/snackbar_service';

import {ActionBarAction} from '@deviceinfra/app/core/constants/action_bar_config';
import {
  APP_DATA,
  getLegacyFeUrl,
} from '@deviceinfra/app/core/models/app_data';
import {ActionButtonState} from '../../../../core/models/action_common';
import {DeviceActions} from '../../../../core/models/device_action';
import type {DeviceOverviewPageData} from '../../../../core/models/device_overview';
import {Environment} from '../../../../core/services/environment';
import {useDeviceActions} from '../../../../shared/composables/device_actions';
import {ComingSoonService} from '../../../../shared/services/coming_soon_service';
import {DeviceConfig} from '../device_config/device_config';
import {DeviceEmpty} from '../device_config/device_empty/device_empty';
import {DeviceSettings} from '../device_config/device_settings/device_settings';
import {DeviceWizard} from '../device_config/device_wizard/device_wizard';

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
  protected readonly deviceActions = useDeviceActions();
  private readonly dialog = inject(MatDialog);
  private readonly environment = inject(Environment);
  private readonly route = inject(ActivatedRoute);
  private readonly comingSoonService = inject(ComingSoonService);
  private readonly appData = inject(APP_DATA);
  private readonly hostService = inject(HOST_SERVICE);
  private readonly router = inject(Router);
  private readonly snackBar = inject(SnackBarService);
  private readonly urlService = inject(UrlService);

  readonly legacyFeUrl = getLegacyFeUrl(this.appData.applicationId ?? '');

  readonly pageData = input.required<DeviceOverviewPageData>();

  protected readonly actions = computed(
    () => this.pageData().headerInfo.actions,
  );

  protected readonly quarantineInfo = computed(
    () => this.pageData().headerInfo.quarantine,
  );

  protected readonly deviceId = computed(() => this.pageData().overview.id);

  protected readonly hostName = computed(
    () => this.pageData().overview.host.name,
  );

  get universe() {
    return this.route.snapshot.queryParamMap.get('universe') || '';
  }

  readonly onDecommission = () => {
    const dialogData = {
      title: 'Decommission Device',
      contentComponent: DecommissionContent,
      contentComponentInputs: {'deviceIds': [this.deviceId()]},
      type: 'error',
      primaryButtonLabel: 'Decommission',
      secondaryButtonLabel: 'Cancel',
      onConfirm: () =>
        this.hostService
          .decommissionMissingDevices(this.hostName(), [this.deviceId()])
          .pipe(
            tap(() => {
              this.snackBar.showSuccess(
                `Device ${this.deviceId()} decommissioned successfully.\nIt may take a few minutes to take effect on the UI side.`,
              );
            }),
            catchError((err) => {
              this.snackBar.showError(
                err.message || 'Failed to decommission device',
              );
              return throwError(() => err);
            }),
          ),
    };

    const dialogRef = this.dialog.open(ConfirmDialog, {
      panelClass: 'confirm-dialog-panel',
      data: dialogData,
      disableClose: true,
    });

    dialogRef
      .afterClosed()
      .pipe(take(1))
      .subscribe((result) => {
        if (result === 'primary') {
          const hostIp = this.pageData().overview.host.ip;
          this.urlService.notifyNavigated('host_details', {
            'host_name': this.hostName(),
            'host_ip': hostIp,
          });
          this.router.navigate(['/hosts', this.hostName()], {
            queryParamsHandling: 'merge',
          });
        }
      });
  };

  readonly onConfiguration = this.openConfiguration.bind(this);

  readonly onScreenshot = () => {
    this.deviceActions.takeScreenshot(this.deviceId());
  };

  readonly onRemoteControl = () => {
    this.deviceActions.startRemoteControl(
      this.hostName(),
      this.pageData().overview,
    );
  };

  readonly onFlash = () => {
    this.deviceActions.flashDevice(
      this.deviceId(),
      this.hostName(),
      this.actions()?.flash?.params,
    );
  };

  readonly onLogcat = () => {
    this.deviceActions.getLogcat(this.deviceId());
  };

  readonly onQuarantine = () => {
    const quarantine = this.quarantineInfo()
      ? {
          isQuarantined: this.quarantineInfo()!.isQuarantined,
          expiry: this.quarantineInfo()!.expiry,
        }
      : undefined;
    this.deviceActions.quarantineDevice(this.deviceId(), {
      quarantineInfo: quarantine,
    });
  };

  readonly onChangeQuarantine = () => {
    this.deviceActions.changeQuarantine(
      this.deviceId(),
      this.quarantineInfo()?.expiry,
    );
  };

  getAction(key: keyof DeviceActions): ActionButtonState | undefined {
    if (key === 'flash') {
      return this.actions()?.flash?.state;
    }
    return (this.actions() as unknown as Record<string, ActionButtonState>)?.[
      key
    ];
  }

  isActionVisible(key: keyof DeviceActions): boolean {
    return this.getAction(key)?.visible ?? false;
  }

  readonly hasXlMoreMenuItems = computed(() => {
    const visible =
      this.isActionVisible('flash') ||
      this.isActionVisible('logcat') ||
      this.isActionVisible('quarantine') ||
      this.isActionVisible('decommission');
    console.log('hasXlMoreMenuItems executed, result:', visible);
    return visible;
  });

  readonly hasSmActionMenuItems = computed(() => {
    const visible =
      this.isActionVisible('screenshot') ||
      this.isActionVisible('remoteControl') ||
      this.isActionVisible('flash') ||
      this.isActionVisible('logcat') ||
      this.isActionVisible('quarantine') ||
      this.isActionVisible('decommission');
    console.log('hasSmActionMenuItems executed, result:', visible);
    return visible;
  });

  openConfiguration(): void {
    const deviceId = this.deviceId();
    const hostName = this.hostName();
    const hostIp = this.pageData().overview.host.ip;
    const universe = this.universe;
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
          result.universe,
        );
      });
  }

  createOrCopyConfiguration(
    action: string,
    deviceId: string,
    config: DeviceConfig | null,
    universe?: string,
  ) {
    // For google internal, the configuration UI has more features when create a new configuration,
    // thus we need a Wizard to guide the user to complete the configuration.
    // While for OSS, the configuration UI is simpler
    // thus we can directly use the HostSettings component.
    if (
      (this.environment.isGoogleInternal() && action === 'new') ||
      action === 'copy'
    ) {
      this.openDeviceWizard(action, deviceId, config, universe);
    }

    if (!this.environment.isGoogleInternal() && action === 'new') {
      this.openDeviceSettings(deviceId, config, universe);
    }
  }

  openDeviceWizard(
    action: string,
    deviceId: string,
    config: DeviceConfig | null,
    universe?: string,
  ) {
    this.dialog.open(DeviceWizard, {
      data: {source: action, deviceId, config, universe},
      autoFocus: false,
    });
  }

  openDeviceSettings(
    deviceId: string,
    config: DeviceConfig | null,
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
            this.hostName(),
            this.pageData().overview.host.ip,
            result.universe,
          );
        }
      });
  }

  showComingSoonPopup(key: string) {
    const featureMap: Record<string, ActionBarAction> = {
      'configuration': ActionBarAction.DEVICE_CONFIGURATION,
      'screenshot': ActionBarAction.DEVICE_SCREENSHOT,
      'remoteControl': ActionBarAction.DEVICE_REMOTE_CONTROL,
      'flash': ActionBarAction.DEVICE_FLASH,
      'logcat': ActionBarAction.DEVICE_LOGCAT,
      'quarantine': ActionBarAction.DEVICE_QUARANTINE,
      'decommission': ActionBarAction.DEVICE_DECOMMISSION,
    };
    const feature = featureMap[key];
    if (feature) {
      const deviceLegacyUrl = this.legacyFeUrl
        ? `${this.legacyFeUrl}/devicedetailview/${this.hostName()}/${this.pageData().overview.host.ip}/${this.deviceId()}`
        : undefined;
      this.comingSoonService.show(feature, 'default', deviceLegacyUrl);
    }
  }
}
