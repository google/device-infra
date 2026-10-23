import {computed, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideNoopAnimations} from '@angular/platform-browser/animations';

import {HeaderSearchDockService} from '../../../../../shared/services/header_search_dock_service';
import {
  FilterChip,
  FilterKeyMetadata,
  FleetScope,
  SearchBoxSuggestion,
} from '../../../models';
import {SearchPageStore} from '../../../services/search_page_store';
import {SearchInput} from './search_input';

class FakeSearchPageStore {
  readonly entity = signal<string>('devices');
  readonly isLandingState = signal<boolean>(false);
  readonly showScopeSwitcher = signal<boolean>(true);
  readonly fleet = signal<FleetScope>('internal');
  readonly activeChips = signal<FilterChip[]>([]);
  readonly groupByKeys = signal<string[]>([]);
  readonly searchQuery = signal<string>('');
  readonly searchPlaceholder = signal<string>('Search devices...');
  readonly showSearchClear = signal<boolean>(false);
  readonly showSuggestions = signal<boolean>(false);
  readonly suggestions = signal<SearchBoxSuggestion[]>([]);
  readonly isSuggestionsLoading = signal<boolean>(false);
  readonly showValuePicker = signal<boolean>(false);
  readonly pickerKey = signal<string>('');
  readonly pickerTitle = signal<string>('');
  readonly pendingFilter = signal<{
    key: string;
    displayName: string;
    metadata?: FilterKeyMetadata;
  } | null>(null);
  readonly pendingFilterOpenRequest = signal<{
    key: string;
    title?: string;
    metadata?: FilterKeyMetadata;
    stagedValues?: string[];
  } | null>(null);
  readonly focusChipKey = signal<string | null>(null);
  readonly focusChipType = signal<'filter' | 'groupby' | null>(null);
  readonly autoCollapseAfterApply = signal<boolean>(true);

  readonly filterChips = computed<FilterChip[]>(() =>
    this.activeChips().filter((c) => !c.isGroupBy),
  );
  readonly groupByChips = computed<FilterChip[]>(() =>
    this.activeChips().filter((c) => c.isGroupBy),
  );
  readonly pendingFilterKey = computed<string | null>(() => {
    const pf = this.pendingFilter();
    const pk = pf?.key || (this.showValuePicker() ? this.pickerKey() : null);
    if (!pk) return null;
    return this.filterChips().some((c) => c.key === pk) ? null : pk;
  });
  readonly pendingFilterTitle = computed<string>(() => {
    return (
      this.pendingFilter()?.displayName ||
      this.pickerTitle() ||
      this.pendingFilterKey() ||
      ''
    );
  });
  readonly isSuggestionsPanelOpen = computed<boolean>(
    () =>
      this.showSuggestions() &&
      (!this.isTjs() ||
        this.activeChips().length > 0 ||
        !!this.pendingFilterKey() ||
        this.isSuggestionsLoading() ||
        this.suggestions().length > 0 ||
        Boolean(this.searchQuery().trim())),
  );
  readonly effectiveTotalCount = signal<number | null>(null);
  readonly isTjs = signal<boolean>(false);
  readonly effectiveFilters = computed<FilterChip[]>(() => this.filterChips());

  setFleet = jasmine.createSpy('setFleet');
  setAutoCollapseAfterApply = jasmine
    .createSpy('setAutoCollapseAfterApply')
    .and.callFake((val: boolean) => {
      this.autoCollapseAfterApply.set(val);
    });
  selectSuggestion = jasmine
    .createSpy('selectSuggestion')
    .and.callFake((item: SearchBoxSuggestion) => {
      if (item.openPicker) {
        this.showSuggestions.set(true);
      } else {
        this.showSuggestions.set(!this.autoCollapseAfterApply());
      }
    });
  cancelPendingFilter = jasmine
    .createSpy('cancelPendingFilter')
    .and.callFake(() => {
      this.pendingFilter.set(null);
    });
  clearSearch = jasmine.createSpy('clearSearch').and.callFake(() => {
    this.searchQuery.set('');
  });
  resetSearchState = jasmine.createSpy('resetSearchState');
  clearSearchQuery = jasmine.createSpy('clearSearchQuery').and.callFake(() => {
    this.searchQuery.set('');
  });
  executeSearch = jasmine.createSpy('executeSearch');
  removeFilterChip = jasmine.createSpy('removeFilterChip');
  clearFilterChips = jasmine.createSpy('clearFilterChips').and.callFake(() => {
    for (const chip of this.activeChips().filter((c) => !c.isGroupBy)) {
      this.removeFilterChip(chip);
    }
  });
  clearGroupByChips = jasmine
    .createSpy('clearGroupByChips')
    .and.callFake(() => {
      for (const chip of this.activeChips().filter((c) => c.isGroupBy)) {
        this.removeFilterChip(chip);
      }
    });
  openValuePicker = jasmine.createSpy('openValuePicker');
  closeValuePicker = jasmine.createSpy('closeValuePicker');
  openQuickFilter = jasmine
    .createSpy('openQuickFilter')
    .and.callFake(
      (
        key: string,
        title?: string,
        metadata?: FilterKeyMetadata,
        stagedValues?: string[],
        anchor?: HTMLElement,
      ) => {
        this.focusChipKey.set(key);
        this.showSuggestions.set(true);
        this.openValuePicker(key, anchor, title, metadata, stagedValues, true);
      },
    );
  openQuickGroupBy = jasmine
    .createSpy('openQuickGroupBy')
    .and.callFake((key: string) => {
      const isAlreadyActive = this.activeChips().some(
        (c) => c.isGroupBy && c.key === key,
      );
      this.focusChipKey.set(key);
      if (isAlreadyActive) {
        this.showSuggestions.set(true);
      } else {
        this.showSuggestions.set(!this.autoCollapseAfterApply());
      }
    });
  isChipPickerActive = jasmine
    .createSpy('isChipPickerActive')
    .and.returnValue(false);
  isKeyPickerActive = jasmine
    .createSpy('isKeyPickerActive')
    .and.returnValue(false);
}

