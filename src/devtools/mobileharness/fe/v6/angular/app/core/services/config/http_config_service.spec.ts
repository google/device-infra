import {HttpClient, provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';

import {APP_DATA, AppData} from '../../models/app_data';
import {GetDeviceConfigResult} from '../../models/device_config_models';

import {CONFIG_SERVICE} from './config_service';
import {HttpConfigService} from './http_config_service';

describe('HttpConfigService', () => {
  let service: HttpConfigService;
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
          provide: CONFIG_SERVICE,
          useFactory: (http: HttpClient, appData: AppData) =>
              new HttpConfigService(http),
          deps: [HttpClient, APP_DATA],
        },
      ],
    });
    service = TestBed.inject(CONFIG_SERVICE) as HttpConfigService;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should retrieve a device config', () => {
    const mockDeviceConfig: GetDeviceConfigResult = {
      deviceConfig: {
          // Add properties as needed
      },
    } as GetDeviceConfigResult;

    service.getDeviceConfig('test-device').subscribe((config) => {
      expect(config).toEqual(mockDeviceConfig);
    });

    const req = httpMock.expectOne(
        'http://testdomain.com/v6/devices/test-device/config');
    expect(req.request.method).toBe('GET');
    req.flush(mockDeviceConfig);
  });
});
