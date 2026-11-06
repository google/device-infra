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

  it('refetches when testId or status changes while live, preserves loaded timeline across transient errors, and skips status-only refetches once final', () => {
    const livePopulated: TimeBreakdown = {
      summaryLines: ['Running for 12s so far.'],
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
      live: true,
    };
    const finalPopulated: TimeBreakdown = {
      ...livePopulated,
      summaryLines: ['Ran for 12s.'],
      live: false,
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

    nextTestLoad$.next({timeline: livePopulated});
    nextTestLoad$.complete();
    fixture.detectChanges();
    expect(component.loading()).toBeFalse();
    expect(component.timingBreakdown()).toEqual(livePopulated);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Running for 12s so far.');

    mockTestService.getTestTimeline.and.returnValue(
      throwError(() => new Error('Transient error')),
    );
    host.status.set('TEST_STATUS_ASSIGNED');
    fixture.detectChanges();
    expect(mockTestService.getTestTimeline).toHaveBeenCalledTimes(3);
    expect(component.timingBreakdown()).toEqual(livePopulated);
    expect(component.errorMessage()).toBeNull();

    const statusRefresh$ = new Subject<{timeline?: TimeBreakdown}>();
    mockTestService.getTestTimeline.and.returnValue(
      statusRefresh$.asObservable(),
    );
    host.status.set('TEST_STATUS_DONE');
    fixture.detectChanges();
    expect(mockTestService.getTestTimeline).toHaveBeenCalledTimes(4);
    expect(component.loading()).toBeFalse();
    expect(component.timingBreakdown()).toEqual(livePopulated);
    statusRefresh$.next({timeline: finalPopulated});
    statusRefresh$.complete();
    fixture.detectChanges();
    expect(component.timingBreakdown()).toEqual(finalPopulated);

    // Once timingBreakdown holds a final (!live) populated breakdown, status changes do not refetch.
    host.status.set('TEST_STATUS_SUSPENDED');
    fixture.detectChanges();
    expect(mockTestService.getTestTimeline).toHaveBeenCalledTimes(4);
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

  it('says that a live timeline updates itself, and stops saying so once it is final', () => {
    const liveTiming: TimeBreakdown = {
      summaryLines: ['Running for 5m so far.'],
      testTimeline: {
        phases: [
          {
            phaseId: 'phase-setup',
            focusKey: 'setup',
            label: 'Running',
            fill: 'TIMELINE_FILL_OPAQUE',
            durationText: '5m',
            shareText: '100%',
            rows: [],
          },
        ],
      },
      live: true,
    };
    mockTestService.getTestTimeline.and.returnValue(of({timeline: liveTiming}));
    host.testId.set('test_live');
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain(
      'Updates every 30 seconds while the test runs.',
    );

    mockTestService.getTestTimeline.and.returnValue(
      of({timeline: {...liveTiming, live: false}}),
    );
    host.status.set('TEST_STATUS_DONE');
    fixture.detectChanges();
    expect(compiled.textContent).not.toContain('Updates every');
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

@Component({
  standalone: true,
  imports: [TestTimelineTab],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-test-timeline-tab [testId]="testId()" jobId="job_456" [active]="active()" (finished)="onFinished()"></app-test-timeline-tab>`,
})
class LiveTestHostComponent {
  readonly testId = signal<string>('test_123');
  readonly active = signal<boolean>(true);
  finishedCount = 0;

  onFinished() {
    this.finishedCount++;
  }
}

describe('TestTimelineTab live refresh', () => {
  const live: TimeBreakdown = {
    summaryLines: ['Running for 1m so far.'],
    testTimeline: {
      phases: [
        {
          phaseId: 'phase-setup',
          focusKey: 'setup',
          label: 'Running',
          fill: 'TIMELINE_FILL_OPAQUE',
          durationText: '1m',
          shareText: '100%',
          rows: [],
        },
      ],
    },
    live: true,
  };
  const final: TimeBreakdown = {
    ...live,
    summaryLines: ['Ran for 2m.'],
    live: false,
  };

  let fixture: ComponentFixture<LiveTestHostComponent>;
  let host: LiveTestHostComponent;
  let component: TestTimelineTab;
  let mockTestService: jasmine.SpyObj<TestService>;

  function createHost(active: boolean) {
    fixture = TestBed.createComponent(LiveTestHostComponent);
    host = fixture.componentInstance;
    host.active.set(active);
    fixture.detectChanges();
    component = fixture.debugElement.query(
      By.directive(TestTimelineTab),
    ).componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    mockTestService = jasmine.createSpyObj<TestService>('TestService', [
      'getTestTimeline',
    ]);
    await TestBed.configureTestingModule({
      imports: [LiveTestHostComponent, NoopAnimationsModule],
      providers: [{provide: TEST_SERVICE, useValue: mockTestService}],
    }).compileComponents();
  });

  it('refetches a live timeline every 30s until it is final, then stops and emits finished', fakeAsync(() => {
    const responses = [live, live, final];
    let call = 0;
    mockTestService.getTestTimeline.and.callFake(() =>
      of({timeline: responses[Math.min(call++, responses.length - 1)]}),
    );

    createHost(true);
    expect(mockTestService.getTestTimeline).toHaveBeenCalledTimes(1);

    tick(30_000);
    fixture.detectChanges();
    expect(mockTestService.getTestTimeline).toHaveBeenCalledTimes(2);
    expect(host.finishedCount).toBe(0);

    tick(30_000);
    fixture.detectChanges();
    expect(mockTestService.getTestTimeline).toHaveBeenCalledTimes(3);
    expect(component.timingBreakdown()).toEqual(final);
    expect(host.finishedCount).toBe(1);

    tick(60_000);
    fixture.detectChanges();
    expect(mockTestService.getTestTimeline).toHaveBeenCalledTimes(3);
    expect(host.finishedCount).toBe(1);
    fixture.destroy();
  }));

  it('does not refetch while the tab or document is hidden, and catches up when it shows again', fakeAsync(() => {
    mockTestService.getTestTimeline.and.returnValue(of({timeline: live}));
    const doc = TestBed.inject(DOCUMENT);

    createHost(false);
    tick(60_000);
    fixture.detectChanges();
    expect(mockTestService.getTestTimeline).toHaveBeenCalledTimes(1);

    host.active.set(true);
    fixture.detectChanges();
    tick(0);
    fixture.detectChanges();
    expect(mockTestService.getTestTimeline).toHaveBeenCalledTimes(2);

    const visibilitySpy = spyOnProperty(
      doc,
      'visibilityState',
      'get',
    ).and.returnValue('hidden');
    doc.dispatchEvent(new Event('visibilitychange'));
    fixture.detectChanges();

    tick(60_000);
    fixture.detectChanges();
    expect(mockTestService.getTestTimeline).toHaveBeenCalledTimes(2);

    visibilitySpy.and.returnValue('visible');
    doc.dispatchEvent(new Event('visibilitychange'));
    fixture.detectChanges();
    tick(0);
    fixture.detectChanges();
    expect(mockTestService.getTestTimeline).toHaveBeenCalledTimes(3);
    fixture.destroy();
  }));

  it('does not refetch a timeline that is not live', fakeAsync(() => {
    mockTestService.getTestTimeline.and.returnValue(of({timeline: final}));

    createHost(true);
    tick(60_000);
    fixture.detectChanges();

    expect(mockTestService.getTestTimeline).toHaveBeenCalledTimes(1);
    expect(host.finishedCount).toBe(0);
    fixture.destroy();
  }));
});
