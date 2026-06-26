import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  Input,
  input,
  OnInit,
  signal,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
  MatDialogState,
} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {finalize} from 'rxjs/operators';

import {
  DEFAULT_DEVICE_CONFIG,
  DEFAULT_DEVICE_CONFIG_UI_STATUS,
} from '../../../../../core/constants/device_config_constants';
import {
  ConfigSection,
  UpdateDeviceConfigRequest,
  type CheckDeviceWritePermissionResult,
  type DeviceConfig,
  type DeviceConfigUiStatus,
} from '../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {DeviceConfigStateService} from '../../../../../core/services/config/device_config_state_service';
import {
  clearEmptyDimensions,
  hasEmptyDimensions,
  normalizeDeviceConfig,
  normalizeDeviceConfigUiStatus,
} from '../../../../../core/utils/device_config_utils';
import {Dialog} from '../../../../../shared/components/config_common/dialog/dialog';
import {Footer} from '../../../../../shared/components/config_common/footer/footer';
import {ConfirmDialog} from '../../../../../shared/components/confirm_dialog/confirm_dialog';
import {useConfigDialogActions} from '../../../../../shared/composables/config_dialog_actions';
import {useSaveInterceptors} from '../../../../../shared/composables/save_interceptors';
import {objectUtils} from '../../../../../shared/utils/object_utils';
import {Dimensions} from '../steps/dimensions/dimensions';
import {Permissions} from '../steps/permissions/permissions';
import {Stability} from '../steps/stability/stability';
import {Wifi} from '../steps/wifi/wifi';

/**
 * Component for displaying the device configuration settings dialog.
 *
 * It is used to configure the device's dimensions, owners, and other
 * properties.
 */
@Component({
  selector: 'app-device-settings',
  standalone: true,
  templateUrl: './device_settings.ng.html',
  styleUrl: './device_settings.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatProgressSpinnerModule,
    MatButtonModule,
    MatMenuModule,
    MatIconModule,
    Dialog,
    Footer,
    Permissions,
    Wifi,
    Dimensions,
    Stability,
  ],
})
export class DeviceSettings implements OnInit {
  readonly ConfigSection = ConfigSection;

  private readonly dialog = inject(MatDialog); // to open confirm dialog
  private readonly dialogRef = inject(MatDialogRef<DeviceSettings>);
  private readonly destroyRef = inject(DestroyRef);

  private isSavingSelfLockout = false;
  readonly dialogActions = useConfigDialogActions({
    onCancelSelfLockout: () => {
      this.activeSection.set(ConfigSection.PERMISSIONS);
    },
    onSubmitOverride: () => {
      this.save(true);
    },
    onSuccessClose: () => {
      if (this.isSavingSelfLockout) {
        this.closeDialogIfOpen(true);
      }
    },
  });

  private readonly saveInterceptors = useSaveInterceptors();
  private readonly dialogData = inject(MAT_DIALOG_DATA, {optional: true}) as {
    deviceId: string;
    config: DeviceConfig;
    universe?: string;
  } | null;

  private readonly configService = inject(CONFIG_SERVICE);
  private readonly deviceConfigStateService = inject(DeviceConfigStateService);

  readonly deviceIdInput = input<string>('', {alias: 'deviceId'});
  readonly universeInput = input<string>('', {alias: 'universe'});

  readonly deviceId = computed(
    () => this.dialogData?.deviceId || this.deviceIdInput(),
  );
  readonly universe = computed(
    () => this.dialogData?.universe || this.universeInput(),
  );

  configInternal: DeviceConfig = DEFAULT_DEVICE_CONFIG;

  /**
   * Input for the device configuration.
   * If any of the main sections are missing, they are filled with defaults.
   */
  @Input()
  set config(value: Partial<DeviceConfig>) {
    this.configInternal = normalizeDeviceConfig(value as DeviceConfig);
  }

  get config(): DeviceConfig {
    return this.configInternal;
  }

  uiStatusInternal = signal<DeviceConfigUiStatus>(
    DEFAULT_DEVICE_CONFIG_UI_STATUS,
  );

