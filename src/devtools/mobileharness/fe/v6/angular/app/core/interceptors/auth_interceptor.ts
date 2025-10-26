import {HttpInterceptorFn} from '@angular/common/http';

/** Intercepts HTTP requests to add authentication details. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  let patchedReq = req;

  // For external builds, pass the request through unmodified.
  return next(patchedReq);
};
