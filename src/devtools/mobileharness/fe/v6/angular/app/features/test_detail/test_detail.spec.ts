import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import {of} from 'rxjs';

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
    ]);
    mockTestService.getTest.and.returnValue(of(mockOverviewData));
    mockTestService.getTestLog.and.returnValue(
      of({
        logContent: '',
        nextOffset: 0,
      }),
    );

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
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TestDetail);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('should fetch test details on init', () => {
    expect(mockTestService.getTest).toHaveBeenCalledWith({testId: 'test_123'});
    const pageData = component.testPageData();
    expect(pageData).toBeTruthy();
    expect(pageData!.testOverviewData!.id).toBe('test_123');
  });

  it('should copy test id to clipboard when copy button is clicked', () => {
    component.copyToClipboard('test_123');
    expect(mockClipboardService.copyToClipboard).toHaveBeenCalledWith(
      'test_123',
    );
    expect(mockSnackBarService.showSuccess).toHaveBeenCalledWith(
      'Copied to clipboard!',
    );
  });
});
