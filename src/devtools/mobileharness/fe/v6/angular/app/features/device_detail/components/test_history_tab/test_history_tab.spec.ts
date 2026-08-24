import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of, Subject} from 'rxjs';

import {DebugService} from '@deviceinfra/app/core/services/debug_service';
import {DeviceTestHistoryResponse} from '../../../../core/models/device_test_history';
import {
  DEVICE_SERVICE,
  DeviceService,
} from '../../../../core/services/device/device_service';
import {TestHistoryTab} from './test_history_tab';

const FIRST_PAGE: DeviceTestHistoryResponse = {
  columns: [
    {key: 'test_id', displayName: 'Test ID'},
    {key: 'status', displayName: 'Status'},
    {key: 'start_time', displayName: 'Start time'},
    {key: 'duration', displayName: 'Duration'},
  ],
  rows: [
    {
      id: 't1',
      cells: [
        {link: {text: 't1', target: {test: {testId: 't1', jobId: 'j1'}}}},
        {status: {text: 'Pass', indicator: 'INDICATOR_OK'}},
        {text: {value: '1700000000000'}},
        {text: {value: '125000'}},
      ],
    },
  ],
  nextPageToken: 'token-2',
};

describe('TestHistoryTab Component', () => {
  let fixture: ComponentFixture<TestHistoryTab>;
  let component: TestHistoryTab;
  let deviceServiceSpy: jasmine.SpyObj<DeviceService>;
  let debugServiceSpy: jasmine.SpyObj<DebugService>;

  beforeEach(async () => {
    deviceServiceSpy = jasmine.createSpyObj<DeviceService>('DeviceService', [
      'getDeviceTestHistory',
    ]);
    deviceServiceSpy.getDeviceTestHistory.and.returnValue(of(FIRST_PAGE));

    debugServiceSpy = jasmine.createSpyObj<DebugService>('DebugService', [
      'isDebug',
    ]);
    debugServiceSpy.isDebug.and.returnValue(false);

    await TestBed.configureTestingModule({
      imports: [TestHistoryTab, NoopAnimationsModule],
      providers: [
        {provide: DEVICE_SERVICE, useValue: deviceServiceSpy},
        {provide: DebugService, useValue: debugServiceSpy},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHistoryTab);
    component = fixture.componentInstance;
    component.deviceId = 'device-1';
    component.hostName = 'host-1';
    fixture.detectChanges();
  });

  it('loads the first page on init', () => {
    // The fake service emits synchronously via of(), so ngOnInit (triggered by
    // detectChanges in beforeEach) has already populated the signals.
    expect(deviceServiceSpy.getDeviceTestHistory).toHaveBeenCalledWith(
      'device-1',
      'host-1',
      '',
    );
    expect(component.rows().length).toBe(1);
    expect(component.hasNextPage()).toBeTrue();
    expect(component.canPrev()).toBeFalse();
  });

  it('builds the test nav link config', () => {
    expect(
      component.getNavLinkConfig({
        text: 't1',
        target: {test: {testId: 't1', jobId: 'j1'}},
      }),
    ).toEqual({
      type: 'test',
      jobId: 'j1',
      testId: 't1',
    });
  });

  it('returns null for unsupported link targets', () => {
    expect(
      component.getNavLinkConfig({
        text: 'j1',
        target: {job: {jobId: 'j1'}},
      }),
    ).toBeNull();

    expect(
      component.getNavLinkConfig({
        text: 'h1',
        target: {host: {hostName: 'h1', hostIp: '1.1.1.1'}},
      }),
    ).toBeNull();

    expect(
      component.getNavLinkConfig({
        text: 'd1',
        target: {device: {id: 'd1', hostName: 'h1', hostIp: '1.1.1.1'}},
      }),
    ).toBeNull();

    expect(
      component.getNavLinkConfig({
        text: 's1',
        target: {session: {sessionId: 's1'}},
      }),
    ).toBeNull();
  });

  it('formats duration and start time text cells', () => {
    expect(component.formatText('duration', '125000')).toBe('2m 5s');
    expect(component.formatText('duration', '')).toBe('-');
    expect(component.formatText('user', 'dafeng')).toBe('dafeng');
  });

  it('maps indicators to chip classes', () => {
    expect(component.indicatorClass('INDICATOR_OK')).toContain('status-ok');
    expect(component.indicatorClass('INDICATOR_ERROR')).toContain(
      'status-error',
    );
    expect(component.indicatorClass(undefined)).toContain('status-neutral');
  });

  it('reloads the first page and resets pagination when refreshTrigger emits', () => {
    const testFixture = TestBed.createComponent(TestHistoryTab);
    const testComponent = testFixture.componentInstance;
    testComponent.deviceId = 'device-1';
    testComponent.hostName = 'host-1';
    const refreshSubject = new Subject<void>();
    testComponent.refreshTrigger$ = refreshSubject.asObservable();
    testFixture.detectChanges();

    deviceServiceSpy.getDeviceTestHistory.calls.reset();
    refreshSubject.next();

    expect(deviceServiceSpy.getDeviceTestHistory).toHaveBeenCalledWith(
      'device-1',
      'host-1',
      '',
    );
    expect(testComponent.canPrev()).toBeFalse();
  });
});
