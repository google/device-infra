import {ChangeDetectionStrategy, Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
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
          type: 'AndroidRealDevice',
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
});
