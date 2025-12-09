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

import {HostConfig} from './host_config';

describe('HostConfig Component', () => {
  let mockGetHostConfig: jasmine.Spy;

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

  it('should convert NODEVICE_CONFIG_MODE_UNSPECIFIEDNE to PER_DEVICE', () => {
    mockGetHostConfig.and.returnValue(
      of({
        hostConfig: {
          deviceConfigMode: 'NODEVICE_CONFIG_MODE_UNSPECIFIEDNE',
          permissions: {hostAdmins: [], sshAccess: []},
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
        },
        uiStatus: {
          hostAdmins: {visible: true},
          sshAccess: {visible: true},
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
    });
  });
});
