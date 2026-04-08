import {provideHttpClient} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';

import {APP_DATA, AppData} from '../../models/app_data';
import {
  CanRolloutResult,
  DecommissionHostResponse,
  GetHostDebugInfoResponse,
  HostHeaderInfo,
  HostReleaseConfig,
  PopularFlag,
  ReleaseLabServerRequest,
  ReleaseLabServerResponse,
  RestartLabServerResponse,
  StartLabServerResponse,
  StopLabServerResponse,
} from '../../models/host_action';
import {
  DeviceSummary,
  HostOverview,
  HostOverviewPageData,
} from '../../models/host_overview';

import {HOST_SERVICE} from './host_service';
import {HttpHostService} from './http_host_service';

describe('HttpHostService', () => {
  let service: HttpHostService;
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
          provide: HOST_SERVICE,
          useClass: HttpHostService,
        },
      ],
    });
    service = TestBed.inject(HOST_SERVICE) as HttpHostService;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should retrieve a host header info', () => {
    const mockHeaderInfo: HostHeaderInfo = {
      hostName: 'test-host',
      actions: {
        configuration: {
          enabled: true,
          visible: true,
          tooltip: '',
          isReady: true,
        },
        debug: {enabled: true, visible: true, tooltip: '', isReady: true},
        deploy: {enabled: true, visible: true, tooltip: '', isReady: true},
        start: {enabled: true, visible: true, tooltip: '', isReady: true},
        restart: {enabled: true, visible: true, tooltip: '', isReady: true},
        stop: {enabled: true, visible: true, tooltip: '', isReady: true},
        decommission: {
          enabled: false,
          visible: true,
          tooltip: '',
          isReady: true,
        },
        updatePassThroughFlags: {
          enabled: true,
          visible: true,
          tooltip: '',
          isReady: true,
        },
        release: {enabled: true, visible: true, tooltip: '', isReady: true},
      },
    };
    service.getHostHeaderInfo('test-host').subscribe((info) => {
      expect(info).toEqual(mockHeaderInfo);
    });
    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host/header-info',
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockHeaderInfo);
  });

  it('should retrieve a host overview', () => {
    const mockOverviewData: HostOverviewPageData = {
      headerInfo: {hostName: 'test-host'} as HostHeaderInfo,
      overviewContent: {hostName: 'test-host'} as HostOverview,
    };
    service.getHostOverview('test-host').subscribe((overview) => {
      expect(overview).toEqual(mockOverviewData);
    });
    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host/overview',
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockOverviewData);
  });

  it('should retrieve host device summaries', () => {
    const mockDeviceSummaries: DeviceSummary[] = [
      {id: 'device-1'} as DeviceSummary,
    ];
    service.getHostDeviceSummaries('test-host').subscribe((response) => {
      expect(response).toEqual({deviceSummaries: mockDeviceSummaries});
    });
    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host/devices',
    );
    expect(req.request.method).toBe('GET');
    req.flush({deviceSummaries: mockDeviceSummaries});
  });

  it('should retrieve host debug info', () => {
    const mockDebugInfo: GetHostDebugInfoResponse = {
      results: [],
      timestamp: '2025-11-19T14:59:33Z',
    };
    service.getHostDebugInfo('test-host').subscribe((info) => {
      expect(info).toEqual(mockDebugInfo);
    });
    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host/debug-info',
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockDebugInfo);
  });

  it('should retrieve popular flags', () => {
    const mockPopularFlags: PopularFlag[] = [{name: 'flag1'} as PopularFlag];
    service.getPopularFlags('test-host').subscribe((flags) => {
      expect(flags).toEqual(mockPopularFlags);
    });
    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host/popular-flags',
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockPopularFlags);
  });

  it('should update pass-through flags', () => {
    service.updatePassThroughFlags('test-host', '--new-flag').subscribe();
    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host/updatePassThroughFlags',
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({flags: '--new-flag'});
    req.flush({});
  });

  it('should retrieve release configs', () => {
    const mockConfigs: HostReleaseConfig[] = [
      {name: 'config1'} as HostReleaseConfig,
    ];
    service.getReleaseConfigs('test-host').subscribe((configs) => {
      expect(configs).toEqual(mockConfigs);
    });
    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host/release-configs',
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockConfigs);
  });

  it('should decommission host', () => {
    const mockResponse: DecommissionHostResponse = {};
    service.decommissionHost('test-host').subscribe((res) => {
      expect(res).toEqual(mockResponse);
    });
    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host:decommission',
    );
    expect(req.request.method).toBe('POST');
    req.flush(mockResponse);
  });

  it('should decommission missing devices', () => {
    service.decommissionMissingDevices('test-host', ['device-1']).subscribe();
    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host/decommissionMissingDevices',
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({deviceControlIds: ['device-1']});
    req.flush({});
  });

  it('should deploy host release', () => {
    const mockReq: ReleaseLabServerRequest = {
      version: '1.2.3',
      flags: '--foo',
    };
    const mockResponse: ReleaseLabServerResponse = {
      trackingUrl: 'http://tracking.url',
    };
    service.releaseLabServer('test-host', mockReq).subscribe((res) => {
      expect(res).toEqual(mockResponse);
    });
    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host:deploy',
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(mockReq);
    req.flush(mockResponse);
  });

  it('should start host server', () => {
    const mockResponse: StartLabServerResponse = {
      trackingUrl: 'http://tracking.url',
    };
    service.startLabServer('test-host').subscribe((res) => {
      expect(res).toEqual(mockResponse);
    });
    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host:start',
    );
    expect(req.request.method).toBe('POST');
    req.flush(mockResponse);
  });

  it('should restart host server', () => {
    const mockResponse: RestartLabServerResponse = {
      trackingUrl: 'http://tracking.url',
    };
    service.restartLabServer('test-host').subscribe((res) => {
      expect(res).toEqual(mockResponse);
    });
    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host:restart',
    );
    expect(req.request.method).toBe('POST');
    req.flush(mockResponse);
  });

  it('should stop host server', () => {
    const mockResponse: StopLabServerResponse = {
      trackingUrl: 'http://tracking.url',
    };
    service.stopLabServer('test-host').subscribe((res) => {
      expect(res).toEqual(mockResponse);
    });
    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host:stop',
    );
    expect(req.request.method).toBe('POST');
    req.flush(mockResponse);
  });

  it('should check if host can rollout', () => {
    const mockResponse: CanRolloutResult = {
      canRollout: true,
      needUpgrade: false,
      message: '',
    };
    service.canRollout('test-host', 'restart').subscribe((res) => {
      expect(res).toEqual(mockResponse);
    });
    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host:can-rollout?action=restart',
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });
});
