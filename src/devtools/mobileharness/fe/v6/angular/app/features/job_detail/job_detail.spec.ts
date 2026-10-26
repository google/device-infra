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
  JobOverviewData,
  JobResult,
  JobStatus,
} from '../../core/models/job_overview';
import {JOB_SERVICE, JobService} from '../../core/services/job/job_service';
import {ClipboardService} from '../../shared/services/clipboard_service';
import {LoadingService} from '../../shared/services/loading_service';
import {SnackBarService} from '../../shared/services/snackbar_service';
import {JobDetail} from './job_detail';

describe('JobDetail Component', () => {
  let fixture: ComponentFixture<JobDetail>;
  let component: JobDetail;
  let mockJobService: jasmine.SpyObj<JobService>;

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
    timingBreakdown: {
      createTime: '2026-06-08T03:00:00Z',
      stages: [],
    },
  };

  beforeEach(async () => {
    mockJobService = jasmine.createSpyObj<JobService>('JobService', [
      'getJob',
      'getJobLog',
      'getJobFile',
      'killJob',
    ]);
    mockJobService.getJob.and.returnValue(of({job: mockJobData}));

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
            paramMap: of(convertToParamMap({'id': 'job_456'})),
            queryParamMap: of(convertToParamMap({})),
          },
        },
        {provide: APP_DATA, useValue: {applicationId: 'arsenal'}},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(JobDetail);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

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
      `${component.legacyFeUrl}/jobdetailview/job_456?tab=2&redirect_to_v6=0`,
    );
  }));
});
