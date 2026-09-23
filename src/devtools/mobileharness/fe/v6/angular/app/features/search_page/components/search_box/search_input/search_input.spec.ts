import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialog} from '@angular/material/dialog';
import {provideNoopAnimations} from '@angular/platform-browser/animations';

import {FilterChip, SearchBoxSuggestion} from '../../../models';
import {SearchPageStore} from '../../../services/search_page_store';
import {SearchInput} from './search_input';

class FakeSearchPageStore {
  readonly entity = signal<string>('devices');
  readonly isLandingState = signal<boolean>(false);
  readonly showScopeSwitcher = signal<boolean>(true);
  readonly fleet = signal<'internal' | 'ats'>('internal');
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
    metadata?: unknown;
  } | null>(null);
  readonly focusChipKey = signal<string | null>(null);
  readonly autoCollapseAfterApply = signal<boolean>(true);

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
        metadata?: unknown,
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
  let mockDialog: jasmine.SpyObj<MatDialog>;

  beforeEach(async () => {
    mockStore = new FakeSearchPageStore();
    mockDialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      imports: [SearchInput],
      providers: [
        provideNoopAnimations(),
        {provide: SearchPageStore, useValue: mockStore},
        {provide: MatDialog, useValue: mockDialog},
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
    expect(buttons[0].textContent.trim()).toBe('Internal');
    expect(buttons[1].textContent.trim()).toBe('ATS');

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
        mainText: [{text: 'model is ', emphasized: false}, {text: 'pixel 9', emphasized: true}],
        rawItem: {key: 'model'},
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
        mainText: [{text: 'status is ', emphasized: false}, {text: 'IDLE', emphasized: true}],
        rawItem: {key: 'status'},
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
});
