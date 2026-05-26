import {TestBed} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {
  CONFIG_SERVICE,
  ConfigService,
} from '../../../../../core/services/config/config_service';
import {HostConfigStateService} from '../../../../../core/services/config/host_config_state_service';

import {HostSettings} from './host_settings';

describe('HostSettings Component', () => {
  let mockConfigService: jasmine.SpyObj<ConfigService>;

  beforeEach(async () => {
    mockConfigService = jasmine.createSpyObj<ConfigService>('ConfigService', [
      'getDeviceConfig',
      'checkDeviceWritePermission',
      'updateDeviceConfig',
      'getRecommendedWifi',
      'getHostDefaultDeviceConfig',
      'getHostConfig',
      'checkHostWritePermission',
      'updateHostConfig',
    ]);

    mockConfigService.updateHostConfig.and.returnValue(of({success: true}));
    mockConfigService.checkHostWritePermission.and.returnValue(
      of({hasPermission: true}),
    );
    mockConfigService.checkDeviceWritePermission.and.returnValue(
      of({hasPermission: true}),
    );

    await TestBed.configureTestingModule({
      imports: [
        HostSettings,
        NoopAnimationsModule, // This makes test faster and more stable.
        MatDialogModule,
        MatTestDialogOpenerModule,
      ],
      providers: [
        provideRouter([]),
        {
          provide: CONFIG_SERVICE,
          useValue: mockConfigService,
        },
        {
          provide: HostConfigStateService,
          useValue: {
            getUiStatus: () => ({
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
            }),
          },
        },
      ],
    }).compileComponents();
  });

  it('should be created', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: {
          hostName: 'test-host',
          config: {
            permissions: {hostAdmins: []},
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
          },
        },
      }),
    );
    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    expect(comp).toBeTruthy();

    const dialogElement = document.querySelector('mat-dialog-container');
    expect(dialogElement).toBeTruthy();
    expect(dialogElement!.querySelector('.nav-bar')).toBeTruthy();
  });

  it('should return correct UI status when has permission', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: {hostName: 'test-host'},
      }),
    );
    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    comp.hasPermission.set(true);

    expect(comp.hostPermissionsUiStatus.hostAdmins.editability?.editable).toBe(
      true,
    );
    expect(comp.configModeUiStatus.editability?.editable).toBe(true);
    expect(comp.dimensionsUiStatus.sectionStatus.editability?.editable).toBe(
      true,
    );
    expect(
      comp.deviceDiscoveryUiStatus.sectionStatus.editability?.editable,
    ).toBe(true);
    expect(comp.permissionsUiStatus.editability?.editable).toBe(true);
    expect(comp.wifiUiStatus.editability?.editable).toBe(true);
    expect(comp.settingsUiStatus.editability?.editable).toBe(true);
    expect(
      comp.hostPropertiesUiStatus.sectionStatus.editability?.editable,
    ).toBe(true);
  });

  it('should return correct UI status when does not have permission', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: {hostName: 'test-host'},
      }),
    );
    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    comp.hasPermission.set(false);

    expect(comp.hostPermissionsUiStatus.hostAdmins.editability?.editable).toBe(
      false,
    );
    expect(comp.hostPermissionsUiStatus.hostAdmins.visible).toBe(true);

    expect(comp.configModeUiStatus.editability?.editable).toBe(false);
    expect(comp.configModeUiStatus.visible).toBe(true);

    expect(comp.dimensionsUiStatus.sectionStatus.editability?.editable).toBe(
      false,
    );
    expect(comp.dimensionsUiStatus.sectionStatus.visible).toBe(true);

    expect(
      comp.deviceDiscoveryUiStatus.sectionStatus.editability?.editable,
    ).toBe(false);
    expect(comp.deviceDiscoveryUiStatus.sectionStatus.visible).toBe(true);

    expect(comp.permissionsUiStatus.editability?.editable).toBe(false);
    expect(comp.permissionsUiStatus.visible).toBe(true);

    expect(comp.wifiUiStatus.editability?.editable).toBe(false);
    expect(comp.wifiUiStatus.visible).toBe(true);

    expect(comp.settingsUiStatus.editability?.editable).toBe(false);
    expect(comp.settingsUiStatus.visible).toBe(true);

    expect(
      comp.hostPropertiesUiStatus.sectionStatus.editability?.editable,
    ).toBe(false);
    expect(comp.hostPropertiesUiStatus.sectionStatus.visible).toBe(true);
  });

  it('should filter navList based on visibility', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: {hostName: 'test-host'},
      }),
    );
    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;

    // Default mock setup has all visible
    expect(comp.visibleSections().length).toBe(4); // host-permissions, device-config, device-discovery, host-properties

    comp.uiStatus.hostAdmins.visible = false;
    expect(
      comp.visibleSections().some((s) => s.id === 'host-permissions'),
    ).toBe(false);
  });

  it('should detect dirty categories', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: {
          hostName: 'test-host',
          config: {
            permissions: {hostAdmins: []},
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
          },
        },
      }),
    );
    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;

    expect(comp.isCategoryDirty('config-mode')).toBe(false);

    comp.hostConfig = {
      ...comp.hostConfig!,
      deviceConfigMode: 'SHARED',
    };

    expect(comp.isCategoryDirty('config-mode')).toBe(true);
  });

  it('should call updateHostConfig on save', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: {hostName: 'test-host'},
      }),
    );
    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    comp.activeSection.set('host-permissions');

    comp.save();

    expect(mockConfigService.updateHostConfig).toHaveBeenCalled();
  });
});
