import {OverlayContainer} from '@angular/cdk/overlay';
import {ApplicationRef} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialogRef} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of, throwError} from 'rxjs';

import {
  BatchGetDeviceWifiConfigsResponse,
  GetWifiSuggestionsResponse,
} from '../../../../../../core/models/device_config_models';
import {Fleet} from '../../../../../../core/models/search';
import {
  CONFIG_SERVICE,
  ConfigService,
} from '../../../../../../core/services/config/config_service';
import {BulkConfigWifiDialogData} from '../../../../models';
import {BulkConfigWifiDialog} from './bulk_config_wifi_dialog';

describe('BulkConfigWifiDialog', () => {
  let fixture: ComponentFixture<MatTestDialogOpener<BulkConfigWifiDialog>>;
  let component: BulkConfigWifiDialog;
  let mockConfigService: jasmine.SpyObj<ConfigService>;
  let mockDialogRef: MatDialogRef<BulkConfigWifiDialog>;
  let overlayContainer: OverlayContainer;
  let overlayElement: HTMLElement;
  let appRef: ApplicationRef;

  function detectChanges() {
    fixture.detectChanges();
    appRef.tick();
  }

  const mockDialogData: BulkConfigWifiDialogData = {
    deviceIds: ['device-1', 'device-2', 'device-3'],
    fleet: Fleet.FLEET_ATS,
  };

  const mockBatchDevicesResponse: BatchGetDeviceWifiConfigsResponse = {
    devices: [
      {
        deviceId: 'device-1',
        wifi: {type: 'custom', ssid: 'lab-guest-5g', psk: '', scanSsid: false},
        writable: true,
      },
      {
        deviceId: 'device-2',
        wifi: {type: 'custom', ssid: 'lab-guest-5g', psk: '', scanSsid: false},
        writable: false,
        unwritableReason:
          'Managed by host shared config (update host config instead)',
      },
      {
        deviceId: 'device-3',
        wifi: {
          type: 'custom',
          ssid: 'other-device-wifi',
          psk: '',
          scanSsid: false,
        },
        writable: true,
      },
    ],
  };

  const mockWifiSuggestionsResponse: GetWifiSuggestionsResponse = {
    candidateWifis: [
      {
        ssid: 'lab-guest-5g',
        scanSsid: false,
        fleetInUse: {deviceCount: 1200},
      },
      {
        ssid: 'candidate-net-2',
        scanSsid: true,
        fleetInUse: {deviceCount: 350},
      },
    ],
  };

  beforeEach(async () => {
    mockConfigService = jasmine.createSpyObj<ConfigService>('ConfigService', [
      'batchGetDeviceWifiConfigs',
      'getWifiSuggestions',
      'batchUpdateDeviceWifiConfigs',
    ]);
    mockConfigService.batchGetDeviceWifiConfigs.and.returnValue(
      of(mockBatchDevicesResponse),
    );
    mockConfigService.getWifiSuggestions.and.returnValue(
      of(mockWifiSuggestionsResponse),
    );
    mockConfigService.batchUpdateDeviceWifiConfigs.and.returnValue(
      of({errors: {}}),
    );
    await TestBed.configureTestingModule({
      imports: [
        BulkConfigWifiDialog,
        NoopAnimationsModule,
        MatTestDialogOpenerModule,
      ],
      providers: [{provide: CONFIG_SERVICE, useValue: mockConfigService}],
    }).compileComponents();

    overlayContainer = TestBed.inject(OverlayContainer);
    overlayElement = overlayContainer.getContainerElement();
    overlayElement.innerHTML = '';

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(BulkConfigWifiDialog, {
        data: mockDialogData,
      }),
    );
    component = fixture.componentInstance.dialogRef.componentInstance;
    mockDialogRef = fixture.componentInstance.dialogRef;
    spyOn(mockDialogRef, 'close');
    appRef = TestBed.inject(ApplicationRef);
    detectChanges();
  });

  afterEach(() => {
    overlayElement.innerHTML = '';
  });

  it('should initialize and load Wi-Fi suggestions in Step 1 without calling batchGetDeviceWifiConfigs', () => {
    expect(mockConfigService.getWifiSuggestions).toHaveBeenCalledWith({
      fleet: Fleet.FLEET_ATS,
    });
    expect(mockConfigService.batchGetDeviceWifiConfigs).not.toHaveBeenCalled();
    expect(component.isLoading()).toBeFalse();
    expect(component.deviceItems().length).toBe(0);
    expect(component.candidateWifis().length).toBe(2);
  });

  it('should call batchGetDeviceWifiConfigs when moving to Step 2 and reuse cached data on Back -> Next', () => {
    expect(mockConfigService.batchGetDeviceWifiConfigs).not.toHaveBeenCalled();
    component.updateTargetWifi({ssid: 'target-office-wifi'});
    component.goToStep2();
    detectChanges();

    expect(mockConfigService.batchGetDeviceWifiConfigs).toHaveBeenCalledTimes(
      1,
    );
    expect(mockConfigService.batchGetDeviceWifiConfigs).toHaveBeenCalledWith({
      deviceIds: ['device-1', 'device-2', 'device-3'],
    });
    expect(component.step()).toBe(2);
    expect(component.isLoading()).toBeFalse();
    expect(component.deviceItems().length).toBe(3);

    // Going back to Step 1 and returning to Step 2 should reuse loaded configs without another RPC
    component.backToStep1();
    detectChanges();
    component.updateTargetWifi({ssid: 'updated-office-wifi'});
    component.goToStep2();
    detectChanges();

    expect(mockConfigService.batchGetDeviceWifiConfigs).toHaveBeenCalledTimes(
      1,
    );
    expect(component.deviceItems().length).toBe(3);
  });

  it('should compute candidate sources directly using backend candidateWifis and fleetInUse', () => {
    const sources = component.filteredSources();
    expect(sources.length).toBe(2);

    const guestNet = sources.find((s) => s.ssid === 'lab-guest-5g');
    expect(guestNet).toBeDefined();
    expect(guestNet?.fleetInUse?.deviceCount).toBe(1200);

    const candNet2 = sources.find((s) => s.ssid === 'candidate-net-2');
    expect(candNet2).toBeDefined();
    expect(candNet2?.fleetInUse?.deviceCount).toBe(350);

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
      .filteredSources()
      .find((s) => s.ssid === 'candidate-net-2')!;
    component.selectSource(candidate);

    expect(component.targetWifi()).toEqual({
      ssid: 'candidate-net-2',
      psk: '',
      scanSsid: true,
    });
    expect(component.canGoNext()).toBeTrue();
  });

  it('should correctly determine isWifiMatchingTarget', () => {
    const candidate = component
      .filteredSources()
      .find((s) => s.ssid === 'candidate-net-2')!;
    expect(component.isWifiMatchingTarget(candidate)).toBeFalse();

    component.selectSource(candidate);
    expect(component.isWifiMatchingTarget(candidate)).toBeTrue();
  });

  it('should toggle password visibility', () => {
    expect(component.showPassword()).toBeFalse();
    component.togglePasswordVisibility();
    expect(component.showPassword()).toBeTrue();
    component.togglePasswordVisibility();
    expect(component.showPassword()).toBeFalse();
  });

  it('should render app-dialog in template', () => {
    detectChanges();
    const appDialog = overlayElement.querySelector('app-dialog');
    expect(appDialog).toBeTruthy();
  });

  it('should render footer action buttons with common dialog-button styles', () => {
    detectChanges();
    const cancelButton = overlayElement.querySelector(
      '.dialog-button-secondary',
    );
    const nextButton = overlayElement.querySelector('.dialog-button-primary');
    expect(cancelButton).toBeTruthy();
    expect(nextButton).toBeTruthy();
    expect(cancelButton?.textContent?.trim()).toBe('Cancel');
    expect(nextButton?.textContent?.trim()).toBe('Next');
  });

  it('should navigate to Step 2 when goToStep2 is called with valid SSID', () => {
    expect(component.headline()).toBe('Configure Wi-Fi');
    expect(component.supportText()).toBe(
      'Step 1 of 2 · Choose a network for 3 devices',
    );

    component.updateTargetWifi({ssid: 'my-new-wifi'});
    component.goToStep2();
    expect(component.step()).toBe(2);
    expect(component.headline()).toBe('Review and apply');
    expect(component.supportText()).toBe('Step 2 of 2 · Confirm and apply');

    component.backToStep1();
    expect(component.step()).toBe(1);
    expect(component.headline()).toBe('Configure Wi-Fi');
    expect(component.supportText()).toBe(
      'Step 1 of 2 · Choose a network for 3 devices',
    );
  });

  it('should compute review rows with correct writability without client-side diffing', () => {
    component.updateTargetWifi({ssid: 'target-office-wifi'});
    component.goToStep2();
    detectChanges();
    const rows = component.deviceItems();
    expect(rows.length).toBe(3);

    const dev1 = rows.find((r) => r.deviceId === 'device-1')!;
    expect(dev1.writable).toBeTrue();

    const dev2 = rows.find((r) => r.deviceId === 'device-2')!;
    expect(dev2.writable).toBeFalse();
    expect(dev2.unwritableReason).toContain('Managed by host shared config');

    const dev3 = rows.find((r) => r.deviceId === 'device-3')!;
    expect(dev3.writable).toBeTrue();

    expect(component.writableRows().length).toBe(2);
    expect(component.skippedCount()).toBe(1);
    expect(component.canApply()).toBeTrue();
  });

  it('should render review table showing Will update for writable devices and Read-only for unwritable devices', () => {
    component.updateTargetWifi({ssid: 'target-office-wifi'});
    component.goToStep2();
    detectChanges();

    // Verify Target Summary Banner
    const banner = overlayElement.querySelector('.bw-review-banner')!;
    expect(banner.textContent).toContain('target-office-wifi');
    expect(banner.textContent).toContain('2 devices will update');
    expect(banner.textContent).toContain('1 device read-only');

    const tableRows = overlayElement.querySelectorAll('tbody tr');
    expect(tableRows.length).toBe(3);

    // dev1 (writable, current: lab-guest-5g): Status shows 'Will update'
    expect(tableRows[0].textContent).toContain('device-1');
    expect(tableRows[0].textContent).toContain('lab-guest-5g');
    expect(tableRows[0].textContent).toContain('Will update');

    // dev2 (unwritable): Status shows 'Read-only' with unwritable reason title
    expect(tableRows[1].textContent).toContain('device-2');
    expect(tableRows[1].textContent).toContain('Read-only');
    const readOnlyPill = tableRows[1].querySelector('.bw-status-skip')!;
    expect(readOnlyPill.getAttribute('title')).toContain(
      'Managed by host shared config',
    );

    // dev3 (writable, current: other-device-wifi): Status shows 'Will update'
    expect(tableRows[2].textContent).toContain('device-3');
    expect(tableRows[2].textContent).toContain('other-device-wifi');
    expect(tableRows[2].textContent).toContain('Will update');
  });

  it('should show Not configured when device has no Wi-Fi configured', () => {
    mockConfigService.batchGetDeviceWifiConfigs.and.returnValue(
      of({
        devices: [
          {
            deviceId: 'device-unconfigured',
            writable: true,
          },
        ],
      }),
    );
    component.updateTargetWifi({ssid: 'target-office-wifi'});
    component.goToStep2();
    detectChanges();

    const tableRows = overlayElement.querySelectorAll('tbody tr');
    expect(tableRows.length).toBe(1);
    expect(tableRows[0].textContent).toContain('device-unconfigured');
    expect(tableRows[0].textContent).toContain('Not configured');
    expect(tableRows[0].textContent).toContain('Will update');
  });

  it('should show Already matches status when current Wi-Fi matches target Wi-Fi', () => {
    component.updateTargetWifi({ssid: 'lab-guest-5g', scanSsid: false});
    component.goToStep2();
    detectChanges();

    const banner = overlayElement.querySelector('.bw-review-banner')!;
    expect(banner.textContent).toContain('1 device already match');
    expect(banner.textContent).toContain('1 device will update');
    expect(banner.textContent).toContain('1 device read-only');

    const tableRows = overlayElement.querySelectorAll('tbody tr');
    // dev1 has current: lab-guest-5g, scanSsid: false -> matches target
    expect(tableRows[0].textContent).toContain('Already matches');

    const primaryButton = overlayElement.querySelector(
      '.dialog-button-primary',
    ) as HTMLButtonElement;
    expect(primaryButton.textContent?.trim()).toBe('Apply to 1 device');

    component.apply();
    expect(mockConfigService.batchUpdateDeviceWifiConfigs).toHaveBeenCalledWith(
      {
        deviceIds: ['device-3'],
        wifi: {
          ssid: 'lab-guest-5g',
          psk: '',
          scanSsid: false,
        },
      },
    );
  });

  it('should treat same SSID with different password as Will update', () => {
    component.updateTargetWifi({
      ssid: 'lab-guest-5g',
      psk: 'new-password',
      scanSsid: false,
    });
    component.goToStep2();
    detectChanges();

    expect(component.alreadyMatchCount()).toBe(0);
    expect(component.willUpdateCount()).toBe(2);
  });

  it('should show Done button instead of Apply when all writable devices already match target Wi-Fi', () => {
    mockConfigService.batchGetDeviceWifiConfigs.and.returnValue(
      of({
        devices: [
          {
            deviceId: 'device-1',
            wifi: {
              type: 'custom',
              ssid: 'lab-guest-5g',
              psk: '',
              scanSsid: false,
            },
            writable: true,
          },
          {
            deviceId: 'device-2',
            wifi: {
              type: 'custom',
              ssid: 'lab-guest-5g',
              psk: '',
              scanSsid: false,
            },
            writable: false,
            unwritableReason: 'Read-only',
          },
        ],
      }),
    );

    component.updateTargetWifi({ssid: 'lab-guest-5g', scanSsid: false});
    component.goToStep2();
    detectChanges();

    expect(component.willUpdateCount()).toBe(0);
    expect(component.alreadyMatchCount()).toBe(1);
    expect(component.canApply()).toBeFalse();

    const primaryButton = overlayElement.querySelector(
      '.dialog-button-primary',
    ) as HTMLButtonElement;
    expect(primaryButton.textContent?.trim()).toBe('Done');

    primaryButton.click();
    expect(
      mockConfigService.batchUpdateDeviceWifiConfigs,
    ).not.toHaveBeenCalled();
    expect(mockDialogRef.close).toHaveBeenCalled();
  });

  it('should show Close button when all selected devices are read-only', () => {
    mockConfigService.batchGetDeviceWifiConfigs.and.returnValue(
      of({
        devices: [
          {
            deviceId: 'device-2',
            wifi: {
              type: 'custom',
              ssid: 'lab-guest-5g',
              psk: '',
              scanSsid: false,
            },
            writable: false,
            unwritableReason: 'Read-only',
          },
        ],
      }),
    );

    component.updateTargetWifi({ssid: 'office-wifi'});
    component.goToStep2();
    detectChanges();

    expect(component.willUpdateCount()).toBe(0);
    expect(component.alreadyMatchCount()).toBe(0);
    expect(component.canApply()).toBeFalse();

    const primaryButton = overlayElement.querySelector(
      '.dialog-button-primary',
    ) as HTMLButtonElement;
    expect(primaryButton.textContent?.trim()).toBe('Close');
  });

  it('should apply batch update to only writable devices and transition to success on no errors', () => {
    component.updateTargetWifi({
      ssid: 'office-wifi',
      psk: 'secure123',
      scanSsid: false,
    });
    component.goToStep2();
    detectChanges();

    component.apply();

    expect(mockConfigService.batchUpdateDeviceWifiConfigs).toHaveBeenCalledWith(
      {
        deviceIds: ['device-1', 'device-3'],
        wifi: {
          ssid: 'office-wifi',
          psk: 'secure123',
          scanSsid: false,
        },
      },
    );

    expect(component.applyResult()).toBe('success');

    component.close();
    expect(mockDialogRef.close).toHaveBeenCalledWith({updated: true});
  });

  it('should show error screen when backend returns partial or complete errors', () => {
    mockConfigService.batchUpdateDeviceWifiConfigs.and.returnValue(
      of({
        errors: {
          'device-1': {
            code: 'VALIDATION_ERROR',
            message: 'Device offline',
          },
        },
      }),
    );

    component.updateTargetWifi({ssid: 'office-wifi'});
    component.goToStep2();
    detectChanges();
    component.apply();

    expect(component.applyResult()).toBe('errors');
    expect(component.applyErrors().length).toBe(1);
    expect(component.applyErrors()[0].deviceId).toBe('device-1');
    expect(component.applyErrors()[0].reason).toBe('Device offline');

    detectChanges();
    const errorTable = overlayElement.querySelector('.bw-error-table');
    expect(errorTable).toBeTruthy();
    const errorRows = overlayElement.querySelectorAll('tbody tr');
    expect(errorRows.length).toBe(1);
    expect(errorRows[0].textContent).toContain('device-1');
    expect(errorRows[0].textContent).toContain('Device offline');

    component.close();
    expect(mockDialogRef.close).toHaveBeenCalledWith({updated: true});
  });

  it('should handle Step 1 Wi-Fi suggestions load error gracefully', () => {
    mockConfigService.getWifiSuggestions.and.returnValue(
      throwError(() => new Error('Suggestions timeout')),
    );

    component.retryLoad();
    detectChanges();
    expect(component.loadError()).toBe('Suggestions timeout');
    expect(component.isLoading()).toBeFalse();
  });

  it('should handle Step 2 device configs load error gracefully and allow retry', () => {
    component.updateTargetWifi({ssid: 'target-office-wifi'});
    mockConfigService.batchGetDeviceWifiConfigs.and.returnValue(
      throwError(() => new Error('Network timeout')),
    );

    component.goToStep2();
    detectChanges();

    expect(component.loadError()).toBe('Network timeout');
    expect(component.isLoading()).toBeFalse();

    mockConfigService.batchGetDeviceWifiConfigs.and.returnValue(
      of(mockBatchDevicesResponse),
    );
    component.retryLoad();
    detectChanges();

    expect(component.loadError()).toBeNull();
    expect(component.deviceItems().length).toBe(3);
  });

  it('should show rich Wi-Fi tooltip on hover and support toggling password', () => {
    component.updateTargetWifi({
      ssid: 'target-office-wifi',
      psk: 'target-password',
      scanSsid: true,
    });
    component.goToStep2();
    detectChanges();

    const wifiChip = overlayElement.querySelector(
      '.bw-wifi-chip',
    ) as HTMLElement;
    expect(wifiChip).toBeTruthy();

    wifiChip.dispatchEvent(new MouseEvent('mouseenter'));
    detectChanges();

    expect(component.activeTip()).not.toBeNull();
    expect(component.activeTip()?.wifi.ssid).toBe('lab-guest-5g');

    const tipElement = overlayElement.querySelector('.bw-tip');
    expect(tipElement).toBeTruthy();
    expect(tipElement?.textContent).toContain('lab-guest-5g');
    expect(tipElement?.textContent).toContain('Open network (no password)');
    expect(tipElement?.textContent).toContain('Broadcast SSID (normal)');

    // Test tip with password and hidden network
    component.showWifiTip(component.activeTip()!.origin, {
      ssid: 'secret-wifi',
      psk: 'supersecret',
      scanSsid: true,
    });
    detectChanges();

    expect(component.showTipPsk()).toBeFalse();
    component.toggleTipPsk();
    expect(component.showTipPsk()).toBeTrue();
    detectChanges();

    const tipWithPsk = overlayElement.querySelector('.bw-tip');
    expect(tipWithPsk?.textContent).toContain('secret-wifi');
    expect(tipWithPsk?.textContent).toContain('supersecret');
    expect(tipWithPsk?.textContent).toContain('Hidden network');

    // Close tip
    component.closeTip();
    detectChanges();
    expect(component.activeTip()).toBeNull();
  });
});
