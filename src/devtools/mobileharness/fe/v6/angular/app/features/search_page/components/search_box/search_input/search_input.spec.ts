import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideNoopAnimations} from '@angular/platform-browser/animations';

import {FilterChip, SearchBoxSuggestion} from '../../../models';
import {SearchPageStore} from '../../../services/search_page_store';
import {SearchInput} from './search_input';

class FakeSearchPageStore {
  readonly showScopeSwitcher = signal<boolean>(true);
  readonly fleet = signal<'internal' | 'ats'>('internal');
  readonly activeChips = signal<FilterChip[]>([]);
  readonly searchQuery = signal<string>('');
  readonly searchPlaceholder = signal<string>('Search devices...');
  readonly showSearchClear = signal<boolean>(false);
  readonly showSuggestions = signal<boolean>(false);
  readonly suggestions = signal<SearchBoxSuggestion[]>([]);
  readonly showValuePicker = signal<boolean>(false);

  setFleet = jasmine.createSpy('setFleet');
  selectSuggestion = jasmine.createSpy('selectSuggestion');
  resetSearchState = jasmine.createSpy('resetSearchState');
  executeSearch = jasmine.createSpy('executeSearch');
  removeFilterChip = jasmine.createSpy('removeFilterChip');
  openValuePicker = jasmine.createSpy('openValuePicker');
  closeValuePicker = jasmine.createSpy('closeValuePicker');
  isChipPickerActive = jasmine
    .createSpy('isChipPickerActive')
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

  it('should render active chips with pillKey and pillCondition', () => {
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
    fixture.detectChanges();

    const chips = fixture.nativeElement.querySelectorAll('.search-chip');
    expect(chips.length).toBe(3);

    // Chip 1: normal
    expect(chips[0].querySelector('.chip-label').textContent.trim()).toBe('status');
    expect(chips[0].querySelector('.chip-value').textContent.trim()).toBe('(IDLE)');

    // Chip 2: negated
    expect(chips[1].classList).toContain('exclude');
    expect(chips[1].querySelector('.chip-label').textContent.trim()).toBe('model');
    expect(chips[1].querySelector('.chip-value').textContent.trim()).toBe('(!Pixel 8)');

    // Chip 3: group by
    expect(chips[2].classList).toContain('group-by-chip');
    expect(chips[2].querySelector('.chip-label').textContent.trim()).toBe('group by');
    expect(chips[2].querySelector('.chip-value').textContent.trim()).toBe('driver');
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

  it('should trigger openPickerForChip when chip is clicked', () => {
    const chip: FilterChip = {
      key: 'status',
      pillKey: 'status',
      pillCondition: 'IDLE',
      metadata: {keyDisplayName: 'Device Status'},
    };
    mockStore.activeChips.set([chip]);
    fixture.detectChanges();

    const chipEl = fixture.nativeElement.querySelector('.search-chip');
    chipEl.click();

    expect(mockStore.openValuePicker).toHaveBeenCalledWith(
      'status',
      chipEl,
      'Device Status',
      chip.metadata,
    );
  });

  it('should update searchQuery and showSuggestions on input', () => {
    const input = fixture.nativeElement.querySelector('input.search-input');
    input.value = 'pixel';
    input.dispatchEvent(new Event('input'));

    expect(mockStore.searchQuery()).toBe('pixel');
    expect(mockStore.showSuggestions()).toBeTrue();
  });

  it('should trigger resetSearchState when clear button is clicked', () => {
    mockStore.showSearchClear.set(true);
    fixture.detectChanges();

    const clearBtn = fixture.nativeElement.querySelector('.search-clear-btn');
    expect(clearBtn).toBeTruthy();
    clearBtn.click();

    expect(mockStore.resetSearchState).toHaveBeenCalled();
  });

  it('should trigger executeSearch when refresh button is clicked', () => {
    const refreshBtn = fixture.nativeElement.querySelector('.query-refresh-btn');
    expect(refreshBtn).toBeTruthy();
    refreshBtn.click();

    expect(mockStore.executeSearch).toHaveBeenCalled();
  });
});
