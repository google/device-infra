import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
  TemplateRef,
  viewChild,
} from '@angular/core';
import {MatChipsModule} from '@angular/material/chips';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {finalize} from 'rxjs/operators';

import {HostConfig} from '../../../../../core/models/host_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {Environment} from '../../../../../core/services/environment';
import {normalizeHostConfig} from '../../../../../core/utils/host_config_utils';

import {Permissions} from '../../../../../features/device_detail/components/device_config/steps/permissions/permissions';
import {Wifi} from '../../../../../features/device_detail/components/device_config/steps/wifi/wifi';
import {Dialog} from '../../../../../shared/components/config_common/dialog/dialog';
import {
  ReviewTable,
  type ReviewTableRow,
} from '../../../../../shared/components/config_common/review_table/review_table';
import {
  WizardStep,
  WizardStepper,
} from '../../../../../shared/components/config_common/wizard_stepper/wizard_stepper';
import {useConfigDialogActions} from '../../../../../shared/composables/config_dialog_actions';
import {ConfigMode} from '../steps/config_mode/config_mode';
import {HostPermissionList} from '../steps/host_permissions/host_permissions';

/**
 * Component for displaying the host configuration wizard dialog.
 *
 * It is used to configure the host's permissions, device config mode,
 * default device config, and review & submit.
 */
@Component({
  selector: 'app-host-wizard',
  standalone: true,
  templateUrl: './host_wizard.ng.html',
  styleUrl: './host_wizard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatChipsModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatDialogModule,
    WizardStepper,
    HostPermissionList,
    ConfigMode,
    Permissions,
    Wifi,
    Dialog,
    ReviewTable,
  ],
})
export class HostWizard implements OnInit {
  readonly data = inject(MAT_DIALOG_DATA); // to receive hostDetail openDialog
  // params source, config, etc.
  private readonly dialogRef = inject(MatDialogRef<HostWizard>); // to close the dialog

  private readonly dialogActions = useConfigDialogActions({
    dialogRef: this.dialogRef,
    onCancelSelfLockout: () => {
      this.currentStep.set('host-permissions');
    },
    onSubmitOverride: () => {
      this.submit(true, true);
    },
  });

  private readonly configService = inject(CONFIG_SERVICE);
  private readonly environment = inject(Environment);
  readonly isGoogleInternal = this.environment.isGoogleInternal();

  // used for stepper
  readonly permissionsTemplate = viewChild<TemplateRef<{}>>('permissions');
  readonly configModeTemplate = viewChild<TemplateRef<{}>>('configMode');
  readonly defaultTemplate = viewChild<TemplateRef<{}>>('default');
  readonly reviewTemplate = viewChild<TemplateRef<{}>>('review');

  readonly WIZARD_STEPS = computed<WizardStep[]>(() => {
    const permissions = this.permissionsTemplate();
    const configMode = this.configModeTemplate();
    const defaultTemp = this.defaultTemplate();
    const review = this.reviewTemplate();

    if (!permissions || !configMode || !defaultTemp || !review) {
      return [];
    }

    return [
      {
        id: 'host-permissions',
        label: 'Host Permissions',
        template: permissions,
      },
      {
        id: 'device-config-mode',
        label: 'Device Config Mode',
        template: configMode,
      },
      {
        id: 'default-device-config',
        label: 'Default Device Config',
        template: defaultTemp,
      },
      {
        id: 'review-and-submit',
        label: 'Review & Submit',
        template: review,
      },
    ];
  });

  currentStep = signal('host-permissions');

  readonly hostConfig = signal<HostConfig>(
    normalizeHostConfig(this.data.config || null),
  );

  // used for review & submit step
  dataSource: ReviewTableRow[] = [];

  // used for apply changes button
  readonly stepper = viewChild<WizardStepper>('stepper');
  verifying = signal<boolean>(false);
  applyChangesDisabled = signal<boolean>(true);

  ngOnInit() {
    if (this.data.source === 'copy') {
      // this.currentStep.set('review-and-submit');
      this.covertToReviewTable();
    }
  }

  onCurrentStepChange(currentStep: string) {
    this.currentStep.set(currentStep);
    if (currentStep === 'default-device-config') {
      this.hostConfig.set({
        ...this.hostConfig(),
        deviceConfig: {
          ...this.hostConfig().deviceConfig!,
          permissions: {
            owners: this.hostConfig().permissions.hostAdmins,
            executors:
              this.hostConfig().deviceConfig?.permissions?.executors || [],
          },
        },
      });
      return;
    }

    if (currentStep === 'review-and-submit') {
      this.covertToReviewTable();
    }
  }

