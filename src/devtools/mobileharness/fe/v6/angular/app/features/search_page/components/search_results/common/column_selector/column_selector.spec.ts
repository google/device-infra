import {CdkDragDrop} from '@angular/cdk/drag-drop';
import {
  ComponentFixture,
  fakeAsync,
  TestBed,
  tick,
} from '@angular/core/testing';
import {MatDialogRef} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of} from 'rxjs';

import {FleetColumnCatalogResponse} from '../../../../../../core/models/search';
import {
  SEARCH_SERVICE,
  SearchService,
} from '../../../../../../core/services/search/search_service';
import {ColumnSelectorDialogData} from '../../../../models';

import {ColumnSelectorComponent} from './column_selector';

describe('ColumnSelectorComponent', () => {
  let component: ColumnSelectorComponent;
  let fixture: ComponentFixture<MatTestDialogOpener<ColumnSelectorComponent>>;
  let mockSearchService: jasmine.SpyObj<SearchService>;
  let mockDialogRef: MatDialogRef<ColumnSelectorComponent>;

  const mockCatalogResponse: FleetColumnCatalogResponse = {
    sections: [
      {
        heading: 'Suggested for you',
        entries: [
          {key: 'status', displayName: 'Status', reason: 'Active filter'},
          {key: 'model', displayName: 'Model', reason: 'Recently used'},
        ],
      },
      {
        heading: 'Built-in fields',
        totalAvailable: 6,
        entries: [
          {key: 'id', displayName: 'Device ID', deviceCount: 14},
          {key: 'status', displayName: 'Status', deviceCount: 14},
          {key: 'type', displayName: 'Device Type', deviceCount: 14},
          {key: 'owner', displayName: 'Owner', deviceCount: 14},
          {key: 'hostName', displayName: 'Host Name', deviceCount: 14},
          {key: 'ip', displayName: 'IP Address', deviceCount: 12},
        ],
      },
      {
        heading: 'Dimensions',
        totalAvailable: 20,
        entries: [
          {key: 'dim::model', displayName: 'Model', deviceCount: 14},
          {key: 'dim::sdk', displayName: 'SDK Version', deviceCount: 13},
        ],
      },
    ],
  };

  const dialogData: ColumnSelectorDialogData = {
    entity: 'devices',
    fleet: 'self',
    selectedColumns: ['id', 'status', 'type', 'owner'],
    lockedColumns: ['id'],
    defaultColumns: ['id', 'status', 'type', 'owner', 'model'],
    activeFilters: [],
  };

  beforeEach(async () => {
    localStorage.clear();
    mockSearchService = jasmine.createSpyObj('SearchService', [
      'getFleetColumnCatalog',
    ]);
    mockSearchService.getFleetColumnCatalog.and.returnValue(
      of(mockCatalogResponse),
    );

    await TestBed.configureTestingModule({
      imports: [
        ColumnSelectorComponent,
        NoopAnimationsModule,
        MatTestDialogOpenerModule,
      ],
      providers: [{provide: SEARCH_SERVICE, useValue: mockSearchService}],
    }).compileComponents();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(ColumnSelectorComponent, {
        data: dialogData,
      }),
    );
    component = fixture.componentInstance.dialogRef.componentInstance;
    mockDialogRef = fixture.componentInstance.dialogRef;
    spyOn(mockDialogRef, 'close');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('initializes draft columns from input data with locked columns first', () => {
    expect(component.draftColumns()).toEqual(['id', 'status', 'type', 'owner']);
    expect(component.isLocked('id')).toBeTrue();
    expect(component.isLocked('status')).toBeFalse();
  });

  it('renders locked column with lock icon and no remove button', () => {
    fixture.detectChanges();
    const lockedRow = document.querySelector('.cs-sel-row.cs-locked');
    expect(lockedRow).toBeTruthy();
    expect(lockedRow?.textContent).toContain('Device ID');
    expect(lockedRow?.textContent).toContain('locked');
    expect(lockedRow?.querySelector('.cs-x')).toBeNull();
  });

  it('renders draggable non-locked columns with remove buttons', () => {
    fixture.detectChanges();
    const rows = document.querySelectorAll('.cs-sel-row:not(.cs-locked)');
    expect(rows.length).toBe(3); // status, type, owner
    expect(rows[0]?.textContent).toContain('Status');
    expect(rows[0]?.querySelector('.cs-x')).toBeTruthy();
  });

  it('removes non-locked column when remove button is clicked', () => {
    component.removeColumn('type');
    expect(component.draftColumns()).toEqual(['id', 'status', 'owner']);
  });

  it('does not remove locked column', () => {
    component.removeColumn('id');
    expect(component.draftColumns()).toContain('id');
  });

  it('toggles column selection via checkbox', () => {
    expect(component.isSelected('ip')).toBeFalse();
    component.toggleColumn('ip', true);
    expect(component.isSelected('ip')).toBeTrue();
    expect(component.draftColumns()).toEqual([
      'id',
      'status',
      'type',
      'owner',
      'ip',
    ]);

    component.toggleColumn('ip', false);
    expect(component.isSelected('ip')).toBeFalse();
    expect(component.draftColumns()).toEqual(['id', 'status', 'type', 'owner']);
  });

  it('reorders columns with drag and drop respecting locked columns', () => {
    // previousIndex: 2 (type), currentIndex: 1 (status)
    const dropEvent: CdkDragDrop<string[]> = {
      previousIndex: 2,
      currentIndex: 1,
    } as unknown as CdkDragDrop<string[]>;

    component.drop(dropEvent);
    expect(component.draftColumns()).toEqual(['id', 'type', 'status', 'owner']);
  });

  it('does not move column before locked column in drag and drop', () => {
    // previousIndex: 2 (type), currentIndex: 0 (locked position)
    const dropEvent: CdkDragDrop<string[]> = {
      previousIndex: 2,
      currentIndex: 0,
    } as unknown as CdkDragDrop<string[]>;

    component.drop(dropEvent);
    // Should be clamped to index 1
    expect(component.draftColumns()).toEqual(['id', 'type', 'status', 'owner']);
  });

  it('resets draft columns to defaultColumns on reset() without closing dialog', () => {
    component.removeColumn('status');
    component.removeColumn('type');
    expect(component.draftColumns()).toEqual(['id', 'owner']);

    const resetBtn = document.querySelector(
      '.cs-btn-reset',
    ) as HTMLButtonElement;
    expect(resetBtn).toBeTruthy();
    resetBtn.click();
    fixture.detectChanges();

    expect(component.draftColumns()).toEqual([
      'id',
      'status',
      'type',
      'owner',
      'model',
    ]);
    expect(mockDialogRef.close).not.toHaveBeenCalled();
  });

  it('closes dialog with undefined on cancel()', () => {
    component.cancel();
    expect(mockDialogRef.close).toHaveBeenCalledWith();
  });

  it('closes dialog with selected columns and descriptors on apply()', () => {
    component.draftColumns.set(['id', 'status', 'dim::sdk', 'ip']);
    component.apply();

    expect(mockDialogRef.close).toHaveBeenCalledWith(
      jasmine.objectContaining({
        columns: [
          {key: 'id', displayName: 'Device ID'},
          {key: 'status', displayName: 'Status'},
          {key: 'dim::sdk', displayName: 'SDK Version'},
          {key: 'ip', displayName: 'IP Address'},
        ],
        isReset: false,
      }),
    );
  });

  it('closes dialog with isReset: true on apply() when reset() was called', () => {
    component.reset();
    component.apply();

    expect(mockDialogRef.close).toHaveBeenCalledWith(
      jasmine.objectContaining({
        isReset: true,
      }),
    );
  });

  it('computes recentKeys from non-locked initial columns', () => {
    // dialogData.selectedColumns is ['id', 'status', 'type', 'owner'], locked is ['id']
    expect(component.recentKeys()).toEqual(['status', 'type', 'owner']);
  });

  it('debounces search input and requests catalog', fakeAsync(() => {
    const inputEl = document.querySelector(
      '.cs-searchbar input',
    ) as HTMLInputElement;
    inputEl.value = 'battery';
    inputEl.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(component.searchQuery()).toBe('battery');
    expect(component.debouncedQuery()).toBe('');

    tick(250);
    expect(component.debouncedQuery()).toBe('battery');
  }));

  it('renders catalog sections and hints', () => {
    fixture.detectChanges();
    const sections = document.querySelectorAll('.cs-section');
    expect(sections.length).toBe(3);

    const builtInHead = sections[1]?.querySelector('.cs-head');
    expect(builtInHead?.textContent).toContain('Built-in fields');
  });

  it('pre-formats catalog sections into ViewModels with device count and reasons', () => {
    fixture.detectChanges();
    const sections = component.viewCatalogSections();
    expect(sections.length).toBe(3);

    const suggested = sections[0];
    expect(suggested.heading).toBe('Suggested for you');
    expect(suggested.entries?.length).toBe(2);
    expect(suggested.entries?.[0]).toEqual({
      key: 'status',
      displayName: 'Status',
      reason: 'Active filter',
    });
    expect(suggested.entries?.[1]).toEqual({
      key: 'model',
      displayName: 'Model',
      reason: 'Recently used',
    });

    const builtIn = sections[1];
    expect(builtIn.heading).toBe('Built-in fields');
    expect(builtIn.totalAvailable).toBe(6);
    expect(builtIn.entries?.[0]).toEqual({
      key: 'id',
      displayName: 'Device ID',
      deviceCount: 14,
    });

    expect(component.matchedColumnsCount()).toBe(10);
  });
});
