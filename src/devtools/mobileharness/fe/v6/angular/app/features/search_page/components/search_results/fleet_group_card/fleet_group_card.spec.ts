import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {FleetGroup, Row} from '../../../../../core/models/search';
import {FleetGroupCardComponent, GroupPageState} from './fleet_group_card';

describe('FleetGroupCardComponent', () => {
  let component: FleetGroupCardComponent;
  let fixture: ComponentFixture<FleetGroupCardComponent>;

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
    await TestBed.configureTestingModule({
      imports: [FleetGroupCardComponent, NoopAnimationsModule],
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

  it('emits toggleGroup output when group header is clicked', () => {
    fixture.componentRef.setInput('group', mockGroup);
    fixture.detectChanges();

    let toggledId = '';
    component.toggleGroup.subscribe((id) => {
      toggledId = id;
    });

    const headBtn = fixture.debugElement.query(By.css('.rt-group-head'));
    headBtn.nativeElement.click();

    expect(toggledId).toBe('group-1');
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
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('groupState', groupState);
    fixture.componentRef.setInput('selectedSet', new Set(['dev-1']));
    fixture.detectChanges();

    expect(component.isGroupPageAllSelected()).toBeFalse();
    expect(component.isGroupPageSomeSelected()).toBeTrue();

    const rows = fixture.debugElement.queryAll(By.css('tbody tr'));
    expect(rows.length).toBe(2);
    expect(rows[0].nativeElement.classList.contains('selected-row')).toBeTrue();
    expect(
      rows[1].nativeElement.classList.contains('selected-row'),
    ).toBeFalse();

    fixture.componentRef.setInput('selectedSet', new Set(['dev-1', 'dev-2']));
    fixture.detectChanges();
    expect(component.isGroupPageAllSelected()).toBeTrue();
    expect(component.isGroupPageSomeSelected()).toBeFalse();

    fixture.componentRef.setInput('selectedSet', new Set());
    fixture.detectChanges();
    expect(component.isGroupPageAllSelected()).toBeFalse();
    expect(component.isGroupPageSomeSelected()).toBeFalse();
  });

  it('renders inner pagination and emits loadGroupPage output on page navigation', () => {
    const mockRows: Row[] = [
      {id: 'dev-1', cells: [{text: {value: 'Device 1'}}]},
    ];

    const groupState: GroupPageState = {
      loading: false,
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
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('groupState', groupState);
    fixture.detectChanges();

    expect(component.innerRangeText()).toBe('1–10 of 25');

    const paginationEl = fixture.debugElement.query(
      By.css('app-search-pagination'),
    );
    expect(paginationEl).not.toBeNull();

    let emittedToken = '';
    component.loadGroupPage.subscribe((token) => {
      emittedToken = token || '';
    });

    // Test next page click
    const nextBtn = paginationEl.query(By.css('button[title="Next page"]'));
    expect(nextBtn).not.toBeNull();
    nextBtn.nativeElement.click();
    expect(emittedToken).toBe('token-next');

    // Test previous page click
    const prevBtn = paginationEl.query(By.css('button[title="Previous page"]'));
    expect(prevBtn).not.toBeNull();
    prevBtn.nativeElement.click();
    expect(emittedToken).toBe('token-prev');
  });
});
