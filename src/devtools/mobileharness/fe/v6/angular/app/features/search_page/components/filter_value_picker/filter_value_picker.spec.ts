import {OverlayContainer} from '@angular/cdk/overlay';
import {computed, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {
  INITIAL_VALUE_PICKER_STATE,
  PickerValueItem,
  ValuePickerConfig,
  ValuePickerState,
} from '../../models';
import {SearchPageStore} from '../../services/search_page_store';

import {FilterValuePicker} from './filter_value_picker';

class FakeSearchPageStore {
  readonly showValuePicker = signal<boolean>(true);
  readonly pickerAnchor = signal<HTMLElement | null>(
    document.createElement('div'),
  );
  readonly pickerConfig = signal<ValuePickerConfig | null>({
    key: 'status',
    title: 'Device Status',
    type: 'list',
    showSearchInput: true,
    showNegateToggle: true,
    showAdvancedMenu: true,
    showRowActions: true,
  });
  readonly pickerState = signal<ValuePickerState>(INITIAL_VALUE_PICKER_STATE);
  readonly pickerKey = computed(() => this.pickerConfig()?.key || '');
  readonly pickerTitle = computed(() => this.pickerConfig()?.title || '');
  readonly effectivePickerState = signal<ValuePickerState>({
    ...INITIAL_VALUE_PICKER_STATE,
    values: [
      {value: 'IDLE', displayLabel: 'Idle', filtered: 10, total: 100},
      {value: 'BUSY', displayLabel: 'Busy', filtered: 20, total: 200},
      {value: 'INIT', displayLabel: 'Initializing', filtered: 5, total: 50},
      {value: 'DYING', displayLabel: 'Dying', filtered: 0, total: 10},
    ],
    selectedValues: new Set(['IDLE']),
  });

  closeValuePicker = jasmine.createSpy('closeValuePicker');
  applyValuePicker = jasmine.createSpy('applyValuePicker');
}

describe('FilterValuePicker', () => {
  let fixture: ComponentFixture<FilterValuePicker>;
  let component: FilterValuePicker;
  let mockStore: FakeSearchPageStore;
  let overlayContainer: OverlayContainer;
  let overlayContainerElement: HTMLElement;

  beforeEach(async () => {
    mockStore = new FakeSearchPageStore();

    await TestBed.configureTestingModule({
      imports: [FilterValuePicker, NoopAnimationsModule],
      providers: [{provide: SearchPageStore, useValue: mockStore}],
    }).compileComponents();

    overlayContainer = TestBed.inject(OverlayContainer);
    overlayContainerElement = overlayContainer.getContainerElement();

    fixture = TestBed.createComponent(FilterValuePicker);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component and open the overlay', () => {
    expect(component).toBeTruthy();
    const pickerEl = overlayContainerElement.querySelector('.value-picker');
    expect(pickerEl).not.toBeNull();
  });

  it('should display title and polarity toggle', () => {
    const titleEl = overlayContainerElement.querySelector('.vp-title');
    expect(titleEl?.textContent?.trim()).toBe('Device Status');

    const polarityBtn = overlayContainerElement.querySelector(
      '.vp-polarity-btn',
    ) as HTMLButtonElement;
    expect(polarityBtn).not.toBeNull();
    expect(polarityBtn.textContent).toContain('is');
  });

  it('should toggle polarity menu and negate selection', () => {
    const polarityBtn = overlayContainerElement.querySelector(
      '.vp-polarity-btn',
    ) as HTMLButtonElement;
    polarityBtn.click();
    fixture.detectChanges();

    const menuItems = overlayContainerElement.querySelectorAll('.vp-pm-item');
    expect(menuItems.length).toBe(2);

    // Select 'is not'
    (menuItems[1] as HTMLElement).click();
    fixture.detectChanges();

    expect(component.isNegated()).toBeTrue();
  });

  it('should toggle item selection when clicked', () => {
    expect(component.selectedSet().has('IDLE')).toBeTrue();
    expect(component.selectedSet().has('BUSY')).toBeFalse();

    component.toggleValue({value: 'BUSY', displayLabel: 'Busy'});
    expect(component.selectedSet().has('BUSY')).toBeTrue();

    component.toggleValue({value: 'IDLE', displayLabel: 'Idle'});
    expect(component.selectedSet().has('IDLE')).toBeFalse();
  });

  it('should handle selectOnlyValue', () => {
    component.selectedSet.set(new Set(['IDLE', 'BUSY', 'INIT']));
    component.selectOnlyValue('INIT');
    expect(Array.from(component.selectedSet())).toEqual(['INIT']);
  });

  it('should handle clearAll', () => {
    component.selectedSet.set(new Set(['IDLE', 'BUSY']));
    component.clearAll();
    expect(component.selectedSet().size).toBe(0);
  });

  it('should filter candidate items by search query', () => {
    component.searchQuery.set('busy');
    fixture.detectChanges();

    const displayed = component.displayList();
    expect(displayed.length).toBe(1);
    expect(displayed[0].value).toBe('BUSY');
  });

  it('should toggle sort column and direction', () => {
    component.toggleSort('value');
    expect(component.sortBy()).toBe('value');
    expect(component.sortAsc()).toBeTrue();

    component.toggleSort('value');
    expect(component.sortAsc()).toBeFalse();
  });

  it('should pin selected items when list exceeds threshold', () => {
    const largeList: PickerValueItem[] = Array.from(
      {length: 25},
      (_, idx) => ({
        value: `val_${idx}`,
        displayLabel: `Value ${idx.toString().padStart(2, '0')}`,
        filtered: 10,
        total: 50,
      }),
    );
    mockStore.effectivePickerState.set({
      ...INITIAL_VALUE_PICKER_STATE,
      values: largeList,
      selectedValues: new Set(['val_1', 'val_5', 'val_10', 'val_15']),
    });
    fixture.detectChanges();

    expect(component.pinnedValues().length).toBe(4);
    expect(component.viewportHeight()).toBe(220);
  });

  it('should adapt viewportHeight when no pinned values exist', () => {
    mockStore.effectivePickerState.set({
      ...INITIAL_VALUE_PICKER_STATE,
      values: [
        {value: 'A', displayLabel: 'A'},
        {value: 'B', displayLabel: 'B'},
      ],
      selectedValues: new Set<string>(),
    });
    fixture.detectChanges();

    expect(component.pinnedValues().length).toBe(0);
    expect(component.viewportHeight()).toBe(72);
  });

  it('should call store.applyValuePicker on Apply button click', () => {
    component.onApply();
    expect(mockStore.applyValuePicker).toHaveBeenCalled();
  });

  it('should call store.closeValuePicker on Cancel button click', () => {
    component.onCancel();
    expect(mockStore.closeValuePicker).toHaveBeenCalled();
  });

  it('should handle date range picker type', () => {
    mockStore.pickerConfig.set({
      key: 'created_time',
      title: 'Created Time',
      type: 'range',
    });
    mockStore.effectivePickerState.set({
      ...INITIAL_VALUE_PICKER_STATE,
      values: [],
      selectedValues: new Set(['2026-01-01T00:00', '2026-01-02T00:00']),
    });
    fixture.detectChanges();

    expect(component.rangeFrom()).toBe('2026-01-01T00:00');
    expect(component.rangeTo()).toBe('2026-01-02T00:00');
  });

  it('should handle namedPair picker type', () => {
    mockStore.pickerConfig.set({
      key: 'property',
      title: 'Property',
      type: 'namedPair',
      needsName: true,
    });
    mockStore.effectivePickerState.set({
      ...INITIAL_VALUE_PICKER_STATE,
      values: [],
      selectedValues: new Set(['custom_key', 'custom_value']),
    });
    fixture.detectChanges();

    expect(component.propName()).toBe('custom_key');
    expect(component.propVal()).toBe('custom_value');
  });

  it('should handle plain text picker type', () => {
    mockStore.pickerConfig.set({
      key: 'description',
      title: 'Description',
      type: 'text',
    });
    mockStore.effectivePickerState.set({
      ...INITIAL_VALUE_PICKER_STATE,
      values: [],
      selectedValues: new Set(['hello world']),
    });
    fixture.detectChanges();

    expect(component.textVal()).toBe('hello world');
  });
});
