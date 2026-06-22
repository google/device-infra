import {ChangeDetectionStrategy, Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {TestOverviewData} from '../../../../core/models/test_overview';
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
    status: 'DONE',
    result: 'PASS',
    user: 'qiupingf',
    actualUser: 'qiupingf@google.com',
    jobId: 'job_456',
    jobName: 'My Mobileharness Job',
    jobStatus: 'Done',
    jobResult: 'PASS',
    deviceId: 'device_01',
    deviceType: 'AndroidRealDevice',
    hostName: 'host-01.example.com',
    hostIp: '192.168.1.1',
    createTime: '2026-06-08T03:00:00Z',
    startTime: '2026-06-08T03:01:00Z',
    endTime: '2026-06-08T03:05:00Z',
    properties: {
      'custom_prop_1': 'val1',
      'custom_prop_2': 'val2',
    },
    warnings: [],
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
      error: {message: 'Fatal test crash', trace: 'Trace line 1'},
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

  it('should return DONE for passed test status', () => {
    expect(component.getStatusPillLabel(mockTest)).toBe('DONE');
  });

  it('should show warning details card and list in nav if warnings are present', () => {
    const warningTest: TestOverviewData = {
      ...mockTest,
      warnings: [
        'Logcat daemon restarted',
        'WARNING 1',
        'WARNING 2',
      ],
    };
    fixture.componentInstance.test = warningTest;
    fixture.detectChanges();

    expect(component.overviewNavList()).toEqual([
      {id: 'overview-warning', label: 'Warning Details'},
      {id: 'overview-execution', label: 'Execution Details'},
      {id: 'overview-properties', label: 'Test Properties'},
    ]);

    expect(component.warningTitle()).toBe('Logcat daemon restarted');
    expect(component.warningDetailsText()).toBe('WARNING 1\nWARNING 2');
  });
});
