import {HttpInterceptorFn} from '@angular/common/http';

/** Intercepts HTTP requests to add 'universe' query parameter if present in the URL. */
export const universeInterceptor: HttpInterceptorFn = (req, next) => {
  const urlParams = new URLSearchParams(window.location.search);
  const universe = urlParams.get('universe');

  if (universe) {
    const patchedReq = req.clone({
      setParams: {
        'universe': universe,
      },
    });
    return next(patchedReq);
  }

  return next(req);
};
