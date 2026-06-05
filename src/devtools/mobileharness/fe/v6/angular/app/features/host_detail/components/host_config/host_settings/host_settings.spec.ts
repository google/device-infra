import {ApplicationRef} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogRef,
} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of, Subject} from 'rxjs';

import {
  type StabilitySettings,
  type WifiConfig,
} from '../../../../../core/models/device_config_models';
import {
  type HostConfig,
  UpdateHostConfigResult,
} from '../../../../../core/models/host_config_models';
import {
  CONFIG_SERVICE,
  ConfigService,
} from '../../../../../core/services/config/config_service';
import {HostConfigStateService} from '../../../../../core/services/config/host_config_state_service';
import {ConfirmDialog} from '../../../../../shared/components/confirm_dialog/confirm_dialog';

import {HostSettings} from './host_settings';

describe('HostSettings Component', () => {
  let mockConfigService: jasmine.SpyObj<ConfigService>;
  let dialogRef: MatDialogRef<HostSettings>;
  const dialogData: {
    hostName?: string;
    config?: HostConfig;
  } = {};
  let comp: HostSettings;
  let mockHostConfigStateService: jasmine.SpyObj<HostConfigStateService>;

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
    mockConfigService.getRecommendedWifi.and.returnValue(of([]));

    mockHostConfigStateService = jasmine.createSpyObj<HostConfigStateService>(
      'HostConfigStateService',
      ['getUiStatus', 'setUiStatus', 'clear'],
    );
    mockHostConfigStateService.getUiStatus.and.returnValue({
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

    dialogData.hostName = undefined;
    dialogData.config = undefined;

    await TestBed.configureTestingModule({
      imports: [
        HostSettings,
        NoopAnimationsModule, // This makes test faster and more stable.
        MatTestDialogOpenerModule,
      ],
      providers: [
        provideRouter([]),
        {
          provide: CONFIG_SERVICE,
          useValue: mockConfigService,
        },
        {
          provide: MAT_DIALOG_DATA,
          useFactory: () => dialogData,
        },
        {
          provide: HostConfigStateService,
          useValue: mockHostConfigStateService,
        },
      ],
    }).compileComponents();
  });

  function setupDialogData(
    data: {hostName?: string; config?: Partial<HostConfig>} = {},
  ) {
    dialogData.hostName = data.hostName ?? 'test-host';
    const defaultConfig: HostConfig = {
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
    };
    dialogData.config = {
      ...defaultConfig,
      ...data.config,
      deviceConfig: {
        ...defaultConfig.deviceConfig,
        ...data.config?.deviceConfig,
      },
    };
  }

  it('should be created', () => {
    setupDialogData();
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();
    expect(comp).toBeTruthy();

    expect(document.querySelector('.nav-bar')).toBeTruthy();
  });

  it('should return correct UI status when has permission', () => {
    setupDialogData();
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();
    comp.hasPermission.set(true);

    expect(
      comp.hostPermissionsUiStatus().hostAdmins.editability?.editable,
    ).toBe(true);
    expect(comp.configModeUiStatus().editability?.editable).toBe(true);
    expect(comp.dimensionsUiStatus().sectionStatus.editability?.editable).toBe(
      true,
    );
    expect(
      comp.deviceDiscoveryUiStatus().sectionStatus.editability?.editable,
    ).toBe(true);
    expect(comp.permissionsUiStatus().editability?.editable).toBe(true);
    expect(comp.wifiUiStatus().editability?.editable).toBe(true);
    expect(comp.settingsUiStatus().editability?.editable).toBe(true);
    expect(
      comp.hostPropertiesUiStatus().sectionStatus.editability?.editable,
    ).toBe(true);
  });

  it('should return correct UI status when does not have permission', () => {
    setupDialogData();
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();
    comp.hasPermission.set(false);

    expect(
      comp.hostPermissionsUiStatus().hostAdmins.editability?.editable,
    ).toBe(false);
    expect(comp.hostPermissionsUiStatus().hostAdmins.visible).toBe(true);

    expect(comp.configModeUiStatus().editability?.editable).toBe(false);
    expect(comp.configModeUiStatus().visible).toBe(true);

    expect(comp.dimensionsUiStatus().sectionStatus.editability?.editable).toBe(
      false,
    );
    expect(comp.dimensionsUiStatus().sectionStatus.visible).toBe(true);

    expect(
      comp.deviceDiscoveryUiStatus().sectionStatus.editability?.editable,
    ).toBe(false);
    expect(comp.deviceDiscoveryUiStatus().sectionStatus.visible).toBe(true);

    expect(comp.permissionsUiStatus().editability?.editable).toBe(false);
    expect(comp.permissionsUiStatus().visible).toBe(true);

    expect(comp.wifiUiStatus().editability?.editable).toBe(false);
    expect(comp.wifiUiStatus().visible).toBe(true);

    expect(comp.settingsUiStatus().editability?.editable).toBe(false);
    expect(comp.settingsUiStatus().visible).toBe(true);

    expect(
      comp.hostPropertiesUiStatus().sectionStatus.editability?.editable,
    ).toBe(false);
    expect(comp.hostPropertiesUiStatus().sectionStatus.visible).toBe(true);
  });

  it('should filter navList based on visibility', () => {
    setupDialogData();
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();

    // Default mock setup has all visible
    expect(comp.visibleSections().length).toBe(4); // host-permissions, device-config, device-discovery, host-properties

    comp.uiStatus.update((status) => ({
      ...status,
      hostAdmins: {
        ...status.hostAdmins,
        visible: false,
      },
    }));
    expect(
      comp.visibleSections().some((s) => s.id === 'host-permissions'),
    ).toBe(false);
  });

  it('should detect dirty categories', () => {
    setupDialogData();
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();

    expect(comp.isCategoryDirty('config-mode')).toBe(false);

    comp.hostConfig.update((c) => ({
      ...c,
      deviceConfigMode: 'SHARED',
    }));

    expect(comp.isCategoryDirty('config-mode')).toBe(true);
  });

  it('should call updateHostConfig on save', () => {
    setupDialogData();
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();
    comp.activeSection.set('host-permissions');

    comp.save();

    expect(mockConfigService.updateHostConfig).toHaveBeenCalled();
  });

  it('should prompt warning dialog when saving with empty dimensions, and clear empty dimensions on confirm without calling API if no other changes', async () => {
    setupDialogData({
      config: {
        deviceConfig: {
          dimensions: {
            supported: [{name: 'existing', value: 'value'}],
            required: [],
          },
        } as unknown as HostConfig['deviceConfig'],
      },
    });
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();
    comp.activeSection.set('dimensions');

    comp.updateDeviceDimensions({
      supported: [
        ...(comp.deviceConfig().dimensions?.supported || []),
        {name: '', value: ''},
      ],
      required: [
        ...(comp.deviceConfig().dimensions?.required || []),
        {name: '', value: ''},
      ],
    });

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('primary'));
    const dialogSpy = spyOn(MatDialog.prototype, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    comp.save();

    expect(dialogSpy).toHaveBeenCalledTimes(1);
    const openCall = dialogSpy.calls.first();
    expect(openCall).toBeTruthy();
    expect(openCall!.args[0]).toBe(ConfirmDialog);
    expect(openCall!.args[1]).toBeTruthy();
    const dialogConfig = openCall!.args[1] as {data: {title: string}};
    expect(dialogConfig.data.title).toBe('Incomplete Dimensions');

    expect(comp.deviceConfig().dimensions?.supported?.length).toBe(1);
    expect(comp.deviceConfig().dimensions?.supported?.[0].name).toBe(
      'existing',
    );
    expect(comp.deviceConfig().dimensions?.required?.length).toBe(0);

    expect(mockConfigService.updateHostConfig).not.toHaveBeenCalled();

    TestBed.inject(ApplicationRef).tick();

    const saveButton = document.querySelector(
      '.wizard-button-primary',
    ) as HTMLButtonElement;
    const discardButton = document.querySelector(
      '.wizard-button-secondary',
    ) as HTMLButtonElement;

    expect(saveButton).toBeTruthy();
    expect(discardButton).toBeTruthy();
    expect(saveButton.disabled).toBeTrue();
    expect(discardButton.disabled).toBeTrue();
  });

  it('should prompt warning dialog when saving with partially filled dimensions (missing name or value), and clear them on confirm', async () => {
    setupDialogData({
      config: {
        deviceConfig: {
          dimensions: {
            supported: [{name: 'existing', value: 'value'}],
            required: [],
          },
        } as unknown as HostConfig['deviceConfig'],
      },
    });
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();
    comp.activeSection.set('dimensions');

    comp.updateDeviceDimensions({
      supported: [
        {name: 'existing', value: 'value'},
        {name: 'partial-no-value', value: ''},
        {name: '', value: 'partial-no-name'},
      ],
      required: [
        {name: 'partial-no-value', value: ''},
        {name: '', value: 'partial-no-name'},
      ],
    });

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('primary'));
    const dialogSpy = spyOn(MatDialog.prototype, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    comp.save();

    expect(dialogSpy).toHaveBeenCalled();
    const openCall = dialogSpy.calls.first();
    expect(openCall).toBeTruthy();
    const dialogConfig = openCall!.args[1] as {data: {title: string}};
    expect(dialogConfig.data.title).toBe('Incomplete Dimensions');

    expect(comp.deviceConfig().dimensions?.supported?.length).toBe(1);
    expect(comp.deviceConfig().dimensions?.supported?.[0].name).toBe(
      'existing',
    );
    expect(comp.deviceConfig().dimensions?.required?.length).toBe(0);

    expect(mockConfigService.updateHostConfig).not.toHaveBeenCalled();
  });

  it('should prompt warning dialog when saving with empty host properties, and clear empty properties on confirm without calling API if no other changes', async () => {
    setupDialogData({
      config: {
        hostProperties: [{key: 'existing', value: 'value'}],
      },
    });
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();
    comp.activeSection.set('host-properties');

    comp.updateHostProperties([
      ...(comp.hostConfig().hostProperties || []),
      {key: '', value: ''},
    ]);

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('primary'));
    const dialogSpy = spyOn(MatDialog.prototype, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    comp.save();

    expect(dialogSpy).toHaveBeenCalledTimes(1);
    const openCall = dialogSpy.calls.first();
    expect(openCall).toBeTruthy();
    expect(openCall!.args[0]).toBe(ConfirmDialog);
    expect(openCall!.args[1]).toBeTruthy();
    const dialogConfig = openCall!.args[1] as {data: {title: string}};
    expect(dialogConfig.data.title).toBe('Incomplete Properties');

    expect(comp.hostConfig().hostProperties?.length).toBe(1);
    expect(comp.hostConfig().hostProperties?.[0].key).toBe('existing');

    expect(mockConfigService.updateHostConfig).not.toHaveBeenCalled();

    TestBed.inject(ApplicationRef).tick();

    const saveButton = document.querySelector(
      '.wizard-button-primary',
    ) as HTMLButtonElement;
    const discardButton = document.querySelector(
      '.wizard-button-secondary',
    ) as HTMLButtonElement;

    expect(saveButton).toBeTruthy();
    expect(discardButton).toBeTruthy();
    expect(saveButton.disabled).toBeTrue();
    expect(discardButton.disabled).toBeTrue();
  });

  it('should prompt warning dialog when saving with partially filled host properties (missing key or value), and clear them on confirm', async () => {
    setupDialogData({
      config: {
        hostProperties: [{key: 'existing', value: 'value'}],
      },
    });
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();
    comp.activeSection.set('host-properties');

    comp.updateHostProperties([
      ...(comp.hostConfig().hostProperties || []),
      {key: 'partial-no-value', value: ''},
      {key: '', value: 'partial-no-key'},
    ]);

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('primary'));
    const dialogSpy = spyOn(MatDialog.prototype, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    comp.save();

    expect(dialogSpy).toHaveBeenCalledTimes(1);
    const openCall = dialogSpy.calls.first();
    expect(openCall).toBeTruthy();
    const dialogConfig = openCall!.args[1] as {data: {title: string}};
    expect(dialogConfig.data.title).toBe('Incomplete Properties');

    expect(comp.hostConfig().hostProperties?.length).toBe(1);
    expect(comp.hostConfig().hostProperties?.[0].key).toBe('existing');
    expect(mockConfigService.updateHostConfig).not.toHaveBeenCalled();
  });

  it('should prompt warning dialog, clear empty dimensions, and call API if there are other valid changes in HostSettings', async () => {
    setupDialogData({
      config: {
        deviceConfig: {
          dimensions: {
            supported: [{name: 'existing', value: 'value'}],
            required: [],
          },
        } as unknown as HostConfig['deviceConfig'],
      },
    });
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();
    comp.activeSection.set('dimensions');

    // Valid change + empty row
    comp.updateDeviceDimensions({
      supported: [
        {name: 'existing', value: 'NEW_VALUE'},
        {name: '', value: ''},
      ],
      required: comp.deviceConfig().dimensions?.required || [],
    });

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('primary'));
    const dialogSpy = spyOn(MatDialog.prototype, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    const updateConfigSubject = new Subject<UpdateHostConfigResult>();
    mockConfigService.updateHostConfig.and.returnValue(
      updateConfigSubject.asObservable(),
    );

    comp.save();

    expect(dialogSpy).toHaveBeenCalled();
    TestBed.inject(ApplicationRef).tick();

    const saveButton = document.querySelector(
      '.wizard-button-primary',
    ) as HTMLButtonElement;
    const discardButton = document.querySelector(
      '.wizard-button-secondary',
    ) as HTMLButtonElement;

    expect(comp.saving()).toBeTrue();
    expect(saveButton.disabled).toBeTrue();
    expect(discardButton.disabled).toBeTrue();

    updateConfigSubject.next({success: true});
    updateConfigSubject.complete();
    TestBed.inject(ApplicationRef).tick();

    expect(comp.saving()).toBeFalse();
    expect(comp.deviceConfig().dimensions?.supported?.length).toBe(1);
    expect(comp.deviceConfig().dimensions?.supported?.[0].value).toBe(
      'NEW_VALUE',
    );

    expect(mockConfigService.updateHostConfig).toHaveBeenCalledWith(
      jasmine.objectContaining({
        config: jasmine.objectContaining({
          deviceConfig: jasmine.objectContaining({
            dimensions: {
              supported: [{name: 'existing', value: 'NEW_VALUE'}],
              required: [],
            },
          }),
        }),
      }),
    );
  });

  it('should prompt warning dialog, clear empty properties, and call API if there are other valid changes in HostSettings', async () => {
    setupDialogData({
      config: {
        hostProperties: [{key: 'existing', value: 'value'}],
      },
    });
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();
    comp.activeSection.set('host-properties');

    // Valid change + empty row
    comp.updateHostProperties([
      {key: 'existing', value: 'NEW_VALUE'},
      {key: '', value: ''},
    ]);

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('primary'));
    const dialogSpy = spyOn(MatDialog.prototype, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    comp.save();

    expect(dialogSpy).toHaveBeenCalled();
    expect(comp.hostConfig().hostProperties?.length).toBe(1);
    expect(comp.hostConfig().hostProperties?.[0].value).toBe('NEW_VALUE');

    expect(mockConfigService.updateHostConfig).toHaveBeenCalledWith(
      jasmine.objectContaining({
        config: jasmine.objectContaining({
          hostProperties: [{key: 'existing', value: 'NEW_VALUE'}],
        }),
      }),
    );
  });

  it('should change section immediately if not dirty', () => {
    setupDialogData();
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();

    expect(comp.activeSection()).toBe('host-permissions');

    const event = jasmine.createSpyObj('Event', ['preventDefault']);
    comp.setActiveSection(event, 'config-mode');

    expect(comp.activeSection()).toBe('config-mode');
    expect(event.preventDefault).toHaveBeenCalled();
  });

  it('should prompt ConfirmDialog if dirty and switch section on confirm', () => {
    setupDialogData({
      config: {
        permissions: {hostAdmins: ['admin1']},
      },
    });
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();

    // Make it dirty
    comp.updateHostPermissions(['admin1', 'admin2']);

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('primary'));
    const dialogSpy = spyOn(MatDialog.prototype, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    const event = jasmine.createSpyObj('Event', ['preventDefault']);
    comp.setActiveSection(event, 'config-mode');

    expect(dialogSpy).toHaveBeenCalled();
    expect(comp.activeSection()).toBe('config-mode');
    // Should revert changes
    expect(comp.hostConfig().permissions.hostAdmins).toEqual(['admin1']);
  });

  it('should prompt ConfirmDialog if dirty and stay on cancel', () => {
    setupDialogData({
      config: {
        permissions: {hostAdmins: ['admin1']},
      },
    });
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();

    // Make it dirty
    comp.updateHostPermissions(['admin1', 'admin2']);

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('secondary'));
    const dialogSpy = spyOn(MatDialog.prototype, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    const event = jasmine.createSpyObj('Event', ['preventDefault']);
    comp.setActiveSection(event, 'config-mode');

    expect(dialogSpy).toHaveBeenCalled();
    expect(comp.activeSection()).toBe('host-permissions');
    // Should keep changes
    expect(comp.hostConfig().permissions.hostAdmins).toEqual([
      'admin1',
      'admin2',
    ]);
  });

  it('should close dialog on reset', () => {
    setupDialogData({
      config: {
        permissions: {hostAdmins: []},
      } as unknown as HostConfig,
    });
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();

    comp.reset();

    expect(dialogRef.close).toHaveBeenCalledWith({
      action: 'reset',
      hostName: 'test-host',
    });
  });

  it('should revert changes on discard', () => {
    setupDialogData({
      config: {
        permissions: {hostAdmins: ['admin1']},
      },
    });
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();

    comp.updateHostPermissions(['admin1', 'admin2']);
    expect(comp.hostConfig().permissions.hostAdmins).toEqual([
      'admin1',
      'admin2',
    ]);

    comp.discard();

    expect(comp.hostConfig().permissions.hostAdmins).toEqual(['admin1']);
  });

  it('should prompt self-lockout warning on SELF_LOCKOUT_DETECTED', () => {
    setupDialogData();
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('secondary'));
    const dialogSpy = spyOn(MatDialog.prototype, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    comp.error('SELF_LOCKOUT_DETECTED');

    expect(dialogSpy).toHaveBeenCalled();
    const openCall = dialogSpy.calls.first();
    expect(openCall).toBeTruthy();
    const dialogConfig = openCall!.args[1] as {data: {title: string}};
    expect(dialogConfig.data.title).toBe('Permission Warning');
  });

  it('should prompt error dialog on other errors', () => {
    setupDialogData();
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();

    const dialogSpy = spyOn(MatDialog.prototype, 'open').and.returnValue(
      jasmine.createSpyObj('MatDialogRef', ['afterClosed']),
    );

    comp.error('UNKNOWN_ERROR');

    expect(dialogSpy).toHaveBeenCalled();
    const openCall = dialogSpy.calls.first();
    expect(openCall).toBeTruthy();
    const dialogConfig = openCall!.args[1] as {data: {title: string}};
    expect(dialogConfig.data.title).toBe('Configuration Failed');
  });

  it('should update config and device config properties', () => {
    setupDialogData();
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();

    comp.updateDeviceConfigMode('SHARED');
    expect(comp.hostConfig().deviceConfigMode).toBe('SHARED');

    const newWifi: WifiConfig = {
      type: 'custom',
      ssid: 'NewSSID',
      psk: '',
      scanSsid: false,
    };
    comp.updateDeviceWifi(newWifi);
    expect(comp.deviceConfig().wifi).toEqual(newWifi);

    const newSettings: StabilitySettings = {
      maxConsecutiveFail: 10,
      maxConsecutiveTest: 10000,
    };
    comp.updateDeviceSettings(newSettings);
    expect(comp.deviceConfig().settings).toEqual(newSettings);

    const newDiscovery: HostConfig['deviceDiscovery'] = {
      monitoredDeviceUuids: ['uuid1'],
      testbedUuids: [],
      miscDeviceUuids: [],
      overTcpIps: [],
      overSshDevices: [],
      manekiSpecs: [],
    };
    comp.updateDeviceDiscovery(newDiscovery);
    expect(comp.hostConfig().deviceDiscovery).toEqual(newDiscovery);

    comp.onHostPermissionsChange(['admin1', 'admin2']);
    expect(comp.hostConfig().permissions.hostAdmins).toEqual([
      'admin1',
      'admin2',
    ]);
    expect(comp.deviceConfig().permissions?.owners).toEqual([
      'admin1',
      'admin2',
    ]);
  });

  it('should default activeSection to "default" if no sections are visible', () => {
    mockHostConfigStateService.getUiStatus.and.returnValue({
      hostAdmins: {visible: false},
      deviceConfigMode: {visible: false},
      deviceConfig: {
        sectionStatus: {visible: false},
        subSections: {},
      },
      hostProperties: {
        sectionStatus: {visible: false},
      },
      deviceDiscovery: {visible: false},
    });

    setupDialogData();
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();

    expect(comp.activeSection()).toBe('default');
  });

  it('should initialize activeSection to config-mode if host-permissions is hidden', () => {
    mockHostConfigStateService.getUiStatus.and.returnValue({
      hostAdmins: {visible: false}, // Hide first item
      deviceConfigMode: {visible: true}, // Group will be visible
      deviceConfig: {
        sectionStatus: {visible: true},
        subSections: {
          permissions: {visible: false},
          wifi: {visible: true},
        },
      },
      hostProperties: {sectionStatus: {visible: false}},
      deviceDiscovery: {visible: false},
    });

    setupDialogData();
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();
    expect(comp.activeSection()).toBe('config-mode');
  });

  it('should initialize activeSection to wifi if config-mode and host-permissions are hidden', () => {
    mockHostConfigStateService.getUiStatus.and.returnValue({
      hostAdmins: {visible: false},
      deviceConfigMode: {visible: false},
      deviceConfig: {
        sectionStatus: {visible: true},
        subSections: {
          permissions: {visible: false},
          wifi: {visible: true},
        },
      },
      hostProperties: {sectionStatus: {visible: false}},
      deviceDiscovery: {visible: false},
    });

    setupDialogData();
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();
    expect(comp.activeSection()).toBe('wifi');
  });

  it('should set editability based on uiStatus for different sections', () => {
    mockHostConfigStateService.getUiStatus.and.returnValue({
      hostAdmins: {
        visible: true,
        editability: {editable: false, reason: 'r1'},
      },
      deviceConfigMode: {visible: true, editability: {editable: true}},
      deviceConfig: {
        sectionStatus: {
          visible: true,
          editability: {editable: false, reason: 'r2'},
        },
        subSections: {},
      },
      hostProperties: {
        sectionStatus: {visible: true, editability: {editable: true}},
      },
      deviceDiscovery: {
        visible: true,
        editability: {editable: false, reason: 'r3'},
      },
    });

    setupDialogData();
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    comp.hasPermission.set(true);
    TestBed.inject(ApplicationRef).tick();

    comp.setActiveSectionEditibility('host-permissions');
    expect(comp.activeSectionEditibility()).toEqual({
      editable: false,
      reason: 'r1',
    });

    comp.setActiveSectionEditibility('config-mode');
    expect(comp.activeSectionEditibility()).toEqual({editable: true});

    comp.setActiveSectionEditibility('permissions');
    expect(comp.activeSectionEditibility()).toEqual({
      editable: false,
      reason: 'r2',
    });

    comp.setActiveSectionEditibility('wifi');
    expect(comp.activeSectionEditibility()).toEqual({
      editable: false,
      reason: 'r2',
    });

    comp.setActiveSectionEditibility('device-discovery');
    expect(comp.activeSectionEditibility()).toEqual({
      editable: false,
      reason: 'r3',
    });

    comp.setActiveSectionEditibility('host-properties');
    expect(comp.activeSectionEditibility()).toEqual({editable: true});

    comp.setActiveSectionEditibility('unknown');
    expect(comp.activeSectionEditibility()).toEqual({
      editable: true,
      reason: '',
    });
  });

  it('should detect dirty states correctly', () => {
    setupDialogData({
      config: {
        permissions: {hostAdmins: ['admin1']},
        deviceConfigMode: 'PER_DEVICE',
        deviceConfig: {
          permissions: {owners: ['admin1'], executors: []},
          wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
          dimensions: {supported: [], required: []},
          settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
        },
        hostProperties: [{key: 'k1', value: 'v1'}],
        deviceDiscovery: {monitoredDeviceUuids: []},
      } as unknown as HostConfig,
    });
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();

    expect(comp.isCategoryDirty('host-permissions')).toBeFalse();
    comp.updateHostPermissions(['admin1', 'admin2']);
    expect(comp.isCategoryDirty('host-permissions')).toBeTrue();

    expect(comp.isCategoryDirty('permissions')).toBeFalse();
    comp.updateDevicePermissions({
      owners: ['admin1', 'admin2'],
      executors: [],
    });
    expect(comp.isCategoryDirty('permissions')).toBeTrue();

    expect(comp.isCategoryDirty('wifi')).toBeFalse();
    comp.updateDeviceWifi({type: 'custom', ssid: 'SSID'} as WifiConfig);
    expect(comp.isCategoryDirty('wifi')).toBeTrue();

    expect(comp.isCategoryDirty('dimensions')).toBeFalse();
    comp.updateDeviceDimensions({
      supported: [{name: 'd1', value: 'v1'}],
      required: [],
    });
    expect(comp.isCategoryDirty('dimensions')).toBeTrue();

    expect(comp.isCategoryDirty('stability')).toBeFalse();
    comp.updateDeviceSettings({maxConsecutiveFail: 10} as StabilitySettings);
    expect(comp.isCategoryDirty('stability')).toBeTrue();

    expect(comp.isCategoryDirty('device-discovery')).toBeFalse();
    comp.updateDeviceDiscovery({
      monitoredDeviceUuids: ['uuid1'],
    } as HostConfig['deviceDiscovery']);
    expect(comp.isCategoryDirty('device-discovery')).toBeTrue();

    expect(comp.isCategoryDirty('host-properties')).toBeFalse();
    comp.updateHostProperties([{key: 'k1', value: 'v2'}]);
    expect(comp.isCategoryDirty('host-properties')).toBeTrue();

    expect(comp.isCategoryDirty('unknown')).toBeFalse();
  });

  it('should handle SELF_LOCKOUT_DETECTED error, prompt warning, and retry on confirm', () => {
    setupDialogData();
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();

    const updateSubject = new Subject<UpdateHostConfigResult>();
    mockConfigService.updateHostConfig.and.returnValue(
      updateSubject.asObservable(),
    );

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('primary')); // User clicks "Proceed Anyway"
    const dialogSpy = spyOn(MatDialog.prototype, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    comp.save();

    // 1. First save returns SELF_LOCKOUT_DETECTED
    updateSubject.next({
      success: false,
      error: {code: 'SELF_LOCKOUT_DETECTED'},
    });
    updateSubject.complete();

    // Verify dialog was opened with warning
    expect(dialogSpy).toHaveBeenCalled();
    const openCall = dialogSpy.calls.first();
    expect(openCall).toBeTruthy();
    const dialogConfig = openCall!.args[1] as {data: {title: string}};
    expect(dialogConfig.data.title).toBe('Permission Warning');

    // 2. After dialog closes with 'primary', it should call save(true)
    // which calls updateHostConfig again with overrideSelfLockout: true.
    expect(mockConfigService.updateHostConfig).toHaveBeenCalledTimes(2);
    expect(
      mockConfigService.updateHostConfig.calls.mostRecent()!.args[0].options
        ?.overrideSelfLockout,
    ).toBeTrue();
  });

  it('should close dialog on successful self-lockout save', () => {
    setupDialogData();
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostSettings, {
        data: dialogData,
      }),
    );
    comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogRef = dialogOpener.componentInstance.dialogRef;
    spyOn(dialogRef, 'close');
    TestBed.inject(ApplicationRef).tick();

    mockConfigService.updateHostConfig.and.returnValue(of({success: true}));

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('primary')); // Success dialog OK click
    spyOn(MatDialog.prototype, 'open').and.returnValue(confirmDialogRefSpy);

    comp.save(true); // selfLockout = true

    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });
});