describe('SearchInput', () => {
  let fixture: ComponentFixture<SearchInput>;
  let component: SearchInput;
  let mockStore: FakeSearchPageStore;

  beforeEach(async () => {
    mockStore = new FakeSearchPageStore();

    await TestBed.configureTestingModule({
      imports: [SearchInput],
      providers: [
        provideNoopAnimations(),
        {provide: SearchPageStore, useValue: mockStore},
        HeaderSearchDockService,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SearchInput);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create component', () => {
    expect(component).toBeTruthy();
  });

  it('should render scope switcher and handle fleet switching', () => {
    const buttons = fixture.nativeElement.querySelectorAll('.scope-seg');
    expect(buttons.length).toBe(2);
    expect(buttons[0].textContent.trim()).toBe('Google');
    expect(buttons[1].textContent.trim()).toBe('Partner');

    buttons[1].click();
    expect(mockStore.setFleet).toHaveBeenCalledWith('ats');
  });

  it('should render single active chip inline when only 1 active chip is present', () => {
    mockStore.activeChips.set([
      {
        key: 'status',
        pillKey: 'status',
        pillCondition: 'IDLE',
        isGroupBy: false,
      },
    ]);
    fixture.detectChanges();

    const chips = fixture.nativeElement.querySelectorAll(
      '.active-chips-container .search-chip',
    );
    expect(chips.length).toBe(1);
    expect(chips[0].querySelector('.chip-label').textContent.trim()).toBe(
      'status',
    );
    expect(chips[0].querySelector('.chip-value').textContent.trim()).toBe(
      '(IDLE)',
    );
  });

  it('should trigger removeFilterChip when close icon is clicked', () => {
    const chip: FilterChip = {
      key: 'status',
      pillKey: 'status',
      pillCondition: 'IDLE',
    };
    mockStore.activeChips.set([chip]);
    fixture.detectChanges();

    const removeBtn = fixture.nativeElement.querySelector('.chip-remove');
    expect(removeBtn).toBeTruthy();
    removeBtn.click();

    expect(mockStore.removeFilterChip).toHaveBeenCalledWith(chip);
  });

  it('should open suggestion popover and locate corresponding filter/group-by when active chip is clicked', () => {
    const filterChip: FilterChip = {
      key: 'status',
      pillKey: 'status',
      pillCondition: 'IDLE',
      metadata: {keyDisplayName: 'Device Status'},
      isGroupBy: false,
    };
    const groupByChip: FilterChip = {
      key: 'driver',
      pillKey: 'driver',
      pillCondition: 'driver',
      isGroupBy: true,
    };
    mockStore.activeChips.set([filterChip, groupByChip]);
    fixture.detectChanges();

    const chips = fixture.nativeElement.querySelectorAll(
      '.active-chips-container .search-chip',
    );
    expect(chips.length).toBe(2);

    // Click active filter chip -> opens suggestion popover and locates filter
    chips[0].click();
    fixture.detectChanges();
    expect(mockStore.openQuickFilter).toHaveBeenCalledWith(
      'status',
      'Device Status',
      filterChip.metadata,
    );
    expect(mockStore.showSuggestions()).toBeTrue();
    expect(mockStore.focusChipKey()).toBe('status');

    // Click active group-by chip (already active) -> opens suggestion popover and locates group-by regardless of autoCollapseAfterApply
    mockStore.autoCollapseAfterApply.set(true);
    mockStore.showSuggestions.set(false);
    chips[1].click();
    fixture.detectChanges();
    expect(mockStore.openQuickGroupBy).toHaveBeenCalledWith('driver', 'driver');
    expect(mockStore.showSuggestions()).toBeTrue();
    expect(mockStore.focusChipKey()).toBe('driver');

    mockStore.autoCollapseAfterApply.set(false);
    mockStore.showSuggestions.set(false);
    chips[1].click();
    fixture.detectChanges();
    expect(mockStore.showSuggestions()).toBeTrue();
    expect(mockStore.focusChipKey()).toBe('driver');
  });

  it('should update searchQuery and showSuggestions on input', () => {
    const input = fixture.nativeElement.querySelector('input.search-input');
    input.value = 'pixel';
    input.dispatchEvent(new Event('input'));

    expect(mockStore.searchQuery()).toBe('pixel');
    expect(mockStore.showSuggestions()).toBeTrue();
    expect(mockStore.closeValuePicker).toHaveBeenCalled();
  });

  it('should close value picker and show suggestions when search input is focused', () => {
    mockStore.showValuePicker.set(true);
    const input = fixture.nativeElement.querySelector(
      'input.search-input',
    ) as HTMLInputElement;

    input.dispatchEvent(new Event('focus'));
    fixture.detectChanges();

    expect(mockStore.closeValuePicker).toHaveBeenCalled();
    expect(mockStore.showSuggestions()).toBeTrue();
  });

  it('should close value picker and show suggestions on input click', () => {
    mockStore.showValuePicker.set(true);
    const input = fixture.nativeElement.querySelector(
      'input.search-input',
    ) as HTMLInputElement;

    input.click();
    fixture.detectChanges();

    expect(mockStore.closeValuePicker).toHaveBeenCalled();
    expect(mockStore.showSuggestions()).toBeTrue();
  });

  it('should clear only searchQuery and preserve activeChips when clear button is clicked', () => {
    mockStore.searchQuery.set('pixel');
    mockStore.activeChips.set([
      {
        key: 'status',
        pillKey: 'status',
        pillCondition: 'IDLE',
        isGroupBy: false,
      },
    ]);
    mockStore.showSearchClear.set(true);
    fixture.detectChanges();

    const clearBtn = fixture.nativeElement.querySelector('.search-clear-btn');
    expect(clearBtn).toBeTruthy();
    clearBtn.click();

    expect(mockStore.searchQuery()).toBe('');
    expect(mockStore.activeChips().length).toBe(1);
    expect(mockStore.resetSearchState).not.toHaveBeenCalled();
  });

  it('should trigger executeSearch when refresh button is clicked in results mode and hide it in landing mode', () => {
    mockStore.isLandingState.set(false);
    fixture.detectChanges();

    const refreshBtn =
      fixture.nativeElement.querySelector('.query-refresh-btn');
    expect(refreshBtn).toBeTruthy();
    refreshBtn.click();

    expect(mockStore.executeSearch).toHaveBeenCalled();

    mockStore.isLandingState.set(true);
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('.query-refresh-btn'),
    ).toBeNull();
  });

  it('should apply docked host class and clear placeholder when active chips exist in docked mode', () => {
    expect(fixture.nativeElement.classList.contains('docked')).toBeFalse();
    mockStore.activeChips.set([
      {
        key: 'status',
        pillKey: 'status',
        pillCondition: 'IN_SERVICE_IDLE',
        isGroupBy: false,
      },
      {
        key: 'model',
        pillKey: 'model',
        pillCondition: 'Pixel 9 Pro XL',
        isGroupBy: false,
      },
    ]);
    fixture.componentRef.setInput('isDocked', true);
    fixture.detectChanges();

    expect(fixture.nativeElement.classList.contains('docked')).toBeTrue();
    expect(component.effectivePlaceholder()).toBe('');
    expect(component.showCompositeFilterChip()).toBeTrue();
  });

  it('should react to HeaderSearchDockService when isDocked input is not set', () => {
    const dockService = TestBed.inject(HeaderSearchDockService);
    expect(component.isDocked()).toBeFalse();
    expect(fixture.nativeElement.classList.contains('docked')).toBeFalse();

    const dummyEl = document.createElement('div');
    dockService.dock(dummyEl);
    fixture.detectChanges();

    expect(component.isDocked()).toBeTrue();
    expect(fixture.nativeElement.classList.contains('docked')).toBeTrue();

    dockService.undock();
    fixture.detectChanges();

    expect(component.isDocked()).toBeFalse();
    expect(fixture.nativeElement.classList.contains('docked')).toBeFalse();
  });

  it('should NOT composite when there is 1 active filter and 1 active group-by', () => {
    mockStore.activeChips.set([
      {
        key: 'status',
        pillKey: 'status',
        pillCondition: 'IDLE',
        isGroupBy: false,
      },
      {
        key: 'driver',
        pillKey: 'driver',
        pillCondition: 'driver',
        isGroupBy: true,
      },
    ]);
    fixture.detectChanges();

    expect(component.isCollapsed()).toBeFalse();
    expect(component.showCompositeFilterChip()).toBeFalse();
    expect(component.showCompositeGroupByChip()).toBeFalse();
    expect(
      fixture.nativeElement.querySelector('.composite-filters'),
    ).toBeNull();
    expect(
      fixture.nativeElement.querySelector('.composite-groupby'),
    ).toBeNull();

    const chips = fixture.nativeElement.querySelectorAll(
      '.active-chips-container .search-chip',
    );
    expect(chips.length).toBe(2);
    expect(chips[0].textContent).toContain('status');
    expect(chips[0].textContent).toContain('(IDLE)');
    expect(chips[1].textContent).toContain('group by');
    expect(chips[1].textContent).toContain('driver');
  });

  it('should composite each type independently only when that type count > 1 and open suggestion popover on composite click', () => {
    mockStore.activeChips.set([
      {
        key: 'status',
        pillKey: 'status',
        pillCondition: 'IDLE',
        isGroupBy: false,
      },
      {
        key: 'model',
        pillKey: 'model',
        pillCondition: '!Pixel 8',
        negated: true,
        isGroupBy: false,
      },
      {
        key: 'driver',
        pillKey: 'driver',
        pillCondition: 'driver',
        isGroupBy: true,
      },
    ]);
    mockStore.suggestions.set([
      {
        label: 'Add filter',
        mainText: [{text: 'Owner', emphasized: false}],
      },
    ]);
    fixture.detectChanges();

    // 2 filters -> composited; 1 group-by -> individual
    expect(component.isCollapsed()).toBeTrue();
    expect(component.showCompositeFilterChip()).toBeTrue();
    expect(component.showCompositeGroupByChip()).toBeFalse();

    const compositeFilters =
      fixture.nativeElement.querySelector('.composite-filters');
    const compositeGroupBy =
      fixture.nativeElement.querySelector('.composite-groupby');
    expect(compositeFilters).toBeTruthy();
    expect(compositeFilters.textContent).toContain('2 filters');
    expect(compositeGroupBy).toBeNull();

    const individualGroupBy = fixture.nativeElement.querySelector(
      '.active-chips-container .group-by-chip',
    );
    expect(individualGroupBy).toBeTruthy();
    expect(individualGroupBy.textContent).toContain('driver');

    // Clicking composite chip opens suggestion popover
    compositeFilters.click();
    fixture.detectChanges();

    expect(mockStore.showSuggestions()).toBeTrue();
    expect(mockStore.closeValuePicker).toHaveBeenCalled();

    // Suggestion popover renders active Filter and Group by section above suggestion items
    const popover = fixture.nativeElement.querySelector(
      '.search-suggestions-popover',
    );
    expect(popover).toBeTruthy();

    const activeSection = popover.querySelector('.active-criteria-section');
    expect(activeSection).toBeTruthy();
    const popoverChips = activeSection.querySelectorAll('.search-chip');
    expect(popoverChips.length).toBe(3);

    // Add filter and Add group-by buttons are rendered at the end of each row
    const addFilterBtn = activeSection.querySelector('.add-filter-btn');
    const addGroupByBtn = activeSection.querySelector('.add-groupby-btn');
    expect(addFilterBtn).toBeTruthy();
    expect(addGroupByBtn).toBeTruthy();

    addFilterBtn.click();
    fixture.detectChanges();
    expect(component.highlightedGroup()).toBe('filters');

    addGroupByBtn.click();
    fixture.detectChanges();
    expect(component.highlightedGroup()).toBe('groupby');

    // Clicking an active filter chip inside suggestion popover opens ValuePicker anchored to that popover chip while keeping suggestions open
    popoverChips[0].click();
    fixture.detectChanges();
    expect(mockStore.openValuePicker).toHaveBeenCalledWith(
      'status',
      popoverChips[0],
      'status',
      undefined,
      undefined,
      true,
    );

    // Clicking remove button on composite filters chip removes all filter chips
    const removeCompositeFiltersBtn =
      compositeFilters.querySelector('.chip-remove');
    expect(removeCompositeFiltersBtn).toBeTruthy();
    removeCompositeFiltersBtn.click();
    expect(mockStore.removeFilterChip).toHaveBeenCalledTimes(2);
  });

  it('should not render add filter or group-by button in TJS mode', () => {
    mockStore.isTjs.set(true);
    mockStore.showSuggestions.set(true);
    mockStore.activeChips.set([
      {
        pillKey: 'status',
        pillCondition: 'passed',
        key: 'status',
        isGroupBy: false,
      },
    ]);
    fixture.detectChanges();

    const popover = fixture.nativeElement.querySelector(
      '.search-suggestions-popover',
    );
    expect(popover).toBeTruthy();

    const activeSection = popover.querySelector('.active-criteria-section');
    expect(activeSection).toBeTruthy();

    const addFilterBtn = activeSection.querySelector('.add-filter-btn');
    const addGroupByBtn = activeSection.querySelector('.add-groupby-btn');
    expect(addFilterBtn).toBeNull();
    expect(addGroupByBtn).toBeNull();
  });

  it('should render temporary pending chip when pendingFilter is set and open ValuePicker on click', () => {
    mockStore.showSuggestions.set(true);
    mockStore.showValuePicker.set(true);
    mockStore.pickerKey.set('dimension::model');
    mockStore.pickerTitle.set('Model');
    mockStore.pendingFilter.set({
      key: 'dimension::model',
      displayName: 'Model',
    });
    mockStore.focusChipKey.set('dimension::model');
    fixture.detectChanges();

    const popover = fixture.nativeElement.querySelector(
      '.search-suggestions-popover',
    );
    expect(popover).toBeTruthy();

    const pendingChip = popover.querySelector(
      '.search-chip.pending',
    ) as HTMLElement;
    expect(pendingChip).toBeTruthy();
    expect(pendingChip.textContent).toContain('Model');

    pendingChip.click();
    fixture.detectChanges();

    expect(mockStore.openValuePicker).toHaveBeenCalledWith(
      'dimension::model',
      pendingChip,
      'Model',
      undefined,
      undefined,
      true,
    );
  });

  it('should display loading state for suggestion items in popover when isSuggestionsLoading is true', () => {
    mockStore.showSuggestions.set(true);
    mockStore.isSuggestionsLoading.set(true);
    fixture.detectChanges();

    const popover = fixture.nativeElement.querySelector(
      '.search-suggestions-popover',
    );
    expect(popover).toBeTruthy();

    const loadingEl = popover.querySelector('.suggestion-loading');
    expect(loadingEl).toBeTruthy();
    expect(loadingEl.textContent).toContain('Loading suggestions…');

    mockStore.isSuggestionsLoading.set(false);
    mockStore.suggestions.set([
      {
        label: 'Filter',
        mainText: [
          {text: 'model is ', emphasized: false},
          {text: 'pixel 9', emphasized: true},
        ],
        rawItem: {openPicker: {key: 'model'}},
      },
    ]);
    fixture.detectChanges();

    expect(popover.querySelector('.suggestion-loading')).toBeNull();
    const items = popover.querySelectorAll('.suggestion-item');
    expect(items.length).toBe(1);
    expect(items[0].textContent).toContain('pixel 9');
  });

  it('should toggle auto-collapse after applying checkbox in sp-foot and keep or close popover when selecting suggestions', () => {
    mockStore.showSuggestions.set(true);
    mockStore.suggestions.set([
      {
        label: 'Filter',
        mainText: [
          {text: 'status is ', emphasized: false},
          {text: 'IDLE', emphasized: true},
        ],
        rawItem: {openPicker: {key: 'status'}},
      },
    ]);
    fixture.detectChanges();

    const checkbox = fixture.nativeElement.querySelector(
      '.sp-foot .sp-auto-collapse input[type="checkbox"]',
    ) as HTMLInputElement;
    expect(checkbox).toBeTruthy();
    expect(checkbox.checked).toBeTrue();

    // Uncheck auto-collapse after applying
    checkbox.checked = false;
    checkbox.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(mockStore.setAutoCollapseAfterApply).toHaveBeenCalledWith(false);
    expect(mockStore.autoCollapseAfterApply()).toBeFalse();

    // Selecting a filter suggestion while unchecked keeps the popover open
    const item = fixture.nativeElement.querySelector('.suggestion-item');
    item.click();
    fixture.detectChanges();
    expect(mockStore.showSuggestions()).toBeTrue();

    // Re-check auto-collapse after applying -> selecting suggestion closes the popover
    checkbox.checked = true;
    checkbox.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(mockStore.autoCollapseAfterApply()).toBeTrue();

    item.click();
    fixture.detectChanges();
    expect(mockStore.showSuggestions()).toBeFalse();
  });

  it('should auto-collapse suggestion popover when removing filter or group-by chips if autoCollapseAfterApply is checked', () => {
    mockStore.activeChips.set([
      {
        key: 'status',
        pillKey: 'status',
        pillCondition: 'IDLE',
        isGroupBy: false,
      },
      {
        key: 'driver',
        pillKey: 'driver',
        pillCondition: 'driver',
        isGroupBy: true,
      },
    ]);
    mockStore.showSuggestions.set(true);
    mockStore.autoCollapseAfterApply.set(false);
    fixture.detectChanges();

    const popover = fixture.nativeElement.querySelector(
      '.search-suggestions-popover',
    );
    expect(popover).toBeTruthy();

    // When autoCollapseAfterApply is false, removing a chip keeps suggestions open
    const removeBtns = popover.querySelectorAll('.chip-remove');
    expect(removeBtns.length).toBe(2);
    removeBtns[0].click();
    fixture.detectChanges();
    expect(mockStore.removeFilterChip).toHaveBeenCalled();
    expect(mockStore.showSuggestions()).toBeTrue();

    // When autoCollapseAfterApply is true, removing a chip closes suggestions popover
    mockStore.autoCollapseAfterApply.set(true);
    fixture.detectChanges();
    removeBtns[1].click();
    fixture.detectChanges();
    expect(mockStore.showSuggestions()).toBeFalse();
  });

  it('should disable Add group-by button when 3 group-by keys are active', () => {
    mockStore.activeChips.set([
      {key: 'host', pillKey: 'host', pillCondition: 'host', isGroupBy: true},
      {key: 'model', pillKey: 'model', pillCondition: 'model', isGroupBy: true},
      {key: 'pool', pillKey: 'pool', pillCondition: 'pool', isGroupBy: true},
    ]);
    mockStore.showSuggestions.set(true);
    fixture.detectChanges();

    const addGroupByBtn = fixture.nativeElement.querySelector(
      '.add-groupby-btn',
    ) as HTMLButtonElement | null;
    expect(addGroupByBtn).toBeTruthy();
    expect(addGroupByBtn?.disabled).toBeTrue();
    expect(addGroupByBtn?.getAttribute('title')).toBe('Up to 3 group-by keys');
  });

  it('should close suggestions on blur when clicking outside and autoCollapseAfterApply is true', () => {
    mockStore.showSuggestions.set(true);
    mockStore.autoCollapseAfterApply.set(true);
    component.highlightedGroup.set('filters');
    fixture.detectChanges();

    const outsideEl = document.createElement('div');
    document.body.appendChild(outsideEl);

    const input = fixture.nativeElement.querySelector('input.search-input');
    input.dispatchEvent(new FocusEvent('blur', {relatedTarget: outsideEl}));
    fixture.detectChanges();

    expect(mockStore.showSuggestions()).toBeFalse();
    expect(component.highlightedGroup()).toBeNull();

    document.body.removeChild(outsideEl);
  });

  it('should NOT close suggestions on blur when relatedTarget is inside overlay or value picker', () => {
    mockStore.showSuggestions.set(true);
    mockStore.autoCollapseAfterApply.set(true);
    fixture.detectChanges();

    const overlayContainer = document.createElement('div');
    overlayContainer.className = 'cdk-overlay-container';
    const overlayTarget = document.createElement('button');
    overlayContainer.appendChild(overlayTarget);
    document.body.appendChild(overlayContainer);

    const input = fixture.nativeElement.querySelector('input.search-input');
    input.dispatchEvent(new FocusEvent('blur', {relatedTarget: overlayTarget}));
    fixture.detectChanges();

    expect(mockStore.showSuggestions()).toBeTrue();

    const pickerContainer = document.createElement('div');
    pickerContainer.className = 'value-picker';
    const pickerTarget = document.createElement('button');
    pickerContainer.appendChild(pickerTarget);
    document.body.appendChild(pickerContainer);

    input.dispatchEvent(new FocusEvent('blur', {relatedTarget: pickerTarget}));
    fixture.detectChanges();

    expect(mockStore.showSuggestions()).toBeTrue();

    document.body.removeChild(overlayContainer);
    document.body.removeChild(pickerContainer);
  });

  it('should NOT close suggestions on blur when autoCollapseAfterApply is false or value picker is open', () => {
    mockStore.showSuggestions.set(true);
    mockStore.autoCollapseAfterApply.set(false);
    fixture.detectChanges();

    const outsideEl = document.createElement('div');
    document.body.appendChild(outsideEl);

    const input = fixture.nativeElement.querySelector('input.search-input');
    input.dispatchEvent(new FocusEvent('blur', {relatedTarget: outsideEl}));
    fixture.detectChanges();

    expect(mockStore.showSuggestions()).toBeTrue();

    // Value picker open
    mockStore.autoCollapseAfterApply.set(true);
    mockStore.showValuePicker.set(true);
    fixture.detectChanges();

    input.dispatchEvent(new FocusEvent('blur', {relatedTarget: outsideEl}));
    fixture.detectChanges();

    expect(mockStore.showSuggestions()).toBeTrue();

    // Pending filter active
    mockStore.showValuePicker.set(false);
    mockStore.pendingFilter.set({key: 'status', displayName: 'Status'});
    fixture.detectChanges();

    input.dispatchEvent(new FocusEvent('blur', {relatedTarget: outsideEl}));
    fixture.detectChanges();

    expect(mockStore.showSuggestions()).toBeTrue();

    document.body.removeChild(outsideEl);
  });

  it('should close suggestions and reset highlightedGroup on document mousedown outside', () => {
    mockStore.showSuggestions.set(true);
    component.highlightedGroup.set('filters');
    fixture.detectChanges();

    const outsideEl = document.createElement('div');
    document.body.appendChild(outsideEl);

    outsideEl.dispatchEvent(new MouseEvent('mousedown', {bubbles: true}));
    fixture.detectChanges();

    expect(mockStore.showSuggestions()).toBeFalse();
    expect(component.highlightedGroup()).toBeNull();

    document.body.removeChild(outsideEl);
  });

  it('should NOT close suggestions on document mousedown inside search box or overlay', () => {
    mockStore.showSuggestions.set(true);
    fixture.detectChanges();

    const searchBox = fixture.nativeElement.querySelector('.search-box');
    searchBox.dispatchEvent(new MouseEvent('mousedown', {bubbles: true}));
    fixture.detectChanges();
    expect(mockStore.showSuggestions()).toBeTrue();

    const overlayContainer = document.createElement('div');
    overlayContainer.className = 'cdk-overlay-container';
    document.body.appendChild(overlayContainer);

    overlayContainer.dispatchEvent(
      new MouseEvent('mousedown', {bubbles: true}),
    );
    fixture.detectChanges();
    expect(mockStore.showSuggestions()).toBeTrue();

    document.body.removeChild(overlayContainer);
  });

  it('should navigate suggestions via ArrowDown and ArrowUp and wrap correctly', () => {
    mockStore.showSuggestions.set(true);
    mockStore.suggestions.set([
      {label: 'Filter 1', mainText: [{text: '1', emphasized: false}]},
      {label: 'Filter 2', mainText: [{text: '2', emphasized: false}]},
      {label: 'Filter 3', mainText: [{text: '3', emphasized: false}]},
    ]);
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('input.search-input');

    // Initial index is -1
    expect(component.activeSuggestionIndex()).toBe(-1);

    // ArrowDown -> 0
    input.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'ArrowDown', bubbles: true}),
    );
    expect(component.activeSuggestionIndex()).toBe(0);

    // ArrowDown -> 1
    input.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'ArrowDown', bubbles: true}),
    );
    expect(component.activeSuggestionIndex()).toBe(1);

    // ArrowDown -> 2
    input.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'ArrowDown', bubbles: true}),
    );
    expect(component.activeSuggestionIndex()).toBe(2);

    // ArrowDown wraps -> 0
    input.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'ArrowDown', bubbles: true}),
    );
    expect(component.activeSuggestionIndex()).toBe(0);

    // ArrowUp wraps -> 2
    input.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'ArrowUp', bubbles: true}),
    );
    expect(component.activeSuggestionIndex()).toBe(2);

    // ArrowUp -> 1
    input.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'ArrowUp', bubbles: true}),
    );
    expect(component.activeSuggestionIndex()).toBe(1);
  });

  it('should navigate suggestions via ArrowDown even when background refetch is pending (isSuggestionsLoading is true)', () => {
    mockStore.showSuggestions.set(true);
    mockStore.isSuggestionsLoading.set(true);
    mockStore.suggestions.set([
      {label: 'Filter 1', mainText: [{text: '1', emphasized: false}]},
      {label: 'Filter 2', mainText: [{text: '2', emphasized: false}]},
    ]);
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('input.search-input');
    input.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'ArrowDown', bubbles: true}),
    );
    expect(component.activeSuggestionIndex()).toBe(0);
  });

  it('should select suggestion on Enter when suggestions exist', () => {
    const suggestion: SearchBoxSuggestion = {
      label: 'Filter',
      mainText: [{text: 'Status is IDLE', emphasized: false}],
      rawItem: {openPicker: {key: 'status'}},
    };
    mockStore.showSuggestions.set(true);
    mockStore.suggestions.set([suggestion]);
    component.activeSuggestionIndex.set(0);
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('input.search-input');
    input.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'Enter', bubbles: true}),
    );
    fixture.detectChanges();

    expect(mockStore.selectSuggestion).toHaveBeenCalledWith(suggestion);
  });

  it('should execute search on Enter when no suggestions exist and query is non-empty', () => {
    mockStore.showSuggestions.set(false);
    mockStore.suggestions.set([]);
    mockStore.searchQuery.set('test-device');
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('input.search-input');
    input.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'Enter', bubbles: true}),
    );
    fixture.detectChanges();

    expect(mockStore.executeSearch).toHaveBeenCalled();
  });

  it('should select first suggestion on Enter when suggestions exist and no item is explicitly highlighted', () => {
    const suggestion: SearchBoxSuggestion = {
      label: 'Filter',
      mainText: [{text: 'Model', emphasized: false}],
    };
    mockStore.showSuggestions.set(true);
    mockStore.suggestions.set([suggestion]);
    component.activeSuggestionIndex.set(-1);
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('input.search-input');
    input.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'Enter', bubbles: true}),
    );
    fixture.detectChanges();

    expect(mockStore.selectSuggestion).toHaveBeenCalledWith(suggestion);
  });

  it('should activate chip and open picker on Enter and Space keydown', () => {
    const chip: FilterChip = {
      key: 'status',
      pillKey: 'status',
      pillCondition: 'IDLE',
      isGroupBy: false,
    };
    mockStore.activeChips.set([chip]);
    fixture.detectChanges();

    const chipEl = fixture.nativeElement.querySelector(
      '.active-chips-container .search-chip',
    );
    expect(chipEl.getAttribute('role')).toBe('button');
    expect(chipEl.getAttribute('tabindex')).toBe('0');

    chipEl.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'Enter', bubbles: true}),
    );
    fixture.detectChanges();
    expect(mockStore.openQuickFilter).toHaveBeenCalledWith(
      'status',
      'status',
      undefined,
    );

    mockStore.openQuickFilter.calls.reset();
    chipEl.dispatchEvent(
      new KeyboardEvent('keydown', {key: ' ', bubbles: true}),
    );
    fixture.detectChanges();
    expect(mockStore.openQuickFilter).toHaveBeenCalledWith(
      'status',
      'status',
      undefined,
    );
  });

  it('should preserve pendingFilter and open suggestions on input click', () => {
    mockStore.pendingFilter.set({key: 'status', displayName: 'Status'});
    mockStore.showValuePicker.set(true);
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector(
      'input.search-input',
    ) as HTMLInputElement;
    input.click();
    fixture.detectChanges();

    expect(mockStore.closeValuePicker).not.toHaveBeenCalled();
    expect(mockStore.showSuggestions()).toBeTrue();
  });

  it('should close suggestions or value picker on Escape key', () => {
    mockStore.showSuggestions.set(true);
    component.activeSuggestionIndex.set(1);
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('input.search-input');
    input.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'Escape', bubbles: true}),
    );
    fixture.detectChanges();

    expect(mockStore.showSuggestions()).toBeFalse();
    expect(component.activeSuggestionIndex()).toBe(-1);

    // When suggestions already closed, Escape closes value picker
    mockStore.showSuggestions.set(false);
    mockStore.showValuePicker.set(true);
    fixture.detectChanges();

    input.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'Escape', bubbles: true}),
    );
    fixture.detectChanges();

    expect(mockStore.closeValuePicker).toHaveBeenCalled();
  });

  it('should ignore non-navigational keys and handle edge cases in onKeyDown', () => {
    const input = fixture.nativeElement.querySelector('input.search-input');

    // 1. Non-navigational key (e.g. 'Tab', 'a') does nothing
    input.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'Tab', bubbles: true}),
    );
    expect(component.activeSuggestionIndex()).toBe(-1);

    // 2. ArrowDown / ArrowUp when suggestions popover is closed does nothing
    mockStore.showSuggestions.set(false);
    mockStore.suggestions.set([
      {label: 'Filter 1', mainText: [{text: '1', emphasized: false}]},
    ]);
    fixture.detectChanges();

    input.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'ArrowDown', bubbles: true}),
    );
    expect(component.activeSuggestionIndex()).toBe(-1);

    input.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'ArrowUp', bubbles: true}),
    );
    expect(component.activeSuggestionIndex()).toBe(-1);

    // 3. ArrowDown / ArrowUp when suggestions list is empty does nothing
    mockStore.showSuggestions.set(true);
    mockStore.suggestions.set([]);
    fixture.detectChanges();

    input.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'ArrowDown', bubbles: true}),
    );
    expect(component.activeSuggestionIndex()).toBe(-1);

    // 4. Enter when suggestions is empty and searchQuery is empty does nothing
    mockStore.searchQuery.set('');
    mockStore.executeSearch.calls.reset();
    input.dispatchEvent(
      new KeyboardEvent('keydown', {key: 'Enter', bubbles: true}),
    );
    expect(mockStore.executeSearch).not.toHaveBeenCalled();
  });
});
