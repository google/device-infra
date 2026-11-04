import {Component} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {
  ActivatedRoute,
  NavigationEnd,
  provideRouter,
  Router,
} from '@angular/router';
import {of, Subject} from 'rxjs';

import {TjsSearchConfig, TjsSearchResponse} from '../../../core/models/search';
import {
  SEARCH_SERVICE,
  SearchService,
} from '../../../core/services/search/search_service';
import {SnackBarService} from '../../../shared/services/snackbar_service';
import {SearchBoxSuggestion} from '../models';
import {TjsSearchStore} from './tjs_search_store';

@Component({standalone: true, template: ''})
class DummyComponent {}

describe('TjsSearchStore', () => {
  let store: TjsSearchStore;
  let router: Router;
  let mockSearchService: jasmine.SpyObj<SearchService>;
  let mockSnackBarService: jasmine.SpyObj<SnackBarService>;
  let queryParams$: Subject<Record<string, unknown>>;
  let activatedRouteStub: {
    queryParams: typeof queryParams$;
    snapshot: {queryParams: Record<string, unknown>};
    routeConfig: {path: string};
  };

  const mockConfig: TjsSearchConfig = {
    entityLabel: 'Tests',
    defaultChips: [
      {
        keyDisplayName: 'User',
        pillKey: 'User',
        pillCondition: '(qiupingf)',
        filter: {
          key: 'user',
          stringValue: {value: 'qiupingf'},
        },
      },
    ],
    promotedKeys: [
      {key: 'user', displayName: 'User'},
      {key: 'name', displayName: 'Name'},
      {
        key: 'create_time',
        displayName: 'Create Time',
        timeRange: {},
      },
    ],
  };

  const emptyResponse: TjsSearchResponse = {
    columns: [],
    rows: [],
  };

  beforeEach(async () => {
    queryParams$ = new Subject();
    activatedRouteStub = {
      queryParams: queryParams$,
      snapshot: {queryParams: {}},
      routeConfig: {path: 'tests'},
    };

    mockSearchService = jasmine.createSpyObj<SearchService>('SearchService', [
      'getTjsSearchConfig',
      'searchTjs',
      'getTjsSuggestions',
      'resolveTjsChips',
    ]);
    mockSearchService.getTjsSearchConfig.and.returnValue(of(mockConfig));
    mockSearchService.searchTjs.and.returnValue(of(emptyResponse));
    mockSearchService.getTjsSuggestions.and.returnValue(of({items: []}));
    mockSearchService.resolveTjsChips.and.callFake((req) =>
      of({
        chips: (req.filters || []).map((f) => ({
          keyDisplayName: f.key,
          pillKey: f.key,
          pillCondition: f.stringValue?.value || '',
        })),
      }),
    );

    mockSnackBarService = jasmine.createSpyObj<SnackBarService>(
      'SnackBarService',
      ['showError', 'showInfo'],
    );

    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {path: 'tests', component: DummyComponent},
          {path: 'jobs', component: DummyComponent},
        ]),
        {provide: SEARCH_SERVICE, useValue: mockSearchService},
        {provide: SnackBarService, useValue: mockSnackBarService},
        {provide: ActivatedRoute, useValue: activatedRouteStub},
        TjsSearchStore,
      ],
    });

    router = TestBed.inject(Router);
    await router.navigateByUrl('/tests');
    store = TestBed.inject(TjsSearchStore);
    await flushAsync();
  });

  async function flushAsync(): Promise<void> {
    TestBed.tick();
    await new Promise((resolve) => {
      setTimeout(resolve, 10);
    });
    TestBed.tick();
  }

  it('should be created and resolve default entity', () => {
    expect(store).toBeTruthy();
    expect(store.entity()).toBe('tests');
  });

  it('should initialize activeChips with defaultChips on clean route load', () => {
    const chips = store.activeChips();
    expect(chips.length).toBe(1);
    expect(chips[0].key).toBe('user');
    expect(chips[0].pillCondition).toBe('(qiupingf)');
  });

  it('should clear all chips to [] when resetSearchState is called and not reset back during tab usage', () => {
    expect(store.activeChips().length).toBe(1);

    // User clicks "Clear all"
    store.resetSearchState(false);
    TestBed.tick();

    expect(store.activeChips()).toEqual([]);
    expect(store.searchQuery()).toBe('');
  });

  it('should restore default chips when restoreDefaultState is called', () => {
    // User clears chips
    store.resetSearchState(false);
    TestBed.tick();
    expect(store.activeChips()).toEqual([]);

    // Restore defaults (e.g. user re-enters tab from clean route navigation)
    store.restoreDefaultState(false);
    TestBed.tick();

    const chips = store.activeChips();
    expect(chips.length).toBe(1);
    expect(chips[0].key).toBe('user');
    expect(chips[0].pillCondition).toBe('(qiupingf)');
  });

  it('should restore default chips on NavigationEnd event to clean route when previously cleared', async () => {
    // User clears chips
    store.resetSearchState(false);
    TestBed.tick();
    expect(store.activeChips()).toEqual([]);

    // Simulate navigation event back to /tests
    activatedRouteStub.snapshot.queryParams = {};
    const router = TestBed.inject(Router);
    spyOnProperty(router, 'url', 'get').and.returnValue('/tests');

    // Dispatch NavigationEnd
    (router.events as Subject<unknown>).next(
      new NavigationEnd(1, '/tests', '/tests'),
    );

    await flushAsync();
    const chips = store.activeChips();
    expect(chips.length).toBe(1);
    expect(chips[0].key).toBe('user');
    expect(chips[0].pillCondition).toBe('(qiupingf)');
  });

  it('should reflect effectiveFilters matching activeChips and default chips', () => {
    expect(store.effectiveFilters().length).toBe(1);
    expect(store.effectiveFilters()[0].key).toBe('user');

    store.resetSearchState(false);
    TestBed.tick();
    expect(store.effectiveFilters().length).toBe(0);

    store.restoreDefaultState(false);
    TestBed.tick();
    expect(store.effectiveFilters().length).toBe(1);
    expect(store.effectiveFilters()[0].key).toBe('user');
  });

  it('should restore default chips and pass them to effectiveFilters after deleting chips, navigating to another tab, and returning to the tab', async () => {
    expect(store.activeChips().length).toBe(1);
    expect(store.effectiveFilters()[0].key).toBe('user');

    // 1. User deletes default chips
    store.resetSearchState(false);
    TestBed.tick();
    expect(store.activeChips()).toEqual([]);
    expect(store.effectiveFilters()).toEqual([]);

    // 2. User switches nav menu to /jobs
    await router.navigateByUrl('/jobs');
    await flushAsync();
    expect(store.isCurrentRouteActive()).toBeFalse();

    // 3. User switches back to /tests
    await router.navigateByUrl('/tests');
    await flushAsync();
    expect(store.isCurrentRouteActive()).toBeTrue();

    // 4. Default chips must be restored and effectiveFilters populated
    const chips = store.activeChips();
    expect(chips.length).toBe(1);
    expect(chips[0].key).toBe('user');
    expect(chips[0].pillCondition).toBe('(qiupingf)');

    const filters = store.effectiveFilters();
    expect(filters.length).toBe(1);
    expect(filters[0].key).toBe('user');
  });

  it('should preserve URL query params filters and not overwrite with default chips when navigating with filters', async () => {
    activatedRouteStub.snapshot.queryParams = {'f': 'user~alice'};
    TestBed.tick();

    // When navigating with explicit URL filters, activeChips should reflect URL filters
    queryParams$.next({'f': 'user~alice'});
    await flushAsync();

    expect(store.activeChips().length).toBe(1);
    expect(store.activeChips()[0].key).toBe('user');
  });

  it('should clear all chips and keep them cleared when resetSearchState is called after adding filters', () => {
    expect(store.activeChips().length).toBe(1);

    // User adds custom chip
    store.addFilterChip({
      key: 'name',
      pillKey: 'Name',
      pillCondition: 'test_foo',
      rawValues: ['test_foo'],
    });
    TestBed.tick();
    expect(store.activeChips().length).toBe(2);

    // User clicks "Clear all"
    store.resetSearchState(true);
    TestBed.tick();

    expect(store.activeChips()).toEqual([]);
    expect(store.searchQuery()).toBe('');
    expect(store.effectiveFilters()).toEqual([]);
  });

  it('should serialize create_time with cleared to as <val>,<empty>', () => {
    store.openValuePicker('create_time', null, 'Create Time', {
      key: 'create_time',
      keyDisplayName: 'Create Time',
      timeRange: {},
    });
    TestBed.tick();

    store.applyValuePicker({
      selected: ['From: 2026-10-06T23:35 To: '],
      negate: false,
      isAdvanced: false,
      rangeFrom: '2026-10-06T23:35',
      rangeTo: '',
    });
    TestBed.tick();

    const timeChip = store.activeChips().find((c) => c.key === 'create_time');
    expect(timeChip).toBeDefined();
    expect(timeChip?.rawValues).toEqual(['2026-10-06T23:35', '<empty>']);

    const serialized = store.serializedActiveFilters();
    expect(serialized).toContain('create_time~2026-10-06T23%3A35,<empty>');
    expect(serialized).not.toContain('create_time~<empty>,2026-10-06T23%3A35');
  });

  it('should serialize create_time with cleared from as <empty>,<val>', () => {
    store.openValuePicker('create_time', null, 'Create Time', {
      key: 'create_time',
      keyDisplayName: 'Create Time',
      timeRange: {},
    });
    TestBed.tick();

    store.applyValuePicker({
      selected: ['From:  To: 2026-10-06T23:35'],
      negate: false,
      isAdvanced: false,
      rangeFrom: '',
      rangeTo: '2026-10-06T23:35',
    });
    TestBed.tick();

    const timeChip = store.activeChips().find((c) => c.key === 'create_time');
    expect(timeChip).toBeDefined();
    expect(timeChip?.rawValues).toEqual(['<empty>', '2026-10-06T23:35']);

    const serialized = store.serializedActiveFilters();
    expect(serialized).toContain('create_time~<empty>,2026-10-06T23%3A35');
  });

  it('should keep default User chip, populate searchQuery, and open suggestions when arriving with ?q=<id>', async () => {
    activatedRouteStub.snapshot.queryParams = {
      'q': 'tjs-id-for-ambiguous-1',
    };
    queryParams$.next({'q': 'tjs-id-for-ambiguous-1'});
    await flushAsync();

    expect(store.activeChips().length).toBe(1);
    expect(store.activeChips()[0].key).toBe('user');
    expect(store.searchQuery()).toBe('tjs-id-for-ambiguous-1');
    expect(store.showSuggestions()).toBeTrue();
    expect(store.focusInputTrigger()).toBeTrue();
  });

  it('should keep default User chip, set searchQuery, and trigger focus on repeat visit via NavigationEnd when queryParams did not change', async () => {
    expect(store.activeChips().length).toBe(1);
    expect(store.activeChips()[0].key).toBe('user');

    // 1. User leaves /tests while root queryParams becomes {'q': 'non-existent-id-404'} on /search
    await router.navigateByUrl('/jobs');
    activatedRouteStub.snapshot.queryParams = {'q': 'non-existent-id-404'};
    queryParams$.next({'q': 'non-existent-id-404'});
    await flushAsync();
    expect(store.isCurrentRouteActive()).toBeFalse();

    // 2. User clicks fallback chip to /tests?q=non-existent-id-404 (queryParams does NOT re-emit)
    await router.navigateByUrl('/tests?q=non-existent-id-404');
    await flushAsync();

    expect(store.isCurrentRouteActive()).toBeTrue();
    expect(store.activeChips().length).toBe(1);
    expect(store.activeChips()[0].key).toBe('user');
    expect(store.searchQuery()).toBe('non-existent-id-404');
    expect(store.showSuggestions()).toBeTrue();
    expect(store.focusInputTrigger()).toBeTrue();
  });

  it('should ignore ?arsenal_query because TJS pages have no legacy query translation', async () => {
    activatedRouteStub.snapshot.queryParams = {'arsenal_query': 'filter=x'};
    queryParams$.next({'arsenal_query': 'filter=x'});
    await flushAsync();

    expect(store.entity()).toBe('tests');
    expect(store.activeChips().map((c) => c.key)).toEqual(['user']);
    expect(mockSnackBarService.showError).not.toHaveBeenCalled();
  });

  it('populates suggestions directly from backend and tracks loading state', async () => {
    const mockItems: SearchBoxSuggestion[] = [
      {
        label: 'User is qiupingf',
        mainText: [{text: 'qiupingf'}],
        openPicker: {key: 'user', keyDisplayName: 'User'},
        count: 10,
      },
    ];
    mockSearchService.getTjsSuggestions.and.returnValue(of({items: mockItems}));

    expect(store.suggestions()).toEqual([]);

    store.showSuggestions.set(true);
    await flushAsync();

    expect(store.suggestions()).toEqual(mockItems);
    expect(store.isSuggestionsLoading()).toBeFalse();
  });

  it('handles selectSuggestion with openPicker and applyFilter on TjsSearchStore', () => {
    // 1. Safe no-op on null/undefined
    store.selectSuggestion(null as unknown as SearchBoxSuggestion);

    // 2. openPicker keeps showSuggestions true and sets pendingFilter
    store.searchQuery.set('name');
    store.selectSuggestion({
      label: 'Filter by Name',
      mainText: [{text: 'Name'}],
      openPicker: {key: 'name', keyDisplayName: 'Name'},
    });

    expect(store.searchQuery()).toBe('');
    expect(store.showSuggestions()).toBeTrue();
    expect(store.pendingFilterKey()).toBe('name');

    // 3. applyFilter with autoCollapseAfterApply = true closes suggestions and adds chip
    store.autoCollapseAfterApply.set(true);
    store.selectSuggestion({
      label: 'User is qiupingf',
      mainText: [{text: 'qiupingf'}],
      applyFilter: {
        filter: {key: 'user', stringValue: {value: 'qiupingf'}},
        pillCondition: '(qiupingf)',
      },
    });

    expect(store.showSuggestions()).toBeFalse();
    expect(store.activeChips().some((c) => c.key === 'user')).toBeTrue();

    // 4. applyFilter with autoCollapseAfterApply = false keeps suggestions open
    store.autoCollapseAfterApply.set(false);
    store.selectSuggestion({
      label: 'Status is RUNNING',
      mainText: [{text: 'RUNNING'}],
      applyFilter: {
        filter: {key: 'status', stringValue: {value: 'RUNNING'}},
        pillCondition: 'RUNNING',
      },
    });

    expect(store.showSuggestions()).toBeTrue();
    expect(store.activeChips().some((c) => c.key === 'status')).toBeTrue();
  });

  it('computes isSuggestionsPanelOpen across all criteria combinations on TjsSearchStore', async () => {
    // 1. Closed when showSuggestions is false
    store.showSuggestions.set(false);
    expect(store.isSuggestionsPanelOpen()).toBeFalse();

    // 2. Closed when showSuggestions is true but all criteria/query/suggestions are empty and loading has settled
    store.resetSearchState(false);
    store.showSuggestions.set(true);
    store.searchQuery.set('');
    await flushAsync();
    expect(store.activeChips()).toEqual([]);
    expect(store.pendingFilterKey()).toBeNull();
    expect(store.suggestions()).toEqual([]);
    expect(store.isSuggestionsPanelOpen()).toBeFalse();

    // 3. Open when searchQuery has text
    store.searchQuery.set('device');
    expect(store.isSuggestionsPanelOpen()).toBeTrue();
    store.searchQuery.set('');

    // 4. Open when activeChips has items
    store.addFilterChip({
      key: 'name',
      pillKey: 'Name',
      pillCondition: 'test-name',
      isGroupBy: false,
    });
    expect(store.isSuggestionsPanelOpen()).toBeTrue();

    // 5. Open when pendingFilterKey is set
    store.resetSearchState(false);
    store.showSuggestions.set(true);
    store.openQuickFilter('name', 'Name');
    expect(store.isSuggestionsPanelOpen()).toBeTrue();
  });

  it('builds initial ValuePicker state from enumPicker options, rawValues, tjsFilter, and pillCondition', () => {
    // 1. No active chip with enumPicker options
    store.openValuePicker(
      'status',
      null,
      'Status',
      {
        key: 'status',
        keyDisplayName: 'Status',
        enumPicker: {
          options: [
            {value: 'RUNNING', label: 'Running'},
            {value: 'DONE', label: ''},
          ],
        },
      },
      ['STAGED'],
    );
    expect(store.pickerState().values).toEqual([
      {value: 'RUNNING', displayLabel: 'Running'},
      {value: 'DONE', displayLabel: 'DONE'},
    ]);
    expect(Array.from(store.pickerState().selectedValues)).toEqual(['STAGED']);

    // 2. Active chip with tjsFilter only (no rawValues)
    store.resetSearchState(false);
    store.addFilterChip({
      key: 'user',
      pillKey: 'User',
      pillCondition: 'alice',
      tjsFilter: {key: 'user', stringValue: {value: 'alice'}},
    });
    store.openValuePicker('user', null, 'User');
    expect(Array.from(store.pickerState().selectedValues)).toEqual(['alice']);

    // 3. Active chip with pillCondition only
    store.resetSearchState(false);
    store.addFilterChip({
      key: 'name',
      pillKey: 'Name',
      pillCondition: 'foo, bar',
    });
    store.openValuePicker('name', null, 'Name');
    expect(Array.from(store.pickerState().selectedValues)).toEqual([
      'foo',
      'bar',
    ]);
  });
});