  /**
   * Input for configuring the UI visibility/editability status of each section.
   * Intercepts and normalizes raw values, and redirects the active section to the
   * first visible item if the currently active section becomes hidden page-wise.
   *
   * Note: The getter and setter must remain physically adjacent in the class block.
   * If separated by a class field, TypeScript and esbuild (under useDefineForClassFields)
   * can fail to merge the accessor descriptors, resulting in 'uiStatus' being treated
   * as a read-only property in the compiled open-source environment.
   */
  @Input()
  set uiStatus(value: Partial<DeviceConfigUiStatus> | undefined) {
    this.uiStatusInternal.set(normalizeDeviceConfigUiStatus(value));
    this.adjustActiveSection();
  }

  get uiStatus(): DeviceConfigUiStatus {
    return this.computedUiStatus();
  }

  private readonly computedUiStatus = computed<DeviceConfigUiStatus>(() => {
    const hasPerm = this.hasPermission() ?? false;
    const internal = this.uiStatusInternal();

    if (this.saving()) {
      const savingStatus = {
        editable: false,
        reason: 'Saving in progress...',
      };
      return {
        permissions: {
          visible: internal.permissions.visible,
          editability: savingStatus,
        },
        wifi: {
          visible: internal.wifi.visible,
          editability: savingStatus,
        },
        dimensions: {
          visible: internal.dimensions.visible,
          editability: savingStatus,
        },
        settings: {
          visible: internal.settings.visible,
          editability: savingStatus,
        },
      };
    }

    return {
      permissions: {
        visible: internal.permissions.visible,
        editability: {editable: hasPerm},
      },
      wifi: {
        visible: internal.wifi.visible,
        editability: {editable: hasPerm},
      },
      dimensions: {
        visible: internal.dimensions.visible,
        editability: {editable: hasPerm},
      },
      settings: {
        visible: internal.settings.visible,
        editability: {editable: hasPerm},
      },
    };
  });

  private readonly allNavItems = [
    {
      id: ConfigSection.PERMISSIONS,
      icon: 'security',
      label: 'Permissions',
    },
    {
      id: ConfigSection.WIFI,
      icon: 'wifi',
      label: 'Wi-Fi',
    },
    {
      id: ConfigSection.DIMENSIONS,
      icon: 'category',
      label: 'Dimensions',
    },
    {
      id: ConfigSection.STABILITY,
      icon: 'autorenew',
      label: 'Stability & Reboot',
    },
  ];

  get navList() {
    return this.allNavItems.filter((nav) => {
      switch (nav.id) {
        case ConfigSection.PERMISSIONS:
          return this.uiStatus.permissions.visible;
        case ConfigSection.WIFI:
          return this.uiStatus.wifi.visible;
        case ConfigSection.DIMENSIONS:
          return this.uiStatus.dimensions.visible;
        case ConfigSection.STABILITY:
          return this.uiStatus.settings.visible;
        default:
          return true;
      }
    });
  }

  activeSection = signal<ConfigSection>(ConfigSection.PERMISSIONS);

  private adjustActiveSection() {
    const visibleSections = this.navList;
    if (
      visibleSections.length > 0 &&
      !visibleSections.find((nav) => nav.id === this.activeSection())
    ) {
      this.activeSection.set(visibleSections[0].id);
    }
  }

  private originalConfig: DeviceConfig = this.config;
  readonly newConfig = signal<DeviceConfig>(DEFAULT_DEVICE_CONFIG);

  updatePermissions(permissions: DeviceConfig['permissions']) {
    this.newConfig.update((c) => ({...c, permissions}));
  }

  updateWifi(wifi: unknown) {
    this.newConfig.update((c) => ({...c, wifi: wifi as DeviceConfig['wifi']}));
  }

  updateDimensions(dimensions: DeviceConfig['dimensions']) {
    this.newConfig.update((c) => ({...c, dimensions}));
  }

  updateSettings(settings: DeviceConfig['settings']) {
    this.newConfig.update((c) => ({...c, settings}));
  }

  // used for dimensions step duplicate check
  hasError = false;

  saving = signal<boolean>(false);
  hasPermission = signal<boolean>(false);

