import {TestBed} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';
import {MatTestDialogOpener, MatTestDialogOpenerModule} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';

import {HostSettings} from './host_settings';

describe('HostSettings Component', () => {
  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            HostSettings,
            NoopAnimationsModule,  // This makes test faster and more stable.
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
          ],
        })
        .compileComponents();
  });

  it('should be created', () => {
    const dialogOpener = TestBed.createComponent(
        MatTestDialogOpener.withComponent(HostSettings, {
          data: {
            hostName: 'test-host',
            config: {
              hostConfig: {
                permissions: {hostAdmins: [], sshAccess: []},
                deviceConfigMode: 'PER_DEVICE',
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
                hostAdmins: {visible: true, editability: {editable: true}},
                sshAccess: {visible: true, editability: {editable: true}},
                deviceConfigMode:
                    {visible: true, editability: {editable: true}},
                deviceConfig: {visible: true, editability: {editable: true}},
                hostProperties: {
                  sectionStatus: {visible: true, editability: {editable: true}},
                },
                deviceDiscovery: {visible: true, editability: {editable: true}},
              },
            },
          },
        }),
    );
    const comp = dialogOpener.componentInstance.dialogRef.componentInstance;
    expect(comp).toBeTruthy();

    const dialogElement = document.querySelector('mat-dialog-container');
    expect(dialogElement).toBeTruthy();
    expect(dialogElement!.querySelector('.nav-bar')).toBeTruthy();
  });
});