  covertToReviewTable() {
    const owners = this.hostConfig().deviceConfig?.permissions?.owners || [];
    const executors =
      this.hostConfig().deviceConfig?.permissions?.executors || [];

    const dataSource: ReviewTableRow[] = [];

    if (this.isGoogleInternal) {
      dataSource.push(
        {type: 'title', feature: 'Host Permissions'},
        {
          type: 'data',
          feature: 'Host Admins',
          value:
            (this.hostConfig().permissions?.hostAdmins || []).join(', ') ||
            'None',
        },
      );
    }

    dataSource.push(
      {type: 'title', feature: 'Device Config Mode'},
      {
        type: 'data',
        feature: 'Device Config Mode',
        value: this.hostConfig().deviceConfigMode,
      },
    );

    if (this.isGoogleInternal) {
      dataSource.push(
        {type: 'title', feature: 'Default Device Config - Permissions'},
        {
          type: 'data',
          feature: 'Device Owners',
          value: owners.length > 0 ? owners.join(', ') : 'None',
        },
        {
          type: 'data',
          feature: 'Device Executors',
          value: executors.length > 0 ? executors.join(', ') : 'None',
        },
      );
    }

    const wifiType = this.hostConfig().deviceConfig?.wifi?.type;
    dataSource.push(
      {type: 'title', feature: 'Default Device Config - Wi-Fi'},
      {
        type: 'data',
        feature: 'Type',
        value: !wifiType || wifiType === 'none' ? 'None' : wifiType,
      },
      {
        type: 'data',
        feature: 'SSID',
        value: this.hostConfig().deviceConfig?.wifi?.ssid || 'None',
      },
      {
        type: 'data',
        feature: 'Hidden Network',
        value: this.hostConfig().deviceConfig?.wifi?.scanSsid ? 'Yes' : 'No',
      },
    );

    if (this.data.source === 'copy') {
      const supportDimensions = (
        this.hostConfig().deviceConfig?.dimensions?.supported || []
      ).filter((item) => !(!item.name && !item.value));
      const requiredDimensions = (
        this.hostConfig().deviceConfig?.dimensions?.required || []
      ).filter((item) => !(!item.name && !item.value));

      const hostProperties = (this.hostConfig().hostProperties || []).filter(
        (v) => !(!v.key && !v.value),
      );

      dataSource.push(
        {type: 'title', feature: 'Default Device Config - Dimensions'},
        {
          type: 'data',
          feature: 'Supported Dimensions',
          value: supportDimensions,
          valueType: 'dimensions',
        },
        {
          type: 'data',
          feature: 'Required Dimensions',
          value: requiredDimensions,
          valueType: 'dimensions',
        },
      );

      if (this.isGoogleInternal) {
        dataSource.push(
          {
            type: 'title',
            feature: 'Default Device Config - Stability & Reboot',
          },
          {
            type: 'data',
            feature: 'Max Consecutive Failures',
            value: this.hostConfig().deviceConfig?.settings?.maxConsecutiveFail,
          },
          {
            type: 'data',
            feature: 'Max Tests between Reboots',
            value: this.hostConfig().deviceConfig?.settings?.maxConsecutiveTest,
          },
          {
            type: 'title',
            feature: 'Device Discovery',
          },
          {
            type: 'data',
            feature: 'Status',
            value:
              'Ignored (host-specific configurations are not transferred during copy)',
          },
          {
            type: 'title',
            feature: 'Host Properties',
          },
          {
            type: 'data',
            feature: 'Host Properties',
            value: hostProperties,
            valueType: 'host-properties',
          },
        );
      }
    }

    this.dataSource = dataSource;
  }

  submit(overrideSelfLockout = false, bypassOwnerCheck = false) {
    const hostAdmins = this.hostConfig().permissions?.hostAdmins || [];
    if (!bypassOwnerCheck && hostAdmins.length === 0) {
      this.dialogActions.emptyOwnerWarning('host', () => {
        this.submit(
          /* overrideSelfLockout= */ true,
          /* bypassOwnerCheck= */ true,
        );
      });
      return;
    }

    const wifi = this.hostConfig().deviceConfig?.wifi;
    const finalWifi = !wifi || wifi.type === 'none' ? undefined : wifi;
    const requestConfig: HostConfig = {
      ...this.hostConfig(),
      deviceConfig: {
        permissions: {
          owners: hostAdmins,
          executors:
            this.hostConfig().deviceConfig?.permissions?.executors || [],
        },
        wifi: finalWifi,
        dimensions: this.hostConfig().deviceConfig?.dimensions!,
        settings: this.hostConfig().deviceConfig?.settings!,
      },
    };

    if (this.data.source === 'copy') {
      this.verifying.set(true);
    } else {
      this.stepper()?.verifying.set(true);
    }

    // Define FieldMask paths corresponding to sections that require updates.
    // ATS environment only supports mode and basic config updates, while standard
    // environments also update permissions, properties, and optionally device discovery.
    let paths: string[];
    if (!this.isGoogleInternal) {
      paths = ['device_config_mode', 'device_config'];
    } else {
      paths =
        this.data.source === 'copy'
          ? [
              'permissions',
              'device_config_mode',
              'device_config',
              'host_properties',
            ]
          : [
              'permissions',
              'device_config_mode',
              'device_config',
              'host_properties',
              'device_discovery',
            ];
    }

    this.configService
      .updateHostConfig({
        hostName: this.data.hostName,
        config: requestConfig,
        scope: {
          updateMask: {paths},
        },
        options: {overrideSelfLockout},
      })
      .pipe(
        finalize(() => {
          if (this.data.source === 'copy') {
            this.verifying.set(false);
          } else {
            this.stepper()?.verifying.set(false);
          }
        }),
      )
      .subscribe((result) => {
        if (!result.success) {
          this.dialogActions.error(result.error?.code);
          return;
        }
        this.dialogActions.success();
      });
  }
}
