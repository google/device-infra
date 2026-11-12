import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialog, MatDialogRef} from '@angular/material/dialog';
import {By} from '@angular/platform-browser';
import {provideNoopAnimations} from '@angular/platform-browser/animations';

import {of, Subject, throwError} from 'rxjs';

import {
  ConfigurableDimension,
  DimensionList,
  GetConfigurableDimensionsResponse,
} from '../../../../../core/models/device_config_models';
import {Column, Row} from '../../../../../core/models/search';
import {
  CONFIG_SERVICE,
  ConfigService,
} from '../../../../../core/services/config/config_service';
import {ClipboardService} from '../../../../../shared/services/clipboard_service';
import {SnackBarService} from '../../../../../shared/services/snackbar_service';
import {BulkConfigDialogResult, FleetScope} from '../../../models';
import {FleetSearchStore} from '../../../services/fleet_search_store';
import {BulkConfigDimensionDialog} from '../../bulk_config/dimension_dialog/bulk_config_dimension_dialog';
import {BulkConfigWifiDialog} from '../../bulk_config/wifi_dialog/bulk_config_wifi_dialog';
import {FleetSearchResultsComponent} from './fleet_search_results';

function toSelectedMap(
  ids: readonly string[],
  hostPrefix = 'host-for-',
): Map<string, Row> {
  return new Map(
    ids.map((id) => [
      `${id}@@${hostPrefix}${id}`,
      {
        id,
        cells: [
          {
            link: {
              text: id,
              target: {device: {id, hostName: `${hostPrefix}${id}`}},
            },
          },
        ],
      },
    ]),
  );
}

class MockFleetSearchStore {
  readonly groupByKeys = signal<string[]>([]);
  readonly fleet = signal<FleetScope>('internal');
  isFleetSwitchable = true;
  readonly entity = signal<string>('devices');
  readonly isLoading = signal<boolean>(false);
  readonly selectedItems = signal<Map<string, Row>>(new Map());
  readonly groupedResults = signal<{
    totalItems?: number;
    totalGroups?: number;
    groupByKeys?: Column[];
  } | null>(null);
  readonly keyMetadataMap = signal<Map<string, {keyDisplayName?: string}>>(
    new Map(),
  );
  readonly activeChips = signal<unknown[]>([]);
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
  readonly toggleGroupRowSort = jasmine.createSpy('toggleGroupRowSort');
  readonly setGroupPageSize = jasmine.createSpy('setGroupPageSize');
  readonly onLoadGroupRows = jasmine.createSpy('onLoadGroupRows');
  readonly hasActiveFilters = signal<boolean>(false);
  readonly searchError = signal<string | null>(null);
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
  readonly selectionState = signal<{all: boolean; some: boolean}>({
    all: false,
    some: false,
  });
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
  let mockConfigService: jasmine.SpyObj<ConfigService>;
  let mockClipboardService: jasmine.SpyObj<ClipboardService>;
  let mockSnackBarService: jasmine.SpyObj<SnackBarService>;

