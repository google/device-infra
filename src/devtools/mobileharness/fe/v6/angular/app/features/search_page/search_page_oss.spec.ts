import {Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideNoopAnimations} from '@angular/platform-browser/animations';
import {ActivatedRoute, provideRouter, Router} from '@angular/router';
import {of, Subject} from 'rxjs';

import {
  Fleet,
  FleetSearchConfig,
  FleetSearchRequest,
  FleetSearchResults,
  SearchEntity,
} from '../../core/models/search';
import {CONFIG_SERVICE} from '../../core/services/config/config_service';
import {FakeConfigService} from '../../core/services/config/fake_config_service';
import {Environment} from '../../core/services/environment';
import {
  SEARCH_SERVICE,
  SearchService,
} from '../../core/services/search/search_service';
import {UrlService} from '../../core/services/url_service';
import {SearchPage} from './search_page';
import {FleetSearchStore} from './services/fleet_search_store';
import {SearchPageStore} from './services/search_page_store';

@Component({standalone: true, template: ''})
class DummyComponent {}

/**
 * OSS-build integration tests for the device/host search page.
 *
 * Renders the real SearchPage -> FleetSearchStore -> SearchInput /
 * FilterPresets / FleetSearchResults component tree with `Environment`
 * reporting the OSS build (`isGoogleInternal() === false`). Only the backend
 * `SearchService` is faked, returning what the OSS BFF (`AtsCuration`) returns:
 * a single FLEET_SELF fleet and `landing.enabled=false`.
 */
