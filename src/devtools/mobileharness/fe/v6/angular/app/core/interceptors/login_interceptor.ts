import {HttpErrorResponse, HttpInterceptorFn} from '@angular/common/http';
import {inject} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';
import {EMPTY, throwError} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {ConfirmDialog} from '../../shared/components/confirm_dialog/confirm_dialog';
import {LoginRequiredContent} from '../../shared/components/login_required_content/login_required_content';

let isLoginDialogOpen = false;

/** Resets the login dialog state for unit testing. */
export function resetLoginDialogOpenForTesting(): void {
  isLoginDialogOpen = false;
}

/**
 * Intercepts HTTP errors to globally catch 401 Unauthorized / gRPC UNAUTHENTICATED
 * and display a login required dialog while blocking subsequent error handling.
 */
export const loginInterceptor: HttpInterceptorFn = (req, next) => {
  const dialog = inject(MatDialog);

  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401) {
        if (!isLoginDialogOpen) {
          isLoginDialogOpen = true;
          const dialogRef = dialog.open(ConfirmDialog, {
            data: {
              title: 'Authentication Required',
              contentComponent: LoginRequiredContent,
              type: 'warning',
              primaryButtonLabel: 'Reload Page',
            },
          });

          dialogRef.afterClosed().subscribe(() => {
            isLoginDialogOpen = false;
            // Prevent reloading the test runner browser during unit tests.
            const win =
              typeof window !== 'undefined'
                ? (window as unknown as Record<string, unknown>)
                : undefined;
            if (win && win['location'] && !win['__karma__']) {
              window.location.reload();
            }
          });
        }
        // Block subsequent action or error handling by returning EMPTY.
        return EMPTY;
      }
      return throwError(() => err);
    }),
  );
};
