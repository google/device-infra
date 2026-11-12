import {Component} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, provideRouter, Router} from '@angular/router';
import {of, Subject, throwError} from 'rxjs';

import {
  Fleet,
  FleetChipResolverRequest,
  FleetChipResolverResponse,
  FleetSearchConfig,
  FleetSearchResults,
  FleetSuggestion,
  IntComparisonOperator,
  SearchEntity,
  TranslateArsenalSearchResponse,
} from '../../../core/models/search';
import {Environment} from '../../../core/services/environment';
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
  let mockEnvironment: jasmine.SpyObj<Environment>;
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

    // Google-internal build by default; OSS tests flip this before the store is created.
    mockEnvironment = jasmine.createSpyObj<Environment>('Environment', [
      'isGoogleInternal',
    ]);
    mockEnvironment.isGoogleInternal.and.returnValue(true);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {path: 'devices', component: DummyComponent},
          {path: 'hosts', component: DummyComponent},
        ]),
        {provide: SEARCH_SERVICE, useValue: mockSearchService},
        {provide: SnackBarService, useValue: mockSnackBarService},
        {provide: Environment, useValue: mockEnvironment},
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

  /** Simulates a full page load / refresh of `/devices` (or `/hosts`) with the given query params. */
  async function createStoreAt(
    queryParams: Record<string, unknown>,
    path: 'devices' | 'hosts' = 'devices',
  ): Promise<FleetSearchStore> {
    activatedRouteStub.routeConfig.path = path;
    activatedRouteStub.snapshot.queryParams = queryParams;
    const search = new URLSearchParams(
      queryParams as Record<string, string>,
    ).toString();
    await router.navigateByUrl(`/${path}${search ? '?' + search : ''}`);
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

  it('shows the fleet scope switcher in the Google-internal build', async () => {
    const store = await createStoreAt({});

    expect(store.isFleetSwitchable).toBeTrue();
    expect(store.showScopeSwitcher()).toBeTrue();
  });

  describe('OSS build (single fleet, FLEET_SELF only)', () => {
    beforeEach(() => {
      mockEnvironment.isGoogleInternal.and.returnValue(false);
    });

    it('hides the fleet scope switcher', async () => {
      const store = await createStoreAt({});

      expect(store.isFleetSwitchable).toBeFalse();
      expect(store.showScopeSwitcher()).toBeFalse();
    });

    it('ignores fleet=ats from the URL on page load and only loads the FLEET_SELF config', async () => {
      const store = await createStoreAt({'fleet': 'ats'});

      expect(store.fleet()).toBe('internal');
      expect(mockSearchService.getFleetSearchConfig).toHaveBeenCalledWith(
        jasmine.objectContaining({fleet: Fleet.FLEET_SELF}),
      );
      expect(mockSearchService.getFleetSearchConfig).not.toHaveBeenCalledWith(
        jasmine.objectContaining({fleet: Fleet.FLEET_ATS}),
      );
    });

    it('keeps the internal fleet when navigating to a URL carrying fleet=ats', async () => {
      const store = await createStoreAt({});

      activatedRouteStub.snapshot.queryParams = {'fleet': 'ats'};
      await router.navigateByUrl('/devices?fleet=ats');
      queryParams$.next({'fleet': 'ats'});
      await flushAsync();

      expect(store.fleet()).toBe('internal');
    });
  });

  describe('landing page controlled by searchConfig.landing.enabled', () => {
    const defaultColumns = [
      {key: 'device_field::uuid', displayName: 'UUID', locked: true},
      {key: 'device_field::status', displayName: 'Status'},
    ];
    const statusChip = {
      key: 'device_field::status',
      pillKey: 'Status',
      pillCondition: 'IDLE',
      rawValues: ['IDLE'],
      fleetFilter: {
        key: 'device_field::status',
        simple: {values: [{value: 'IDLE'}]},
      },
    };

    it('stays on the landing page without searching when landing is enabled', async () => {
      mockSearchService.getFleetSearchConfig.and.returnValue(
        of({
          columns: {defaults: defaultColumns},
          landing: {enabled: true, browseAllCount: 5},
        }),
      );

      const store = await createStoreAt({});

      expect(store.isLandingDisabled()).toBeFalse();
      expect(store.isLandingState()).toBeTrue();
      expect(mockSearchService.searchFleet).not.toHaveBeenCalled();
    });

    it('treats an omitted `enabled` as disabled when a landing config is present (ESF omits proto3 defaults)', async () => {
      // The OSS Envoy transcoder prints `"enabled": false`; the internal ESF
      // layer omits the proto3 default, leaving `landing` without the key.
      mockSearchService.getFleetSearchConfig.and.returnValue(
        of({
          columns: {defaults: defaultColumns},
          landing: {browseAllCount: 5},
        }),
      );

      const store = await createStoreAt({});

      expect(store.isLandingDisabled()).toBeTrue();
      expect(store.isLandingState()).toBeFalse();
    });

    it('keeps the landing page enabled while the config is loading or has no landing section', async () => {
      mockSearchService.getFleetSearchConfig.and.returnValue(
        of({columns: {defaults: defaultColumns}}),
      );

      const store = await createStoreAt({});

      expect(store.isLandingDisabled()).toBeFalse();
      expect(store.isLandingState()).toBeTrue();
      expect(mockSearchService.searchFleet).not.toHaveBeenCalled();
    });

    it('skips the landing page and lists all items when landing is disabled (OSS)', async () => {
      mockSearchService.getFleetSearchConfig.and.returnValue(
        of({
          columns: {defaults: defaultColumns},
          landing: {enabled: false, browseAllCount: 5},
        }),
      );

      const store = await createStoreAt({});

      expect(store.isLandingDisabled()).toBeTrue();
      expect(store.isLandingState()).toBeFalse();
      expect(store.browseAll()).toBeFalse();
      expect(mockSearchService.searchFleet).toHaveBeenCalledWith(
        jasmine.objectContaining({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          fleet: Fleet.FLEET_SELF,
          filters: undefined,
          flat: jasmine.objectContaining({
            columns: ['device_field::uuid', 'device_field::status'],
          }),
        }),
      );
    });

    it('returns to the full list instead of the landing page when the last chip is removed or the state is reset', async () => {
      mockSearchService.getFleetSearchConfig.and.returnValue(
        of({
          columns: {defaults: defaultColumns},
          landing: {enabled: false, browseAllCount: 5},
        }),
      );
      const store = await createStoreAt({});

      store.addFilterChip(statusChip);
      await flushAsync();
      expect(store.isLandingState()).toBeFalse();
      expect(mockSearchService.searchFleet.calls.mostRecent().args[0]).toEqual(
        jasmine.objectContaining({filters: [statusChip.fleetFilter]}),
      );

      mockSearchService.searchFleet.calls.reset();
      store.removeFilterChip(statusChip);
      await flushAsync();
      expect(store.activeChips()).toEqual([]);
      expect(store.isLandingState()).toBeFalse();
      expect(mockSearchService.searchFleet.calls.mostRecent().args[0]).toEqual(
        jasmine.objectContaining({filters: undefined}),
      );

      store.addFilterChip(statusChip);
      await flushAsync();
      store.restoreDefaultState(false);
      await flushAsync();
      expect(store.activeChips()).toEqual([]);
      expect(store.isLandingState()).toBeFalse();
    });
  });

  describe('recovery from saved columns the BFF no longer knows', () => {
    const defaultColumns = [
      {key: 'device_field::uuid', displayName: 'UUID', locked: true},
      {key: 'device_field::status', displayName: 'Status'},
    ];
    const staleColumns = [
      {key: 'field::uuid', displayName: 'Device ID / UUID', locked: true},
      {key: 'dim::pool', displayName: 'Pool'},
    ];
    const rows = [{id: 'dev-1'}, {id: 'dev-2'}];

    /** OSS-style error body of `POST /v6/fleet-search` for an unknown column key. */
    function unknownKeyError(message: string): unknown {
      return {status: 500, error: {code: 13, message, details: []}};
    }

    /** Fails the first `failures` searches with `error`, then returns `rows`. */
    function failSearchesThenSucceed(failures: number, error: unknown) {
      let calls = 0;
      mockSearchService.searchFleet.and.callFake(() => {
        calls++;
        return calls <= failures
          ? throwError(() => error)
          : of({flat: {rows, rangeStart: 1, rangeEnd: 2, totalItems: 2}});
      });
    }

    beforeEach(() => {
      localStorage.clear();
      mockSearchService.getFleetSearchConfig.and.returnValue(
        of({
          columns: {defaults: defaultColumns},
          landing: {enabled: false, browseAllCount: 5},
        }),
      );
    });

    afterEach(() => {
      localStorage.clear();
    });

    it('clears the saved device columns, warns and retries once with the default columns', async () => {
      localStorage.setItem(
        'mh_fe_v6_visible_columns_devices_internal',
        JSON.stringify(staleColumns),
      );
      failSearchesThenSucceed(
        1,
        unknownKeyError('Unknown device key: field::uuid'),
      );

      const store = await createStoreAt({});
      await flushAsync();

      expect(mockSearchService.searchFleet).toHaveBeenCalledTimes(2);
      expect(mockSearchService.searchFleet.calls.argsFor(0)[0]).toEqual(
        jasmine.objectContaining({
          flat: jasmine.objectContaining({
            columns: ['field::uuid', 'dim::pool'],
          }),
        }),
      );
      expect(mockSearchService.searchFleet.calls.argsFor(1)[0]).toEqual(
        jasmine.objectContaining({
          flat: jasmine.objectContaining({
            columns: ['device_field::uuid', 'device_field::status'],
          }),
        }),
      );
      expect(
        localStorage.getItem('mh_fe_v6_visible_columns_devices_internal'),
      ).toBeNull();
      expect(store.visibleColumnDescriptors()).toEqual(defaultColumns);
      expect(store.rows()).toEqual(rows);
      expect(mockSnackBarService.showWarning).toHaveBeenCalledOnceWith(
        'Saved column "field::uuid" is no longer supported. Cleared your saved columns and retrying with the default columns…',
      );
    });

    it('clears the saved host columns on /hosts', async () => {
      localStorage.setItem(
        'mh_fe_v6_visible_columns_hosts_internal',
        JSON.stringify([{key: 'field::host_name', displayName: 'Host Name'}]),
      );
      failSearchesThenSucceed(
        1,
        unknownKeyError('Unknown host key: field::host_name'),
      );

      const store = await createStoreAt({}, 'hosts');
      await flushAsync();

      expect(store.entity()).toBe('hosts');
      expect(mockSearchService.searchFleet).toHaveBeenCalledTimes(2);
      expect(
        localStorage.getItem('mh_fe_v6_visible_columns_hosts_internal'),
      ).toBeNull();
      expect(store.rows()).toEqual(rows);
      expect(mockSnackBarService.showWarning).toHaveBeenCalledOnceWith(
        jasmine.stringContaining('"field::host_name"'),
      );
    });

    it('does not retry when the error does not name an unknown column key', async () => {
      localStorage.setItem(
        'mh_fe_v6_visible_columns_devices_internal',
        JSON.stringify(staleColumns),
      );
      failSearchesThenSucceed(1, {
        status: 500,
        error: {code: 13, message: 'boom'},
      });

      const store = await createStoreAt({});
      await flushAsync();

      expect(mockSearchService.searchFleet).toHaveBeenCalledTimes(1);
      expect(
        localStorage.getItem('mh_fe_v6_visible_columns_devices_internal'),
      ).toBe(JSON.stringify(staleColumns));
      expect(store.rows()).toEqual([]);
      expect(mockSnackBarService.showWarning).not.toHaveBeenCalled();
    });

    it('does not retry when nothing is saved (the default columns were rejected)', async () => {
      failSearchesThenSucceed(
        1,
        unknownKeyError('Unknown device key: device_field::uuid'),
      );

      await createStoreAt({});
      await flushAsync();

      expect(mockSearchService.searchFleet).toHaveBeenCalledTimes(1);
      expect(mockSnackBarService.showWarning).not.toHaveBeenCalled();
    });

    it('retries at most once when the default columns are rejected as well', async () => {
      localStorage.setItem(
        'mh_fe_v6_visible_columns_devices_internal',
        JSON.stringify(staleColumns),
      );
      failSearchesThenSucceed(
        5,
        unknownKeyError('Unknown device key: field::uuid'),
      );

      const store = await createStoreAt({});
      await flushAsync();

      expect(mockSearchService.searchFleet).toHaveBeenCalledTimes(2);
      expect(
        localStorage.getItem('mh_fe_v6_visible_columns_devices_internal'),
      ).toBeNull();
      expect(store.rows()).toEqual([]);
      expect(mockSnackBarService.showWarning).toHaveBeenCalledTimes(1);
    });
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

    // Other suites populate localStorage keys and Karma runs specs in random
    // order, so each test starts from a clean localStorage state.
    beforeEach(() => {
      localStorage.clear();
    });

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
      expect(mockSnackBarService.showWarning).toHaveBeenCalledOnceWith(
        'Unknown filter key: no_such_key; Unknown group-by key: version',
      );
    });

    it('keeps the valid filter chip and shows a warning snackbar when an unmergeable duplicate-key condition is ignored', async () => {
      const duplicateKeyQuery =
        'filter=host_name:match:substring:3a-cm&filter=universe:match:values:google_1p&filter=host_name:not_match:substring:10-19';
      mockSearchService.translateArsenalSearch.and.returnValue(
        of({
          fleet: Fleet.FLEET_SELF,
          filters: [
            {
              key: 'host_field::host_name',
              complex: {containsSubstring: {value: '3a-cm', negated: false}},
            },
            {key: 'host_field::host_name'},
          ],
          filterChips: [
            {
              valid: {
                pillKey: 'Host Name',
                pillCondition: 'contains 3a-cm',
              },
            },
            {
              invalid: {
                reason:
                  'Only one filter per key is supported; ignored Host Name (does not contain 10-19).',
              },
            },
          ],
        }),
      );

      const store = await createStoreAt({'arsenal_query': duplicateKeyQuery});

      expect(store.activeChips().map((c) => c.pillCondition)).toEqual([
        'contains 3a-cm',
      ]);
      expect(store.serializedActiveFilters()).toEqual([
        'host_field::host_name~contains~3a-cm',
      ]);
      expect(mockSnackBarService.showWarning).toHaveBeenCalledOnceWith(
        'Only one filter per key is supported; ignored Host Name (does not contain 10-19).',
      );
      expect(mockSearchService.searchFleet).toHaveBeenCalledWith(
        jasmine.objectContaining({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          fleet: Fleet.FLEET_SELF,
          filters: [
            {
              key: 'host_field::host_name',
              complex: {containsSubstring: {value: '3a-cm', negated: false}},
            },
          ],
        }),
      );
    });

    it('coalesces multiple ignored duplicate-key conditions into a single sentence without stacking lines', async () => {
      const multiDuplicateQuery =
        'filter=host_name:not_match:substring:10-19&filter=host_name:match:substring:3a-cm&filter=owner:any_match:values:chengwilliam&filter=owner:none_match:values:pte-bluetooth-automation';
      mockSearchService.translateArsenalSearch.and.returnValue(
        of({
          fleet: Fleet.FLEET_SELF,
          filters: [
            {
              key: 'host_field::host_name',
              complex: {containsSubstring: {value: '3a-cm', negated: false}},
            },
            {
              key: 'device_field::owner',
              simple: {values: [{value: 'chengwilliam'}], negated: false},
            },
            {key: 'host_field::host_name'},
            {key: 'device_field::owner'},
          ],
          filterChips: [
            {
              valid: {
                pillKey: 'Host Name',
                pillCondition: 'contains 3a-cm',
              },
            },
            {
              valid: {
                pillKey: 'Owners',
                pillCondition: 'chengwilliam',
              },
            },
            {
              invalid: {
                reason:
                  'Only one filter per key is supported; ignored Host Name (does not contain 10-19).',
              },
            },
            {
              invalid: {
                reason:
                  'Only one filter per key is supported; ignored Owners (\u2260 pte-bluetooth-automation).',
              },
            },
          ],
        }),
      );

      await createStoreAt({'arsenal_query': multiDuplicateQuery});

      expect(mockSnackBarService.showWarning).toHaveBeenCalledOnceWith(
        'Only one filter per key is supported; ignored Host Name (does not contain 10-19), Owners (\u2260 pte-bluetooth-automation).',
      );
    });

    it('drops invalid URL filter and group-by chips and shows a coalesced warning snackbar', async () => {
      mockSearchService.resolveFleetChips.and.returnValue(
        of({
          filterChips: [
            {valid: {pillKey: 'Status', pillCondition: 'IDLE'}},
            {
              invalid: {
                reason:
                  'Only one filter per key is supported; ignored Host Name (does not contain 10-19).',
              },
            },
            {
              invalid: {
                reason:
                  'Only one filter per key is supported; ignored Host Name (does not contain 10-19).',
              },
            },
          ],
          groupByChips: [
            {invalid: {reason: 'Unknown group-by key: bad_gb'}},
            {invalid: {reason: 'Unknown group-by key: bad_gb'}},
          ],
        }),
      );

      const store = await createStoreAt({
        'f': [
          'device_field::status~IDLE',
          'host_field::host_name~not_contains~10-19',
          'host_field::host_name~not_contains~10-19',
        ],
        'gb': 'bad_gb,bad_gb2',
      });

      expect(store.activeChips().map((c) => c.key)).toEqual([
        'device_field::status',
      ]);
      expect(mockSnackBarService.showWarning).toHaveBeenCalledOnceWith(
        'Only one filter per key is supported; ignored Host Name (does not contain 10-19); Unknown group-by key: bad_gb',
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

    it('resets pagination, selection, and expanded group states when setPageSize is called', async () => {
      const store = await createStoreAt({});
      store.toggleSelectRows([{id: 'dev-1'}]);
      store.openGroupIds.set(new Set(['group-1']));
      store.expandedGroupPages.set(new Map([['group-1', {loading: false}]]));

      store.setPageSize(50);

      expect(store.pageSize()).toBe(50);
      expect(store.pageIndex()).toBe(0);
      expect(store.pageToken()).toBe('');
      expect(store.selectedItems().size).toBe(0);
      expect(store.openGroupIds().size).toBe(0);
      expect(store.expandedGroupPages().size).toBe(0);
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

  it('propagates canCompareInt to pickerConfig, applies compare_int filter, and restores intOp on reopen', async () => {
    mockSearchService.resolveFleetChips.and.callFake(
      (req: FleetChipResolverRequest) =>
        of({
          filterChips: (req.filters || []).map((f) => ({
            valid: {
              pillKey: 'Battery Level',
              pillCondition: `\u2264 ${f.complex?.intComparison?.value}`,
              metadata: {
                keyDisplayName: 'Battery Level',
                canUseAdvanced: true,
                canCompareInt: true,
              },
            },
          })),
        }),
    );

    const store = await createStoreAt({});
    store.openValuePicker('dimension::battery_level', null, 'Battery Level', {
      keyDisplayName: 'Battery Level',
      canUseAdvanced: true,
      canCompareInt: true,
    });
    expect(store.pickerConfig()?.canCompareInt).toBeTrue();

    store.applyValuePicker({
      selected: [],
      negate: false,
      isAdvanced: true,
      advMode: 'compare_int',
      advText: '20',
      intOp: 'lte',
    });

    expect(store.activeChips().length).toBe(1);
    expect(store.activeChips()[0].pillCondition).toBe('\u2264 20');
    expect(store.activeChips()[0].complex).toEqual({
      intComparison: {
        operator:
          IntComparisonOperator.INT_COMPARISON_OPERATOR_LESS_THAN_OR_EQUAL,
        value: 20,
      },
    });
    expect(store.serializedActiveFilters()).toEqual([
      'dimension::battery_level~lte~20',
    ]);

    store.openValuePicker('dimension::battery_level');
    expect(store.pickerConfig()?.canCompareInt).toBeTrue();
    expect(store.effectivePickerState().advanced).toEqual({
      active: true,
      mode: 'compare_int',
      text: '20',
      values: [],
      intOp: 'lte',
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

  describe('duplicate SearchFleet prevention', () => {
    afterEach(() => {
      localStorage.clear();
    });

    it('waits for searchConfig locked sort key on flat search even when localStorage has saved columns', async () => {
      const savedColumns = [
        {key: 'device_field::uuid', displayName: 'UUID', locked: true},
        {key: 'dimension::model', displayName: 'Model'},
      ];
      localStorage.setItem(
        'mh_fe_v6_visible_columns_devices_internal',
        JSON.stringify(savedColumns),
      );
      const configSubject = new Subject<FleetSearchConfig>();
      mockSearchService.getFleetSearchConfig.and.returnValue(configSubject);
      mockSearchService.resolveFleetChips.and.returnValue(
        of({
          filterChips: [{valid: {pillKey: 'Status', pillCondition: 'IDLE'}}],
          groupByChips: [],
        }),
      );

      const store = await createStoreAt({'f': 'device_field::status~IDLE'});

      expect(store.isConfigLoading()).toBeTrue();
      expect(mockSearchService.searchFleet).not.toHaveBeenCalled();

      configSubject.next({
        columns: {
          defaults: [
            {key: 'device_field::uuid', displayName: 'UUID', locked: true},
            {key: 'device_field::status', displayName: 'Status'},
          ],
        },
      });
      configSubject.complete();
      await flushAsync();

      expect(mockSearchService.searchFleet).toHaveBeenCalledTimes(1);
      expect(mockSearchService.searchFleet).toHaveBeenCalledWith(
        jasmine.objectContaining({
          flat: jasmine.objectContaining({
            columns: ['device_field::uuid', 'dimension::model'],
            sort: {key: 'device_field::uuid', ascending: true},
          }),
        }),
      );
    });

    it('re-fetches flat results once when visible columns change and skips RPC when unchanged', async () => {
      mockSearchService.getFleetSearchConfig.and.returnValue(
        of({
          columns: {
            defaults: [
              {key: 'device_field::uuid', displayName: 'UUID', locked: true},
              {key: 'device_field::status', displayName: 'Status'},
            ],
          },
        }),
      );
      mockSearchService.resolveFleetChips.and.returnValue(
        of({
          filterChips: [{valid: {pillKey: 'Status', pillCondition: 'IDLE'}}],
          groupByChips: [],
        }),
      );
      mockSearchService.searchFleet.and.returnValue(
        of({flat: {rows: [{id: 'dev-1', cells: []}], nextPageToken: 'p2'}}),
      );

      const store = await createStoreAt({'f': 'device_field::status~IDLE'});
      expect(mockSearchService.searchFleet).toHaveBeenCalledTimes(1);

      store.nextPage();
      await flushAsync();
      store.toggleSelectRows([{id: 'dev-1', cells: []}]);
      expect(store.pageIndex()).toBe(1);
      expect(store.selectedItems().size).toBe(1);
      mockSearchService.searchFleet.calls.reset();

      // Applying the exact same columns does not fire SearchFleet or drop pagination/selection.
      store.setVisibleColumns([
        {key: 'device_field::uuid', displayName: 'UUID', locked: true},
        {key: 'device_field::status', displayName: 'Status'},
      ]);
      await flushAsync();
      expect(mockSearchService.searchFleet).not.toHaveBeenCalled();
      expect(store.pageIndex()).toBe(1);
      expect(store.selectedItems().size).toBe(1);

      // Changing columns resets pagination/selection and triggers exactly one flat SearchFleet.
      store.setVisibleColumns([
        {key: 'device_field::uuid', displayName: 'UUID', locked: true},
        {key: 'dimension::model', displayName: 'Model'},
      ]);
      await flushAsync();
      expect(mockSearchService.searchFleet).toHaveBeenCalledTimes(1);
      expect(
        mockSearchService.searchFleet.calls.mostRecent().args[0].flat?.page
          ?.pageToken,
      ).toBeUndefined();
      expect(store.pageIndex()).toBe(0);
      expect(store.selectedItems().size).toBe(0);

      // Re-applying the same custom columns as a new array does not fire SearchFleet.
      mockSearchService.searchFleet.calls.reset();
      store.setVisibleColumns([
        {key: 'device_field::uuid', displayName: 'UUID', locked: true},
        {key: 'dimension::model', displayName: 'Model'},
      ]);
      await flushAsync();
      expect(mockSearchService.searchFleet).not.toHaveBeenCalled();

      // Resetting back to defaults triggers exactly one flat SearchFleet, and a second reset is a no-op.
      mockSearchService.searchFleet.calls.reset();
      store.resetVisibleColumns();
      await flushAsync();
      expect(mockSearchService.searchFleet).toHaveBeenCalledTimes(1);

      mockSearchService.searchFleet.calls.reset();
      store.resetVisibleColumns();
      await flushAsync();
      expect(mockSearchService.searchFleet).not.toHaveBeenCalled();
    });

    it('reloads only open group rows without re-fetching group headers when visible columns change in grouped mode', async () => {
      mockSearchService.getFleetSearchConfig.and.returnValue(
        of({
          columns: {
            defaults: [
              {key: 'device_field::uuid', displayName: 'UUID', locked: true},
            ],
          },
        }),
      );
      mockSearchService.searchFleet.and.returnValue(
        of({
          grouped: {
            groups: [
              {groupId: 'dimension::model=pixel 8', values: ['Pixel 8']},
            ],
            totalGroups: 1,
            totalItems: 5,
            nextPageToken: 'g2',
          },
          flat: {rows: [{id: 'dev-1', cells: []}]},
        }),
      );

      const store = await createStoreAt({'gb': 'dimension::model'});
      expect(mockSearchService.searchFleet).toHaveBeenCalledTimes(1);

      store.toggleGroup('dimension::model=pixel 8');
      await flushAsync();
      expect(store.openGroupIds().has('dimension::model=pixel 8')).toBeTrue();
      store.nextPage();
      await flushAsync();
      expect(store.pageIndex()).toBe(1);
      mockSearchService.searchFleet.calls.reset();

      store.setVisibleColumns([
        {key: 'device_field::uuid', displayName: 'UUID', locked: true},
        {key: 'dimension::os', displayName: 'OS'},
      ]);
      await flushAsync();

      // The group header page stays on page 2 and only the open group reloads its rows.
      expect(store.pageIndex()).toBe(1);
      expect(store.openGroupIds().has('dimension::model=pixel 8')).toBeTrue();
      expect(mockSearchService.searchFleet).toHaveBeenCalledTimes(1);
      expect(mockSearchService.searchFleet).toHaveBeenCalledWith(
        jasmine.objectContaining({
          groupExpand: jasmine.objectContaining({
            groupId: 'dimension::model=pixel 8',
            columns: ['device_field::uuid', 'dimension::os'],
          }),
        }),
      );

      // A failed reload drops the rows fetched with the previous columns instead of showing them
      // under the new column headers.
      mockSearchService.searchFleet.and.returnValue(
        throwError(() => new Error('reload failed')),
      );
      store.setVisibleColumns([
        {key: 'device_field::uuid', displayName: 'UUID', locked: true},
        {key: 'dimension::sdk', displayName: 'SDK'},
      ]);
      await flushAsync();

      const groupState = store
        .expandedGroupPages()
        .get('dimension::model=pixel 8');
      expect(groupState?.data).toBeUndefined();
      expect(groupState?.error).toBe('reload failed');
    });

    it('drops group row responses from older requests that resolve after a newer reload', async () => {
      mockSearchService.getFleetSearchConfig.and.returnValue(
        of({
          columns: {
            defaults: [
              {key: 'device_field::uuid', displayName: 'UUID', locked: true},
            ],
          },
        }),
      );
      mockSearchService.searchFleet.and.returnValue(
        of({
          grouped: {
            groups: [
              {groupId: 'dimension::model=pixel 8', values: ['Pixel 8']},
            ],
            totalGroups: 1,
            totalItems: 5,
          },
        }),
      );
      const store = await createStoreAt({'gb': 'dimension::model'});

      const toggleLoad = new Subject<FleetSearchResults>();
      const firstColumnReload = new Subject<FleetSearchResults>();
      mockSearchService.searchFleet.and.returnValues(
        toggleLoad,
        firstColumnReload,
        of({flat: {rows: [{id: 'dev-newest', cells: []}]}}),
      );
      store.toggleGroup('dimension::model=pixel 8');
      store.setVisibleColumns([
        {key: 'device_field::uuid', displayName: 'UUID', locked: true},
        {key: 'dimension::os', displayName: 'OS'},
      ]);
      store.setVisibleColumns([
        {key: 'device_field::uuid', displayName: 'UUID', locked: true},
        {key: 'dimension::sdk', displayName: 'SDK'},
      ]);
      await flushAsync();

      // The two older requests resolve last, one with rows and one with an error; both are dropped.
      toggleLoad.next({flat: {rows: [{id: 'dev-stale', cells: []}]}});
      firstColumnReload.error(new Error('stale failure'));
      await flushAsync();

      const groupState = store
        .expandedGroupPages()
        .get('dimension::model=pixel 8');
      expect(groupState?.data?.rows?.map((r) => r.id)).toEqual(['dev-newest']);
      expect(groupState?.error).toBeUndefined();
    });

    it('exposes searchError when searchFleet fails and recovers on executeSearch()', async () => {
      mockSearchService.resolveFleetChips.and.returnValue(
        of({
          filterChips: [{valid: {pillKey: 'Status', pillCondition: 'IDLE'}}],
          groupByChips: [],
        }),
      );
      mockSearchService.searchFleet.and.returnValue(
        throwError(() => ({
          error: {error: {message: 'UNAVAILABLE: Search backend down'}},
        })),
      );
      const store = await createStoreAt({'f': 'device_field::status~IDLE'});

      expect(store.searchError()).toBe('UNAVAILABLE: Search backend down');
      expect(store.rows()).toEqual([]);
      expect(store.groups()).toEqual([]);

      mockSearchService.searchFleet.and.returnValue(
        of({flat: {rows: [{id: 'dev-recovered', cells: []}], total: 1}}),
      );
      store.executeSearch();
      await flushAsync();

      expect(store.searchError()).toBeNull();
      expect(store.rows().map((r) => r.id)).toEqual(['dev-recovered']);
    });

    it('selects and deselects all rows via toggleSelectRows even when a row has an empty id', async () => {
      const emptyUuidRow = {
        id: '',
        cells: [
          {text: {value: '—'}},
          {
            link: {
              text: 'host-empty-uuid.example.com',
              target: {
                host: {
                  hostName: 'host-empty-uuid.example.com',
                },
              },
            },
          },
        ],
      };
      const normalRow = {
        id: 'dev-1',
        cells: [
          {
            link: {
              text: 'dev-1',
              target: {device: {id: 'dev-1', hostName: 'host-1.corp'}},
            },
          },
        ],
      };
      mockSearchService.resolveFleetChips.and.returnValue(
        of({
          filterChips: [{valid: {pillKey: 'Status', pillCondition: 'IDLE'}}],
          groupByChips: [],
        }),
      );
      mockSearchService.searchFleet.and.returnValue(
        of({flat: {rows: [emptyUuidRow, normalRow], total: 2}}),
      );

      const store = await createStoreAt({'f': 'device_field::status~IDLE'});
      expect(store.selectionState()).toEqual({all: false, some: false});

      // Selecting all rows includes the empty-UUID row and reaches `all: true`.
      store.toggleSelectRows(store.rows());
      expect(store.selectedItems().size).toBe(2);
      expect(store.selectionState()).toEqual({all: true, some: false});

      // Toggling select-all again cleanly deselects all rows.
      store.toggleSelectRows(store.rows());
      expect(store.selectedItems().size).toBe(0);
      expect(store.selectionState()).toEqual({all: false, some: false});
    });
  });

  describe('initial load and URL refresh loading states', () => {
    beforeEach(() => {
      localStorage.clear();
    });

    afterEach(() => {
      localStorage.clear();
    });

    it('fires grouped search in parallel with getFleetSearchConfig without flashing empty state', async () => {
      const configSubject = new Subject<FleetSearchConfig>();
      const searchSubject = new Subject<FleetSearchResults>();
      const resolveSubject = new Subject<FleetChipResolverResponse>();
      mockSearchService.getFleetSearchConfig.and.returnValue(configSubject);
      mockSearchService.searchFleet.and.returnValue(searchSubject);
      mockSearchService.resolveFleetChips.and.returnValue(resolveSubject);

      const store = await createStoreAt({
        'f': 'device_field::status~IDLE',
        'gb': 'dimension::model',
      });

      // Even before getFleetSearchConfig resolves, grouped search is already in flight and isLoading is true.
      expect(store.isConfigLoading()).toBeTrue();
      expect(mockSearchService.searchFleet).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({
          groupHeader: jasmine.objectContaining({
            groupBy: ['dimension::model'],
          }),
        }),
      );
      expect(store.isLoading()).toBeTrue();
      expect(store.groups()).toEqual([]);

      searchSubject.next({
        grouped: {
          groups: [
            {
              groupId: 'Pixel 9',
              values: ['Pixel 9'],
              itemCount: 3,
            },
          ],
          totalGroups: 1,
        },
      });
      await flushAsync();

      expect(store.groups().length).toBe(1);
      expect(store.isLoading()).toBeFalse();

      resolveSubject.next({
        filterChips: [{valid: {pillKey: 'Status', pillCondition: 'IDLE'}}],
        groupByChips: [{valid: {pillKey: 'Model', displayName: 'Model'}}],
      });
      configSubject.next({
        columns: {
          defaults: [
            {key: 'device_field::uuid', displayName: 'UUID', locked: true},
          ],
        },
      });
      await flushAsync();

      expect(store.isLoading()).toBeFalse();
      expect(mockSearchService.searchFleet).toHaveBeenCalledTimes(1);
    });

    it('keeps isLoading true while flat search waits for initial default columns from searchConfig', async () => {
      const configSubject = new Subject<FleetSearchConfig>();
      mockSearchService.getFleetSearchConfig.and.returnValue(configSubject);
      mockSearchService.resolveFleetChips.and.returnValue(
        of({
          filterChips: [{valid: {pillKey: 'Status', pillCondition: 'IDLE'}}],
        }),
      );
      mockSearchService.searchFleet.and.returnValue(
        of({
          flat: {
            rows: [{id: 'dev-1'}],
            rangeStart: 1,
            rangeEnd: 1,
            totalItems: 1,
          },
        }),
      );

      const store = await createStoreAt({
        'f': 'device_field::status~IDLE',
      });

      // Flat search has not fired yet because default columns have not loaded,
      // but store.isLoading() must be true so the UI does not flash "No devices match your filters".
      expect(store.isConfigLoading()).toBeTrue();
      expect(store.visibleColumns().size).toBe(0);
      expect(mockSearchService.searchFleet).not.toHaveBeenCalled();
      expect(store.isLoading()).toBeTrue();

      configSubject.next({
        columns: {
          defaults: [
            {key: 'device_field::uuid', displayName: 'UUID', locked: true},
          ],
        },
      });
      await flushAsync();

      expect(mockSearchService.searchFleet).toHaveBeenCalledTimes(1);
      expect(store.rows().length).toBe(1);
      expect(store.isLoading()).toBeFalse();
    });

    it('keeps isLoading true when initial search with an unvalidated URL chip returns empty while resolveFleetChips is still pending', async () => {
      const resolveSubject = new Subject<FleetChipResolverResponse>();
      mockSearchService.resolveFleetChips.and.returnValue(resolveSubject);

      let searchCallCount = 0;
      mockSearchService.searchFleet.and.callFake(() => {
        searchCallCount++;
        if (searchCallCount === 1) {
          // Search #1 with raw invalid chip returns 0 groups
          return of({grouped: {groups: [], totalGroups: 0}});
        }
        // Search #2 after invalid filter chip is dropped returns matching groups
        return of({
          grouped: {
            groups: [
              {
                groupId: 'IDLE',
                values: ['IDLE'],
                itemCount: 5,
              },
            ],
            totalGroups: 1,
          },
        });
      });

      const store = await createStoreAt({
        'f': 'device_field::invalid_key~foo',
        'gb': 'device_field::status',
      });

      // Search #1 completed with 0 groups, but resolveFleetChips is still in flight,
      // so store.isLoading() remains true instead of flashing empty state.
      expect(mockSearchService.searchFleet).toHaveBeenCalledTimes(1);
      expect(store.groups()).toEqual([]);
      expect(store.isResolvingChips()).toBeTrue();
      expect(store.isLoading()).toBeTrue();

      // resolveFleetChips drops the invalid filter chip and keeps the valid group-by chip
      resolveSubject.next({
        filterChips: [{invalid: {reason: 'Unknown filter key'}}],
        groupByChips: [{valid: {pillKey: 'Status', displayName: 'Status'}}],
      });
      await flushAsync();

      expect(store.isResolvingChips()).toBeFalse();
      expect(mockSearchService.searchFleet).toHaveBeenCalledTimes(2);
      expect(store.groups().length).toBe(1);
      expect(store.isLoading()).toBeFalse();
    });

    it('initializes isResolvingChips as false and resets it to false when pending chip resolution is cancelled', async () => {
      const resolveSubject = new Subject<FleetChipResolverResponse>();
      mockSearchService.resolveFleetChips.and.returnValue(resolveSubject);
      mockSearchService.searchFleet.and.returnValue(
        of({grouped: {groups: [], totalGroups: 0}}),
      );

      activatedRouteStub.snapshot.queryParams = {};
      await router.navigateByUrl('/devices');
      const store = TestBed.inject(FleetSearchStore);
      // Before queryParams$ emits, isResolvingChips is initialized to false.
      expect(store.isResolvingChips()).toBeFalse();

      queryParams$.next({
        'f': 'device_field::invalid_key~foo',
        'gb': 'device_field::status',
      });
      await flushAsync();

      expect(store.isResolvingChips()).toBeTrue();
      expect(store.isLoading()).toBeTrue();

      // Cancelling pending chip resolution (e.g. via restoreDefaultState(false)) immediately clears isResolvingChips.
      store.restoreDefaultState(false);
      await flushAsync();

      expect(store.isResolvingChips()).toBeFalse();
      expect(store.isLoading()).toBeFalse();
    });

    it('forwards src query parameter as source in resolveFleetChips and strips src from the URL', async () => {
      mockSearchService.resolveFleetChips.and.returnValue(
        of({
          filterChips: [
            {valid: {pillKey: 'Type', pillCondition: 'AndroidRealDevice'}},
          ],
          groupByChips: [{valid: {pillKey: 'Status', displayName: 'Status'}}],
        }),
      );
      const navigateSpy = spyOn(router, 'navigate').and.callThrough();

      await createStoreAt({
        'f': 'device_field::type~AndroidRealDevice',
        'gb': 'device_field::status',
        'src': 'alloc_diag',
      });
      await flushAsync();

      expect(mockSearchService.resolveFleetChips).toHaveBeenCalledOnceWith({
        filters: [
          {
            key: 'device_field::type',
            simple: {
              values: [{value: 'AndroidRealDevice'}],
              negated: false,
            },
          },
        ],
        groupByKeys: ['device_field::status'],
        entity: SearchEntity.SEARCH_ENTITY_DEVICE,
        fleet: Fleet.FLEET_SELF,
        source: 'alloc_diag',
      });
      expect(navigateSpy).toHaveBeenCalledWith(
        [],
        jasmine.objectContaining({
          queryParams: {'src': null},
          replaceUrl: true,
        }),
      );
      expect(router.url).not.toContain('src=');
    });

    it('keeps src in the URL when resolveFleetChips fails with an RPC error', async () => {
      mockSearchService.resolveFleetChips.and.returnValue(
        throwError(() => new Error('RPC failed')),
      );
      const navigateSpy = spyOn(router, 'navigate').and.callThrough();

      await createStoreAt({
        'f': 'device_field::type~AndroidRealDevice',
        'gb': 'device_field::status',
        'src': 'alloc_diag',
      });
      await flushAsync();

      expect(navigateSpy).not.toHaveBeenCalled();
      expect(router.url).toContain('src=alloc_diag');
    });

    it('forwards src query parameter and strips src even when f and gb are absent', async () => {
      mockSearchService.resolveFleetChips.and.returnValue(of({}));
      const navigateSpy = spyOn(router, 'navigate').and.callThrough();

      await createStoreAt({'src': 'cli'});
      await flushAsync();

      expect(mockSearchService.resolveFleetChips).toHaveBeenCalledOnceWith({
        filters: undefined,
        groupByKeys: undefined,
        entity: SearchEntity.SEARCH_ENTITY_DEVICE,
        fleet: Fleet.FLEET_SELF,
        source: 'cli',
      });
      expect(navigateSpy).toHaveBeenCalledWith(
        [],
        jasmine.objectContaining({
          queryParams: {'src': null},
          replaceUrl: true,
        }),
      );
      expect(router.url).not.toContain('src=');
    });
  });
});
