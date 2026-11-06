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
import {BehaviorSubject, Subject, of} from 'rxjs';

import {APP_DATA} from '../../core/models/app_data';
import {
  JobResult,
  JobStatus,
  TestOverviewData,
  TestResult,
  TestStatus,
  TimeBreakdown,
} from '../../core/models/test_overview';
import {JOB_SERVICE, JobService} from '../../core/services/job/job_service';
import {MOCK_TEST_SCENARIOS} from '../../core/services/mock_data/tests';
import {TEST_SERVICE, TestService} from '../../core/services/test/test_service';
import {ClipboardService} from '../../shared/services/clipboard_service';
import {LoadingService} from '../../shared/services/loading_service';
import {SnackBarService} from '../../shared/services/snackbar_service';
import {TestFilesTab} from './components/test_files_tab/test_files_tab';
import {TestLogTab} from './components/test_log_tab/test_log_tab';
import {TestTimelineTab} from './components/test_timeline_tab/test_timeline_tab';
import {TestDetail} from './test_detail';

describe('TestDetail Component', () => {
  let fixture: ComponentFixture<TestDetail>;
  let component: TestDetail;
  let mockTestService: jasmine.SpyObj<TestService>;
  let mockJobService: jasmine.SpyObj<JobService>;
  let mockClipboardService: jasmine.SpyObj<ClipboardService>;
  let mockSnackBarService: jasmine.SpyObj<SnackBarService>;
  let mockLoadingService: jasmine.SpyObj<LoadingService>;
  let mockActivatedRoute: Partial<ActivatedRoute>;
  let paramMapSubject: BehaviorSubject<ParamMap>;
  let queryParamMapSubject: BehaviorSubject<ParamMap>;

  const mockOverviewData: TestOverviewData = {
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
    },
    properties: {},
  };

  const mockTimingBreakdown: TimeBreakdown = {
    summaryLines: ['Passed in 10.0s: 10.0s test run.'],
    originTime: '2026-06-08T03:00:00Z',
    finishTime: '2026-06-08T03:00:10Z',
    testTimeline: {
      phases: [
        {
          phaseId: 'test',
          focusKey: 'test',
          label: 'Test run',
          fill: 'TIMELINE_FILL_TEST',
          durationText: '10.0s',
          shareText: '100%',
          rows: [
            {
              label: 'NoOpDriver',
              focusKey: 'test',
              fill: 'TIMELINE_FILL_TEST',
              durationText: '10.0s',
              shareText: '100%',
              bars: [
                {
                  startTime: '2026-06-08T03:00:00Z',
                  endTime: '2026-06-08T03:00:10Z',
                  leftPercent: 0,
                  widthPercent: 100,
                  durationText: '10.0s',
                },
              ],
            },
          ],
        },
      ],
    },
  };

  beforeEach(async () => {
    mockTestService = jasmine.createSpyObj('TestService', [
      'getTest',
      'getTestTimeline',
      'getTestLog',
      'getTestFile',
    ]);
    mockJobService = jasmine.createSpyObj('JobService', ['killJob']);
    mockTestService.getTest.and.returnValue(of({test: mockOverviewData}));
    mockTestService.getTestTimeline.and.returnValue(
      of({timeline: mockTimingBreakdown}),
    );
    mockTestService.getTestLog.and.returnValue(
      of({
        logContent: '',
        nextOffset: 0,
        testStatus: TestStatus.TEST_STATUS_DONE,
        logReset: false,
        contentHash: 'hash-test-detail',
      }),
    );
    mockTestService.getTestFile.and.returnValue(of(''));

    mockClipboardService = jasmine.createSpyObj('ClipboardService', [
      'copyToClipboard',
    ]);
    mockClipboardService.copyToClipboard.and.returnValue(true);
    mockSnackBarService = jasmine.createSpyObj('SnackBarService', [
      'showSuccess',
      'showError',
    ]);
    mockLoadingService = jasmine.createSpyObj('LoadingService', [
      'show',
      'hide',
    ]);

    paramMapSubject = new BehaviorSubject(
      convertToParamMap({'id': 'test_123'}),
    );
    queryParamMapSubject = new BehaviorSubject(convertToParamMap({}));
    mockActivatedRoute = {
      paramMap: paramMapSubject.asObservable(),
      queryParamMap: queryParamMapSubject.asObservable(),
    };

    await TestBed.configureTestingModule({
      imports: [TestDetail, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        {provide: TEST_SERVICE, useValue: mockTestService},
        {provide: JOB_SERVICE, useValue: mockJobService},
        {provide: ClipboardService, useValue: mockClipboardService},
        {provide: SnackBarService, useValue: mockSnackBarService},
        {provide: LoadingService, useValue: mockLoadingService},
        {provide: ActivatedRoute, useValue: mockActivatedRoute},
        {provide: APP_DATA, useValue: {applicationId: 'arsenal'}},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TestDetail);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('should fetch test details on init', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
    expect(mockTestService.getTest).toHaveBeenCalledWith({
      testId: 'test_123',
      jobId: '',
    });
    const pageData = component.testPageData();
    expect(pageData).toBeTruthy();
    expect(pageData!.testOverviewData!.id).toBe('test_123');
  }));

  it('names the test in the browser page title once it loads', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    expect(TestBed.inject(Title).getTitle()).toBe(
      'Example test execution - OmniLab Console',
    );
  }));

  it('names a test without a name by its ID in the browser page title', fakeAsync(() => {
    fixture.destroy();
    mockTestService.getTest.and.returnValue(
      of({test: {...mockOverviewData, name: ''}}),
    );

    const unnamedFixture = TestBed.createComponent(TestDetail);
    unnamedFixture.detectChanges();
    tick();
    unnamedFixture.detectChanges();

    expect(TestBed.inject(Title).getTitle()).toBe(
      'Test test_123... - OmniLab Console',
    );
  }));

  it('should copy test id to clipboard when copy button is clicked', () => {
    component.copyToClipboard('test_123');
    expect(mockClipboardService.copyToClipboard).toHaveBeenCalledWith(
      'test_123',
    );
    expect(mockSnackBarService.showSuccess).toHaveBeenCalledWith(
      'Copied to clipboard!',
    );
  });

  it('should render "Devices" label when there are multiple devices', fakeAsync(() => {
    const multiDeviceData = {
      ...mockOverviewData,
      devices: {
        device: [
          {id: 'device_01', type: 'AndroidRealDevice'},
          {id: 'device_02', type: 'AndroidRealDevice'},
        ],
      },
    };
    mockTestService.getTest.and.returnValue(of({test: multiDeviceData}));

    const multiFixture = TestBed.createComponent(TestDetail);
    multiFixture.detectChanges();
    tick();
    multiFixture.detectChanges();
    const compiled = multiFixture.nativeElement as HTMLElement;
    const labels = compiled.querySelectorAll('.executed-on-grid .grid-label');
    expect(labels[0]?.textContent?.trim()).toBe('Devices');
  }));

  it('should render "Device" label when there is only one device', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const labels = compiled.querySelectorAll('.executed-on-grid .grid-label');
    expect(labels[0]?.textContent?.trim()).toBe('Device');
  }));

  it('should hide executed on card when both devices and host are missing or empty', fakeAsync(() => {
    const emptyData: TestOverviewData = {
      ...mockOverviewData,
      devices: {},
      host: undefined,
    };
    mockTestService.getTest.and.returnValue(of({test: emptyData}));

    const emptyFixture = TestBed.createComponent(TestDetail);
    emptyFixture.detectChanges();
    tick();
    emptyFixture.detectChanges();
    const compiled = emptyFixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.executed-on-card')).toBeNull();
  }));

  it('should render N/A for host when devices are present but host is undefined', fakeAsync(() => {
    const noHostData: TestOverviewData = {
      ...mockOverviewData,
      host: undefined,
    };
    mockTestService.getTest.and.returnValue(of({test: noHostData}));

    const noHostFixture = TestBed.createComponent(TestDetail);
    noHostFixture.detectChanges();
    tick();
    noHostFixture.detectChanges();
    const compiled = noHostFixture.nativeElement as HTMLElement;
    const card = compiled.querySelector('.executed-on-card');
    expect(card).not.toBeNull();
    const values = compiled.querySelectorAll('.executed-on-grid .grid-value');
    expect(values.length).toBe(2);
    expect(values[0]?.textContent?.trim()).toContain('device_01');
    expect(values[1]?.textContent?.trim()).toBe('N/A');
  }));

  it('should render kill action button when test scenario has running job', fakeAsync(() => {
    const runningScenario = MOCK_TEST_SCENARIOS.find(
      (s) => s.overview.job?.status === JobStatus.JOB_STATUS_RUNNING,
    );
    expect(runningScenario).toBeDefined();
    mockTestService.getTest.and.returnValue(
      of({
        test: runningScenario!.overview,
        actions: runningScenario!.actions,
      }),
    );

    const runningFixture = TestBed.createComponent(TestDetail);
    runningFixture.detectChanges();
    tick();
    runningFixture.detectChanges();

    const compiled = runningFixture.nativeElement as HTMLElement;
    const killButton = compiled.querySelector(
      'button[data-testid="kill-job"]',
    ) as HTMLButtonElement;
    expect(killButton).not.toBeNull();
    expect(killButton.disabled).toBeFalse();
    expect(
      runningFixture.componentInstance.killActionState()?.enabled,
    ).toBeTrue();
    expect(
      runningFixture.componentInstance.killActionState()?.visible,
    ).toBeTrue();
    expect(compiled.querySelector('.live-badge')?.textContent).toContain(
      'LIVE',
    );
  }));

  it('hides job status and result pills when status is JOB_STATUS_UNSPECIFIED and result is JOB_RESULT_UNSPECIFIED', fakeAsync(() => {
    const baseScenario = MOCK_TEST_SCENARIOS[0];
    mockTestService.getTest.and.returnValue(
      of({
        test: {
          ...baseScenario.overview,
          job: {
            ...baseScenario.overview.job!,
            status: JobStatus.JOB_STATUS_UNSPECIFIED,
            result: JobResult.JOB_RESULT_UNSPECIFIED,
          },
        },
        actions: baseScenario.actions,
      }),
    );

    const unspecifiedJobFixture = TestBed.createComponent(TestDetail);
    unspecifiedJobFixture.detectChanges();
    tick();
    unspecifiedJobFixture.detectChanges();

    const compiled = unspecifiedJobFixture.nativeElement as HTMLElement;
    const jobPanelText =
      compiled.querySelector('.job-details-panel')?.textContent ?? '';
    expect(jobPanelText).not.toContain('Unspecified');
    expect(jobPanelText).not.toContain('JOB_STATUS_UNSPECIFIED');
    expect(jobPanelText).not.toContain('JOB_RESULT_UNSPECIFIED');

    mockTestService.getTest.and.returnValue(
      of({
        test: {
          ...baseScenario.overview,
          job: {
            ...baseScenario.overview.job!,
            status: JobStatus.JOB_STATUS_RUNNING,
            result: JobResult.JOB_RESULT_UNSPECIFIED,
          },
        },
        actions: baseScenario.actions,
      }),
    );

    const runningUnspecifiedResultFixture = TestBed.createComponent(TestDetail);
    runningUnspecifiedResultFixture.detectChanges();
    tick();
    runningUnspecifiedResultFixture.detectChanges();

    const runningJobPanelText =
      (
        runningUnspecifiedResultFixture.nativeElement as HTMLElement
      ).querySelector('.job-details-panel')?.textContent ?? '';
    expect(runningJobPanelText).toContain('Running');
    expect(runningJobPanelText).not.toContain('Unspecified');
    expect(runningJobPanelText).not.toContain('JOB_RESULT_UNSPECIFIED');
  }));

  it('renders TestTimelineTab lazily when timeline tab is selected and keeps it mounted across tab switches', async () => {
    const router = TestBed.inject(Router);
    const navSpy = spyOn(router, 'navigate').and.returnValue(
      Promise.resolve(true),
    );
    fixture.detectChanges();
    await fixture.whenStable();
    expect(mockTestService.getTestTimeline).not.toHaveBeenCalled();

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

    expect(mockTestService.getTestTimeline).toHaveBeenCalledTimes(1);
    expect(mockTestService.getTestTimeline).toHaveBeenCalledWith({
      testId: 'test_123',
      jobId: 'job_456',
    });
    const timelineDebug = fixture.debugElement.query(
      By.directive(TestTimelineTab),
    );
    const timelineTab = timelineDebug.componentInstance as TestTimelineTab;
    const timelineWrapper = timelineDebug.parent!.nativeElement as HTMLElement;
    expect(timelineWrapper.hidden).toBeFalse();
    expect(timelineTab.active()).toBeTrue();
    expect(timelineTab.timingBreakdown()).toEqual(mockTimingBreakdown);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Passed in 10.0s: 10.0s test run.');

    queryParamMapSubject.next(convertToParamMap({'tab': 'timeline'}));
    navSpy.calls.reset();
    component.setActiveTab('overview');
    fixture.detectChanges();
    expect(timelineWrapper.hidden).toBeTrue();
    expect(timelineTab.active()).toBeFalse();
    expect(navSpy).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: {tab: null},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });

    component.setActiveTab('timeline');
    fixture.detectChanges();
    expect(timelineWrapper.hidden).toBeFalse();
    expect(timelineTab.active()).toBeTrue();
    expect(mockTestService.getTestTimeline).toHaveBeenCalledTimes(1);

    // onTimelineFinished() does not reload when the test is already DONE.
    mockTestService.getTest.calls.reset();
    timelineTab.finished.emit();
    fixture.detectChanges();
    expect(mockTestService.getTest).not.toHaveBeenCalled();
  });

  it('opens timeline tab on initial load when ?tab=timeline is in query params and resets on test route change', async () => {
    fixture.destroy();
    const router = TestBed.inject(Router);
    const navSpy = spyOn(router, 'navigate').and.returnValue(
      Promise.resolve(true),
    );
    mockTestService.getTest.and.returnValue(
      of({
        test: {
          ...mockOverviewData,
          status: TestStatus.TEST_STATUS_RUNNING,
        },
      }),
    );
    queryParamMapSubject.next(convertToParamMap({'tab': 'timeline'}));
    const tabFixture = TestBed.createComponent(TestDetail);
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
      tabFixture.debugElement.query(By.directive(TestTimelineTab)),
    ).not.toBeNull();

    // reloadSilent() on the same root test preserves activeTab, timelineVisited, and the mounted TestTimelineTab.
    mockTestService.getTest.and.returnValue(
      of({
        test: {
          ...mockOverviewData,
          subTestsInfo: {rootTestId: 'test_123'},
        },
      }),
    );
    tabFixture.componentInstance.onLogStreamCompleted();
    tabFixture.detectChanges();
    await tabFixture.whenStable();
    tabFixture.detectChanges();
    expect(tabFixture.componentInstance.activeTab()).toBe('timeline');
    expect(tabFixture.componentInstance.timelineVisited()).toBeTrue();
    expect(
      tabFixture.debugElement.query(By.directive(TestTimelineTab)),
    ).not.toBeNull();

    // While a new route's getTest is in flight, jobId() and rootTestId() do not read stale overview fields.
    const inFlight$ = new Subject<{test: TestOverviewData}>();
    mockTestService.getTest.and.returnValue(inFlight$.asObservable());
    queryParamMapSubject.next(convertToParamMap({'tab': 'files'}));
    paramMapSubject.next(convertToParamMap({'id': 'test_running'}));
    tabFixture.detectChanges();
    expect(tabFixture.componentInstance.jobId()).toBe('');
    expect(tabFixture.componentInstance.rootTestId()).toBe('test_running');

    inFlight$.next({
      test: {
        ...mockOverviewData,
        id: 'test_running',
        status: TestStatus.TEST_STATUS_RUNNING,
      },
    });
    inFlight$.complete();
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

  it('supports sub-test route params, breadcrumb, Part of chips, legacy URL, child tabs, and silent reload', async () => {
    const subTestOverviewData: TestOverviewData = {
      ...mockOverviewData,
      id: 'sub_test_abcdef1234',
      name: 'SubTestExecution',
      status: TestStatus.TEST_STATUS_NEW,
      subTestsInfo: {
        rootTestId: 'root_test_12345678',
      },
      fileExplorer: {
        cnsPath: '/cns-fake/test-path',
        files: [],
      },
    };
    const router = TestBed.inject(Router);
    const navSpy = spyOn(router, 'navigate').and.returnValue(
      Promise.resolve(true),
    );
    mockTestService.getTest.and.returnValue(of({test: subTestOverviewData}));
    paramMapSubject.next(
      convertToParamMap({'id': 'root_test_12345678', 'jobId': 'job_456'}),
    );
    queryParamMapSubject.next(
      convertToParamMap({
        'sub_test_id': 'sub_test_abcdef1234',
        'tab': 'timeline',
      }),
    );

    const subFixture = TestBed.createComponent(TestDetail);
    const subComponent = subFixture.componentInstance;
    subFixture.detectChanges();
    await subFixture.whenStable();
    subFixture.detectChanges();

    expect(mockTestService.getTest).toHaveBeenCalledWith({
      testId: 'root_test_12345678',
      subTestId: 'sub_test_abcdef1234',
      jobId: 'job_456',
    });
    expect(subComponent.rootTestId()).toBe('root_test_12345678');
    expect(subComponent.subTestId()).toBe('sub_test_abcdef1234');
    expect(subComponent.isSubTest()).toBeTrue();
    expect(subComponent.activeTab()).toBe('overview');
    expect(navSpy).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: {tab: null},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
    expect(subComponent.shortId('root_test_12345678')).toBe('root_tes…');
    expect(subComponent.shortId('short')).toBe('short');
    expect(subComponent.legacyTestDetailUrl()).toBe(
      `${subComponent.legacyFeUrl}/testdetailview/job_456/root_test_12345678?sub_test=sub_test_abcdef1234`,
    );

    const compiled = subFixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.breadcrumb')?.textContent).toContain(
      'Sub-test sub_test…',
    );
    const partOfChips = compiled.querySelectorAll('.part-of-chip');
    expect(partOfChips.length).toBe(2);
    expect(partOfChips[0]?.textContent).toContain('Test');
    expect(partOfChips[0]?.textContent).toContain('root_test_12345678');
    expect(partOfChips[1]?.textContent).toContain('Job');
    expect(partOfChips[1]?.textContent).toContain('job_456');
    expect(compiled.querySelector('.live-badge')).toBeNull();
    expect(
      compiled.querySelector('.status-indicator')?.textContent,
    ).not.toContain('Status:');
    expect(compiled.querySelector('.status-indicator')?.textContent).toContain(
      'Result: Pass',
    );

    const tabLabels = Array.from(
      compiled.querySelectorAll('.tabs-nav .tab-link'),
    ).map((el) => el.textContent?.trim());
    expect(tabLabels).toEqual(['Overview', 'Test Log', 'Files']);

    subComponent.setActiveTab('timeline');
    subFixture.detectChanges();
    await subFixture.whenStable();
    expect(subComponent.activeTab()).toBe('overview');
    expect(subComponent.timelineVisited()).toBeFalse();
    expect(
      subFixture.debugElement.query(By.directive(TestTimelineTab)),
    ).toBeNull();
    expect(mockTestService.getTestTimeline).not.toHaveBeenCalled();

    subComponent.setActiveTab('log');
    subFixture.detectChanges();
    await subFixture.whenStable();
    for (const block of await subFixture.getDeferBlocks()) {
      await block.render(DeferBlockState.Complete);
    }
    subFixture.detectChanges();
    const logTab = subFixture.debugElement.query(By.directive(TestLogTab))
      .componentInstance as TestLogTab;
    expect(logTab.testId()).toBe('root_test_12345678');
    expect(logTab.subTestId()).toBe('sub_test_abcdef1234');

    subComponent.setActiveTab('files');
    subFixture.detectChanges();
    await subFixture.whenStable();
    for (const block of await subFixture.getDeferBlocks()) {
      await block.render(DeferBlockState.Complete);
    }
    subFixture.detectChanges();
    const filesTab = subFixture.debugElement.query(By.directive(TestFilesTab))
      .componentInstance as TestFilesTab;
    expect(filesTab.testId()).toBe('root_test_12345678');
    expect(filesTab.subTestId()).toBe('sub_test_abcdef1234');

    mockTestService.getTest.calls.reset();
    subComponent.onLogStreamCompleted();
    subFixture.detectChanges();
    await subFixture.whenStable();
    subFixture.detectChanges();
    expect(mockTestService.getTest).toHaveBeenCalledWith({
      testId: 'root_test_12345678',
      subTestId: 'sub_test_abcdef1234',
      jobId: 'job_456',
    });

    // When a sub-test is still NEW and has no result yet, .vertical-separator and .status-indicator are hidden.
    mockTestService.getTest.and.returnValue(
      of({
        test: {
          ...subTestOverviewData,
          status: TestStatus.TEST_STATUS_NEW,
          result: TestResult.TEST_RESULT_UNSPECIFIED,
        },
      }),
    );
    subComponent.onLogStreamCompleted();
    subFixture.detectChanges();
    await subFixture.whenStable();
    subFixture.detectChanges();
    expect(compiled.querySelector('.vertical-separator')).toBeNull();
    expect(compiled.querySelector('.status-indicator')).toBeNull();

    // When a sub-test's root test is SUSPENDED without a sub-test result, the Status: pill is shown.
    mockTestService.getTest.and.returnValue(
      of({
        test: {
          ...subTestOverviewData,
          status: TestStatus.TEST_STATUS_SUSPENDED,
          result: TestResult.TEST_RESULT_UNSPECIFIED,
        },
      }),
    );
    const suspendedSubFixture = TestBed.createComponent(TestDetail);
    suspendedSubFixture.detectChanges();
    await suspendedSubFixture.whenStable();
    suspendedSubFixture.detectChanges();
    const suspendedCompiled = suspendedSubFixture.nativeElement as HTMLElement;
    expect(
      suspendedCompiled.querySelector('.status-indicator')?.textContent,
    ).toContain('Status: Suspended');

    // When a sub-test is already DONE, onLogStreamCompleted() does not trigger reloadSilent().
    mockTestService.getTest.and.returnValue(
      of({
        test: {
          ...subTestOverviewData,
          status: TestStatus.TEST_STATUS_DONE,
          result: TestResult.TEST_RESULT_PASS,
        },
      }),
    );
    const doneSubFixture = TestBed.createComponent(TestDetail);
    const doneSubComponent = doneSubFixture.componentInstance;
    doneSubFixture.detectChanges();
    await doneSubFixture.whenStable();
    doneSubFixture.detectChanges();
    mockTestService.getTest.calls.reset();
    doneSubComponent.onLogStreamCompleted();
    doneSubFixture.detectChanges();
    await doneSubFixture.whenStable();
    doneSubFixture.detectChanges();
    expect(mockTestService.getTest).not.toHaveBeenCalled();

    // Navigating from a root test with Timeline open to a sub-test identified via subTestsInfo.rootTestId !== test.id (without sub_test_id) resets timelineVisited, unmounts TestTimelineTab, and falls back to 'log' when RUNNING and 'overview' when DONE.
    fixture.destroy();
    subFixture.destroy();
    suspendedSubFixture.destroy();
    doneSubFixture.destroy();
    mockTestService.getTest.and.returnValue(
      of({
        test: {
          ...mockOverviewData,
          id: 'root_test_12345678',
          subTestsInfo: {rootTestId: 'root_test_12345678'},
        },
      }),
    );
    paramMapSubject.next(
      convertToParamMap({'id': 'root_test_12345678', 'jobId': 'job_456'}),
    );
    queryParamMapSubject.next(convertToParamMap({'tab': 'timeline'}));
    const rootToSubFixture = TestBed.createComponent(TestDetail);
    const rootToSubComponent = rootToSubFixture.componentInstance;
    rootToSubFixture.detectChanges();
    await rootToSubFixture.whenStable();
    rootToSubFixture.detectChanges();
    for (const block of await rootToSubFixture.getDeferBlocks()) {
      await block.render(DeferBlockState.Complete);
    }
    rootToSubFixture.detectChanges();
    expect(rootToSubComponent.isSubTest()).toBeFalse();
    expect(rootToSubComponent.activeTab()).toBe('timeline');
    expect(rootToSubComponent.timelineVisited()).toBeTrue();
    expect(
      rootToSubFixture.debugElement.query(By.directive(TestTimelineTab)),
    ).not.toBeNull();

    navSpy.calls.reset();
    mockTestService.getTest.and.returnValue(
      of({
        test: {
          ...subTestOverviewData,
          status: TestStatus.TEST_STATUS_RUNNING,
        },
      }),
    );
    paramMapSubject.next(
      convertToParamMap({'id': 'sub_test_abcdef1234', 'jobId': 'job_456'}),
    );
    rootToSubFixture.detectChanges();
    await rootToSubFixture.whenStable();
    rootToSubFixture.detectChanges();
    expect(rootToSubComponent.isSubTest()).toBeTrue();
    expect(rootToSubComponent.timelineVisited()).toBeFalse();
    expect(rootToSubComponent.activeTab()).toBe('log');
    expect(
      rootToSubFixture.debugElement.query(By.directive(TestTimelineTab)),
    ).toBeNull();
    expect(navSpy).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: {tab: 'log'},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });

    navSpy.calls.reset();
    mockTestService.getTest.and.returnValue(
      of({
        test: {
          ...subTestOverviewData,
          id: 'sub_test_done',
          status: TestStatus.TEST_STATUS_DONE,
        },
      }),
    );
    paramMapSubject.next(
      convertToParamMap({'id': 'sub_test_done', 'jobId': 'job_456'}),
    );
    rootToSubFixture.detectChanges();
    await rootToSubFixture.whenStable();
    rootToSubFixture.detectChanges();
    expect(rootToSubComponent.isSubTest()).toBeTrue();
    expect(rootToSubComponent.activeTab()).toBe('overview');
    expect(navSpy).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: {tab: null},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  });

  it('passes active to TestTimelineTab and reloads silently on onTimelineFinished() only while not DONE', async () => {
    fixture.destroy();
    mockTestService.getTest.and.returnValue(
      of({
        test: {
          ...mockOverviewData,
          status: TestStatus.TEST_STATUS_ASSIGNED,
        },
      }),
    );
    queryParamMapSubject.next(convertToParamMap({'tab': 'timeline'}));
    const tabFixture = TestBed.createComponent(TestDetail);
    tabFixture.detectChanges();
    await tabFixture.whenStable();
    tabFixture.detectChanges();
    for (const block of await tabFixture.getDeferBlocks()) {
      await block.render(DeferBlockState.Complete);
    }
    tabFixture.detectChanges();

    const timelineTab = tabFixture.debugElement.query(
      By.directive(TestTimelineTab),
    ).componentInstance as TestTimelineTab;
    expect(timelineTab.active()).toBeTrue();

    tabFixture.componentInstance.setActiveTab('overview');
    tabFixture.detectChanges();
    expect(timelineTab.active()).toBeFalse();

    mockTestService.getTest.calls.reset();
    tabFixture.componentInstance.onTimelineFinished();
    tabFixture.detectChanges();
    await tabFixture.whenStable();
    expect(mockTestService.getTest).toHaveBeenCalledTimes(1);

    mockTestService.getTest.and.returnValue(
      of({
        test: {
          ...mockOverviewData,
          status: TestStatus.TEST_STATUS_DONE,
        },
      }),
    );
    tabFixture.componentInstance.onTimelineFinished();
    tabFixture.detectChanges();
    await tabFixture.whenStable();

    mockTestService.getTest.calls.reset();
    tabFixture.componentInstance.onTimelineFinished();
    tabFixture.detectChanges();
    await tabFixture.whenStable();
    expect(mockTestService.getTest).not.toHaveBeenCalled();
  });
});
