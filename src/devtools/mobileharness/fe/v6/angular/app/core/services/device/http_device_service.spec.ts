import {HttpClient, provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';

import {APP_DATA, AppData} from '../../models/app_data';
import {DeviceOverview} from '../../models/device_overview';

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
    const mockDeviceOverview: DeviceOverview = {
      id: 'test-device',
      // Add other properties as needed
    } as DeviceOverview;

    service.getDeviceOverview('test-device').subscribe((overview) => {
      expect(overview).toEqual(mockDeviceOverview);
    });

    const req = httpMock.expectOne(
        'http://testdomain.com/v6/devices/test-device/overview');
    expect(req.request.method).toBe('GET');
    req.flush(mockDeviceOverview);
  });
});
