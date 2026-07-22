import {HttpInterceptorFn} from '@angular/common/http';

/** Intercepts HTTP requests to add 'universe' query parameter if present in the URL. */
export const universeInterceptor: HttpInterceptorFn = (req, next) => {
  const urlParams = new URLSearchParams(window.location.search);
  const universe = urlParams.get('universe');

  // NETWORK LAYER INTERCEPTION: Exclude '/tests' and '/jobs' requests.
  // The gRPC backends for test/job detail (e.g. GetTestRequest) do not support the 'universe' field.
  // Injecting it would trigger a backend GFEs / HTTP-transcoding 400 Bad Request error.
  // Note: This operates at the HTTP/network request level, while stripUniverseGuard and url_utils
  // sanitize the browser navigation URL bar parameters to keep the addresses clean.
  if (universe && !req.url.includes('/tests') && !req.url.includes('/jobs')) {
    const patchedReq = req.clone({
      setParams: {
        'universe': universe,
      },
    });
    return next(patchedReq);
  }

  return next(req);
};
