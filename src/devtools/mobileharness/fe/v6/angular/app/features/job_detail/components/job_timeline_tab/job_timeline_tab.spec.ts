import {provideHttpClient} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import {ChangeDetectionStrategy, Component, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {Subject, of, throwError} from 'rxjs';

import {APP_DATA} from '../../../../core/models/app_data';
import {TimeBreakdown} from '../../../../core/models/job_overview';
import {HttpJobService} from '../../../../core/services/job/http_job_service';
import {
  JOB_SERVICE,
  JobService,
} from '../../../../core/services/job/job_service';
import {JobTimelineTab} from './job_timeline_tab';

@Component({
  standalone: true,
  imports: [JobTimelineTab],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-job-timeline-tab [jobId]="jobId()" [status]="status()"></app-job-timeline-tab>`,
})
class TestHostComponent {
  readonly jobId = signal<string>('job_456');
  readonly status = signal<string | undefined>('JOB_STATUS_RUNNING');
}

describe('JobTimelineTab Component', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let host: TestHostComponent;
  let component: JobTimelineTab;
  let mockJobService: jasmine.SpyObj<JobService>;

  beforeEach(async () => {
    mockJobService = jasmine.createSpyObj<JobService>('JobService', [
      'getJobTimeline',
    ]);
    mockJobService.getJobTimeline.and.returnValue(of({timeline: {}}));

    await TestBed.configureTestingModule({
      imports: [JobTimelineTab, TestHostComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        {provide: JOB_SERVICE, useValue: mockJobService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
    component = fixture.debugElement.query(
      By.directive(JobTimelineTab),
    ).componentInstance;
    fixture.detectChanges();
  });

  it('fetches job timeline on init, shows empty message when timing is empty, and clears when jobId is empty', () => {
    expect(component).toBeTruthy();
    expect(component.jobId()).toBe('job_456');
    expect(mockJobService.getJobTimeline).toHaveBeenCalledWith({
      jobId: 'job_456',
    });
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain(
      'No timing was recorded for this job.',
    );

    mockJobService.getJobTimeline.calls.reset();
    host.jobId.set('');
    fixture.detectChanges();
    expect(mockJobService.getJobTimeline).not.toHaveBeenCalled();
    expect(component.timingBreakdown()).toBeUndefined();
    expect(component.loading()).toBeFalse();
  });

  it('renders populated TimeBreakdown and refetches silently when job status changes', () => {
    const populated: TimeBreakdown = {
      summaryLines: ['Ran for 40s and passed.', '1 test run: 1 passed.'],
      jobTimeline: {
        totalDurationText: '40s',
        lanes: [],
      },
    };
    const refresh$ = new Subject<{timeline?: TimeBreakdown}>();
    mockJobService.getJobTimeline.and.returnValue(refresh$.asObservable());

    host.status.set('JOB_STATUS_DONE');
    fixture.detectChanges();
    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(2);
    expect(component.loading()).toBeFalse();

    refresh$.next({timeline: populated});
    refresh$.complete();
    fixture.detectChanges();

    expect(component.timingBreakdown()).toEqual(populated);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Ran for 40s and passed.');

    // If a background status refresh fails for the same jobId, keep the existing breakdown.
    mockJobService.getJobTimeline.and.returnValue(
      throwError(() => new Error('Transient error')),
    );
    host.status.set('JOB_STATUS_ABORT');
    fixture.detectChanges();
    expect(component.timingBreakdown()).toEqual(populated);
    expect(component.errorMessage()).toBeNull();

    // Switching to a different jobId clears timingBreakdown and sets loading=true while pending.
    const newJob$ = new Subject<{timeline?: TimeBreakdown}>();
    mockJobService.getJobTimeline.and.returnValue(newJob$.asObservable());
    host.jobId.set('job_789');
    fixture.detectChanges();
    expect(component.loading()).toBeTrue();
    expect(component.timingBreakdown()).toBeUndefined();
  });

  it('renders error message when getJobTimeline fails', () => {
    mockJobService.getJobTimeline.and.returnValue(
      throwError(() => new Error('RPC failed')),
    );

    host.jobId.set('job_err');
    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Failed to load timeline.');
    expect(component.loading()).toBeFalse();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Failed to load timeline.');
  });
});

describe('HttpJobService.getJobTimeline', () => {
  let service: HttpJobService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        HttpJobService,
        {provide: APP_DATA, useValue: {labConsoleServerUrl: 'http://bff'}},
      ],
    });
    service = TestBed.inject(HttpJobService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('sends GET /v6/jobs/{jobId}/timeline', () => {
    let responseTimeline: TimeBreakdown | undefined;
    service.getJobTimeline({jobId: 'job_456'}).subscribe((resp) => {
      responseTimeline = resp.timeline;
    });

    const req = httpMock.expectOne('http://bff/v6/jobs/job_456/timeline');
    expect(req.request.method).toBe('GET');
    req.flush({timeline: {summaryLines: ['Ran for 10s and passed.']}});

    expect(responseTimeline?.summaryLines).toEqual(['Ran for 10s and passed.']);
  });
});