  ngOnInit() {
    if (this.dialogData && this.dialogData.config) {
      this.config = this.dialogData.config;
    }

    this.uiStatusInternal.set(
      this.deviceConfigStateService.getUiStatus(this.deviceId()),
    );
    this.adjustActiveSection();

    this.originalConfig = objectUtils.deepCopy(this.config) as DeviceConfig;
    this.newConfig.set(objectUtils.deepCopy(this.config) as DeviceConfig);

    this.isCategoryDirty(this.activeSection());
  }

  setActiveSection(event: Event, section: string | ConfigSection) {
    const targetSection = section as ConfigSection;
    event.preventDefault();

    if (this.saving()) {
      return;
    }

    if (
      targetSection !== this.activeSection() &&
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
            this.newConfig.set(
              objectUtils.deepCopy(this.originalConfig) as DeviceConfig,
            );
            this.activeSection.set(targetSection);
          }

          return;
        });
    } else {
      this.activeSection.set(targetSection);
    }
  }

  private closeDialogIfOpen(result?: unknown) {
    if (this.dialogRef && this.dialogRef.getState() === MatDialogState.OPEN) {
      this.dialogRef.close(result);
    }
  }

  reset() {
    this.closeDialogIfOpen({
      action: 'reset',
      deviceId: this.deviceId(),
      universe: this.universe(),
    });
  }

  isCategoryDirty(category: ConfigSection) {
    const newDimensions = {
      supported: this.newConfig().dimensions?.supported,
      required: this.newConfig().dimensions?.required,
    };

    const originalMap: Partial<Record<ConfigSection, unknown>> = {
      [ConfigSection.PERMISSIONS]: this.originalConfig.permissions,
      [ConfigSection.WIFI]: this.originalConfig.wifi,
      [ConfigSection.DIMENSIONS]: this.originalConfig.dimensions,
      [ConfigSection.STABILITY]: this.originalConfig.settings,
    };
    const newMap: Partial<Record<ConfigSection, unknown>> = {
      [ConfigSection.PERMISSIONS]: this.newConfig().permissions,
      [ConfigSection.WIFI]: this.newConfig().wifi,
      [ConfigSection.DIMENSIONS]: newDimensions,
      [ConfigSection.STABILITY]: this.newConfig().settings,
    };

    return (
      JSON.stringify(originalMap[category]) !== JSON.stringify(newMap[category])
    );
  }

  handlePermissionChange(result: CheckDeviceWritePermissionResult) {
    this.hasPermission.set(result.hasPermission);
  }

  save(selfLockout = false, forceSave = false) {
    const section = this.activeSection();

    if (
      section === ConfigSection.DIMENSIONS &&
      !forceSave &&
      hasEmptyDimensions(this.newConfig().dimensions)
    ) {
      this.saveInterceptors.promptEmptyData('dimensions', () => {
        const cleared = clearEmptyDimensions(this.newConfig().dimensions);
        this.updateDimensions(cleared);
        if (!this.isCategoryDirty(section)) {
          this.originalConfig = objectUtils.deepCopy(
            this.newConfig(),
          ) as DeviceConfig;
          return;
        }
        this.save(selfLockout, true);
      });
      return;
    }

    this.saving.set(true);
    this.isSavingSelfLockout = selfLockout;

    const deviceConfig = {...this.newConfig()};
    if (deviceConfig.wifi && deviceConfig.wifi.type === 'none') {
      deviceConfig.wifi = undefined;
    }

    const request: UpdateDeviceConfigRequest = {
      id: this.deviceId(),
      config: deviceConfig,
      section,
      options: {overrideSelfLockout: selfLockout},
      universe: this.universe(),
    };

    this.configService
      .updateDeviceConfig(request)
      .pipe(
        finalize(() => {
          this.saving.set(false);
        }),
      )
      .subscribe((result) => {
        if (!result.success) {
          this.dialogActions.error(result.error?.code);
          return;
        }

        this.originalConfig = objectUtils.deepCopy(
          this.newConfig(),
        ) as DeviceConfig;

        this.dialogActions.success();
      });
  }

  discard() {
    this.newConfig.set(
      objectUtils.deepCopy(this.originalConfig) as DeviceConfig,
    );
  }
}
