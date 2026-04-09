import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  Input,
} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatTooltipModule} from '@angular/material/tooltip';

import {ActionBarAction} from 'app/core/constants/action_bar_config';
import {
  APP_DATA,
  getLegacyFeUrl,
} from 'app/core/models/app_data';
import {ActionButtonState} from '../../../../core/models/action_common';
import {HostActions} from '../../../../core/models/host_action';
import type {HostOverviewPageData} from '../../../../core/models/host_overview';
import {Environment} from '../../../../core/services/environment';
import {ComingSoonService} from '../../../../shared/services/coming_soon_service';
import {SnackBarService} from '../../../../shared/services/snackbar_service';
import {HostConfig} from '../host_config/host_config';
import {HostEmpty} from '../host_config/host_empty/host_empty';
import {HostSettings} from '../host_config/host_settings/host_settings';
import {HostWizard} from '../host_config/host_wizard/host_wizard';

/**
 * Component for the action bar in the host detail page header.
 */
@Component({
  selector: 'app-host-action-bar',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatMenuModule, MatTooltipModule],
  templateUrl: './host_action_bar.ng.html',
  styleUrl: './host_action_bar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HostActionBar {
  private readonly snackBar = inject(SnackBarService);
  private readonly dialog = inject(MatDialog);
  private readonly environment = inject(Environment);
  private readonly comingSoonService = inject(ComingSoonService);
  private readonly appData = inject(APP_DATA);

  readonly legacyFeUrl = getLegacyFeUrl(this.appData.applicationId ?? '');

  @Input({required: true}) pageData!: HostOverviewPageData;

  get actions(): HostActions | undefined {
    return this.pageData?.headerInfo?.actions;
  }

  get hostName(): string {
    return this.pageData?.overviewContent?.hostName || '';
  }

  readonly onConfiguration = () => {
    const dialogRef = this.dialog.open(HostConfig, {
      data: {hostName: this.hostName},
      autoFocus: false,
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (!result) {
        return;
      }

      if (result.action === 'reset') {
        this.resetConfiguration(result.hostName);
        return;
      }

      if (result.action === 'new' || result.action === 'copy') {
        this.createOrCopyConfiguration(
          result.action,
          this.hostName,
          result.config,
        );
      }
    });
  };

  private resetConfiguration(hostName: string) {
    this.dialog
      .open(HostEmpty, {
        data: {
          hostName,
          title:
            'You are about to clear the existing configuration for this host. Your current settings will be discarded. Please choose how you want to proceed.',
        },
        autoFocus: false,
      })
      .afterClosed()
      .subscribe((result) => {
        if (!result) {
          return;
        }

        this.createOrCopyConfiguration(
          result.action,
          result.hostName,
          result.config,
        );
      });
  }

  private createOrCopyConfiguration(
    action: string,
    hostName: string,
    config: HostConfig,
  ) {
    if (this.environment.isGoogleInternal()) {
      this.dialog.open(HostWizard, {
        data: {hostName, source: action, config},
        autoFocus: false,
      });
    } else {
      const dialogRef = this.dialog.open(HostSettings, {
        data: {hostName, config},
        autoFocus: false,
      });

      dialogRef.afterClosed().subscribe((result) => {
        if (!result) {
          return;
        }
        if (result.action === 'reset') {
          this.resetConfiguration(result.hostName);
        }
      });
    }
  }

  readonly onDebug = () => {
    this.snackBar.showInfo(`Debug action triggered for ${this.hostName}`);
  };

  readonly onDeploy = () => {
    this.snackBar.showInfo(`Deploy action triggered for ${this.hostName}`);
  };

  readonly onStart = () => {
    this.snackBar.showInfo(`Start action triggered for ${this.hostName}`);
  };

  readonly onRestart = () => {
    this.snackBar.showInfo(`Restart action triggered for ${this.hostName}`);
  };

  readonly onStop = () => {
    this.snackBar.showInfo(`Stop action triggered for ${this.hostName}`);
  };

  readonly onDecommission = () => {
    this.snackBar.showInfo(
      `Decommission action triggered for ${this.hostName}`,
    );
  };

  readonly onUpdatePassThroughFlags = () => {
    this.snackBar.showInfo(
      `Update Flags action triggered for ${this.hostName}`,
    );
  };

  readonly onRelease = () => {
    this.snackBar.showInfo(`Release action triggered for ${this.hostName}`);
  };

  showComingSoonPopup(key: string) {
    const featureMap: Record<string, ActionBarAction> = {
      'configuration': ActionBarAction.HOST_CONFIGURATION,
      'debug': ActionBarAction.HOST_DEBUG,
      'deploy': ActionBarAction.HOST_DEPLOY,
      'start': ActionBarAction.HOST_START,
      'restart': ActionBarAction.HOST_RESTART,
      'stop': ActionBarAction.HOST_STOP,
      'decommission': ActionBarAction.HOST_DECOMMISSION,
      'updatePassThroughFlags': ActionBarAction.HOST_UPDATE_PASS_THROUGH_FLAGS,
      'release': ActionBarAction.HOST_RELEASE,
    };
    const feature = featureMap[key];
    if (feature) {
      const hostLegacyUrl = this.legacyFeUrl
        ? `${this.legacyFeUrl}/labdetailview/${this.hostName}/${this.pageData.overviewContent.ip}`
        : undefined;
      this.comingSoonService.show(feature, 'default', hostLegacyUrl);
    }
  }

  getAction(key: keyof HostActions): ActionButtonState | undefined {
    return (this.actions as unknown as Record<string, ActionButtonState>)?.[
      key
    ];
  }

  isActionVisible(key: keyof HostActions): boolean {
    return this.getAction(key)?.visible ?? false;
  }

  readonly hasXlMoreMenuItems = computed(() => {
    return (
      this.isActionVisible('start') ||
      this.isActionVisible('stop') ||
      this.isActionVisible('debug') ||
      this.isActionVisible('decommission')
    );
  });

  readonly hasSmActionMenuItems = computed(() => {
    return (
      this.isActionVisible('release') ||
      this.isActionVisible('start') ||
      this.isActionVisible('restart') ||
      this.isActionVisible('stop') ||
      this.isActionVisible('debug') ||
      this.isActionVisible('decommission')
    );
  });
}
