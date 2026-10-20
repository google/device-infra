import {Component} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, provideRouter, Router} from '@angular/router';
import {of, Subject, throwError} from 'rxjs';

import {
  Fleet,
  FleetChipResolverResponse,
  SearchEntity,
  TranslateArsenalSearchResponse,
} from '../../../core/models/search';
import {
  SEARCH_SERVICE,
  SearchService,
} from '../../../core/services/search/search_service';
import {SnackBarService} from '../../../shared/services/snackbar_service';
import {SearchBoxSuggestion} from '../models';
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
      'getFleetSearchConfig',
      'searchFleet',
      'getFleetSuggestions',
      'getFleetPromotedKeys',
      'getFleetValueList',
      'resolveFleetChips',
      'translateArsenalSearch',
    ]);
    mockSearchService.getFleetSearchConfig.and.returnValue(of({}));
    mockSearchService.searchFleet.and.returnValue(of({}));
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

  describe('legacy ?arsenal_query cold load', () => {
    const arsenalQuery = 'filter=status:match:values:IDLE&group_by=model';

    afterEach(() => {
      localStorage.clear();
    });

    it('applies the translated fleet and chips and drops arsenal_query from the URL', async () => {
      const arsenalQueryWithColumns =
        'filter=status:match:values:IDLE&column=uuid&group_by=model';
      const columns = [
        {key: 'device_field::uuid', displayName: 'UUID', locked: true},
      ];
      mockSearchService.translateArsenalSearch.and.returnValue(
        of({
          fleet: Fleet.FLEET_ATS,
          filters: [
            {key: 'device_field::status', simple: {values: [{value: 'IDLE'}]}},
          ],
          filterChips: [{valid: {pillKey: 'Status', pillCondition: 'IDLE'}}],
          groupByKeys: ['dimension::model'],
          groupByChips: [{valid: {pillKey: 'Model', displayName: 'Model'}}],
          columns,
        }),
      );
      const navigateSpy = spyOn(router, 'navigate').and.callThrough();

      const store = await createStoreAt({
        'arsenal_query': arsenalQueryWithColumns,
      });

      expect(mockSearchService.translateArsenalSearch).toHaveBeenCalledOnceWith(
        {
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          filters: [
            {
              key: 'status',
              matchType: 'match',
              valueType: 'values',
              values: ['IDLE'],
            },
          ],
          columns: ['uuid'],
          groupByKeys: ['model'],
        },
      );
      expect(store.fleet()).toBe('ats');
      expect(store.visibleColumnDescriptors()).toEqual(columns);
      expect(localStorage.getItem('mh_fe_v6_visible_columns_devices_ats')).toBe(
        JSON.stringify(columns),
      );
      expect(store.activeChips().map((c) => c.key)).toEqual([
        'device_field::status',
        'dimension::model',
      ]);
      expect(navigateSpy).toHaveBeenCalledWith(
        [],
        jasmine.objectContaining({replaceUrl: true}),
      );
      expect(mockSearchService.searchFleet).toHaveBeenCalledWith(
        jasmine.objectContaining({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          fleet: Fleet.FLEET_ATS,
          filters: [
            {
              key: 'device_field::status',
              simple: {values: [{value: 'IDLE'}]},
            },
          ],
          groupHeader: jasmine.objectContaining({
            groupBy: ['dimension::model'],
          }),
        }),
      );
      expect(router.url).not.toContain('arsenal_query');
    });

    it('drops invalid translated chips, stays on the landing page, and does not persist default columns', async () => {
      const defaultColumns = [
        {key: 'device_field::uuid', displayName: 'UUID', locked: true},
      ];
      mockSearchService.translateArsenalSearch.and.returnValue(
        of({
          fleet: Fleet.FLEET_SELF,
          filters: [{key: 'no_such_key', simple: {values: [{value: 'x'}]}}],
          filterChips: [{invalid: {reason: 'Unknown filter key: no_such_key'}}],
          groupByKeys: ['version'],
          groupByChips: [{invalid: {reason: 'Unknown group-by key: version'}}],
          columns: defaultColumns,
        }),
      );

      const store = await createStoreAt({'arsenal_query': arsenalQuery});

      expect(store.fleet()).toBe('internal');
      expect(store.visibleColumnDescriptors()).toEqual([]);
      expect(
        localStorage.getItem('mh_fe_v6_visible_columns_devices_internal'),
      ).toBeNull();
      expect(store.activeChips()).toEqual([]);
      expect(store.browseAll()).toBeFalse();
      expect(store.isLandingState()).toBeTrue();
      expect(mockSearchService.searchFleet).not.toHaveBeenCalled();
      expect(mockSnackBarService.showWarning).toHaveBeenCalledWith(
        'Unknown filter key: no_such_key',
      );
      expect(mockSnackBarService.showWarning).toHaveBeenCalledWith(
        'Unknown group-by key: version',
      );
    });

    it('shows a warning and keeps arsenal_query when translation fails', async () => {
      mockSearchService.translateArsenalSearch.and.returnValue(
        throwError(() => new Error('translate failed')),
      );

      const store = await createStoreAt({'arsenal_query': arsenalQuery});

      expect(mockSnackBarService.showWarning).toHaveBeenCalledWith(
        'Could not translate legacy Arsenal query.',
      );
      expect(store.activeChips()).toEqual([]);
      expect(mockSearchService.searchFleet).not.toHaveBeenCalled();
      expect(router.url).toContain('arsenal_query');
    });

    it('does not translate the same arsenal_query twice when query params re-emit', async () => {
      mockSearchService.translateArsenalSearch.and.returnValue(
        throwError(() => new Error('translate failed')),
      );

      await createStoreAt({'arsenal_query': arsenalQuery});
      queryParams$.next({'arsenal_query': arsenalQuery});
      await flushAsync();

      expect(mockSearchService.translateArsenalSearch).toHaveBeenCalledTimes(1);
    });

    it('browses all devices in the translated fleet for a valid universe-only query', async () => {
      mockSearchService.translateArsenalSearch.and.returnValue(
        of({
          fleet: Fleet.FLEET_ATS,
          filters: [],
          filterChips: [],
          groupByKeys: [],
          groupByChips: [],
        }),
      );

      const store = await createStoreAt({
        'arsenal_query': 'filter=universe:not_match:values:google_1p',
      });

      expect(store.fleet()).toBe('ats');
      expect(store.activeChips()).toEqual([]);
      expect(store.browseAll()).toBeTrue();
      expect(store.isLandingState()).toBeFalse();
      expect(mockSearchService.searchFleet).toHaveBeenCalledWith(
        jasmine.objectContaining({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          fleet: Fleet.FLEET_ATS,
          filters: undefined,
        }),
      );
    });

    it('preserves fleet=ats on NavigationEnd when arsenal_query is present without fleet', async () => {
      const translateSubject = new Subject<TranslateArsenalSearchResponse>();
      mockSearchService.translateArsenalSearch.and.returnValue(
        translateSubject,
      );

      const store = await createStoreAt({'fleet': 'ats'});
      expect(store.fleet()).toBe('ats');

      activatedRouteStub.snapshot.queryParams = {'arsenal_query': arsenalQuery};
      await router.navigateByUrl(
        `/devices?arsenal_query=${encodeURIComponent(arsenalQuery)}`,
      );
      queryParams$.next({'arsenal_query': arsenalQuery});
      await flushAsync();

      expect(store.fleet()).toBe('ats');
      expect(translateSubject.observed).toBeTrue();
    });

    it('cancels in-flight chip resolution and ignores superseded translation when state changes', async () => {
      const resolveSubject = new Subject<FleetChipResolverResponse>();
      const translateSubject1 = new Subject<TranslateArsenalSearchResponse>();
      const translateSubject2 = new Subject<TranslateArsenalSearchResponse>();
      const translateSubject3 = new Subject<TranslateArsenalSearchResponse>();
      mockSearchService.resolveFleetChips.and.returnValue(resolveSubject);
      mockSearchService.translateArsenalSearch.and.returnValues(
        translateSubject1,
        translateSubject2,
        translateSubject3,
      );

      const store = await createStoreAt({'f': 'device_field::status~BUSY'});
      expect(resolveSubject.observed).toBeTrue();

      // 1. Intercepting arsenal_query cancels in-flight resolveFiltersSubject.
      activatedRouteStub.snapshot.queryParams = {'arsenal_query': arsenalQuery};
      queryParams$.next({'arsenal_query': arsenalQuery});
      await flushAsync();

      expect(resolveSubject.observed).toBeFalse();
      expect(translateSubject1.observed).toBeTrue();
      resolveSubject.next({
        filterChips: [{valid: {pillKey: 'Status', pillCondition: 'BUSY'}}],
      });
      await flushAsync();

      // 2. Switching fleet cancels in-flight translation; late response is ignored.
      store.setFleet('ats');
      await flushAsync();

      expect(translateSubject1.observed).toBeFalse();
      translateSubject1.next({
        fleet: Fleet.FLEET_SELF,
        filters: [
          {key: 'device_field::status', simple: {values: [{value: 'IDLE'}]}},
        ],
        filterChips: [{valid: {pillKey: 'Status', pillCondition: 'IDLE'}}],
      });
      await flushAsync();
      expect(store.fleet()).toBe('ats');
      expect(store.activeChips()).toEqual([]);

      // 3. Navigating to a canonical ?f= URL cancels in-flight translation; late response is ignored.
      const secondArsenalQuery = 'filter=status:match:values:MISSING';
      activatedRouteStub.snapshot.queryParams = {
        'arsenal_query': secondArsenalQuery,
      };
      queryParams$.next({'arsenal_query': secondArsenalQuery});
      await flushAsync();
      expect(translateSubject2.observed).toBeTrue();

      activatedRouteStub.snapshot.queryParams = {
        'f': 'device_field::status~LAB_MISSING',
      };
      queryParams$.next({'f': 'device_field::status~LAB_MISSING'});
      await flushAsync();
      expect(translateSubject2.observed).toBeFalse();
      translateSubject2.next({
        fleet: Fleet.FLEET_ATS,
        filters: [
          {key: 'device_field::status', simple: {values: [{value: 'MISSING'}]}},
        ],
        filterChips: [{valid: {pillKey: 'Status', pillCondition: 'MISSING'}}],
      });
      await flushAsync();
      expect(store.activeChips().map((c) => c.pillCondition)).toEqual([
        'LAB_MISSING',
      ]);

      // 4. Editing chips cancels in-flight resolveFiltersSubject.
      store.addFilterChip({
        key: 'dimension::pool',
        pillKey: 'Pool',
        pillCondition: 'shared',
        rawValues: ['shared'],
      });
      await flushAsync();
      expect(resolveSubject.observed).toBeFalse();

      // 5. Leaving the active route cancels in-flight translation; late error does not show warning.
      const thirdArsenalQuery = 'filter=status:match:values:DYING';
      activatedRouteStub.snapshot.queryParams = {
        'arsenal_query': thirdArsenalQuery,
      };
      queryParams$.next({'arsenal_query': thirdArsenalQuery});
      await flushAsync();
      expect(translateSubject3.observed).toBeTrue();

      await router.navigateByUrl('/hosts');
      await flushAsync();
      expect(translateSubject3.observed).toBeFalse();
      translateSubject3.error(new Error('late failure'));
      await flushAsync();
      expect(mockSnackBarService.showWarning).not.toHaveBeenCalled();
    });
  });

  it('calls getFleetSuggestions with empty input when showSuggestions is true and no filters or groupby', async () => {
    const store = await createStoreAt({});
    mockSearchService.getFleetSuggestions.calls.reset();

    expect(store.activeChips()).toEqual([]);
    expect(store.searchQuery()).toBe('');

    store.showSuggestions.set(true);
    await flushAsync();

    expect(mockSearchService.getFleetSuggestions).toHaveBeenCalledWith(
      jasmine.objectContaining({
        input: '',
        filters: undefined,
        groupBy: undefined,
        limit: 10,
      }),
    );
    expect(store.isSuggestionsPanelOpen()).toBeTrue();
  });

  it('populates suggestions directly from backend and tracks loading state', async () => {
    const mockItems: SearchBoxSuggestion[] = [
      {
        label: 'Filter by Status',
        mainText: [{text: 'Status'}],
        openPicker: {key: 'status', metadata: {keyDisplayName: 'Status'}},
        count: 42,
      },
    ];
    mockSearchService.getFleetSuggestions.and.returnValue(of({items: mockItems}));

    const store = await createStoreAt({});
    expect(store.suggestions()).toEqual([]);

    store.showSuggestions.set(true);
    await flushAsync();

    expect(store.suggestions()).toEqual(mockItems);
    expect(store.isSuggestionsLoading()).toBeFalse();
  });

  it('handles selectSuggestion with openPicker, applyFilter, and addGroupBy correctly', async () => {
    const store = await createStoreAt({});

    // 1. Safe no-op on null/undefined
    store.selectSuggestion(null as unknown as SearchBoxSuggestion);

    // 2. openPicker keeps showSuggestions true and sets pendingFilter
    store.searchQuery.set('stat');
    store.selectSuggestion({
      label: 'Filter by Status',
      mainText: [{text: 'Status'}],
      openPicker: {key: 'status', metadata: {keyDisplayName: 'Status'}},
    });

    expect(store.searchQuery()).toBe('');
    expect(store.showSuggestions()).toBeTrue();
    expect(store.pendingFilterKey()).toBe('status');

    // 3. applyFilter with autoCollapseAfterApply = true closes suggestions and adds chip
    store.autoCollapseAfterApply.set(true);
    store.selectSuggestion({
      label: 'Status is IDLE',
      mainText: [{text: 'IDLE'}],
      applyFilter: {
        resultingFilter: {
          key: 'status',
          simple: {values: [{value: 'IDLE'}]},
        },
        pillCondition: 'IDLE',
      },
    });

    expect(store.showSuggestions()).toBeFalse();
    expect(store.activeChips().some((c) => c.key === 'status')).toBeTrue();

    // 4. applyFilter with autoCollapseAfterApply = false keeps suggestions open
    store.autoCollapseAfterApply.set(false);
    store.selectSuggestion({
      label: 'Model is Pixel 9',
      mainText: [{text: 'Pixel 9'}],
      applyFilter: {
        resultingFilter: {
          key: 'model',
          simple: {values: [{value: 'Pixel 9'}]},
        },
        pillCondition: 'Pixel 9',
      },
    });

    expect(store.showSuggestions()).toBeTrue();
    expect(store.activeChips().some((c) => c.key === 'model')).toBeTrue();

    // 5. addGroupBy adds group by chip
    store.selectSuggestion({
      label: 'Group by Model',
      mainText: [{text: 'Model'}],
      addGroupBy: {key: 'model', pillKey: 'Model'},
    });

    expect(store.groupByChips().some((c) => c.key === 'model')).toBeTrue();
  });

  it('computes isSuggestionsPanelOpen correctly on FleetSearchStore', async () => {
    const store = await createStoreAt({});

    // Fleet mode opens popover whenever showSuggestions is true
    store.showSuggestions.set(false);
    expect(store.isSuggestionsPanelOpen()).toBeFalse();

    store.showSuggestions.set(true);
    expect(store.isSuggestionsPanelOpen()).toBeTrue();
  });
});