  beforeEach(async () => {
    mockStore = new MockFleetSearchStore();
    mockDialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    mockConfigService = jasmine.createSpyObj<ConfigService>('ConfigService', [
      'getConfigurableDimensions',
    ]);
    mockConfigService.getConfigurableDimensions.and.returnValue(
      of({
        enabled: true,
        dimensions: [
          {
            dimensionName: 'recovery',
            displayName: 'recovery',
            dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
          },
        ],
        allowCustomDimensions: true,
      }),
    );
    mockClipboardService = jasmine.createSpyObj<ClipboardService>(
      'ClipboardService',
      ['copyToClipboard'],
    );
    mockClipboardService.copyToClipboard.and.returnValue(true);
    mockSnackBarService = jasmine.createSpyObj<SnackBarService>(
      'SnackBarService',
      ['showSuccess', 'showError'],
    );

    await TestBed.configureTestingModule({
      imports: [FleetSearchResultsComponent],
      providers: [
        provideNoopAnimations(),
        {provide: FleetSearchStore, useValue: mockStore},
        {provide: MatDialog, useValue: mockDialog},
        {provide: CONFIG_SERVICE, useValue: mockConfigService},
        {provide: ClipboardService, useValue: mockClipboardService},
        {provide: SnackBarService, useValue: mockSnackBarService},
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

    it('computes error mode when searchError is present and rows are empty, and triggers executeSearch on Retry', () => {
      mockStore.rows.set([]);
      mockStore.isLoading.set(false);
      mockStore.hasActiveFilters.set(true);
      mockStore.searchError.set('UNAVAILABLE: TJS backend unreachable');
      fixture.detectChanges();

      expect(component.flatTableViewMode()).toBe('error');
      const errorEl = fixture.debugElement.query(By.css('.rt-error-state'));
      expect(errorEl).toBeTruthy();
      expect(errorEl.nativeElement.textContent).toContain(
        'Failed to load devices',
      );
      expect(errorEl.nativeElement.textContent).toContain(
        'UNAVAILABLE: TJS backend unreachable',
      );

      const retryBtn = errorEl.query(By.css('.rt-btn-outlined'));
      retryBtn.nativeElement.click();
      expect(mockStore.executeSearch).toHaveBeenCalled();
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

      const headers = fixture.debugElement.queryAll(
        By.css('th.sortable-header'),
      );
      expect(headers.length).toBe(2);
      for (const header of headers) {
        expect(getComputedStyle(header.nativeElement).textTransform).toBe(
          'none',
        );
      }
      const headerNames = fixture.debugElement
        .queryAll(By.css('th .header-name'))
        .map((el) => el.nativeElement.textContent.trim());
      expect(headerNames).toEqual(['Device ID', 'Version']);

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

    it('computes groups mode when group list is non-empty and renders group cards', () => {
      mockStore.groups.set([{groupId: 'group-1', values: ['Host A']}]);
      fixture.detectChanges();

      expect(component.groupedViewMode()).toBe('groups');
      const cardEl = fixture.debugElement.query(By.css('app-fleet-group-card'));
      expect(cardEl).toBeTruthy();

      const headBtn = cardEl.query(By.css('.rt-group-head'));
      headBtn.nativeElement.click();
      expect(mockStore.toggleGroup).toHaveBeenCalledWith('group-1');
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

    it('computes error mode when groups are empty and searchError is present', () => {
      mockStore.groups.set([]);
      mockStore.isLoading.set(false);
      mockStore.searchError.set(
        'DEADLINE_EXCEEDED: Group aggregation timed out',
      );
      fixture.detectChanges();

      expect(component.groupedViewMode()).toBe('error');
      const errorEl = fixture.debugElement.query(By.css('.rt-error-state'));
      expect(errorEl).toBeTruthy();
      expect(errorEl.nativeElement.textContent).toContain(
        'Failed to load devices',
      );
      expect(errorEl.nativeElement.textContent).toContain(
        'DEADLINE_EXCEEDED: Group aggregation timed out',
      );

      const retryBtn = errorEl.query(By.css('.rt-btn-outlined'));
      retryBtn.nativeElement.click();
      expect(mockStore.executeSearch).toHaveBeenCalled();
    });
  });

  describe('Toolbar Selections', () => {
    it('shows Configure Wi-Fi and Configure Dimension buttons only for devices', () => {
      mockStore.selectedItems.set(toSelectedMap(['dev-1', 'dev-2']));
      mockStore.entity.set('devices');
      fixture.detectChanges();

      const buttons = fixture.debugElement.queryAll(
        By.css('.rt-toolbar button'),
      );
      const buttonTexts = buttons.map((b) =>
        b.nativeElement.textContent.trim(),
      );
      expect(buttonTexts.some((t) => t.includes('Configure Wi-Fi'))).toBeTrue();
      expect(
        buttonTexts.some((t) => t.includes('Configure Dimension')),
      ).toBeTrue();

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
      expect(
        hostButtonTexts.some((t) => t.includes('Configure Dimension')),
      ).toBeFalse();
    });

    it('does not open dialog when entity is hosts', () => {
      mockStore.entity.set('hosts');
      mockStore.selectedItems.set(toSelectedMap(['host-1']));
      const openSpy = spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      );

      component.bulkConfig.openWifiDialog();
      component.bulkConfig.openDimensionDialog('supported');

      expect(openSpy).not.toHaveBeenCalled();
    });

    it('opens BulkConfigWifiDialog with selected devices and clears selection on update', async () => {
      mockStore.selectedItems.set(toSelectedMap(['dev-1', 'dev-2']));
      fixture.detectChanges();
      await fixture.whenStable();
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

      component.bulkConfig.openWifiDialog();

      expect(openSpy).toHaveBeenCalledWith(
        BulkConfigWifiDialog,
        jasmine.objectContaining({
          autoFocus: false,
          data: {
            devices: [
              {deviceId: 'dev-1', hostName: 'host-for-dev-1'},
              {deviceId: 'dev-2', hostName: 'host-for-dev-2'},
            ],
          },
        }),
      );
      expect(mockStore.clearSelection).toHaveBeenCalled();
      expect(mockStore.executeSearch).toHaveBeenCalled();
    });

    it('does not open dialog when selectedItems is empty', () => {
      mockStore.selectedItems.set(new Map());
      const openSpy = spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      );

      component.bulkConfig.openWifiDialog();

      expect(openSpy).not.toHaveBeenCalled();
    });

    it('does not clear selection or re-execute search when dialog is closed without update', async () => {
      mockStore.selectedItems.set(toSelectedMap(['dev-1']));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      ).and.returnValue({
        afterClosed: () => of(undefined),
      } as unknown as MatDialogRef<
        BulkConfigWifiDialog,
        BulkConfigDialogResult
      >);

      component.bulkConfig.openWifiDialog();

      expect(mockStore.clearSelection).not.toHaveBeenCalled();
      expect(mockStore.executeSearch).not.toHaveBeenCalled();
    });

    it('does not clear selection or re-execute search when dialog returns updated: false', async () => {
      mockStore.selectedItems.set(toSelectedMap(['dev-1']));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      ).and.returnValue({
        afterClosed: () => of({updated: false}),
      } as unknown as MatDialogRef<
        BulkConfigWifiDialog,
        BulkConfigDialogResult
      >);

      component.bulkConfig.openWifiDialog();

      expect(mockStore.clearSelection).not.toHaveBeenCalled();
      expect(mockStore.executeSearch).not.toHaveBeenCalled();
    });

    it('reactively pre-fetches configurable dimensions when devices are selected', async () => {
      expect(
        mockConfigService.getConfigurableDimensions,
      ).not.toHaveBeenCalled();

      mockStore.selectedItems.set(toSelectedMap(['dev-1', 'dev-2']));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(mockConfigService.getConfigurableDimensions).toHaveBeenCalledTimes(
        1,
      );
      const dimConfig = component.bulkConfig.dimensionConfig();
      expect(dimConfig.enabled).toBeTrue();
      expect(dimConfig.dimensions.length).toBe(1);
      expect(dimConfig.dimensions[0].dimensionName).toBe('recovery');
      expect(dimConfig.allowCustom).toBeTrue();
      expect(dimConfig.isLoading).toBeFalse();
    });

    it('hides Configure Wi-Fi and Configure Dimension buttons when GetConfigurableDimensionsResponse.enabled is false', async () => {
      mockConfigService.getConfigurableDimensions.and.returnValue(
        of({
          enabled: false,
          dimensions: [],
          allowCustomDimensions: false,
        }),
      );

      mockStore.selectedItems.set(toSelectedMap(['dev-1', 'dev-2']));
      mockStore.entity.set('devices');
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(component.bulkConfig.dimensionConfig().enabled).toBeFalse();
      const buttons = fixture.debugElement.queryAll(
        By.css('.rt-toolbar button'),
      );
      const buttonTexts = buttons.map((b) =>
        b.nativeElement.textContent.trim(),
      );
      expect(
        buttonTexts.some((t) => t.includes('Configure Wi-Fi')),
      ).toBeFalse();
      expect(
        buttonTexts.some((t) => t.includes('Configure Dimension')),
      ).toBeFalse();
    });

    it('hides Configure Wi-Fi and Configure Dimension buttons when GetConfigurableDimensionsResponse.enabled is omitted', async () => {
      mockConfigService.getConfigurableDimensions.and.returnValue(
        of({
          dimensions: [],
          allowCustomDimensions: false,
        }),
      );

      mockStore.selectedItems.set(toSelectedMap(['dev-1', 'dev-2']));
      mockStore.entity.set('devices');
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(component.bulkConfig.dimensionConfig().enabled).toBeFalse();
      const buttons = fixture.debugElement.queryAll(
        By.css('.rt-toolbar button'),
      );
      const buttonTexts = buttons.map((b) =>
        b.nativeElement.textContent.trim(),
      );
      expect(
        buttonTexts.some((t) => t.includes('Configure Wi-Fi')),
      ).toBeFalse();
      expect(
        buttonTexts.some((t) => t.includes('Configure Dimension')),
      ).toBeFalse();

      const openSpy = spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      );
      component.bulkConfig.openWifiDialog();
      component.bulkConfig.openDimensionDialog('supported');
      expect(openSpy).not.toHaveBeenCalled();
    });

    it('hides Configure Wi-Fi and Configure Dimension buttons without fetching on Partner ATS fleet', async () => {
      mockStore.fleet.set('ats');
      mockStore.entity.set('devices');
      mockStore.selectedItems.set(toSelectedMap(['dev-1', 'dev-2']));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(
        mockConfigService.getConfigurableDimensions,
      ).not.toHaveBeenCalled();
      expect(component.bulkConfig.dimensionConfig().enabled).toBeFalse();

      const buttons = fixture.debugElement.queryAll(
        By.css('.rt-toolbar button'),
      );
      const buttonTexts = buttons.map((b) =>
        b.nativeElement.textContent.trim(),
      );
      expect(
        buttonTexts.some((t) => t.includes('Configure Wi-Fi')),
      ).toBeFalse();
      expect(
        buttonTexts.some((t) => t.includes('Configure Dimension')),
      ).toBeFalse();

      const openSpy = spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      );
      component.bulkConfig.openWifiDialog();
      component.bulkConfig.openDimensionDialog('supported');
      expect(openSpy).not.toHaveBeenCalled();
    });

    it('disables button and displays loading state during dimension loading', async () => {
      const subject = new Subject<GetConfigurableDimensionsResponse>();
      mockConfigService.getConfigurableDimensions.and.returnValue(subject);

      mockStore.selectedItems.set(toSelectedMap(['dev-1']));
      fixture.detectChanges();

      expect(component.bulkConfig.dimensionConfig().isLoading).toBeTrue();
      expect(mockConfigService.getConfigurableDimensions).toHaveBeenCalledTimes(
        1,
      );

      const loadingButtons = fixture.debugElement.queryAll(
        By.css('.rt-toolbar button'),
      );
      const wifiBtnLoading = loadingButtons.find((b) =>
        b.nativeElement.textContent.includes('Configure Wi-Fi'),
      );
      const configBtnLoading = loadingButtons.find((b) =>
        b.nativeElement.textContent.includes('Configure Dimension'),
      );
      expect(wifiBtnLoading?.nativeElement.disabled).toBeTrue();
      expect(configBtnLoading?.nativeElement.disabled).toBeTrue();

      const openSpy = spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      );
      component.bulkConfig.openWifiDialog();
      expect(openSpy).not.toHaveBeenCalled();

      // Updating selection while already loading for the same context does not re-trigger fetch
      mockStore.selectedItems.set(toSelectedMap(['dev-1', 'dev-2']));
      fixture.detectChanges();
      expect(mockConfigService.getConfigurableDimensions).toHaveBeenCalledTimes(
        1,
      );

      subject.next({
        enabled: true,
        dimensions: [],
        allowCustomDimensions: true,
      });
      subject.complete();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(component.bulkConfig.dimensionConfig().isLoading).toBeFalse();
      expect(component.bulkConfig.dimensionConfig().allowCustom).toBeTrue();
      const loadedButtons = fixture.debugElement.queryAll(
        By.css('.rt-toolbar button'),
      );
      const wifiBtnLoaded = loadedButtons.find((b) =>
        b.nativeElement.textContent.includes('Configure Wi-Fi'),
      );
      const configBtnLoaded = loadedButtons.find((b) =>
        b.nativeElement.textContent.includes('Configure Dimension'),
      );
      expect(wifiBtnLoaded?.nativeElement.disabled).toBeFalse();
      expect(configBtnLoaded?.nativeElement.disabled).toBeFalse();
    });

    it('retains loaded dimensions across selection changes and tab switches without re-fetching', async () => {
      mockStore.selectedItems.set(toSelectedMap(['dev-1']));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(mockConfigService.getConfigurableDimensions).toHaveBeenCalledTimes(
        1,
      );

      mockStore.selectedItems.set(toSelectedMap(['dev-1', 'dev-2']));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(mockConfigService.getConfigurableDimensions).toHaveBeenCalledTimes(
        1,
      );

      // Clearing selection and re-selecting on own fleet does not re-fetch
      mockStore.selectedItems.set(new Map());
      fixture.detectChanges();
      mockStore.selectedItems.set(toSelectedMap(['dev-2']));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(mockConfigService.getConfigurableDimensions).toHaveBeenCalledTimes(
        1,
      );

      // Switching to Partner ATS fleet hides bulk config without calling backend again
      mockStore.fleet.set('ats');
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(mockConfigService.getConfigurableDimensions).toHaveBeenCalledTimes(
        1,
      );
      expect(component.bulkConfig.dimensionConfig().enabled).toBeFalse();

      // Returning to own fleet restores cached dimensions without re-fetching
      mockStore.fleet.set('internal');
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(mockConfigService.getConfigurableDimensions).toHaveBeenCalledTimes(
        1,
      );
      expect(component.bulkConfig.dimensionConfig().enabled).toBeTrue();
      expect(component.bulkConfig.dimensionConfig().dimensions.length).toBe(1);
    });

    it('exposes error state during dimension loading failure and reloads on retryDimensions()', async () => {
      mockConfigService.getConfigurableDimensions.and.returnValue(
        throwError(() => ({
          error: {error: {message: 'Service down'}},
        })),
      );

      mockStore.selectedItems.set(toSelectedMap(['dev-1']));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(component.bulkConfig.dimensionConfig().isLoading).toBeFalse();
      expect(component.bulkConfig.dimensionConfig().error).toBe('Service down');
      expect(component.bulkConfig.dimensionConfig().dimensions).toEqual([]);
      const errorButtons = fixture.debugElement.queryAll(
        By.css('.rt-toolbar button'),
      );
      const wifiBtnError = errorButtons.find((b) =>
        b.nativeElement.textContent.includes('Configure Wi-Fi'),
      );
      expect(wifiBtnError?.nativeElement.disabled).toBeTrue();
      const openSpy = spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      );
      component.bulkConfig.openWifiDialog();
      expect(openSpy).not.toHaveBeenCalled();

      mockConfigService.getConfigurableDimensions.and.returnValue(
        of({
          enabled: true,
          dimensions: [
            {
              dimensionName: 'recovery',
              displayName: 'recovery',
              dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
            },
          ],
          allowCustomDimensions: true,
        }),
      );
      component.bulkConfig.retryDimensions();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(component.bulkConfig.dimensionConfig().error).toBeNull();
      expect(component.bulkConfig.dimensionConfig().dimensions.length).toBe(1);
    });

    it('opens BulkConfigDimensionDialog with selected devices and dimension', async () => {
      mockStore.selectedItems.set(toSelectedMap(['dev-1', 'dev-2']));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      const openSpy = spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      ).and.returnValue({
        afterClosed: () => of({updated: true}),
      } as unknown as MatDialogRef<
        BulkConfigDimensionDialog,
        BulkConfigDialogResult
      >);

      const dim: ConfigurableDimension = {
        dimensionName: 'recovery',
        displayName: 'recovery',
        dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
      };

      component.bulkConfig.openDimensionDialog(dim);

      expect(openSpy).toHaveBeenCalledWith(
        BulkConfigDimensionDialog,
        jasmine.objectContaining({
          data: jasmine.objectContaining({
            devices: [
              {deviceId: 'dev-1', hostName: 'host-for-dev-1'},
              {deviceId: 'dev-2', hostName: 'host-for-dev-2'},
            ],
            dimension: dim,
          }),
        }),
      );
      expect(mockStore.clearSelection).toHaveBeenCalled();
      expect(mockStore.executeSearch).toHaveBeenCalled();
    });

