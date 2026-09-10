import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialog} from '@angular/material/dialog';
import {By} from '@angular/platform-browser';
import {provideNoopAnimations} from '@angular/platform-browser/animations';

import {Column, Row} from '../../../../../core/models/search';
import {FleetSearchStore} from '../../../services/fleet_search_store';
import {FleetSearchResultsComponent} from './fleet_search_results';

class MockFleetSearchStore {
  readonly groupByKeys = signal<string[]>([]);
  readonly fleet = signal<'internal' | 'external'>('internal');
  readonly entity = signal<string>('devices');
  readonly isLoading = signal<boolean>(false);
  readonly selectedItems = signal<Set<string>>(new Set());
  readonly groupedResults = signal<{
    totalItems?: number;
    totalGroups?: number;
  } | null>(null);
  readonly groupKeysText = signal<string>('');
  readonly groupSortActiveLabel = signal<string>('Count (High to Low)');
  readonly groupSortOptions = signal<Array<{label: string; value: string}>>([]);
  readonly groupSort = signal<string>('count_desc');
  readonly setGroupSort = jasmine.createSpy('setGroupSort');
  readonly openGroupIds = signal<Set<string>>(new Set());
  readonly expandedGroupPages = signal<Map<string, unknown>>(new Map());
  readonly displayColumns = signal<Column[]>([]);
  readonly groups = signal<unknown[]>([]);
  readonly toggleGroup = jasmine.createSpy('toggleGroup');
  readonly toggleSelectRows = jasmine.createSpy('toggleSelectRows');
  readonly toggleSelectRow = jasmine.createSpy('toggleSelectRow');
  readonly onLoadGroupRows = jasmine.createSpy('onLoadGroupRows');
  readonly hasActiveFilters = signal<boolean>(false);
  readonly resetSearchState = jasmine.createSpy('resetSearchState');
  readonly rangeText = signal<string>('1–10 of 20');
  readonly hasPrevPage = signal<boolean>(false);
  readonly hasNextPage = signal<boolean>(true);
  readonly prevPage = jasmine.createSpy('prevPage');
  readonly nextPage = jasmine.createSpy('nextPage');
  readonly selectedCount = signal<number>(0);
  readonly effectiveTotalCount = signal<number>(0);
  readonly selectAllMatching = signal<boolean>(false);
  readonly density = signal<'compact' | 'normal' | 'comfortable'>('normal');
  readonly executeSearch = jasmine.createSpy('executeSearch');
  readonly rows = signal<Row[]>([]);
  readonly showSelectAllMatchingBanner = signal<boolean>(false);
  readonly excludedCount = signal<number>(0);
  readonly clearSelection = jasmine.createSpy('clearSelection');
  readonly selectAllMatchingRecords = jasmine.createSpy(
    'selectAllMatchingRecords',
  );
  readonly isAllSelected = signal<boolean>(false);
  readonly isSomeSelected = signal<boolean>(false);
  readonly toggleSelectAll = jasmine.createSpy('toggleSelectAll');
  readonly isRowSelected = jasmine
    .createSpy('isRowSelected')
    .and.returnValue(false);
  readonly sortColumn = signal<string>('');
  readonly sortAsc = signal<boolean>(true);
  readonly toggleSort = jasmine.createSpy('toggleSort');
  readonly pageSize = signal<number>(50);
  readonly setPageSize = jasmine.createSpy('setPageSize');
  readonly visibleColumnDescriptors = signal<unknown[]>([]);
  readonly searchConfig = signal<unknown>(null);
  readonly effectiveFilters = signal<unknown[]>([]);
  readonly resetVisibleColumns = jasmine.createSpy('resetVisibleColumns');
  readonly setVisibleColumns = jasmine.createSpy('setVisibleColumns');
}

