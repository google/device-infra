import {ChangeDetectionStrategy, Component} from '@angular/core';
import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {
  JobResult,
  JobStatus,
  TestOverviewData,
  TestResult,
  TestStatus,
} from '../../../../core/models/test_overview';
import {AccordionItem} from '../../../../shared/components/accordion_item/accordion_item';
import {NavLink} from '../../../../shared/components/nav_link/nav_link';
import {getTestSummaryResultBadge} from '../../../../shared/composables/status_badge';
import {ClipboardService} from '../../../../shared/services/clipboard_service';
import {dateUtils} from '../../../../shared/utils/date_utils';
import {TestOverviewTab} from './test_overview_tab';

@Component({
  standalone: true,
  imports: [TestOverviewTab],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-test-overview-tab [test]="test"></app-test-overview-tab>`,
})
class TestHostComponent {
  test!: TestOverviewData;
}

describe('TestOverviewTab Component', () => {
  const mockTest: TestOverviewData = {
    id: 'test_123',
    name: 'Example test execution',
    status: TestStatus.TEST_STATUS_DONE,
    result: TestResult.TEST_RESULT_PASS,
    job: {
      id: 'job_456',
      name: 'My Mobileharness Job',
      status: JobStatus.JOB_STATUS_DONE,
      result: JobResult.JOB_RESULT_PASS,
      spongeLink: 'http://sponge/mock-job-link',
    },
    devices: {
      device: [
        {
          id: 'device_01',
        },
      ],
    },
    host: {
      name: 'host-01.example.com',
      ip: '192.168.1.1',
    },
    executionDetails: {
      user: 'qiupingf',
      actualUser: 'qiupingf@google.com',
      createTime: '2026-06-08T03:00:00Z',
      startTime: '2026-06-08T03:01:00Z',
      endTime: '2026-06-08T03:05:00Z',
      updateTime: '2026-06-08T03:05:00Z',
    },
    properties: {
      'custom_prop_1': 'val1',
      'custom_prop_2': 'val2',
    },
  };

  let fixture: ComponentFixture<TestHostComponent>;
  let component: TestOverviewTab;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestOverviewTab, TestHostComponent, NoopAnimationsModule],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    fixture.componentInstance.test = mockTest;
    fixture.detectChanges();
    component = fixture.debugElement.query(
      By.directive(TestOverviewTab),
    ).componentInstance;
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('should list all overview nav items', () => {
    expect(component.overviewNavList()).toEqual([
      {id: 'overview-execution', label: 'Execution Details'},
      {id: 'overview-properties', label: 'Test Properties'},
    ]);
  });

  it('should show error details card if test error is present', () => {
    const errorTest: TestOverviewData = {
      ...mockTest,
      troubleshooting: {
        resultCause: {
          error: [{message: 'Fatal test crash', trace: 'Trace line 1'}],
        },
      },
    };
    fixture.componentInstance.test = errorTest;
    fixture.detectChanges();

    expect(component.overviewNavList()).toEqual([
      {id: 'overview-error', label: 'Error Details'},
      {id: 'overview-execution', label: 'Execution Details'},
      {id: 'overview-properties', label: 'Test Properties'},
    ]);
  });

  it('should filter properties based on search term', () => {
    component.propertiesSearchTerm.set('custom_prop_1');
    fixture.detectChanges();
    expect(component.filteredProperties()).toEqual([
      {key: 'custom_prop_1', value: 'val1'},
    ]);
  });

  it('should show warning details card and list in nav if warnings are present', () => {
    const warningTest: TestOverviewData = {
      ...mockTest,
      troubleshooting: {
        warnings: {
          warning: [
            {message: 'Logcat daemon restarted'},
            {message: 'WARNING 1'},
            {message: 'WARNING 2'},
          ],
        },
      },
    };
    fixture.componentInstance.test = warningTest;
    fixture.detectChanges();

    expect(component.overviewNavList()).toEqual([
      {id: 'overview-warning', label: 'Warning Details'},
      {id: 'overview-execution', label: 'Execution Details'},
      {id: 'overview-properties', label: 'Test Properties'},
    ]);
  });

  it('should support multiple errors and warnings with count badges and accordion expansion toggle', () => {
    const multiTest: TestOverviewData = {
      ...mockTest,
      troubleshooting: {
        resultCause: {
          error: [
            {message: 'Error 1', trace: 'Trace 1'},
            {message: 'Error 2', trace: 'Trace 2'},
          ],
        },
        warnings: {
          warning: [{message: 'Warning 1'}, {message: 'Warning 2'}],
        },
      },
    };
    fixture.componentInstance.test = multiTest;
    fixture.detectChanges();

    expect(component.overviewNavList()).toEqual([
      {id: 'overview-error', label: 'Error Details'},
      {id: 'overview-warning', label: 'Warning Details'},
      {id: 'overview-execution', label: 'Execution Details'},
      {id: 'overview-properties', label: 'Test Properties'},
    ]);

    // Check count badges rendered in DOM
    const errorBadge = fixture.debugElement.query(By.css('.error-count-badge'));
    expect(errorBadge.nativeElement.textContent.trim()).toBe('2 errors');

    const warningBadge = fixture.debugElement.query(
      By.css('.warning-count-badge'),
    );
    expect(warningBadge.nativeElement.textContent.trim()).toBe('2 warnings');

    // Verify default signal expansion states on AccordionItem components (index 0 is expanded by default)
    const accordionItems = fixture.debugElement.queryAll(
      By.directive(AccordionItem),
    );
    expect(accordionItems.length).toBe(4); // 2 errors + 2 warnings

    const errorItem0 = accordionItems[0].componentInstance as AccordionItem;
    const errorItem1 = accordionItems[1].componentInstance as AccordionItem;
    expect(errorItem0.expanded()).toBeTrue();
    expect(errorItem1.expanded()).toBeFalse();

    // Toggle error index 1 and check signal updates
    errorItem1.toggle();
    expect(errorItem1.expanded()).toBeTrue();
    errorItem0.toggle();
    expect(errorItem0.expanded()).toBeFalse();

    // Verify warning accordion toggling
    const warningItem0 = accordionItems[2].componentInstance as AccordionItem;
    const warningItem1 = accordionItems[3].componentInstance as AccordionItem;
    expect(warningItem0.expanded()).toBeTrue();
    expect(warningItem1.expanded()).toBeFalse();
    warningItem1.toggle();
    expect(warningItem1.expanded()).toBeTrue();
    fixture.detectChanges();

    // Verify ARIA accessibility attributes on triggers
    const errorTrigger1 = fixture.debugElement.query(
      By.css('#error-panel-1-trigger'),
    );
    expect(errorTrigger1.nativeElement.getAttribute('aria-expanded')).toBe(
      'true',
    );
    expect(errorTrigger1.nativeElement.getAttribute('aria-controls')).toBe(
      'error-panel-1',
    );

    const errorPanel1 = fixture.debugElement.query(By.css('#error-panel-1'));
    expect(errorPanel1.nativeElement.getAttribute('role')).toBe('region');
    expect(errorPanel1.nativeElement.getAttribute('aria-labelledby')).toBe(
      'error-panel-1-trigger',
    );
  });

  it('should show sub-tests card and nav item when subTestsInfo has sub-tests', () => {
    const testWithSubTests: TestOverviewData = {
      ...mockTest,
      subTestsInfo: {
        rootTestId: 'test_123',
        subTests: {
          test: [
            {
              id: 'sub_01',
              name: 'SubTestCase1',
              status: TestStatus.TEST_STATUS_DONE,
              result: TestResult.TEST_RESULT_PASS,
            },
          ],
        },
      },
    };
    fixture.componentInstance.test = testWithSubTests;
    fixture.detectChanges();

    expect(component.overviewNavList()).toEqual([
      {id: 'test-sub-tests', label: 'Sub-tests'},
      {id: 'overview-execution', label: 'Execution Details'},
      {id: 'overview-properties', label: 'Test Properties'},
    ]);

    const subTestsCard = fixture.debugElement.query(By.css('#test-sub-tests'));
    expect(subTestsCard).not.toBeNull();
  });

  it('shows the MOSS result of a sub-test whose status stays NEW and has no Status column', () => {
    const testWithSubTests: TestOverviewData = {
      ...mockTest,
      subTestsInfo: {
        rootTestId: 'test_123',
        subTests: {
          test: [
            {
              id: 'sub_01',
              name: 'MoblyTest',
              status: TestStatus.TEST_STATUS_NEW,
              result: TestResult.TEST_RESULT_PASS,
            },
          ],
        },
      },
    };
    fixture.componentInstance.test = testWithSubTests;
    fixture.detectChanges();

    const subTestsCard = fixture.debugElement.query(By.css('#test-sub-tests'));
    expect(subTestsCard.query(By.css('th.mat-column-status'))).toBeNull();
    const resultCell = subTestsCard.query(By.css('td.mat-column-result'));
    expect(resultCell.nativeElement.textContent).toContain('Pass');
    expect(resultCell.nativeElement.textContent).not.toContain('N/A');
  });

  it('binds sub-test Host and Device NavLink configs with universe only when labUniverse is set', () => {
    const subTestWithHostAndDevices = {
      id: 'sub_01',
      name: 'MoblyTest',
      status: TestStatus.TEST_STATUS_DONE,
      result: TestResult.TEST_RESULT_PASS,
      host: {
        name: 'sub-host.example.com',
        ip: '10.0.0.2',
      },
      devices: {
        device: [{id: 'sub-dev-1'}],
      },
    };
    const emptyUniverseFixture = TestBed.createComponent(TestHostComponent);
    emptyUniverseFixture.componentInstance.test = {
      ...mockTest,
      labUniverse: '',
      subTestsInfo: {
        rootTestId: 'test_123',
        subTests: {test: [subTestWithHostAndDevices]},
      },
    };
    emptyUniverseFixture.detectChanges();

    const emptySubTestsCard = emptyUniverseFixture.debugElement.query(
      By.css('#test-sub-tests'),
    );
    const emptyHostLink = emptySubTestsCard
      .query(By.css('td.mat-column-host a'))
      .injector.get(NavLink);
    const emptyDeviceLink = emptySubTestsCard
      .query(By.css('td.mat-column-device a'))
      .injector.get(NavLink);
    expect(emptyHostLink.config()).toEqual({
      type: 'host',
      hostName: 'sub-host.example.com',
      hostIp: '10.0.0.2',
    });
    expect(emptyDeviceLink.config()).toEqual({
      type: 'device',
      hostName: 'sub-host.example.com',
      hostIp: '10.0.0.2',
      deviceId: 'sub-dev-1',
    });

    const atsUniverseFixture = TestBed.createComponent(TestHostComponent);
    atsUniverseFixture.componentInstance.test = {
      ...mockTest,
      labUniverse: 'ats_1',
      subTestsInfo: {
        rootTestId: 'test_123',
        subTests: {test: [subTestWithHostAndDevices]},
      },
    };
    atsUniverseFixture.detectChanges();

    const atsSubTestsCard = atsUniverseFixture.debugElement.query(
      By.css('#test-sub-tests'),
    );
    const atsHostLink = atsSubTestsCard
      .query(By.css('td.mat-column-host a'))
      .injector.get(NavLink);
    const atsDeviceLink = atsSubTestsCard
      .query(By.css('td.mat-column-device a'))
      .injector.get(NavLink);
    expect(atsHostLink.config()).toEqual({
      type: 'host',
      hostName: 'sub-host.example.com',
      hostIp: '10.0.0.2',
      universe: 'ats_1',
    });
    expect(atsDeviceLink.config()).toEqual({
      type: 'device',
      hostName: 'sub-host.example.com',
      hostIp: '10.0.0.2',
      deviceId: 'sub-dev-1',
      universe: 'ats_1',
    });
  });

  it('computes sub-test duration, start time, devices list, and result badge across edge cases', () => {
    expect(
      component.getTestDuration({
        id: 's1',
        name: 's1',
        status: TestStatus.TEST_STATUS_DONE,
        result: TestResult.TEST_RESULT_PASS,
      }),
    ).toBe('N/A');

    expect(
      component.getTestDuration({
        id: 's2',
        name: 's2',
        status: TestStatus.TEST_STATUS_DONE,
        result: TestResult.TEST_RESULT_PASS,
        startTime: '2026-06-08T03:00:00Z',
        endTime: '2026-06-08T03:00:45Z',
      }),
    ).toBe('45s');

    expect(
      component.getTestDuration({
        id: 's3',
        name: 's3',
        status: TestStatus.TEST_STATUS_DONE,
        result: TestResult.TEST_RESULT_PASS,
        startTime: '2026-06-08T03:00:00Z',
        endTime: '2026-06-08T03:02:05Z',
      }),
    ).toBe('2m 5s');

    expect(
      component.getTestDuration({
        id: 's4',
        name: 's4',
        status: TestStatus.TEST_STATUS_DONE,
        result: TestResult.TEST_RESULT_PASS,
        startTime: '2026-06-08T03:00:00Z',
        endTime: '2026-06-08T04:02:03Z',
      }),
    ).toBe('1h 2m 3s');

    expect(
      component.getTestDuration({
        id: 's5',
        name: 's5',
        status: TestStatus.TEST_STATUS_DONE,
        result: TestResult.TEST_RESULT_PASS,
        startTime: '2026-06-08T04:00:00Z',
        endTime: '2026-06-08T03:00:00Z',
      }),
    ).toBe('N/A');

    expect(
      component.getTestDuration({
        id: 's6',
        name: 's6',
        status: TestStatus.TEST_STATUS_DONE,
        result: TestResult.TEST_RESULT_PASS,
        startTime: 'June 8, 2026 03:00:00 UTC',
        endTime: 'June 8, 2026 03:00:30 UTC',
      }),
    ).toBe('30s');

    expect(
      component.getTestDuration({
        id: 's7',
        name: 's7',
        status: TestStatus.TEST_STATUS_DONE,
        result: TestResult.TEST_RESULT_PASS,
        startTime: 'not-a-date',
        endTime: 'also-not-a-date',
      }),
    ).toBe('N/A');

    expect(
      component.getTestDevicesList({
        id: 's1',
        name: 's1',
        status: TestStatus.TEST_STATUS_DONE,
        result: TestResult.TEST_RESULT_PASS,
        devices: {device: [{id: 'dev-1'}, {id: 'dev-2'}]},
      }),
    ).toEqual(['dev-1', 'dev-2']);

    expect(
      component.getTestDevicesList({
        id: 's2',
        name: 's2',
        status: TestStatus.TEST_STATUS_DONE,
        result: TestResult.TEST_RESULT_PASS,
      }),
    ).toEqual([]);

    expect(
      component.getTestResultBadge({
        id: 's1',
        name: 's1',
        status: TestStatus.TEST_STATUS_DONE,
        result: TestResult.TEST_RESULT_UNSPECIFIED,
      }),
    ).toBeNull();

    expect(
      getTestSummaryResultBadge(
        {
          id: 's1',
          name: 's1',
          status: TestStatus.TEST_STATUS_RUNNING,
          result: TestResult.TEST_RESULT_PASS,
        },
        {requireDoneStatus: true},
      ),
    ).toBeNull();

    expect(
      getTestSummaryResultBadge(
        {
          id: 's1',
          name: 's1',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_UNSPECIFIED,
        },
        {requireDoneStatus: true},
      ),
    ).toBeNull();

    expect(
      getTestSummaryResultBadge(
        {
          id: 's1',
          name: 's1',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
        },
        {requireDoneStatus: true},
      )?.text,
    ).toBe('Pass');

    expect(
      component.getTestStartTime({
        id: 's1',
        name: 's1',
        status: TestStatus.TEST_STATUS_DONE,
        result: TestResult.TEST_RESULT_PASS,
      }),
    ).toBe('N/A');

    expect(
      component.getTestStartTime({
        id: 's2',
        name: 's2',
        status: TestStatus.TEST_STATUS_DONE,
        result: TestResult.TEST_RESULT_PASS,
        startTime: 'invalid-timestamp',
      }),
    ).toBe('invalid-timestamp');

    expect(
      component.getTestStartTime({
        id: 's3',
        name: 's3',
        status: TestStatus.TEST_STATUS_DONE,
        result: TestResult.TEST_RESULT_PASS,
        startTime: '2026-06-08T03:01:00Z',
      }),
    ).toBe(dateUtils.formatDetailedLocal(new Date('2026-06-08T03:01:00Z')));

    expect(
      component.getTestStartTime({
        id: 's4',
        name: 's4',
        status: TestStatus.TEST_STATUS_DONE,
        result: TestResult.TEST_RESULT_PASS,
        startTime: '2026-06-08T05:01:00+02:00',
      }),
    ).toBe(
      dateUtils.formatDetailedLocal(new Date('2026-06-08T05:01:00+02:00')),
    );
  });

  it('tracks copied sub-test ID state and resets after 2 seconds', fakeAsync(() => {
    const clipboard = TestBed.inject(ClipboardService);
    const copySpy = spyOn(clipboard, 'copyToClipboard').and.returnValue(true);

    component.copyTestId('sub_01');
    expect(component.copiedTestIds()['sub_01']).toBeTrue();

    tick(1000);
    component.copyTestId('sub_01');
    tick(1000);
    expect(component.copiedTestIds()['sub_01']).toBeTrue();

    tick(1000);
    expect(component.copiedTestIds()['sub_01']).toBeFalse();

    copySpy.and.returnValue(false);
    component.copyTestId('sub_02');
    expect(component.copiedTestIds()['sub_02']).toBeUndefined();
  }));
});
