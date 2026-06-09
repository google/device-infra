import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {Environment} from '../../../../../core/services/environment';
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
});
