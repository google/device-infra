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
  template: `<app-test-timeline-tab [testId]="testId()" [jobId]="jobId()" [status]="status()"></app-test-timeline-tab>`,
})
class TestHostComponent {
  readonly testId = signal<string>('test_123');
  readonly jobId = signal<string>('job_456');
  readonly status = signal<string | undefined>('TEST_STATUS_RUNNING');
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

  it('refetches when testId or status changes and preserves loaded timeline across transient refresh errors', () => {
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
    const nextTestLoad$ = new Subject<{timeline?: TimeBreakdown}>();
    mockTestService.getTestTimeline.and.returnValue(
      nextTestLoad$.asObservable(),
    );

    host.testId.set('test_99');
    fixture.detectChanges();
    expect(mockTestService.getTestTimeline).toHaveBeenCalledWith({
      testId: 'test_99',
      jobId: 'job_456',
    });
    expect(component.loading()).toBeTrue();
    expect(component.timingBreakdown()).toBeUndefined();

    nextTestLoad$.next({timeline: populated});
    nextTestLoad$.complete();
    fixture.detectChanges();
    expect(component.loading()).toBeFalse();
    expect(component.timingBreakdown()).toEqual(populated);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Ran for 12s.');

    const statusRefresh$ = new Subject<{timeline?: TimeBreakdown}>();
    mockTestService.getTestTimeline.and.returnValue(
      statusRefresh$.asObservable(),
    );
    host.status.set('TEST_STATUS_DONE');
    fixture.detectChanges();
    expect(mockTestService.getTestTimeline).toHaveBeenCalledTimes(3);
    expect(component.loading()).toBeFalse();
    expect(component.timingBreakdown()).toEqual(populated);
    statusRefresh$.next({timeline: populated});
    statusRefresh$.complete();
    fixture.detectChanges();

    mockTestService.getTestTimeline.and.returnValue(
      throwError(() => new Error('Transient error')),
    );
    host.status.set('TEST_STATUS_SUSPENDED');
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

  it('sends GET /v6/tests/{testId}/timeline with job_id', () => {
    let responseTimeline: TimeBreakdown | undefined;
    service
      .getTestTimeline({
        testId: 'test_123',
        jobId: 'job_456',
      })
      .subscribe((resp) => {
        responseTimeline = resp.timeline;
      });

    const req = httpMock.expectOne(
      (r) =>
        r.url === 'http://bff/v6/tests/test_123/timeline' &&
        r.params.get('job_id') === 'job_456',
    );
    expect(req.request.method).toBe('GET');
    req.flush({timeline: {summaryLines: ['Ran for 5.0s.']}});
    expect(responseTimeline?.summaryLines).toEqual(['Ran for 5.0s.']);
  });
});
