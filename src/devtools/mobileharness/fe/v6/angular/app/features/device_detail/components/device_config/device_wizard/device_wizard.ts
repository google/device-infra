import {CommonModule} from '@angular/common';
import {AfterViewInit, ChangeDetectionStrategy, Component, inject, OnInit, signal, TemplateRef, ViewChild} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatStepperModule} from '@angular/material/stepper';
import {MatTableModule} from '@angular/material/table';
import {delay, finalize, tap} from 'rxjs/operators';

import type {DeviceConfig, DeviceDimension} from '../../../../../core/models/device_config_models';
import {ConfigSection, UpdateDeviceConfigRequest} from '../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {Dialog} from '../../../../../shared/components/config_common/dialog/dialog';
import {ReviewTable, type ReviewTableRow} from '../../../../../shared/components/config_common/review_table/review_table';
import {WizardStep, WizardStepper} from '../../../../../shared/components/config_common/wizard_stepper/wizard_stepper';
import {ConfirmDialog} from '../../../../../shared/components/confirm_dialog/confirm_dialog';
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
export class DeviceWizard implements OnInit, AfterViewInit {
  readonly data = inject(MAT_DIALOG_DATA);
  readonly configService = inject(CONFIG_SERVICE);
  private readonly dialog = inject(MatDialog);
  private readonly dialogRef = inject(MatDialogRef<DeviceWizard>);

  // used for wizard stepper
  @ViewChild('permissions') permissionsTemplate!: TemplateRef<{}>;
  @ViewChild('wifi') wifiTemplate!: TemplateRef<{}>;
  @ViewChild('dimensions') dimensionsTemplate!: TemplateRef<{}>;
  @ViewChild('review') reviewTemplate!: TemplateRef<{}>;
  WIZARD_STEPS_NEW: WizardStep[] = this.getWizardSteps();

  currentStep = signal<string>('permissions');

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

  // used for dimensions step duplicate check
  hasError = false;
  errorStep = 'dimensions';

  // used for review & submit step
  dataSource: ReviewTableRow[] = [];

  // used for apply changes button
  @ViewChild('stepper') stepper!: WizardStepper;
  verifying = signal<boolean>(false);
  applyChangesDisabled = signal<boolean>(false);

  getWizardSteps(): WizardStep[] {
    return [
      {
        id: 'permissions',
        label: 'Permissions',
        template: this.permissionsTemplate,
      },
      {id: 'wifi', label: 'Wi-Fi', template: this.wifiTemplate},
      {
        id: 'dimensions',
        label: 'Dimensions',
        template: this.dimensionsTemplate,
      },
      {
        id: 'review-and-submit',
        label: 'Review & Submit',
        template: this.reviewTemplate,
      },
    ];
  }

  ngOnInit() {
    if (this.data.source === 'copy') {
      this.currentStep.set('review-and-submit');
      this.config = this.data.config;
      this.covertToReviewTable();
    }
  }

  ngAfterViewInit() {
    this.WIZARD_STEPS_NEW = this.getWizardSteps();
  }

  onCurrentStepChange(currentStep: string) {
    this.currentStep.set(currentStep);
    if (currentStep === 'review-and-submit') {
      this.covertToReviewTable();
    }
  }

  // used for review & submit table
  isTitleRow(index: number, row: ReviewTableRow): boolean {
    return row.type === 'title';
  }
  isDataRow(index: number, row: ReviewTableRow): boolean {
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
        value: this.config.permissions.owners.length > 0 ?
            this.config.permissions.owners.join(', ') :
            'None',
      },
      {
        type: 'data',
        feature: 'Executors',
        value: this.config.permissions.executors.length > 0 ?
            this.config.permissions.executors.join(', ') :
            'None',
      },
      {type: 'title', feature: 'Wi-Fi'},
      {
        type: 'data',
        feature: 'Type',
        value: !this.config.wifi.type || this.config.wifi.type === 'none' ?
            'None' :
            this.config.wifi.type,
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
        value: supportDimensions.length > 0 ?
            supportDimensions
                .map(
                    (s: DeviceDimension) =>
                        `<div class="review-dim-value"><strong>${
                            s.name}</strong>: ${s.value}</div>`,
                    )
                .join('') :
            'None',
      },
      {
        type: 'data',
        feature: 'Required Dimensions',
        value: requiredDimensions.length > 0 ?
            requiredDimensions
                .map(
                    (r: DeviceDimension) =>
                        `<div class="review-dim-value"><strong>${
                            r.name}</strong>: ${r.value}</div>`,
                    )
                .join('') :
            'None',
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

  // used for apply changes button
  submit(overrideSelfLockout = false) {
    const request: UpdateDeviceConfigRequest = {
      deviceId: this.data.deviceId,
      config: this.config,
      section: ConfigSection.ALL,
      options: {overrideSelfLockout},
    };

    this.configService.updateDeviceConfig(request)
        .pipe(
            tap(() => {
              if (this.data.source === 'copy') {
                this.verifying.set(true);
              } else {
                this.stepper.verifying.set(true);
              }
            }),
            delay(1000),
            finalize(() => {
              if (this.data.source === 'copy') {
                this.verifying.set(false);
              } else {
                this.stepper.verifying.set(false);
              }
            }),
            )
        .subscribe((result) => {
          if (!result.success) {
            this.error(result.error?.code);
            return;
          }

          this.success();
        });
  }

  success() {
    const dialogData = {
      title: 'Configuration Saved',
      content: 'Your configuration has been saved successfully. ',
      type: 'success',
      primaryButtonLabel: 'OK',
    };
    this.dialog
        .open(ConfirmDialog, {
          data: dialogData,
          disableClose: true,
        })
        .afterClosed()
        .subscribe(() => {
          this.dialogRef.close(true);
        });
  }

  error(errorCode?: string) {
    if (errorCode === 'SELF_LOCKOUT_DETECTED' && this.data.source !== 'copy') {
      this.selfLockout();
      return;
    }

    const errorMessage = errorCode ?
        ` with error code ${errorCode}. Please try again.` :
        '. Please try again.';
    const dialogData = {
      title: 'Configuration Failed',
      content: `Your configuration has failed to save` + errorMessage,
      type: 'error',
      primaryButtonLabel: 'OK',
    };

    this.dialog.open(ConfirmDialog, {
      data: dialogData,
      disableClose: true,
    });
  }

  selfLockout() {
    const dialogData = {
      title: 'Permission Warning',
      content:
          'The new owners list does not contain your username, and you are not a member of any of the specified owner groups. Proceeding will remove your ability to configure this device in the future.',
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
        this.currentStep.set('permissions');
        // this.cdr.markForCheck();
        return;
      }
      if (result === 'primary') {
        this.submit(true);
      }
    });
  }
}
