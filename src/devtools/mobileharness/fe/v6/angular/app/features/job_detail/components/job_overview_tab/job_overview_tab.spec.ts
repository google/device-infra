import {ChangeDetectionStrategy, Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {
  JobOverviewData,
  JobResult,
  JobStatus,
  TestResult,
  TestStatus,
} from '../../../../core/models/job_overview';
import {JobOverviewTab} from './job_overview_tab';

@Component({
  standalone: true,
  imports: [JobOverviewTab],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-job-overview-tab [job]="job"></app-job-overview-tab>`,
})
class TestHostComponent {
  job!: JobOverviewData;
}

describe('JobOverviewTab Component', () => {
  const mockJob: JobOverviewData = {
    id: 'job_12345',
    name: 'Sample Job Execution',
    status: JobStatus.JOB_STATUS_DONE,
    result: JobResult.JOB_RESULT_PASS,
    executionDetails: {
      user: 'qiupingf',
      actualUser: 'qiupingf@google.com',
      createTime: '2026-06-08T03:00:00Z',
      startTime: '2026-06-08T03:01:00Z',
      endTime: '2026-06-08T03:05:00Z',
      updateTime: '2026-06-08T03:05:00Z',
    },
    config: {
      devices: {
        device: [
          {
            deviceType: 'AndroidRealDevice',
            driver: 'AndroidInstrumentation',
            decorators: ['AndroidAdbShell'],
            dimensions: {
              'label': 'pixel_8',
              'pool': 'shared',
            },
          },
        ],
      },
      params: {
        'test_timeout': '300',
      },
    },
    properties: {
      'prop_key_1': 'prop_val_1',
      'prop_key_2': 'prop_val_2',
    },
    tests: {
      test: [
        {
          id: 'test_101',
          name: 'Test Alpha',
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
          startTime: '2026-06-08T03:01:00Z',
          endTime: '2026-06-08T03:03:00Z',
          host: {
            name: 'host-01.example.com',
            ip: '192.168.1.1',
          },
          devices: {
            device: [{id: 'dev_01'}],
          },
        },
      ],
    },
  };

  let fixture: ComponentFixture<TestHostComponent>;
  let component: JobOverviewTab;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JobOverviewTab, TestHostComponent, NoopAnimationsModule],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    fixture.componentInstance.job = mockJob;
    fixture.detectChanges();
    component = fixture.debugElement.query(
      By.directive(JobOverviewTab),
    ).componentInstance;
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('should filter properties when propertiesSearchTerm is set', () => {
    expect(component.filteredProperties().length).toBe(2);

    component.propertiesSearchTerm.set('prop_key_1');
    expect(component.filteredProperties()).toEqual([
      {key: 'prop_key_1', value: 'prop_val_1'},
    ]);
  });

  it('should calculate timestamp info map correctly', () => {
    const map = component.timestampInfoMap();
    expect(map['createTime'].displayValue).not.toBe('N/A');
    expect(map['startTime'].displayValue).not.toBe('N/A');
    expect(map['endTime'].displayValue).not.toBe('N/A');
  });

  it('should handle single device configuration correctly', () => {
    expect(component.isMultiDevice()).toBeFalse();
    expect(component.hasDimensions()).toBeTrue();
    expect(component.deviceRequirementsBasic()).toEqual({
      'Device Type': 'AndroidRealDevice',
      'Driver': 'AndroidInstrumentation',
      'Decorator(s)': 'AndroidAdbShell',
    });
  });

  it('should format test duration correctly', () => {
    const test = mockJob.tests!.test![0];
    expect(component.getTestDuration(test)).toBe('2m 0s');
  });

  it('should return correct test result badge and test start time', () => {
    const test = mockJob.tests!.test![0];
    const badge = component.getTestResultBadge(test);
    expect(badge?.text).toBe('Pass');
    expect(component.getTestStartTime(test)).not.toBe('N/A');
  });
});
