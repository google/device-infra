import {provideLocationMocks} from '@angular/common/testing';
import {Component} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {Title} from '@angular/platform-browser';
import {provideRouter, Router, Routes, TitleStrategy} from '@angular/router';

import {fallbackRedirect, routes} from './app_routes';
import {PageTitleStrategy} from './shared/composables/page_title';

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

/**
 * Returns the full paths of the routes, nested ones included, that render a
 * page, eagerly or lazily, without declaring a title.
 */
function findUntitledPagePaths(input: Routes, parentPath = ''): string[] {
  return input.flatMap((route) => {
    const path = [parentPath, route.path ?? ''].filter(Boolean).join('/');
    const rendersPage = Boolean(route.component || route.loadComponent);
    const untitled = rendersPage && route.title === undefined ? [path] : [];
    return [...untitled, ...findUntitledPagePaths(route.children ?? [], path)];
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
    expect(lastRoute.redirectTo).toBe(fallbackRedirect);
  });

  it('should redirect the empty path to home', async () => {
    await router.navigateByUrl('/');
    expect(router.url).toBe('/home');
  });

  it('should redirect an unknown single segment path to all-site search', async () => {
    await router.navigateByUrl('/unknown');
    expect(router.url).toBe('/search?q=unknown');

    await router.navigateByUrl('/abcd');
    expect(router.url).toBe('/search?q=abcd');
  });

  it('should redirect an unknown nested path to home', async () => {
    await router.navigateByUrl('/foo/bar');
    expect(router.url).toBe('/home');
  });

  it('should preserve query params when redirecting to search or home', async () => {
    await router.navigateByUrl('/unknown?is_embedded_mode=true&fake_data=true');
    expect(router.url).toBe(
      '/search?is_embedded_mode=true&fake_data=true&q=unknown',
    );

    await router.navigateByUrl('/foo/bar?is_embedded_mode=true&fake_data=true');
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

    await router.navigateByUrl('/search');
    expect(router.url).toBe('/search');

    await router.navigateByUrl('/search?q=existing');
    expect(router.url).toBe('/search?q=existing');
  });
});

describe('App route titles', () => {
  let router: Router;
  let title: Title;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(stubRoutes(routes)),
        provideLocationMocks(),
        {provide: TitleStrategy, useClass: PageTitleStrategy},
      ],
    });
    router = TestBed.inject(Router);
    title = TestBed.inject(Title);
  });

  const expectedTitles: ReadonlyArray<[string, string]> = [
    ['/home', 'Home - OmniLab Console'],
    ['/search', 'Search - OmniLab Console'],
    ['/devices', 'Devices - OmniLab Console'],
    ['/hosts', 'Hosts - OmniLab Console'],
    ['/tests', 'Tests - OmniLab Console'],
    ['/jobs', 'Jobs - OmniLab Console'],
    ['/sessions', 'Sessions - OmniLab Console'],
    ['/dev/device-harness', 'Dev harness - OmniLab Console'],
  ];

  for (const [url, expectedTitle] of expectedTitles) {
    it(`should set the browser page title of ${url}`, async () => {
      await router.navigateByUrl(url);
      expect(title.getTitle()).toBe(expectedTitle);
    });
  }

  it('should replace the home page title when opening a search page', async () => {
    await router.navigateByUrl('/home');
    await router.navigateByUrl('/devices');
    expect(title.getTitle()).toBe('Devices - OmniLab Console');
  });

  it('should declare a title on every page route except the detail pages', () => {
    // Detail pages set a title that names the entity they show.
    const detailPagePaths = new Set([
      'devices/:id',
      'hosts/:hostName',
      'jobs/:jobId/tests/:id',
      'jobs/:id',
      'sessions/:id',
    ]);
    const untitledPagePaths = findUntitledPagePaths(routes).filter(
      (path) => !detailPagePaths.has(path),
    );
    expect(untitledPagePaths).toEqual([]);
  });

  it('should find untitled pages among nested and lazily loaded routes', () => {
    const untitledPagePaths = findUntitledPagePaths([
      {path: 'titled', component: StubPage, title: 'Titled'},
      {
        path: 'parent',
        children: [
          {path: 'eager', component: StubPage},
          {path: 'lazy', loadComponent: () => StubPage},
          {path: 'lazy-titled', loadComponent: () => StubPage, title: 'Lazy'},
        ],
      },
    ]);
    expect(untitledPagePaths).toEqual(['parent/eager', 'parent/lazy']);
  });
});
