import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  Input,
  OnInit,
  signal,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
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
import {of, throwError} from 'rxjs';
import {concatMap, finalize} from 'rxjs/operators';

import {
  ConfigSection,
  type DeviceConfig,
} from '../../../../../core/models/device_config_models';
import {
  type Editability,
  type HostConfig,
  HostConfigSection,
  type HostConfigUiStatus,
  type PartStatus,
  type UpdateHostConfigRequest,
} from '../../../../../core/models/host_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {HostConfigStateService} from '../../../../../core/services/config/host_config_state_service';
import {
  clearEmptyDimensions,
  hasEmptyDimensions,
} from '../../../../../core/utils/device_config_utils';
import {
  clearEmptyProperties,
  hasEmptyProperties,
} from '../../../../../core/utils/host_config_utils';
import {Dialog} from '../../../../../shared/components/config_common/dialog/dialog';
import {Footer} from '../../../../../shared/components/config_common/footer/footer';
import {ConfirmDialog} from '../../../../../shared/components/confirm_dialog/confirm_dialog';
import {useSaveInterceptors} from '../../../../../shared/composables/save_interceptors';
import {SnackBarService} from '../../../../../shared/services/snackbar_service';
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
  readonly testId = Math.random();
  constructor() {
    console.log('HostSettings constructor called, testId =', this.testId);
  }
  private readonly dialog = inject(MatDialog); // to open confirm dialog
  private readonly dialogRef = inject(MatDialogRef<HostSettings>);
  private readonly destroyRef = inject(DestroyRef);
  private readonly saveInterceptors = useSaveInterceptors();
  private readonly dialogData = inject(MAT_DIALOG_DATA, {optional: true}) as {
    hostName: string;
    config: HostConfig;
  } | null;

  private readonly configService = inject(CONFIG_SERVICE);
  private readonly hostConfigStateService = inject(HostConfigStateService);
  private readonly snackBar = inject(SnackBarService);

  @Input() hostName = '';
  @Input() config?: HostConfig;

  readonly uiStatus = signal<HostConfigUiStatus>({
    hostAdmins: {visible: true, editability: {editable: true}},
    deviceConfigMode: {visible: true, editability: {editable: true}},
    deviceConfig: {
      sectionStatus: {visible: true, editability: {editable: true}},
      subSections: {},
    },
    hostProperties: {
      sectionStatus: {visible: true, editability: {editable: true}},
    },
    deviceDiscovery: {visible: true, editability: {editable: true}},
  });

  readonly navList = [
    {
      id: 'host-permissions',
      type: 'item',
      icon: 'security',
      label: 'Host Permissions',
      visibility: () => this.uiStatus().hostAdmins.visible,
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
          visibility: () => this.uiStatus().deviceConfigMode.visible,
        },
        {
          id: 'permissions',
          icon: 'badge',
          label: 'Permissions',
          visibility: () =>
            this.uiStatus().deviceConfig.sectionStatus.visible &&
            this.uiStatus().deviceConfig.subSections.permissions?.visible ===
              true,
        },
        {
          id: 'wifi',
          icon: 'wifi',
          label: 'Wi-Fi',
          visibility: () =>
            this.uiStatus().deviceConfig.sectionStatus.visible &&
            this.uiStatus().deviceConfig.subSections.wifi?.visible === true,
        },
        {
          id: 'dimensions',
          icon: 'category',
          label: 'Dimensions',
          visibility: () =>
            this.uiStatus().deviceConfig.sectionStatus.visible &&
            this.uiStatus().deviceConfig.subSections.dimensions?.visible ===
              true,
        },
        {
          id: 'stability',
          icon: 'autorenew',
          label: 'Stability & Reboot',
          visibility: () =>
            this.uiStatus().deviceConfig.sectionStatus.visible &&
            this.uiStatus().deviceConfig.subSections.settings?.visible === true,
        },
      ],
      visibility: () =>
        this.uiStatus().deviceConfig.sectionStatus.visible ||
        this.uiStatus().deviceConfigMode.visible,
    },
    {
      id: 'device-discovery',
      type: 'item',
      icon: 'travel_explore',
      label: 'Device Discovery',
      visibility: () => this.uiStatus().deviceDiscovery.visible,
    },
    {
      id: 'host-properties',
      type: 'item',
      icon: 'tune',
      label: 'Host Properties',
      visibility: () => this.uiStatus().hostProperties.sectionStatus.visible,
    },
  ];
  activeSection = signal<string>('host-permissions');

  private getSectionEditability(section: string): Editability {
    switch (section) {
      case 'host-permissions':
        return (
          this.hostPermissionsUiStatus().hostAdmins.editability || {
            editable: true,
          }
        );
      case 'config-mode':
        return this.configModeUiStatus().editability || {editable: true};
      case 'permissions':
        return this.permissionsUiStatus().editability || {editable: true};
      case 'wifi':
        return this.wifiUiStatus().editability || {editable: true};
      case 'dimensions':
        return (
          this.dimensionsUiStatus().sectionStatus.editability || {
            editable: true,
          }
        );
      case 'stability':
        return this.settingsUiStatus().editability || {editable: true};
      case 'device-discovery':
        return (
          this.deviceDiscoveryUiStatus().sectionStatus.editability || {
            editable: true,
          }
        );
      case 'host-properties':
        return (
          this.hostPropertiesUiStatus().sectionStatus.editability || {
            editable: true,
          }
        );
      default:
        return {editable: true, reason: ''};
    }
  }

  readonly activeSectionEditibility = computed<Editability>(() => {
    return this.getSectionEditability(this.activeSection());
  });

  private getVisibleLeafSectionIds(): string[] {
    return this.navList
      .filter((item) => item.visibility())
      .flatMap((item) => {
        if (item.type === 'item') {
          return [item.id];
        }
        if (item.type === 'group') {
          return (item.children ?? [])
            .filter((child) => child.visibility())
            .map((child) => child.id);
        }
        return [];
      });
  }

  readonly isAllVisibleSectionsEditable = computed<boolean>(() => {
    if (!this.hasPermission()) {
      return false;
    }
    const leafSectionIds = this.getVisibleLeafSectionIds();
    if (leafSectionIds.length === 0) {
      return false;
    }
    return leafSectionIds.every(
      (id) => this.getSectionEditability(id).editable,
    );
  });

  private getPermissionOverriddenStatus(
    status: PartStatus | undefined,
  ): PartStatus {
    const defaultStatus: PartStatus = {
      visible: true,
      editability: {editable: true},
    };
    const currentStatus = status || defaultStatus;

    if (!this.hasPermission()) {
      return {
        ...currentStatus,
        editability: {
          editable: false,
          reason: 'You do not have permission to edit this host configuration.',
        },
      };
    }
    return currentStatus;
  }

  private getDeviceConfigSectionEditability(): {
    editable: boolean;
    reason: string;
  } {
    const editability = this.uiStatus().deviceConfig.sectionStatus.editability;
    if (!editability) {
      return {
        editable: false,
        reason:
          'Configuration editability status is unavailable. This section is read-only for safety.',
      };
    }
    return {
      editable: editability.editable === true,
      reason: editability.reason || 'This section is not editable.',
    };
  }

  readonly hostPermissionsUiStatus = computed(() => {
    return {
      hostAdmins: this.getPermissionOverriddenStatus(
        this.uiStatus().hostAdmins,
      ),
    };
  });

  readonly configModeUiStatus = computed(() => {
    return this.getPermissionOverriddenStatus(this.uiStatus().deviceConfigMode);
  });

  readonly permissionsUiStatus = computed(() => {
    const parent = this.getDeviceConfigSectionEditability();
    if (!parent.editable) {
      return {
        visible:
          this.uiStatus().deviceConfig.subSections?.permissions?.visible ??
          true,
        editability: {
          editable: false,
          reason: parent.reason,
        },
      };
    }
    return this.getPermissionOverriddenStatus(
      this.uiStatus().deviceConfig.subSections?.permissions,
    );
  });

  readonly wifiUiStatus = computed(() => {
    const parent = this.getDeviceConfigSectionEditability();
    if (!parent.editable) {
      return {
        visible:
          this.uiStatus().deviceConfig.subSections?.wifi?.visible ?? true,
        editability: {
          editable: false,
          reason: parent.reason,
        },
      };
    }
    return this.getPermissionOverriddenStatus(
      this.uiStatus().deviceConfig.subSections?.wifi,
    );
  });

  readonly dimensionsUiStatus = computed(() => {
    const parent = this.getDeviceConfigSectionEditability();
    if (!parent.editable) {
      return {
        sectionStatus: {
          visible:
            this.uiStatus().deviceConfig.subSections?.dimensions?.visible ??
            true,
          editability: {
            editable: false,
            reason: parent.reason,
          },
        },
      };
    }
    return {
      sectionStatus: this.getPermissionOverriddenStatus(
        this.uiStatus().deviceConfig.subSections?.dimensions,
      ),
    };
  });

  readonly settingsUiStatus = computed(() => {
    const parent = this.getDeviceConfigSectionEditability();
    if (!parent.editable) {
      return {
        visible:
          this.uiStatus().deviceConfig.subSections?.settings?.visible ?? true,
        editability: {
          editable: false,
          reason: parent.reason,
        },
      };
    }
    return this.getPermissionOverriddenStatus(
      this.uiStatus().deviceConfig.subSections?.settings,
    );
  });

  readonly deviceDiscoveryUiStatus = computed(() => {
    return {
      sectionStatus: this.getPermissionOverriddenStatus(
        this.uiStatus().deviceDiscovery,
      ),
    };
  });

  readonly hostPropertiesUiStatus = computed(() => {
    const currentStatus = this.uiStatus().hostProperties;
    const sectionStatus = this.getPermissionOverriddenStatus(
      currentStatus?.sectionStatus,
    );
    return {
      sectionStatus,
      itemEditabilityOverrides: currentStatus?.itemEditabilityOverrides,
      unlockable: currentStatus?.unlockable,
      unlockPrompt: currentStatus?.unlockPrompt,
    };
  });

  readonly deviceConfig = signal<DeviceConfig>({
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
  });

  readonly hostConfig = signal<HostConfig>({
    permissions: {
      hostAdmins: [],
    },
    deviceConfigMode: 'PER_DEVICE',
    deviceConfig: {
      permissions: {owners: [], executors: []},
      wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
      dimensions: {supported: [], required: []},
      settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
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
  });

  updateHostPermissions(hostAdmins: string[]) {
    this.hostConfig.update((c) => ({
      ...c,
      permissions: {hostAdmins},
    }));
  }

  updateDeviceConfigMode(deviceConfigMode: string) {
    this.hostConfig.update((c) => ({
      ...c,
      deviceConfigMode: deviceConfigMode as HostConfig['deviceConfigMode'],
    }));
  }

  updateDevicePermissions(permissions: DeviceConfig['permissions']) {
    this.deviceConfig.update((c) => ({...c, permissions}));
  }

  updateDeviceWifi(wifi: unknown) {
    this.deviceConfig.update((c) => ({
      ...c,
      wifi: wifi as DeviceConfig['wifi'],
    }));
  }

  updateDeviceDimensions(dimensions: DeviceConfig['dimensions']) {
    this.deviceConfig.update((c) => ({...c, dimensions}));
  }

  updateDeviceSettings(settings: DeviceConfig['settings']) {
    this.deviceConfig.update((c) => ({...c, settings}));
  }

  updateDeviceDiscovery(deviceDiscovery: HostConfig['deviceDiscovery']) {
    this.hostConfig.update((c) => ({...c, deviceDiscovery}));
  }

  updateHostProperties(hostProperties: HostConfig['hostProperties']) {
    this.hostConfig.update((c) => ({...c, hostProperties}));
  }

  private originalDeviceConfig: DeviceConfig = this.deviceConfig();
  private originalHostConfig: HostConfig = this.hostConfig();

  tooltipText = signal<string>('');

  // Used for dimensions step duplicate check.
  hasError = false;

  saving = signal<boolean>(false);
  hasPermission = signal<boolean>(false);

  ngOnInit() {
    console.log(
      'HostSettings.ngOnInit:',
      this.testId,
      'dialogData present:',
      !!this.dialogData,
    );
    if (this.dialogData) {
      console.log(
        'HostSettings.ngOnInit:',
        this.testId,
        'dialogData.hostName =',
        this.dialogData.hostName,
      );
      this.hostName = this.dialogData.hostName || this.hostName;
      if (this.dialogData.config) {
        this.config = this.dialogData.config;
      }
    }
    console.log(
      'HostSettings.ngOnInit:',
      this.testId,
      'hostName set to:',
      this.hostName,
    );

    this.uiStatus.set(this.hostConfigStateService.getUiStatus(this.hostName));

    // If there is no visible section, set the active section to default.
    if (this.visibleSections().length === 0) {
      this.activeSection.set('default');
      return;
    }

    this.initializeVisibleSections();
    this.initializeData();

    // Set the tooltip text of device config based on the device config mode.
    const tooltip =
      this.hostConfig().deviceConfigMode === 'PER_DEVICE'
        ? `The settings on this page define the default configuration that will be automatically applied to any new device connected to this host.`
        : `The settings on this page are shared by all devices on this host, as 'Shared Configuration' is selected in the Config Mode settings.`;
    this.tooltipText.set(tooltip);
  }

  setActiveSection(event: Event, section: string) {
    event.preventDefault();

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
      unsaveDialogRef
        .afterClosed()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((result) => {
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
  }

  initializeData() {
    if (this.config) {
      this.deviceConfig.set(
        objectUtils.deepCopy({
          ...this.config.deviceConfig!,
          permissions: {
            owners: this.config.permissions.hostAdmins,
            executors: this.config.deviceConfig?.permissions?.executors || [],
          },
        }) as DeviceConfig,
      );
      this.hostConfig.set(objectUtils.deepCopy(this.config) as HostConfig);
    }

    this.originalDeviceConfig = objectUtils.deepCopy(
      this.deviceConfig(),
    ) as DeviceConfig;
    this.originalHostConfig = objectUtils.deepCopy(
      this.hostConfig(),
    ) as HostConfig;
  }

  onHostPermissionsChange(hostAdmins: string[]) {
    this.updateHostPermissions(hostAdmins);
    this.updateDevicePermissions({
      owners: [...hostAdmins],
      executors: this.deviceConfig().permissions?.executors || [],
    });
  }

  isCategoryDirty(category: string) {
    if (category === 'host-permissions') {
      return (
        JSON.stringify(this.originalHostConfig.permissions) !==
        JSON.stringify(this.hostConfig().permissions)
      );
    }

    if (category === 'config-mode') {
      return (
        this.originalHostConfig.deviceConfigMode !==
        this.hostConfig().deviceConfigMode
      );
    }

    // Device Configs
    if (category === 'permissions') {
      return (
        JSON.stringify(this.originalDeviceConfig.permissions) !==
        JSON.stringify(this.deviceConfig().permissions)
      );
    }
    if (category === 'wifi') {
      return (
        JSON.stringify(this.originalDeviceConfig.wifi) !==
        JSON.stringify(this.deviceConfig().wifi)
      );
    }
    if (category === 'dimensions') {
      return (
        JSON.stringify(this.originalDeviceConfig.dimensions) !==
        JSON.stringify(this.deviceConfig().dimensions)
      );
    }
    if (category === 'stability') {
      return (
        JSON.stringify(this.originalDeviceConfig.settings) !==
        JSON.stringify(this.deviceConfig().settings)
      );
    }

    if (category === 'device-discovery') {
      return (
        JSON.stringify(this.originalHostConfig.deviceDiscovery) !==
        JSON.stringify(this.hostConfig().deviceDiscovery)
      );
    }

    if (category === 'host-properties') {
      return (
        JSON.stringify(this.originalHostConfig.hostProperties) !==
        JSON.stringify(this.hostConfig().hostProperties)
      );
    }

    return false;
  }

  handlePermissionChange(result: {hasPermission: boolean}) {
    this.hasPermission.set(result.hasPermission);
  }

  resetConfig() {
    this.deviceConfig.set(
      objectUtils.deepCopy(this.originalDeviceConfig) as DeviceConfig,
    );
    this.hostConfig.set(
      objectUtils.deepCopy(this.originalHostConfig) as HostConfig,
    );
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
        return;
      }
      if (result === 'primary') {
        this.save(true);
      }
    });
  }

  success(isSelfLockout = false) {
    const dialogData = {
      title: 'Configuration Saved',
      content: 'Your configuration has been saved successfully. ',
      type: 'success',
      primaryButtonLabel: 'OK',
    };

    const successDialogRef = this.dialog.open(ConfirmDialog, {
      data: dialogData,
      disableClose: true,
    });

    if (isSelfLockout) {
      successDialogRef.afterClosed().subscribe(() => {
        this.dialogRef.close(true);
      });
    }
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

  save(selfLockout = false, forceSave = false) {
    const section = this.activeSection();

    if (
      section === 'dimensions' &&
      !forceSave &&
      hasEmptyDimensions(this.deviceConfig().dimensions)
    ) {
      this.saveInterceptors.promptEmptyData('dimensions', () => {
        this.updateDeviceDimensions(
          clearEmptyDimensions(this.deviceConfig().dimensions),
        );
        if (!this.isCategoryDirty(section)) {
          this.hostConfig.update((c) => ({
            ...c,
            deviceConfig: this.deviceConfig(),
          }));
          this.originalHostConfig = objectUtils.deepCopy(
            this.hostConfig(),
          ) as HostConfig;
          this.originalDeviceConfig = objectUtils.deepCopy(
            this.deviceConfig(),
          ) as DeviceConfig;
          return;
        }
        this.save(selfLockout, true);
      });
      return;
    }

    if (
      section === 'host-properties' &&
      !forceSave &&
      hasEmptyProperties(this.hostConfig().hostProperties)
    ) {
      this.saveInterceptors.promptEmptyData('properties', () => {
        this.updateHostProperties(
          clearEmptyProperties(this.hostConfig().hostProperties),
        );
        if (!this.isCategoryDirty(section)) {
          this.originalHostConfig = objectUtils.deepCopy(
            this.hostConfig(),
          ) as HostConfig;
          return;
        }
        this.save(selfLockout, true);
      });
      return;
    }
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

    if (
      this.hostConfig().permissions?.hostAdmins &&
      this.deviceConfig().permissions
    ) {
      this.updateDevicePermissions({
        owners: [...this.hostConfig().permissions.hostAdmins],
        executors: this.deviceConfig().permissions?.executors || [],
      });
    }
    const deviceConfig = {...this.deviceConfig()};
    if (deviceConfig.wifi && deviceConfig.wifi.type === 'none') {
      deviceConfig.wifi = undefined;
    }
    const requestConfig: HostConfig = {
      ...this.hostConfig(),
      deviceConfig,
    };
    const request: UpdateHostConfigRequest = {
      hostName: this.hostName,
      config: requestConfig,
      scope: {
        section: requestSectionMap[section],
        deviceConfigSection: deviceConfigSectionMap[section],
      },
      options: {overrideSelfLockout: selfLockout},
    };

    this.saving.set(true);
    this.configService
      .updateHostConfig(request)
      .pipe(
        finalize(() => {
          this.saving.set(false);
        }),
      )
      .subscribe((result) => {
        if (!result.success) {
          this.error(result.error?.code);
          return;
        }

        this.reloadConfig(true, selfLockout);
      });
  }

  discard() {
    this.resetConfig();
  }

  unlockHostProperties() {
    const unlockPrompt = this.uiStatus().hostProperties.unlockPrompt;
    if (!unlockPrompt) return;

    const dialogData = {
      title: 'Unlock Host Properties',
      content: unlockPrompt,
      type: 'warning',
      primaryButtonLabel: 'Unlock',
      secondaryButtonLabel: 'Cancel',
      onConfirm: () => {
        return this.configService.unlockHostProperties(this.hostName).pipe(
          concatMap((response) => {
            if (response.success) {
              return of(undefined);
            } else {
              this.snackBar.showError(
                response.error?.message || 'Failed to unlock host properties.',
              );
              return throwError(() => new Error(response.error?.message));
            }
          }),
        );
      },
    };

    const confirmDialogRef = this.dialog.open(ConfirmDialog, {
      data: dialogData,
      disableClose: true,
    });

    confirmDialogRef
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        if (result === 'primary') {
          this.reloadConfig();
        }
      });
  }

  private reloadConfig(showSuccess = false, selfLockout = false) {
    this.configService.getHostConfig(this.hostName).subscribe({
      next: (result) => {
        if (result) {
          if (result.uiStatus) {
            this.uiStatus.set(result.uiStatus);
            this.hostConfigStateService.setUiStatus(
              this.hostName,
              result.uiStatus,
            );
          }
          if (result.hostConfig) {
            this.config = result.hostConfig;
            this.initializeData();
          }
        }
        if (showSuccess) {
          this.success(selfLockout);
        }
      },
      error: (err) => {
        this.snackBar.showError('Failed to reload configuration.');
        if (showSuccess) {
          this.success(selfLockout);
        }
      },
    });
  }
}
