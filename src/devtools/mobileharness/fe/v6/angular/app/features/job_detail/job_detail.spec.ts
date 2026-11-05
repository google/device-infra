import {
  ComponentFixture,
  DeferBlockState,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import {By, Title} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  ParamMap,
  Router,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import {BehaviorSubject, of} from 'rxjs';

import {APP_DATA} from '../../core/models/app_data';
import {
  JobOverviewData,
  JobResult,
  JobStatus,
  TimeBreakdown,
} from '../../core/models/job_overview';
import {JOB_SERVICE, JobService} from '../../core/services/job/job_service';
import {ClipboardService} from '../../shared/services/clipboard_service';
import {LoadingService} from '../../shared/services/loading_service';
import {SnackBarService} from '../../shared/services/snackbar_service';
import {JobTimelineTab} from './components/job_timeline_tab/job_timeline_tab';
import {JobDetail} from './job_detail';

describe('JobDetail Component', () => {
  let fixture: ComponentFixture<JobDetail>;
  let component: JobDetail;
  let mockJobService: jasmine.SpyObj<JobService>;
  let paramMapSubject: BehaviorSubject<ParamMap>;
  let queryParamMapSubject: BehaviorSubject<ParamMap>;

  const mockJobData: JobOverviewData = {
    id: 'job_456',
    name: 'Example job execution',
    status: JobStatus.JOB_STATUS_DONE,
    result: JobResult.JOB_RESULT_PASS,
    executionDetails: {
      user: 'dafeng',
      createTime: '2026-06-08T03:00:00Z',
    },
    config: {
      devices: {
        device: [{deviceType: 'AndroidRealDevice', driver: 'NoOpDriver'}],
      },
    },
    properties: {},
  };

  const mockJobTimeline: TimeBreakdown = {
    summaryLines: ['Passed in 40.0s: 1 passed across 1 test run.'],
    originTime: '2026-06-08T03:00:00Z',
    finishTime: '2026-06-08T03:00:40Z',
    jobTimeline: {
      totalDurationText: '40.0s',
      lanes: [],
    },
  };

  beforeEach(async () => {
    mockJobService = jasmine.createSpyObj<JobService>('JobService', [
      'getJob',
      'getJobTimeline',
      'getJobLog',
      'getJobFile',
      'killJob',
    ]);
    mockJobService.getJob.and.returnValue(of({job: mockJobData}));
    mockJobService.getJobTimeline.and.returnValue(
      of({timeline: mockJobTimeline}),
    );
    paramMapSubject = new BehaviorSubject(convertToParamMap({'id': 'job_456'}));
    queryParamMapSubject = new BehaviorSubject(convertToParamMap({}));

    await TestBed.configureTestingModule({
      imports: [JobDetail, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        {provide: JOB_SERVICE, useValue: mockJobService},
        {
          provide: ClipboardService,
          useValue: jasmine.createSpyObj('ClipboardService', [
            'copyToClipboard',
          ]),
        },
        {
          provide: SnackBarService,
          useValue: jasmine.createSpyObj('SnackBarService', [
            'showSuccess',
            'showError',
          ]),
        },
        {
          provide: LoadingService,
          useValue: jasmine.createSpyObj('LoadingService', ['show', 'hide']),
        },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: paramMapSubject.asObservable(),
            queryParamMap: queryParamMapSubject.asObservable(),
          },
        },
        {provide: APP_DATA, useValue: {applicationId: 'arsenal'}},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(JobDetail);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders JobTimelineTab lazily when timeline tab is selected and keeps it mounted across tab switches', async () => {
    const router = TestBed.inject(Router);
    const navSpy = spyOn(router, 'navigate').and.returnValue(
      Promise.resolve(true),
    );
    fixture.detectChanges();
    await fixture.whenStable();
    expect(mockJobService.getJobTimeline).not.toHaveBeenCalled();

    component.setActiveTab('timeline');
    fixture.detectChanges();
    expect(navSpy).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: {tab: 'timeline'},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
    for (const block of await fixture.getDeferBlocks()) {
      await block.render(DeferBlockState.Complete);
    }
    fixture.detectChanges();

    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(1);
    expect(mockJobService.getJobTimeline).toHaveBeenCalledWith({
      jobId: 'job_456',
    });
    const timelineDebug = fixture.debugElement.query(
      By.directive(JobTimelineTab),
    );
    const timelineTab = timelineDebug.componentInstance as JobTimelineTab;
    const timelineWrapper = timelineDebug.parent!.nativeElement as HTMLElement;
    expect(timelineWrapper.hidden).toBeFalse();
    expect(timelineTab.jobId()).toBe('job_456');
    expect(timelineTab.timingBreakdown()).toEqual(mockJobTimeline);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain(
      'Passed in 40.0s: 1 passed across 1 test run.',
    );

    queryParamMapSubject.next(convertToParamMap({'tab': 'timeline'}));
    navSpy.calls.reset();
    component.setActiveTab('overview');
    fixture.detectChanges();
    expect(timelineWrapper.hidden).toBeTrue();
    expect(navSpy).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: {tab: null},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });

    component.setActiveTab('timeline');
    fixture.detectChanges();
    expect(timelineWrapper.hidden).toBeFalse();
    expect(mockJobService.getJobTimeline).toHaveBeenCalledTimes(1);
  });

  it('opens timeline tab on initial load when ?tab=timeline is in query params and resets on job route change', async () => {
    fixture.destroy();
    const router = TestBed.inject(Router);
    const navSpy = spyOn(router, 'navigate').and.returnValue(
      Promise.resolve(true),
    );
    mockJobService.getJob.and.returnValue(
      of({
        job: {
          ...mockJobData,
          status: JobStatus.JOB_STATUS_RUNNING,
        },
      }),
    );
    queryParamMapSubject.next(convertToParamMap({'tab': 'timeline'}));
    const tabFixture = TestBed.createComponent(JobDetail);
    tabFixture.detectChanges();
    await tabFixture.whenStable();
    tabFixture.detectChanges();

    expect(tabFixture.componentInstance.activeTab()).toBe('timeline');
    expect(navSpy).not.toHaveBeenCalled();
    for (const block of await tabFixture.getDeferBlocks()) {
      await block.render(DeferBlockState.Complete);
    }
    tabFixture.detectChanges();
    expect(
      tabFixture.debugElement.query(By.directive(JobTimelineTab)),
    ).not.toBeNull();

    // reloadSilent() on the same job preserves activeTab and timelineVisited.
    mockJobService.getJob.and.returnValue(of({job: mockJobData}));
    tabFixture.componentInstance.onLogStreamCompleted();
    tabFixture.detectChanges();
    await tabFixture.whenStable();
    tabFixture.detectChanges();
    expect(tabFixture.componentInstance.activeTab()).toBe('timeline');
    expect(tabFixture.componentInstance.timelineVisited()).toBeTrue();

    // Navigating to a RUNNING job with ?tab=files without fileExplorer resets timelineVisited, selects 'log', and rewrites ?tab.
    queryParamMapSubject.next(convertToParamMap({'tab': 'files'}));
    mockJobService.getJob.and.returnValue(
      of({
        job: {
          ...mockJobData,
          id: 'job_running',
          status: JobStatus.JOB_STATUS_RUNNING,
        },
      }),
    );
    paramMapSubject.next(convertToParamMap({'id': 'job_running'}));
    tabFixture.detectChanges();
    await tabFixture.whenStable();
    tabFixture.detectChanges();

    expect(tabFixture.componentInstance.timelineVisited()).toBeFalse();
    expect(tabFixture.componentInstance.activeTab()).toBe('log');
    expect(navSpy).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: {tab: 'log'},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  });

  it('names the job in the browser page title once it loads', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    expect(TestBed.inject(Title).getTitle()).toBe(
      'Job job_456... - OmniLab Console',
    );
  }));

  it('treats JOB_STATUS_UNSPECIFIED as UNKNOWN and filters out JOB_RESULT_UNSPECIFIED', fakeAsync(() => {
    mockJobService.getJob.and.returnValue(
      of({
        job: {
          ...mockJobData,
          status: JobStatus.JOB_STATUS_UNSPECIFIED,
          result: JobResult.JOB_RESULT_UNSPECIFIED,
        },
      }),
    );

    const unspecifiedFixture = TestBed.createComponent(JobDetail);
    unspecifiedFixture.detectChanges();
    tick();
    unspecifiedFixture.detectChanges();

    expect(unspecifiedFixture.componentInstance.statusPillLabel()).toBe(
      'UNKNOWN',
    );
    expect(unspecifiedFixture.componentInstance.resultPillLabel()).toBeNull();

    mockJobService.getJob.and.returnValue(
      of({
        job: {
          ...mockJobData,
          status: JobStatus.JOB_STATUS_DONE,
          result: JobResult.JOB_RESULT_UNSPECIFIED,
        },
      }),
    );

    const doneUnspecifiedResultFixture = TestBed.createComponent(JobDetail);
    doneUnspecifiedResultFixture.detectChanges();
    tick();
    doneUnspecifiedResultFixture.detectChanges();

    expect(
      doneUnspecifiedResultFixture.componentInstance.statusPillLabel(),
    ).toBe('DONE');
    expect(
      doneUnspecifiedResultFixture.componentInstance.resultPillLabel(),
    ).toBeNull();
  }));
});
