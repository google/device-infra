import {provideHttpClient} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {APP_DATA, AppData} from '../../models/app_data';
import {
  DeviceConfig,
  GetDeviceConfigResult,
  RecommendedWifi,
  UpdateDeviceConfigRequest,
} from '../../models/device_config_models';
import {UpdateHostConfigRequest} from '../../models/host_config_models';
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
          useClass: HttpConfigService,
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
      'http://testdomain.com/v6/devices/test-device/config',
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockDeviceConfig);
  });

  it('should update device config via POST to :update', () => {
    const mockRequest = {
      deviceId: 'test-device',
    } as unknown as UpdateDeviceConfigRequest;
    service.updateDeviceConfig(mockRequest).subscribe();

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/devices/test-device/config:update',
    );
    expect(req.request.method).toBe('POST');
    req.flush({success: true});
  });

  it('should retrieve recommended wifi and map from object', () => {
    const mockRecommendedWifi: RecommendedWifi[] = [
      {ssid: 'wifi-1', psk: 'psk1'},
    ];
    service.getRecommendedWifi().subscribe((recommendations) => {
      expect(recommendations).toEqual(mockRecommendedWifi);
    });

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/configs/wifi/recommendations',
    );
    expect(req.request.method).toBe('GET');
    req.flush({recommendations: mockRecommendedWifi});
  });

  it('should check device write permission via POST', () => {
    service
      .checkDeviceWritePermission('test-device', 'test-universe')
      .subscribe();

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/devices/test-device/config:checkWritePermission',
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      id: 'test-device',
      universe: 'test-universe',
    });
    req.flush({hasPermission: true});
  });

  it('should check host write permission via POST', () => {
    service.checkHostWritePermission('test-host', 'test-universe').subscribe();

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host/config:checkWritePermission',
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      hostName: 'test-host',
      universe: 'test-universe',
    });
    req.flush({hasPermission: true});
  });

  it('should get host default device config via POST and map from object', () => {
    const mockConfig = {id: 'test-config'} as unknown as DeviceConfig;
    service.getHostDefaultDeviceConfig('test-host').subscribe((config) => {
      expect(config).toEqual(mockConfig);
    });

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host:getDefaultDeviceConfig',
    );
    expect(req.request.method).toBe('POST');
    req.flush({deviceConfig: mockConfig});
  });

  it('should update host config via POST to :update', () => {
    const mockRequest = {
      hostName: 'test-host',
    } as unknown as UpdateHostConfigRequest;
    service.updateHostConfig(mockRequest).subscribe();

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host/config:update',
    );
    expect(req.request.method).toBe('POST');
    req.flush({success: true});
  });
});
