import {provideLocationMocks} from '@angular/common/testing';
import {Component} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideRouter, Router, Routes} from '@angular/router';

import {routes} from './app_routes';

/** A lightweight stand-in for the real feature pages. */
@Component({
  standalone: true,
  template: '',
})
class StubPage {}

/**
 * Keeps the routing config (paths, redirects, guards) under test, but replaces
 * the heavy feature pages and their providers with a stub.
 */
function stubRoutes(input: Routes): Routes {
  return input.map((route) => {
    const stubbed = {...route};
    if (stubbed.component) {
      stubbed.component = StubPage;
    }
    delete stubbed.providers;
    return stubbed;
  });
}

describe('App routes', () => {
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(stubRoutes(routes)), provideLocationMocks()],
    });
    router = TestBed.inject(Router);
  });

  it('should keep the wildcard fallback as the last route', () => {
    const lastRoute = routes[routes.length - 1];
    expect(lastRoute.path).toBe('**');
    expect(lastRoute.redirectTo).toBe('home');
  });

  it('should redirect the empty path to home', async () => {
    await router.navigateByUrl('/');
    expect(router.url).toBe('/home');
  });

  it('should redirect an unknown path to home', async () => {
    await router.navigateByUrl('/unknown');
    expect(router.url).toBe('/home');
  });

  it('should redirect an unknown nested path to home', async () => {
    await router.navigateByUrl('/foo/bar');
    expect(router.url).toBe('/home');
  });

  it('should preserve query params when falling back to home', async () => {
    await router.navigateByUrl('/unknown?is_embedded_mode=true&fake_data=true');
    expect(router.url).toBe('/home?is_embedded_mode=true&fake_data=true');
  });

  it('should redirect /device to /devices', async () => {
    await router.navigateByUrl('/device');
    expect(router.url).toBe('/devices');
  });

  it('should redirect /host to /hosts', async () => {
    await router.navigateByUrl('/host');
    expect(router.url).toBe('/hosts');
  });

  it('should redirect /test to /tests', async () => {
    await router.navigateByUrl('/test');
    expect(router.url).toBe('/tests');
  });

  it('should redirect /job to /jobs', async () => {
    await router.navigateByUrl('/job');
    expect(router.url).toBe('/jobs');
  });

  it('should redirect /session to /sessions', async () => {
    await router.navigateByUrl('/session');
    expect(router.url).toBe('/sessions');
  });

  it('should preserve query params for the aliases', async () => {
    await router.navigateByUrl('/device?is_embedded_mode=true');
    expect(router.url).toBe('/devices?is_embedded_mode=true');

    await router.navigateByUrl('/job?fake_data=true');
    expect(router.url).toBe('/jobs?fake_data=true');
  });

  it('should not redirect known routes', async () => {
    await router.navigateByUrl('/devices');
    expect(router.url).toBe('/devices');

    await router.navigateByUrl('/hosts/host-1');
    expect(router.url).toBe('/hosts/host-1');

    await router.navigateByUrl('/devices/device-1');
    expect(router.url).toBe('/devices/device-1');

    await router.navigateByUrl('/tests');
    expect(router.url).toBe('/tests');

    await router.navigateByUrl('/jobs/job-1');
    expect(router.url).toBe('/jobs/job-1');

    await router.navigateByUrl('/jobs/job-1/tests/test-1');
    expect(router.url).toBe('/jobs/job-1/tests/test-1');

    await router.navigateByUrl('/sessions/session-1');
    expect(router.url).toBe('/sessions/session-1');
  });
});
