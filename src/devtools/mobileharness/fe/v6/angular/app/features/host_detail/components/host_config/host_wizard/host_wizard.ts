import {AfterViewInit, ChangeDetectionStrategy, Component, inject, OnInit, signal, TemplateRef, ViewChild} from '@angular/core';
import {MatChipsModule} from '@angular/material/chips';
import {MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {delay, finalize, tap} from 'rxjs/operators';

import {HostConfig} from '../../../../../core/models/host_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {Permissions} from '../../../../../features/device_detail/components/device_config/steps/permissions/permissions';
import {Wifi} from '../../../../../features/device_detail/components/device_config/steps/wifi/wifi';
import {Dialog} from '../../../../../shared/components/config_common/dialog/dialog';
import {ReviewTable, type ReviewTableRow} from '../../../../../shared/components/config_common/review_table/review_table';
import {WizardStep, WizardStepper} from '../../../../../shared/components/config_common/wizard_stepper/wizard_stepper';
import {ConfirmDialog} from '../../../../../shared/components/confirm_dialog/confirm_dialog';
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
export class HostWizard implements OnInit, AfterViewInit {
  readonly data = inject(MAT_DIALOG_DATA);  // to receive hostDetail openDialog
                                            // params source, config, etc.
  private readonly dialog = inject(MatDialog);  // to open confirm dialog
  private readonly dialogRef =
      inject(MatDialogRef<HostWizard>);  // to close the dialog

  private readonly configService = inject(CONFIG_SERVICE);

  // used for stepper
  @ViewChild('permissions') permissionsTemplate!: TemplateRef<{}>;
  @ViewChild('configMode') configModeTemplate!: TemplateRef<{}>;
  @ViewChild('default') defaultTemplate!: TemplateRef<{}>;
  @ViewChild('review') reviewTemplate!: TemplateRef<{}>;

  WIZARD_STEPS: WizardStep[] = this.getWizardSteps();

  currentStep = signal('host-permissions');

  readonly hostConfig = signal<HostConfig>(
      this.data.config || {
        permissions: {
          hostAdmins: [],
          sshAccess: [],
        },
        deviceConfigMode: 'PER_DEVICE',
        deviceConfig: {
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
          dimensions: {
            supported: [],
            required: [],
          },
          settings: {
            maxConsecutiveFail: 5,
            maxConsecutiveTest: 10000,
          },
        },
        hostProperties: [],
        deviceDiscovery: {
          monitoredDeviceUuids: [],
          testbedUuids: [],
          miscDeviceUuids: [],
          overTcpIps: [],
          overSshDevices: [],
          manekiSpecs: [],
        },
      },
  );

  // used for review & submit step
  dataSource: ReviewTableRow[] = [];

  // used for apply changes button
  @ViewChild('stepper') stepper!: WizardStepper;
  verifying = signal<boolean>(false);
  applyChangesDisabled = signal<boolean>(false);

  ngOnInit() {
    if (this.data.source === 'copy') {
      this.currentStep.set('review-and-submit');
      this.covertToReviewTable();
    }
  }

  getWizardSteps(): WizardStep[] {
    return [
      {
        id: 'host-permissions',
        label: 'Host Permissions',
        template: this.permissionsTemplate,
      },
      {
        id: 'device-config-mode',
        label: 'Device Config Mode',
        template: this.configModeTemplate,
      },
      {
        id: 'default-device-config',
        label: 'Default Device Config',
        template: this.defaultTemplate,
      },
      {
        id: 'review-and-submit',
        label: 'Review & Submit',
        template: this.reviewTemplate,
      },
    ];
  }

  ngAfterViewInit() {
    this.WIZARD_STEPS = this.getWizardSteps();
  }

  onCurrentStepChange(currentStep: string) {
    if (currentStep === 'default-device-config') {
      this.hostConfig.set({
        ...this.hostConfig(),
        deviceConfig: {
          ...this.hostConfig().deviceConfig!,
          permissions: {
            owners: this.hostConfig().permissions.hostAdmins,
            executors:
                this.hostConfig().deviceConfig?.permissions.executors || [],
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
    const owners = this.hostConfig().deviceConfig?.permissions.owners || [];
    const executors =
        this.hostConfig().deviceConfig?.permissions.executors || [];

    this.dataSource = [
      {type: 'title', feature: 'Host Permissions'},
      {
        type: 'data',
        feature: 'Host Admins',
        value: this.hostConfig().permissions.hostAdmins.length > 0 ?
            this.hostConfig().permissions.hostAdmins.join(', ') :
            'None',
      },
      {
        type: 'data',
        feature: 'SSH Access',
        value: this.hostConfig().permissions.sshAccess.length > 0 ?
            this.hostConfig().permissions.sshAccess.join(', ') :
            'None',
      },
      {type: 'title', feature: 'Device Config Mode'},
      {
        type: 'data',
        feature: 'Device Config Mode',
        value: this.hostConfig().deviceConfigMode,
      },
      {type: 'title', feature: 'Default Device Config - Permissions'},
      {
        type: 'data',
        feature: 'Device Owners',
        value: owners.length > 0 ?
            this.hostConfig().deviceConfig?.permissions.owners.join(', ') :
            'None',
      },
      {
        type: 'data',
        feature: 'Device Executors',
        value: executors.length > 0 ?
            this.hostConfig().deviceConfig?.permissions.executors.join(', ') :
            'None',
      },
      {type: 'title', feature: 'Default Device Config - Wi-Fi'},
      {
        type: 'data',
        feature: 'Type',
        value: !this.hostConfig().deviceConfig?.wifi.type ||
                this.hostConfig().deviceConfig?.wifi.type === 'none' ?
            'None' :
            this.hostConfig().deviceConfig?.wifi.type,
      },
      {
        type: 'data',
        feature: 'SSID',
        value: this.hostConfig().deviceConfig?.wifi.ssid || 'None',
      },
      {
        type: 'data',
        feature: 'Hidden Network',
        value: this.hostConfig().deviceConfig?.wifi.scanSsid ? 'Yes' : 'No',
      },
    ];

    if (this.data.source === 'copy') {
      const supportDimensions =
          (this.hostConfig().deviceConfig?.dimensions.supported ||
           []).filter((item) => !(!item.name && !item.value));
      const requiredDimensions =
          (this.hostConfig().deviceConfig?.dimensions.required ||
           []).filter((item) => !(!item.name && !item.value));

      const overSshDevices =
          (this.hostConfig().deviceDiscovery?.overSshDevices || [])
              .filter(
                  (v) =>
                      !(!v.ipAddress && !v.username && !v.password &&
                        !v.sshDeviceType),
              );
      const manekiSpecs = (this.hostConfig().deviceDiscovery?.manekiSpecs ||
                           []).filter((v) => !(!v.type && !v.macAddress));

      const hostProperties = (this.hostConfig().hostProperties || [])
                                 .filter(
                                     (v) => !(!v.key && !v.value),
                                 );

      this.dataSource.push(
          {type: 'title', feature: 'Default Device Config - Dimensions'},
          {
            type: 'data',
            feature: 'Supported Dimensions',
            value: supportDimensions.length > 0 ?
                supportDimensions
                    .map(
                        (item) => `<div class="review-dim-value"><strong>${
                            item.name}</strong>: ${item.value}</div>`,
                        )
                    .join(', ') :
                'None',
          },
          {
            type: 'data',
            feature: 'Required Dimensions',
            value: requiredDimensions.length > 0 ?
                requiredDimensions
                    .map(
                        (item) => `<div class="review-dim-value"><strong>${
                            item.name}</strong>: ${item.value}</div>`,
                        )
                    .join(', ') :
                'None',
          },
          {
            type: 'title',
            feature: 'Default Device Config - Stability & Reboot',
          },
          {
            type: 'data',
            feature: 'Max Consecutive Failures',
            value: this.hostConfig().deviceConfig?.settings.maxConsecutiveFail,
          },
          {
            type: 'data',
            feature: 'Max Tests between Reboots',
            value: this.hostConfig().deviceConfig?.settings.maxConsecutiveTest,
          },
          {
            type: 'title',
            feature: 'Device Discovery',
          },
          {
            type: 'data',
            feature: 'Monitored Device UUIDs',
            value:
                this.hostConfig().deviceDiscovery.monitoredDeviceUuids.length >
                    0 ?
                this.hostConfig().deviceDiscovery.monitoredDeviceUuids.join(
                    ', ',
                    ) :
                'None',
          },
          {
            type: 'data',
            feature: 'Testbed UUIDs',
            value: this.hostConfig().deviceDiscovery.testbedUuids.length > 0 ?
                this.hostConfig().deviceDiscovery.testbedUuids.join(', ') :
                'None',
          },
          {
            type: 'data',
            feature: 'Misc Device UUIDs',
            value:
                this.hostConfig().deviceDiscovery.miscDeviceUuids.length > 0 ?
                this.hostConfig().deviceDiscovery.miscDeviceUuids.join(', ') :
                'None',
          },
          {
            type: 'data',
            feature: 'Over TCP IPs',
            value: this.hostConfig().deviceDiscovery.overTcpIps.length > 0 ?
                this.hostConfig().deviceDiscovery.overTcpIps.join('<br />') :
                'None',
          },
          {
            type: 'data',
            feature: 'Over SSH Devices',
            value: overSshDevices.length > 0 ? `<table class="review-ssh-table">
                    <tr>
                      <td width="20%"><strong>IP Address</strong></td>
                      <td width="20%"><strong>Username</strong></td>
                      <td width="20%"><strong>Password</strong></td>
                      <td width="20%"><strong>SSH Device Type</strong></td>
                    </tr>` +
                    overSshDevices
                        .map(
                            (item) => `<tr>
                          <td>${item.ipAddress}</td>
                          <td>${item.username}</td>
                          <td>${item.password}</td>
                          <td>${item.sshDeviceType}</td>
                        </tr>`,
                            )
                        .join('') +
                    `</table>` :
                                               'None',
          },
          {
            type: 'data',
            feature: 'Maneki Specs',
            value: manekiSpecs.length > 0 ? `<table>
                    <tr>
                      <td width="20%"><strong>Device Type</strong></td>
                      <td width="20%"><strong>Mac Address</strong></td>
                    </tr>` +
                    manekiSpecs
                        .map(
                            (item) => `<tr>
                          <td>${item.type}</td>
                          <td>${item.macAddress}</td>
                        </tr>`,
                            )
                        .join('') +
                    `</table>` :
                                            'None',
          },
          {
            type: 'title',
            feature: 'Host Properties',
          },
          {
            type: 'data',
            feature: 'Host Properties',
            value: hostProperties.length > 0 ?
                hostProperties
                    .map(
                        (item) => `<div><strong>${item.key}</strong>: ${
                            item.value}</div>`,
                        )
                    .join('') :
                'None',
          },
      );
    }
  }

  submit() {
    this.hostConfig.set({
      ...this.hostConfig(),
      deviceConfig: {
        permissions: {
          owners: this.hostConfig().permissions.hostAdmins,
          executors:
              this.hostConfig().deviceConfig?.permissions?.executors || [],
        },
        wifi: this.hostConfig().deviceConfig?.wifi!,
        dimensions: this.hostConfig().deviceConfig?.dimensions!,
        settings: this.hostConfig().deviceConfig?.settings!,
      },
    });

    this.configService
        .updateHostConfig({
          hostName: this.data.hostName,
          config: this.hostConfig(),
        })
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
        .subscribe((result) => {
          if (result) {
            this.dialogRef.close();
          }
        });
  }

  error(errorCode?: string) {
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
}
