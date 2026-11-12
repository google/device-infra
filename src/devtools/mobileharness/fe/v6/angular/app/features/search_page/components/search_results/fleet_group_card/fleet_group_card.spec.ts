import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {Column, FleetGroup, Row} from '../../../../../core/models/search';
import {GroupPageState} from '../../../models';
import {FleetSearchStore} from '../../../services/fleet_search_store';
import {FleetGroupCardComponent} from './fleet_group_card';

class MockFleetSearchStore {
  readonly entity = signal<string>('devices');
  readonly openGroupIds = signal<Set<string>>(new Set());
  readonly expandedGroupPages = signal<Map<string, GroupPageState>>(new Map());
  readonly displayColumns = signal<Column[]>([]);
  readonly columnDescriptions = signal<ReadonlyMap<string, string>>(new Map());
  readonly selectedItems = signal<Map<string, Row>>(new Map());
  readonly toggleGroup = jasmine.createSpy('toggleGroup');
  readonly toggleSelectRows = jasmine.createSpy('toggleSelectRows');
  readonly toggleGroupRowSort = jasmine.createSpy('toggleGroupRowSort');
  readonly setGroupPageSize = jasmine.createSpy('setGroupPageSize');
  readonly onLoadGroupRows = jasmine.createSpy('onLoadGroupRows');
}

