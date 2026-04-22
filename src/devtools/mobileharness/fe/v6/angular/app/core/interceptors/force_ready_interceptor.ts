import {HttpInterceptorFn, HttpResponse} from '@angular/common/http';
import {map} from 'rxjs/operators';
import {
  PatchRule,
  modifyDeviceHeaderInfo,
  modifyDeviceOverview,
  modifyHostDevices,
  modifyHostHeaderInfo,
  modifyHostOverview,
} from '../utils/force_ready_utils';

/**
 * Intercepts HTTP responses to force action buttons to be ready based on query parameters.
 */
export const forceReadyInterceptor: HttpInterceptorFn = (req, next) => {
  const urlParams = new URLSearchParams(window.location.search);
  const forceHostReady = urlParams.get('force_host_ready');
  const forceDeviceReady = urlParams.get('force_device_ready');

  // do nothing if no force ready parameters are set
  if (!forceHostReady && !forceDeviceReady) {
    return next(req);
  }

  const rules: PatchRule[] = [];

  // generate rules for force button ready
  if (forceHostReady) {
    const buttons = forceHostReady.split(',');
    rules.push({
      matcher: (r) =>
        r.method === 'GET' && /\/v6\/hosts\/[^\/]+\/header-info$/.test(r.url),
      handler: modifyHostHeaderInfo,
      forcedButtons: buttons,
    });
    rules.push({
      matcher: (r) =>
        r.method === 'GET' && /\/v6\/hosts\/[^\/]+\/overview$/.test(r.url),
      handler: modifyHostOverview,
      forcedButtons: buttons,
    });
  }

  if (forceDeviceReady) {
    const buttons = forceDeviceReady.split(',');
    rules.push({
      matcher: (r) =>
        r.method === 'GET' && /\/v6\/devices\/[^\/]+\/header-info$/.test(r.url),
      handler: modifyDeviceHeaderInfo,
      forcedButtons: buttons,
    });
    rules.push({
      matcher: (r) =>
        r.method === 'GET' && /\/v6\/devices\/[^\/]+\/overview$/.test(r.url),
      handler: modifyDeviceOverview,
      forcedButtons: buttons,
    });
    rules.push({
      matcher: (r) =>
        r.method === 'GET' && /\/v6\/hosts\/[^\/]+\/devices$/.test(r.url),
      handler: modifyHostDevices,
      forcedButtons: buttons,
    });
  }

  return next(req).pipe(
    map((event) => {
      if (event instanceof HttpResponse && event.body) {
        let body = event.body;
        let modified = false;

        // in response, check if any rules match and modify the body accordingly
        for (const rule of rules) {
          if (rule.matcher(req)) {
            const newBody = rule.handler(body, rule.forcedButtons);
            if (newBody !== body) {
              body = newBody;
              modified = true;
            }
          }
        }

        if (modified) {
          return event.clone({body});
        }
      }
      return event;
    }),
  );
};
