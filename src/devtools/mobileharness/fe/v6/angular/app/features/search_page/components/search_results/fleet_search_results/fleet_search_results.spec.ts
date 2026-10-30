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
  readonly columnDescriptions = signal<ReadonlyMap<string, string>>(new Map());
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
  readonly effectiveTotalCount = signal<number>(0);
  readonly density = signal<'compact' | 'normal' | 'comfortable'>('normal');
  readonly executeSearch = jasmine.createSpy('executeSearch');
  readonly rows = signal<Row[]>([]);
  readonly clearSelection = jasmine.createSpy('clearSelection');
  readonly isAllSelected = signal<boolean>(false);
  readonly isSomeSelected = signal<boolean>(false);
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

    it('renders a header info icon only for columns with a description', () => {
      mockStore.displayColumns.set([
        {key: 'id', displayName: 'Device ID'},
        {key: 'device_field::version', displayName: 'Version'},
      ]);
      mockStore.columnDescriptions.set(
        new Map([['device_field::version', 'The device OS version.']]),
      );
      mockStore.rows.set([
        {id: 'dev-1', cells: [{text: {value: 'dev-1'}}, {text: {value: '16'}}]},
      ]);
      fixture.detectChanges();

      const infoIcons = fixture.debugElement.queryAll(
        By.css('th .header-info'),
      );
      expect(infoIcons.length).toBe(1);
      expect(infoIcons[0].nativeElement.getAttribute('aria-label')).toBe(
        'The device OS version.',
      );

      infoIcons[0].nativeElement.click();
      expect(mockStore.toggleSort).not.toHaveBeenCalled();
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
      const cardEl = fixture.debugElement.query(By.css('app-fleet-group-card'));
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

  describe('Toolbar Selections', () => {
    it('shows Configure Wi-Fi button only for devices', () => {
      mockStore.selectedItems.set(new Set(['dev-1', 'dev-2']));
      mockStore.entity.set('devices');
      fixture.detectChanges();

      const buttons = fixture.debugElement.queryAll(
        By.css('.rt-toolbar button'),
      );
      const buttonTexts = buttons.map((b) =>
        b.nativeElement.textContent.trim(),
      );
      expect(buttonTexts.some((t) => t.includes('Configure Wi-Fi'))).toBeTrue();

      mockStore.entity.set('hosts');
      fixture.detectChanges();
      const hostButtons = fixture.debugElement.queryAll(
        By.css('.rt-toolbar button'),
      );
      const hostButtonTexts = hostButtons.map((b) =>
        b.nativeElement.textContent.trim(),
      );
      expect(
        hostButtonTexts.some((t) => t.includes('Configure Wi-Fi')),
      ).toBeFalse();
    });
  });

  describe('Grouped Mode Layout', () => {
    it('renders grouped toolbar and results-area container when groupByKeys are set', () => {
      mockStore.groupByKeys.set(['host']);
      mockStore.groupKeysText.set('host');
      mockStore.groups.set([{groupId: 'group-1', title: 'Host A'}]);
      fixture.detectChanges();

      const areaEl = fixture.nativeElement.querySelector('.results-area');
      const tb = fixture.nativeElement.querySelector('.rt-toolbar');

      expect(areaEl).toBeTruthy();
      expect(areaEl.classList.contains('grouped-mode')).toBeTrue();
      expect(tb).toBeTruthy();
      expect(tb.textContent).toContain('by host');
    });
  });
});
