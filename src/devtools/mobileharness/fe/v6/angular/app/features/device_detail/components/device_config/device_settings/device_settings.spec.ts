// Force recompile
import {ApplicationRef} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {MatDialog, MatDialogModule} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of, Subject} from 'rxjs';

import {
  ConfigSection,
  type DeviceConfig,
  type GetDeviceConfigResult,
  type StabilitySettings,
  type UpdateDeviceConfigResult,
  type WifiConfig,
} from '../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {DeviceConfigStateService} from '../../../../../core/services/config/device_config_state_service';
import {FakeConfigService} from '../../../../../core/services/config/fake_config_service';
import {DEVICE_SERVICE} from '../../../../../core/services/device/device_service';
import {FakeDeviceService} from '../../../../../core/services/device/fake_device_service';
import {SCENARIO_IN_SERVICE_IDLE} from '../../../../../core/services/mock_data/devices/01_in_service_idle';
import {ConfirmDialog} from '../../../../../shared/components/confirm_dialog/confirm_dialog';

import {DeviceSettings} from './device_settings';

describe('Device Settings Component', () => {
  let fakeConfigService: FakeConfigService;
  let fakeDeviceConfigStateService: jasmine.SpyObj<DeviceConfigStateService>;
  let deviceConfigResponse: GetDeviceConfigResult;

  beforeEach(async () => {
    fakeConfigService = new FakeConfigService();
    deviceConfigResponse = {
      deviceConfig: {},
      isHostManaged: false,
      uiStatus: {},
    };
    spyOn(fakeConfigService, 'getDeviceConfig').and.callFake(() =>
      of(deviceConfigResponse),
    );
    spyOn(fakeConfigService, 'checkDeviceWritePermission').and.returnValue(
      of({hasPermission: true, userName: 'test-user'}),
    );

    fakeDeviceConfigStateService =
      jasmine.createSpyObj<DeviceConfigStateService>(
        'DeviceConfigStateService',
        ['getUiStatus', 'setUiStatus', 'clear'],
      );
    fakeDeviceConfigStateService.getUiStatus.and.returnValue({
      permissions: {visible: true, editability: {editable: true}},
      wifi: {visible: true, editability: {editable: true}},
      dimensions: {visible: true, editability: {editable: true}},
      settings: {visible: true, editability: {editable: true}},
    });

    await TestBed.configureTestingModule({
      imports: [
        DeviceSettings,
        NoopAnimationsModule, // This makes test faster and more stable.
        MatDialogModule,
        MatTestDialogOpenerModule,
      ],
      providers: [
        provideRouter([]),
        {provide: DEVICE_SERVICE, useClass: FakeDeviceService},
        {provide: CONFIG_SERVICE, useValue: fakeConfigService},
        {
          provide: DeviceConfigStateService,
          useValue: fakeDeviceConfigStateService,
        },
      ],
    }).compileComponents();
  });

  it('should be created', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {
          deviceId: 'test-device',
          config: SCENARIO_IN_SERVICE_IDLE.config as DeviceConfig,
        },
      }),
    );
    TestBed.inject(ApplicationRef).tick();
    expect(dialogOpener.componentInstance.dialogRef).toBeTruthy();
    expect(
      dialogOpener.componentInstance.dialogRef.componentInstance,
    ).toBeTruthy();
  });

  it('should prompt warning dialog when saving with empty dimensions, and clear empty dimensions on confirm without calling API if no other changes', async () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    component.config = {
      dimensions: {
        supported: [{name: 'existing', value: 'value'}],
        required: [],
      },
    } as DeviceConfig;
    TestBed.inject(ApplicationRef).tick();

    component.activeSection.set(ConfigSection.DIMENSIONS);

    component.updateDimensions({
      supported: [
        ...(component.newConfig().dimensions?.supported || []),
        {name: '', value: ''},
      ],
      required: [
        ...(component.newConfig().dimensions?.required || []),
        {name: '', value: ''},
      ],
    });

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('primary'));
    const dialog = TestBed.inject(MatDialog);
    const dialogSpy = spyOn(dialog, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    spyOn(fakeConfigService, 'updateDeviceConfig').and.returnValue(
      of({success: true}),
    );

    component.save();

    expect(dialogSpy).toHaveBeenCalledTimes(1);
    const openCall = dialogSpy.calls.first();
    expect(openCall).toBeTruthy();
    expect(openCall!.args[0]).toBe(ConfirmDialog);
    expect(openCall!.args[1]).toBeTruthy();
    const dialogConfig = openCall!.args[1] as {data: {title: string}};
    expect(dialogConfig.data.title).toBe('Incomplete Dimensions');

    expect(component.newConfig().dimensions?.supported?.length).toBe(1);
    expect(component.newConfig().dimensions?.supported?.[0].name).toBe(
      'existing',
    );
    expect(component.newConfig().dimensions?.required?.length).toBe(0);

    expect(fakeConfigService.updateDeviceConfig).not.toHaveBeenCalled();

    TestBed.inject(ApplicationRef).tick();

    const dialogElement = document.querySelector(
      'mat-dialog-container, mat-mdc-dialog-container',
    );
    expect(dialogElement).toBeTruthy();
    const saveButton = dialogElement!.querySelector(
      '.save-button',
    ) as HTMLButtonElement;
    const discardButton = dialogElement!.querySelector(
      '.discard-button',
    ) as HTMLButtonElement;

    expect(saveButton).toBeTruthy();
    expect(discardButton).toBeTruthy();
    expect(saveButton.disabled).toBeTrue();
    expect(discardButton.disabled).toBeTrue();
  });

  it('should prompt warning dialog when saving with empty dimensions, and do nothing on cancel', async () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    component.config = {
      dimensions: {
        supported: [
          {name: 'existing', value: 'value'},
          {name: '', value: ''},
        ],
        required: [{name: '', value: ''}],
      },
    } as DeviceConfig;
    TestBed.inject(ApplicationRef).tick();

    component.activeSection.set(ConfigSection.DIMENSIONS);

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('secondary'));
    const dialog = TestBed.inject(MatDialog);
    const dialogSpy = spyOn(dialog, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    spyOn(fakeConfigService, 'updateDeviceConfig').and.returnValue(
      of({success: true}),
    );

    component.save();

    expect(dialogSpy).toHaveBeenCalled();
    expect(component.newConfig().dimensions?.supported?.length).toBe(2);
    expect(component.newConfig().dimensions?.required?.length).toBe(1);

    expect(fakeConfigService.updateDeviceConfig).not.toHaveBeenCalled();
  });

  it('should prompt warning dialog when saving with partially filled dimensions (missing name or value), and clear them on confirm', async () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    component.config = {
      dimensions: {
        supported: [{name: 'existing', value: 'value'}],
        required: [],
      },
    } as DeviceConfig;
    TestBed.inject(ApplicationRef).tick();

    component.activeSection.set(ConfigSection.DIMENSIONS);

    component.updateDimensions({
      supported: [
        {name: 'existing', value: 'value'},
        {name: 'partial-no-value', value: ''},
      ],
      required: [{name: '', value: 'partial-no-name'}],
    });

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('primary'));
    const dialog = TestBed.inject(MatDialog);
    const dialogSpy = spyOn(dialog, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    spyOn(fakeConfigService, 'updateDeviceConfig').and.returnValue(
      of({success: true}),
    );

    component.save();

    expect(dialogSpy).toHaveBeenCalled();
    const openCall = dialogSpy.calls.first();
    expect(openCall).toBeTruthy();
    const dialogConfig = openCall!.args[1] as {data: {title: string}};
    expect(dialogConfig.data.title).toBe('Incomplete Dimensions');

    expect(component.newConfig().dimensions?.supported?.length).toBe(1);
    expect(component.newConfig().dimensions?.supported?.[0].name).toBe(
      'existing',
    );
    expect(component.newConfig().dimensions?.required?.length).toBe(0);

    expect(fakeConfigService.updateDeviceConfig).not.toHaveBeenCalled();
  });

  it('should prompt warning dialog, clear empty dimensions, and call API if there are other valid changes', async () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    component.config = {
      dimensions: {
        supported: [{name: 'existing', value: 'value'}],
        required: [],
      },
    } as DeviceConfig;
    TestBed.inject(ApplicationRef).tick();

    component.activeSection.set(ConfigSection.DIMENSIONS);

    // Valid change + empty row
    component.updateDimensions({
      supported: [
        {name: 'existing', value: 'NEW_VALUE'},
        {name: '', value: ''},
      ],
      required: component.newConfig().dimensions?.required || [],
    });

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('primary'));
    const dialog = TestBed.inject(MatDialog);
    const dialogSpy = spyOn(dialog, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    const updateConfigSubject = new Subject<UpdateDeviceConfigResult>();
    spyOn(fakeConfigService, 'updateDeviceConfig').and.returnValue(
      updateConfigSubject.asObservable(),
    );

    component.save();

    expect(dialogSpy).toHaveBeenCalled();
    TestBed.inject(ApplicationRef).tick();

    const dialogElement = document.querySelector(
      'mat-dialog-container, mat-mdc-dialog-container',
    );
    const saveButton = dialogElement!.querySelector(
      '.save-button',
    ) as HTMLButtonElement;
    const discardButton = dialogElement!.querySelector(
      '.discard-button',
    ) as HTMLButtonElement;

    expect(component.saving()).toBeTrue();
    expect(saveButton.disabled).toBeTrue();
    expect(discardButton.disabled).toBeTrue();

    deviceConfigResponse = {
      deviceConfig: {
        dimensions: {
          supported: [{name: 'existing', value: 'NEW_VALUE'}],
          required: [],
        },
      },
      isHostManaged: false,
      uiStatus: {},
    };
    updateConfigSubject.next({success: true});
    updateConfigSubject.complete();
    TestBed.inject(ApplicationRef).tick();

    expect(component.saving()).toBeFalse();
    expect(component.newConfig().dimensions?.supported?.length).toBe(1);
    expect(component.newConfig().dimensions?.supported?.[0].value).toBe(
      'NEW_VALUE',
    );

    expect(fakeConfigService.updateDeviceConfig).toHaveBeenCalledWith(
      jasmine.objectContaining({
        config: jasmine.objectContaining({
          dimensions: {
            supported: [{name: 'existing', value: 'NEW_VALUE'}],
            required: [],
          },
        }),
      }),
    );
  });

  it('should filter navList based on uiStatus visibility', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;

    // Default uiStatus should show all
    TestBed.inject(ApplicationRef).tick();
    expect(component.navList.map((n) => n.id)).toEqual([
      ConfigSection.PERMISSIONS,
      ConfigSection.WIFI,
      ConfigSection.DIMENSIONS,
      ConfigSection.STABILITY,
    ]);

    // Hide WIFI and STABILITY
    component.uiStatus = {
      permissions: {visible: true},
      wifi: {visible: false},
      dimensions: {visible: true},
      settings: {visible: false},
    };
    TestBed.inject(ApplicationRef).tick();
    expect(component.navList.map((n) => n.id)).toEqual([
      ConfigSection.PERMISSIONS,
      ConfigSection.DIMENSIONS,
    ]);
  });

  it('should set editability in computed uiStatus based on hasPermission', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;

    component.hasPermission.set(true);
    TestBed.inject(ApplicationRef).tick();
    expect(component.uiStatus.permissions.editability?.editable).toBeTrue();
    expect(component.uiStatus.wifi.editability?.editable).toBeTrue();

    component.hasPermission.set(false);
    TestBed.inject(ApplicationRef).tick();
    expect(component.uiStatus.permissions.editability?.editable).toBeFalse();
    expect(component.uiStatus.wifi.editability?.editable).toBeFalse();
  });

  it('setActiveSection: should change section immediately if not dirty', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    TestBed.inject(ApplicationRef).tick();

    expect(component.activeSection()).toBe(ConfigSection.PERMISSIONS);

    const event = jasmine.createSpyObj('Event', ['preventDefault']);
    component.setActiveSection(event, ConfigSection.WIFI);

    expect(component.activeSection()).toBe(ConfigSection.WIFI);
    expect(event.preventDefault).toHaveBeenCalled();
  });

  it('setActiveSection: should prompt ConfirmDialog if dirty and switch section on confirm', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    component.config = {
      permissions: {owners: ['user1']},
    } as DeviceConfig;
    TestBed.inject(ApplicationRef).tick();

    // Make it dirty
    component.updatePermissions({owners: ['user1', 'user2']});

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('primary'));
    const dialog = TestBed.inject(MatDialog);
    const dialogSpy = spyOn(dialog, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    const event = jasmine.createSpyObj('Event', ['preventDefault']);
    component.setActiveSection(event, ConfigSection.WIFI);

    expect(dialogSpy).toHaveBeenCalled();
    expect(component.activeSection()).toBe(ConfigSection.WIFI);
    // Should revert changes
    expect(component.newConfig().permissions?.owners).toEqual(['user1']);
  });

  it('setActiveSection: should prompt ConfirmDialog if dirty and stay on cancel', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    component.config = {
      permissions: {owners: ['user1']},
    } as DeviceConfig;
    TestBed.inject(ApplicationRef).tick();

    // Make it dirty
    component.updatePermissions({owners: ['user1', 'user2']});

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('secondary'));
    const dialog = TestBed.inject(MatDialog);
    const dialogSpy = spyOn(dialog, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    const event = jasmine.createSpyObj('Event', ['preventDefault']);
    component.setActiveSection(event, ConfigSection.WIFI);

    expect(dialogSpy).toHaveBeenCalled();
    expect(component.activeSection()).toBe(ConfigSection.PERMISSIONS);
    // Should keep changes
    expect(component.newConfig().permissions?.owners).toEqual([
      'user1',
      'user2',
    ]);
  });

  it('should close dialog on reset', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device', universe: 'test-universe'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    TestBed.inject(ApplicationRef).tick();

    spyOn(dialogOpener.componentInstance.dialogRef, 'close');

    component.reset();

    expect(dialogOpener.componentInstance.dialogRef.close).toHaveBeenCalledWith(
      {
        action: 'reset',
        deviceId: 'test-device',
        universe: 'test-universe',
      },
    );
  });

  it('should revert changes on discard', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    component.config = {
      permissions: {owners: ['user1']},
    } as DeviceConfig;
    TestBed.inject(ApplicationRef).tick();

    component.updatePermissions({owners: ['user1', 'user2']});
    expect(component.newConfig().permissions?.owners).toEqual([
      'user1',
      'user2',
    ]);

    component.discard();

    expect(component.newConfig().permissions?.owners).toEqual(['user1']);
  });

  it('save (non-dimensions): should call API to save permissions', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    component.config = {
      permissions: {owners: ['user1']},
    } as DeviceConfig;
    TestBed.inject(ApplicationRef).tick();

    component.activeSection.set(ConfigSection.PERMISSIONS);
    component.updatePermissions({owners: ['user1', 'user2']});

    spyOn(fakeConfigService, 'updateDeviceConfig').and.returnValue(
      of({success: true}),
    );
    const dialog = TestBed.inject(MatDialog);
    const successDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    successDialogRefSpy.afterClosed.and.returnValue(of('primary'));
    const dialogSpy = spyOn(dialog, 'open').and.returnValue(
      successDialogRefSpy,
    );

    deviceConfigResponse = {
      deviceConfig: {
        permissions: {owners: ['user1', 'user2']},
      },
      isHostManaged: false,
      uiStatus: {},
    };
    component.save();

    expect(fakeConfigService.updateDeviceConfig).toHaveBeenCalledWith({
      id: 'test-device',
      config: jasmine.objectContaining({
        permissions: {owners: ['user1', 'user2']},
      }),
      section: ConfigSection.PERMISSIONS,
      options: {overrideSelfLockout: false},
      universe: '',
    });
    expect(dialogSpy).toHaveBeenCalled(); // Success dialog
    expect(component.newConfig().permissions?.owners).toEqual([
      'user1',
      'user2',
    ]);
  });

  it('should update wifi in newConfig', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    TestBed.inject(ApplicationRef).tick();

    const newWifi: WifiConfig = {
      type: 'custom',
      ssid: 'NewSSID',
      psk: '',
      scanSsid: false,
    };
    component.updateWifi(newWifi);

    expect(component.newConfig().wifi).toEqual(newWifi);
  });

  it('should update settings in newConfig', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    TestBed.inject(ApplicationRef).tick();

    const newSettings: StabilitySettings = {
      maxConsecutiveFail: 10,
      maxConsecutiveTest: 10000,
    };
    component.updateSettings(newSettings);

    expect(component.newConfig().settings).toEqual(newSettings);
  });

  it('should default activeSection to first visible section if PERMISSIONS is hidden', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    component.uiStatus = {
      permissions: {visible: false},
      wifi: {visible: true},
    };
    TestBed.inject(ApplicationRef).tick();

    expect(component.activeSection()).toBe(ConfigSection.WIFI);
    const activeNavItem = document.querySelector('.nav-item.active');
    expect(activeNavItem).toBeTruthy();
    expect(activeNavItem!.textContent).toContain('Wi-Fi');
  });

  it('should default activeSection to first visible section during ngOnInit if PERMISSIONS is hidden in saved UI status', () => {
    fakeDeviceConfigStateService.getUiStatus.and.returnValue({
      permissions: {visible: false, editability: {editable: true}},
      wifi: {visible: true, editability: {editable: true}},
      dimensions: {visible: true, editability: {editable: true}},
      settings: {visible: true, editability: {editable: true}},
    });

    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    TestBed.inject(ApplicationRef).tick();

    expect(component.activeSection()).toBe(ConfigSection.WIFI);
    const activeNavItem = document.querySelector('.nav-item.active');
    expect(activeNavItem).toBeTruthy();
    expect(activeNavItem!.textContent).toContain('Wi-Fi');
  });

  it('self lockout and error handling: should handle SELF_LOCKOUT_DETECTED error, prompt dialog, and retry on confirm', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    TestBed.inject(ApplicationRef).tick();

    const updateSubject = new Subject<UpdateDeviceConfigResult>();
    const updateSpy = spyOn(
      fakeConfigService,
      'updateDeviceConfig',
    ).and.returnValue(updateSubject.asObservable());

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('primary')); // User clicks "Proceed Anyway"
    const dialog = TestBed.inject(MatDialog);
    const dialogSpy = spyOn(dialog, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    component.save();

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
    // which calls updateDeviceConfig again with overrideSelfLockout: true.
    expect(updateSpy).toHaveBeenCalledTimes(2);
    expect(
      updateSpy.calls.mostRecent()!.args[0].options?.overrideSelfLockout,
    ).toBeTrue();
  });

  it('self lockout and error handling: should handle SELF_LOCKOUT_DETECTED error, prompt dialog, and switch to PERMISSIONS on cancel', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    TestBed.inject(ApplicationRef).tick();

    const updateSubject = new Subject<UpdateDeviceConfigResult>();
    const updateSpy = spyOn(
      fakeConfigService,
      'updateDeviceConfig',
    ).and.returnValue(updateSubject.asObservable());

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('secondary')); // User clicks "Go Back"
    const dialog = TestBed.inject(MatDialog);
    const dialogSpy = spyOn(dialog, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    component.activeSection.set(ConfigSection.WIFI); // Start on WIFI

    component.save();

    updateSubject.next({
      success: false,
      error: {code: 'SELF_LOCKOUT_DETECTED'},
    });
    updateSubject.complete();

    expect(dialogSpy).toHaveBeenCalled();
    // Should switch back to PERMISSIONS section
    expect(component.activeSection()).toBe(ConfigSection.PERMISSIONS);
    // Should not retry save
    expect(updateSpy).toHaveBeenCalledTimes(1);
  });

  it('self lockout and error handling: should show error dialog on other save failures', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    TestBed.inject(ApplicationRef).tick();

    spyOn(fakeConfigService, 'updateDeviceConfig').and.returnValue(
      of({success: false, error: {code: 'VALIDATION_ERROR'}}),
    );

    const dialog = TestBed.inject(MatDialog);
    const dialogSpy = spyOn(dialog, 'open').and.returnValue(
      jasmine.createSpyObj('MatDialogRef', ['afterClosed']),
    );

    component.save();

    expect(dialogSpy).toHaveBeenCalled();
    const openCall = dialogSpy.calls.first();
    expect(openCall).toBeTruthy();
    const dialogConfig = openCall!.args[1] as {data: {title: string}};
    expect(dialogConfig.data.title).toBe('Configuration Failed');
  });

  it('self lockout and error handling: should close dialog on successful self-lockout save', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    TestBed.inject(ApplicationRef).tick();

    spyOn(fakeConfigService, 'updateDeviceConfig').and.returnValue(
      of({success: true}),
    );

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('primary')); // Success dialog OK click
    const dialog = TestBed.inject(MatDialog);
    spyOn(dialog, 'open').and.returnValue(confirmDialogRefSpy);

    spyOn(dialogOpener.componentInstance.dialogRef, 'close');

    component.save(true); // selfLockout = true

    expect(dialogOpener.componentInstance.dialogRef.close).toHaveBeenCalledWith(
      true,
    );
  });

  it('self lockout and error handling: should save permissions and NOT prompt dimensions warning even if there are empty dimensions', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    component.config = {
      dimensions: {
        supported: [{name: 'existing', value: 'value'}],
        required: [],
      },
    } as DeviceConfig;
    TestBed.inject(ApplicationRef).tick();

    component.activeSection.set(ConfigSection.PERMISSIONS); // Saving PERMISSIONS

    // Set incomplete dimensions in newConfig (dirty dimensions, but we are not saving them)
    component.updateDimensions({
      supported: [
        {name: 'existing', value: 'value'},
        {name: '', value: ''}, // Incomplete
      ],
      required: [],
    });
    // Also make permissions dirty so it actually saves
    component.updatePermissions({owners: ['user1']});

    const updateSubject = new Subject<UpdateDeviceConfigResult>();
    spyOn(fakeConfigService, 'updateDeviceConfig').and.returnValue(
      updateSubject.asObservable(),
    );
    const dialog = TestBed.inject(MatDialog);
    const dialogSpy = spyOn(dialog, 'open').and.returnValue(
      jasmine.createSpyObj('MatDialogRef', ['afterClosed']),
    );

    component.save();

    // Should NOT open dialog
    expect(dialogSpy).not.toHaveBeenCalled();
    // Should call API with PERMISSIONS section
    expect(fakeConfigService.updateDeviceConfig).toHaveBeenCalledWith(
      jasmine.objectContaining({
        section: ConfigSection.PERMISSIONS,
      }),
    );
  });

  it('save (wifi): should clear wifi settings if type is none on submit', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    component.config = {
      wifi: {type: 'custom', ssid: 'old_ssid', psk: '', scanSsid: false},
    } as DeviceConfig;
    TestBed.inject(ApplicationRef).tick();

    component.activeSection.set(ConfigSection.WIFI);
    component.updateWifi({type: 'none', ssid: '', psk: '', scanSsid: false});

    const updateSpy = spyOn(
      fakeConfigService,
      'updateDeviceConfig',
    ).and.returnValue(of({success: true}));
    const dialog = TestBed.inject(MatDialog);
    const successDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    successDialogRefSpy.afterClosed.and.returnValue(of('primary'));
    spyOn(dialog, 'open').and.returnValue(successDialogRefSpy);

    component.save();

    expect(updateSpy).toHaveBeenCalled();
    const updateRequest = updateSpy.calls.mostRecent().args[0];
    expect(updateRequest.config.wifi).toBeUndefined();
  });

  it('save (wifi): should preserve wifi settings if type is not none on submit', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    component.config = {
      wifi: {type: 'custom', ssid: 'old_ssid', psk: '', scanSsid: false},
    } as DeviceConfig;
    TestBed.inject(ApplicationRef).tick();

    component.activeSection.set(ConfigSection.WIFI);
    const validWifi: WifiConfig = {
      type: 'custom',
      ssid: 'NewSSID',
      psk: 'pass',
      scanSsid: true,
    };
    component.updateWifi(validWifi);

    const updateSpy = spyOn(
      fakeConfigService,
      'updateDeviceConfig',
    ).and.returnValue(of({success: true}));
    const dialog = TestBed.inject(MatDialog);
    const successDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    successDialogRefSpy.afterClosed.and.returnValue(of('primary'));
    spyOn(dialog, 'open').and.returnValue(successDialogRefSpy);

    component.save();

    expect(updateSpy).toHaveBeenCalled();
    const updateRequest = updateSpy.calls.mostRecent().args[0];
    expect(updateRequest.config.wifi).toEqual(validWifi);
  });

  it('should return non-editable UI status when saving is in progress', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    component.saving.set(true);
    TestBed.inject(ApplicationRef).tick();

    expect(component.uiStatus.permissions.editability?.editable).toBeFalse();
    expect(component.uiStatus.permissions.editability?.reason).toBe(
      'Saving in progress...',
    );

    expect(component.uiStatus.wifi.editability?.editable).toBeFalse();
    expect(component.uiStatus.dimensions.editability?.editable).toBeFalse();
    expect(component.uiStatus.settings.editability?.editable).toBeFalse();
  });

  it('should not change section if saving is in progress', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    TestBed.inject(ApplicationRef).tick();

    expect(component.activeSection()).toBe(ConfigSection.PERMISSIONS);

    component.saving.set(true);
    const event = jasmine.createSpyObj('Event', ['preventDefault']);
    component.setActiveSection(event, ConfigSection.WIFI);

    expect(component.activeSection()).toBe(ConfigSection.PERMISSIONS); // Should not change
    expect(event.preventDefault).toHaveBeenCalled();
  });
});
