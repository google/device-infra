import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  convertToParamMap,
  ParamMap,
  provideRouter,
} from '@angular/router';
import {BehaviorSubject, of} from 'rxjs';

import {APP_DATA} from '../../core/models/app_data';
import {DeviceOverviewPageData} from '../../core/models/device_overview';
import {
  DEVICE_SERVICE,
  DeviceService,
} from '../../core/services/device/device_service';
import {EnvUniverseService} from '../../core/services/env_universe_service';
import {FakeHostService} from '../../core/services/host/fake_host_service';
import {HOST_SERVICE} from '../../core/services/host/host_service';
import {ClipboardService} from '../../shared/services/clipboard_service';
import {DeviceActionService} from '../../shared/services/device_action_service';
import {RemoteControlService} from '../../shared/services/remote_control_service';
import {SnackBarService} from '../../shared/services/snackbar_service';
import {DeviceDetailPage} from './device_detail_page';

const MOCK_PAGE_DATA: DeviceOverviewPageData = {
  overview: {
    id: 'device-1',
    host: {name: 'host-1.example.com', ip: '192.168.1.1'},
    healthAndActivity: {
      title: 'Idle',
      subtitle: 'Device is idle',
      state: 'IN_SERVICE_IDLE',
      deviceStatus: {status: 'IDLE', isCritical: false},
      deviceTypes: [{type: 'AndroidRealDevice', isAbnormal: false}],
      lastInServiceTime: null,
    },
    basicInfo: {
      model: 'Pixel 8',
      version: '16',
      form: 'physical',
      os: 'Android',
      batteryLevel: 100,
      network: {wifiRssi: -50, hasInternet: true},
      hardware: 'husky',
      build: 'latest',
    },
    permissions: {owners: [], executors: []},
    capabilities: {supportedDrivers: [], supportedDecorators: []},
    dimensions: {supported: {}, required: {}},
    properties: {},
  },
  headerInfo: {
    id: 'device-1',
    quarantine: {isQuarantined: false, expiry: ''},
  },
};

describe('DeviceDetailPage Component', () => {
  let fixture: ComponentFixture<DeviceDetailPage>;
  let deviceServiceSpy: jasmine.SpyObj<DeviceService>;
  let paramMapSubject: BehaviorSubject<ParamMap>;
  let queryParamMapSubject: BehaviorSubject<ParamMap>;

  beforeEach(async () => {
    paramMapSubject = new BehaviorSubject<ParamMap>(
      convertToParamMap({'id': 'device-1'}),
    );
    queryParamMapSubject = new BehaviorSubject<ParamMap>(
      convertToParamMap({
        'host_name': 'host-1.example.com',
        'openDeviceConfig': 'true',
      }),
    );

    deviceServiceSpy = jasmine.createSpyObj<DeviceService>('DeviceService', [
      'getDeviceOverview',
    ]);
    deviceServiceSpy.getDeviceOverview.and.returnValue(of(MOCK_PAGE_DATA));

    const envUniverseSpy = jasmine.createSpyObj<EnvUniverseService>(
      'EnvUniverseService',
      ['isGoogle1P'],
    );
    envUniverseSpy.isGoogle1P.and.returnValue(false);

    await TestBed.configureTestingModule({
      imports: [DeviceDetailPage, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: paramMapSubject.asObservable(),
            queryParamMap: queryParamMapSubject.asObservable(),
            queryParams: of({}),
          },
        },
        {provide: DEVICE_SERVICE, useValue: deviceServiceSpy},
        {provide: HOST_SERVICE, useClass: FakeHostService},
        {provide: EnvUniverseService, useValue: envUniverseSpy},
        {
          provide: DeviceActionService,
          useValue: jasmine.createSpyObj('DeviceActionService', [
            'takeScreenshot',
            'getLogcat',
            'flashDevice',
            'quarantineDevice',
            'changeQuarantine',
            'configureDevice',
          ]),
        },
        {
          provide: RemoteControlService,
          useValue: jasmine.createSpyObj('RemoteControlService', [
            'startRemoteControl',
          ]),
        },
        {
          provide: SnackBarService,
          useValue: jasmine.createSpyObj('SnackBarService', [
            'showSuccess',
            'showError',
            'showInProgress',
          ]),
        },
        {
          provide: ClipboardService,
          useValue: jasmine.createSpyObj('ClipboardService', [
            'copyToClipboard',
          ]),
        },
        {provide: APP_DATA, useValue: {applicationId: 'arsenal'}},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DeviceDetailPage);
  });

  it('does not call getDeviceOverview a second time when an unrelated query parameter changes', fakeAsync(() => {
    fixture.detectChanges();
    expect(deviceServiceSpy.getDeviceOverview).toHaveBeenCalledOnceWith({
      id: 'device-1',
      forceRefresh: true,
      hostName: 'host-1.example.com',
    });

    // Removing openDeviceConfig keeps host_name unchanged.
    queryParamMapSubject.next(
      convertToParamMap({'host_name': 'host-1.example.com'}),
    );
    tick(1000);
    fixture.detectChanges();

    expect(deviceServiceSpy.getDeviceOverview).toHaveBeenCalledTimes(1);
  }));
});
