import {OverlayContainer} from '@angular/cdk/overlay';
import {ApplicationRef} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialogRef} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of, Subject, throwError} from 'rxjs';

import {
  BatchGetDeviceWifiConfigsResponse,
  BatchUpdateDeviceWifiConfigsResponse,
  DeviceConfigChangeStatus,
  GetWifiSuggestionsResponse,
} from '../../../../../core/models/device_config_models';
import {
  CONFIG_SERVICE,
  ConfigService,
} from '../../../../../core/services/config/config_service';
import {ClipboardService} from '../../../../../shared/services/clipboard_service';
import {BulkConfigDialogData} from '../../../models';
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

  const mockDialogData: BulkConfigDialogData = {
    devices: [
      {deviceId: 'device-1', hostName: 'host-1'},
      {deviceId: 'device-2', hostName: 'host-2'},
      {deviceId: 'device-3', hostName: 'host-3'},
    ],
    universe: 'google_1p',
    fleet: 'FLEET_SELF',
  };

  const mockBatchDevicesResponse: BatchGetDeviceWifiConfigsResponse = {
    entries: [
      {
        deviceId: 'device-1',
        hostName: 'host-1',
        currentWifi: {ssid: 'lab-guest-5g', psk: '', scanSsid: false},
        status:
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
      },
      {
        deviceId: 'device-2',
        hostName: 'host-2',
        currentWifi: {ssid: 'lab-guest-5g', psk: '', scanSsid: false},
        status: DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
        readOnlyReason:
          'Managed by host shared config (update host config instead)',
      },
      {
        deviceId: 'device-3',
        hostName: 'host-3',
        currentWifi: {
          ssid: 'other-device-wifi',
          psk: '',
          scanSsid: false,
        },
        status:
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
      },
    ],
  };

  const mockWifiSuggestionsResponse: GetWifiSuggestionsResponse = {
    candidateWifis: [
      {
        wifi: {
          ssid: 'lab-guest-5g',
          psk: '',
          scanSsid: false,
        },
        fleetInUse: {deviceCount: 1200},
      },
      {
        wifi: {
          ssid: 'candidate-net-2',
          psk: '',
          scanSsid: true,
        },
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
    mockConfigService.batchGetDeviceWifiConfigs.and.callFake((req) => {
      if (!req.target) {
        return of(mockBatchDevicesResponse);
      }
      const isClear = Boolean(req.target.clearWifi);
      const targetSsid = req.target.wifi?.ssid;
      const baseEntries =
        component?.initialDeviceEntries().length > 0
          ? component.initialDeviceEntries()
          : mockBatchDevicesResponse.entries!;
      const entries = baseEntries.map((entry) => {
        if (
          entry.status ===
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY
        ) {
          return entry;
        }
        const matches = isClear
          ? !entry.currentWifi?.ssid
          : entry.currentWifi?.ssid === targetSsid;
        return {
          ...entry,
          status: matches
            ? DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES
            : DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
        };
      });
      return of({entries});
    });
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

  it('should initialize and load Step 1 candidate suggestions and current device Wi-Fi configs', () => {
    expect(mockConfigService.getWifiSuggestions).toHaveBeenCalledWith({
      universe: 'google_1p',
      fleet: 'FLEET_SELF',
    });
    expect(mockConfigService.batchGetDeviceWifiConfigs).toHaveBeenCalledWith({
      devices: mockDialogData.devices,
      universe: 'google_1p',
      fleet: 'FLEET_SELF',
    });
    expect(component.wizard.viewMode()).toBe('network');
    expect(component.initialDeviceEntries().length).toBe(3);
    expect(component.candidateWifis().length).toBe(2);
  });

  it('should call batchGetDeviceWifiConfigs with target when moving to review and display backend review items', () => {
    expect(mockConfigService.batchGetDeviceWifiConfigs).toHaveBeenCalledTimes(
      1,
    );
    expect(mockConfigService.batchGetDeviceWifiConfigs).toHaveBeenCalledWith({
      devices: mockDialogData.devices,
      universe: 'google_1p',
      fleet: 'FLEET_SELF',
    });

    component.updateTargetWifi({ssid: 'target-office-wifi'});
    component.goToReview();
    detectChanges();

    expect(mockConfigService.batchGetDeviceWifiConfigs).toHaveBeenCalledTimes(
      2,
    );
    expect(mockConfigService.batchGetDeviceWifiConfigs).toHaveBeenCalledWith({
      devices: mockDialogData.devices,
      target: jasmine.objectContaining({
        wifi: jasmine.objectContaining({ssid: 'target-office-wifi'}),
      }),
      universe: 'google_1p',
      fleet: 'FLEET_SELF',
    });
    expect(component.wizard.phase()).toBe('review');
    expect(component.wizard.viewMode()).toBe('review');
    expect(component.wizard.reviewEntries().length).toBe(3);
    expect(component.wizard.reviewSummary().willUpdateCount).toBe(2);
    expect(component.wizard.reviewSummary().readOnlyCount).toBe(1);
  });

  it('should query backend with updated target when returning to network and moving to review again', () => {
    component.updateTargetWifi({ssid: 'target-office-wifi'});
    component.goToReview();
    expect(mockConfigService.batchGetDeviceWifiConfigs).toHaveBeenCalledTimes(
      2,
    );
    expect(component.wizard.reviewSummary().willUpdateCount).toBe(2);

    component.wizard.setPhase('network');
    component.updateTargetWifi({ssid: 'lab-guest-5g'});
    component.goToReview();
    expect(mockConfigService.batchGetDeviceWifiConfigs).toHaveBeenCalledTimes(
      3,
    );
    expect(mockConfigService.batchGetDeviceWifiConfigs).toHaveBeenCalledWith({
      devices: mockDialogData.devices,
      target: jasmine.objectContaining({
        wifi: jasmine.objectContaining({ssid: 'lab-guest-5g'}),
      }),
      universe: 'google_1p',
      fleet: 'FLEET_SELF',
    });
    expect(component.wizard.reviewSummary().alreadyMatchCount).toBe(1);
    expect(component.wizard.reviewSummary().willUpdateCount).toBe(1);
  });

  it('should filter Wi-Fi options based on search filter', () => {
    component.searchFilter.set('candidate');
    const filtered = component.filteredWifiOptions();
    expect(filtered.some((c) => c.wifi.ssid === 'candidate-net-2')).toBeTrue();
    expect(filtered.some((c) => c.wifi.ssid === 'lab-guest-5g')).toBeFalse();
  });

  it('should concatenate current Wi-Fi from selected devices and backend candidate suggestions', () => {
    const options = component.wifiOptions();
    expect(options.length).toBe(3);

    const labGuest = options.find((o) => o.wifi.ssid === 'lab-guest-5g');
    expect(labGuest).toBeDefined();
    expect(labGuest?.badge).toContain('2 devices selected');
    expect(labGuest?.subtitle).toContain('1,200 devices in fleet');

    const otherDev = options.find((o) => o.wifi.ssid === 'other-device-wifi');
    expect(otherDev).toBeDefined();
    expect(otherDev?.badge).toContain('1 device selected');

    const cand2 = options.find((o) => o.wifi.ssid === 'candidate-net-2');
    expect(cand2).toBeDefined();
    expect(cand2?.subtitle).toContain('350 devices in fleet');

    component.searchFilter.set('other');
    const filtered = component.filteredWifiOptions();
    expect(filtered.length).toBe(1);
    expect(filtered[0].wifi.ssid).toBe('other-device-wifi');
  });

  it('should select Wi-Fi and populate target form when selectWifi is called', () => {
    const candidate = component
      .candidateWifis()
      .find((c) => c.wifi.ssid === 'candidate-net-2')!;
    component.selectWifi(candidate.wifi);

    expect(component.targetWifi()).toEqual({
      ssid: 'candidate-net-2',
      psk: '',
      scanSsid: true,
    });
    expect(component.canGoNext()).toBeTrue();
  });

  it('should switch to clear mode when clearTargetWifi is called', () => {
    component.clearTargetWifi();

    expect(component.isClearWifi()).toBeTrue();
    expect(component.targetDeviceWifi()).toEqual({clearWifi: {}});
    expect(component.canGoNext()).toBeTrue();

    component.updateTargetWifi({});
    expect(component.isClearWifi()).toBeFalse();
  });

  it('should correctly compute isActive in filteredWifiOptions', () => {
    const wifi = component
      .candidateWifis()
      .find((c) => c.wifi.ssid === 'candidate-net-2')!.wifi;
    expect(
      component
        .filteredWifiOptions()
        .find((o) => o.wifi.ssid === 'candidate-net-2')?.isActive,
    ).toBeFalse();

    component.selectWifi(wifi);
    expect(
      component
        .filteredWifiOptions()
        .find((o) => o.wifi.ssid === 'candidate-net-2')?.isActive,
    ).toBeTrue();

    component.clearTargetWifi();
    expect(
      component
        .filteredWifiOptions()
        .find((o) => o.wifi.ssid === 'candidate-net-2')?.isActive,
    ).toBeFalse();
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

  it('should navigate to review phase when goToReview is called with valid SSID', () => {
    expect(component.headline()).toBe('Configure Wi-Fi for 3 devices');
    expect(component.supportText()).toBe('Step 1 of 2 · Choose a network');

    component.updateTargetWifi({ssid: 'my-new-wifi'});
    component.goToReview();
    expect(component.wizard.phase()).toBe('review');
    expect(component.headline()).toBe('Review and apply');
    expect(component.supportText()).toBe('Step 2 of 2 · Confirm and apply');

    component.wizard.setPhase('network');
    expect(component.wizard.phase()).toBe('network');
    expect(component.headline()).toBe('Configure Wi-Fi for 3 devices');
    expect(component.supportText()).toBe('Step 1 of 2 · Choose a network');
  });

  it('should compute review rows with correct status', () => {
    component.updateTargetWifi({ssid: 'target-office-wifi'});
    component.goToReview();
    detectChanges();
    const rows = component.wizard.reviewEntries();
    expect(rows.length).toBe(3);

    const dev1 = rows.find((r) => r.deviceId === 'device-1')!;
    expect(dev1.status).toBe(
      DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
    );

    const dev2 = rows.find((r) => r.deviceId === 'device-2')!;
    expect(dev2.status).toBe(
      DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
    );
    expect(dev2.readOnlyReason).toContain('Managed by host shared config');

    const dev3 = rows.find((r) => r.deviceId === 'device-3')!;
    expect(dev3.status).toBe(
      DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
    );

    expect(component.wizard.reviewSummary().willUpdateRows.length).toBe(2);
    expect(component.wizard.reviewSummary().readOnlyCount).toBe(1);
    expect(component.wizard.reviewSummary().willUpdateCount).toBe(2);
  });

  it('should render review table showing Will update for writable devices and Read-only for unwritable devices', () => {
    component.updateTargetWifi({ssid: 'target-office-wifi'});
    component.goToReview();
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
    expect(tableRows[0].textContent).toContain('host-1');
    expect(tableRows[0].textContent).toContain('lab-guest-5g');
    expect(tableRows[0].textContent).toContain('Will update');

    // dev2 (unwritable): Status shows 'Read-only' with readOnlyReason title
    expect(tableRows[1].textContent).toContain('device-2');
    expect(tableRows[1].textContent).toContain('host-2');
    expect(tableRows[1].textContent).toContain('Read-only');
    const readOnlyPill = tableRows[1].querySelector('.bw-status-skip')!;
    expect(readOnlyPill.getAttribute('title')).toContain(
      'Managed by host shared config',
    );

    // dev3 (writable, current: other-device-wifi): Status shows 'Will update'
    expect(tableRows[2].textContent).toContain('device-3');
    expect(tableRows[2].textContent).toContain('host-3');
    expect(tableRows[2].textContent).toContain('other-device-wifi');
    expect(tableRows[2].textContent).toContain('Will update');
  });

  it('should render multiple error devices sharing non-unique deviceId (000000000000) with distinct hostNames in review table', () => {
    component.initialDeviceEntries.set([
      {
        deviceId: '000000000000',
        hostName: 'mh-lab-01.google.com',
        status: DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
        readOnlyReason: 'Unrecognizable or invalid device ID',
      },
      {
        deviceId: '000000000000',
        hostName: 'mh-lab-02.google.com',
        status: DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
        readOnlyReason: 'Unrecognizable or invalid device ID',
      },
    ]);
    component.updateTargetWifi({ssid: 'target-office-wifi'});
    component.goToReview();
    detectChanges();

    const tableRows = overlayElement.querySelectorAll('tbody tr');
    expect(tableRows.length).toBe(2);
    expect(tableRows[0].textContent).toContain('000000000000');
    expect(tableRows[0].textContent).toContain('mh-lab-01.google.com');
    expect(tableRows[1].textContent).toContain('000000000000');
    expect(tableRows[1].textContent).toContain('mh-lab-02.google.com');
  });

  it('should show Not configured when device has no Wi-Fi configured', () => {
    component.initialDeviceEntries.set([
      {
        deviceId: 'device-unconfigured',
        status: DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WRITABLE,
      },
    ]);
    component.updateTargetWifi({ssid: 'target-office-wifi'});
    component.goToReview();
    detectChanges();

    const tableRows = overlayElement.querySelectorAll('tbody tr');
    expect(tableRows.length).toBe(1);
    expect(tableRows[0].textContent).toContain('device-unconfigured');
    expect(tableRows[0].textContent).toContain('Not configured');
    expect(tableRows[0].textContent).toContain('Will update');
  });

  it('should show Already matches status when device Wi-Fi matches target Wi-Fi', () => {
    component.updateTargetWifi({ssid: 'lab-guest-5g', scanSsid: false});
    component.goToReview();
    detectChanges();

    const banner = overlayElement.querySelector('.bw-review-banner')!;
    expect(banner.textContent).toContain('1 device already match');
    expect(banner.textContent).toContain('1 device will update');
    expect(banner.textContent).toContain('1 device read-only');

    const tableRows = overlayElement.querySelectorAll('tbody tr');
    expect(tableRows[0].textContent).toContain('Already matches');

    const primaryButton = overlayElement.querySelector(
      '.dialog-button-primary',
    ) as HTMLButtonElement;
    expect(primaryButton.textContent?.trim()).toBe('Apply to 1 device');

    component.apply();
    expect(mockConfigService.batchUpdateDeviceWifiConfigs).toHaveBeenCalledWith(
      {
        devices: [{deviceId: 'device-3', hostName: 'host-3'}],
        target: {
          wifi: {
            ssid: 'lab-guest-5g',
            psk: '',
            scanSsid: false,
          },
        },
        universe: 'google_1p',
        fleet: 'FLEET_SELF',
      },
    );
  });

  it('should show Nothing to update banner and Close button when all devices already match target Wi-Fi', () => {
    component.initialDeviceEntries.set([
      {
        deviceId: 'device-1',
        currentWifi: {
          ssid: 'lab-guest-5g',
          psk: '',
          scanSsid: false,
        },
        status: DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WRITABLE,
      },
      {
        deviceId: 'device-2',
        currentWifi: {
          ssid: 'lab-guest-5g',
          psk: '',
          scanSsid: false,
        },
        status: DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WRITABLE,
      },
      {
        deviceId: 'device-3',
        currentWifi: {
          ssid: 'lab-guest-5g',
          psk: '',
          scanSsid: false,
        },
        status: DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WRITABLE,
      },
    ]);

    component.updateTargetWifi({ssid: 'lab-guest-5g', scanSsid: false});
    component.goToReview();
    detectChanges();

    expect(component.wizard.reviewSummary().willUpdateCount).toBe(0);
    expect(component.wizard.reviewSummary().alreadyMatchCount).toBe(3);

    const infoBanner = overlayElement.querySelector('.bw-review-banner-info');
    expect(infoBanner).toBeTruthy();
    expect(infoBanner?.textContent).toContain(
      'All 3 devices already use this Wi-Fi configuration. No update is needed.',
    );

    const primaryButton = overlayElement.querySelector(
      '.dialog-button-primary',
    ) as HTMLButtonElement;
    expect(primaryButton.textContent?.trim()).toBe('Close');

    primaryButton.click();
    expect(
      mockConfigService.batchUpdateDeviceWifiConfigs,
    ).not.toHaveBeenCalled();
    expect(mockDialogRef.close).toHaveBeenCalled();
  });

  it('should show Close button and read-only banner when all selected devices are read-only', () => {
    component.initialDeviceEntries.set([
      {
        deviceId: 'device-1',
        currentWifi: {ssid: 'lab-guest-5g', psk: '', scanSsid: false},
        status: DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
        readOnlyReason: 'Read-only',
      },
      {
        deviceId: 'device-2',
        currentWifi: {ssid: 'lab-guest-5g', psk: '', scanSsid: false},
        status: DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
        readOnlyReason: 'Read-only',
      },
      {
        deviceId: 'device-3',
        currentWifi: {ssid: 'lab-guest-5g', psk: '', scanSsid: false},
        status: DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
        readOnlyReason: 'Read-only',
      },
    ]);

    component.updateTargetWifi({ssid: 'office-wifi'});
    component.goToReview();
    detectChanges();

    expect(component.wizard.reviewSummary().willUpdateCount).toBe(0);
    expect(component.wizard.reviewSummary().alreadyMatchCount).toBe(0);
    expect(component.wizard.reviewSummary().readOnlyCount).toBe(3);

    const infoBanner = overlayElement.querySelector('.bw-review-banner-info');
    expect(infoBanner).toBeTruthy();
    expect(infoBanner?.textContent).toContain(
      'All 3 devices are read-only and cannot be updated.',
    );

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
    component.goToReview();
    detectChanges();

    component.apply();

    expect(mockConfigService.batchUpdateDeviceWifiConfigs).toHaveBeenCalledWith(
      {
        devices: [
          {deviceId: 'device-1', hostName: 'host-1'},
          {deviceId: 'device-3', hostName: 'host-3'},
        ],
        target: {
          wifi: {
            ssid: 'office-wifi',
            psk: 'secure123',
            scanSsid: false,
          },
        },
        universe: 'google_1p',
        fleet: 'FLEET_SELF',
      },
    );

    expect(component.wizard.viewMode()).toBe('success');
    expect(component.successSupportText()).toContain('1 device read-only');

    component.wizard.close();
    expect(mockDialogRef.close).toHaveBeenCalledWith({updated: true});
  });

  it('should apply Clear network to writable devices', () => {
    component.clearTargetWifi();
    component.goToReview();
    detectChanges();

    component.apply();

    expect(mockConfigService.batchUpdateDeviceWifiConfigs).toHaveBeenCalledWith(
      {
        devices: [
          {deviceId: 'device-1', hostName: 'host-1'},
          {deviceId: 'device-3', hostName: 'host-3'},
        ],
        target: {
          clearWifi: {},
        },
        universe: 'google_1p',
        fleet: 'FLEET_SELF',
      },
    );

    expect(component.wizard.viewMode()).toBe('success');
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
    component.goToReview();
    detectChanges();
    component.apply();

    expect(component.wizard.viewMode()).toBe('errors');
    expect(component.wizard.applyErrors().length).toBe(1);
    expect(component.wizard.applyErrors()[0].deviceId).toBe('device-1');
    expect(component.wizard.applyErrors()[0].reason).toBe('Device offline');

    detectChanges();
    const errorTable = overlayElement.querySelector('.bw-error-table');
    expect(errorTable).toBeTruthy();
    const errorRows = overlayElement.querySelectorAll('tbody tr');
    expect(errorRows.length).toBe(1);
    expect(errorRows[0].textContent).toContain('device-1');
    expect(errorRows[0].textContent).toContain('Device offline');

    component.wizard.close();
    expect(mockDialogRef.close).toHaveBeenCalledWith({updated: true});
  });

  it('should handle network step Wi-Fi suggestions load error gracefully', () => {
    mockConfigService.getWifiSuggestions.and.returnValue(
      throwError(() => new Error('Suggestions timeout')),
    );

    component.loadNetworkStepData();
    detectChanges();
    expect(component.wizard.stepError()).toBe('Suggestions timeout');
    expect(component.wizard.viewMode()).toBe('step_error');
  });

  it('should handle review device configs load error and support retry', () => {
    mockConfigService.batchGetDeviceWifiConfigs.and.callFake((req) => {
      if (!req.target) {
        return of(mockBatchDevicesResponse);
      }
      return throwError(() => new Error('Step 2 load timeout'));
    });

    component.updateTargetWifi({ssid: 'target-office-wifi'});
    component.goToReview();
    detectChanges();

    expect(component.wizard.phase()).toBe('review');
    expect(component.wizard.stepError()).toBe('Step 2 load timeout');
    expect(component.wizard.viewMode()).toBe('step_error');

    const errorContainer = overlayElement.querySelector('.bw-error-container');
    expect(errorContainer).toBeTruthy();
    expect(errorContainer?.textContent).toContain('Step 2 load timeout');

    // Test retry
    mockConfigService.batchGetDeviceWifiConfigs.and.callFake((req) => {
      if (!req.target) return of(mockBatchDevicesResponse);
      return of({
        entries: [
          {
            deviceId: 'device-1',
            status:
              DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
          },
        ],
      });
    });

    const retryBtn = overlayElement.querySelector(
      '.dialog-button-primary',
    ) as HTMLButtonElement;
    expect(retryBtn.textContent?.trim()).toBe('Retry');
    retryBtn.click();
    detectChanges();

    expect(component.wizard.stepError()).toBeNull();
    expect(component.wizard.reviewEntries().length).toBe(1);
  });

  it('should show rich Wi-Fi tooltip on hover and support toggling password', () => {
    component.initialDeviceEntries.set([
      {
        deviceId: 'device-1',
        currentWifi: {ssid: 'lab-guest-5g', psk: '', scanSsid: false},
        status:
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
      },
      {
        deviceId: 'device-2',
        currentWifi: {
          ssid: 'secret-wifi',
          psk: 'supersecret',
          scanSsid: true,
        },
        status:
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
      },
    ]);
    component.updateTargetWifi({
      ssid: 'target-office-wifi',
      psk: 'target-password',
      scanSsid: true,
    });
    component.goToReview();
    detectChanges();

    const wifiChips = overlayElement.querySelectorAll('.bw-wifi-chip');
    expect(wifiChips.length).toBe(2);

    wifiChips[0].dispatchEvent(new MouseEvent('mouseenter'));
    detectChanges();

    const tipElement = overlayElement.querySelector('.bw-tip');
    expect(tipElement).toBeTruthy();
    expect(tipElement?.textContent).toContain('lab-guest-5g');
    expect(tipElement?.textContent).toContain('Open network (no password)');
    expect(tipElement?.textContent).toContain('Broadcast SSID (normal)');

    // Hover over second chip with password and hidden network
    wifiChips[1].dispatchEvent(new MouseEvent('mouseenter'));
    detectChanges();

    const eyeBtn = overlayElement.querySelector(
      '.bw-tip .bw-psk-eye',
    ) as HTMLButtonElement;
    expect(eyeBtn).toBeTruthy();
    eyeBtn.click();
    detectChanges();

    const tips = overlayElement.querySelectorAll('.bw-tip');
    const tipWithPsk = tips[tips.length - 1];
    expect(tipWithPsk?.textContent).toContain('secret-wifi');
    expect(tipWithPsk?.textContent).toContain('supersecret');
    expect(tipWithPsk?.textContent).toContain('Hidden network');
  });

  it('should evaluate review items dynamically against the selected target', () => {
    component.initialDeviceEntries.set([
      {
        deviceId: 'device-1',
        currentWifi: {ssid: 'bff-wifi-1', psk: 'pass1', scanSsid: true},
        status: DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WRITABLE,
      },
      {
        deviceId: 'device-2',
        currentWifi: {ssid: 'bff-wifi-2'},
        status: DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WRITABLE,
      },
      {
        deviceId: 'device-3',
        status: DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
        readOnlyReason: 'Device locked by policy',
      },
    ]);

    component.updateTargetWifi({ssid: 'bff-wifi-2'});
    component.goToReview();
    detectChanges();

    const rows = component.wizard.reviewEntries();
    expect(rows.length).toBe(3);

    // Verify row 0: device-1 differs from target -> WILL_UPDATE
    expect(rows[0].deviceId).toBe('device-1');
    expect(rows[0].currentWifi).toEqual({
      ssid: 'bff-wifi-1',
      psk: 'pass1',
      scanSsid: true,
    });
    expect(rows[0].status).toBe(
      DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
    );

    // Verify row 1: device-2 matches target -> ALREADY_MATCHES
    expect(rows[1].deviceId).toBe('device-2');
    expect(rows[1].currentWifi?.ssid).toBe('bff-wifi-2');
    expect(rows[1].status).toBe(
      DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
    );

    // Verify row 2: device-3 is read-only -> READ_ONLY
    expect(rows[2].deviceId).toBe('device-3');
    expect(rows[2].currentWifi).toBeUndefined();
    expect(rows[2].status).toBe(
      DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
    );
    expect(rows[2].readOnlyReason).toBe('Device locked by policy');

    // Verify rendered status pills in table
    const tableRows = overlayElement.querySelectorAll('tbody tr');
    expect(tableRows[0].textContent).toContain('Will update');
    expect(tableRows[1].textContent).toContain('Already matches');
    expect(tableRows[2].textContent).toContain('Read-only');
    expect(tableRows[2].textContent).toContain('Not configured');

    // Counts
    expect(component.wizard.reviewSummary().willUpdateCount).toBe(1);
    expect(component.wizard.reviewSummary().alreadyMatchCount).toBe(1);
    expect(component.wizard.reviewSummary().readOnlyCount).toBe(1);
  });

  it('should copy tooltip password to clipboard synchronously using ClipboardService', () => {
    const clipboardService = TestBed.inject(ClipboardService);
    spyOn(clipboardService, 'copyToClipboard').and.returnValue(true);

    component.initialDeviceEntries.set([
      {
        deviceId: 'device-1',
        currentWifi: {ssid: 'test-ssid', psk: 'secret123', scanSsid: false},
        status:
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
      },
    ]);
    component.updateTargetWifi({ssid: 'other-target'});
    component.goToReview();
    detectChanges();

    const wifiChip = overlayElement.querySelector(
      '.bw-wifi-chip',
    ) as HTMLElement;
    wifiChip.dispatchEvent(new MouseEvent('mouseenter'));
    detectChanges();

    const copyBtn = overlayElement.querySelector(
      '.bw-tip .bw-copy',
    ) as HTMLButtonElement;
    expect(copyBtn).toBeTruthy();
    copyBtn.click();
    detectChanges();

    expect(clipboardService.copyToClipboard).toHaveBeenCalledWith('secret123');
    expect(copyBtn.getAttribute('aria-label')).toBe('Copied');
  });

  it('should compute unconfiguredBadge and render it for No default Wi-Fi', () => {
    // When none of the devices are unconfigured (all have SSIDs)
    expect(component.unconfiguredBadge()).toBeNull();

    // When 1 device is unconfigured
    component.initialDeviceEntries.set([
      {
        deviceId: 'device-1',
        currentWifi: undefined,
        status:
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
      },
      {
        deviceId: 'device-2',
        currentWifi: {ssid: 'wifi-2', psk: '', scanSsid: false},
        status:
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
      },
      {
        deviceId: 'device-3',
        currentWifi: {ssid: 'wifi-3', psk: '', scanSsid: false},
        status:
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
      },
    ]);
    expect(component.unconfiguredBadge()).toBe('Current on 1 device selected');

    // When all N devices are unconfigured (e.g. after clearing default Wi-Fi)
    component.initialDeviceEntries.set([
      {
        deviceId: 'device-1',
        currentWifi: undefined,
        status:
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
      },
      {
        deviceId: 'device-2',
        currentWifi: undefined,
        status:
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
      },
      {
        deviceId: 'device-3',
        currentWifi: undefined,
        status:
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
      },
    ]);
    expect(component.unconfiguredBadge()).toBe('Current on all 3 selected');

    detectChanges();
    const clearRow = overlayElement.querySelector('.bw-li');
    expect(clearRow?.textContent).toContain('No default Wi-Fi');
    expect(clearRow?.textContent).toContain('Current on all 3 selected');
  });

  it('should update headline and display loading state during apply', () => {
    component.updateTargetWifi({ssid: 'office-wifi'});
    component.goToReview();
    detectChanges();

    const pendingSubject = new Subject<BatchUpdateDeviceWifiConfigsResponse>();
    mockConfigService.batchUpdateDeviceWifiConfigs.and.returnValue(
      pendingSubject.asObservable(),
    );

    component.apply();

    expect(component.wizard.viewMode()).toBe('applying');
    expect(component.headline()).toBe('Applying Wi-Fi');
    expect(component.supportText()).toBe('');
    expect(mockDialogRef.disableClose).toBeTrue();

    detectChanges();
    const applyingContainer = overlayElement.querySelector('.bw-applying');
    expect(applyingContainer).toBeTruthy();
    expect(applyingContainer?.textContent).toContain('Applying to 2 devices');
    expect(applyingContainer?.textContent).toContain(
      'This usually takes a few seconds.',
    );

    // Once mutation resolves with success
    pendingSubject.next({errors: {}});
    pendingSubject.complete();

    expect(component.wizard.viewMode()).toBe('success');
    expect(component.headline()).toBe('Wi-Fi updated');
    expect(mockDialogRef.disableClose).toBeFalse();

    detectChanges();
    const successContainer = overlayElement.querySelector('.bw-success');
    expect(successContainer).toBeTruthy();
    expect(successContainer?.textContent).toContain('2 devices updated');
  });

  it('should update headline when errors occur during apply', () => {
    component.updateTargetWifi({ssid: 'target'});
    component.goToReview();
    mockConfigService.batchUpdateDeviceWifiConfigs.and.returnValue(
      of({
        errors: {
          'device-1': {code: 'UNKNOWN', message: 'Offline'},
          'device-2': {code: 'UNKNOWN', message: 'Timeout'},
        },
      }),
    );
    component.apply();
    expect(component.headline()).toBe('2 devices could not be updated');
  });

  describe('targetDeviceWifi computed signal', () => {
    it('should return clearWifi when isClearWifi is true', () => {
      component.clearTargetWifi();
      expect(component.targetDeviceWifi()).toEqual({clearWifi: {}});
    });

    it('should construct TargetDeviceWifi with trimmed SSID, PSK and scanSsid', () => {
      component.updateTargetWifi({
        ssid: '   MyEnterpriseNetwork   ',
        psk: 'SecretPass123',
        scanSsid: true,
      });
      expect(component.targetDeviceWifi()).toEqual({
        wifi: {
          ssid: 'MyEnterpriseNetwork',
          psk: 'SecretPass123',
          scanSsid: true,
        },
      });
    });

    it('should construct TargetDeviceWifi for open unhidden network with empty psk', () => {
      component.updateTargetWifi({
        ssid: 'PublicOpen',
      });
      expect(component.targetDeviceWifi()).toEqual({
        wifi: {
          ssid: 'PublicOpen',
          psk: '',
          scanSsid: false,
        },
      });
    });
  });

  describe('filteredWifiOptions isActive', () => {
    beforeEach(() => {
      component.candidateWifis.set([
        {wifi: {ssid: 'GoogleGuest', psk: 'abc', scanSsid: true}},
        {wifi: {ssid: 'OtherNet'}},
      ]);
      component.initialDeviceEntries.set([]);
    });

    it('should mark all options inactive when isClearWifi is true', () => {
      component.clearTargetWifi();
      expect(
        component.filteredWifiOptions().every((o) => !o.isActive),
      ).toBeTrue();
    });

    it('should mark option active when wifi matches trimmed target and false when ssid, psk, or scanSsid differs', () => {
      component.updateTargetWifi({
        ssid: '  GoogleGuest  ',
        psk: 'abc',
        scanSsid: true,
      });
      const opts = component.filteredWifiOptions();
      expect(
        opts.find((o) => o.wifi.ssid === 'GoogleGuest')?.isActive,
      ).toBeTrue();
      expect(
        opts.find((o) => o.wifi.ssid === 'OtherNet')?.isActive,
      ).toBeFalse();

      component.updateTargetWifi({psk: 'other'});
      expect(
        component
          .filteredWifiOptions()
          .find((o) => o.wifi.ssid === 'GoogleGuest')?.isActive,
      ).toBeFalse();

      component.updateTargetWifi({psk: 'abc', scanSsid: false});
      expect(
        component
          .filteredWifiOptions()
          .find((o) => o.wifi.ssid === 'GoogleGuest')?.isActive,
      ).toBeFalse();
    });
  });

  describe('nothingToUpdateMessage', () => {
    it('should format message when all devices already have no default Wi-Fi configured', () => {
      component.clearTargetWifi();
      component.wizard.loadReview(
        () =>
          of({
            entries: [
              {
                deviceId: 'dev1',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
              },
              {
                deviceId: 'dev2',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
              },
              {
                deviceId: 'dev3',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
              },
            ],
          }),
        '',
      );
      expect(component.nothingToUpdateMessage()).toContain(
        'already have no default Wi-Fi configured. No update is needed.',
      );
    });

    it('should format message when all devices already use target Wi-Fi', () => {
      component.updateTargetWifi({ssid: 'TargetNet'});
      component.wizard.loadReview(
        () =>
          of({
            entries: [
              {
                deviceId: 'dev1',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
              },
              {
                deviceId: 'dev2',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
              },
              {
                deviceId: 'dev3',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
              },
            ],
          }),
        '',
      );
      expect(component.nothingToUpdateMessage()).toContain(
        'already use this Wi-Fi configuration. No update is needed.',
      );
    });

    it('should format message when some match and some are read-only', () => {
      component.updateTargetWifi({ssid: 'TargetNet'});
      component.wizard.loadReview(
        () =>
          of({
            entries: [
              {
                deviceId: 'dev1',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
              },
              {
                deviceId: 'dev2',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
              },
              {
                deviceId: 'dev3',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
              },
            ],
          }),
        '',
      );
      expect(component.nothingToUpdateMessage()).toBe(
        '1 device already match this Wi-Fi configuration and 2 devices are read-only. No devices can be updated.',
      );
    });

    it('should format message when all devices are read-only', () => {
      component.updateTargetWifi({ssid: 'TargetNet'});
      component.wizard.loadReview(
        () =>
          of({
            entries: [
              {
                deviceId: 'dev1',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
              },
              {
                deviceId: 'dev2',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
              },
              {
                deviceId: 'dev3',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
              },
            ],
          }),
        '',
      );
      expect(component.nothingToUpdateMessage()).toBe(
        'All 3 devices are read-only and cannot be updated.',
      );
    });
  });

  describe('successSupportText', () => {
    it('should format extra notes when some devices already matched and some were read-only', () => {
      component.wizard.loadReview(
        () =>
          of({
            entries: [
              {
                deviceId: 'dev1',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
              },
              {
                deviceId: 'dev2',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
              },
              {
                deviceId: 'dev3',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
              },
            ],
          }),
        '',
      );
      expect(component.successSupportText()).toBe(
        '1 device already matched; 1 device read-only.',
      );
    });

    it('should format default success text when no extra notes', () => {
      component.wizard.loadReview(
        () =>
          of({
            entries: [
              {
                deviceId: 'dev1',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
              },
              {
                deviceId: 'dev2',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
              },
            ],
          }),
        '',
      );
      expect(component.successSupportText()).toBe(
        'Every updated device now uses this network.',
      );
      component.clearTargetWifi();
      expect(component.successSupportText()).toBe(
        'Removed default Wi-Fi from every updated device.',
      );
    });
  });

  describe('apply lifecycle and error handling', () => {
    it('should do nothing when willUpdateRows is empty', () => {
      component.wizard.loadReview(() => of({entries: []}), '');
      component.apply();
      expect(
        mockConfigService.batchUpdateDeviceWifiConfigs,
      ).not.toHaveBeenCalled();
    });

    it('should handle apply errors with custom and default message', () => {
      component.updateTargetWifi({ssid: 'target-wifi'});
      component.goToReview();
      detectChanges();

      mockConfigService.batchUpdateDeviceWifiConfigs.and.returnValue(
        throwError(() => new Error('Network failure')),
      );
      component.apply();
      expect(component.wizard.viewMode()).toBe('errors');
      expect(component.wizard.applyErrors()[0].reason).toBe('Network failure');

      mockConfigService.batchUpdateDeviceWifiConfigs.and.returnValue(
        throwError(() => ({})),
      );
      component.apply();
      expect(component.wizard.applyErrors()[0].reason).toBe(
        'Server error while applying Wi-Fi update',
      );
    });

    it('should map UpdateError code when message is absent in response errors', () => {
      component.updateTargetWifi({ssid: 'target-wifi'});
      component.goToReview();
      detectChanges();

      mockConfigService.batchUpdateDeviceWifiConfigs.and.returnValue(
        of({
          errors: {
            'device-1': {code: 'PERMISSION_DENIED', message: ''},
          },
        }),
      );
      component.apply();
      expect(component.wizard.viewMode()).toBe('errors');
      expect(component.wizard.applyErrors()[0].reason).toBe(
        'PERMISSION_DENIED',
      );
    });
  });

  describe('dialog close results', () => {
    it('should close with undefined when applyResult is null', () => {
      component.wizard.close();
      expect(mockDialogRef.close).toHaveBeenCalledWith(undefined);
    });

    it('should close with undefined when all devices failed with errors', () => {
      component.wizard.loadReview(
        () =>
          of({
            entries: [
              {
                deviceId: 'dev1',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
              },
              {
                deviceId: 'dev2',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
              },
            ],
          }),
        '',
      );
      mockConfigService.batchUpdateDeviceWifiConfigs.and.returnValue(
        of({
          errors: {
            dev1: {code: 'UNKNOWN', message: 'Failed'},
            dev2: {code: 'UNKNOWN', message: 'Failed'},
          },
        }),
      );
      component.apply();
      component.wizard.close();
      expect(mockDialogRef.close).toHaveBeenCalledWith(undefined);
    });

    it('should close with updated: true when partially successful', () => {
      component.wizard.loadReview(
        () =>
          of({
            entries: [
              {
                deviceId: 'dev1',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
              },
              {
                deviceId: 'dev2',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
              },
            ],
          }),
        '',
      );
      mockConfigService.batchUpdateDeviceWifiConfigs.and.returnValue(
        of({
          errors: {
            dev1: {code: 'UNKNOWN', message: 'Failed'},
          },
        }),
      );
      component.apply();
      component.wizard.close();
      expect(mockDialogRef.close).toHaveBeenCalledWith({updated: true});
    });
  });

  describe('canGoNext and step navigation', () => {
    it('should compute canGoNext correctly', () => {
      component.clearTargetWifi();
      expect(component.canGoNext()).toBeTrue();

      component.updateTargetWifi({});
      component.targetWifi.set({ssid: '   ', psk: '', scanSsid: false});
      expect(component.canGoNext()).toBeFalse();

      component.targetWifi.set({ssid: 'valid-ssid', psk: '', scanSsid: false});
      expect(component.canGoNext()).toBeTrue();
    });

    it('should not navigate to review when canGoNext is false', () => {
      component.targetWifi.set({ssid: '', psk: '', scanSsid: false});
      component.goToReview();
      expect(component.wizard.phase()).toBe('network');
    });

    it('should reset stepError when navigating back to network', () => {
      component.wizard.loadReview(
        () => throwError(() => new Error('Some error')),
        'Fallback error',
      );
      expect(component.wizard.stepError()).toBe('Some error');
      component.wizard.setPhase('network');
      expect(component.wizard.phase()).toBe('network');
      expect(component.wizard.stepError()).toBeNull();
    });
  });

  describe('error fallbacks for loadNetworkStepData and loadReviewData', () => {
    it('should use fallback error message when loadNetworkStepData error has no message', () => {
      mockConfigService.getWifiSuggestions.and.returnValue(
        throwError(() => ({})),
      );
      component.loadNetworkStepData();
      expect(component.wizard.stepError()).toBe(
        'Failed to load Wi-Fi configurations',
      );
    });

    it('should use fallback error message when loadReviewData error has no message', () => {
      mockConfigService.batchGetDeviceWifiConfigs.and.callFake((req) => {
        if (!req.target) return of(mockBatchDevicesResponse);
        return throwError(() => ({}));
      });
      component.updateTargetWifi({ssid: 'valid-target'});
      component.goToReview();
      expect(component.wizard.stepError()).toBe(
        'Failed to check device Wi-Fi status',
      );
    });
  });

  describe('reviewTargetSubtitle', () => {
    it('should return security meta for clearWifi mode', () => {
      component.clearTargetWifi();
      expect(component.reviewTargetSubtitle()).toBe(
        'Removes default Wi-Fi from target devices',
      );
    });

    it('should return security meta for password-protected hidden network', () => {
      component.updateTargetWifi({
        ssid: 'SecretNet',
        psk: 'securePass',
        scanSsid: true,
      });
      expect(component.reviewTargetSubtitle()).toBe(
        'Target Wi-Fi · Password protected · Hidden SSID',
      );
    });

    it('should return security meta for open unhidden network', () => {
      component.updateTargetWifi({
        ssid: 'OpenNet',
        psk: '',
        scanSsid: false,
      });
      expect(component.reviewTargetSubtitle()).toBe(
        'Target Wi-Fi · Open network',
      );
    });

    it('should return security meta for open hidden network', () => {
      component.updateTargetWifi({
        ssid: 'OpenHidden',
        psk: '',
        scanSsid: true,
      });
      expect(component.reviewTargetSubtitle()).toBe(
        'Target Wi-Fi · Open network · Hidden SSID',
      );
    });
  });

  describe('headline and supportText', () => {
    it('should format headline when applying, on success, and on single error', () => {
      component.updateTargetWifi({ssid: 'target'});
      component.goToReview();

      const pendingSubject =
        new Subject<BatchUpdateDeviceWifiConfigsResponse>();
      mockConfigService.batchUpdateDeviceWifiConfigs.and.returnValue(
        pendingSubject.asObservable(),
      );
      component.apply();
      expect(component.headline()).toBe('Applying Wi-Fi');
      expect(component.supportText()).toBe('');

      pendingSubject.next({errors: {}});
      pendingSubject.complete();
      expect(component.headline()).toBe('Wi-Fi updated');
      expect(component.supportText()).toBe('');

      mockConfigService.batchUpdateDeviceWifiConfigs.and.returnValue(
        of({
          errors: {
            'device-1': {code: 'UNKNOWN', message: 'fail'},
          },
        }),
      );
      component.apply();
      expect(component.headline()).toBe('1 device could not be updated');
      expect(component.supportText()).toBe('');
    });

    it('should format headline for step 1 and step 2', () => {
      expect(component.headline()).toBe('Configure Wi-Fi for 3 devices');
      expect(component.supportText()).toBe('Step 1 of 2 · Choose a network');

      component.updateTargetWifi({ssid: 'target'});
      component.goToReview();
      expect(component.headline()).toBe('Review and apply');
      expect(component.supportText()).toBe('Step 2 of 2 · Confirm and apply');
    });
  });

  describe('getCandidateSubtitle and wifi options', () => {
    it('should format curated preset subtitle when fleetInUse count is absent', () => {
      component.candidateWifis.set([
        {
          wifi: {ssid: 'PresetNet', psk: 'p', scanSsid: false},
          curatedPreset: {},
        },
        {
          wifi: {ssid: 'NoInfoNet', psk: '', scanSsid: false},
        },
      ]);
      const options = component.wifiOptions();
      const presetOpt = options.find((o) => o.wifi.ssid === 'PresetNet');
      expect(presetOpt?.subtitle).toBe('Preset network');

      const noInfoOpt = options.find((o) => o.wifi.ssid === 'NoInfoNet');
      expect(noInfoOpt?.subtitle).toBe('');
    });
  });

  describe('reviewSummary switch handling', () => {
    it('should ignore entries with unspecified status', () => {
      component.wizard.loadReview(
        () =>
          of({
            entries: [
              {
                deviceId: 'dev-unspec',
                status:
                  DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_UNSPECIFIED,
              },
            ],
          }),
        '',
      );
      expect(component.wizard.reviewSummary().willUpdateCount).toBe(0);
      expect(component.wizard.reviewSummary().alreadyMatchCount).toBe(0);
      expect(component.wizard.reviewSummary().readOnlyCount).toBe(0);
    });
  });
});
