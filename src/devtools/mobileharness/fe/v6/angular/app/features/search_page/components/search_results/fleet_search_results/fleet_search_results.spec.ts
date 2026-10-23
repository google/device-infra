import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialog, MatDialogRef} from '@angular/material/dialog';
import {By} from '@angular/platform-browser';
import {provideNoopAnimations} from '@angular/platform-browser/animations';

import {of} from 'rxjs';
import {Column, Row} from '../../../../../core/models/search';
import {Fleet} from '../../../../../core/models/search_fleet';
import {EnvUniverseService} from '../../../../../core/services/env_universe_service';
import {BulkConfigDialogResult, FleetScope} from '../../../models';
import {FleetSearchStore} from '../../../services/fleet_search_store';
import {BulkConfigWifiDialog} from '../../bulk_config/wifi_dialog/bulk_config_wifi_dialog';
import {FleetSearchResultsComponent} from './fleet_search_results';

class MockFleetSearchStore {
  readonly groupByKeys = signal<string[]>([]);
  readonly fleet = signal<FleetScope>('internal');
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
  let mockEnvUniverseService: jasmine.SpyObj<EnvUniverseService>;

  beforeEach(async () => {
    mockStore = new MockFleetSearchStore();
    mockDialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    mockEnvUniverseService = jasmine.createSpyObj<EnvUniverseService>(
      'EnvUniverseService',
      ['getUniverseString'],
    );
    mockEnvUniverseService.getUniverseString.and.returnValue('google_1p');

    await TestBed.configureTestingModule({
      imports: [FleetSearchResultsComponent],
      providers: [
        provideNoopAnimations(),
        {provide: FleetSearchStore, useValue: mockStore},
        {provide: MatDialog, useValue: mockDialog},
        {provide: EnvUniverseService, useValue: mockEnvUniverseService},
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
      mockStore.selectAllMatching.set(false);
      expect(component.canShowBatchActions()).toBeTrue();

      mockStore.selectAllMatching.set(true);
      expect(component.canShowBatchActions()).toBeFalse();
    });

    it('shows Configure Wi-Fi button only for devices when batch actions are enabled', () => {
      mockStore.selectedCount.set(2);
      mockStore.entity.set('devices');
      mockStore.selectAllMatching.set(false);
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

    it('opens BulkConfigWifiDialog with selected devices and clears selection on update', () => {
      mockStore.selectedItems.set(new Set(['dev-1', 'dev-2']));
      const openSpy = spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      ).and.returnValue({
        afterClosed: () => of({updated: true}),
      } as unknown as MatDialogRef<
        BulkConfigWifiDialog,
        BulkConfigDialogResult
      >);

      component.bulkConfig.openBulkConfigWifiDialog();

      expect(openSpy).toHaveBeenCalledWith(
        BulkConfigWifiDialog,
        jasmine.objectContaining({
          autoFocus: false,
          data: {
            deviceIds: ['dev-1', 'dev-2'],
            universe: 'google_1p',
            fleet: Fleet.FLEET_SELF,
          },
        }),
      );
      expect(mockStore.clearSelection).toHaveBeenCalled();
      expect(mockStore.executeSearch).toHaveBeenCalled();
    });

    it('does not open dialog when selectedItems is empty', () => {
      mockStore.selectedItems.set(new Set());
      const openSpy = spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      );

      component.bulkConfig.openBulkConfigWifiDialog();

      expect(openSpy).not.toHaveBeenCalled();
    });

    it('does not clear selection or re-execute search when dialog is closed without update', () => {
      mockStore.selectedItems.set(new Set(['dev-1']));
      spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      ).and.returnValue({
        afterClosed: () => of(undefined),
      } as unknown as MatDialogRef<
        BulkConfigWifiDialog,
        BulkConfigDialogResult
      >);

      component.bulkConfig.openBulkConfigWifiDialog();

      expect(mockStore.clearSelection).not.toHaveBeenCalled();
      expect(mockStore.executeSearch).not.toHaveBeenCalled();
    });

    it('does not clear selection or re-execute search when dialog returns updated: false', () => {
      mockStore.selectedItems.set(new Set(['dev-1']));
      spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      ).and.returnValue({
        afterClosed: () => of({updated: false}),
      } as unknown as MatDialogRef<
        BulkConfigWifiDialog,
        BulkConfigDialogResult
      >);

      component.bulkConfig.openBulkConfigWifiDialog();

      expect(mockStore.clearSelection).not.toHaveBeenCalled();
      expect(mockStore.executeSearch).not.toHaveBeenCalled();
    });
  });

  describe('Column Selector', () => {
    it('opens ColumnSelectorComponent and resets visible columns when result isReset is true', () => {
      const openSpy = spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      ).and.returnValue({
        afterClosed: () => of({isReset: true}),
      } as unknown as MatDialogRef<unknown>);

      component.openColumnSelector();

      expect(openSpy).toHaveBeenCalled();
      expect(mockStore.resetVisibleColumns).toHaveBeenCalled();
      expect(mockStore.setVisibleColumns).not.toHaveBeenCalled();
    });

    it('sets visible columns when result contains columns', () => {
      spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      ).and.returnValue({
        afterClosed: () => of({columns: ['status', 'type']}),
      } as unknown as MatDialogRef<unknown>);

      component.openColumnSelector();

      expect(mockStore.setVisibleColumns).toHaveBeenCalledWith([
        'status',
        'type',
      ]);
    });

    it('does nothing when column selector dialog is dismissed without result', () => {
      spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      ).and.returnValue({
        afterClosed: () => of(undefined),
      } as unknown as MatDialogRef<unknown>);

      component.openColumnSelector();

      expect(mockStore.resetVisibleColumns).not.toHaveBeenCalled();
      expect(mockStore.setVisibleColumns).not.toHaveBeenCalled();
    });

    it('does nothing when column selector returns empty columns array', () => {
      spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      ).and.returnValue({
        afterClosed: () => of({isReset: false, columns: []}),
      } as unknown as MatDialogRef<unknown>);

      component.openColumnSelector();

      expect(mockStore.resetVisibleColumns).not.toHaveBeenCalled();
      expect(mockStore.setVisibleColumns).not.toHaveBeenCalled();
    });

    it('clicks Columns button in toolbar to trigger openColumnSelector', () => {
      mockStore.selectedCount.set(0);
      fixture.detectChanges();

      const openSpy = spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      ).and.returnValue({
        afterClosed: () => of(undefined),
      } as unknown as MatDialogRef<unknown>);

      const colBtn = fixture.debugElement.query(
        By.css('button[title="Configure table columns"]'),
      );
      expect(colBtn).toBeTruthy();
      colBtn.nativeElement.click();

      expect(openSpy).toHaveBeenCalled();
    });

    it('clicks Configure Wi-Fi button in selection toolbar and opens dialog', () => {
      mockStore.selectedCount.set(2);
      mockStore.selectedItems.set(new Set(['dev-1', 'dev-2']));
      mockStore.entity.set('devices');
      mockStore.selectAllMatching.set(false);
      fixture.detectChanges();

      const openSpy = spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      ).and.returnValue({
        afterClosed: () => of({updated: true}),
      } as unknown as MatDialogRef<
        BulkConfigWifiDialog,
        BulkConfigDialogResult
      >);

      const buttons = fixture.debugElement.queryAll(
        By.css('.rt-toolbar button'),
      );
      const wifiBtn = buttons.find((b) =>
        b.nativeElement.textContent.includes('Configure Wi-Fi'),
      );
      expect(wifiBtn).toBeTruthy();
      wifiBtn!.nativeElement.click();

      expect(openSpy).toHaveBeenCalled();
      expect(mockStore.clearSelection).toHaveBeenCalled();
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
