import {DOCUMENT} from '@angular/common';
import {provideHttpClient} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import {ChangeDetectionStrategy, Component, signal} from '@angular/core';
import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
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

  it('renders populated TimeBreakdown, preserves live timeline across transient errors, and skips status-only refetches once final', () => {
    const livePopulated: TimeBreakdown = {
      summaryLines: ['Running for 20s so far.', '1 test run: 1 running.'],
      jobTimeline: {
        totalDurationText: '20s',
        lanes: [],
      },
      live: true,
    };
    const finalPopulated: TimeBreakdown = {
      summaryLines: ['Ran for 40s and passed.', '1 test run: 1 passed.'],
      jobTimeline: {
        totalDurationText: '40s',
        lanes: [],
      },
    };
    const refresh$ = new Subject<{timeline?: TimeBreakdown}>();
    mockJobService.getJobTimeline.and.returnValue(refresh$.asObservable());

    host.status.set('JOB_STATUS_ASSIGNED');
    fixture.detectChanges();
    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(2);
    expect(component.loading()).toBeFalse();

    refresh$.next({timeline: livePopulated});
    refresh$.complete();
    fixture.detectChanges();

    expect(component.timingBreakdown()).toEqual(livePopulated);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Running for 20s so far.');

    // If a background status refresh fails while live for the same jobId, keep the existing breakdown.
    mockJobService.getJobTimeline.and.returnValue(
      throwError(() => new Error('Transient error')),
    );
    host.status.set('JOB_STATUS_RUNNING');
    fixture.detectChanges();
    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(3);
    expect(component.timingBreakdown()).toEqual(livePopulated);
    expect(component.errorMessage()).toBeNull();

    // Once a status refresh returns a final (!live) populated breakdown, further status changes do not refetch.
    mockJobService.getJobTimeline.and.returnValue(
      of({timeline: finalPopulated}),
    );
    host.status.set('JOB_STATUS_DONE');
    fixture.detectChanges();
    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(4);
    expect(component.timingBreakdown()).toEqual(finalPopulated);

    host.status.set('JOB_STATUS_ABORT');
    fixture.detectChanges();
    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(4);

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

  it('says that a live timeline updates itself, and stops saying so once it is final', () => {
    const liveTiming: TimeBreakdown = {
      summaryLines: ['Running for 5m so far.', '1 test run: 1 running.'],
      jobTimeline: {totalDurationText: '5m', lanes: []},
      live: true,
    };
    mockJobService.getJobTimeline.and.returnValue(of({timeline: liveTiming}));
    host.jobId.set('job_live');
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain(
      'Updates every 30 seconds while the job runs.',
    );

    mockJobService.getJobTimeline.and.returnValue(
      of({timeline: {...liveTiming, live: false}}),
    );
    host.status.set('JOB_STATUS_DONE');
    fixture.detectChanges();
    expect(compiled.textContent).not.toContain('Updates every');
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

@Component({
  standalone: true,
  imports: [JobTimelineTab],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-job-timeline-tab [jobId]="jobId()" [active]="active()" (finished)="onFinished()"></app-job-timeline-tab>`,
})
class LiveJobHostComponent {
  readonly jobId = signal<string>('job_456');
  readonly active = signal<boolean>(true);
  finishedCount = 0;

  onFinished() {
    this.finishedCount++;
  }
}

describe('JobTimelineTab live refresh', () => {
  const live: TimeBreakdown = {
    summaryLines: ['Running for 5m so far.'],
    jobTimeline: {totalDurationText: '5m', lanes: []},
    live: true,
  };
  const final: TimeBreakdown = {
    summaryLines: ['Ran for 6m and passed.'],
    jobTimeline: {totalDurationText: '6m', lanes: []},
  };

  let fixture: ComponentFixture<LiveJobHostComponent>;
  let host: LiveJobHostComponent;
  let component: JobTimelineTab;
  let mockJobService: jasmine.SpyObj<JobService>;

  function createHost(active: boolean) {
    fixture = TestBed.createComponent(LiveJobHostComponent);
    host = fixture.componentInstance;
    host.active.set(active);
    fixture.detectChanges();
    component = fixture.debugElement.query(
      By.directive(JobTimelineTab),
    ).componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    mockJobService = jasmine.createSpyObj<JobService>('JobService', [
      'getJobTimeline',
    ]);
    await TestBed.configureTestingModule({
      imports: [LiveJobHostComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        {provide: JOB_SERVICE, useValue: mockJobService},
      ],
    }).compileComponents();
  });

  it('refetches a live timeline every 30s until it is final, then stops and emits finished', fakeAsync(() => {
    const responses = [live, live, final];
    let call = 0;
    mockJobService.getJobTimeline.and.callFake(() =>
      of({timeline: responses[Math.min(call++, responses.length - 1)]}),
    );

    createHost(true);
    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(1);

    tick(30_000);
    fixture.detectChanges();
    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(2);
    expect(host.finishedCount).toBe(0);

    tick(30_000);
    fixture.detectChanges();
    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(3);
    expect(component.timingBreakdown()).toEqual(final);
    expect(host.finishedCount).toBe(1);

    tick(60_000);
    fixture.detectChanges();
    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(3);
    expect(host.finishedCount).toBe(1);
    fixture.destroy();
  }));

  it('does not refetch while the tab or document is hidden, and catches up when shown again', fakeAsync(() => {
    mockJobService.getJobTimeline.and.returnValue(of({timeline: live}));
    const doc = TestBed.inject(DOCUMENT);

    createHost(false);
    tick(60_000);
    fixture.detectChanges();
    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(1);

    host.active.set(true);
    fixture.detectChanges();
    tick(0);
    fixture.detectChanges();
    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(2);

    const visibilitySpy = spyOnProperty(
      doc,
      'visibilityState',
      'get',
    ).and.returnValue('hidden');
    doc.dispatchEvent(new Event('visibilitychange'));
    fixture.detectChanges();

    tick(60_000);
    fixture.detectChanges();
    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(2);

    visibilitySpy.and.returnValue('visible');
    doc.dispatchEvent(new Event('visibilitychange'));
    fixture.detectChanges();
    tick(0);
    fixture.detectChanges();
    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(3);
    fixture.destroy();
  }));

  it('waits a full interval when switching from a final job to a live job after 30s', fakeAsync(() => {
    mockJobService.getJobTimeline.and.returnValue(of({timeline: final}));
    createHost(true);
    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(1);

    tick(45_000);
    mockJobService.getJobTimeline.and.returnValue(of({timeline: live}));
    host.jobId.set('job_live_next');
    fixture.detectChanges();
    tick(0);
    fixture.detectChanges();
    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(2);

    tick(29_999);
    fixture.detectChanges();
    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(2);

    tick(1);
    fixture.detectChanges();
    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(3);
    fixture.destroy();
  }));
});
