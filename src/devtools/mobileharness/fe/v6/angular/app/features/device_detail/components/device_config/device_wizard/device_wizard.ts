import {CommonModule} from '@angular/common';
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
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatStepperModule} from '@angular/material/stepper';
import {MatTableModule} from '@angular/material/table';
import {finalize} from 'rxjs/operators';

import {DEFAULT_DEVICE_CONFIG} from '../../../../../core/constants/device_config_constants';
import {
  ConfigSection,
  type DeviceConfig,
  UpdateDeviceConfigRequest,
} from '../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {Environment} from '../../../../../core/services/environment';
import {normalizeDeviceConfig} from '../../../../../core/utils/device_config_utils';
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
import {Dimensions} from '../steps/dimensions/dimensions';
import {Permissions} from '../steps/permissions/permissions';
import {Wifi} from '../steps/wifi/wifi';

/**
 * Component for displaying the device configuration wizard dialog.
 *
 * It is used to configure the device's dimensions, owners, and other
 * properties.
 */
@Component({
  selector: 'app-device-wizard',
  standalone: true,
  templateUrl: './device_wizard.ng.html',
  styleUrl: './device_wizard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatIconModule,
    MatStepperModule,
    MatTableModule,
    MatProgressSpinnerModule,
    WizardStepper,
    Dialog,
    Permissions,
    Wifi,
    Dimensions,
    ReviewTable,
  ],
})
export class DeviceWizard implements OnInit {
  readonly data = inject(MAT_DIALOG_DATA);
  readonly configService = inject(CONFIG_SERVICE);
  private readonly dialogRef = inject(MatDialogRef<DeviceWizard>);
  private readonly environment = inject(Environment);

  private readonly dialogActions = useConfigDialogActions({
    dialogRef: this.dialogRef,
    onCancelSelfLockout: () => {
      // If user cancels self-lockout, take them back to the permissions step.
      this.currentStep.set('permissions');
    },
    onSubmitOverride: () => {
      // If user proceeds anyway, force-save with selfLockout override enabled.
      this.submit(true);
    },
  });
  readonly isGoogleInternal = this.environment.isGoogleInternal();

  // used for wizard stepper
  readonly permissionsTemplate = viewChild<TemplateRef<{}>>('permissions');
  readonly wifiTemplate = viewChild<TemplateRef<{}>>('wifi');
  readonly dimensionsTemplate = viewChild<TemplateRef<{}>>('dimensions');
  readonly reviewTemplate = viewChild<TemplateRef<{}>>('review');

  readonly WIZARD_STEPS_NEW = computed<WizardStep[]>(() => {
    const permissions = this.permissionsTemplate();
    const wifi = this.wifiTemplate();
    const dimensions = this.dimensionsTemplate();
    const review = this.reviewTemplate();

    if (!permissions || !wifi || !dimensions || !review) {
      return [];
    }

    return [
      {
        id: 'permissions',
        label: 'Permissions',
        template: permissions,
      },
      {id: 'wifi', label: 'Wi-Fi', template: wifi},
      {
        id: 'dimensions',
        label: 'Dimensions',
        template: dimensions,
      },
      {
        id: 'review-and-submit',
        label: 'Review & Submit',
        template: review,
      },
    ];
  });

  currentStep = signal<string>('permissions');

  config: DeviceConfig = DEFAULT_DEVICE_CONFIG;

  // used for dimensions step duplicate check
  hasError = false;
  errorStep = 'dimensions';

  // used for review & submit step
  dataSource: ReviewTableRow[] = [];

  // used for apply changes button
  readonly stepper = viewChild<WizardStepper>('stepper');
  verifying = signal<boolean>(false);
  applyChangesDisabled = signal<boolean>(true);

  ngOnInit() {
    if (this.data.source === 'copy') {
      this.config = normalizeDeviceConfig(this.data.config);
      this.covertToReviewTable();
    }
  }

  onCurrentStepChange(currentStep: string) {
    this.currentStep.set(currentStep);
    if (currentStep === 'review-and-submit') {
      this.covertToReviewTable();
    }
  }

  covertToReviewTable() {
    const supportDimensions = (this.config.dimensions?.supported || []).filter(
      (item) => !(!item.name && !item.value),
    );
    const requiredDimensions = (this.config.dimensions?.required || []).filter(
      (item) => !(!item.name && !item.value),
    );
    const owners = this.config.permissions?.owners || [];
    const executors = this.config.permissions?.executors || [];

    this.dataSource = [];

    if (this.isGoogleInternal) {
      this.dataSource.push(
        {type: 'title', feature: 'Permissions'},
        {
          type: 'data',
          feature: 'Owners',
          value: owners.length > 0 ? owners.join(', ') : 'None',
        },
        {
          type: 'data',
          feature: 'Executors',
          value: executors.length > 0 ? executors.join(', ') : 'None',
        },
      );
    }

    this.dataSource.push(
      {type: 'title', feature: 'Wi-Fi'},
      {
        type: 'data',
        feature: 'Type',
        value:
          !this.config.wifi?.type || this.config.wifi.type === 'none'
            ? 'None'
            : this.config.wifi.type,
      },
      {
        type: 'data',
        feature: 'SSID',
        value: this.config.wifi?.ssid || 'None',
      },
      {
        type: 'data',
        feature: 'Hidden Network',
        value: this.config.wifi?.scanSsid ? 'Yes' : 'No',
      },
      {type: 'title', feature: 'Dimensions'},
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

    if (this.isGoogleInternal && this.data.source === 'copy') {
      this.dataSource.push(
        {
          type: 'title',
          feature: 'Stability & Reboot',
        },
        {
          type: 'data',
          feature: 'Max Consecutive Failures',
          value: this.config.settings?.maxConsecutiveFail,
        },
        {
          type: 'data',
          feature: 'Max Tests between Reboots',
          value: this.config.settings?.maxConsecutiveTest,
        },
      );
    }
  }

  // used for apply changes button
  submit(overrideSelfLockout = false) {
    if (this.data.source === 'copy') {
      this.verifying.set(true);
    } else {
      this.stepper()?.verifying.set(true);
    }

    const deviceConfig = {...this.config};
    if (deviceConfig.wifi && deviceConfig.wifi.type === 'none') {
      deviceConfig.wifi = undefined;
    }

    const request: UpdateDeviceConfigRequest = {
      id: this.data.deviceId,
      config: deviceConfig,
      section: ConfigSection.ALL,
      options: {overrideSelfLockout},
      universe: this.data.universe,
    };

    this.configService
      .updateDeviceConfig(request)
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
