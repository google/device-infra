import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialogRef} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of, throwError} from 'rxjs';

import {GetBatchWifiContextResponse} from '../../../../../../core/models/device_config_models';
import {
  CONFIG_SERVICE,
  ConfigService,
} from '../../../../../../core/services/config/config_service';
import {
  BulkConfigWifiDialog,
  BulkConfigWifiDialogData,
  getWifiConfigKey,
} from './bulk_config_wifi_dialog';

describe('BulkConfigWifiDialog', () => {
  let fixture: ComponentFixture<MatTestDialogOpener<BulkConfigWifiDialog>>;
  let component: BulkConfigWifiDialog;
  let mockConfigService: jasmine.SpyObj<ConfigService>;
  let mockDialogRef: MatDialogRef<BulkConfigWifiDialog>;

  const mockDialogData: BulkConfigWifiDialogData = {
    deviceIds: ['device-1', 'device-2', 'device-3'],
  };

  const mockBatchWifiResponse: GetBatchWifiContextResponse = {
    current: [
      {
        deviceId: 'device-1',
        wifi: {type: 'custom', ssid: 'lab-guest-5g', psk: '', scanSsid: false},
        writable: true,
      },
      {
        deviceId: 'device-2',
        wifi: {type: 'custom', ssid: 'lab-guest-5g', psk: '', scanSsid: false},
        writable: false,
        unwritableReason: 'Managed by host shared config (update host config instead)',
      },
      {
        deviceId: 'device-3',
        wifi: {type: 'custom', ssid: 'other-device-wifi', psk: '', scanSsid: false},
        writable: true,
      },
    ],
    candidateWifis: [
      {ssid: 'lab-guest-5g', scanSsid: false, deviceCount: 1200},
      {ssid: 'candidate-net-2', scanSsid: true, deviceCount: 350},
    ],
  };

  beforeEach(async () => {
    mockConfigService = jasmine.createSpyObj<ConfigService>('ConfigService', [
      'getBatchWifiContext',
      'batchUpdateDeviceConfig',
    ]);
    mockConfigService.getBatchWifiContext.and.returnValue(
      of(mockBatchWifiResponse),
    );
    mockConfigService.batchUpdateDeviceConfig.and.returnValue(
      of({errors: {}}),
    );
    await TestBed.configureTestingModule({
      imports: [
        BulkConfigWifiDialog,
        NoopAnimationsModule,
        MatTestDialogOpenerModule,
      ],
      providers: [
        {provide: CONFIG_SERVICE, useValue: mockConfigService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(BulkConfigWifiDialog, {
        data: mockDialogData,
      }),
    );
    component = fixture.componentInstance.dialogRef.componentInstance;
    mockDialogRef = fixture.componentInstance.dialogRef;
    spyOn(mockDialogRef, 'close');
    fixture.detectChanges();
  });

  it('should initialize and load batch Wi-Fi context', () => {
    expect(mockConfigService.getBatchWifiContext).toHaveBeenCalledWith([
      'device-1',
      'device-2',
      'device-3',
    ]);
    expect(component.isLoading()).toBeFalse();
    expect(component.deviceItems().length).toBe(3);
    expect(component.candidateWifis().length).toBe(2);
  });

  it('should compute candidate sources directly using backend candidateWifis and deviceCount', () => {
    const sources = component.candidateSources();
    expect(sources.length).toBe(2);

    const guestNet = sources.find((s) => s.ssid === 'lab-guest-5g');
    expect(guestNet).toBeDefined();
    expect(guestNet?.deviceCount).toBe(1200);

    const candNet2 = sources.find((s) => s.ssid === 'candidate-net-2');
    expect(candNet2).toBeDefined();
    expect(candNet2?.deviceCount).toBe(350);

    // Follows Google3 BFF best practices: does not synthesize candidate sources from current devices
    expect(sources.some((s) => s.ssid === 'other-device-wifi')).toBeFalse();
  });

  it('should filter candidate sources based on search filter', () => {
    component.searchFilter.set('candidate');
    const filtered = component.filteredSources();
    expect(filtered.some((s) => s.ssid === 'candidate-net-2')).toBeTrue();
    expect(filtered.some((s) => s.ssid === 'lab-guest-5g')).toBeFalse();
  });

  it('should select candidate source and populate target form when selectSource is called', () => {
    const candidate = component
      .candidateSources()
      .find((s) => s.ssid === 'candidate-net-2')!;
    component.selectSource(candidate);

    expect(component.targetSsid()).toBe('candidate-net-2');
    expect(component.targetScanSsid()).toBeTrue();
    expect(component.canGoNext()).toBeTrue();
  });

  it('should generate wifi config key using getWifiConfigKey', () => {
    expect(getWifiConfigKey('guest', false)).toBe('guest:::broadcast');
    expect(getWifiConfigKey('corp-hidden', true)).toBe('corp-hidden:::hidden');
  });

  it('should correctly determine isSourceActive', () => {
    const candidate = component
      .candidateSources()
      .find((s) => s.ssid === 'candidate-net-2')!;
    expect(component.isSourceActive(candidate)).toBeFalse();

    component.selectSource(candidate);
    expect(component.isSourceActive(candidate)).toBeTrue();
  });

  it('should toggle password visibility', () => {
    expect(component.showPassword()).toBeFalse();
    component.togglePasswordVisibility();
    expect(component.showPassword()).toBeTrue();
    component.togglePasswordVisibility();
    expect(component.showPassword()).toBeFalse();
  });

  it('should render app-dialog in template', () => {
    fixture.detectChanges();
    const appDialog = document.querySelector('app-dialog');
    expect(appDialog).toBeTruthy();
  });

  it('should render footer action buttons with common dialog-button styles', () => {
    fixture.detectChanges();
    const cancelButton = document.querySelector('.dialog-button-secondary');
    const nextButton = document.querySelector('.dialog-button-primary');
    expect(cancelButton).toBeTruthy();
    expect(nextButton).toBeTruthy();
    expect(cancelButton?.textContent?.trim()).toBe('Cancel');
    expect(nextButton?.textContent?.trim()).toBe('Next');
  });

  it('should navigate to Step 2 when goToStep2 is called with valid SSID', () => {
    expect(component.headline).toBe('Configure Wi-Fi');
    expect(component.supportText()).toBe('Step 1 of 2 · Choose a network for 3 devices');

    component.targetSsid.set('my-new-wifi');
    component.goToStep2();
    expect(component.step()).toBe(2);
    expect(component.headline).toBe('Configure Wi-Fi');
    expect(component.supportText()).toBe('Step 2 of 2 · Review and apply');

    component.backToStep1();
    expect(component.step()).toBe(1);
    expect(component.headline).toBe('Configure Wi-Fi');
    expect(component.supportText()).toBe('Step 1 of 2 · Choose a network for 3 devices');
  });

  it('should compute review rows with correct writability without client-side diffing', () => {
    const rows = component.reviewRows();
    expect(rows.length).toBe(3);

    const dev1 = rows.find((r) => r.deviceId === 'device-1')!;
    expect(dev1.writable).toBeTrue();

    const dev2 = rows.find((r) => r.deviceId === 'device-2')!;
    expect(dev2.writable).toBeFalse();
    expect(dev2.unwritableReason).toContain('Managed by host shared config');

    const dev3 = rows.find((r) => r.deviceId === 'device-3')!;
    expect(dev3.writable).toBeTrue();

    expect(component.writableRows().length).toBe(2);
    expect(component.writableCount()).toBe(2);
    expect(component.skippedCount()).toBe(1);
    expect(component.hasWritableDevices()).toBeTrue();
  });

  it('should render review table showing Will apply for writable devices and Skipped for unwritable devices', () => {
    component.targetSsid.set('target-office-wifi');
    component.goToStep2();
    fixture.detectChanges();

    const tableRows = document.querySelectorAll('tbody tr');
    expect(tableRows.length).toBe(3);

    // dev1 (writable): Status shows 'Will apply'
    expect(tableRows[0].textContent).toContain('Will apply');
    expect(tableRows[0].textContent).toContain('target-office-wifi');

    // dev2 (unwritable): Status shows 'Skipped', New Wi-Fi shows unwritable reason
    expect(tableRows[1].textContent).toContain('Skipped');
    expect(tableRows[1].textContent).toContain('Managed by host shared config');

    // dev3 (writable): Status shows 'Will apply'
    expect(tableRows[2].textContent).toContain('Will apply');
  });

  it('should apply batch update to only writable devices and transition to success on no errors', () => {
    component.targetSsid.set('office-wifi');
    component.targetPsk.set('secure123');
    component.targetScanSsid.set(false);

    component.apply();

    expect(mockConfigService.batchUpdateDeviceConfig).toHaveBeenCalledWith({
      deviceIds: ['device-1', 'device-3'],
      wifi: {
        ssid: 'office-wifi',
        psk: 'secure123',
        scanSsid: false,
        type: 'custom',
      },
    });

    expect(component.applyResult()).toBe('success');

    component.done();
    expect(mockDialogRef.close).toHaveBeenCalledWith({updated: true});
  });

  it('should show error screen when backend returns partial or complete errors', () => {
    mockConfigService.batchUpdateDeviceConfig.and.returnValue(
      of({
        errors: {
          'device-1': {
            code: 'FAILED_PRECONDITION',
            message: 'Device offline',
          },
        },
      }),
    );

    component.targetSsid.set('office-wifi');
    component.apply();

    expect(component.applyResult()).toBe('errors');
    expect(component.applyErrors().length).toBe(1);
    expect(component.applyErrors()[0].deviceId).toBe('device-1');
    expect(component.applyErrors()[0].reason).toBe('Device offline');

    component.close();
    expect(mockDialogRef.close).toHaveBeenCalled();
  });

  it('should handle backend load error gracefully', () => {
    mockConfigService.getBatchWifiContext.and.returnValue(
      throwError(() => new Error('Network timeout')),
    );

    component.loadBatchWifiContext();
    expect(component.loadError()).toBe('Network timeout');
    expect(component.isLoading()).toBeFalse();
  });
});
