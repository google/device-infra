import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
  OnInit,
  signal,
  TemplateRef,
  ViewChild,
} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatStepper, MatStepperModule} from '@angular/material/stepper';
import {MatTableModule} from '@angular/material/table';
import type {
  DeviceConfig,
  DeviceDimension,
} from '../../../../../core/models/device_config_models';
import {
  CheckDeviceWritePermissionResult,
  ConfigSection,
  UpdateDeviceConfigRequest,
} from '../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {ConfirmDialog} from '../../../../../shared/components/common_config/confirm_dialog/confirm_dialog';
import {Dialog} from '../../../../../shared/components/common_config/dialog/dialog';
import {Dimensions} from '../steps/dimensions/dimensions';
import {Permissions} from '../steps/permissions/permissions';
import {Wifi} from '../steps/wifi/wifi';

interface ReviewTableRow {
  type: 'title' | 'data';
  feature: string;
  value?: string | number;
}

/**
 * Component for displaying the device configuration wizard dialog.
 *
 * It is used to configure the device's dimensions, owners, and other properties.
 */
@Component({
  selector: 'app-wizard',
  standalone: true,
  templateUrl: './wizard.ng.html',
  styleUrl: './wizard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatIconModule,
    MatStepperModule,
    Dialog,
    Permissions,
    Wifi,
    Dimensions,
    MatTableModule,
    MatProgressSpinnerModule,
    ConfirmDialog,
  ],
})
export class Wizard implements OnInit {
  private readonly cdr = inject(ChangeDetectorRef);

  private readonly dialog = inject(MatDialog);
  private readonly dialogRef = inject(MatDialogRef<Wizard>);

  readonly data = inject(MAT_DIALOG_DATA);
  private readonly configService = inject(CONFIG_SERVICE);

  @ViewChild('stepper') stepper!: MatStepper;

  WIZARD_STEPS_NEW = ['Permissions', 'Wi-Fi', 'Dimensions', 'Review & Submit'];

  currentStep = 0;

  subtitle =
    this.data.source === 'new'
      ? `Step ${this.currentStep + 1} of ${
          this.WIZARD_STEPS_NEW.length
        }: <strong class="font-medium text-gray-700">${
          this.WIZARD_STEPS_NEW[this.currentStep]
        }</strong>`
      : `For device: ${this.data.deviceId}`;

  config: DeviceConfig = {
    permissions: {
      owners: [],
      executors: [],
    },
    wifi: {
      type: 'none',
      ssid: 'GoogleGuest',
      psk: '',
      scanSsid: false,
    },
    dimensions: {supported: [], required: []},
    settings: {
      maxConsecutiveFail: 0,
      maxConsecutiveTest: 0,
    },
  };

  hasError = false;

  displayedColumns = ['feature', 'value'];
  dataSource: ReviewTableRow[] = [];

  verifying = signal<boolean>(false);

  permissionResult: CheckDeviceWritePermissionResult = {
    hasPermission: true,
    userName: 'derekchen',
  };

  ngOnInit() {
    if (this.data.source === 'copy') {
      this.currentStep = this.WIZARD_STEPS_NEW.length - 1;

      this.config = this.data.config;
      this.covertToReviewTable();
    }
  }

  onPermissionChange(permissionResult: CheckDeviceWritePermissionResult) {
    this.permissionResult = permissionResult;
  }

  nextStep() {
    if (this.currentStep < this.WIZARD_STEPS_NEW.length) {
      this.currentStep++;
      this.stepper.next();
    }

    if (this.data.source === 'new') {
      this.subtitle = `Step ${this.currentStep + 1} of ${
        this.WIZARD_STEPS_NEW.length
      }: <strong class="font-medium text-gray-700">${
        this.WIZARD_STEPS_NEW[this.currentStep]
      }</strong>`;
    }

    if (this.currentStep === this.WIZARD_STEPS_NEW.length - 1) {
      this.covertToReviewTable();
    }
  }

  previousStep() {
    if (this.currentStep > 0) {
      this.currentStep--;
      this.stepper.previous();
    }
  }

