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

import {ActionBarAction} from '@deviceinfra/app/core/constants/action_bar_config';
import {
  APP_DATA,
  getLegacyFeUrl,
} from '@deviceinfra/app/core/models/app_data';
import {ActionButtonState} from '../../../../core/models/action_common';
import {HostActions} from '../../../../core/models/host_action';
import type {HostOverviewPageData} from '../../../../core/models/host_overview';
import {Environment} from '../../../../core/services/environment';
import {ComingSoonService} from '../../../../shared/services/coming_soon_service';
import {HostConfig} from '../host_config/host_config';
import {HostEmpty} from '../host_config/host_empty/host_empty';
import {HostSettings} from '../host_config/host_settings/host_settings';
import {HostWizard} from '../host_config/host_wizard/host_wizard';
import {HostDebugDialog} from '../host_debug_dialog/host_debug_dialog';
import {HostDecommissionDialog} from '../host_decommission_dialog/host_decommission_dialog';

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
  private readonly dialog = inject(MatDialog);
  private readonly environment = inject(Environment);
  private readonly comingSoonService = inject(ComingSoonService);
  private readonly appData = inject(APP_DATA);

  readonly legacyFeUrl = getLegacyFeUrl(this.appData.applicationId ?? '');

  readonly pageData = input.required<HostOverviewPageData>();

  readonly actions = computed(() => this.pageData().headerInfo?.actions);

  readonly hostName = computed(
    () => this.pageData().overviewContent?.hostName || '',
  );

  readonly onConfiguration = () => {
    const dialogRef = this.dialog.open(HostConfig, {
      data: {hostName: this.hostName()},
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

      this.createOrCopyConfiguration(
        result.action,
        this.hostName(),
        result.config,
      );
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
    config: HostConfig | null | undefined,
  ) {
    if (
      (this.environment.isGoogleInternal() && action === 'new') ||
      action === 'copy'
    ) {
      this.openHostWizard(action, hostName, config);
    }

    if (!this.environment.isGoogleInternal() && action === 'new') {
      this.openHostSettings(hostName, config);
    }
  }

  private openHostWizard(
    action: string,
    hostName: string,
    config: HostConfig | null | undefined,
  ) {
    this.dialog.open(HostWizard, {
      data: {hostName, source: action, config},
      autoFocus: false,
    });
  }

  private openHostSettings(
    hostName: string,
    config: HostConfig | null | undefined,
  ) {
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

  readonly onDebug = () => {
    this.dialog.open(HostDebugDialog, {
      data: {hostName: this.hostName()},
      autoFocus: false,
      width: '90vw',
      height: '90vh',
      maxHeight: '90vh',
      maxWidth: '1280px',
    });
  };

  readonly onDecommission = () => {
    this.dialog.open(HostDecommissionDialog, {
      data: {hostName: this.hostName()},
      autoFocus: false,
      disableClose: true,
    });
  };

  showComingSoonPopup(key: string) {
    const featureMap: Record<string, ActionBarAction> = {
      'configuration': ActionBarAction.HOST_CONFIGURATION,
      'debug': ActionBarAction.HOST_DEBUG,
      'decommission': ActionBarAction.HOST_DECOMMISSION,
    };
    const feature = featureMap[key];
    if (feature) {
      const hostLegacyUrl = this.legacyFeUrl
        ? `${this.legacyFeUrl}/labdetailview/${this.hostName()}/${this.pageData().overviewContent.ip}`
        : undefined;
      this.comingSoonService.show(feature, 'default', hostLegacyUrl);
    }
  }

  getAction(key: keyof HostActions): ActionButtonState | undefined {
    return (this.actions() as unknown as Record<string, ActionButtonState>)?.[
      key
    ];
  }

  isActionVisible(key: keyof HostActions): boolean {
    return this.getAction(key)?.visible ?? false;
  }

  readonly hasXlMoreMenuItems = computed(() => {
    return (
      this.isActionVisible('debug') || this.isActionVisible('decommission')
    );
  });

  readonly hasSmActionMenuItems = computed(() => {
    return (
      this.isActionVisible('debug') || this.isActionVisible('decommission')
    );
  });
}
