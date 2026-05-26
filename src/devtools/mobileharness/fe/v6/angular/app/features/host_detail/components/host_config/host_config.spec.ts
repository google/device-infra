import {TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';
import {HostConfigStateService} from '../../../../core/services/config/host_config_state_service';

import {
  GetHostConfigResult,
  HostConfig as HostConfigModel,
} from '../../../../core/models/host_config_models';
import {normalizeHostConfig} from '../../../../core/utils/host_config_utils';
import {HostConfig} from './host_config';

describe('HostConfig Component', () => {
  let mockGetHostConfig: jasmine.Spy;
  let stateService: HostConfigStateService;
  let setUiStatusSpy: jasmine.Spy;

  beforeEach(async () => {
    mockGetHostConfig = jasmine
      .createSpy('getHostConfig')
      .and.returnValue(of({hostConfig: null}));

    await TestBed.configureTestingModule({
      imports: [
        HostConfig,
        NoopAnimationsModule, // This makes test faster and more stable.
        MatTestDialogOpenerModule,
      ],
      providers: [
        provideRouter([]),
        {provide: MAT_DIALOG_DATA, useValue: {hostName: 'test-host'}},
        {
          provide: CONFIG_SERVICE,
          useValue: {
            getHostConfig: mockGetHostConfig,
            checkHostWritePermission: () => of({hasPermission: true}),
            checkDeviceWritePermission: () => of({hasPermission: true}),
          },
        },
      ],
    }).compileComponents();

    stateService = TestBed.inject(HostConfigStateService);
    stateService.clear();
    setUiStatusSpy = spyOn(stateService, 'setUiStatus').and.callThrough();
  });

  it('should be created', () => {
    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostConfig, {
        data: {hostName: 'test-host'},
      }),
    );
    const comp = (
      dialogOpener.componentInstance as MatTestDialogOpener<HostConfig>
    ).dialogRef.componentInstance;
    dialogOpener.detectChanges();
    expect(comp).toBeTruthy();
    // Example assertion, adjust as needed based on actual component content
    // expect(fixture.nativeElement.querySelector('app-host-empty')).toBeTruthy();
  });

  it('should convert DEVICE_CONFIG_MODE_UNSPECIFIED to PER_DEVICE', () => {
    mockGetHostConfig.and.returnValue(
      of({
        hostConfig: normalizeHostConfig({
          deviceConfigMode: 'DEVICE_CONFIG_MODE_UNSPECIFIED',
          permissions: {hostAdmins: []},
          deviceConfig: {
            permissions: {owners: [], executors: []},
            wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
            dimensions: {supported: [], required: []},
            settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
          },
          hostProperties: [],
          deviceDiscovery: {
            monitoredDeviceUuids: [],
            testbedUuids: [],
            miscDeviceUuids: [],
            overTcpIps: [],
            overSshDevices: [],
            manekiSpecs: [],
          },
        } as Partial<HostConfigModel>),
        uiStatus: {
          hostAdmins: {visible: true},
          deviceConfigMode: {visible: true},
          deviceConfig: {visible: true},
          hostProperties: {sectionStatus: {visible: true}},
          deviceDiscovery: {visible: true},
        },
      }),
    );

    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostConfig, {
        data: {hostName: 'test-host'},
      }),
    );
    const comp = (
      dialogOpener.componentInstance as MatTestDialogOpener<HostConfig>
    ).dialogRef.componentInstance;

    comp.configResult$.subscribe((result) => {
      expect(result.hostConfig!.deviceConfigMode).toBe('PER_DEVICE');
      expect(setUiStatusSpy).toHaveBeenCalledWith('test-host', result.uiStatus);
      expect(stateService.getUiStatus('test-host')).toBe(result.uiStatus);
    });
  });

  it('should handle null result from getHostConfig', () => {
    mockGetHostConfig.and.returnValue(of(null));

    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostConfig, {
        data: {hostName: 'test-host'},
      }),
    );
    const comp = (
      dialogOpener.componentInstance as MatTestDialogOpener<HostConfig>
    ).dialogRef.componentInstance;

    expect(() => {
      comp.configResult$.subscribe();
    }).not.toThrow();
    expect(setUiStatusSpy).not.toHaveBeenCalled();
  });

  it('should handle result without uiStatus from getHostConfig', () => {
    mockGetHostConfig.and.returnValue(
      of({hostConfig: {}} as unknown as GetHostConfigResult),
    );

    const dialogOpener = TestBed.createComponent(
      MatTestDialogOpener.withComponent(HostConfig, {
        data: {hostName: 'test-host'},
      }),
    );
    const comp = (
      dialogOpener.componentInstance as MatTestDialogOpener<HostConfig>
    ).dialogRef.componentInstance;

    expect(() => {
      comp.configResult$.subscribe();
    }).not.toThrow();
    expect(setUiStatusSpy).not.toHaveBeenCalled();
  });
});
