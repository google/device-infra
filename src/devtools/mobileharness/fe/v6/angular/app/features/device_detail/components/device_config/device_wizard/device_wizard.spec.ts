import {TestBed} from '@angular/core/testing';
import {MatDialog, MatDialogModule} from '@angular/material/dialog';
import {MatTestDialogOpener} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {FakeConfigService} from '../../../../../core/services/config/fake_config_service';
import {DEVICE_SERVICE} from '../../../../../core/services/device/device_service';
import {FakeDeviceService} from '../../../../../core/services/device/fake_device_service';
import {Environment} from '../../../../../core/services/environment';

import {DeviceWizard} from './device_wizard';

describe('Device Wizard Component', () => {
  let mockEnvironment: jasmine.SpyObj<Environment>;

  beforeEach(async () => {
    mockEnvironment = jasmine.createSpyObj('Environment', ['isGoogleInternal']);
    mockEnvironment.isGoogleInternal.and.returnValue(true);

    await TestBed.configureTestingModule({
      imports: [
        DeviceWizard,
        NoopAnimationsModule, // This makes test faster and more stable.
        MatDialogModule,
      ],
      providers: [
        provideRouter([]),
        {provide: DEVICE_SERVICE, useClass: FakeDeviceService},
        {provide: CONFIG_SERVICE, useClass: FakeConfigService},
        {provide: Environment, useValue: mockEnvironment},
      ],
    }).compileComponents();
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceWizard, {
        data: {deviceId: 'test-id', source: 'new'},
      }),
    );
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should default settings when copying config with empty settings', async () => {
    const fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceWizard, {
        data: {
          deviceId: 'test-id',
          source: 'copy',
          config: {
            permissions: {owners: ['owner1']},
            dimensions: {supported: [{name: 'dim1', value: 'val1'}]},
            settings: {},
          },
        },
      }),
    );
    fixture.detectChanges();
    await fixture.whenStable();

    const opener =
      fixture.componentInstance as MatTestDialogOpener<DeviceWizard>;
    const component = opener.dialogRef.componentInstance;

    expect(component).toBeTruthy();
    expect(component.config.settings?.maxConsecutiveFail).toBe(5);
    expect(component.config.settings?.maxConsecutiveTest).toBe(10000);

    const maxFailRow = component.dataSource.find(
      (row) => row.feature === 'Max Consecutive Failures',
    );
    const maxTestRow = component.dataSource.find(
      (row) => row.feature === 'Max Tests between Reboots',
    );

    expect(maxFailRow).toBeTruthy();
    expect(maxFailRow?.value).toBe(5);
    expect(maxTestRow).toBeTruthy();
    expect(maxTestRow?.value).toBe(10000);
  });

  it('should default settings when copying config with missing settings', async () => {
    const fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceWizard, {
        data: {
          deviceId: 'test-id',
          source: 'copy',
          config: {
            permissions: {owners: ['owner1']},
            dimensions: {supported: [{name: 'dim1', value: 'val1'}]},
          },
        },
      }),
    );
    fixture.detectChanges();
    await fixture.whenStable();

    const opener =
      fixture.componentInstance as MatTestDialogOpener<DeviceWizard>;
    const component = opener.dialogRef.componentInstance;

    expect(component).toBeTruthy();
    expect(component.config.settings?.maxConsecutiveFail).toBe(5);
    expect(component.config.settings?.maxConsecutiveTest).toBe(10000);

    const maxFailRow = component.dataSource.find(
      (row) => row.feature === 'Max Consecutive Failures',
    );
    const maxTestRow = component.dataSource.find(
      (row) => row.feature === 'Max Tests between Reboots',
    );

    expect(maxFailRow).toBeTruthy();
    expect(maxFailRow?.value).toBe(5);
    expect(maxTestRow).toBeTruthy();
    expect(maxTestRow?.value).toBe(10000);
  });

  it('should default settings when copying config with null config', async () => {
    const fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceWizard, {
        data: {
          deviceId: 'test-id',
          source: 'copy',
          config: null,
        },
      }),
    );
    fixture.detectChanges();
    await fixture.whenStable();

    const opener =
      fixture.componentInstance as MatTestDialogOpener<DeviceWizard>;
    const component = opener.dialogRef.componentInstance;

    expect(component).toBeTruthy();
    expect(component.config.settings?.maxConsecutiveFail).toBe(5);
    expect(component.config.settings?.maxConsecutiveTest).toBe(10000);

    const maxFailRow = component.dataSource.find(
      (row) => row.feature === 'Max Consecutive Failures',
    );
    const maxTestRow = component.dataSource.find(
      (row) => row.feature === 'Max Tests between Reboots',
    );

    expect(maxFailRow).toBeTruthy();
    expect(maxFailRow?.value).toBe(5);
    expect(maxTestRow).toBeTruthy();
    expect(maxTestRow?.value).toBe(10000);
  });

  it('should only show wifi and dimensions when isGoogleInternal is false', async () => {
    mockEnvironment.isGoogleInternal.and.returnValue(false);
    const fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceWizard, {
        data: {
          deviceId: 'test-id',
          source: 'copy',
          config: {
            permissions: {owners: ['owner1']},
            dimensions: {supported: [{name: 'dim1', value: 'val1'}]},
          },
        },
      }),
    );
    fixture.detectChanges();
    await fixture.whenStable();

    const opener =
      fixture.componentInstance as MatTestDialogOpener<DeviceWizard>;
    const component = opener.dialogRef.componentInstance;

    expect(component).toBeTruthy();

    const ownerRow = component.dataSource.find(
      (row) => row.feature === 'Owners',
    );
    const executorRow = component.dataSource.find(
      (row) => row.feature === 'Executors',
    );
    const maxFailRow = component.dataSource.find(
      (row) => row.feature === 'Max Consecutive Failures',
    );

    expect(ownerRow).toBeFalsy();
    expect(executorRow).toBeFalsy();
    expect(maxFailRow).toBeFalsy();

    const ssidRow = component.dataSource.find((row) => row.feature === 'SSID');
    const dimRow = component.dataSource.find(
      (row) => row.feature === 'Supported Dimensions',
    );
    expect(ssidRow).toBeTruthy();
    expect(dimRow).toBeTruthy();
  });

  it('should show owners and executors in review table when isGoogleInternal is true', async () => {
    mockEnvironment.isGoogleInternal.and.returnValue(true);
    const fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceWizard, {
        data: {
          deviceId: 'test-id',
          source: 'copy',
          config: {
            permissions: {owners: ['owner1'], executors: ['executor1']},
            dimensions: {supported: [{name: 'dim1', value: 'val1'}]},
          },
        },
      }),
    );
    fixture.detectChanges();
    await fixture.whenStable();

    const opener =
      fixture.componentInstance as MatTestDialogOpener<DeviceWizard>;
    const component = opener.dialogRef.componentInstance;

    expect(component).toBeTruthy();

    const ownerRow = component.dataSource.find(
      (row) => row.feature === 'Owners',
    );
    const executorRow = component.dataSource.find(
      (row) => row.feature === 'Executors',
    );
    expect(ownerRow).toBeTruthy();
    expect(ownerRow?.value).toContain('owner1');
    expect(executorRow).toBeTruthy();
    expect(executorRow?.value).toContain('executor1');
  });

  it('should not duplicate review table rows when covertToReviewTable is called multiple times', async () => {
    const fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceWizard, {
        data: {
          deviceId: 'test-id',
          source: 'copy',
          config: {
            wifi: {type: 'custom', ssid: 'test-wifi', psk: '', scanSsid: false},
          },
        },
      }),
    );
    fixture.detectChanges();
    await fixture.whenStable();

    const opener =
      fixture.componentInstance as MatTestDialogOpener<DeviceWizard>;
    const component = opener.dialogRef.componentInstance;

    expect(component).toBeTruthy();

    component.covertToReviewTable();
    const countFirstRun = component.dataSource.length;

    component.covertToReviewTable();
    expect(component.dataSource.length).toBe(countFirstRun);
  });

  it('should clean up wifi settings when wifi type is none on submit', async () => {
    const fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceWizard, {
        data: {
          deviceId: 'test-id',
          source: 'copy',
          config: {
            permissions: {owners: ['owner1']},
            wifi: {type: 'none', ssid: 'dummy'},
          },
        },
      }),
    );
    fixture.detectChanges();
    await fixture.whenStable();

    const opener =
      fixture.componentInstance as MatTestDialogOpener<DeviceWizard>;
    const component = opener.dialogRef.componentInstance;

    const configService = TestBed.inject(CONFIG_SERVICE);
    const updateSpy = spyOn(
      configService,
      'updateDeviceConfig',
    ).and.returnValue(of({success: true, deviceConfig: {}, uiStatus: {}}));

    component.submit();

    expect(updateSpy).toHaveBeenCalled();
    const updateRequest = updateSpy.calls.mostRecent().args[0];
    expect(updateRequest.config.wifi).toBeUndefined();
  });

  it('should close dialog ref on successful config submit in copy flow', async () => {
    const fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceWizard, {
        data: {
          deviceId: 'test-id',
          source: 'copy',
          config: {
            permissions: {owners: ['owner1']},
          },
        },
      }),
    );
    fixture.detectChanges();
    await fixture.whenStable();

    const opener =
      fixture.componentInstance as MatTestDialogOpener<DeviceWizard>;
    const component = opener.dialogRef.componentInstance;

    const configService = TestBed.inject(CONFIG_SERVICE);
    spyOn(configService, 'updateDeviceConfig').and.returnValue(
      of({success: true, deviceConfig: {}, uiStatus: {}}),
    );

    const dialog = TestBed.inject(MatDialog);
    const successDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    successDialogRefSpy.afterClosed.and.returnValue(of(true));
    spyOn(dialog, 'open').and.returnValue(successDialogRefSpy);

    const wizardDialogRef = opener.dialogRef;
    spyOn(wizardDialogRef, 'close');

    component.submit();

    expect(wizardDialogRef.close).toHaveBeenCalledWith(true);
  });

  it('should return empty steps when templates are not resolved', () => {
    const fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceWizard, {
        data: {deviceId: 'test-id', source: 'new'},
      }),
    );
    const component = fixture.componentInstance.dialogRef.componentInstance;
    // detectChanges is not called yet, so viewChild templates are undefined.
    expect(component.WIZARD_STEPS_NEW()).toEqual([]);
  });

  it('should redirect currentStep to permissions when self lockout warning is cancelled', async () => {
    const fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceWizard, {
        data: {
          deviceId: 'test-id',
          source: 'new',
        },
      }),
    );
    fixture.detectChanges();
    await fixture.whenStable();

    const component = fixture.componentInstance.dialogRef.componentInstance;
    const configService = TestBed.inject(CONFIG_SERVICE);

    spyOn(configService, 'updateDeviceConfig').and.returnValue(
      of({
        success: false,
        error: {code: 'SELF_LOCKOUT_DETECTED'},
        deviceConfig: {},
        uiStatus: {},
      }),
    );

    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('secondary')); // clicks "Go Back"
    const dialogSpy = spyOn(MatDialog.prototype, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    component.currentStep.set('review-and-submit');
    component.config.permissions = {owners: ['user1'], executors: []};
    component.submit();

    expect(dialogSpy).toHaveBeenCalled();
    expect(component.currentStep()).toBe('permissions');
  });

  it('should retry submit with override self lockout set when proceeds anyway', async () => {
    const fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceWizard, {
        data: {
          deviceId: 'test-id',
          source: 'new',
        },
      }),
    );
    fixture.detectChanges();
    await fixture.whenStable();

    const component = fixture.componentInstance.dialogRef.componentInstance;
    const configService = TestBed.inject(CONFIG_SERVICE);

    let callCount = 0;
    const updateSpy = spyOn(configService, 'updateDeviceConfig').and.callFake(
      () => {
        callCount++;
        if (callCount === 1) {
          return of({
            success: false,
            error: {code: 'SELF_LOCKOUT_DETECTED'},
            deviceConfig: {},
            uiStatus: {},
          });
        }
        return of({success: true, deviceConfig: {}, uiStatus: {}});
      },
    );

    const dialog = TestBed.inject(MatDialog);
    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('primary')); // Proceed anyway
    spyOn(dialog, 'open').and.returnValue(confirmDialogRefSpy);

    component.config.permissions = {owners: ['user1'], executors: []};
    component.submit();

    expect(updateSpy).toHaveBeenCalledTimes(2);
    const firstCallArgs = updateSpy.calls.first().args[0];
    const secondCallArgs = updateSpy.calls.mostRecent().args[0];
    expect(firstCallArgs.options?.overrideSelfLockout).toBeFalse();
    expect(secondCallArgs.options?.overrideSelfLockout).toBeTrue();
  });

  it('should return to permissions step when canceling empty owner warning', async () => {
    const fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceWizard, {
        data: {
          deviceId: 'test-id',
          source: 'new',
        },
      }),
    );
    fixture.detectChanges();
    await fixture.whenStable();

    const component = fixture.componentInstance.dialogRef.componentInstance;
    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('secondary')); // clicks "Go Back"
    const dialogSpy = spyOn(MatDialog.prototype, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    component.currentStep.set('review-and-submit');
    component.config.permissions = {owners: [], executors: []};
    component.submit();

    expect(dialogSpy).toHaveBeenCalled();
    expect(component.currentStep()).toBe('permissions');
  });

  it('should retry submit with override self lockout set when empty owner warning is confirmed', async () => {
    const fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(DeviceWizard, {
        data: {
          deviceId: 'test-id',
          source: 'new',
        },
      }),
    );
    fixture.detectChanges();
    await fixture.whenStable();

    const component = fixture.componentInstance.dialogRef.componentInstance;
    const configService = TestBed.inject(CONFIG_SERVICE);
    const updateSpy = spyOn(
      configService,
      'updateDeviceConfig',
    ).and.returnValue(of({success: true, deviceConfig: {}, uiStatus: {}}));

    const dialog = TestBed.inject(MatDialog);
    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('primary')); // Proceed anyway
    const dialogSpy = spyOn(dialog, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    component.config.permissions = {owners: [], executors: []};
    component.submit();

    expect(dialogSpy).toHaveBeenCalledTimes(2);
    expect(updateSpy).toHaveBeenCalledTimes(1);
    const callArgs = updateSpy.calls.first().args[0];
    expect(callArgs.options?.overrideSelfLockout).toBeTrue();
  });
});
