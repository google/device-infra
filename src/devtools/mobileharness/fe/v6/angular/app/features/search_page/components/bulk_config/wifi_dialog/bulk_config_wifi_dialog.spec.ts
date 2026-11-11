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
        autoFocus: false,
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

  it('should initialize and load Step 1 candidate suggestions', () => {
    expect(mockConfigService.getWifiSuggestions).toHaveBeenCalledWith();
    expect(
      mockConfigService.batchGetDeviceWifiConfigs,
    ).not.toHaveBeenCalled();
    expect(component.wizard.viewMode()).toBe('network');
    expect(component.networkTask.hasLoaded()).toBeTrue();
    expect(component.candidateWifis().length).toBe(2);
    expect(document.activeElement).toBe(
      component.ssidInputField()!.nativeElement,
    );
  });

  it('should call batchGetDeviceWifiConfigs with target when moving to review and display backend review items', () => {
    expect(
      mockConfigService.batchGetDeviceWifiConfigs,
    ).not.toHaveBeenCalled();

    component.updateTargetWifi({ssid: 'target-office-wifi'});
    component.goToReview();
    detectChanges();

    expect(mockConfigService.batchGetDeviceWifiConfigs).toHaveBeenCalledTimes(
      1,
    );
    expect(mockConfigService.batchGetDeviceWifiConfigs).toHaveBeenCalledWith({
      devices: mockDialogData.devices,
      target: jasmine.objectContaining({
        wifi: jasmine.objectContaining({ssid: 'target-office-wifi'}),
      }),
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
      1,
    );
    expect(component.wizard.reviewSummary().willUpdateCount).toBe(2);

    mockConfigService.batchGetDeviceWifiConfigs.and.returnValue(
      of({
        entries: [
          {
            deviceId: 'device-1',
            hostName: 'host-1',
            currentWifi: {ssid: 'lab-guest-5g', psk: '', scanSsid: false},
            status:
              DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
          },
          {
            deviceId: 'device-2',
            hostName: 'host-2',
            currentWifi: {ssid: 'lab-guest-5g', psk: '', scanSsid: false},
            status:
              DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
          },
          {
            deviceId: 'device-3',
            hostName: 'host-3',
            currentWifi: {ssid: 'other-device-wifi', psk: '', scanSsid: false},
            status:
              DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
          },
        ],
      }),
    );

    component.wizard.setPhase('network');
    expect(component.wizard.phase()).toBe('network');
    component.updateTargetWifi({ssid: 'lab-guest-5g'});
    component.goToReview();
    expect(mockConfigService.batchGetDeviceWifiConfigs).toHaveBeenCalledTimes(
      2,
    );
    expect(mockConfigService.batchGetDeviceWifiConfigs).toHaveBeenCalledWith({
      devices: mockDialogData.devices,
      target: jasmine.objectContaining({
        wifi: jasmine.objectContaining({ssid: 'lab-guest-5g'}),
      }),
    });
    expect(component.wizard.reviewSummary().alreadyMatchCount).toBe(1);
    expect(component.wizard.reviewSummary().willUpdateCount).toBe(1);
  });

  it('should map backend candidate suggestions and filter by search', () => {
    const options = component.filteredWifiOptions();
    expect(options.length).toBe(2);

    const labGuest = options.find((o) => o.wifi.ssid === 'lab-guest-5g');
    expect(labGuest).toBeDefined();
    expect(labGuest?.subtitle).toContain('1,200 devices in fleet');

    const cand2 = options.find((o) => o.wifi.ssid === 'candidate-net-2');
    expect(cand2).toBeDefined();
    expect(cand2?.subtitle).toContain('350 devices in fleet');

    component.searchFilter.set('candidate');
    const filtered = component.filteredWifiOptions();
    expect(filtered.length).toBe(1);
    expect(filtered[0].wifi.ssid).toBe('candidate-net-2');
  });

  it('should select Wi-Fi and populate target form when updateTargetWifi is called', () => {
    const candidate = component
      .candidateWifis()
      .find((c) => c.wifi.ssid === 'candidate-net-2')!;
    component.updateTargetWifi(candidate.wifi);

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

  it('should toggle password visibility', () => {
    expect(component.showPassword()).toBeFalse();
    component.togglePasswordVisibility();
    expect(component.showPassword()).toBeTrue();
    component.togglePasswordVisibility();
    expect(component.showPassword()).toBeFalse();
  });

  it('should render app-dialog and footer action buttons', () => {
    detectChanges();
    const appDialog = overlayElement.querySelector('app-dialog');
    expect(appDialog).toBeTruthy();

    const cancelButton = overlayElement.querySelector(
      '.dialog-button-secondary',
    );
    const nextButton = overlayElement.querySelector('.dialog-button-primary');
    expect(cancelButton?.textContent?.trim()).toBe('Cancel');
    expect(nextButton?.textContent?.trim()).toBe('Next');
  });

  it('should render review table showing Will update for writable devices and Read-only for unwritable devices', () => {
    component.updateTargetWifi({ssid: 'target-office-wifi'});
    component.goToReview();
    detectChanges();

    const banner = overlayElement.querySelector('.bw-review-banner')!;
    expect(banner.textContent).toContain('target-office-wifi');
    expect(banner.textContent).toContain('2 devices will update');
    expect(banner.textContent).toContain('1 device read-only');

    const tableRows = overlayElement.querySelectorAll('tbody tr');
    expect(tableRows.length).toBe(3);

    expect(tableRows[0].textContent).toContain('device-1');
    expect(tableRows[0].textContent).toContain('host-1');
    expect(tableRows[0].textContent).toContain('lab-guest-5g');
    expect(tableRows[0].textContent).toContain('Will update');

    expect(tableRows[1].textContent).toContain('device-2');
    expect(tableRows[1].textContent).toContain('host-2');
    expect(tableRows[1].textContent).toContain('Read-only');
    const readOnlyPill = tableRows[1].querySelector('.bw-status-skip')!;
    const describedById = readOnlyPill.getAttribute('aria-describedby')!;
    expect(document.getElementById(describedById)?.textContent).toContain(
      'Managed by host shared config',
    );

    expect(tableRows[2].textContent).toContain('device-3');
    expect(tableRows[2].textContent).toContain('host-3');
    expect(tableRows[2].textContent).toContain('other-device-wifi');
    expect(tableRows[2].textContent).toContain('Will update');
  });

  it('should render multiple error devices sharing non-unique deviceId (000000000000) with distinct hostNames in review table', () => {
    mockConfigService.batchGetDeviceWifiConfigs.and.returnValue(
      of({
        entries: [
          {
            deviceId: '000000000000',
            hostName: 'mh-lab-01.google.com',
            status:
              DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
            readOnlyReason: 'Unrecognizable or invalid device ID',
          },
          {
            deviceId: '000000000000',
            hostName: 'mh-lab-02.google.com',
            status:
              DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY,
            readOnlyReason: 'Unrecognizable or invalid device ID',
          },
        ],
      }),
    );
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
    mockConfigService.batchGetDeviceWifiConfigs.and.returnValue(
      of({
        entries: [
          {
            deviceId: 'device-unconfigured',
            status:
              DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
          },
        ],
      }),
    );
    component.updateTargetWifi({ssid: 'target-office-wifi'});
    component.goToReview();
    detectChanges();

    const tableRows = overlayElement.querySelectorAll('tbody tr');
    expect(tableRows.length).toBe(1);
    expect(tableRows[0].textContent).toContain('device-unconfigured');
    expect(tableRows[0].textContent).toContain('Not configured');
    expect(tableRows[0].textContent).toContain('Will update');
  });

  it('should show Nothing to update banner and Close button when all devices already match target Wi-Fi', () => {
    mockConfigService.batchGetDeviceWifiConfigs.and.returnValue(
      of({
        entries: [
          {
            deviceId: 'device-1',
            status:
              DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
          },
          {
            deviceId: 'device-2',
            status:
              DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
          },
          {
            deviceId: 'device-3',
            status:
              DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES,
          },
        ],
      }),
    );

    component.updateTargetWifi({ssid: 'lab-guest-5g', scanSsid: false});
    component.goToReview();
    detectChanges();

    expect(component.wizard.reviewSummary().willUpdateCount).toBe(0);
    expect(component.wizard.reviewSummary().alreadyMatchCount).toBe(3);

    const infoBanner = overlayElement.querySelector('.bw-review-banner-info');
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

  it('should apply batch update to only WILL_UPDATE devices and transition to success on no errors', () => {
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
      },
    );

    expect(component.wizard.viewMode()).toBe('success');
    detectChanges();
    expect(
      overlayElement.querySelector('.bw-success-support')?.textContent,
    ).toBe('1 device read-only.');

    component.wizard.close();
    expect(mockDialogRef.close).toHaveBeenCalledWith({updated: true});
  });

  it('should apply Clear network to WILL_UPDATE devices', () => {
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

  it('should render Step 1 form immediately while networkTask is loading and support inline error retry', () => {
    const pendingSuggestions = new Subject<GetWifiSuggestionsResponse>();
    mockConfigService.getWifiSuggestions.and.returnValue(
      pendingSuggestions.asObservable(),
    );

    component.loadNetworkStepData();
    detectChanges();

    expect(component.wizard.viewMode()).toBe('network');
    expect(component.networkTask.isLoading()).toBeTrue();
    const loadingEl = overlayElement.querySelector('.bw-panel-status');
    expect(loadingEl?.textContent).toContain('Loading Wi-Fi configurations…');
    expect(overlayElement.querySelector('#bw-ssid')).toBeTruthy();

    pendingSuggestions.error(new Error('Suggestions timeout'));
    detectChanges();

    expect(component.wizard.viewMode()).toBe('network');
    expect(component.networkTask.isLoading()).toBeFalse();
    expect(component.networkTask.error()).toBe('Suggestions timeout');

    const statusError = overlayElement.querySelector('.bw-panel-status-error');
    expect(statusError).toBeTruthy();
    expect(statusError?.textContent).toContain('Suggestions timeout');

    mockConfigService.getWifiSuggestions.and.returnValue(
      of(mockWifiSuggestionsResponse),
    );
    const retryBtn = statusError?.querySelector('button') as HTMLButtonElement;
    retryBtn.click();
    detectChanges();

    expect(component.networkTask.error()).toBeNull();
    expect(component.networkTask.hasLoaded()).toBeTrue();
    expect(component.candidateWifis().length).toBe(2);
  });

  it('should handle review device configs load error and support retry', () => {
    mockConfigService.batchGetDeviceWifiConfigs.and.returnValue(
      throwError(() => new Error('Step 2 load timeout')),
    );

    component.updateTargetWifi({ssid: 'target-office-wifi'});
    component.goToReview();
    detectChanges();

    expect(component.wizard.phase()).toBe('review');
    expect(component.wizard.stepError()).toBe('Step 2 load timeout');
    expect(component.wizard.viewMode()).toBe('step_error');

    const errorContainer = overlayElement.querySelector('.bw-error-container');
    expect(errorContainer).toBeTruthy();
    expect(errorContainer?.textContent).toContain('Step 2 load timeout');

    mockConfigService.batchGetDeviceWifiConfigs.and.returnValue(
      of({
        entries: [
          {
            deviceId: 'device-1',
            status:
              DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
          },
        ],
      }),
    );

    const retryBtn = overlayElement.querySelector(
      '.dialog-button-primary',
    ) as HTMLButtonElement;
    expect(retryBtn.textContent?.trim()).toBe('Retry');
    retryBtn.click();
    detectChanges();

    expect(component.wizard.stepError()).toBeNull();
    expect(component.wizard.reviewEntries().length).toBe(1);
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
    expect(applyingContainer?.textContent).toContain('Applying to 2 devices');

    pendingSubject.next({errors: {}});
    pendingSubject.complete();

    expect(component.wizard.viewMode()).toBe('success');
    expect(component.headline()).toBe('Wi-Fi updated');
    expect(mockDialogRef.disableClose).toBeFalse();

    detectChanges();
    const successContainer = overlayElement.querySelector('.bw-success');
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
    expect(component.headline()).toBe('Configure Wi-Fi');
    expect(component.supportText()).toBe('Apply completed with errors');
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
    it('should format nothingToUpdateMessage for clear mode and network mode', () => {
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

      component.updateTargetWifi({ssid: 'TargetNet'});
      expect(component.nothingToUpdateMessage()).toContain(
        'already use this Wi-Fi configuration. No update is needed.',
      );
    });
  });

  describe('apply lifecycle and error handling', () => {
    it('should handle whole-RPC apply errors on review step with inline banner and Retry Apply button', () => {
      component.updateTargetWifi({ssid: 'target-wifi'});
      component.goToReview();
      detectChanges();

      mockConfigService.batchUpdateDeviceWifiConfigs.and.returnValue(
        throwError(() => new Error('Network failure')),
      );
      component.apply();
      detectChanges();

      expect(component.wizard.viewMode()).toBe('review');
      expect(component.wizard.applyRpcError()).toBe('Network failure');
      const bannerEl = document.body.querySelector('.bw-apply-error-banner');
      expect(bannerEl?.textContent).toContain('Failed to apply Wi-Fi update');
      expect(bannerEl?.textContent).toContain('Network failure');
      const primaryBtn = document.body.querySelector('.dialog-button-primary');
      expect(primaryBtn?.textContent?.trim()).toBe('Retry Apply');

      mockConfigService.batchUpdateDeviceWifiConfigs.and.returnValue(
        throwError(() => ({})),
      );
      component.apply();
      detectChanges();

      expect(component.wizard.viewMode()).toBe('review');
      expect(component.wizard.applyRpcError()).toBe(
        'Server error while applying Wi-Fi update',
      );
    });
  });

  describe('canGoNext and step navigation', () => {
    it('should compute canGoNext correctly and block goToReview when false', () => {
      component.targetWifi.set({ssid: '   ', psk: '', scanSsid: false});
      expect(component.canGoNext()).toBeFalse();
      component.goToReview();
      expect(component.wizard.phase()).toBe('network');

      component.targetWifi.set({ssid: 'valid-ssid', psk: '', scanSsid: false});
      expect(component.canGoNext()).toBeTrue();
    });
  });

  describe('error fallbacks for loadNetworkStepData and loadReviewData', () => {
    it('should use fallback error message when loadNetworkStepData error has no message', () => {
      mockConfigService.getWifiSuggestions.and.returnValue(
        throwError(() => ({})),
      );
      component.loadNetworkStepData();
      expect(component.networkTask.error()).toBe(
        'Failed to load Wi-Fi configurations',
      );
    });

    it('should use fallback error message when loadReviewData error has no message', () => {
      mockConfigService.batchGetDeviceWifiConfigs.and.returnValue(
        throwError(() => ({})),
      );
      component.updateTargetWifi({ssid: 'valid-target'});
      component.goToReview();
      expect(component.wizard.stepError()).toBe(
        'Failed to check device Wi-Fi status',
      );
    });
  });

  describe('reviewTargetSubtitle and preset subtitle', () => {
    it('should return security meta for clearWifi, password-protected, open, and hidden networks', () => {
      component.clearTargetWifi();
      expect(component.reviewTargetSubtitle()).toBe(
        'Removes default Wi-Fi from target devices',
      );

      component.updateTargetWifi({
        ssid: 'SecretNet',
        psk: 'securePass',
        scanSsid: true,
      });
      expect(component.reviewTargetSubtitle()).toBe(
        'Target Wi-Fi · Password protected · Hidden SSID',
      );

      component.updateTargetWifi({
        ssid: 'OpenNet',
        psk: '',
        scanSsid: false,
      });
      expect(component.reviewTargetSubtitle()).toBe(
        'Target Wi-Fi · Open network',
      );

      component.updateTargetWifi({
        ssid: 'OpenHidden',
        psk: '',
        scanSsid: true,
      });
      expect(component.reviewTargetSubtitle()).toBe(
        'Target Wi-Fi · Open network · Hidden SSID',
      );
    });

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
      const options = component.filteredWifiOptions();
      expect(options.find((o) => o.wifi.ssid === 'PresetNet')?.subtitle).toBe(
        'Preset network',
      );
      expect(options.find((o) => o.wifi.ssid === 'NoInfoNet')?.subtitle).toBe(
        '',
      );
    });
  });
});