  applyChanges() {
    if (this.data.source === 'copy') {
      this.submit();
      return;
    }

    // Check self-lockout
    // TODO: Remove this check once the backend implements the
    // self-lockout check.

    const currentUser = this.permissionResult.userName || '';
    const selfLockout = !this.config.permissions.owners.includes(currentUser);

    if (!selfLockout) {
      this.submit(true);
      return;
    }

    this.verifying.set(true);
    setTimeout(() => {
      this.verifying.set(false);

      const dialogData = {
        title: 'Permission Warning',
        content:
          'You are about to change the device configuration. This action will remove all the existing owners of the device. Are you sure you want to proceed?',
        type: 'warning',
        primaryButtonLabel: 'Proceed Anyway',
        secondaryButtonLabel: 'Go Back',
      };

      const selfLockoutDialog = this.dialog.open(ConfirmDialog, {
        data: dialogData,
        disableClose: true,
      });
      selfLockoutDialog.afterClosed().subscribe((result) => {
        if (result === 'secondary') {
          this.currentStep = 0;
          this.cdr.markForCheck();

          return;
        }

        if (result === 'primary') {
          this.submit(selfLockout);
        }
      });
    }, 2000);
  }

  submit(overrideSelfLockout = false) {
    const request: UpdateDeviceConfigRequest = {
      deviceId: this.data.deviceId,
      config: this.config,
      section: ConfigSection.ALL,
      options: {overrideSelfLockout},
    };

    this.configService.updateDeviceConfig(request).subscribe((result) => {
      if (!result.success) {
        this.error(result.error?.code);
        return;
      }

      this.success();
      this.dialogRef.close(true);
      console.log(result);
    });
  }

  success() {
    const dialogData = {
      title: 'Configuration Saved',
      content: 'Your configuration has been saved successfully. ',
      type: 'success',
      primaryButtonLabel: 'OK',
    };

    this.dialog.open(ConfirmDialog, {
      data: dialogData,
      disableClose: true,
    });
  }

  error(errorCode?: string) {
    const errorMessage = errorCode
      ? ` with error code ${errorCode}. Please try again.`
      : '. Please try again.';

    const dialogData = {
      title: 'Configuration Failed',
      content: `Your configuration has failed to save` + errorMessage,
      type: 'error',
      primaryButtonLabel: 'OK',
    };
  }

  isTitleRow(index: number, row: any): boolean {
    return row.type === 'title';
  }

  isDataRow(index: number, row: any): boolean {
    return row.type === 'data';
  }

  covertToReviewTable() {
    const supportDimensions = this.config.dimensions.supported.filter(
      (item) => !(!item.name && !item.value),
    );
    const requiredDimensions = this.config.dimensions.required.filter(
      (item) => !(!item.name && !item.value),
    );

    this.dataSource = [
      {type: 'title', feature: 'Permissions'},
      {
        type: 'data',
        feature: 'Owners',
        value:
          this.config.permissions.owners.length > 0
            ? this.config.permissions.owners.join(', ')
            : 'None',
      },
      {
        type: 'data',
        feature: 'Executors',
        value:
          this.config.permissions.executors.length > 0
            ? this.config.permissions.executors.join(', ')
            : 'None',
      },

      {type: 'title', feature: 'Wi-Fi'},
      {
        type: 'data',
        feature: 'Type',
        value:
          !this.config.wifi.type || this.config.wifi.type === 'none'
            ? 'None'
            : this.config.wifi.type,
      },
      {type: 'data', feature: 'SSID', value: this.config.wifi.ssid || 'None'},
      {
        type: 'data',
        feature: 'Hidden Network',
        value: this.config.wifi.scanSsid ? 'Yes' : 'No',
      },

      {type: 'title', feature: 'Dimensions'},
      {
        type: 'data',
        feature: 'Supported Dimensions',
        value:
          supportDimensions.length > 0
            ? supportDimensions
                .map(
                  (s: DeviceDimension) =>
                    `<div class="review-dim-value"><strong>${s.name}</strong>: ${s.value}</div>`,
                )
                .join('')
            : 'None',
      },
      {
        type: 'data',
        feature: 'Required Dimensions',
        value:
          requiredDimensions.length > 0
            ? requiredDimensions
                .map(
                  (r: DeviceDimension) =>
                    `<div class="review-dim-value"><strong>${r.name}</strong>: ${r.value}</div>`,
                )
                .join('')
            : 'None',
      },
    ];

    if (this.data.source === 'copy') {
      this.dataSource.push(
        {
          type: 'title',
          feature: 'Stability & Reboot',
        },
        {
          type: 'data',
          feature: 'Max Consecutive Failures',
          value: this.config.settings.maxConsecutiveFail,
        },
        {
          type: 'data',
          feature: 'Max Tests between Reboots',
          value: this.config.settings.maxConsecutiveTest,
        },
      );
    }
  }
}