describe('SearchPage (OSS build integration)', () => {
  const DEVICE_COLUMNS = [
    {key: 'device_field::uuid', displayName: 'UUID', locked: true},
    {key: 'host_field::host_name', displayName: 'Host Name'},
    {key: 'device_field::status', displayName: 'Status'},
  ];
  const HOST_COLUMNS = [
    {key: 'host_field::host_name', displayName: 'Host Name', locked: true},
    {key: 'host_field::device_count', displayName: 'Device Count'},
  ];
  const TRY_CATEGORIES = [
    {label: 'a value', examples: [{text: 'Pixel'}, {text: 'IDLE'}]},
  ];
  const HOST_NAME = 'host.example.com';

  let router: Router;
  let searchService: jasmine.SpyObj<SearchService>;
  let mockEnvironment: jasmine.SpyObj<Environment>;
  let queryParams$: Subject<Record<string, unknown>>;
  let activatedRouteStub: {
    queryParams: Subject<Record<string, unknown>>;
    snapshot: {queryParams: Record<string, unknown>};
    routeConfig: {path: string};
  };
  let landingEnabled: boolean;

  function deviceRows(count: number) {
    return Array.from({length: count}, (_, i) => ({
      id: `${HOST_NAME}:NoOpDevice-${i}`,
      cells: [
        {text: {value: `${HOST_NAME}:NoOpDevice-${i}`}},
        {text: {value: HOST_NAME}},
        {text: {value: 'IDLE'}},
      ],
    }));
  }

  function configFor(entity: SearchEntity): FleetSearchConfig {
    const isHost = entity === SearchEntity.SEARCH_ENTITY_HOST;
    return {
      columns: {defaults: isHost ? HOST_COLUMNS : DEVICE_COLUMNS},
      landing: {
        enabled: landingEnabled,
        browseAllCount: isHost ? 1 : 5,
        tryCategories: TRY_CATEGORIES,
      },
    };
  }

  function resultsFor(req: FleetSearchRequest): FleetSearchResults {
    const isHost = req.entity === SearchEntity.SEARCH_ENTITY_HOST;
    const total = isHost ? 1 : 5;
    if (req.groupHeader) {
      return {
        grouped: {
          groupByKeys: [
            {key: 'host_field::host_name', displayName: 'Host Name'},
          ],
          groups: [{groupId: 'g1', values: [HOST_NAME], itemCount: total}],
          totalGroups: 1,
          totalItems: total,
          rangeStart: 1,
          rangeEnd: 1,
        },
      };
    }
    const columns = (isHost ? HOST_COLUMNS : DEVICE_COLUMNS).map((c) => ({
      key: c.key,
      displayName: c.displayName,
    }));
    const rows = isHost
      ? [
          {
            id: HOST_NAME,
            cells: [{text: {value: HOST_NAME}}, {text: {value: '5'}}],
          },
        ]
      : deviceRows(total);
    return {
      flat: {columns, rows, total, rangeStart: 1, rangeEnd: total},
    };
  }

  beforeEach(() => {
    window.localStorage.clear();
    landingEnabled = false;
    queryParams$ = new Subject();
    activatedRouteStub = {
      queryParams: queryParams$,
      snapshot: {queryParams: {}},
      routeConfig: {path: 'devices'},
    };

    mockEnvironment = jasmine.createSpyObj<Environment>('Environment', [
      'isGoogleInternal',
    ]);
    mockEnvironment.isGoogleInternal.and.returnValue(false);

    searchService = jasmine.createSpyObj<SearchService>('SearchService', [
      'getFleetSearchConfig',
      'searchFleet',
      'getFleetSuggestions',
      'getFleetPromotedKeys',
      'getFleetValueList',
      'resolveFleetChips',
      'translateArsenalSearch',
    ]);
    searchService.getFleetSearchConfig.and.callFake((req) =>
      of(configFor(req.entity)),
    );
    searchService.searchFleet.and.callFake((req) => of(resultsFor(req)));
    searchService.getFleetSuggestions.and.returnValue(of({items: []}));
    searchService.getFleetPromotedKeys.and.returnValue(
      of({
        filterKeys: [
          {key: 'device_field::uuid', metadata: {keyDisplayName: 'UUID'}},
          {key: 'device_field::status', metadata: {keyDisplayName: 'Status'}},
        ],
        groupByKeys: [{key: 'host_field::host_name', displayName: 'Host Name'}],
      }),
    );
    searchService.getFleetValueList.and.returnValue(of({}));
    searchService.resolveFleetChips.and.returnValue(
      of({filterChips: [], groupByChips: []}),
    );

    TestBed.configureTestingModule({
      imports: [SearchPage],
      providers: [
        provideNoopAnimations(),
        provideRouter([
          {path: 'devices', component: DummyComponent},
          {path: 'hosts', component: DummyComponent},
        ]),
        {provide: Environment, useValue: mockEnvironment},
        {provide: SEARCH_SERVICE, useValue: searchService},
        // The results table's bulk configuration actions inject the config
        // service; the fake keeps this test independent of the HTTP layer.
        {provide: CONFIG_SERVICE, useClass: FakeConfigService},
        {provide: ActivatedRoute, useValue: activatedRouteStub},
        FleetSearchStore,
        {provide: SearchPageStore, useExisting: FleetSearchStore},
      ],
    });
    router = TestBed.inject(Router);
  });

  /** Lets the reactive resources (config -> search) settle and re-renders. */
  async function settle(fixture: ComponentFixture<SearchPage>) {
    for (let i = 0; i < 3; i++) {
      TestBed.tick();
      fixture.detectChanges();
      await fixture.whenStable();
      await new Promise((resolve) => {
        setTimeout(resolve, 20);
      });
    }
    TestBed.tick();
    fixture.detectChanges();
  }

  /** Simulates a page load of `/devices` or `/hosts` with the given query params. */
  async function openPage(
    path: 'devices' | 'hosts',
    queryParams: Record<string, string> = {},
  ): Promise<{fixture: ComponentFixture<SearchPage>; store: FleetSearchStore}> {
    activatedRouteStub.routeConfig = {path};
    activatedRouteStub.snapshot.queryParams = queryParams;
    const search = new URLSearchParams(queryParams).toString();
    await router.navigateByUrl(`/${path}${search ? '?' + search : ''}`);
    const fixture = TestBed.createComponent(SearchPage);
    const store = TestBed.inject(FleetSearchStore);
    queryParams$.next(queryParams);
    await settle(fixture);
    return {fixture, store};
  }

  function text(fixture: ComponentFixture<SearchPage>, selector: string) {
    return (
      (fixture.nativeElement as HTMLElement)
        .querySelector(selector)
        ?.textContent?.trim() ?? null
    );
  }

  function count(fixture: ComponentFixture<SearchPage>, selector: string) {
    return (fixture.nativeElement as HTMLElement).querySelectorAll(selector)
      .length;
  }

  it('lists all devices right away without the landing page or the fleet switcher', async () => {
    const {fixture, store} = await openPage('devices');

    // Fleet: single fleet, switcher hidden, FLEET_SELF requested.
    expect(store.isFleetSwitchable).toBeFalse();
    expect(count(fixture, '.scope-switcher')).toBe(0);
    expect(count(fixture, '.scope-seg')).toBe(0);
    expect(searchService.getFleetSearchConfig).toHaveBeenCalledWith({
      entity: SearchEntity.SEARCH_ENTITY_DEVICE,
      fleet: Fleet.FLEET_SELF,
    });

    // Landing skipped because the backend says landing.enabled=false.
    expect(store.isLandingDisabled()).toBeTrue();
    expect(store.isLandingState()).toBeFalse();
    expect(store.browseAll()).toBeFalse();
    expect(count(fixture, '.rt-landing')).toBe(0);
    expect(count(fixture, '.browse-all-btn')).toBe(0);

    // The full list is shown, with the quick filter / group-by presets kept.
    expect(searchService.searchFleet).toHaveBeenCalledTimes(1);
    expect(searchService.searchFleet.calls.mostRecent().args[0]).toEqual(
      jasmine.objectContaining({
        entity: SearchEntity.SEARCH_ENTITY_DEVICE,
        fleet: Fleet.FLEET_SELF,
        filters: undefined,
        flat: jasmine.objectContaining({
          columns: DEVICE_COLUMNS.map((c) => c.key),
        }),
      }),
    );
    expect(count(fixture, 'app-fleet-search-results')).toBe(1);
    expect(count(fixture, 'table.results-table tbody tr')).toBe(5);
    expect(text(fixture, '.rt-count')).toBe('5 devices');
    expect(count(fixture, '.preset-row[aria-label="Quick filters"]')).toBe(1);
    expect(count(fixture, '.preset-row[aria-label="Quick group-by"]')).toBe(1);
  });

  it('lists all hosts right away on the hosts page', async () => {
    const {fixture, store} = await openPage('hosts');

    expect(store.entity()).toBe('hosts');
    expect(count(fixture, '.scope-switcher')).toBe(0);
    expect(count(fixture, '.rt-landing')).toBe(0);
    expect(searchService.searchFleet.calls.mostRecent().args[0]).toEqual(
      jasmine.objectContaining({
        entity: SearchEntity.SEARCH_ENTITY_HOST,
        fleet: Fleet.FLEET_SELF,
        filters: undefined,
      }),
    );
    expect(count(fixture, 'table.results-table tbody tr')).toBe(1);
    expect(text(fixture, '.rt-count')).toBe('1 hosts');
  });

  it('returns to the full list (not the landing page) after the last chip is removed', async () => {
    const {fixture, store} = await openPage('devices');

    // Apply the quick group-by, as a user clicking "Quick Group-by: Host Name".
    store.openQuickGroupBy('host_field::host_name', 'Host Name');
    await settle(fixture);
    expect(store.groupByKeys()).toEqual(['host_field::host_name']);
    expect(count(fixture, 'app-fleet-group-card')).toBe(1);
    expect(text(fixture, '.rt-count')).toContain('5 devices in 1 groups');
    expect(text(fixture, '.rt-count')).not.toContain('·');

    // Remove the only chip: the OSS page must show the full list again.
    const chip = store.activeChips()[0];
    searchService.searchFleet.calls.reset();
    store.removeFilterChip(chip);
    await settle(fixture);

    expect(store.activeChips()).toEqual([]);
    expect(store.isLandingState()).toBeFalse();
    expect(count(fixture, '.rt-landing')).toBe(0);
    expect(count(fixture, 'table.results-table tbody tr')).toBe(5);
    expect(text(fixture, '.rt-count')).toBe('5 devices');
    const lastRequest = searchService.searchFleet.calls.mostRecent().args[0];
    expect(lastRequest.filters).toBeUndefined();
    expect(lastRequest.groupHeader).toBeUndefined();
    expect(lastRequest.flat).toBeDefined();
  });

  it('returns to the full list when the search state is reset (left-nav click)', async () => {
    const {fixture, store} = await openPage('devices');
    store.openQuickGroupBy('host_field::host_name', 'Host Name');
    await settle(fixture);
    expect(count(fixture, 'app-fleet-group-card')).toBe(1);

    // Simulates the parent (MTT UI2) navigating the iframe back to a clean /devices URL.
    activatedRouteStub.snapshot.queryParams = {};
    await router.navigateByUrl('/devices');
    queryParams$.next({});
    await settle(fixture);

    expect(store.activeChips()).toEqual([]);
    expect(store.isLandingState()).toBeFalse();
    expect(count(fixture, '.rt-landing')).toBe(0);
    expect(count(fixture, 'table.results-table tbody tr')).toBe(5);
  });

  it('ignores a hand-crafted ?fleet=ats and never requests FLEET_ATS', async () => {
    const {fixture, store} = await openPage('devices', {'fleet': 'ats'});

    expect(store.fleet()).toBe('internal');
    expect(count(fixture, '.scope-switcher')).toBe(0);
    for (const call of searchService.getFleetSearchConfig.calls.all()) {
      expect(call.args[0].fleet).toBe(Fleet.FLEET_SELF);
    }
    for (const call of searchService.searchFleet.calls.all()) {
      expect(call.args[0].fleet).toBe(Fleet.FLEET_SELF);
    }
    expect(count(fixture, 'table.results-table tbody tr')).toBe(5);
  });

  it('keeps the landing page when the backend enables it, still without a fleet switcher', async () => {
    // Control case: the landing skip is driven by the backend flag, not by the OSS env.
    landingEnabled = true;
    const {fixture, store} = await openPage('devices');

    expect(store.isLandingDisabled()).toBeFalse();
    expect(store.isLandingState()).toBeTrue();
    expect(count(fixture, '.rt-landing')).toBe(1);
    expect(text(fixture, '.browse-all-btn')).toBe('Browse all 5 devices');
    expect(count(fixture, '.rt-try-ex')).toBe(2);
    expect(count(fixture, 'app-fleet-search-results')).toBe(0);
    expect(searchService.searchFleet).not.toHaveBeenCalled();
    expect(count(fixture, '.scope-switcher')).toBe(0);
  });

  it('does not frame the results card when not embedded in the ATS host', async () => {
    const {fixture} = await openPage('devices');
    expect(
      fixture.nativeElement.querySelector('.search-page-container.results-mode'),
    ).not.toBeNull();
    expect(
      fixture.nativeElement.querySelector('.search-page-container.ats-embedded'),
    ).toBeNull();
  });

  it('frames the results card on all sides when embedded in the ATS host', async () => {
    spyOn(UrlService.prototype, 'isInAtsEmbeddedMode').and.returnValue(true);
    const {fixture} = await openPage('devices');
    expect(
      fixture.nativeElement.querySelector(
        '.search-page-container.results-mode.ats-embedded',
      ),
    ).not.toBeNull();
  });

  it('remembers that the quick filter panel was hidden, separately for devices and hosts', async () => {
    const {fixture} = await openPage('devices');
    const hideButton = fixture.nativeElement.querySelector(
      '.preset-hide-btn',
    ) as HTMLButtonElement;
    expect(hideButton).not.toBeNull();

    hideButton.click();
    fixture.detectChanges();

    expect(count(fixture, '.preset-show-pill')).toBe(1);
    expect(
      window.localStorage.getItem('mh_fe_v6_filter_collapsed_devices'),
    ).toBe('true');
    expect(
      window.localStorage.getItem('mh_fe_v6_filter_collapsed_hosts'),
    ).toBeNull();
  });

  it('restores the hidden quick filter panel of the device page after a reload', async () => {
    window.localStorage.setItem('mh_fe_v6_filter_collapsed_devices', 'true');
    const {fixture} = await openPage('devices');

    expect(count(fixture, '.preset-hide-btn')).toBe(0);
    expect(count(fixture, '.preset-show-pill')).toBe(1);

    // Showing it again is remembered too.
    (
      fixture.nativeElement.querySelector(
        '.preset-show-pill',
      ) as HTMLButtonElement
    ).click();
    fixture.detectChanges();
    expect(count(fixture, '.preset-hide-btn')).toBe(1);
    expect(
      window.localStorage.getItem('mh_fe_v6_filter_collapsed_devices'),
    ).toBe('false');
  });

  it('keeps the host page quick filter panel visible when only the device one is hidden', async () => {
    window.localStorage.setItem('mh_fe_v6_filter_collapsed_devices', 'true');
    const {fixture} = await openPage('hosts');

    expect(count(fixture, '.preset-hide-btn')).toBe(1);
    expect(count(fixture, '.preset-show-pill')).toBe(0);
  });
});
