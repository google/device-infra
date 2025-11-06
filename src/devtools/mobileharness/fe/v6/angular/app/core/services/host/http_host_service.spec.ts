import {provideHttpClient} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';

import {APP_DATA, AppData} from '../../models/app_data';
import {HostOverview} from '../../models/host_overview';

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
  it('should retrieve a host overview', () => {
    const mockHostOverview: HostOverview = {
      hostName: 'test-host',
      // Add other properties as needed
    } as HostOverview;
    service.getHostOverview('test-host').subscribe((overview) => {
      expect(overview).toEqual(mockHostOverview);
    });
    const req = httpMock.expectOne(
      'http://testdomain.com/v6/hosts/test-host/overview',
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockHostOverview);
  });
});
