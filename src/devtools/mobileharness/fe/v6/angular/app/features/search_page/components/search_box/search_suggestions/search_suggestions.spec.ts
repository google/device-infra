import {EnvironmentProviders, Provider, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialog} from '@angular/material/dialog';
import {provideNoopAnimations} from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  Params,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import {of} from 'rxjs';

import {Filter} from '../../../../../core/models/search';
import {CommonParamsService} from '../../../../../core/services/common_params_service';
import {IS_ASSISTANT_ENABLED} from '../../../../../shared/components/diagnose_in_assistant/diagnose_in_assistant';
import {FilterChip, SearchBoxSuggestion} from '../../../models';
import {SearchPageStore} from '../../../services/search_page_store';
import {SearchSuggestions} from './search_suggestions';

class FakeSearchPageStore {
  readonly activeChips = signal<FilterChip[]>([]);
  readonly filterChips = signal<FilterChip[]>([]);
  readonly groupByChips = signal<FilterChip[]>([]);
  readonly pendingFilterKey = signal<string | null>(null);
  readonly pendingFilterTitle = signal<string>('');
  readonly pendingFilter = signal<unknown | null>(null);
  readonly pendingFilterOpenRequest = signal<unknown | null>(null);
  readonly focusChipKey = signal<string | null>(null);
  readonly focusChipType = signal<'filter' | 'groupby' | null>(null);
  readonly isChipPickerActive = jasmine
    .createSpy('isChipPickerActive')
    .and.returnValue(false);
  readonly isKeyPickerActive = jasmine
    .createSpy('isKeyPickerActive')
    .and.returnValue(false);
  readonly effectiveTotalCount = signal<number | null>(151607);
  readonly entity = signal<string>('devices');
  readonly fleet = signal<string>('internal');
  readonly effectiveFilters = signal<Filter[]>([]);
  readonly groupByKeys = signal<string[]>([]);
  readonly isTjs = signal<boolean>(false);
  readonly isSuggestionsLoading = signal<boolean>(false);
  readonly isSuggestionsPanelOpen = signal<boolean>(true);
  readonly suggestions = signal<SearchBoxSuggestion[]>([]);
  readonly searchQuery = signal<string>('');
  readonly autoCollapseAfterApply = signal<boolean>(true);

  clearAllSearch = jasmine.createSpy('clearAllSearch');
  resetSearchState = jasmine.createSpy('resetSearchState');
  removeChip = jasmine.createSpy('removeChip');
  openQuickFilter = jasmine.createSpy('openQuickFilter');
  setAutoCollapseAfterApply = jasmine.createSpy('setAutoCollapseAfterApply');
  openValuePicker = jasmine.createSpy('openValuePicker');
  closeValuePicker = jasmine.createSpy('closeValuePicker');
}

