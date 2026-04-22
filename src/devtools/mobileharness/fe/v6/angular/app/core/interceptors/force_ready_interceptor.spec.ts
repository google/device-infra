import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {forceReadyInterceptor} from './force_ready_interceptor';

describe('forceReadyInterceptor', () => {
  let httpMock: HttpTestingController;
  let httpClient: HttpClient;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([forceReadyInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should not modify response if no query params are present', () => {
    const urlParamsGetSpy = spyOn(URLSearchParams.prototype, 'get');
    urlParamsGetSpy.and.returnValue(null);

    const mockResponse = {actions: {debug: {isReady: false}}};

    httpClient.get('/v6/hosts/my-host/header-info').subscribe((response) => {
      expect(response).toEqual(mockResponse);
    });

    const req = httpMock.expectOne('/v6/hosts/my-host/header-info');
    req.flush(mockResponse);
  });

  it('should force host actions to be ready', () => {
    const urlParamsGetSpy = spyOn(URLSearchParams.prototype, 'get');
    urlParamsGetSpy
      .withArgs('force_host_ready')
      .and.returnValue('debug,decommission');
    urlParamsGetSpy.withArgs('force_device_ready').and.returnValue(null);

    const mockResponse = {
      actions: {
        debug: {isReady: false, visible: true},
        decommission: {isReady: false, visible: true},
        configuration: {isReady: false, visible: true},
      },
    };

    httpClient
      .get<{
        actions: Record<string, {isReady: boolean}>;
      }>('/v6/hosts/my-host/header-info')
      .subscribe((response) => {
        expect(response.actions['debug'].isReady).toBeTrue();
        expect(response.actions['decommission'].isReady).toBeTrue();
        expect(response.actions['configuration'].isReady).toBeFalse();
      });

    const req = httpMock.expectOne('/v6/hosts/my-host/header-info');
    req.flush(mockResponse);
  });

  it('should force lab server actions to be ready in HostOverviewPageData', () => {
    const urlParamsGetSpy = spyOn(URLSearchParams.prototype, 'get');
    urlParamsGetSpy.withArgs('force_host_ready').and.returnValue('start');
    urlParamsGetSpy.withArgs('force_device_ready').and.returnValue(null);

    const mockResponse = {
      overviewContent: {
        labServer: {
          actions: {
            start: {isReady: false},
            stop: {isReady: false},
          },
        },
      },
    };

    httpClient
      .get<{
        overviewContent: {
          labServer: {actions: Record<string, {isReady: boolean}>};
        };
      }>('/v6/hosts/my-host/overview')
      .subscribe((response) => {
        expect(
          response.overviewContent.labServer.actions['start'].isReady,
        ).toBeTrue();
        expect(
          response.overviewContent.labServer.actions['stop'].isReady,
        ).toBeFalse();
      });

    const req = httpMock.expectOne('/v6/hosts/my-host/overview');
    req.flush(mockResponse);
  });

  it('should force device actions to be ready', () => {
    const urlParamsGetSpy = spyOn(URLSearchParams.prototype, 'get');
    urlParamsGetSpy.withArgs('force_host_ready').and.returnValue(null);
    urlParamsGetSpy
      .withArgs('force_device_ready')
      .and.returnValue('screenshot');

    const mockResponse = {
      actions: {
        screenshot: {isReady: false},
        logcat: {isReady: false},
      },
    };

    httpClient
      .get<{
        actions: Record<string, {isReady: boolean}>;
      }>('/v6/devices/my-device/header-info')
      .subscribe((response) => {
        expect(response.actions['screenshot'].isReady).toBeTrue();
        expect(response.actions['logcat'].isReady).toBeFalse();
      });

    const req = httpMock.expectOne('/v6/devices/my-device/header-info');
    req.flush(mockResponse);
  });

  it('should force device actions in GetHostDeviceSummariesResponse', () => {
    const urlParamsGetSpy = spyOn(URLSearchParams.prototype, 'get');
    urlParamsGetSpy.withArgs('force_host_ready').and.returnValue(null);
    urlParamsGetSpy
      .withArgs('force_device_ready')
      .and.returnValue('screenshot');

    const mockResponse = {
      deviceSummaries: [
        {id: 'd1', actions: {screenshot: {isReady: false}}},
        {id: 'd2', actions: {screenshot: {isReady: false}}},
      ],
    };

    httpClient
      .get<{
        deviceSummaries: Array<{actions: Record<string, {isReady: boolean}>}>;
      }>('/v6/hosts/my-host/devices')
      .subscribe((response) => {
        expect(
          response.deviceSummaries[0].actions['screenshot'].isReady,
        ).toBeTrue();
        expect(
          response.deviceSummaries[1].actions['screenshot'].isReady,
        ).toBeTrue();
      });

    const req = httpMock.expectOne('/v6/hosts/my-host/devices');
    req.flush(mockResponse);
  });
});