describe('FleetGroupCardComponent', () => {
  let component: FleetGroupCardComponent;
  let fixture: ComponentFixture<FleetGroupCardComponent>;
  let mockStore: MockFleetSearchStore;

  const mockGroup: FleetGroup = {
    groupId: 'group-1',
    values: ['Pixel 8', 'ready'],
    itemCount: 10,
    utilization: {
      busy: 3,
      idle: 5,
      other: 2,
      total: 10,
    },
  };

  beforeEach(async () => {
    mockStore = new MockFleetSearchStore();
    await TestBed.configureTestingModule({
      imports: [FleetGroupCardComponent, NoopAnimationsModule],
      providers: [{provide: FleetSearchStore, useValue: mockStore}],
    }).compileComponents();

    fixture = TestBed.createComponent(FleetGroupCardComponent);
    component = fixture.componentInstance;
  });

  it('computes structured utilization metrics correctly', () => {
    fixture.componentRef.setInput('group', mockGroup);
    fixture.detectChanges();

    const u = component.utilization();
    expect(u).not.toBeNull();
    expect(u?.busyCount).toBe(3);
    expect(u?.busyPct).toBe(30);
    expect(u?.busyWidth).toBe(30);
    expect(u?.idleCount).toBe(5);
    expect(u?.idlePct).toBe(50);
    expect(u?.idleWidth).toBe(50);
    expect(u?.otherCount).toBe(2);
    expect(u?.otherPct).toBe(20);
    expect(u?.otherWidth).toBe(20);
  });

  it('returns null for utilization when total is 0 or utilization is absent', () => {
    fixture.componentRef.setInput('group', {
      groupId: 'group-2',
      values: ['Host-1'],
      itemCount: 0,
      utilization: undefined,
    });
    fixture.detectChanges();

    expect(component.utilization()).toBeNull();
  });

  it('calls store.toggleGroup when group header is clicked', () => {
    fixture.componentRef.setInput('group', mockGroup);
    fixture.detectChanges();

    const headBtn = fixture.debugElement.query(By.css('.rt-group-head'));
    headBtn.nativeElement.click();

    expect(mockStore.toggleGroup).toHaveBeenCalledWith('group-1');
  });

  it('renders expanded group rows and handles row selection', () => {
    const mockRows: Row[] = [
      {id: 'dev-1', cells: [{text: {value: 'Device 1'}}]},
      {id: 'dev-2', cells: [{text: {value: 'Device 2'}}]},
    ];

    const groupState: GroupPageState = {
      loading: false,
      data: {
        rows: mockRows,
        columns: [{key: 'name', displayName: 'Device Name'}],
        rangeStart: 1,
        rangeEnd: 2,
        total: 2,
      },
    };

    fixture.componentRef.setInput('group', mockGroup);
    mockStore.openGroupIds.set(new Set(['group-1']));
    mockStore.expandedGroupPages.set(new Map([['group-1', groupState]]));
    mockStore.selectedItems.set(new Map([['dev-1', mockRows[0]]]));
    fixture.detectChanges();

    expect(component.selectionState()).toEqual({all: false, some: true});

    const rows = fixture.debugElement.queryAll(By.css('tbody tr'));
    expect(rows.length).toBe(2);
    expect(rows[0].nativeElement.classList.contains('selected-row')).toBeTrue();
    expect(
      rows[1].nativeElement.classList.contains('selected-row'),
    ).toBeFalse();

    mockStore.selectedItems.set(
      new Map([
        ['dev-1', mockRows[0]],
        ['dev-2', mockRows[1]],
      ]),
    );
    fixture.detectChanges();
    expect(component.selectionState()).toEqual({all: true, some: false});

    mockStore.selectedItems.set(new Map());
    fixture.detectChanges();
    expect(component.selectionState()).toEqual({all: false, some: false});

    const checkboxes = fixture.debugElement.queryAll(By.css('mat-checkbox'));
    checkboxes[0].triggerEventHandler('change', {});
    expect(mockStore.toggleSelectRows).toHaveBeenCalledWith(mockRows);
    checkboxes[1].triggerEventHandler('change', {});
    expect(mockStore.toggleSelectRows).toHaveBeenCalledWith([mockRows[0]]);
  });

  it('renders inner pagination with pageSize and delegates page and pageSize changes to store', () => {
    const mockRows: Row[] = [
      {id: 'dev-1', cells: [{text: {value: 'Device 1'}}]},
    ];

    const groupState: GroupPageState = {
      loading: false,
      pageSize: 50,
      data: {
        rows: mockRows,
        columns: [{key: 'name', displayName: 'Device Name'}],
        rangeStart: 1,
        rangeEnd: 10,
        total: 25,
        prevPageToken: 'token-prev',
        nextPageToken: 'token-next',
      },
    };

    fixture.componentRef.setInput('group', mockGroup);
    mockStore.openGroupIds.set(new Set(['group-1']));
    mockStore.expandedGroupPages.set(new Map([['group-1', groupState]]));
    fixture.detectChanges();

    expect(component.innerRangeText()).toBe('1–10 of 25');

    const paginationEl = fixture.debugElement.query(
      By.css('app-search-pagination'),
    );
    expect(paginationEl).not.toBeNull();
    expect(paginationEl.componentInstance.pageSize()).toBe(50);

    // Test next page click
    const nextBtn = paginationEl.query(By.css('button[title="Next page"]'));
    expect(nextBtn).not.toBeNull();
    nextBtn.nativeElement.click();
    expect(mockStore.onLoadGroupRows).toHaveBeenCalledWith(
      'group-1',
      'token-next',
    );

    // Test previous page click
    const prevBtn = paginationEl.query(By.css('button[title="Previous page"]'));
    expect(prevBtn).not.toBeNull();
    prevBtn.nativeElement.click();
    expect(mockStore.onLoadGroupRows).toHaveBeenCalledWith(
      'group-1',
      'token-prev',
    );

    // Test page size change
    paginationEl.componentInstance.pageSizeChange.emit(100);
    expect(mockStore.setGroupPageSize).toHaveBeenCalledWith('group-1', 100);
  });

  it('keeps table visible with progress bar when reloading an already populated group page', () => {
    const groupState: GroupPageState = {
      loading: true,
      data: {
        rows: [{id: 'dev-1', cells: [{text: {value: 'Device 1'}}]}],
        columns: [{key: 'name', displayName: 'Device Name'}],
        rangeStart: 1,
        rangeEnd: 1,
        total: 50,
        prevPageToken: 'token-prev',
        nextPageToken: 'token-next',
      },
    };

    fixture.componentRef.setInput('group', mockGroup);
    mockStore.openGroupIds.set(new Set(['group-1']));
    mockStore.expandedGroupPages.set(new Map([['group-1', groupState]]));
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.rt-group-loading'))).toBeNull();
    expect(
      fixture.debugElement.query(By.css('.inner-table-progress-bar')),
    ).not.toBeNull();
    const scrollEl = fixture.debugElement.query(By.css('.rt-group-scroll'));
    expect(
      scrollEl.nativeElement.classList.contains('stale-loading'),
    ).toBeTrue();

    const nextBtn = fixture.debugElement.query(
      By.css('button[title="Next page"]'),
    );
    const prevBtn = fixture.debugElement.query(
      By.css('button[title="Previous page"]'),
    );
    expect(nextBtn.nativeElement.disabled).toBeTrue();
    expect(prevBtn.nativeElement.disabled).toBeTrue();
  });

  it('renders loading state when groupState is undefined or loading is true', () => {
    fixture.componentRef.setInput('group', mockGroup);
    mockStore.openGroupIds.set(new Set(['group-1']));
    fixture.detectChanges();

    let loadingEl = fixture.debugElement.query(By.css('.rt-group-loading'));
    expect(loadingEl).not.toBeNull();
    expect(loadingEl.nativeElement.textContent).toContain('Loading devices...');

    mockStore.expandedGroupPages.set(new Map([['group-1', {loading: true}]]));
    fixture.detectChanges();

    loadingEl = fixture.debugElement.query(By.css('.rt-group-loading'));
    expect(loadingEl).not.toBeNull();
  });

  it('renders error message when groupState has an error', () => {
    fixture.componentRef.setInput('group', mockGroup);
    mockStore.openGroupIds.set(new Set(['group-1']));
    mockStore.expandedGroupPages.set(
      new Map([
        [
          'group-1',
          {
            loading: false,
            error: 'Network connection failed',
          },
        ],
      ]),
    );
    fixture.detectChanges();

    const errorEl = fixture.debugElement.query(By.css('.rt-group-error-msg'));
    expect(errorEl).not.toBeNull();
    expect(errorEl.nativeElement.textContent).toContain(
      'Network connection failed',
    );
  });

  it('renders an info icon in the header only for columns with a description', () => {
    const groupState: GroupPageState = {
      loading: false,
      data: {
        rows: [
          {id: 'dev-1', cells: [{text: {value: 'D1'}}, {text: {value: '16'}}]},
        ],
        columns: [
          {key: 'device_field::uuid', displayName: 'UUID'},
          {key: 'device_field::version', displayName: 'Version'},
        ],
      },
    };

    fixture.componentRef.setInput('group', mockGroup);
    mockStore.openGroupIds.set(new Set(['group-1']));
    mockStore.expandedGroupPages.set(new Map([['group-1', groupState]]));
    mockStore.columnDescriptions.set(
      new Map([['device_field::version', 'The device OS version.']]),
    );
    fixture.detectChanges();

    const headers = fixture.debugElement.queryAll(By.css('thead th'));
    // The first header is the select-all checkbox column.
    expect(getComputedStyle(headers[1].nativeElement).textTransform).toBe(
      'none',
    );
    expect(
      headers[1].query(By.css('.rt-header-name')).nativeElement.textContent,
    ).toBe('UUID');
    expect(headers[1].query(By.css('.rt-header-info'))).toBeNull();
    expect(
      headers[2].query(By.css('.rt-header-name')).nativeElement.textContent,
    ).toBe('Version');
    const info = headers[2].query(By.css('.rt-header-info'));
    expect(info).not.toBeNull();
    expect(info.nativeElement.getAttribute('aria-label')).toBe(
      'The device OS version.',
    );
  });

  it('renders empty message when group has no rows and falls back to store displayColumns when group columns are empty', () => {
    fixture.componentRef.setInput('group', mockGroup);
    mockStore.displayColumns.set([{key: 'id', displayName: 'Device ID'}]);
    mockStore.openGroupIds.set(new Set(['group-1']));
    mockStore.expandedGroupPages.set(
      new Map([
        [
          'group-1',
          {
            loading: false,
            data: {
              rows: [],
              columns: [],
            },
          },
        ],
      ]),
    );
    fixture.detectChanges();

    expect(component.innerColumns()).toEqual([
      {key: 'id', displayName: 'Device ID'},
    ]);
    expect(component.rowItems().length).toBe(0);
    const emptyEl = fixture.debugElement.query(By.css('.rt-group-empty'));
    expect(emptyEl).not.toBeNull();
    expect(emptyEl.nativeElement.textContent).toContain(
      'No devices in this group.',
    );
  });

  it('renders sort icon on active sort column and calls store.toggleGroupRowSort on header click', () => {
    const groupState: GroupPageState = {
      loading: false,
      sortColumn: 'device_field::status',
      sortAsc: false,
      data: {
        rows: [
          {
            id: 'dev-1',
            cells: [{text: {value: 'D1'}}, {text: {value: 'IDLE'}}],
          },
        ],
        columns: [
          {key: 'device_field::uuid', displayName: 'UUID'},
          {
            key: 'device_field::status',
            displayName: 'Status',
            description: 'Current status.',
          },
        ],
      },
    };

    fixture.componentRef.setInput('group', mockGroup);
    mockStore.openGroupIds.set(new Set(['group-1']));
    mockStore.expandedGroupPages.set(new Map([['group-1', groupState]]));
    fixture.detectChanges();

    const headers = fixture.debugElement.queryAll(By.css('thead th'));
    // headers[0] is checkbox, headers[1] is UUID, headers[2] is Status
    expect(headers[1].query(By.css('.rt-sort-icon'))).toBeNull();
    const statusSortIcon = headers[2].query(By.css('.rt-sort-icon'));
    expect(statusSortIcon).not.toBeNull();
    expect(statusSortIcon.nativeElement.textContent.trim()).toBe(
      'arrow_downward',
    );

    // Clicking the info icon should not call toggleGroupRowSort
    const infoIcon = headers[2].query(By.css('.rt-header-info'));
    infoIcon.nativeElement.click();
    expect(mockStore.toggleGroupRowSort).not.toHaveBeenCalled();

    // Clicking the column header calls toggleGroupRowSort
    headers[1].nativeElement.click();
    expect(mockStore.toggleGroupRowSort).toHaveBeenCalledWith(
      'group-1',
      'device_field::uuid',
    );
  });
});
