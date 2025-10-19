import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnInit,
  TemplateRef,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatDialog, MatDialogModule} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {delay} from 'rxjs/operators';
import type {DeviceConfig} from '../../../../../core/models/device_config_models';
import {
  ConfigSection,
  UpdateDeviceConfigRequest,
} from '../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {ConfirmDialog} from '../../../../../shared/components/common_config/confirm_dialog/confirm_dialog';
import {Dialog} from '../../../../../shared/components/common_config/dialog/dialog';
import {Footer} from '../../../../../shared/components/common_config/footer/footer';
import {objectUtils} from '../../../../../shared/utils/object_utils';
import {EmptyConfig} from '../empty_config/empty_config';
import {Dimensions} from '../steps/dimensions/dimensions';
import {Permissions} from '../steps/permissions/permissions';
import {Wifi} from '../steps/wifi/wifi';

/**
 * Component for displaying the device configuration settings dialog.
 *
 * It is used to configure the device's dimensions, owners, and other properties.
 */
@Component({
  selector: 'app-settings',
  standalone: true,
  templateUrl: './settings.ng.html',
  styleUrl: './settings.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    Dialog,
    MatDialogModule,
    Footer,
    Permissions,
    Wifi,
    Dimensions,
    MatButtonModule,
    MatMenuModule,
    MatIconModule,
    EmptyConfig,
    MatProgressSpinnerModule,
    ConfirmDialog,
  ],
})
export class Settings implements OnInit {
  private readonly dialog = inject(MatDialog);
  private readonly configService = inject(CONFIG_SERVICE);

  @Input() deviceId = '';
  @Input() config: DeviceConfig = {
    permissions: {
      owners: [],
      executors: [],
    },
    wifi: {type: 'none', ssid: 'GoogleGuest', psk: '', scanSsid: false},
    dimensions: {supported: [], required: []},
    settings: {
      maxConsecutiveFail: 5,
      maxConsecutiveTest: 10000,
    },
  };

  readonly navList = [
    {
      id: 'permissions',
      icon: 'security',
      label: 'Permissions',
    },
    {
      id: 'wifi',
      icon: 'wifi',
      label: 'Wi-Fi',
    },
    {
      id: 'dimensions',
      icon: 'category',
      label: 'Dimensions',
    },
    {
      id: 'stability',
      icon: 'autorenew',
      label: 'Stability & Reboot',
    },
  ];

  activeSection = signal<string>('permissions');

  private originalConfig: DeviceConfig = this.config;
  newConfig: DeviceConfig = this.config;

  saving = signal<boolean>(false);

  ngOnInit() {
    console.log('ngOnInit');
    this.originalConfig = objectUtils.deepCopy(this.config) as DeviceConfig;
    this.newConfig = objectUtils.deepCopy(this.config) as DeviceConfig;

    this.isCategoryDirty(this.activeSection());
  }

  setActiveSection(event: Event, section: string) {
    console.log('setActiveSection', section);
    event.preventDefault();

    if (this.isCategoryDirty(this.activeSection())) {
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
          this.newConfig = objectUtils.deepCopy(
            this.originalConfig,
          ) as DeviceConfig;
          this.activeSection.set(section);
        }

        return;
      });
    } else {
      this.activeSection.set(section);
    }
  }

  reset() {
    this.dialog.closeAll();
    const dialogRef = this.dialog.open(EmptyConfig, {
      data: {
        deviceId: this.deviceId,
        title:
          'You are about to clear the existing configuration for this device. Your current settings will be discarded. Please choose how you want to proceed.',
      },
    });

    dialogRef.afterClosed().subscribe((permission: string | null) => {});
  }

  onDimensionsChange(dimensions: any) {
    this.newConfig.dimensions = dimensions;
  }

  isCategoryDirty(category: string) {
    let newDimensions = {
      supported: this.newConfig.dimensions.supported,
      required: this.newConfig.dimensions.required,
    };

    if (category === 'dimensions') {
      const supportDimensions = this.newConfig.dimensions.supported.filter(
        (item) => !(!item.name && !item.value),
      );
      const requiredDimensions = this.newConfig.dimensions.required.filter(
        (item) => !(!item.name && !item.value),
      );

      newDimensions = {
        supported: supportDimensions,
        required: requiredDimensions,
      };
    }

    const originalMap: Record<string, unknown> = {
      'permissions': this.originalConfig.permissions,
      'wifi': this.originalConfig.wifi,
      'dimensions': this.originalConfig.dimensions,
      'stability': this.originalConfig.settings,
    };
    const newMap: Record<string, unknown> = {
      'permissions': this.newConfig.permissions,
      'wifi': this.newConfig.wifi,
      'dimensions': newDimensions,
      'stability': this.newConfig.settings,
    };

    return (
      JSON.stringify(originalMap[category]) !== JSON.stringify(newMap[category])
    );
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

    this.dialog.open(ConfirmDialog, {
      data: dialogData,
      disableClose: true,
    });
  }

  save() {
    const section = this.activeSection();
    const requestSectionMap: Record<string, ConfigSection> = {
      'permissions': ConfigSection.PERMISSIONS,
      'wifi': ConfigSection.WIFI,
      'dimensions': ConfigSection.DIMENSIONS,
      'stability': ConfigSection.STABILITY,
    };

    const request: UpdateDeviceConfigRequest = {
      deviceId: this.deviceId,
      config: this.newConfig,
      section: requestSectionMap[section],
    };

    this.saving.set(true);
    this.configService
      .updateDeviceConfig(request)
      .pipe(delay(1000))
      .subscribe((result) => {
        this.saving.set(false);

        if (!result.success) {
          this.error(result.error?.code);
          return;
        }

        this.originalConfig = objectUtils.deepCopy(
          this.newConfig,
        ) as DeviceConfig;

        this.success();
      });
  }

  discard() {
    this.newConfig = objectUtils.deepCopy(this.originalConfig) as DeviceConfig;
  }
}
