import {provideHttpClient} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import {ChangeDetectionStrategy, Component, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {Subject, of, throwError} from 'rxjs';

import {APP_DATA} from '../../../../core/models/app_data';
import {TimeBreakdown} from '../../../../core/models/test_overview';
import {HttpTestService} from '../../../../core/services/test/http_test_service';
import {
  TEST_SERVICE,
  TestService,
} from '../../../../core/services/test/test_service';
import {TestTimelineTab} from './test_timeline_tab';

@Component({
  standalone: true,
  imports: [TestTimelineTab],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-test-timeline-tab [testId]="testId()" [subTestId]="subTestId()" [jobId]="jobId()" [status]="status()" [result]="result()"></app-test-timeline-tab>`,
})
class TestHostComponent {
  readonly testId = signal<string>('test_123');
  readonly subTestId = signal<string | undefined>(undefined);
  readonly jobId = signal<string>('job_456');
  readonly status = signal<string | undefined>('TEST_STATUS_RUNNING');
  readonly result = signal<string | undefined>('TEST_RESULT_UNSPECIFIED');
}

describe('TestTimelineTab Component', () => {
  const mockTiming: TimeBreakdown = {};

  let fixture: ComponentFixture<TestHostComponent>;
  let host: TestHostComponent;
  let component: TestTimelineTab;
  let mockTestService: jasmine.SpyObj<TestService>;

  beforeEach(async () => {
    mockTestService = jasmine.createSpyObj<TestService>('TestService', [
      'getTestTimeline',
    ]);
    mockTestService.getTestTimeline.and.returnValue(of({timeline: mockTiming}));

    await TestBed.configureTestingModule({
      imports: [TestTimelineTab, TestHostComponent, NoopAnimationsModule],
      providers: [{provide: TEST_SERVICE, useValue: mockTestService}],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
    component = fixture.debugElement.query(
      By.directive(TestTimelineTab),
    ).componentInstance;
    fixture.detectChanges();
  });

  it('fetches test timeline on init, shows empty message when timing is empty, and clears when testId is empty', () => {
    expect(component).toBeTruthy();
    expect(mockTestService.getTestTimeline).toHaveBeenCalledWith({
      testId: 'test_123',
      jobId: 'job_456',
    });
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain(
      'No timing was recorded for this test.',
    );

    mockTestService.getTestTimeline.calls.reset();
    host.testId.set('');
    fixture.detectChanges();
    expect(mockTestService.getTestTimeline).not.toHaveBeenCalled();
    expect(component.timingBreakdown()).toBeUndefined();
    expect(component.loading()).toBeFalse();
  });

  it('includes subTestId and refetches when subTestId or status changes', () => {
    const populated: TimeBreakdown = {
      summaryLines: ['Ran for 12s.'],
      testTimeline: {
        phases: [
          {
            phaseId: 'phase-test',
            focusKey: 'test',
            label: 'Test run',
            fill: 'TIMELINE_FILL_TEST',
            durationText: '12s',
            shareText: '100%',
            rows: [],
          },
        ],
      },
    };
    const subTestLoad$ = new Subject<{timeline?: TimeBreakdown}>();
    mockTestService.getTestTimeline.and.returnValue(
      subTestLoad$.asObservable(),
    );

    host.subTestId.set('sub_99');
    fixture.detectChanges();
    expect(mockTestService.getTestTimeline).toHaveBeenCalledWith({
      testId: 'test_123',
      subTestId: 'sub_99',
      jobId: 'job_456',
    });
    expect(component.loading()).toBeTrue();
    expect(component.timingBreakdown()).toBeUndefined();

    subTestLoad$.next({timeline: populated});
    subTestLoad$.complete();
    fixture.detectChanges();
    expect(component.loading()).toBeFalse();
    expect(component.timingBreakdown()).toEqual(populated);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Ran for 12s.');

    const statusRefresh$ = new Subject<{timeline?: TimeBreakdown}>();
    mockTestService.getTestTimeline.and.returnValue(
      statusRefresh$.asObservable(),
    );
    host.status.set('TEST_STATUS_NEW');
    fixture.detectChanges();
    expect(mockTestService.getTestTimeline).toHaveBeenCalledTimes(3);
    expect(component.loading()).toBeFalse();
    expect(component.timingBreakdown()).toEqual(populated);
    statusRefresh$.next({timeline: populated});
    statusRefresh$.complete();
    fixture.detectChanges();

    // Sub-test completion updates result while status stays NEW; verify refetch and error preservation.
    mockTestService.getTestTimeline.and.returnValue(
      throwError(() => new Error('Transient error')),
    );
    host.result.set('TEST_RESULT_PASS');
    fixture.detectChanges();
    expect(mockTestService.getTestTimeline).toHaveBeenCalledTimes(4);
    expect(component.timingBreakdown()).toEqual(populated);
    expect(component.errorMessage()).toBeNull();
  });

  it('renders error message when getTestTimeline fails', () => {
    mockTestService.getTestTimeline.and.returnValue(
      throwError(() => new Error('RPC failed')),
    );

    host.testId.set('test_err');
    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Failed to load timeline.');
    expect(component.loading()).toBeFalse();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Failed to load timeline.');
  });
});

describe('HttpTestService.getTestTimeline', () => {
  let service: HttpTestService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        HttpTestService,
        {provide: APP_DATA, useValue: {labConsoleServerUrl: 'http://bff'}},
      ],
    });
    service = TestBed.inject(HttpTestService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('sends GET /v6/tests/{testId}/timeline with job_id and optional sub_test_id', () => {
    let responseTimeline: TimeBreakdown | undefined;
    service
      .getTestTimeline({
        testId: 'test_123',
        jobId: 'job_456',
        subTestId: 'sub_1',
      })
      .subscribe((resp) => {
        responseTimeline = resp.timeline;
      });

    const req = httpMock.expectOne(
      (r) =>
        r.url === 'http://bff/v6/tests/test_123/timeline' &&
        r.params.get('job_id') === 'job_456' &&
        r.params.get('sub_test_id') === 'sub_1',
    );
    expect(req.request.method).toBe('GET');
    req.flush({timeline: {summaryLines: ['Ran for 5.0s.']}});
    expect(responseTimeline?.summaryLines).toEqual(['Ran for 5.0s.']);

    service
      .getTestTimeline({
        testId: 'test_123',
        jobId: 'job_456',
      })
      .subscribe();
    const reqNoSub = httpMock.expectOne(
      (r) =>
        r.url === 'http://bff/v6/tests/test_123/timeline' &&
        r.params.get('job_id') === 'job_456' &&
        !r.params.has('sub_test_id'),
    );
    expect(reqNoSub.request.method).toBe('GET');
    reqNoSub.flush({timeline: {}});
  });
});
