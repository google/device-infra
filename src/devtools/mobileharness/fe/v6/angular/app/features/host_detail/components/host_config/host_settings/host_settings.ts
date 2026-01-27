import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  Input,
  OnInit,
  signal,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTooltipModule} from '@angular/material/tooltip';
import {delay, finalize, tap} from 'rxjs/operators';

import {
  ConfigSection,
  type DeviceConfig,
} from '../../../../../core/models/device_config_models';
import {
  type Editability,
  type GetHostConfigResult,
  HostConfig,
  HostConfigSection,
  type UpdateHostConfigRequest,
} from '../../../../../core/models/host_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {Dialog} from '../../../../../shared/components/config_common/dialog/dialog';
import {Footer} from '../../../../../shared/components/config_common/footer/footer';
import {ConfirmDialog} from '../../../../../shared/components/confirm_dialog/confirm_dialog';
import {objectUtils} from '../../../../../shared/utils/object_utils';
import {Dimensions} from '../../../../device_detail/components/device_config/steps/dimensions/dimensions';
import {Permissions} from '../../../../device_detail/components/device_config/steps/permissions/permissions';
import {Stability} from '../../../../device_detail/components/device_config/steps/stability/stability';
import {Wifi} from '../../../../device_detail/components/device_config/steps/wifi/wifi';
import {ConfigMode} from '../steps/config_mode/config_mode';
import {DeviceDiscovery} from '../steps/device_discovery/device_discovery';
import {HostPermissionList} from '../steps/host_permissions/host_permissions';
import {HostProperties} from '../steps/host_properties/host_properties';

/**
 * Component for displaying the host configuration settings dialog.
 *
 * It is used to configure the host's dimensions, owners, and other properties.
 */
@Component({
  selector: 'app-host-settings',
  standalone: true,
  templateUrl: './host_settings.ng.html',
  styleUrl: './host_settings.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    Dialog,
    Footer,
    HostPermissionList,
    ConfigMode,
    Permissions,
    Wifi,
    Dimensions,
    Stability,
    DeviceDiscovery,
    HostProperties,
  ],
})
export class HostSettings implements OnInit {
  private readonly dialog = inject(MatDialog); // to open confirm dialog
  private readonly dialogRef = inject(MatDialogRef<HostSettings>);
  private readonly dialogData = inject(MAT_DIALOG_DATA, {optional: true}) as {
    hostName: string;
    config: HostConfig;
  } | null;

  private readonly configService = inject(CONFIG_SERVICE);

  @Input() hostName = '';
  @Input()
  config: GetHostConfigResult = {
    hostConfig: undefined,
    uiStatus: {
      hostAdmins: {visible: true, editability: {editable: true}},
      sshAccess: {visible: true, editability: {editable: true}},
      deviceConfigMode: {visible: true, editability: {editable: true}},
      deviceConfig: {
        sectionStatus: {visible: true, editability: {editable: true}},
        subSections: {},
      },
      hostProperties: {
        sectionStatus: {visible: true, editability: {editable: true}},
      },
      deviceDiscovery: {visible: true, editability: {editable: true}},
    },
  };

