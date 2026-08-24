import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import {of} from 'rxjs';

import {APP_DATA} from '../../core/models/app_data';
import {
  JobResult,
  JobStatus,
  TestOverviewData,
  TestResult,
  TestStatus,
} from '../../core/models/test_overview';
import {TEST_SERVICE, TestService} from '../../core/services/test/test_service';
import {ClipboardService} from '../../shared/services/clipboard_service';
import {LoadingService} from '../../shared/services/loading_service';
import {SnackBarService} from '../../shared/services/snackbar_service';
import {TestDetail} from './test_detail';

describe('TestDetail Component', () => {
  let fixture: ComponentFixture<TestDetail>;
  let component: TestDetail;
  let mockTestService: jasmine.SpyObj<TestService>;
  let mockClipboardService: jasmine.SpyObj<ClipboardService>;
  let mockSnackBarService: jasmine.SpyObj<SnackBarService>;
  let mockLoadingService: jasmine.SpyObj<LoadingService>;
  let mockActivatedRoute: Partial<ActivatedRoute>;

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
    ]);
    mockLoadingService = jasmine.createSpyObj('LoadingService', [
      'show',
      'hide',
    ]);

    mockActivatedRoute = {
      paramMap: of(convertToParamMap({'id': 'test_123'})),
      queryParamMap: of(convertToParamMap({})),
    };

    await TestBed.configureTestingModule({
      imports: [TestDetail, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        {provide: TEST_SERVICE, useValue: mockTestService},
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
});
