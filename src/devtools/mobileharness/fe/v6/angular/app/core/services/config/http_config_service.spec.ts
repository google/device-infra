import {provideHttpClient} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {APP_DATA, AppData} from '../../models/app_data';
import {
  BatchGetDeviceWifiConfigsRequest,
  BatchGetDeviceWifiConfigsResponse,
  BatchUpdateDeviceWifiConfigsRequest,
  BatchUpdateDeviceWifiConfigsResponse,
  DeviceConfig,
  DeviceConfigChangeStatus,
  GetDeviceConfigResult,
  GetWifiSuggestionsResponse,
  RecommendedWifi,
  UpdateDeviceConfigRequest,
} from '../../models/device_config_models';
import {UpdateHostConfigRequest} from '../../models/host_config_models';
import {normalizeHostConfig} from '../../utils/host_config_utils';
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

    service.getDeviceConfig('test-device', 'test-host').subscribe((config) => {
      expect(config).toEqual(mockDeviceConfig);
    });

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/devices/test-device/config?host_name=test-host',
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockDeviceConfig);
  });

  it('should update device config via POST to :update', () => {
    const mockRequest = {
      id: 'test-device',
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

  it('should check device config permission via POST', () => {
    service
      .checkDeviceConfigPermission('test-device', 'test-universe')
      .subscribe();

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/devices/test-device/config:checkConfigPermission',
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      id: 'test-device',
      universe: 'test-universe',
    });
    req.flush({hasPermission: true});
  });

  it('should check host config permission via POST', () => {
    service.checkHostConfigPermission('test-host', 'test-universe').subscribe();

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host/config:checkConfigPermission',
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      hostName: 'test-host',
      universe: 'test-universe',
    });
    req.flush({hasPermission: true});
  });

  it('should get host default device config via POST and map from object', () => {
    const mockConfig: DeviceConfig = {permissions: {owners: ['admin']}};
    service.getHostDefaultDeviceConfig('test-host').subscribe((config) => {
      expect(config).toEqual(mockConfig);
    });

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host:getDefaultDeviceConfig',
    );
    expect(req.request.method).toBe('POST');
    req.flush({deviceConfig: mockConfig});
  });

  it('should get host default device config and return null if deviceConfig is missing', () => {
    service.getHostDefaultDeviceConfig('test-host').subscribe((config) => {
      expect(config).toBeNull();
    });

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host:getDefaultDeviceConfig',
    );
    expect(req.request.method).toBe('POST');
    req.flush({});
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

  it('should unlock host properties via POST to :unlockHostProperties', () => {
    service.unlockHostProperties('test-host').subscribe();

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host/config:unlockHostProperties',
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({hostName: 'test-host'});
    req.flush({success: true});
  });

  it('should retrieve a host config', () => {
    const rawHostConfig = {
      permissions: {hostAdmins: ['admin1']},
    };
    const mockHostConfigResult = {
      hostConfig: rawHostConfig,
    };

    service.getHostConfig('test-host').subscribe((result) => {
      expect(result.hostConfig).toEqual(normalizeHostConfig(rawHostConfig));
    });

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host/config',
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockHostConfigResult);
  });

  it('should retrieve wifi suggestions via GET', () => {
    const mockSuggestions: GetWifiSuggestionsResponse = {
      candidateWifis: [
        {
          wifi: {
            ssid: 'suggested-wifi',
            psk: '',
            scanSsid: false,
          },
          fleetInUse: {deviceCount: 15},
        },
      ],
    };

    service.getWifiSuggestions().subscribe((res) => {
      expect(res).toEqual(mockSuggestions);
    });

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/configs/wifi:suggestions',
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockSuggestions);
  });

  it('should retrieve wifi suggestions via GET with fleet and universe query params', () => {
    const mockSuggestions: GetWifiSuggestionsResponse = {
      candidateWifis: [],
    };

    service
      .getWifiSuggestions({fleet: 'FLEET_SELF', universe: 'google_1p'})
      .subscribe((res) => {
        expect(res).toEqual(mockSuggestions);
      });

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/configs/wifi:suggestions?fleet=FLEET_SELF&universe=google_1p',
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockSuggestions);
  });

  it('should retrieve wifi suggestions via GET with universe-only param', () => {
    service.getWifiSuggestions({universe: 'google_1p'}).subscribe();

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/configs/wifi:suggestions?universe=google_1p',
    );
    expect(req.request.method).toBe('GET');
    req.flush({candidateWifis: []});
  });

  it('should retrieve wifi suggestions via GET with fleet-only param', () => {
    service.getWifiSuggestions({fleet: 'FLEET_SELF'}).subscribe();

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/configs/wifi:suggestions?fleet=FLEET_SELF',
    );
    expect(req.request.method).toBe('GET');
    req.flush({candidateWifis: []});
  });

  it('should batch get device wifi configs via POST', () => {
    const mockResponse: BatchGetDeviceWifiConfigsResponse = {
      entries: [
        {
          deviceId: 'dev-1',
          hostName: 'host-1',
          currentWifi: {ssid: 'wifi-1', psk: '', scanSsid: false},
          status: DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WRITABLE,
        },
      ],
    };

    const mockRequest: BatchGetDeviceWifiConfigsRequest = {
      devices: [{deviceId: 'dev-1', hostName: 'host-1'}],
      fleet: 'FLEET_SELF',
      universe: 'google_1p',
    };

    service.batchGetDeviceWifiConfigs(mockRequest).subscribe((res) => {
      expect(res).toEqual(mockResponse);
    });

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/devices:batchGetWifiConfigs',
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(mockRequest);
    req.flush(mockResponse);
  });

  it('should batch update device wifi configs via POST', () => {
    const mockRequest: BatchUpdateDeviceWifiConfigsRequest = {
      devices: [{deviceId: 'dev-1', hostName: 'host-1'}],
      target: {wifi: {ssid: 'wifi-1', psk: 'secret', scanSsid: false}},
      fleet: 'FLEET_SELF',
      universe: 'google_1p',
    };

    service.batchUpdateDeviceWifiConfigs(mockRequest).subscribe((res) => {
      expect(res.errors).toEqual({});
    });

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/devices:batchUpdateWifiConfigs',
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(mockRequest);
    req.flush({errors: {}});
  });

  it('should batch update device wifi configs and return errors map with UpdateError', () => {
    const mockRequest: BatchUpdateDeviceWifiConfigsRequest = {
      devices: [
        {deviceId: 'dev-1', hostName: 'host-1'},
        {deviceId: 'dev-2', hostName: 'host-2'},
      ],
      target: {wifi: {ssid: 'wifi-1', psk: 'secret', scanSsid: false}},
    };
    const mockResponse: BatchUpdateDeviceWifiConfigsResponse = {
      errors: {
        'dev-2': {
          code: 'PERMISSION_DENIED',
          message: 'User does not have write access to device',
        },
      },
    };

    service.batchUpdateDeviceWifiConfigs(mockRequest).subscribe((res) => {
      expect(res).toEqual(mockResponse);
      expect(res.errors?.['dev-2'].code).toBe('PERMISSION_DENIED');
      expect(res.errors?.['dev-2'].message).toBe(
        'User does not have write access to device',
      );
    });

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/devices:batchUpdateWifiConfigs',
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(mockRequest);
    req.flush(mockResponse);
  });
});
