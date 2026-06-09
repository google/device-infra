import {TestBed} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';
import {MatTestDialogOpener} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

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
});
