import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {MatDialog, MatDialogRef} from '@angular/material/dialog';
import {of} from 'rxjs';
import {ConfirmDialog} from '../../shared/components/confirm_dialog/confirm_dialog';
import {LoginRequiredContent} from '../../shared/components/login_required_content/login_required_content';
import {
  loginInterceptor,
  resetLoginDialogOpenForTesting,
} from './login_interceptor';

describe('loginInterceptor', () => {
  let httpMock: HttpTestingController;
  let httpClient: HttpClient;
  let dialogSpy: jasmine.SpyObj<MatDialog>;

  beforeEach(() => {
    resetLoginDialogOpenForTesting();
    dialogSpy = jasmine.createSpyObj('MatDialog', ['open']);
    const mockDialogRef = {
      afterClosed: () => of(undefined),
    };
    dialogSpy.open.and.returnValue(
      mockDialogRef as unknown as MatDialogRef<ConfirmDialog, unknown>,
    );

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([loginInterceptor])),
        provideHttpClientTesting(),
        {provide: MatDialog, useValue: dialogSpy},
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should open login dialog and return EMPTY when status is 401', () => {
    let nextCalled = false;
    let errorCalled = false;
    let completeCalled = false;

    httpClient.get('/api/test').subscribe({
      next: () => {
        nextCalled = true;
      },
      error: () => {
        errorCalled = true;
      },
      complete: () => {
        completeCalled = true;
      },
    });

    const req = httpMock.expectOne('/api/test');
    req.flush('Unauthorized', {status: 401, statusText: 'Unauthorized'});

    expect(dialogSpy.open).toHaveBeenCalledWith(
      ConfirmDialog,
      jasmine.objectContaining({
        data: jasmine.objectContaining({
          title: 'Authentication Required',
          contentComponent: LoginRequiredContent,
        }),
      }),
    );
    expect(nextCalled).toBeFalse();
    expect(errorCalled).toBeFalse();
    expect(completeCalled).toBeTrue();
  });

  it('should not open login dialog and should rethrow error when status is not 401', () => {
    let errorReceived: HttpErrorResponse | undefined;

    httpClient.get('/api/test').subscribe({
      error: (err) => {
        errorReceived = err;
      },
    });

    const req = httpMock.expectOne('/api/test');
    req.flush('Server Error', {status: 500, statusText: 'Server Error'});

    expect(dialogSpy.open).not.toHaveBeenCalled();
    expect(errorReceived?.status).toBe(500);
  });
});