describe('FleetSearchResultsComponent', () => {
  let component: FleetSearchResultsComponent;
  let fixture: ComponentFixture<FleetSearchResultsComponent>;
  let mockStore: MockFleetSearchStore;
  let mockDialog: jasmine.SpyObj<MatDialog>;

  beforeEach(async () => {
    mockStore = new MockFleetSearchStore();
    mockDialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      imports: [FleetSearchResultsComponent],
      providers: [
        provideNoopAnimations(),
        {provide: FleetSearchStore, useValue: mockStore},
        {provide: MatDialog, useValue: mockDialog},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(FleetSearchResultsComponent);
    component = fixture.componentInstance;
  });

  describe('Flat Table View Modes', () => {
    it('computes empty_all when rows are empty and no active filters', () => {
      mockStore.rows.set([]);
      mockStore.isLoading.set(false);
      mockStore.hasActiveFilters.set(false);
      fixture.detectChanges();

      expect(component.flatTableViewMode()).toBe('empty_all');
      const emptyEl = fixture.debugElement.query(By.css('.rt-empty-state'));
      expect(emptyEl).toBeTruthy();
      expect(emptyEl.nativeElement.textContent).toContain('No devices found');
    });

    it('computes empty_filtered when rows are empty with active filters', () => {
      mockStore.rows.set([]);
      mockStore.isLoading.set(false);
      mockStore.hasActiveFilters.set(true);
      fixture.detectChanges();

      expect(component.flatTableViewMode()).toBe('empty_filtered');
      const emptyEl = fixture.debugElement.query(By.css('.rt-empty-state'));
      expect(emptyEl).toBeTruthy();
      expect(emptyEl.nativeElement.textContent).toContain(
        'No devices match your filters',
      );

      const clearBtn = fixture.debugElement.query(By.css('.rt-btn-outlined'));
      clearBtn.nativeElement.click();
      expect(mockStore.resetSearchState).toHaveBeenCalled();
    });

    it('computes table mode when rows are present', () => {
      mockStore.displayColumns.set([{key: 'id', displayName: 'Device ID'}]);
      mockStore.rows.set([{id: 'dev-1', cells: [{text: {value: 'dev-1'}}]}]);
      fixture.detectChanges();

      expect(component.flatTableViewMode()).toBe('table');
      expect(component.columnsToDisplay()).toEqual(['checkbox', 'id']);
      const tableEl = fixture.debugElement.query(By.css('table.results-table'));
      expect(tableEl).toBeTruthy();
    });
  });

  describe('Grouped View Modes', () => {
    beforeEach(() => {
      mockStore.groupByKeys.set(['host']);
    });

    it('computes groups mode when group list is non-empty', () => {
      mockStore.groups.set([{groupId: 'group-1', title: 'Host A'}]);
      fixture.detectChanges();

      expect(component.groupedViewMode()).toBe('groups');
      const cardEl = fixture.debugElement.query(
        By.css('app-fleet-group-card'),
      );
      expect(cardEl).toBeTruthy();
    });

    it('computes empty_all mode when groups are empty without filters', () => {
      mockStore.groups.set([]);
      mockStore.isLoading.set(false);
      mockStore.hasActiveFilters.set(false);
      fixture.detectChanges();

      expect(component.groupedViewMode()).toBe('empty_all');
      const emptyEl = fixture.debugElement.query(By.css('.rt-empty-state'));
      expect(emptyEl.nativeElement.textContent).toContain('No groups found');
    });

    it('computes empty_filtered mode when groups are empty with active filters', () => {
      mockStore.groups.set([]);
      mockStore.isLoading.set(false);
      mockStore.hasActiveFilters.set(true);
      fixture.detectChanges();

      expect(component.groupedViewMode()).toBe('empty_filtered');
      const emptyEl = fixture.debugElement.query(By.css('.rt-empty-state'));
      expect(emptyEl.nativeElement.textContent).toContain(
        'No devices match your filters',
      );
    });

    it('computes loading mode when groups are empty and loading', () => {
      mockStore.groups.set([]);
      mockStore.isLoading.set(true);
      fixture.detectChanges();

      expect(component.groupedViewMode()).toBe('loading');
      const emptyEl = fixture.debugElement.query(By.css('.rt-empty-state'));
      expect(emptyEl).toBeNull();
    });
  });

  describe('Select-All Banner and Toolbar Selections', () => {
    it('computes selectedBannerMode correctly', () => {
      mockStore.showSelectAllMatchingBanner.set(false);
      expect(component.selectedBannerMode()).toBe('hidden');

      mockStore.showSelectAllMatchingBanner.set(true);
      mockStore.selectAllMatching.set(false);
      expect(component.selectedBannerMode()).toBe('page_only');

      mockStore.selectAllMatching.set(true);
      expect(component.selectedBannerMode()).toBe('all_matching');
    });

    it('computes flatToolbarSelectedText correctly', () => {
      mockStore.selectedCount.set(5);
      mockStore.fleet.set('internal');
      mockStore.selectAllMatching.set(false);
      expect(component.flatToolbarSelectedText()).toBe(
        '5 selected · Google Internal',
      );

      mockStore.selectAllMatching.set(true);
      expect(component.flatToolbarSelectedText()).toBe(
        'All 5 matching devices selected · Google Internal',
      );
    });

    it('computes batch action visibility correctly', () => {
      mockStore.entity.set('devices');
      mockStore.selectAllMatching.set(false);
      expect(component.canShowBatchActions()).toBeTrue();
      expect(component.canShowDeviceBatchActions()).toBeTrue();

      mockStore.entity.set('hosts');
      expect(component.canShowBatchActions()).toBeTrue();
      expect(component.canShowDeviceBatchActions()).toBeFalse();

      mockStore.selectAllMatching.set(true);
      expect(component.canShowBatchActions()).toBeFalse();
      expect(component.canShowDeviceBatchActions()).toBeFalse();
    });
  });
});
