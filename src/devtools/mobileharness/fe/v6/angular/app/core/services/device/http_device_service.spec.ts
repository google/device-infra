import {HttpClient, provideHttpClient} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';

import {APP_DATA, AppData} from '../../models/app_data';
import {DeviceOverviewPageData} from '../../models/device_overview';
import {
  HealthinessStats,
  RecoveryTaskStats,
  TestResultStats,
} from '../../models/device_stats';

import {DEVICE_SERVICE} from './device_service';
import {HttpDeviceService} from './http_device_service';

describe('HttpDeviceService', () => {
  let service: HttpDeviceService;
  let httpMock: HttpTestingController;
  const mockAppData: AppData = {
    labConsoleServerUrl: 'http://testdomain.com',
  } as AppData;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {provide: APP_DATA, useValue: mockAppData},
        {
          provide: DEVICE_SERVICE,
          useFactory: (http: HttpClient, appData: AppData) =>
            new HttpDeviceService(http),
          deps: [HttpClient, APP_DATA],
        },
      ],
    });
    service = TestBed.inject(DEVICE_SERVICE) as HttpDeviceService;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should retrieve a device overview', () => {
    const mockDeviceOverview: DeviceOverviewPageData = {
      overview: {
        id: 'test-device',
      },
      // Add other properties as needed
    } as DeviceOverviewPageData;

    service.getDeviceOverview('test-device').subscribe((overview) => {
      expect(overview).toEqual(mockDeviceOverview);
    });

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/devices/test-device/overview',
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockDeviceOverview);
  });

  it('should retrieve healthiness stats with correct date params', () => {
    const startDate = {year: 2023, month: 10, day: 25};
    const endDate = {year: 2023, month: 10, day: 26};
    const mockStats: HealthinessStats = {} as HealthinessStats;

    service
      .getDeviceHealthinessStats('test-device', startDate, endDate)
      .subscribe((stats) => {
        expect(stats).toEqual(mockStats);
      });

    const req = httpMock.expectOne(
      (request) =>
        request.url ===
          'http://testdomain.com/v6/devices/test-device/stats:getHealthiness' &&
        request.body['start_date']['year'] === 2023 &&
        request.body['start_date']['month'] === 10 &&
        request.body['start_date']['day'] === 25 &&
        request.body['end_date']['year'] === 2023 &&
        request.body['end_date']['month'] === 10 &&
        request.body['end_date']['day'] === 26,
    );
    expect(req.request.method).toBe('POST');
    req.flush(mockStats);
  });

  it('should retrieve test result stats with correct date params', () => {
    const startDate = {year: 2023, month: 10, day: 25};
    const endDate = {year: 2023, month: 10, day: 26};
    const mockStats: TestResultStats = {} as TestResultStats;

    service
      .getDeviceTestResultStats('test-device', startDate, endDate)
      .subscribe((stats) => {
        expect(stats).toEqual(mockStats);
      });

    const req = httpMock.expectOne(
      (request) =>
        request.url ===
          'http://testdomain.com/v6/devices/test-device/stats:getTestResults' &&
        request.body['start_date']['year'] === 2023 &&
        request.body['start_date']['month'] === 10 &&
        request.body['start_date']['day'] === 25 &&
        request.body['end_date']['year'] === 2023 &&
        request.body['end_date']['month'] === 10 &&
        request.body['end_date']['day'] === 26,
    );
    expect(req.request.method).toBe('POST');
    req.flush(mockStats);
  });

  it('should retrieve recovery task stats with correct date params', () => {
    const startDate = {year: 2023, month: 10, day: 25};
    const endDate = {year: 2023, month: 10, day: 26};
    const mockStats: RecoveryTaskStats = {} as RecoveryTaskStats;

    service
      .getDeviceRecoveryTaskStats('test-device', startDate, endDate)
      .subscribe((stats) => {
        expect(stats).toEqual(mockStats);
      });

    const req = httpMock.expectOne(
      (request) =>
        request.url ===
          'http://testdomain.com/v6/devices/test-device/stats:getRecoveryTasks' &&
        request.body['start_date']['year'] === 2023 &&
        request.body['start_date']['month'] === 10 &&
        request.body['start_date']['day'] === 25 &&
        request.body['end_date']['year'] === 2023 &&
        request.body['end_date']['month'] === 10 &&
        request.body['end_date']['day'] === 26,
    );
    expect(req.request.method).toBe('POST');
    req.flush(mockStats);
  });
});