  readonly navList = [
    {
      id: 'host-permissions',
      type: 'item',
      icon: 'security',
      label: 'Host Permissions',
      visibility: () =>
        this.config.uiStatus.hostAdmins.visible ||
        this.config.uiStatus.sshAccess.visible,
    },
    {
      id: 'device-config',
      type: 'group',
      title: 'Device Configs',
      children: [
        {
          id: 'config-mode',
          icon: 'settings_ethernet',
          label: 'Config Mode',
          visibility: () => this.config.uiStatus.deviceConfigMode.visible,
        },
        {
          id: 'permissions',
          icon: 'badge',
          label: 'Permissions',
          visibility: () =>
            this.config.uiStatus.deviceConfig.sectionStatus.visible &&
            this.config.uiStatus.deviceConfig.subSections.permissions
              ?.visible !== false,
        },
        {
          id: 'wifi',
          icon: 'wifi',
          label: 'Wi-Fi',
          visibility: () =>
            this.config.uiStatus.deviceConfig.sectionStatus.visible &&
            this.config.uiStatus.deviceConfig.subSections.wifi?.visible !==
              false,
        },
        {
          id: 'dimensions',
          icon: 'category',
          label: 'Dimensions',
          visibility: () =>
            this.config.uiStatus.deviceConfig.sectionStatus.visible &&
            this.config.uiStatus.deviceConfig.subSections.dimensions
              ?.visible !== false,
        },
        {
          id: 'stability',
          icon: 'autorenew',
          label: 'Stability & Reboot',
          visibility: () =>
            this.config.uiStatus.deviceConfig.sectionStatus.visible &&
            this.config.uiStatus.deviceConfig.subSections.settings?.visible !==
              false,
        },
      ],
      visibility: () =>
        this.config.uiStatus.deviceConfig.sectionStatus.visible ||
        this.config.uiStatus.deviceConfigMode.visible,
    },
    {
      id: 'device-discovery',
      type: 'item',
      icon: 'travel_explore',
      label: 'Device Discovery',
      visibility: () => this.config.uiStatus.deviceDiscovery.visible,
    },
    {
      id: 'host-properties',
      type: 'item',
      icon: 'tune',
      label: 'Host Properties',
      visibility: () =>
        this.config.uiStatus.hostProperties.sectionStatus.visible,
    },
  ];
  activeSection = signal<string>('host-permissions');
  activeSectionEditibility = signal<Editability>(
    this.config.uiStatus.hostAdmins.editability || {editable: true},
  );

  // Host permissions UI status.
  // This is used to determine the visibility and editability of the host
  // permissions in the UI.
  hostPermissionsUiStatus = {
    hostAdmins: this.config.uiStatus.hostAdmins,
    sshAccess: this.config.uiStatus.sshAccess,
  };

  // Dimensions UI status.
  // This is used to determine the visibility and editability of the dimensions
  // in the UI.
  dimensionsUiStatus = {
    sectionStatus: this.config.uiStatus.deviceConfig.sectionStatus,
  };

  // Device discovery UI status.
  // This is used to determine the visibility and editability of the device
  // discovery in the UI.
  deviceDiscoveryUiStatus = {
    sectionStatus: this.config.uiStatus.deviceDiscovery,
  };

  get permissionsUiStatus() {
    return (
      this.config.uiStatus.deviceConfig.subSections.permissions || {
        visible: true,
        editability: {editable: true},
      }
    );
  }

  get wifiUiStatus() {
    return (
      this.config.uiStatus.deviceConfig.subSections.wifi || {
        visible: true,
        editability: {editable: true},
      }
    );
  }

  get settingsUiStatus() {
    return (
      this.config.uiStatus.deviceConfig.subSections.settings || {
        visible: true,
        editability: {editable: true},
      }
    );
  }

