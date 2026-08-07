import {ChangeDetectionStrategy, Component} from '@angular/core';
import {
  ComponentFixture,
  TestBed,
  discardPeriodicTasks,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of} from 'rxjs';

import {JobStatus} from '../../../../core/models/job_overview';
import {
  JOB_SERVICE,
  JobService,
} from '../../../../core/services/job/job_service';
import {JobLogTab} from './job_log_tab';

@Component({
  standalone: true,
  imports: [JobLogTab],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-job-log-tab [jobId]="jobId" [cloudLogLink]="cloudLogLink" [initialStatus]="initialStatus"></app-job-log-tab>`,
})
class TestHostComponent {
  jobId = 'job_123';
  cloudLogLink = 'http://cloud-log-link';
  initialStatus = JobStatus.JOB_STATUS_DONE;
}

describe('JobLogTab Component', () => {
  describe('with DONE job', () => {
    let fixture: ComponentFixture<TestHostComponent>;
    let component: JobLogTab;
    let mockJobService: jasmine.SpyObj<JobService>;

    beforeEach(async () => {
      mockJobService = jasmine.createSpyObj('JobService', ['getJobLog']);
      mockJobService.getJobLog.and.returnValue(
        of({
          logContent: 'Log Content 1\nLog Content 2',
          nextOffset: 27,
          jobStatus: JobStatus.JOB_STATUS_DONE,
          logReset: false,
          contentHash: 'hash-done',
        }),
      );

      await TestBed.configureTestingModule({
        imports: [JobLogTab, TestHostComponent, NoopAnimationsModule],
        providers: [{provide: JOB_SERVICE, useValue: mockJobService}],
      }).compileComponents();

      fixture = TestBed.createComponent(TestHostComponent);
      fixture.componentInstance.jobId = 'job_123';
      fixture.detectChanges();
      component = fixture.debugElement.query(
        By.directive(JobLogTab),
      ).componentInstance;
    });

    it('should be created', () => {
      expect(component).toBeTruthy();
    });

    it('should fetch logs and bind outputs correctly', () => {
      expect(mockJobService.getJobLog).toHaveBeenCalledWith({
        jobId: 'job_123',
        offset: 0,
        contentHash: undefined,
      });
      expect(component.logLines()).toEqual(['Log Content 1', 'Log Content 2']);
      expect(component.cloudLogLink()).toBe('http://cloud-log-link');
      expect(component.logViewport()).toBeTruthy();
    });
  });

  describe('with RUNNING job', () => {
    let runningFixture: ComponentFixture<TestHostComponent>;
    let runningComponent: JobLogTab;
    let runningMockJobService: jasmine.SpyObj<JobService>;

    beforeEach(async () => {
      runningMockJobService = jasmine.createSpyObj('JobService', ['getJobLog']);

      let calls = 0;
      runningMockJobService.getJobLog.and.callFake(() => {
        calls++;
        if (calls === 1) {
          return of({
            logContent: 'Line 1\nLine 2\n',
            nextOffset: 13,
            jobStatus: JobStatus.JOB_STATUS_RUNNING,
            logReset: false,
            contentHash: 'hash-running-1',
          });
        }
        return of({
          logContent: 'Line 3\nLine 4\n',
          nextOffset: 27,
          jobStatus: JobStatus.JOB_STATUS_RUNNING,
          logReset: false,
          contentHash: 'hash-running-2',
        });
      });

      await TestBed.configureTestingModule({
        imports: [JobLogTab, TestHostComponent, NoopAnimationsModule],
        providers: [{provide: JOB_SERVICE, useValue: runningMockJobService}],
      }).compileComponents();

      runningFixture = TestBed.createComponent(TestHostComponent);
      runningFixture.componentInstance.jobId = 'job_running';
      runningFixture.componentInstance.initialStatus =
        JobStatus.JOB_STATUS_RUNNING;
      runningComponent = runningFixture.debugElement.query(
        By.directive(JobLogTab),
      ).componentInstance;
    });

    it('should poll and append logs', fakeAsync(() => {
      runningFixture.detectChanges();

      expect(runningComponent.logLines()).toEqual(['Line 1', 'Line 2']);

      tick(5000);

      expect(runningComponent.logLines()).toEqual([
        'Line 1',
        'Line 2',
        'Line 3',
        'Line 4',
      ]);

      discardPeriodicTasks();
    }));
  });
});
