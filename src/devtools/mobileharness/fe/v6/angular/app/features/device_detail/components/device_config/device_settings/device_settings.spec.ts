// Force recompile
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
  type StabilitySettings,
  type UpdateDeviceConfigResult,
  type WifiConfig,
} from '../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {FakeConfigService} from '../../../../../core/services/config/fake_config_service';
import {DEVICE_SERVICE} from '../../../../../core/services/device/device_service';
import {FakeDeviceService} from '../../../../../core/services/device/fake_device_service';
import {SCENARIO_IN_SERVICE_IDLE} from '../../../../../core/services/mock_data/devices/01_in_service_idle';
import {ConfirmDialog} from '../../../../../shared/components/confirm_dialog/confirm_dialog';

import {DeviceSettings} from './device_settings';

describe('Device Settings Component', () => {
  let fakeConfigService: FakeConfigService;

  beforeEach(async () => {
    fakeConfigService = new FakeConfigService();
    spyOn(fakeConfigService, 'checkDeviceWritePermission').and.returnValue(
      of({hasPermission: true, userName: 'test-user'}),
    );

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
      ],
    }).compileComponents();
  });

  it('should be created', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    dialogOpener.componentInstance.dialogRef.componentInstance.config =
      SCENARIO_IN_SERVICE_IDLE.config as DeviceConfig;
    dialogOpener.detectChanges();
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
    dialogOpener.detectChanges();

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

    spyOn(fakeConfigService, 'updateDeviceConfig');

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

    dialogOpener.detectChanges();

    const dialogElement = document.querySelector('mat-dialog-container');
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
    dialogOpener.detectChanges();

    component.activeSection.set(ConfigSection.DIMENSIONS);

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('secondary'));
    const dialog = TestBed.inject(MatDialog);
    const dialogSpy = spyOn(dialog, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    spyOn(fakeConfigService, 'updateDeviceConfig');

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
    dialogOpener.detectChanges();

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

    spyOn(fakeConfigService, 'updateDeviceConfig');

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
    dialogOpener.detectChanges();

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
    dialogOpener.detectChanges();

    const dialogElement = document.querySelector('mat-dialog-container');
    const saveButton = dialogElement!.querySelector(
      '.save-button',
    ) as HTMLButtonElement;
    const discardButton = dialogElement!.querySelector(
      '.discard-button',
    ) as HTMLButtonElement;

    expect(component.saving()).toBeTrue();
    expect(saveButton.disabled).toBeTrue();
    expect(discardButton.disabled).toBeTrue();

    updateConfigSubject.next({success: true});
    updateConfigSubject.complete();
    dialogOpener.detectChanges();

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
    dialogOpener.detectChanges();
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
    dialogOpener.detectChanges();
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
    dialogOpener.detectChanges();
    expect(component.uiStatus.permissions.editability?.editable).toBeTrue();
    expect(component.uiStatus.wifi.editability?.editable).toBeTrue();

    component.hasPermission.set(false);
    dialogOpener.detectChanges();
    expect(component.uiStatus.permissions.editability?.editable).toBeFalse();
    expect(component.uiStatus.wifi.editability?.editable).toBeFalse();
  });

  describe('setActiveSection', () => {
    it('should change section immediately if not dirty', () => {
      const dialogOpener = TestBed.createComponent(
        MatTestDialogOpener.withComponent(DeviceSettings, {
          data: {deviceId: 'test-device'},
        }),
      );
      const component =
        dialogOpener.componentInstance.dialogRef.componentInstance;
      dialogOpener.detectChanges();

      expect(component.activeSection()).toBe(ConfigSection.PERMISSIONS);

      const event = jasmine.createSpyObj('Event', ['preventDefault']);
      component.setActiveSection(event, ConfigSection.WIFI);

      expect(component.activeSection()).toBe(ConfigSection.WIFI);
      expect(event.preventDefault).toHaveBeenCalled();
    });

    it('should prompt ConfirmDialog if dirty and switch section on confirm', () => {
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
      dialogOpener.detectChanges();

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

    it('should prompt ConfirmDialog if dirty and stay on cancel', () => {
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
      dialogOpener.detectChanges();

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
  });

  it('should close dialog on reset', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device', universe: 'test-universe'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogOpener.detectChanges();

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
    dialogOpener.detectChanges();

    component.updatePermissions({owners: ['user1', 'user2']});
    expect(component.newConfig().permissions?.owners).toEqual([
      'user1',
      'user2',
    ]);

    component.discard();

    expect(component.newConfig().permissions?.owners).toEqual(['user1']);
  });

  describe('save (non-dimensions)', () => {
    it('should call API to save permissions', () => {
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
      dialogOpener.detectChanges();

      component.activeSection.set(ConfigSection.PERMISSIONS);
      component.updatePermissions({owners: ['user1', 'user2']});

      spyOn(fakeConfigService, 'updateDeviceConfig').and.returnValue(
        of({success: true}),
      );
      const dialog = TestBed.inject(MatDialog);
      const dialogSpy = spyOn(dialog, 'open').and.returnValue(
        jasmine.createSpyObj('MatDialogRef', ['afterClosed']),
      );

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
    });
  });

  it('should update wifi in newConfig', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceSettings, {
        data: {deviceId: 'test-device'},
      }),
    );
    const component =
      dialogOpener.componentInstance.dialogRef.componentInstance;
    dialogOpener.detectChanges();

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
    dialogOpener.detectChanges();

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
    dialogOpener.detectChanges();

    expect(component.activeSection()).toBe(ConfigSection.WIFI);
  });

  describe('self lockout and error handling', () => {
    it('should handle SELF_LOCKOUT_DETECTED error, prompt dialog, and retry on confirm', () => {
      const dialogOpener = TestBed.createComponent(
        MatTestDialogOpener.withComponent(DeviceSettings, {
          data: {deviceId: 'test-device'},
        }),
      );
      const component =
        dialogOpener.componentInstance.dialogRef.componentInstance;
      dialogOpener.detectChanges();

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

    it('should handle SELF_LOCKOUT_DETECTED error, prompt dialog, and switch to PERMISSIONS on cancel', () => {
      const dialogOpener = TestBed.createComponent(
        MatTestDialogOpener.withComponent(DeviceSettings, {
          data: {deviceId: 'test-device'},
        }),
      );
      const component =
        dialogOpener.componentInstance.dialogRef.componentInstance;
      dialogOpener.detectChanges();

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

    it('should show error dialog on other save failures', () => {
      const dialogOpener = TestBed.createComponent(
        MatTestDialogOpener.withComponent(DeviceSettings, {
          data: {deviceId: 'test-device'},
        }),
      );
      const component =
        dialogOpener.componentInstance.dialogRef.componentInstance;
      dialogOpener.detectChanges();

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

    it('should close dialog on successful self-lockout save', () => {
      const dialogOpener = TestBed.createComponent(
        MatTestDialogOpener.withComponent(DeviceSettings, {
          data: {deviceId: 'test-device'},
        }),
      );
      const component =
        dialogOpener.componentInstance.dialogRef.componentInstance;
      dialogOpener.detectChanges();

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

      expect(
        dialogOpener.componentInstance.dialogRef.close,
      ).toHaveBeenCalledWith(true);
    });

    it('should save permissions and NOT prompt dimensions warning even if there are empty dimensions', () => {
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
      dialogOpener.detectChanges();

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
  });
});
