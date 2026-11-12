import {
  ComponentFixture,
  fakeAsync,
  TestBed,
  tick,
} from '@angular/core/testing';
import {Title} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  convertToParamMap,
  ParamMap,
  provideRouter,
  Router,
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
  let envUniverseSpy: jasmine.SpyObj<EnvUniverseService>;
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
      'getDeviceTestHistory',
    ]);
    deviceServiceSpy.getDeviceOverview.and.returnValue(of(MOCK_PAGE_DATA));
    deviceServiceSpy.getDeviceTestHistory.and.returnValue(
      of({columns: [], rows: [], nextPageToken: ''}),
    );

    envUniverseSpy = jasmine.createSpyObj<EnvUniverseService>(
      'EnvUniverseService',
      ['isGoogle1P', 'isGoogleInternal'],
    );
    envUniverseSpy.isGoogle1P.and.returnValue(false);
    envUniverseSpy.isGoogleInternal.and.returnValue(true);

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

  it('names the device in the browser page title', () => {
    const title = TestBed.inject(Title);
    title.setTitle('Previous page');

    fixture.detectChanges();

    expect(title.getTitle()).toBe('Device device-1 - OmniLab Console');
  });

  it('shows only the product name in the browser page title in embedded mode', () => {
    const title = TestBed.inject(Title);
    title.setTitle('Previous page');
    queryParamMapSubject.next(convertToParamMap({'is_embedded_mode': 'true'}));

    fixture.detectChanges();

    expect(title.getTitle()).toBe('OmniLab Console');
  });

  it('restores the product name in the browser page title when it closes', () => {
    const title = TestBed.inject(Title);
    fixture.detectChanges();

    fixture.destroy();

    expect(title.getTitle()).toBe('OmniLab Console');
  });

  it('initializes activeTab from the tab query parameter and syncs tab changes to the URL', () => {
    const router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

    queryParamMapSubject.next(
      convertToParamMap({
        'host_name': 'host-1.example.com',
        'tab': 'test-history',
      }),
    );
    fixture.detectChanges();

    const component = fixture.componentInstance;
    expect(component.activeTab()).toBe('test-history');
    expect(component.testHistoryVisited()).toBeTrue();
    expect(router.navigate).not.toHaveBeenCalled();

    component.setActiveTab('overview');
    expect(component.activeTab()).toBe('overview');
    expect(router.navigate).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: {tab: null},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });

    (router.navigate as jasmine.Spy).calls.reset();
    component.setActiveTab('test-history');
    expect(component.activeTab()).toBe('test-history');
    expect(router.navigate).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: {tab: 'test-history'},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });

    fixture.destroy();
    envUniverseSpy.isGoogle1P.and.returnValue(true);
    const google1pFixture = TestBed.createComponent(DeviceDetailPage);
    (router.navigate as jasmine.Spy).calls.reset();
    queryParamMapSubject.next(
      convertToParamMap({
        'host_name': 'host-1.example.com',
        'tab': 'health',
      }),
    );
    google1pFixture.detectChanges();
    expect(google1pFixture.componentInstance.activeTab()).toBe('health');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('falls back to overview and clears unsupported tab query parameters', () => {
    fixture.destroy();
    envUniverseSpy.isGoogleInternal.and.returnValue(false);
    const ossFixture = TestBed.createComponent(DeviceDetailPage);
    const router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

    queryParamMapSubject.next(
      convertToParamMap({
        'host_name': 'host-1.example.com',
        'tab': 'test-history',
      }),
    );
    ossFixture.detectChanges();

    expect(ossFixture.componentInstance.activeTab()).toBe('overview');
    expect(ossFixture.componentInstance.testHistoryVisited()).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: {tab: null},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });

    (router.navigate as jasmine.Spy).calls.reset();
    queryParamMapSubject.next(
      convertToParamMap({
        'host_name': 'host-1.example.com',
        'tab': 'health',
      }),
    );
    ossFixture.detectChanges();

    expect(ossFixture.componentInstance.activeTab()).toBe('overview');
    expect(router.navigate).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: {tab: null},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });

    (router.navigate as jasmine.Spy).calls.reset();
    queryParamMapSubject.next(
      convertToParamMap({
        'host_name': 'host-1.example.com',
        'tab': 'record',
      }),
    );
    ossFixture.detectChanges();

    expect(ossFixture.componentInstance.activeTab()).toBe('overview');
    expect(router.navigate).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: {tab: null},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  });
});
