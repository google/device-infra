import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of, Subject, throwError} from 'rxjs';

import {DeviceEventsResponse} from '../../../../core/models/device_events';
import {
  DEVICE_SERVICE,
  DeviceService,
} from '../../../../core/services/device/device_service';
import {DeviceEventsTab} from './device_events_tab';

const REFERENCE_NOW_MS = new Date('2026-10-19T17:00:00-07:00').getTime();

const SAMPLE_RESPONSE: DeviceEventsResponse = {
  changes: [
    {
      changeTime: '2026-10-18T16:50:00-07:00',
      field: 'build',
      fromValue: '14952565',
      toValue: '16600061',
      meta: 'API 36 to 37',
    },
    {
      changeTime: '2026-10-18T02:10:00-07:00',
      field: 'build',
      fromValue: '16618200',
      toValue: '14952565',
      meta: 'API 38 to 36',
    },
    {
      changeTime: '2026-10-16T09:15:00-07:00',
      field: 'build',
      fromValue: '16607409',
      toValue: '16618200',
      meta: 'API 37 to 38',
    },
    {
      changeTime: '2026-10-14T23:42:00-07:00',
      field: 'status',
      fromValue: 'FAILED',
      toValue: 'IDLE',
      meta: '',
    },
    {
      changeTime: '2026-10-14T13:17:00-07:00',
      field: 'status',
      fromValue: 'BUSY',
      toValue: 'MISSING',
      meta: 'Needs manual repair',
    },
    {
      changeTime: '2026-07-22T06:15:00-07:00',
      field: 'host',
      fromValue: 'slaas5-9-10-bcpc.blr.omnilab.in.goog',
      toValue: 'slaas4-9-10-bcpc.blr.omnilab.in.goog',
      meta: '',
    },
  ],
  recoveryRecords: [
    {
      createTime: '2026-10-14T23:35:00-07:00',
      author: 'mgokul',
      recoveryAction: 'Flashed Device',
      offlineReason: 'Device Corrupt',
      note: 'Reflashed to stable build',
    },
  ],
  inventoryRecords: [
    {
      eventTime: '2026-10-13T12:10:15-07:00',
      author: 'Omnilab Omnilab',
      subject: 'Moved',
      body: 'Deployed to slaas4-9-10-bcpc.blr.omnilab.in.goog',
    },
  ],
  hasOlderEvents: true,
};

describe('DeviceEventsTab Component', () => {
  let fixture: ComponentFixture<DeviceEventsTab>;
  let component: DeviceEventsTab;
  let deviceServiceSpy: jasmine.SpyObj<DeviceService>;

  beforeEach(async () => {
    deviceServiceSpy = jasmine.createSpyObj<DeviceService>('DeviceService', [
      'getDeviceEvents',
    ]);
    deviceServiceSpy.getDeviceEvents.and.returnValue(of(SAMPLE_RESPONSE));

    await TestBed.configureTestingModule({
      imports: [DeviceEventsTab, NoopAnimationsModule],
      providers: [{provide: DEVICE_SERVICE, useValue: deviceServiceSpy}],
    }).compileComponents();

    fixture = TestBed.createComponent(DeviceEventsTab);
    component = fixture.componentInstance;
    component.deviceId = '41031FDAS00073';
    component.hostName = 'slaas4-9-10-bcpc.blr.omnilab.in.goog';
    component.referenceNowMs = REFERENCE_NOW_MS;
    fixture.detectChanges();
  });

  it('loads device events on init and computes summary and category counts', () => {
    expect(deviceServiceSpy.getDeviceEvents).toHaveBeenCalledWith(
      '41031FDAS00073',
      'slaas4-9-10-bcpc.blr.omnilab.in.goog',
      90,
    );
    expect(component.windowEvents().length).toBe(8);
    expect(component.hasOlderEvents()).toBeTrue();

    const summary = component.summary();
    expect(summary.outageValue).toBe('1 time');
    expect(summary.repairValue).toBe('1');
    expect(summary.segments.length).toBe(1);
    expect(summary.marks.length).toBe(3);
  });

  it('collapses 3 consecutive build changes into an expandable group row and renders two-style details', () => {
    const root: HTMLElement = fixture.nativeElement;
    const groupRow = root.querySelector<HTMLElement>('.de-row--group');
    expect(groupRow).not.toBeNull();
    expect(groupRow!.textContent).toContain('3 build changes');
    expect(groupRow!.textContent).toContain('16607409');
    expect(groupRow!.textContent).toContain('16600061');
    expect(root.querySelectorAll('.de-row--child').length).toBe(0);

    // Click to expand the build group row.
    groupRow!.click();
    fixture.detectChanges();
    expect(root.querySelectorAll('.de-row--child').length).toBe(3);

    // Verify two-style Details badges ('state', 'tag') and phrases ('val', 'word').
    const stateBadges = Array.from(
      root.querySelectorAll('.de-badge--state'),
    ).map((el) => el.textContent?.trim());
    expect(stateBadges).toContain('Missing');
    const tagBadges = Array.from(root.querySelectorAll('.de-badge--tag')).map(
      (el) => el.textContent?.trim(),
    );
    expect(tagBadges).toContain('Device corrupt');
    expect(tagBadges).toContain('API 36 to 37');
  });

  it('filters events by category chips and clears filters with Clear button', () => {
    component.toggleCategory('recovery');
    fixture.detectChanges();

    expect(component.visibleEvents().length).toBe(1);
    expect(component.visibleEvents()[0].title).toBe('Flashed device');

    const root: HTMLElement = fixture.nativeElement;
    const clearBtn = root.querySelector<HTMLButtonElement>(
      '[data-action="clear-chips"]',
    );
    expect(clearBtn).not.toBeNull();
    clearBtn!.click();
    fixture.detectChanges();

    expect(component.selectedCategories().size).toBe(0);
    expect(component.visibleEvents().length).toBe(8);
  });

  it('filters events by search query and loads older events on button click', () => {
    component.query.set('mgokul');
    fixture.detectChanges();
    expect(component.visibleEvents().length).toBe(1);

    component.clearSearch();
    fixture.detectChanges();
    expect(component.visibleEvents().length).toBe(8);

    component.loadOlderEvents();
    fixture.detectChanges();
    expect(deviceServiceSpy.getDeviceEvents).toHaveBeenCalledWith(
      '41031FDAS00073',
      'slaas4-9-10-bcpc.blr.omnilab.in.goog',
      180,
    );
  });

  it('reloads when refreshTrigger emits and surfaces error state on failure', () => {
    const refreshSubject = new Subject<void>();
    const testFixture = TestBed.createComponent(DeviceEventsTab);
    const testComponent = testFixture.componentInstance;
    testComponent.deviceId = 'device-2';
    testComponent.hostName = 'host-2';
    testComponent.refreshTrigger$ = refreshSubject.asObservable();
    testFixture.detectChanges();

    deviceServiceSpy.getDeviceEvents.calls.reset();
    deviceServiceSpy.getDeviceEvents.and.returnValue(
      throwError(() => new Error('RPC error')),
    );
    refreshSubject.next();
    testFixture.detectChanges();

    expect(deviceServiceSpy.getDeviceEvents).toHaveBeenCalledWith(
      'device-2',
      'host-2',
      90,
    );
    expect(testComponent.error()).toBe('Failed to load device events.');
  });
});
