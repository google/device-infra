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
    await new Promise((resolve) => setTimeout(resolve, 10));
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
});
