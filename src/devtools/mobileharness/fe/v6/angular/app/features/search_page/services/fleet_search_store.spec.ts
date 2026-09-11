import {Component} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, provideRouter, Router} from '@angular/router';
import {of, Subject, throwError} from 'rxjs';

import {
  Fleet,
  FleetChipResolverRequest,
  FleetChipResolverResponse,
  FleetSuggestion,
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

  it('updates an active chip in place and closes ValuePicker when selecting a Modify filter applyFilter suggestion', async () => {
    const store = await createStoreAt({});
    store.addFilterChip({
      key: 'dimension::model',
      pillKey: 'Model',
      pillCondition: 'Pixel 8',
      rawValues: ['Pixel 8'],
      fleetFilter: {
        key: 'dimension::model',
        simple: {values: [{value: 'Pixel 8'}]},
      },
    });
    store.addFilterChip({
      key: 'device_field::status',
      pillKey: 'Status',
      pillCondition: 'IDLE',
      rawValues: ['IDLE'],
      fleetFilter: {
        key: 'device_field::status',
        simple: {values: [{value: 'IDLE'}]},
      },
    });
    store.openValuePicker('dimension::model', null, 'Model');
    store.searchQuery.set('pixel 9');
    await flushAsync();
    expect(store.showValuePicker()).toBeTrue();
    mockSearchService.resolveFleetChips.calls.reset();

    const modifySuggestion: FleetSuggestion = {
      label: 'Modify filter',
      mainText: [
        {text: 'Model: add ', emphasized: false},
        {text: 'Pixel 9', emphasized: true},
      ],
      count: 12,
      countPrefix: '+',
      countUnit: 'devices',
      applyFilter: {
        resultingFilter: {
          key: 'dimension::model',
          simple: {values: [{value: 'Pixel 8'}, {value: 'Pixel 9'}]},
        },
        pillKey: 'Model',
        pillCondition: '2',
      },
    };
    store.selectSuggestion(modifySuggestion);
    await flushAsync();

    expect(store.showValuePicker()).toBeFalse();
    expect(store.searchQuery()).toBe('');
    expect(mockSearchService.resolveFleetChips).not.toHaveBeenCalled();
    expect(
      store
        .activeChips()
        .map((c) => ({key: c.key, cond: c.pillCondition, vals: c.rawValues})),
    ).toEqual([
      {
        key: 'dimension::model',
        cond: '2',
        vals: ['Pixel 8', 'Pixel 9'],
      },
      {
        key: 'device_field::status',
        cond: 'IDLE',
        vals: ['IDLE'],
      },
    ]);
    expect(store.serializedActiveFilters()).toEqual([
      'dimension::model~Pixel%208,Pixel%209',
      'device_field::status~IDLE',
    ]);

    // Bare-key Modify filter row (openPicker) keeps suggestions open and requests the picker.
    const bareKeySuggestion: FleetSuggestion = {
      label: 'Modify filter',
      mainText: [{text: 'Model', emphasized: false}],
      openPicker: {
        key: 'dimension::model',
        metadata: {keyDisplayName: 'Model', canUseAdvanced: true},
        viewExisting: {},
      },
    };
    store.selectSuggestion(bareKeySuggestion);
    await flushAsync();
    expect(store.showSuggestions()).toBeTrue();
    expect(store.pendingFilterOpenRequest()?.key).toBe('dimension::model');
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
    mockSearchService.getFleetSuggestions.and.returnValue(
      of({items: mockItems}),
    );

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
  describe('row selection and column management', () => {
    it('toggles row selection, computes selectionState, and clears selection', async () => {
      mockSearchService.searchFleet.and.returnValue(
        of({
          flat: {
            columns: [{key: 'id', displayName: 'Device ID'}],
            rows: [{id: 'dev-1'}, {id: 'dev-2'}],
            rangeStart: 1,
            rangeEnd: 2,
            total: 2,
          },
        }),
      );
      const store = await createStoreAt({});
      store.addFilterChip({
        key: 'device_field::status',
        pillKey: 'Status',
        pillCondition: 'IDLE',
        rawValues: ['IDLE'],
        fleetFilter: {
          key: 'device_field::status',
          simple: {values: [{value: 'IDLE'}]},
        },
      });
      await flushAsync();

      expect(store.selectionState()).toEqual({all: false, some: false});
      expect(store.rangeText()).toBe('1–2 of 2');

      // Ignore empty/undefined rows
      store.toggleSelectRows(undefined);
      store.toggleSelectRows([]);
      expect(store.selectedItems().size).toBe(0);

      // Select single row
      store.toggleSelectRows([{id: 'dev-1'}]);
      expect(store.selectedItems().has('dev-1')).toBeTrue();
      expect(store.selectionState()).toEqual({all: false, some: true});

      // Select all rows
      store.toggleSelectRows([{id: 'dev-1'}, {id: 'dev-2'}]);
      expect(store.selectedItems().size).toBe(2);
      expect(store.selectionState()).toEqual({all: true, some: false});

      // Deselect all when all are selected
      store.toggleSelectRows([{id: 'dev-1'}, {id: 'dev-2'}]);
      expect(store.selectedItems().size).toBe(0);

      // Clear selection
      store.toggleSelectRows([{id: 'dev-1'}]);
      store.clearSelection();
      expect(store.selectedItems().size).toBe(0);

      // Grouped rangeText formatting
      mockSearchService.searchFleet.and.returnValue(
        of({
          grouped: {
            groups: [],
            rangeStart: 1,
            rangeEnd: 5,
            totalGroups: 12,
            totalItems: 60,
          },
        }),
      );
      store.addFilterChip({
        key: 'dimension::model',
        pillKey: 'Model',
        pillCondition: '',
        isGroupBy: true,
      });
      await flushAsync();
      expect(store.rangeText()).toBe('1–5 of 12 groups');
    });

    it('updates visible columns and resets when matching defaults', async () => {
      mockSearchService.getFleetSearchConfig.and.returnValue(
        of({
          columns: {
            defaults: [
              {key: 'id', displayName: 'Device ID', locked: true},
              {key: 'status', displayName: 'Status'},
            ],
          },
        }),
      );
      const store = await createStoreAt({});

      expect(store.sortColumn()).toBe('id');

      const customCols = [
        {key: 'id', displayName: 'Device ID', locked: true},
        {key: 'model', displayName: 'Model'},
      ];
      store.setVisibleColumns(customCols);
      expect(store.visibleColumnDescriptors()).toEqual(customCols);

      // Setting back to default columns triggers resetVisibleColumns
      const defaultCols = [
        {key: 'id', displayName: 'Device ID', locked: true},
        {key: 'status', displayName: 'Status'},
      ];
      store.setVisibleColumns(defaultCols);
      expect(store.visibleColumnDescriptors()).toEqual(defaultCols);
    });
  });

  describe('row selection in selectedItems', () => {
    it('selects all rows including unrecognizable device IDs without filtering and keys by deviceId@@hostName', async () => {
      const store = await createStoreAt({});
      const rows = [
        {
          id: '43021FDAQ000UM',
          cells: [
            {
              link: {
                text: '43021FDAQ000UM',
                target: {
                  device: {
                    id: '43021FDAQ000UM',
                    hostName: 'mh-lab-01.google.com',
                  },
                },
              },
            },
          ],
        },
        {
          id: 'FailedToInitDevice-0',
          cells: [
            {
              link: {
                text: 'FailedToInitDevice-0',
                target: {
                  device: {
                    id: 'FailedToInitDevice-0',
                    hostName: 'mh-lab-02.google.com',
                  },
                },
              },
            },
          ],
        },
      ];

      store.toggleSelectRows(rows);

      expect(Array.from(store.selectedItems().keys())).toEqual([
        '43021FDAQ000UM@@mh-lab-01.google.com',
        'FailedToInitDevice-0@@mh-lab-02.google.com',
      ]);
      expect(Array.from(store.selectedItems().values())).toEqual(rows);

      // Toggling again when all are selected deselects all
      store.toggleSelectRows(rows);
      expect(store.selectedItems().size).toBe(0);
      expect(Array.from(store.selectedItems().values())).toEqual([]);
    });

    it('toggles individual row selection via toggleSelectRows([row]) and falls back to host cell link when device link lacks hostName', async () => {
      const store = await createStoreAt({});
      const rowWithHostCell = {
        id: 'DisconnectedDevice-1',
        cells: [
          {text: {value: 'DisconnectedDevice-1'}},
          {
            link: {
              text: 'mh-lab-03.google.com',
              target: {host: {hostName: 'mh-lab-03.google.com'}},
            },
          },
        ],
      };

      store.toggleSelectRows([rowWithHostCell]);
      expect(
        store.selectedItems().has('DisconnectedDevice-1@@mh-lab-03.google.com'),
      ).toBeTrue();
      expect(Array.from(store.selectedItems().values())).toEqual([
        rowWithHostCell,
      ]);

      store.toggleSelectRows([rowWithHostCell]);
      expect(
        store.selectedItems().has('DisconnectedDevice-1@@mh-lab-03.google.com'),
      ).toBeFalse();
      expect(Array.from(store.selectedItems().values())).toEqual([]);
    });

    it('distinguishes and independently selects error devices sharing non-unique deviceId (e.g. 000000000000) across different hosts', async () => {
      const store = await createStoreAt({});
      const zeroRowHost1 = {
        id: '000000000000',
        cells: [
          {
            link: {
              text: '000000000000',
              target: {
                device: {
                  id: '000000000000',
                  hostName: 'mh-lab-01.google.com',
                },
              },
            },
          },
        ],
      };
      const zeroRowHost2 = {
        id: '000000000000',
        cells: [
          {
            link: {
              text: '000000000000',
              target: {
                device: {
                  id: '000000000000',
                  hostName: 'mh-lab-02.google.com',
                },
              },
            },
          },
        ],
      };

      // Select only host 1's 000000000000 row
      store.toggleSelectRows([zeroRowHost1]);
      expect(store.selectedItems().size).toBe(1);
      expect(
        store.selectedItems().has('000000000000@@mh-lab-01.google.com'),
      ).toBeTrue();
      expect(
        store.selectedItems().has('000000000000@@mh-lab-02.google.com'),
      ).toBeFalse();

      // Selecting both rows adds host 2's 000000000000 row without overwriting host 1's
      store.toggleSelectRows([zeroRowHost1, zeroRowHost2]);
      expect(store.selectedItems().size).toBe(2);
      expect(Array.from(store.selectedItems().values())).toEqual([
        zeroRowHost1,
        zeroRowHost2,
      ]);

      // Toggling only host 1's row leaves host 2's row selected
      store.toggleSelectRows([zeroRowHost1]);
      expect(store.selectedItems().size).toBe(1);
      expect(Array.from(store.selectedItems().values())).toEqual([
        zeroRowHost2,
      ]);
    });

    it('builds initial ValuePicker state for simple, advanced, and empty chips and applies ValuePicker', async () => {
      mockSearchService.resolveFleetChips.and.returnValue(
        of({
          filterChips: [
            {invalid: {reason: 'Invalid chip'}},
            {
              valid: {
                keyDisplayName: 'Model',
                pillKey: 'Model',
                pillCondition: 'pixel',
              },
            },
          ],
        }),
      );
      const store = await createStoreAt({});

      // Open without active chip
      store.openValuePicker('dimension::model', null, 'Model', undefined, [
        'pixel-8',
      ]);
      expect(Array.from(store.pickerState().selectedValues)).toEqual([
        'pixel-8',
      ]);

      // Apply value picker (triggers unwrapValidFilterChip for invalid + valid)
      store.applyValuePicker({
        selected: ['pixel'],
        negate: false,
        isAdvanced: false,
      });
      await flushAsync();
      expect(mockSnackBarService.showWarning).toHaveBeenCalledWith(
        'Invalid chip',
      );
      expect(
        store.activeChips().some((c) => c.key === 'dimension::model'),
      ).toBeTrue();

      // Open with simple active chip
      store.openValuePicker('dimension::model', null, 'Model');
      expect(Array.from(store.pickerState().selectedValues)).toEqual(['pixel']);

      // Open with advanced active chip
      store.addFilterChip({
        key: 'dimension::pool',
        pillKey: 'Pool',
        pillCondition: '^shared',
        complex: {matchesRegex: {value: '^shared'}},
      });
      store.openValuePicker('dimension::pool', null, 'Pool');
      expect(store.pickerState().advanced.active).toBeTrue();
      expect(store.pickerState().advanced.text).toBe('^shared');
    });
  });

  it('applies advanced matching filter and updates correctly when value is changed', async () => {
    mockSearchService.resolveFleetChips.and.callFake(
      (req: FleetChipResolverRequest) =>
        of({
          filterChips: (req.filters || []).map((f) => ({
            valid: {
              pillKey: f.key,
              pillCondition: f.complex?.startsWith?.value
                ? `^${f.complex.startsWith.value}`
                : 'custom',
            },
          })),
        }),
    );

    const store = await createStoreAt({});

    // 1. First apply: prefix 'Pixel'
    store.openValuePicker('model', null, 'Model');
    store.applyValuePicker({
      selected: [],
      negate: false,
      isAdvanced: true,
      advMode: 'prefix',
      advText: 'Pixel',
    });

    expect(store.activeChips().length).toBe(1);
    expect(store.activeChips()[0].pillCondition).toBe('^Pixel');
    expect(store.activeChips()[0].complex).toEqual({
      startsWith: {value: 'Pixel'},
    });

    // 2. User edits chip and changes value to 'Pixel 9'
    store.openValuePicker('model');
    expect(store.effectivePickerState().advanced.active).toBeTrue();
    expect(store.effectivePickerState().advanced.mode).toBe('prefix');
    expect(store.effectivePickerState().advanced.text).toBe('Pixel');
    expect(store.effectivePickerState().advanced.values).toEqual([]);

    store.applyValuePicker({
      selected: [],
      negate: false,
      isAdvanced: true,
      advMode: 'prefix',
      advText: 'Pixel 9',
    });

    // Verify chip was updated in place with new value
    expect(store.activeChips().length).toBe(1);
    expect(store.activeChips()[0].pillCondition).toBe('^Pixel 9');
    expect(store.activeChips()[0].complex).toEqual({
      startsWith: {value: 'Pixel 9'},
    });
  });

  it('merges keyMetadataMap and columnDescriptions with live BFF sources overriding saved visible descriptors', async () => {
    localStorage.clear();
    try {
      mockSearchService.getFleetPromotedKeys.and.returnValue(
        of({
          filterKeys: [
            {
              key: 'dimension::label',
              metadata: {
                keyDisplayName: 'Label',
                description: 'Promoted label description.',
                canUseAdvanced: true,
              },
            },
            {
              key: 'device_field::status',
              metadata: {keyDisplayName: 'Status'},
            },
          ],
          groupByKeys: [
            {
              key: 'dimension::label',
              displayName: 'Label',
            },
            {
              key: 'device_field::version',
              displayName: 'Version',
            },
            {
              key: 'device_field::driver',
              displayName: 'Driver',
            },
          ],
        }),
      );
      mockSearchService.getFleetSearchConfig.and.returnValue(
        of({
          columns: {
            defaults: [
              {key: 'device_field::uuid', displayName: 'UUID', locked: true},
              {
                key: 'dimension::default_col',
                displayName: 'Default Col',
                description: 'Default col description.',
              },
              {
                key: 'device_field::version',
                displayName: 'Version',
                description: 'Default version description.',
              },
            ],
          },
        }),
      );
      mockSearchService.searchFleet.and.returnValue(
        of({
          grouped: {
            groupByKeys: [
              {
                key: 'dimension::pool',
                displayName: 'Pool',
                description: 'Grouped pool description.',
              },
            ],
          },
        }),
      );

      const store = await createStoreAt({});
      store.addFilterChip({
        key: 'device_field::status',
        pillKey: 'Status',
        pillCondition: 'IDLE',
        rawValues: ['IDLE'],
        fleetFilter: {
          key: 'device_field::status',
          simple: {values: [{value: 'IDLE'}]},
        },
      });
      store.addFilterChip({
        key: 'dimension::chip_only',
        pillKey: 'Chip Only',
        pillCondition: 'v1',
        rawValues: ['v1'],
        metadata: {
          keyDisplayName: 'Chip Only',
          description: 'Active chip description.',
        },
        fleetFilter: {
          key: 'dimension::chip_only',
          simple: {values: [{value: 'v1'}]},
        },
      });
      store.setVisibleColumns([
        {key: 'device_field::uuid', displayName: 'UUID', locked: true},
        {
          key: 'device_field::version',
          displayName: 'Version',
          description: 'Stale cached version description.',
        },
        {
          key: 'dimension::label',
          displayName: 'Label',
          description: 'Stale cached label description.',
        },
        {
          key: 'dimension::saved_col',
          displayName: 'Saved Col',
          description: 'Saved col description.',
        },
      ]);
      await flushAsync();

      expect(store.displayColumns()).toEqual([
        {key: 'device_field::uuid', displayName: 'UUID'},
        {
          key: 'device_field::version',
          displayName: 'Version',
          description: 'Stale cached version description.',
        },
        {
          key: 'dimension::label',
          displayName: 'Label',
          description: 'Stale cached label description.',
        },
        {
          key: 'dimension::saved_col',
          displayName: 'Saved Col',
          description: 'Saved col description.',
        },
      ]);
      expect(store.columnDescriptions().get('dimension::saved_col')).toBe(
        'Saved col description.',
      );
      expect(store.columnDescriptions().get('dimension::label')).toBe(
        'Promoted label description.',
      );
      expect(store.columnDescriptions().get('dimension::default_col')).toBe(
        'Default col description.',
      );
      expect(store.columnDescriptions().get('dimension::chip_only')).toBe(
        'Active chip description.',
      );
      expect(
        store.columnDescriptions().has('device_field::status'),
      ).toBeFalse();
      expect(store.columnDescriptions().get('device_field::version')).toBe(
        'Default version description.',
      );
      expect(store.columnDescriptions().get('dimension::pool')).toBe(
        'Grouped pool description.',
      );

      store.openValuePicker('dimension::label');
      expect(store.pickerConfig()?.description).toBe(
        'Promoted label description.',
      );
      expect(store.pickerConfig()?.canUseAdvanced).toBeTrue();

      store.openValuePicker('device_field::version');
      expect(store.pickerConfig()?.title).toBe('Version');
      expect(store.pickerConfig()?.description).toBe(
        'Default version description.',
      );

      store.openValuePicker('device_field::driver');
      expect(store.pickerConfig()?.title).toBe('Driver');
      expect(store.pickerConfig()?.description).toBeUndefined();

      store.openValuePicker('dimension::chip_only');
      expect(store.pickerConfig()?.description).toBe(
        'Active chip description.',
      );

      store.openQuickFilter('dimension::pending_only', 'Pending Only', {
        keyDisplayName: 'Pending Only',
        description: 'Pending filter description.',
      });
      store.openValuePicker('device_field::status');
      expect(store.pickerConfig()?.description).toBeUndefined();

      // Reopen the staged pending filter without passing explicit metadata.
      store.openValuePicker('dimension::pending_only');
      expect(store.pickerConfig()?.description).toBe(
        'Pending filter description.',
      );

      mockSearchService.searchFleet.and.returnValue(
        of({
          flat: {
            columns: [
              {
                key: 'dimension::flat_col',
                displayName: 'Flat Col',
                description: 'Flat col description.',
              },
            ],
            rows: [],
          },
        }),
      );
      store.addFilterChip({
        key: 'dimension::label',
        pillKey: 'Label',
        pillCondition: 'v1',
        rawValues: ['v1'],
        fleetFilter: {
          key: 'dimension::label',
          simple: {values: [{value: 'v1'}]},
        },
      });
      await flushAsync();
      expect(store.columnDescriptions().get('dimension::flat_col')).toBe(
        'Flat col description.',
      );
    } finally {
      localStorage.clear();
    }
  });
});