describe('SearchSuggestions', () => {
  let fixture: ComponentFixture<SearchSuggestions>;
  let component: SearchSuggestions;
  let mockStore: FakeSearchPageStore;
  let mockDialog: jasmine.SpyObj<MatDialog>;

  beforeEach(() => {
    mockStore = new FakeSearchPageStore();
    mockDialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    TestBed.configureTestingModule({
      imports: [SearchSuggestions],
      providers: [
        provideNoopAnimations(),
        {provide: SearchPageStore, useValue: mockStore},
        {provide: MatDialog, useValue: mockDialog},
      ],
    });

    fixture = TestBed.createComponent(SearchSuggestions);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('displays Add filter and Add group-by buttons and hides sp-head when there are no filters and groupby selected', () => {
    expect(mockStore.activeChips().length).toBe(0);
    expect(component.showCriteriaSection()).toBeTrue();

    const criteriaSection = fixture.nativeElement.querySelector(
      '.active-criteria-section',
    );
    expect(criteriaSection).toBeTruthy();

    // sp-head should be hidden when there are no filters and groupby selected
    const spHead = fixture.nativeElement.querySelector('.sp-head');
    expect(spHead).toBeNull();

    // Add filter button should be visible
    const addFilterBtn = fixture.nativeElement.querySelector('.add-filter-btn');
    expect(addFilterBtn).toBeTruthy();
    expect(addFilterBtn?.textContent?.trim()).toContain('Add filter');

    // Add group-by button should be visible
    const addGroupByBtn =
      fixture.nativeElement.querySelector('.add-groupby-btn');
    expect(addGroupByBtn).toBeTruthy();
    expect(addGroupByBtn?.textContent?.trim()).toContain('Add group-by');
  });

  it('shows sp-head with title and clear all when active chips exist', () => {
    mockStore.activeChips.set([
      {
        key: 'status',
        pillKey: 'status',
        pillCondition: 'IDLE',
        isGroupBy: false,
      },
    ]);
    mockStore.filterChips.set(mockStore.activeChips());
    fixture.detectChanges();

    const spHead = fixture.nativeElement.querySelector('.sp-head');
    expect(spHead).toBeTruthy();

    const title = fixture.nativeElement.querySelector('.sp-title');
    expect(title?.textContent?.trim()).toBe('151,607 devices');

    const clearAll = fixture.nativeElement.querySelector('.sp-clear');
    expect(clearAll).toBeTruthy();
  });

  it('emits addFilter event when Add filter button is clicked', () => {
    const addFilterSpy = jasmine.createSpy('addFilter');
    component.addFilter.subscribe(addFilterSpy);

    const addFilterBtn = fixture.nativeElement.querySelector('.add-filter-btn');
    addFilterBtn.click();

    expect(addFilterSpy).toHaveBeenCalled();
  });

  it('emits addGroupBy event when Add group-by button is clicked', () => {
    const addGroupBySpy = jasmine.createSpy('addGroupBy');
    component.addGroupBy.subscribe(addGroupBySpy);

    const addGroupByBtn =
      fixture.nativeElement.querySelector('.add-groupby-btn');
    addGroupByBtn.click();

    expect(addGroupBySpy).toHaveBeenCalled();
  });

  it('shows divider and suggestions when suggestions exist', () => {
    mockStore.suggestions.set([
      {
        label: 'Add filter',
        mainText: [{text: 'Model is pixel', emphasized: false}],
        count: 42,
      },
    ]);
    fixture.detectChanges();

    const divider = fixture.nativeElement.querySelector('.suggestions-divider');
    expect(divider).toBeTruthy();

    const items = fixture.nativeElement.querySelectorAll('.suggestion-item');
    expect(items.length).toBe(1);
    expect(items[0]?.textContent).toContain('Model is pixel');
  });

  it('preserves suggestions and adds is-refetching class when background refetching occurs', () => {
    mockStore.suggestions.set([
      {
        label: 'Add filter',
        mainText: [{text: 'Model is pixel', emphasized: false}],
        count: 42,
      },
    ]);
    mockStore.isSuggestionsLoading.set(true);
    fixture.detectChanges();

    const listbox = fixture.nativeElement.querySelector(
      '#search-suggestions-listbox',
    );
    expect(listbox).toBeTruthy();
    expect(listbox.classList.contains('is-refetching')).toBeTrue();

    const items = fixture.nativeElement.querySelectorAll('.suggestion-item');
    expect(items.length).toBe(1);
    expect(items[0]?.textContent).toContain('Model is pixel');

    // Spinner should NOT replace existing items
    const spinner = fixture.nativeElement.querySelector('.suggestion-loading');
    expect(spinner).toBeNull();
  });

  it('shows loading spinner when isSuggestionsLoading is true and suggestions are empty', () => {
    mockStore.suggestions.set([]);
    mockStore.isSuggestionsLoading.set(true);
    fixture.detectChanges();

    const listbox = fixture.nativeElement.querySelector(
      '#search-suggestions-listbox',
    );
    expect(listbox).toBeNull();

    const loadingEl = fixture.nativeElement.querySelector(
      '.suggestion-loading',
    );
    expect(loadingEl).toBeTruthy();
    expect(loadingEl.textContent).toContain('Loading suggestions…');
  });

  it('trackSuggestion generates stable unique identity strings', () => {
    expect(
      component.trackSuggestion({
        openPicker: {key: 'status'},
      }),
    ).toBe('picker:status');

    expect(
      component.trackSuggestion({
        addGroupBy: {key: 'model'},
      }),
    ).toBe('groupby:model');

    expect(
      component.trackSuggestion({
        applyFilter: {
          resultingFilter: {key: 'field::status'},
          pillCondition: 'IDLE',
        },
        label: 'Add filter',
      }),
    ).toBe('apply:field::status:Add filter:IDLE');

    expect(
      component.trackSuggestion({
        applyFilter: {
          filter: {key: 'status'},
          pillCondition: 'RUNNING',
        },
        label: 'Filter',
      }),
    ).toBe('apply-tjs:status:Filter:RUNNING');

    expect(
      component.trackSuggestion({
        label: 'Add filter',
        mainText: [{text: 'Model is Pixel 9'}],
      }),
    ).toBe('item:Add filter:Model is Pixel 9');
  });

  it('formats counts and tooltips correctly per BFF contract', () => {
    expect(component.formatCount({})).toBe('');
    expect(component.formatCount({count: null as unknown as number})).toBe('');
    expect(component.formatCount({count: 0})).toBe('0');
    expect(component.formatCount({count: 0, countPrefix: '+'})).toBe('0');
    expect(component.formatCount({count: 1234})).toBe('1,234');
    expect(component.formatCount({count: 5, countPrefix: '+'})).toBe('+5');
    expect(component.formatCount({count: 10, countUnit: 'groups'})).toBe(
      '10 groups',
    );
    expect(
      component.formatCount({
        count: 42,
        countPrefix: '+',
        countUnit: 'devices',
      }),
    ).toBe('+42 devices');

    expect(component.getCountTooltip({})).toBe('');
    expect(component.getCountTooltip({overMax: false})).toBe('');
    expect(component.getCountTooltip({overMax: true})).toBe(
      'That many groups is hard to scan — consider filtering first.',
    );
  });

  it('evaluates showCriteriaSection correctly across all criteria states', () => {
    // 1. On TJS with no chips and no pending filter -> false
    mockStore.isTjs.set(true);
    mockStore.activeChips.set([]);
    mockStore.pendingFilterKey.set(null);
    fixture.detectChanges();
    expect(component.showCriteriaSection()).toBeFalse();
    expect(
      fixture.nativeElement.querySelector('.active-criteria-section'),
    ).toBeNull();

    // 2. On TJS with pending filter key set -> true
    mockStore.pendingFilterKey.set('status');
    fixture.detectChanges();
    expect(component.showCriteriaSection()).toBeTrue();
    expect(
      fixture.nativeElement.querySelector('.active-criteria-section'),
    ).toBeTruthy();

    // 3. On TJS with active chips present -> true
    mockStore.pendingFilterKey.set(null);
    mockStore.activeChips.set([
      {
        key: 'status',
        pillKey: 'status',
        pillCondition: 'IDLE',
        isGroupBy: false,
      },
    ]);
    fixture.detectChanges();
    expect(component.showCriteriaSection()).toBeTrue();

    // 4. On Fleet mode (isTjs = false) with no chips -> true (Add filter button shown)
    mockStore.isTjs.set(false);
    mockStore.activeChips.set([]);
    fixture.detectChanges();
    expect(component.showCriteriaSection()).toBeTrue();
  });

  it('shows No matches without the Assistant row when IS_ASSISTANT_ENABLED is not provided (OSS)', () => {
    mockStore.searchQuery.set('satellite lab pixel 10');
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('.sp-empty-head')?.textContent,
    ).toContain('No matches for “satellite lab pixel 10”');
    expect(fixture.nativeElement.querySelector('.sp-ask-assistant')).toBeNull();
  });
});

describe('SearchSuggestions Ask Assistant row', () => {
  let mockStore: FakeSearchPageStore;

  function createSuggestions(
    assistantEnabled: boolean,
    pageParams: Params = {},
  ): ComponentFixture<SearchSuggestions> {
    TestBed.resetTestingModule();
    const providers: Array<Provider | EnvironmentProviders> = [
      provideNoopAnimations(),
      provideRouter([]),
      {provide: SearchPageStore, useValue: mockStore},
      {
        provide: MatDialog,
        useValue: jasmine.createSpyObj<MatDialog>('MatDialog', ['open']),
      },
      {
        provide: CommonParamsService,
        useValue: {
          getCommonParams: () => ({
            'fake_data': 'true',
            'is_embedded_mode': 'true',
          }),
        },
      },
      {
        provide: ActivatedRoute,
        useValue: {queryParamMap: of(convertToParamMap(pageParams))},
      },
      {provide: IS_ASSISTANT_ENABLED, useValue: () => assistantEnabled},
    ];
    TestBed.configureTestingModule({imports: [SearchSuggestions], providers});
    const fixture = TestBed.createComponent(SearchSuggestions);
    fixture.detectChanges();
    return fixture;
  }

  function askLink(
    fixture: ComponentFixture<SearchSuggestions>,
  ): HTMLAnchorElement | null {
    return fixture.nativeElement.querySelector('a.sp-ask-assistant');
  }

  function linkParam(link: HTMLAnchorElement, name: string): string | null {
    return new URL(
      link.getAttribute('href') ?? '',
      'http://localhost',
    ).searchParams.get(name);
  }

  function askedQuestion(link: HTMLAnchorElement): string | null {
    return linkParam(link, 'q');
  }

  function askedFromPage(link: HTMLAnchorElement): string | null {
    return linkParam(link, 'from');
  }

  beforeEach(() => {
    mockStore = new FakeSearchPageStore();
    mockStore.searchQuery.set('satellite lab pixel 10');
  });

  it('opens the Assistant in a new tab asking the typed text as written', () => {
    const fixture = createSuggestions(true);

    const link = askLink(fixture);
    expect(link).not.toBeNull();
    expect(link!.querySelector('.sp-ask-label')?.textContent?.trim()).toBe(
      'Ask Assistant about “satellite lab pixel 10”',
    );
    expect(link!.getAttribute('target')).toBe('_blank');
    expect(link!.getAttribute('rel')).toBe('noopener');
    expect(link!.getAttribute('role')).toBe('option');
    expect(link!.querySelector('.sp-ask-icon')?.textContent?.trim()).toBe(
      'smart_toy',
    );
    const href = link!.getAttribute('href') ?? '';
    expect(href).toContain('/assistant?');
    expect(href).toContain('enable_assistant=true');
    expect(href).toContain('fake_data=true');
    expect(href).not.toContain('is_embedded_mode');
    expect(askedQuestion(link!)).toBe('satellite lab pixel 10');
    expect(askedFromPage(link!)).toBe('/devices');
  });

  it('asks the typed text as written about the page with its filters and group-by', () => {
    const status: FilterChip = {
      key: 'device_field::status',
      pillKey: 'Status',
      pillCondition: '2',
      fleetFilter: {
        key: 'device_field::status',
        simple: {values: [{value: 'IDLE'}, {value: 'BUSY'}]},
      },
    };
    const pool: FilterChip = {
      key: 'dimension::pool',
      pillKey: 'Pool',
      pillCondition: 'shared',
      fleetFilter: {
        key: 'dimension::pool',
        simple: {values: [{value: 'shared'}]},
      },
    };
    const model: FilterChip = {
      key: 'dimension::model',
      pillKey: 'Model',
      pillCondition: '',
      isGroupBy: true,
    };
    mockStore.activeChips.set([status, pool, model]);
    mockStore.filterChips.set([status, pool]);
    mockStore.groupByChips.set([model]);
    const fixture = createSuggestions(true, {
      'f': ['device_field::status~IDLE,BUSY', 'dimension::pool~shared'],
      'gb': 'dimension::model',
    });

    const link = askLink(fixture)!;
    expect(link.querySelector('.sp-ask-label')?.textContent?.trim()).toBe(
      'Ask Assistant about “satellite lab pixel 10”',
    );
    expect(askedQuestion(link)).toBe('satellite lab pixel 10');
    expect(askedFromPage(link)).toBe(
      '/devices?f=device_field::status~IDLE,BUSY&f=dimension::pool~shared&gb=dimension::model',
    );
  });

  it('offers the same row on Partner host search', () => {
    mockStore.entity.set('hosts');
    mockStore.fleet.set('ats');
    const fixture = createSuggestions(true, {'fleet': 'ats'});

    const link = askLink(fixture)!;
    expect(link.querySelector('.sp-ask-label')?.textContent?.trim()).toBe(
      'Ask Assistant about “satellite lab pixel 10”',
    );
    expect(askedQuestion(link)).toBe('satellite lab pixel 10');
    expect(askedFromPage(link)).toBe('/hosts?fleet=ats');
  });

  it('keeps only f, gb, and fleet in the page path', () => {
    const fixture = createSuggestions(true, {
      'f': 'dimension::model~Pixel 10',
      'fake_data': 'true',
      'is_embedded_mode': 'true',
      'debug': 'true',
      'q': 'stale all-site query',
      'enable_assistant': 'true',
      'origin': 'home',
      'src': 'alloc_diag',
      'arsenal_query': 'filter=foo',
    });

    expect(askedFromPage(askLink(fixture)!)).toBe(
      '/devices?f=dimension::model~Pixel%2010',
    );
  });

  it('is not offered when the Assistant is off or on test, job, and session search', () => {
    const off = createSuggestions(false);
    expect(off.componentInstance.showAskAssistantRow()).toBeFalse();
    expect(askLink(off)).toBeNull();

    mockStore.isTjs.set(true);
    const tjs = createSuggestions(true);
    expect(tjs.componentInstance.showAskAssistantRow()).toBeFalse();
    expect(tjs.nativeElement.querySelector('.sp-empty')).not.toBeNull();
    expect(askLink(tjs)).toBeNull();
  });

  it('is not offered while suggestions exist or load', () => {
    mockStore.suggestions.set([
      {label: 'Add filter', mainText: [{text: 'Model is pixel 10'}]},
    ]);
    const withSuggestions = createSuggestions(true);
    expect(withSuggestions.componentInstance.showAskAssistantRow()).toBeFalse();
    expect(askLink(withSuggestions)).toBeNull();

    mockStore.suggestions.set([]);
    mockStore.isSuggestionsLoading.set(true);
    const loading = createSuggestions(true);
    expect(loading.componentInstance.showAskAssistantRow()).toBeFalse();
    expect(askLink(loading)).toBeNull();
  });

  it('marks the row selected while keyboard navigation highlights it', () => {
    const fixture = createSuggestions(true);
    const link = askLink(fixture)!;
    expect(fixture.componentInstance.showAskAssistantRow()).toBeTrue();
    expect(link.id).toBe('search-ask-assistant');
    expect(link.getAttribute('aria-selected')).toBe('false');
    expect(link.classList).not.toContain('active');

    fixture.componentRef.setInput('askAssistantActive', true);
    fixture.detectChanges();

    expect(link.getAttribute('aria-selected')).toBe('true');
    expect(link.classList).toContain('active');
  });

  it('clicks the link when openAskAssistant is called', () => {
    const fixture = createSuggestions(true);
    const click = spyOn(askLink(fixture)!, 'click');

    fixture.componentInstance.openAskAssistant();

    expect(click).toHaveBeenCalled();
  });
});
