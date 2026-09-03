import {provideHttpClient} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {APP_DATA, AppData} from '../../models/app_data';
import {GetGlobalSummaryRequest, GlobalSummary} from '../../models/home';
import {MOCK_GLOBAL_SUMMARY} from '../mock_data/home';
import {HOME_SERVICE} from './home_service';
import {HttpHomeService} from './http_home_service';

describe('HttpHomeService', () => {
  let service: HttpHomeService;
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
        {provide: HOME_SERVICE, useClass: HttpHomeService},
        HttpHomeService,
      ],
    });
    service = TestBed.inject(HttpHomeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should retrieve global summary without request argument', (done) => {
    service.getGlobalSummary().subscribe((summary: GlobalSummary) => {
      expect(summary).toEqual(MOCK_GLOBAL_SUMMARY);
      done();
    });

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/fleet-search/global-summary',
    );
    expect(req.request.method).toBe('GET');
    req.flush(MOCK_GLOBAL_SUMMARY);
  });

  it('should retrieve global summary with request argument', (done) => {
    const request: GetGlobalSummaryRequest = {};
    service.getGlobalSummary(request).subscribe((summary: GlobalSummary) => {
      expect(summary).toEqual(MOCK_GLOBAL_SUMMARY);
      done();
    });

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/fleet-search/global-summary',
    );
    expect(req.request.method).toBe('GET');
    req.flush(MOCK_GLOBAL_SUMMARY);
  });

  it('should propagate error when HTTP call fails', (done) => {
    service.getGlobalSummary().subscribe({
      next: () => {
        fail('should have failed with 500 error');
      },
      error: (error) => {
        expect(error.status).toBe(500);
        expect(error.statusText).toBe('Internal Server Error');
        done();
      },
    });

    const req = httpMock.expectOne(
      'http://testdomain.com/v6/fleet-search/global-summary',
    );
    expect(req.request.method).toBe('GET');
    req.flush('Server error', {
      status: 500,
      statusText: 'Internal Server Error',
    });
  });

  it('should be injectable via HOME_SERVICE token', () => {
    const tokenService = TestBed.inject(HOME_SERVICE);
    expect(tokenService).toBeInstanceOf(HttpHomeService);
  });
});
