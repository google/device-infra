import {ComponentFixture, TestBed} from '@angular/core/testing';
import {
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {Environment} from '../../../../../core/services/environment';
import {ConfirmDialog} from '../../../../../shared/components/confirm_dialog/confirm_dialog';
import {HostWizard} from './host_wizard';

describe('HostWizard Component', () => {
  let mockEnvironment: jasmine.SpyObj<Environment>;

  beforeEach(async () => {
    mockEnvironment = jasmine.createSpyObj('Environment', ['isGoogleInternal']);
    mockEnvironment.isGoogleInternal.and.returnValue(true);

    await TestBed.configureTestingModule({
      imports: [
        HostWizard,
        NoopAnimationsModule, // This makes test faster and more stable.
        MatDialogModule,
        MatTestDialogOpenerModule,
      ],
      providers: [
        provideRouter([]),
        {
          provide: CONFIG_SERVICE,
          useValue: {
            updateHostConfig: () => of({success: true}),
            checkHostWritePermission: () => of({hasPermission: true}),
            checkDeviceWritePermission: () => of({hasPermission: true}),
          },
        },
        {
          provide: Environment,
          useValue: mockEnvironment,
        },
      ],
    }).compileComponents();
  });

  it('should be created', async () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostWizard, {
        data: {hostName: 'test-host', source: 'new'},
      }),
    ) as ComponentFixture<MatTestDialogOpener<HostWizard>>;
    dialogOpener.detectChanges();
    await dialogOpener.whenStable();
    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    expect(comp).toBeTruthy();
  });

  it('should show all permissions and stability settings when isGoogleInternal is true', async () => {
    mockEnvironment.isGoogleInternal.and.returnValue(true);
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostWizard, {
        data: {
          hostName: 'test-host',
          source: 'copy',
          config: {
            permissions: {hostAdmins: ['admin']},
            deviceConfig: {
              permissions: {owners: ['owner']},
              wifi: {type: 'WPA', ssid: 'test-wifi'},
              dimensions: {
                supported: [{name: 'dim1', value: 'val1'}],
              },
            },
          },
        },
      }),
    ) as ComponentFixture<MatTestDialogOpener<HostWizard>>;
    dialogOpener.detectChanges();
    await dialogOpener.whenStable();
    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    expect(comp).toBeTruthy();

    const hostAdminRow = comp.dataSource.find(
      (row) => row.feature === 'Host Admins',
    );
    const deviceOwnerRow = comp.dataSource.find(
      (row) => row.feature === 'Device Owners',
    );
    const wifiRow = comp.dataSource.find((row) => row.feature === 'SSID');
    const dimensionsRow = comp.dataSource.find(
      (row) => row.feature === 'Supported Dimensions',
    );

    expect(hostAdminRow).toBeTruthy();
    expect(deviceOwnerRow).toBeTruthy();
    expect(wifiRow).toBeTruthy();
    expect(dimensionsRow).toBeTruthy();

    const maxFailuresRow = comp.dataSource.find(
      (row) => row.feature === 'Max Consecutive Failures',
    );
    const maxTestsRow = comp.dataSource.find(
      (row) => row.feature === 'Max Tests between Reboots',
    );
    expect(maxFailuresRow).toBeTruthy();
    expect(maxTestsRow).toBeTruthy();
  });

  it('should only show wifi and mode configurations when isGoogleInternal is false', async () => {
    mockEnvironment.isGoogleInternal.and.returnValue(false);
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostWizard, {
        data: {
          hostName: 'test-host',
          source: 'copy',
          config: {
            permissions: {hostAdmins: ['admin']},
            deviceConfig: {
              permissions: {owners: ['owner']},
              wifi: {type: 'WPA', ssid: 'test-wifi'},
              dimensions: {
                supported: [{name: 'dim1', value: 'val1'}],
              },
            },
          },
        },
      }),
    ) as ComponentFixture<MatTestDialogOpener<HostWizard>>;
    dialogOpener.detectChanges();
    await dialogOpener.whenStable();
    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    expect(comp).toBeTruthy();

    const hostAdminRow = comp.dataSource.find(
      (row) => row.feature === 'Host Admins',
    );
    const deviceOwnerRow = comp.dataSource.find(
      (row) => row.feature === 'Device Owners',
    );
    const wifiRow = comp.dataSource.find((row) => row.feature === 'SSID');
    const dimensionsRow = comp.dataSource.find(
      (row) => row.feature === 'Supported Dimensions',
    );

    expect(hostAdminRow).toBeFalsy();
    expect(deviceOwnerRow).toBeFalsy();
    expect(wifiRow).toBeTruthy();
    expect(dimensionsRow).toBeTruthy();

    const maxFailuresRow = comp.dataSource.find(
      (row) => row.feature === 'Max Consecutive Failures',
    );
    const maxTestsRow = comp.dataSource.find(
      (row) => row.feature === 'Max Tests between Reboots',
    );
    expect(maxFailuresRow).toBeFalsy();
    expect(maxTestsRow).toBeFalsy();
  });

  it('should cover empty permissions, type-none wifi, and SHARED device config mode in review table', async () => {
    mockEnvironment.isGoogleInternal.and.returnValue(true);
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostWizard, {
        data: {
          hostName: 'test-host',
          source: 'copy',
          config: {
            permissions: {hostAdmins: []},
            deviceConfigMode: 'SHARED',
            deviceConfig: {
              permissions: {owners: [], executors: []},
              wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
            },
          },
        },
      }),
    ) as ComponentFixture<MatTestDialogOpener<HostWizard>>;
    dialogOpener.detectChanges();
    await dialogOpener.whenStable();
    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    expect(comp).toBeTruthy();

    const hostAdminRow = comp.dataSource.find(
      (row) => row.feature === 'Host Admins',
    );
    const deviceOwnerRow = comp.dataSource.find(
      (row) => row.feature === 'Device Owners',
    );
    const wifiTypeRow = comp.dataSource.find((row) => row.feature === 'Type');
    const modeRow = comp.dataSource.find(
      (row) => row.feature === 'Device Config Mode' && row.type === 'data',
    );

    expect(hostAdminRow?.value).toBe('None');
    expect(deviceOwnerRow?.value).toBe('None');
    expect(wifiTypeRow?.value).toBe('None');
    expect(modeRow?.value).toBe('SHARED');
  });

  it('should cover review table generation directly and synchronously', async () => {
    mockEnvironment.isGoogleInternal.and.returnValue(true);
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostWizard, {
        data: {
          hostName: 'test-host',
          source: 'copy',
          config: {
            permissions: {hostAdmins: ['admin1']},
            deviceConfigMode: 'SHARED',
            deviceConfig: {
              permissions: {owners: ['owner1'], executors: []},
              wifi: {
                type: 'custom',
                ssid: 'test-ssid',
                psk: '',
                scanSsid: false,
              },
            },
          },
        },
      }),
    ) as ComponentFixture<MatTestDialogOpener<HostWizard>>;
    dialogOpener.detectChanges();
    await dialogOpener.whenStable();
    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    expect(comp).toBeTruthy();

    const hostAdminRow = comp.dataSource.find(
      (row) => row.feature === 'Host Admins',
    );
    const modeRow = comp.dataSource.find(
      (row) => row.feature === 'Device Config Mode' && row.type === 'data',
    );
    const ownerRow = comp.dataSource.find(
      (row) => row.feature === 'Device Owners',
    );
    const wifiRow = comp.dataSource.find((row) => row.feature === 'SSID');

    expect(hostAdminRow?.value).toBe('admin1');
    expect(modeRow?.value).toBe('SHARED');
    expect(ownerRow?.value).toBe('owner1');
    expect(wifiRow?.value).toBe('test-ssid');

    const maxFailuresRow = comp.dataSource.find(
      (row) => row.feature === 'Max Consecutive Failures',
    );
    const maxTestsRow = comp.dataSource.find(
      (row) => row.feature === 'Max Tests between Reboots',
    );
    expect(maxFailuresRow).toBeTruthy();
    expect(maxTestsRow).toBeTruthy();
  });

  it('should clean up wifi settings when wifi type is none on submit', async () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostWizard, {
        data: {
          hostName: 'test-host',
          source: 'copy',
          config: {
            permissions: {hostAdmins: ['admin']},
            deviceConfig: {
              wifi: {type: 'none', ssid: 'test'},
            },
          },
        },
      }),
    ) as ComponentFixture<MatTestDialogOpener<HostWizard>>;
    dialogOpener.detectChanges();
    await dialogOpener.whenStable();

    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    const configService = TestBed.inject(CONFIG_SERVICE);
    const updateSpy = spyOn(
      configService,
      'updateHostConfig',
    ).and.callThrough();

    comp.submit();

    expect(updateSpy).toHaveBeenCalled();
    const updateRequest = updateSpy.calls.mostRecent().args[0];
    expect(updateRequest.config.deviceConfig?.wifi).toBeUndefined();
  });

  it('should call updateHostConfig with copy scope when source is copy', async () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostWizard, {
        data: {
          hostName: 'test-host',
          source: 'copy',
          config: {
            permissions: {hostAdmins: ['admin']},
          },
        },
      }),
    ) as ComponentFixture<MatTestDialogOpener<HostWizard>>;
    dialogOpener.detectChanges();
    await dialogOpener.whenStable();

    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    const configService = TestBed.inject(CONFIG_SERVICE);
    const updateSpy = spyOn(
      configService,
      'updateHostConfig',
    ).and.callThrough();

    comp.submit();

    expect(updateSpy).toHaveBeenCalledWith(
      jasmine.objectContaining({
        scope: {
          updateMask: {
            paths: [
              'permissions',
              'device_config_mode',
              'device_config',
              'host_properties',
            ],
          },
        },
      }),
    );
  });

  it('should call updateHostConfig with new host scope when source is new', async () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostWizard, {
        data: {
          hostName: 'test-host',
          source: 'new',
          config: {
            permissions: {hostAdmins: ['admin']},
          },
        },
      }),
    ) as ComponentFixture<MatTestDialogOpener<HostWizard>>;
    dialogOpener.detectChanges();
    await dialogOpener.whenStable();

    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    const configService = TestBed.inject(CONFIG_SERVICE);
    const updateSpy = spyOn(
      configService,
      'updateHostConfig',
    ).and.callThrough();

    comp.submit();

    expect(updateSpy).toHaveBeenCalledWith(
      jasmine.objectContaining({
        scope: {
          updateMask: {
            paths: [
              'permissions',
              'device_config_mode',
              'device_config',
              'host_properties',
              'device_discovery',
            ],
          },
        },
      }),
    );
  });

  it('should call updateHostConfig with ATS scope when running in ATS env', async () => {
    mockEnvironment.isGoogleInternal.and.returnValue(false); // Mock ATS env

    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostWizard, {
        data: {
          hostName: 'test-host',
          source: 'new',
          config: {
            permissions: {hostAdmins: ['admin']},
          },
        },
      }),
    ) as ComponentFixture<MatTestDialogOpener<HostWizard>>;
    dialogOpener.detectChanges();
    await dialogOpener.whenStable();

    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    const configService = TestBed.inject(CONFIG_SERVICE);
    const updateSpy = spyOn(
      configService,
      'updateHostConfig',
    ).and.callThrough();

    comp.submit();

    expect(updateSpy).toHaveBeenCalledWith(
      jasmine.objectContaining({
        scope: {
          updateMask: {
            paths: ['device_config_mode', 'device_config'],
          },
        },
      }),
    );
  });

  it('should close dialog ref on successful config submit in copy flow', async () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostWizard, {
        data: {
          hostName: 'test-host',
          source: 'copy',
          config: {
            permissions: {hostAdmins: ['admin']},
          },
        },
      }),
    ) as ComponentFixture<MatTestDialogOpener<HostWizard>>;
    dialogOpener.detectChanges();
    await dialogOpener.whenStable();

    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    const configService = TestBed.inject(CONFIG_SERVICE);
    spyOn(configService, 'updateHostConfig').and.returnValue(
      of({success: true}),
    );

    const dialog = TestBed.inject(MatDialog);
    const successDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    successDialogRefSpy.afterClosed.and.returnValue(of(true));
    spyOn(dialog, 'open').and.returnValue(successDialogRefSpy);

    const wizardDialogRef = (
      comp as unknown as {dialogRef: MatDialogRef<HostWizard>}
    ).dialogRef;
    spyOn(wizardDialogRef, 'close');

    comp.submit();

    expect(wizardDialogRef.close).toHaveBeenCalledWith(true);
  });

  it('should show self lockout warning and retry with override when SELF_LOCKOUT_DETECTED is returned', async () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostWizard, {
        data: {
          hostName: 'test-host',
          source: 'copy',
          config: {
            permissions: {hostAdmins: ['admin']},
          },
        },
      }),
    ) as ComponentFixture<MatTestDialogOpener<HostWizard>>;
    dialogOpener.detectChanges();
    await dialogOpener.whenStable();

    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    const configService = TestBed.inject(CONFIG_SERVICE);

    let callCount = 0;
    const updateSpy = spyOn(configService, 'updateHostConfig').and.callFake(
      () => {
        callCount++;
        if (callCount === 1) {
          return of({
            success: false,
            error: {
              code: 'SELF_LOCKOUT_DETECTED',
              message: 'Self lockout detected',
            },
          });
        }
        return of({success: true});
      },
    );

    const dialog = TestBed.inject(MatDialog);
    const dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['afterClosed']);
    dialogRefSpy.afterClosed.and.returnValue(of('primary'));
    const dialogSpy = spyOn(dialog, 'open').and.returnValue(dialogRefSpy);

    comp.submit();

    expect(updateSpy).toHaveBeenCalledWith(
      jasmine.objectContaining({
        options: {overrideSelfLockout: false},
      }),
    );

    expect(dialogSpy).toHaveBeenCalledWith(
      ConfirmDialog,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          type: 'warning',
          primaryButtonLabel: 'Proceed Anyway',
        }),
      }),
    );

    expect(updateSpy).toHaveBeenCalledWith(
      jasmine.objectContaining({
        options: {overrideSelfLockout: true},
      }),
    );
  });

  it('should return empty steps when templates are not resolved', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostWizard, {
        data: {hostName: 'test-host', source: 'new'},
      }),
    ) as ComponentFixture<MatTestDialogOpener<HostWizard>>;
    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    expect(comp.WIZARD_STEPS()).toEqual([]);
  });

  it('should redirect currentStep to host-permissions when self lockout warning is cancelled', async () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostWizard, {
        data: {
          hostName: 'test-host',
          source: 'new',
        },
      }),
    ) as ComponentFixture<MatTestDialogOpener<HostWizard>>;
    dialogOpener.detectChanges();
    await dialogOpener.whenStable();

    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    const configService = TestBed.inject(CONFIG_SERVICE);

    spyOn(configService, 'updateHostConfig').and.returnValue(
      of({
        success: false,
        error: {code: 'SELF_LOCKOUT_DETECTED'},
        uiStatus: {},
      }),
    );

    const dialog = TestBed.inject(MatDialog);
    const confirmDialogRefSpy = jasmine.createSpyObj('MatDialogRef', [
      'afterClosed',
    ]);
    confirmDialogRefSpy.afterClosed.and.returnValue(of('secondary')); // clicks "Go Back"
    const dialogSpy = spyOn(dialog, 'open').and.returnValue(
      confirmDialogRefSpy,
    );

    comp.currentStep.set('review-and-submit');
    comp.submit();

    expect(dialogSpy).toHaveBeenCalled();
    expect(comp.currentStep()).toBe('host-permissions');
  });

  it('should update currentStep on step change', async () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostWizard, {
        data: {hostName: 'test-host', source: 'new'},
      }),
    ) as ComponentFixture<MatTestDialogOpener<HostWizard>>;
    dialogOpener.detectChanges();
    await dialogOpener.whenStable();

    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    comp.onCurrentStepChange('device-config-mode');
    expect(comp.currentStep()).toBe('device-config-mode');
  });
});
