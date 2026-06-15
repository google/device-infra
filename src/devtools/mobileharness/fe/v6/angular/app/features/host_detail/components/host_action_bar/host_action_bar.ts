import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  OnInit,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatDialog} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatTooltipModule} from '@angular/material/tooltip';
import {ActivatedRoute, Router} from '@angular/router';

import {HOST_ACTION_UI_CONFIG} from '@deviceinfra/app/core/constants/action_bar_config';
import {
  APP_DATA,
  getLegacyFeUrl,
} from '@deviceinfra/app/core/models/app_data';
import {ActionButtonState} from '../../../../core/models/action_common';
import {HostActions} from '../../../../core/models/host_action';
import type {HostOverviewPageData} from '../../../../core/models/host_overview';
import {Environment} from '../../../../core/services/environment';
import {ActionButton} from '../../../../shared/components/action_button/action_button';
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
  imports: [
    CommonModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
    ActionButton,
  ],
  templateUrl: './host_action_bar.ng.html',
  styleUrl: './host_action_bar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HostActionBar implements OnInit {
  private readonly dialog = inject(MatDialog);
  private readonly environment = inject(Environment);
  private readonly comingSoonService = inject(ComingSoonService);
  private readonly appData = inject(APP_DATA);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly legacyFeUrl = getLegacyFeUrl(this.appData.applicationId ?? '');

  /**
   * Configuration driving the UI for host actions.
   *
   * NOTE: This follows the "next-level" refactoring pattern of separating data/configuration
   * from logic and UI. By using HOST_ACTION_UI_CONFIG, we avoid hardcoding button labels,
   * icons, and test IDs in the template. This makes the component highly maintainable and
   * reusable.
   */
  protected readonly actionUiConfig = HOST_ACTION_UI_CONFIG;

  /**
   * Layout configurations for different screen sizes.
   * These arrays define WHICH actions appear and WHERE (direct button vs menu),
   * while actionUiConfig defines HOW they look. This separation was key to
   * optimizing the template and reducing code duplication.
   */

  protected readonly layout2xl: Array<keyof HostActions> = [
    'configuration',
    'decommission',
    'debug',
  ];

  protected readonly layoutXlDirect: Array<keyof HostActions> = [
    'configuration',
  ];

  protected readonly layoutXlMenu: Array<keyof HostActions> = [
    'debug',
    'decommission',
  ];

  protected readonly layoutSmDirect: Array<keyof HostActions> = [
    'configuration',
  ];

  protected readonly layoutSmMenu: Array<keyof HostActions> = [
    'debug',
    'decommission',
  ];

  onAction(actionId: keyof HostActions) {
    switch (actionId) {
      case 'configuration':
        this.onConfiguration();
        break;
      case 'debug':
        this.onDebug();
        break;
      case 'decommission':
        this.onDecommission();
        break;
      default:
        break;
    }
  }

  ngOnInit() {
    this.route.queryParams
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        if (params['openHostConfig'] === 'true') {
          this.onConfiguration();
          this.router.navigate([], {
            relativeTo: this.route,
            queryParams: {'openHostConfig': null},
            queryParamsHandling: 'merge',
            replaceUrl: true,
          });
        }
      });
  }

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

  /**
   * Shows the "Coming Soon" popup for the given action.
   *
   * NOTE: This method was optimized during "next-level" refactoring to use the
   * centralized HOST_ACTION_UI_CONFIG. It automatically retrieves the correct
   * feature flag associated with the action, driving the "Coming Soon" dialog
   * without needing component-level switch cases or mappings.
   */
  showComingSoonPopup(key: keyof HostActions) {
    const feature = this.actionUiConfig[key]?.feature;
    if (feature) {
      this.comingSoonService.showForHost(
        feature,
        this.legacyFeUrl,
        this.hostName(),
        this.pageData().overviewContent.ip,
      );
    }
  }

  getAction(key: keyof HostActions): ActionButtonState | undefined {
    return (
      this.actions() as unknown as Record<string, ActionButtonState | undefined>
    )?.[key];
  }

  isActionVisible(key: keyof HostActions): boolean {
    return this.getAction(key)?.visible ?? false;
  }

  readonly hasXlMoreMenuItems = computed(() => {
    return this.layoutXlMenu.some((actionId) => this.isActionVisible(actionId));
  });

  readonly hasSmActionMenuItems = computed(() => {
    return this.layoutSmMenu.some((actionId) => this.isActionVisible(actionId));
  });
}
