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
import {universeInterceptor} from './universe_interceptor';

describe('universeInterceptor', () => {
  let httpMock: HttpTestingController;
  let httpClient: HttpClient;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([universeInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should add universe query parameter if present in the URL', () => {
    const urlParamsGetSpy = spyOn(URLSearchParams.prototype, 'get');
    urlParamsGetSpy.withArgs('universe').and.returnValue('my-universe');

    httpClient.get('/api/test').subscribe();

    const req = httpMock.expectOne(
      (request) =>
        request.url === '/api/test' && request.params.has('universe'),
    );
    expect(req.request.params.get('universe')).toBe('my-universe');

    req.flush({});
  });

  it('should not add universe query parameter if not present in the URL', () => {
    const urlParamsGetSpy = spyOn(URLSearchParams.prototype, 'get');
    urlParamsGetSpy.withArgs('universe').and.returnValue(null);

    httpClient.get('/api/test').subscribe();

    const req = httpMock.expectOne('/api/test');
    expect(req.request.params.has('universe')).toBeFalse();

    req.flush({});
  });
});
