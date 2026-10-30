import {
  ComponentFixture,
  DeferBlockState,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  ParamMap,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import {BehaviorSubject, of} from 'rxjs';

import {APP_DATA} from '../../core/models/app_data';
import {
  JobResult,
  JobStatus,
  TestOverviewData,
  TestResult,
  TestStatus,
} from '../../core/models/test_overview';
import {
  JOB_SERVICE,
  JobService,
} from '../../core/services/job/job_service';
import {MOCK_TEST_SCENARIOS} from '../../core/services/mock_data/tests';
import {
  TEST_SERVICE,
  TestService,
} from '../../core/services/test/test_service';
import {ClipboardService} from '../../shared/services/clipboard_service';
import {LoadingService} from '../../shared/services/loading_service';
import {SnackBarService} from '../../shared/services/snackbar_service';
import {TestFilesTab} from './components/test_files_tab/test_files_tab';
import {TestLogTab} from './components/test_log_tab/test_log_tab';
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
    timingBreakdown: {
      createTime: '2026-06-08T03:00:00Z',
      stages: [],
    },
  };

  beforeEach(async () => {
    mockTestService = jasmine.createSpyObj('TestService', [
      'getTest',
      'getTestLog',
      'getTestFile',
    ]);
    mockJobService = jasmine.createSpyObj('JobService', ['killJob']);
    mockTestService.getTest.and.returnValue(of({test: mockOverviewData}));
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

    paramMapSubject = new BehaviorSubject(convertToParamMap({'id': 'test_123'}));
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
    expect(runningFixture.componentInstance.killActionState()?.enabled).toBeTrue();
    expect(runningFixture.componentInstance.killActionState()?.visible).toBeTrue();
    expect(compiled.querySelector('.live-badge')?.textContent).toContain(
      'LIVE',
    );
  }));

  it('renders timeline legacy link with redirect_to_v6=0', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    component.setActiveTab('timeline');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const timelineLink = compiled.querySelector(
      '.under-development-text a',
    ) as HTMLAnchorElement | null;
    expect(timelineLink).not.toBeNull();
    expect(timelineLink?.getAttribute('href')).toBe(
      `${component.legacyFeUrl}/testdetailview/job_456/test_123?tab=2&redirect_to_v6=0`,
    );
  }));

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
    mockTestService.getTest.and.returnValue(of({test: subTestOverviewData}));
    paramMapSubject.next(
      convertToParamMap({'id': 'root_test_12345678', 'jobId': 'job_456'}),
    );
    queryParamMapSubject.next(
      convertToParamMap({'sub_test_id': 'sub_test_abcdef1234'}),
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
    expect(compiled.querySelector('.status-indicator')?.textContent).not.toContain(
      'Status:',
    );
    expect(compiled.querySelector('.status-indicator')?.textContent).toContain(
      'Result: Pass',
    );

    subComponent.setActiveTab('timeline');
    subFixture.detectChanges();
    const timelineLink = compiled.querySelector(
      '.under-development-text a',
    ) as HTMLAnchorElement | null;
    expect(timelineLink?.getAttribute('href')).toBe(
      `${subComponent.legacyFeUrl}/testdetailview/job_456/root_test_12345678?sub_test=sub_test_abcdef1234&tab=2&redirect_to_v6=0`,
    );

    subComponent.setActiveTab('log');
    subFixture.detectChanges();
    await subFixture.whenStable();
    for (const block of await subFixture.getDeferBlocks()) {
      await block.render(DeferBlockState.Complete);
    }
    subFixture.detectChanges();
    const logTab = subFixture.debugElement.query(
      By.directive(TestLogTab),
    ).componentInstance as TestLogTab;
    expect(logTab.testId()).toBe('root_test_12345678');
    expect(logTab.subTestId()).toBe('sub_test_abcdef1234');

    subComponent.setActiveTab('files');
    subFixture.detectChanges();
    await subFixture.whenStable();
    for (const block of await subFixture.getDeferBlocks()) {
      await block.render(DeferBlockState.Complete);
    }
    subFixture.detectChanges();
    const filesTab = subFixture.debugElement.query(
      By.directive(TestFilesTab),
    ).componentInstance as TestFilesTab;
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
  });
});

