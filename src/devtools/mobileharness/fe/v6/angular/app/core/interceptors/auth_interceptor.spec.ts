import {HttpClient, provideHttpClient, withInterceptors} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
// an example of using absolute path for test import.
import {authInterceptor} from 'app/core/interceptors/auth_interceptor';

describe('authInterceptor', () => {
  let httpMock: HttpTestingController;
  let httpClient: HttpClient;

  const setup = () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
  };

  afterEach(() => {
    httpMock.verify();
  });

  const description = 'should not add withCredentials for external builds';
  const shouldAddCredentials = false;

  it(description, () => {
    setup();
    httpClient.get('/api/test').subscribe();

    const req = httpMock.expectOne('/api/test');
    expect(req.request.withCredentials).toBe(shouldAddCredentials);
    req.flush({});
  });
});
