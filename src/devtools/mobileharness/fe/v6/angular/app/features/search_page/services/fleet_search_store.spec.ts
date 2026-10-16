import {Component} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, provideRouter, Router} from '@angular/router';
import {of, Subject} from 'rxjs';

import {
  SEARCH_SERVICE,
  SearchService,
} from '../../../core/services/search/search_service';
import {SnackBarService} from '../../../shared/services/snackbar_service';
import {FleetSearchStore} from './fleet_search_store';

@Component({standalone: true, template: ''})
class DummyComponent {}

describe('FleetSearchStore', () => {
  let router: Router;
  let mockSearchService: jasmine.SpyObj<SearchService>;
  let mockSnackBarService: jasmine.SpyObj<SnackBarService>;
  let queryParams$: Subject<Record<string, unknown>>;
  let activatedRouteStub: {
    queryParams: typeof queryParams$;
    snapshot: {queryParams: Record<string, unknown>};
    routeConfig: {path: string};
  };

  beforeEach(() => {
    queryParams$ = new Subject();
    activatedRouteStub = {
      queryParams: queryParams$,
      snapshot: {queryParams: {}},
      routeConfig: {path: 'devices'},
    };

    mockSearchService = jasmine.createSpyObj<SearchService>('SearchService', [
      'getFleetSuggestions',
      'getFleetPromotedKeys',
      'getFleetValueList',
      'resolveFleetChips',
    ]);
    mockSearchService.getFleetSuggestions.and.returnValue(of({items: []}));
    mockSearchService.getFleetPromotedKeys.and.returnValue(
      of({filterKeys: [], groupByKeys: []}),
    );
    mockSearchService.getFleetValueList.and.returnValue(of({}));
    mockSearchService.resolveFleetChips.and.returnValue(
      of({filterChips: [], groupByChips: []}),
    );

    mockSnackBarService = jasmine.createSpyObj<SnackBarService>(
      'SnackBarService',
      ['showError', 'showInfo', 'showWarning'],
    );

    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {path: 'devices', component: DummyComponent},
          {path: 'hosts', component: DummyComponent},
        ]),
        {provide: SEARCH_SERVICE, useValue: mockSearchService},
        {provide: SnackBarService, useValue: mockSnackBarService},
        {provide: ActivatedRoute, useValue: activatedRouteStub},
        FleetSearchStore,
      ],
    });

    router = TestBed.inject(Router);
  });

  async function flushAsync(): Promise<void> {
    TestBed.tick();
    await new Promise((resolve) => {
      setTimeout(resolve, 10);
    });
    TestBed.tick();
  }

  /** Simulates a full page load / refresh of `/devices` with the given query params. */
  async function createStoreAt(
    queryParams: Record<string, unknown>,
  ): Promise<FleetSearchStore> {
    activatedRouteStub.snapshot.queryParams = queryParams;
    const search = new URLSearchParams(
      queryParams as Record<string, string>,
    ).toString();
    await router.navigateByUrl(`/devices${search ? '?' + search : ''}`);
    const store = TestBed.inject(FleetSearchStore);
    queryParams$.next(queryParams);
    await flushAsync();
    return store;
  }

  it('defaults to the internal fleet when the URL has no fleet param', async () => {
    const store = await createStoreAt({'fake_data': 'true'});

    expect(store.entity()).toBe('devices');
    expect(store.fleet()).toBe('internal');
  });

  it('keeps fleet=ats from the URL on refresh of the ATS landing page (no chips)', async () => {
    // Regression test: restoreDefaultState() used to force the fleet back to 'internal' whenever the
    // URL carried no `f`/`gb` chips, so a refresh of `/devices?fleet=ats` always landed on the
    // internal fleet.
    const store = await createStoreAt({'fake_data': 'true', 'fleet': 'ats'});

    expect(store.fleet()).toBe('ats');
    expect(store.activeChips()).toEqual([]);
  });

  it('keeps fleet=ats when restoreDefaultState() is invoked explicitly', async () => {
    const store = await createStoreAt({'fleet': 'ats'});

    store.restoreDefaultState(false);
    TestBed.tick();

    expect(store.fleet()).toBe('ats');
  });

  it('resets to the internal fleet when navigating to the same page without a fleet param', async () => {
    const store = await createStoreAt({'fleet': 'ats'});
    expect(store.fleet()).toBe('ats');

    // Simulates clicking the "Devices" entry in the left nav menu, which drops `fleet` from the URL.
    activatedRouteStub.snapshot.queryParams = {};
    await router.navigateByUrl('/devices');
    queryParams$.next({});
    await flushAsync();

    expect(store.fleet()).toBe('internal');
  });

  it('switches fleet via setFleet() and writes it to the URL', async () => {
    const store = await createStoreAt({});

    store.setFleet('ats');
    await flushAsync();

    expect(store.fleet()).toBe('ats');
    expect(router.url).toContain('fleet=ats');
  });
});