    it('opens BulkConfigDimensionDialog with custom dimension list', async () => {
      mockStore.selectedItems.set(toSelectedMap(['dev-1']));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      const openSpy = spyOn(
        (component as unknown as {dialog: MatDialog}).dialog,
        'open',
      ).and.returnValue({
        afterClosed: () => of({updated: true}),
      } as unknown as MatDialogRef<
        BulkConfigDimensionDialog,
        BulkConfigDialogResult
      >);

      component.bulkConfig.openDimensionDialog('supported');

      expect(openSpy).toHaveBeenCalledWith(
        BulkConfigDimensionDialog,
        jasmine.objectContaining({
          data: jasmine.objectContaining({
            devices: [{deviceId: 'dev-1', hostName: 'host-for-dev-1'}],
            dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
          }),
        }),
      );
      expect(mockStore.clearSelection).toHaveBeenCalled();
      expect(mockStore.executeSearch).toHaveBeenCalled();
    });

    it('renders Custom Supported/Required Dimension menu items without icons when allowCustom is true', async () => {
      mockStore.selectedItems.set(toSelectedMap(['dev-1']));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const configBtn = fixture.debugElement.query(
        By.css('button[title="Configure device dimensions"]'),
      );
      expect(configBtn).toBeTruthy();
      configBtn.nativeElement.click();
      fixture.detectChanges();

      const footerButtons = Array.from(
        document.querySelectorAll(
          '.configure-menu-footer button[mat-menu-item]',
        ),
      );
      expect(footerButtons.length).toBe(2);

      const icons = footerButtons.map((btn) => btn.querySelector('mat-icon'));
      const labels = footerButtons.map((btn) =>
        btn.querySelector('span')?.textContent?.trim(),
      );

      expect(icons).toEqual([null, null]);
      expect(labels).toEqual([
        'Custom Supported Dimension…',
        'Custom Required Dimension…',
      ]);
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
      mockStore.selectedItems.set(new Map());
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

    it('clicks Configure Wi-Fi button in selection toolbar and opens dialog', async () => {
      mockStore.selectedItems.set(toSelectedMap(['dev-1', 'dev-2']));
      mockStore.entity.set('devices');
      fixture.detectChanges();
      await fixture.whenStable();
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

  describe('Fleet Label', () => {
    it('renders the toolbar counts with the fleet suffix when the fleet is switchable (internal build)', () => {
      mockStore.effectiveTotalCount.set(3);
      mockStore.displayColumns.set([{key: 'id', displayName: 'Device ID'}]);
      mockStore.rows.set([{id: 'dev-1', cells: [{text: {value: 'dev-1'}}]}]);
      fixture.detectChanges();

      expect(component.fleetLabel()).toBe('Google Internal');
      expect(component.fleetLabelSuffix()).toBe(' · Google Internal');
      expect(
        fixture.nativeElement.querySelector('.rt-count').textContent.trim(),
      ).toBe('3 devices · Google Internal');

      mockStore.selectedItems.set(toSelectedMap(['dev-1', 'dev-2']));
      fixture.detectChanges();
      expect(
        fixture.nativeElement.querySelector('.rt-count').textContent.trim(),
      ).toBe('2 selected · Google Internal');
    });

    it('omits the fleet label and suffix when the fleet is not switchable (OSS build)', () => {
      mockStore.isFleetSwitchable = false;
      mockStore.effectiveTotalCount.set(3);
      mockStore.displayColumns.set([{key: 'id', displayName: 'Device ID'}]);
      mockStore.rows.set([{id: 'dev-1', cells: [{text: {value: 'dev-1'}}]}]);
      fixture.detectChanges();

      expect(component.fleetLabel()).toBe('');
      expect(component.fleetLabelSuffix()).toBe('');
      expect(
        fixture.nativeElement.querySelector('.rt-count').textContent.trim(),
      ).toBe('3 devices');

      mockStore.selectedItems.set(toSelectedMap(['dev-1', 'dev-2']));
      fixture.detectChanges();
      expect(
        fixture.nativeElement.querySelector('.rt-count').textContent.trim(),
      ).toBe('2 selected');
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

    it('renders group pagination with "Groups per page:" label and delegates pageSizeChange to store', () => {
      mockStore.groupByKeys.set(['host']);
      mockStore.groups.set([{groupId: 'group-1', title: 'Host A'}]);
      mockStore.pageSize.set(25);
      fixture.detectChanges();

      const pageLabelEl = fixture.nativeElement.querySelector('.rt-page-label');
      expect(pageLabelEl?.textContent?.trim()).toBe('Groups per page:');

      const paginationDebug = fixture.debugElement.query(
        By.css('app-search-pagination'),
      );
      expect(paginationDebug.componentInstance.pageSize()).toBe(25);

      paginationDebug.componentInstance.pageSizeChange.emit(50);
      expect(mockStore.setPageSize).toHaveBeenCalledWith(50);
    });

    it('applies active selection toolbar background color in grouped mode matching flat view', () => {
      mockStore.selectedItems.set(toSelectedMap(['dev-1']));
      mockStore.groupByKeys.set([]);
      fixture.detectChanges();

      const flatToolbar = fixture.nativeElement.querySelector(
        '.rt-toolbar.rt-toolbar-active',
      ) as HTMLElement;
      expect(flatToolbar).toBeTruthy();
      const flatBg = getComputedStyle(flatToolbar).backgroundColor;

      mockStore.groupByKeys.set(['host']);
      mockStore.groups.set([{groupId: 'group-1', title: 'Host A'}]);
      fixture.detectChanges();

      const groupedToolbar = fixture.nativeElement.querySelector(
        '.results-area.grouped-mode .rt-toolbar.rt-toolbar-active',
      ) as HTMLElement;
      expect(groupedToolbar).toBeTruthy();
      expect(getComputedStyle(groupedToolbar).backgroundColor).toBe(flatBg);
    });
  });

  describe('Zero-Dialog Bulk Export & Toolbar Cleanup', () => {
    it('does not render a redundant more_vert overflow menu in unselected flat toolbar', () => {
      mockStore.selectedItems.set(new Map());
      mockStore.groupByKeys.set([]);
      fixture.detectChanges();

      const moreBtn = fixture.debugElement.query(
        By.css('button[title="More options"]'),
      );
      expect(moreBtn).toBeNull();
    });

    it('renders active Export menu trigger for both devices and hosts when items are selected', () => {
      mockStore.entity.set('devices');
      mockStore.selectedItems.set(toSelectedMap(['dev-1', 'dev-2']));
      fixture.detectChanges();

      let exportBtn = fixture.debugElement.query(
        By.css('button[title="Export selected items"]'),
      );
      expect(exportBtn).toBeTruthy();
      expect(exportBtn.nativeElement.disabled).toBeFalse();
      expect(component.selectionExportLabel()).toBe(
        'Download CSV (2 selected devices)',
      );

      mockStore.entity.set('hosts');
      mockStore.selectedItems.set(new Map([['host-1', {id: 'host-1'}]]));
      fixture.detectChanges();

      exportBtn = fixture.debugElement.query(
        By.css('button[title="Export selected items"]'),
      );
      expect(exportBtn).toBeTruthy();
      expect(exportBtn.nativeElement.disabled).toBeFalse();
      expect(component.selectionExportLabel()).toBe(
        'Download CSV (1 selected host)',
      );
    });

    it('exports selected rows as CSV directly from selectedItems', () => {
      mockStore.entity.set('devices');
      mockStore.fleet.set('internal');
      mockStore.groupByKeys.set([]);
      mockStore.displayColumns.set([
        {key: 'field::uuid', displayName: 'Device ID'},
        {key: 'status', displayName: 'Status'},
      ]);
      const row1: Row = {
        id: 'dev-1',
        cells: [
          {
            link: {
              text: 'dev-1',
              target: {device: {id: 'dev-1', hostName: 'host-for-dev-1'}},
            },
          },
          {status: {text: 'IDLE'}},
        ],
      };
      mockStore.selectedItems.set(new Map([['dev-1@@host-for-dev-1', row1]]));
      fixture.detectChanges();

      const clickSpy = spyOn(HTMLAnchorElement.prototype, 'click');
      spyOn(URL, 'createObjectURL').and.returnValue('blob:mock');

      component.exportSelection('csv');

      expect(clickSpy).toHaveBeenCalled();
      expect(mockSnackBarService.showSuccess).toHaveBeenCalledWith(
        jasmine.stringMatching(
          /^Exported 1 device \(2 columns\) to omnilab-devices-internal-\d{4}-\d{2}-\d{2}\.csv$/,
        ),
      );
    });

    it('copies selected rows as TSV to clipboard and shows success snackbar', async () => {
      mockStore.entity.set('devices');
      mockStore.groupByKeys.set([]);
      mockStore.displayColumns.set([
        {key: 'field::uuid', displayName: 'Device ID'},
        {key: 'model', displayName: 'Model'},
      ]);
      const row1: Row = {
        id: 'dev-1',
        cells: [
          {
            link: {
              text: 'dev-1',
              target: {device: {id: 'dev-1', hostName: 'host-for-dev-1'}},
            },
          },
          {text: {value: 'Pixel 9'}},
        ],
      };
      mockStore.selectedItems.set(new Map([['dev-1@@host-for-dev-1', row1]]));
      fixture.detectChanges();

      component.exportSelection('tsv');

      expect(mockClipboardService.copyToClipboard).toHaveBeenCalledWith(
        'Device ID\tModel\ndev-1\tPixel 9',
      );
      expect(mockSnackBarService.showSuccess).toHaveBeenCalledWith(
        'Copied 1 device (2 columns) to clipboard',
      );
    });

    it('exports all selected rows across multiple pages using selectedItems', () => {
      mockStore.entity.set('devices');
      mockStore.fleet.set('internal');
      mockStore.groupByKeys.set([]);
      mockStore.displayColumns.set([
        {key: 'field::uuid', displayName: 'Device ID'},
        {key: 'status', displayName: 'Status'},
      ]);

      const page1Row: Row = {
        id: 'dev-page-1',
        cells: [
          {
            link: {
              text: 'dev-page-1',
              target: {device: {id: 'dev-page-1', hostName: 'host-1'}},
            },
          },
          {status: {text: 'IDLE'}},
        ],
      };
      const page2Row: Row = {
        id: 'dev-page-2',
        cells: [
          {
            link: {
              text: 'dev-page-2',
              target: {device: {id: 'dev-page-2', hostName: 'host-2'}},
            },
          },
          {status: {text: 'BUSY'}},
        ],
      };

      // Simulate user having selected page 1 row, navigated to page 2, and selected page 2 row
      mockStore.rows.set([page2Row]);
      mockStore.selectedItems.set(
        new Map([
          ['dev-page-1@@host-1', page1Row],
          ['dev-page-2@@host-2', page2Row],
        ]),
      );

      component.exportSelection('tsv');

      expect(mockClipboardService.copyToClipboard).toHaveBeenCalledWith(
        'Device ID\tStatus\ndev-page-1\tIDLE\ndev-page-2\tBUSY',
      );
      expect(mockSnackBarService.showSuccess).toHaveBeenCalledWith(
        'Copied 2 devices (2 columns) to clipboard',
      );
    });

    it('disables Export button and shows tooltip when more than 5,000 items are selected', () => {
      mockStore.entity.set('devices');
      const overLimitMap = new Map<string, Row>();
      for (let i = 1; i <= 5001; i++) {
        overLimitMap.set(`dev-${i}`, {id: `dev-${i}`});
      }
      mockStore.selectedItems.set(overLimitMap);
      fixture.detectChanges();

      expect(component.isExportDisabled()).toBeTrue();
      expect(component.exportDisabledTooltip()).toBe(
        'Export is limited to 5,000 selected devices',
      );

      const wrapper = fixture.debugElement.query(
        By.css('.rt-export-trigger-wrapper'),
      );
      expect(wrapper).toBeTruthy();
      const exportBtn = wrapper.query(By.css('button'));
      expect(exportBtn.nativeElement.disabled).toBeTrue();
      expect(exportBtn.nativeElement.title).toBe('');

      const clickSpy = spyOn(HTMLAnchorElement.prototype, 'click');
      component.exportSelection('csv');
      expect(clickSpy).not.toHaveBeenCalled();
      expect(mockSnackBarService.showSuccess).not.toHaveBeenCalled();
    });
  });
});
