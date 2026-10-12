import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideNoopAnimations} from '@angular/platform-browser/animations';

import {PromotedFilterKeyItem, PromotedGroupByKeyItem} from '../../../models';
import {SearchPageStore} from '../../../services/search_page_store';
import {FilterPresets} from './filter_presets';

class FakeSearchPageStore {
  readonly isLandingState = signal<boolean>(true);
  readonly isPromotedKeysLoading = signal<boolean>(false);
  readonly promotedFilterKeys = signal<PromotedFilterKeyItem[]>([]);
  readonly promotedGroupByKeys = signal<PromotedGroupByKeyItem[]>([]);

  openQuickFilter = jasmine.createSpy('openQuickFilter');
  openQuickGroupBy = jasmine.createSpy('openQuickGroupBy');
}

describe('FilterPresets', () => {
  let fixture: ComponentFixture<FilterPresets>;
  let component: FilterPresets;
  let mockStore: FakeSearchPageStore;

  const sampleFilters: PromotedFilterKeyItem[] = [
    {
      key: 'status',
      displayName: 'Device Status',
      metadata: {keyDisplayName: 'Device Status'},
    },
    {
      key: 'model',
      displayName: 'Model',
      metadata: {keyDisplayName: 'Model'},
    },
  ];

  const sampleGroups: PromotedGroupByKeyItem[] = [
    {
      key: 'driver',
      displayName: 'Driver',
    },
    {
      key: 'host_name',
      displayName: 'Host Name',
      disabled: true,
      disabledReason: 'Already grouped by Host Name',
    },
  ];

  beforeEach(async () => {
    mockStore = new FakeSearchPageStore();

    await TestBed.configureTestingModule({
      imports: [FilterPresets],
      providers: [
        provideNoopAnimations(),
        {provide: SearchPageStore, useValue: mockStore},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(FilterPresets);
    component = fixture.componentInstance;
  });

  it('renders loading shimmer placeholder when loading and no keys present', () => {
    mockStore.isPromotedKeysLoading.set(true);
    fixture.detectChanges();

    const placeholder = fixture.nativeElement.querySelector(
      '.preset-filters-container.loading-placeholder',
    );
    expect(placeholder).not.toBeNull();
    const shimmerLine = fixture.nativeElement.querySelector(
      '.preset-shimmer-line',
    );
    expect(shimmerLine).not.toBeNull();
  });

  it('renders nothing when not loading and no promoted keys', () => {
    mockStore.isPromotedKeysLoading.set(false);
    mockStore.promotedFilterKeys.set([]);
    mockStore.promotedGroupByKeys.set([]);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('.preset-filters-container'),
    ).toBeNull();
    expect(
      fixture.nativeElement.querySelector('.preset-collapsed-bar'),
    ).toBeNull();
  });

  it('renders quick filters and quick group-bys', () => {
    mockStore.promotedFilterKeys.set(sampleFilters);
    mockStore.promotedGroupByKeys.set(sampleGroups);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('.preset-row');
    expect(rows.length).toBe(2);

    // Filter row
    expect(rows[0].textContent).toContain('Quick Filter:');
    expect(rows[0].textContent).toContain('Device Status');
    expect(rows[0].textContent).toContain('Model');

    // Group-by row
    expect(rows[1].textContent).toContain('Quick Group-by:');
    expect(rows[1].textContent).toContain('Driver');
    expect(rows[1].textContent).toContain('Host Name');
  });

  it('triggers store.openQuickFilter on quick filter click', () => {
    mockStore.promotedFilterKeys.set(sampleFilters);
    fixture.detectChanges();

    const filterBtn = fixture.nativeElement.querySelectorAll(
      '.preset-link',
    )[0] as HTMLButtonElement;
    filterBtn.click();
    fixture.detectChanges();

    expect(mockStore.openQuickFilter).toHaveBeenCalledWith(
      'status',
      'Device Status',
      {keyDisplayName: 'Device Status'},
    );
  });

  it('triggers store.openQuickGroupBy on enabled group-by click', () => {
    mockStore.promotedGroupByKeys.set(sampleGroups);
    fixture.detectChanges();

    const groupBtns = fixture.nativeElement.querySelectorAll(
      '.preset-link',
    ) as NodeListOf<HTMLButtonElement>;
    groupBtns[0].click(); // 'Driver'
    fixture.detectChanges();

    expect(mockStore.openQuickGroupBy).toHaveBeenCalledWith('driver', 'Driver');
  });

  it('disables group-by button and does not trigger openQuickGroupBy when disabled', () => {
    mockStore.promotedGroupByKeys.set(sampleGroups);
    fixture.detectChanges();

    const groupBtns = fixture.nativeElement.querySelectorAll(
      '.preset-link',
    ) as NodeListOf<HTMLButtonElement>;
    const disabledBtn = groupBtns[1]; // 'Host Name'

    expect(disabledBtn.disabled).toBeTrue();
    expect(disabledBtn.title).toBe('Already grouped by Host Name');

    disabledBtn.click();
    fixture.detectChanges();

    expect(mockStore.openQuickGroupBy).not.toHaveBeenCalled();
  });

  it('hides the Hide button in landing state', () => {
    mockStore.isLandingState.set(true);
    mockStore.promotedFilterKeys.set(sampleFilters);
    fixture.detectChanges();

    const hideBtn = fixture.nativeElement.querySelector('.preset-hide-btn');
    expect(hideBtn).toBeNull();
  });

  it('shows Hide button in results mode and collapses when clicked', () => {
    mockStore.isLandingState.set(false);
    mockStore.promotedFilterKeys.set(sampleFilters);
    fixture.detectChanges();

    const hideBtn = fixture.nativeElement.querySelector(
      '.preset-hide-btn',
    ) as HTMLButtonElement;
    expect(hideBtn).not.toBeNull();

    hideBtn.click();
    fixture.detectChanges();

    expect(component.isCollapsed()).toBeTrue();
    expect(
      fixture.nativeElement.querySelector('.preset-filters-container'),
    ).toBeNull();
    const showPill = fixture.nativeElement.querySelector(
      '.preset-show-pill',
    ) as HTMLButtonElement;
    expect(showPill).not.toBeNull();
    expect(showPill.textContent).toContain('Suggested filters');

    // Click to re-expand
    showPill.click();
    fixture.detectChanges();

    expect(component.isCollapsed()).toBeFalse();
    expect(
      fixture.nativeElement.querySelector('.preset-filters-container'),
    ).not.toBeNull();
  });

  it('automatically uncollapses when transitioning back to landing state', () => {
    mockStore.isLandingState.set(false);
    mockStore.promotedFilterKeys.set(sampleFilters);
    fixture.detectChanges();

    // Collapse in results mode
    component.toggleCollapsed(true);
    fixture.detectChanges();
    expect(component.isCollapsed()).toBeTrue();

    // Return to landing page
    mockStore.isLandingState.set(true);
    fixture.detectChanges();

    expect(component.isCollapsed()).toBeFalse();
    expect(
      fixture.nativeElement.querySelector('.preset-filters-container'),
    ).not.toBeNull();
  });
});
