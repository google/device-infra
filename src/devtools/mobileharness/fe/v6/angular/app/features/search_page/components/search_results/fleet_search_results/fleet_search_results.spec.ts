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
import {Fleet} from '../../../../../core/models/search_fleet';
import {
  CONFIG_SERVICE,
  ConfigService,
} from '../../../../../core/services/config/config_service';
import {EnvUniverseService} from '../../../../../core/services/env_universe_service';
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
  let mockEnvUniverseService: jasmine.SpyObj<EnvUniverseService>;
  let mockConfigService: jasmine.SpyObj<ConfigService>;
  let mockClipboardService: jasmine.SpyObj<ClipboardService>;
  let mockSnackBarService: jasmine.SpyObj<SnackBarService>;

  beforeEach(async () => {
    mockStore = new MockFleetSearchStore();
    mockDialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    mockEnvUniverseService = jasmine.createSpyObj<EnvUniverseService>(
      'EnvUniverseService',
      ['getUniverseString'],
    );
    mockEnvUniverseService.getUniverseString.and.returnValue('google_1p');
    mockConfigService = jasmine.createSpyObj<ConfigService>('ConfigService', [
      'getConfigurableDimensions',
    ]);
    mockConfigService.getConfigurableDimensions.and.returnValue(
      of({
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
        {provide: EnvUniverseService, useValue: mockEnvUniverseService},
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
    it('shows Configure Wi-Fi and Configuration buttons only for devices', () => {
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
      expect(buttonTexts.some((t) => t.includes('Configuration'))).toBeTrue();

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
        hostButtonTexts.some((t) => t.includes('Configuration')),
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

    it('opens BulkConfigWifiDialog with selected devices and clears selection on update', () => {
      mockStore.selectedItems.set(toSelectedMap(['dev-1', 'dev-2']));
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
            universe: 'google_1p',
            fleet: Fleet.FLEET_SELF,
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

    it('does not clear selection or re-execute search when dialog is closed without update', () => {
      mockStore.selectedItems.set(toSelectedMap(['dev-1']));
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

    it('does not clear selection or re-execute search when dialog returns updated: false', () => {
      mockStore.selectedItems.set(toSelectedMap(['dev-1']));
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

    it('keeps Configure Wi-Fi visible and hides Configuration button when GetConfigurableDimensionsResponse.enabled is false', async () => {
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
      expect(buttonTexts.some((t) => t.includes('Configure Wi-Fi'))).toBeTrue();
      expect(buttonTexts.some((t) => t.includes('Configuration'))).toBeFalse();
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
    });

    it('does not re-fetch dimensions if already loaded for the same fleet, but invalidates and re-fetches when fleet changes', async () => {
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

      // Clearing selection and re-selecting in the same fleet does not re-fetch
      mockStore.selectedItems.set(new Map());
      fixture.detectChanges();
      mockStore.selectedItems.set(toSelectedMap(['dev-2']));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(mockConfigService.getConfigurableDimensions).toHaveBeenCalledTimes(
        1,
      );

      // Switching fleet invalidates cached dimensions and automatically re-fetches via effect
      mockStore.fleet.set('ats');
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(mockConfigService.getConfigurableDimensions).toHaveBeenCalledTimes(
        2,
      );
      expect(component.bulkConfig.dimensionConfig().dimensions.length).toBe(1);
    });

    it('handles error during dimension loading gracefully', async () => {
      mockConfigService.getConfigurableDimensions.and.returnValue(
        throwError(() => new Error('Service down')),
      );

      mockStore.selectedItems.set(toSelectedMap(['dev-1']));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(component.bulkConfig.dimensionConfig().isLoading).toBeFalse();
      expect(component.bulkConfig.dimensionConfig().dimensions).toEqual([]);
    });

    it('opens BulkConfigDimensionDialog with selected devices and dimension', () => {
      mockStore.selectedItems.set(toSelectedMap(['dev-1', 'dev-2']));
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

    it('opens BulkConfigDimensionDialog with custom dimension list', () => {
      mockStore.selectedItems.set(toSelectedMap(['dev-1']));
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

    it('clicks Configure Wi-Fi button in selection toolbar and opens dialog', () => {
      mockStore.selectedItems.set(toSelectedMap(['dev-1', 'dev-2']));
      mockStore.entity.set('devices');
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
