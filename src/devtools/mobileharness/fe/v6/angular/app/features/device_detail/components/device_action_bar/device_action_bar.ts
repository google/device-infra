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

import {DEVICE_ACTION_UI_CONFIG} from '@deviceinfra/app/core/constants/action_bar_config';
import {
  APP_DATA,
  getLegacyFeUrl,
} from '@deviceinfra/app/core/models/app_data';
import {ActionButtonState} from '../../../../core/models/action_common';
import {DeviceActions} from '../../../../core/models/device_action';
import type {DeviceOverviewPageData} from '../../../../core/models/device_overview';

import {ActionButton} from '../../../../shared/components/action_button/action_button';
import {useDeviceActions} from '../../../../shared/composables/device_actions';
import {ComingSoonService} from '../../../../shared/services/coming_soon_service';

/**
 * Component for the action bar in the device detail page header.
 */
@Component({
  selector: 'app-device-action-bar',
  standalone: true,
  imports: [
    CommonModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
    ActionButton,
  ],
  templateUrl: './device_action_bar.ng.html',
  styleUrl: './device_action_bar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeviceActionBar {
  protected readonly deviceActions = useDeviceActions();

  /**
   * Configuration driving the UI for device actions.
   *
   * NOTE: This follows the "next-level" refactoring pattern of separating data/configuration
   * from logic and UI. By using DEVICE_ACTION_UI_CONFIG, we avoid hardcoding button labels,
   * icons, and test IDs in the template. This makes the component highly maintainable and
   * reusable across different views (like DeviceActionBar and HostOverview).
   */
  protected readonly actionUiConfig = DEVICE_ACTION_UI_CONFIG;

  /**
   * Layout configurations for different screen sizes.
   * These arrays define WHICH actions appear and WHERE (direct button vs menu),
   * while actionUiConfig defines HOW they look. This separation was key to
   * optimizing the template and reducing code duplication.
   */

  protected readonly layout2xl: Array<keyof DeviceActions> = [
    'configuration',
    'screenshot',
    'remoteControl',
    'flash',
    'logcat',
    'quarantine',
    'decommission',
  ];

  protected readonly layoutXlDirect: Array<keyof DeviceActions> = [
    'configuration',
    'screenshot',
    'remoteControl',
  ];

  protected readonly layoutXlMenu: Array<keyof DeviceActions> = [
    'flash',
    'logcat',
    'quarantine',
    'decommission',
  ];

  protected readonly layoutSmDirect: Array<keyof DeviceActions> = [
    'configuration',
  ];

  protected readonly layoutSmMenu: Array<keyof DeviceActions> = [
    'screenshot',
    'remoteControl',
    'flash',
    'logcat',
    'quarantine',
    'decommission',
  ];

  onAction(actionId: keyof DeviceActions) {
    switch (actionId) {
      case 'configuration':
        this.onConfiguration();
        break;
      case 'screenshot':
        this.onScreenshot();
        break;
      case 'remoteControl':
        this.onRemoteControl();
        break;
      case 'flash':
        this.onFlash();
        break;
      case 'logcat':
        this.onLogcat();
        break;
      case 'quarantine':
        this.onQuarantine();
        break;
      case 'decommission':
        this.onDecommission();
        break;
      default:
        break;
    }
  }
  private readonly dialog = inject(MatDialog);
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

  readonly onConfiguration = () => {
    this.deviceActions.configureDevice(
      this.deviceId(),
      this.hostName(),
      this.pageData().overview.host.ip,
      this.universe,
    );
  };

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
    const visible = this.layoutXlMenu.some((actionId) =>
      this.isActionVisible(actionId),
    );
    console.log('hasXlMoreMenuItems executed, result:', visible);
    return visible;
  });

  readonly hasSmActionMenuItems = computed(() => {
    const visible = this.layoutSmMenu.some((actionId) =>
      this.isActionVisible(actionId),
    );
    console.log('hasSmActionMenuItems executed, result:', visible);
    return visible;
  });

  /**
   * Shows the "Coming Soon" popup for the given action.
   *
   * NOTE: This method was optimized during "next-level" refactoring to use the
   * centralized DEVICE_ACTION_UI_CONFIG. The hardcoded 'featureMap' was removed,
   * separating data (Action mappings) from logic. This ensures that new actions
   * added to the config automatically inherit "Coming Soon" support without
   * needing to modify this function.
   */
  showComingSoonPopup(key: keyof DeviceActions) {
    const feature = this.actionUiConfig[key]?.feature;
    if (feature) {
      this.comingSoonService.showForDevice(
        feature,
        this.legacyFeUrl,
        this.hostName(),
        this.pageData().overview.host.ip,
        this.deviceId(),
      );
    }
  }
}
