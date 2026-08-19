import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of, Subject} from 'rxjs';

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
        {link: {text: 't1', target: {test: {testId: 't1'}}}},
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

  beforeEach(async () => {
    deviceServiceSpy = jasmine.createSpyObj<DeviceService>('DeviceService', [
      'getDeviceTestHistory',
    ]);
    deviceServiceSpy.getDeviceTestHistory.and.returnValue(of(FIRST_PAGE));

    await TestBed.configureTestingModule({
      imports: [TestHistoryTab, NoopAnimationsModule],
      providers: [{provide: DEVICE_SERVICE, useValue: deviceServiceSpy}],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHistoryTab);
    component = fixture.componentInstance;
    component.deviceId = 'device-1';
    fixture.detectChanges();
  });

  it('loads the first page on init', () => {
    // The fake service emits synchronously via of(), so ngOnInit (triggered by
    // detectChanges in beforeEach) has already populated the signals.
    expect(deviceServiceSpy.getDeviceTestHistory).toHaveBeenCalledWith(
      'device-1',
      '',
    );
    expect(component.rows().length).toBe(1);
    expect(component.hasNextPage()).toBeTrue();
    expect(component.canPrev()).toBeFalse();
  });

  it('builds the test detail link as http://mhfe/<test_id>', () => {
    expect(
      component.linkHref({text: 't1', target: {test: {testId: 't1'}}}),
    ).toBe('http://mhfe/t1');
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
    const refreshSubject = new Subject<void>();
    testComponent.refreshTrigger$ = refreshSubject.asObservable();
    testFixture.detectChanges();

    deviceServiceSpy.getDeviceTestHistory.calls.reset();
    refreshSubject.next();

    expect(deviceServiceSpy.getDeviceTestHistory).toHaveBeenCalledWith(
      'device-1',
      '',
    );
    expect(testComponent.canPrev()).toBeFalse();
  });
});