  // Default device config for the host.
  // This is used to display the device config in the UI.
  deviceConfig: DeviceConfig = {
    permissions: {
      owners: [],
      executors: [],
    },
    wifi: {
      type: 'none',
      ssid: '',
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
  };

  // host config for the host.
  // This is used to display the host config in the UI.
  hostConfig: HostConfig = {
    permissions: {
      hostAdmins: [],
      sshAccess: [],
    },
    deviceConfigMode: 'PER_DEVICE',
    deviceConfig: this.deviceConfig,
    hostProperties: [],
    deviceDiscovery: {
      monitoredDeviceUuids: [],
      testbedUuids: [],
      miscDeviceUuids: [],
      overTcpIps: [],
      overSshDevices: [],
      manekiSpecs: [],
    },
  };

  private originalDeviceConfig: DeviceConfig = this.deviceConfig;
  private originalHostConfig: HostConfig = this.hostConfig;

  tooltipText = signal<string>('');

  // Used for dimensions step duplicate check.
  hasError = false;

  saving = signal<boolean>(false);

  ngOnInit() {
    if (this.dialogData) {
      this.hostName = this.dialogData.hostName || this.hostName;
      if (this.dialogData.config) {
        this.config = {
          ...this.config,
          hostConfig: this.dialogData.config,
        };
      }
    }

    // If there is no visible section, set the active section to default.
    if (this.visibleSections().length === 0) {
      this.activeSection.set('default');
      return;
    }

    this.initializeVisibleSections();
    this.initializeData();
    this.initializeUIStatus();

    // Set the tooltip text of device config based on the device config mode.
    const tooltip =
      this.hostConfig.deviceConfigMode === 'PER_DEVICE'
        ? `The settings on this page define the default configuration that will be automatically applied to any new device connected to this host.`
        : `The settings on this page are shared by all devices on this host, as 'Shared Configuration' is selected in the Config Mode settings.`;
    this.tooltipText.set(tooltip);
  }

  setActiveSection(event: Event, section: string) {
    event.preventDefault();

    this.setActiveSectionEditibility(section);

    if (
      section !== this.activeSection() &&
      this.isCategoryDirty(this.activeSection())
    ) {
      const dialogData = {
        title: 'Unsaved Changes',
        content:
          'You have unsaved changes. Are you sure you want to discard them?',
        type: 'warning',
        primaryButtonLabel: 'Discard and Switch',
        secondaryButtonLabel: 'Stay Here',
      };

      const unsaveDialogRef = this.dialog.open(ConfirmDialog, {
        data: dialogData,
        disableClose: true,
      });
      unsaveDialogRef.afterClosed().subscribe((result) => {
        if (result === 'primary') {
          this.resetConfig();
          this.activeSection.set(section);
        }

        return;
      });
    } else {
      this.activeSection.set(section);
    }
  }

  setActiveSectionEditibility(section: string) {
    switch (section) {
      case 'host-permissions':
        this.activeSectionEditibility.set(
          this.config.uiStatus.hostAdmins.editability || {editable: true},
        );
        break;
      case 'config-mode':
        this.activeSectionEditibility.set(
          this.config.uiStatus.deviceConfigMode.editability || {editable: true},
        );
        break;
      case 'permissions':
      case 'wifi':
      case 'dimensions':
      case 'stability':
        this.activeSectionEditibility.set(
          this.config.uiStatus.deviceConfig.sectionStatus.editability || {
            editable: true,
          },
        );
        break;
      case 'device-discovery':
        this.activeSectionEditibility.set(
          this.config.uiStatus.deviceDiscovery.editability || {editable: true},
        );
        break;
      case 'host-properties':
        this.activeSectionEditibility.set(
          this.config.uiStatus.hostProperties.sectionStatus.editability || {
            editable: true,
          },
        );
        break;
      default:
        this.activeSectionEditibility.set({editable: true, reason: ''});
    }
  }

  visibleSections() {
    return this.navList.filter((item) => item.visibility());
  }

  initializeVisibleSections() {
    const firstVisibleSection = this.visibleSections()[0];
    let sectionId = '';
    if (firstVisibleSection.type === 'item') {
      sectionId = firstVisibleSection.id;
    }

    if (firstVisibleSection.type === 'group') {
      const firstVisibleChild = firstVisibleSection.children?.find((child) =>
        child.visibility(),
      );
      sectionId = firstVisibleChild?.id || '';
    }

    this.activeSection.set(sectionId);
    this.setActiveSectionEditibility(this.activeSection());
  }

  initializeData() {
    this.deviceConfig = objectUtils.deepCopy({
      ...this.config.hostConfig!.deviceConfig!,
      permissions: {
        owners: this.config.hostConfig!.permissions.hostAdmins,
        executors: this.config.hostConfig!.deviceConfig!.permissions.executors,
      },
    }) as DeviceConfig;
    this.hostConfig = objectUtils.deepCopy(
      this.config.hostConfig!,
    ) as HostConfig;

    this.originalDeviceConfig = objectUtils.deepCopy(
      this.deviceConfig,
    ) as DeviceConfig;
    this.originalHostConfig = objectUtils.deepCopy(
      this.hostConfig,
    ) as HostConfig;
  }

  initializeUIStatus() {
    this.hostPermissionsUiStatus = {
      hostAdmins: this.config.uiStatus.hostAdmins,
      sshAccess: this.config.uiStatus.sshAccess,
    };

    this.dimensionsUiStatus = {
      sectionStatus: this.config.uiStatus.deviceConfig.sectionStatus,
    };

    this.deviceDiscoveryUiStatus = {
      sectionStatus: this.config.uiStatus.deviceDiscovery,
    };
  }

  isCategoryDirty(category: string) {
    if (category === 'host-permissions') {
      return (
        JSON.stringify(this.originalHostConfig.permissions) !==
        JSON.stringify(this.hostConfig.permissions)
      );
    }

    if (category === 'config-mode') {
      return (
        this.originalHostConfig.deviceConfigMode !==
        this.hostConfig.deviceConfigMode
      );
    }

    // Device Configs
    if (category === 'permissions') {
      return (
        JSON.stringify(this.originalDeviceConfig.permissions) !==
        JSON.stringify(this.deviceConfig.permissions)
      );
    }
    if (category === 'wifi') {
      return (
        JSON.stringify(this.originalDeviceConfig.wifi) !==
        JSON.stringify(this.deviceConfig.wifi)
      );
    }
    if (category === 'dimensions') {
      return (
        JSON.stringify(this.originalDeviceConfig.dimensions) !==
        JSON.stringify(this.deviceConfig.dimensions)
      );
    }
    if (category === 'stability') {
      return (
        JSON.stringify(this.originalDeviceConfig.settings) !==
        JSON.stringify(this.deviceConfig.settings)
      );
    }

    if (category === 'device-discovery') {
      return (
        JSON.stringify(this.originalHostConfig.deviceDiscovery) !==
        JSON.stringify(this.hostConfig.deviceDiscovery)
      );
    }

    if (category === 'host-properties') {
      return (
        JSON.stringify(this.originalHostConfig.hostProperties) !==
        JSON.stringify(this.hostConfig.hostProperties)
      );
    }

    return false;
  }

  resetConfig() {
    this.deviceConfig = objectUtils.deepCopy(
      this.originalDeviceConfig,
    ) as DeviceConfig;
    this.hostConfig = objectUtils.deepCopy(
      this.originalHostConfig,
    ) as HostConfig;
  }

  reset() {
    this.dialogRef.close({action: 'reset', hostName: this.hostName});
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
        this.activeSection.set('permissions');
        return;
      }
      if (result === 'primary') {
        this.save(true);
      }
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
    if (errorCode === 'SELF_LOCKOUT_DETECTED') {
      this.selfLockout();
      return;
    }

    const errorMessage = errorCode
      ? ` with error code ${errorCode}. Please try again.`
      : '. Please try again.';

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

  save(selfLockout = false) {
    const section = this.activeSection();
    const requestSectionMap: Record<string, HostConfigSection> = {
      'host-permissions': HostConfigSection.HOST_PERMISSIONS,
      'config-mode': HostConfigSection.DEVICE_CONFIG_MODE,
      'permissions': HostConfigSection.DEVICE_CONFIG,
      'wifi': HostConfigSection.DEVICE_CONFIG,
      'dimensions': HostConfigSection.DEVICE_CONFIG,
      'stability': HostConfigSection.DEVICE_CONFIG,
      'device-discovery': HostConfigSection.DEVICE_DISCOVERY,
      'host-properties': HostConfigSection.HOST_PROPERTIES,
    };

    const deviceConfigSectionMap: Record<string, ConfigSection> = {
      'permissions': ConfigSection.PERMISSIONS,
      'wifi': ConfigSection.WIFI,
      'dimensions': ConfigSection.DIMENSIONS,
      'stability': ConfigSection.STABILITY,
    };

    this.hostConfig.deviceConfig = this.deviceConfig;
    const request: UpdateHostConfigRequest = {
      hostName: this.hostName,
      config: this.hostConfig,
      scope: {
        section: requestSectionMap[section],
        deviceConfigSection: deviceConfigSectionMap[section],
      },
      options: {overrideSelfLockout: selfLockout},
    };

    this.configService
      .updateHostConfig(request)
      .pipe(
        tap(() => {
          this.saving.set(true);
        }),
        delay(1000),
        finalize(() => {
          this.saving.set(false);
        }),
      )
      .subscribe((result) => {
        if (!result.success) {
          this.error(result.error?.code);
          return;
        }

        this.originalHostConfig = objectUtils.deepCopy(
          this.hostConfig,
        ) as HostConfig;
        this.originalDeviceConfig = objectUtils.deepCopy(
          this.deviceConfig,
        ) as DeviceConfig;

        this.success();
      });
  }

  discard() {
    this.resetConfig();
  }
}
