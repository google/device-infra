import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {Title} from '@angular/platform-browser';
import {ActivatedRoute, RouterModule} from '@angular/router';
import {combineLatest, of} from 'rxjs';
import {
  catchError,
  distinctUntilChanged,
  finalize,
  map,
  switchMap,
  tap,
} from 'rxjs/operators';

import {NavLink} from '@deviceinfra/app/shared/components/nav_link/nav_link';
import {useCopyToClipboard} from '@deviceinfra/app/shared/composables/copy';
import {LoadingService} from '@deviceinfra/app/shared/services/loading_service';
import {
  GetTestRequest,
  TestResult,
  TestStatus,
} from '../../core/models/test_overview';
import {TEST_SERVICE} from '../../core/services/test/test_service';
import {TestLogTab} from './components/test_log_tab/test_log_tab';
import {TestOverviewTab} from './components/test_overview_tab/test_overview_tab';
import {TestTimelineTab} from './components/test_timeline_tab/test_timeline_tab';
import {TestPageData} from './models/test_page_ui';
import {
  TEST_RESULT_DISPLAY_MAP,
  TEST_STATUS_DISPLAY_MAP,
} from './models/test_status_ui';

/**
 * Component for displaying the detailed information of a single test.
 * It fetches test data based on the ID from the route parameters and
 * presents different tabs for overview, timeline, and logs.
 */
@Component({
  selector: 'app-test-detail',
  standalone: true,
  templateUrl: './test_detail.ng.html',
  styleUrl: './test_detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatIconModule,
    MatTooltipModule,
    RouterModule,
    TestOverviewTab,
    TestTimelineTab,
    TestLogTab,
    NavLink,
  ],
})
export class TestDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly testService = inject(TEST_SERVICE);
  private readonly titleService = inject(Title);
  private readonly loadingService = inject(LoadingService);
  readonly copyToClipboard = useCopyToClipboard();
  private readonly destroyRef = inject(DestroyRef);

  readonly testId = toSignal(
    this.route.paramMap.pipe(map((params) => params.get('id'))),
    {initialValue: null},
  );

  readonly activeTab = signal<'overview' | 'timeline' | 'log'>('overview');

  readonly testPageData = toSignal(
    combineLatest([this.route.paramMap, this.route.queryParamMap]).pipe(
      map(([params, queryParams]) => ({
        testId: params.get('id'),
        subTestId:
          queryParams.get('sub_test_id') ||
          queryParams.get('sub_test') ||
          undefined,
        jobId: params.get('jobId') || '',
      })),
      distinctUntilChanged(
        (a, b) =>
          a.testId === b.testId &&
          a.subTestId === b.subTestId &&
          a.jobId === b.jobId,
      ),
      tap(() => {
        this.loadingService.show();
      }),
      switchMap(({testId, subTestId, jobId}) => {
        if (!testId) {
          this.loadingService.hide();
          return of<TestPageData>({
            testOverviewData: null,
            error: 'No test ID provided in the route.',
            jobId,
          });
        }

        const request: GetTestRequest = {testId, jobId};
        if (subTestId) {
          request.subTestId = subTestId;
        }

        const idForErrorLogging = subTestId || testId;

        return this.testService.getTest(request).pipe(
          map(
            (testOverviewData) => ({testOverviewData, jobId}) as TestPageData,
          ),
          tap((data) => {
            if (data?.testOverviewData) {
              const test = data.testOverviewData;
              this.activeTab.set(
                test.status === TestStatus.TEST_STATUS_RUNNING
                  ? 'log'
                  : 'overview',
              );
            }
          }),
          catchError((err) => {
            console.error(`Error fetching test ${idForErrorLogging}:`, err);
            return of<TestPageData>({
              testOverviewData: null,
              error: `Failed to load test data for ID: ${idForErrorLogging}. ${err.message || ''}`,
              jobId,
            });
          }),
          finalize(() => {
            this.loadingService.hide();
          }),
        );
      }),
    ),
  );

  readonly testOverview = computed(
    () => this.testPageData()?.testOverviewData ?? null,
  );

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.titleService.setTitle('OmniLab Console');
    });
    effect(() => {
      const test = this.testOverview();
      const id = this.testId();
      if (test?.name) {
        this.titleService.setTitle(`OmniLab Console - ${test.name}`);
      } else if (id) {
        const shortId = id.substring(0, 8);
        this.titleService.setTitle(`OmniLab Console - Test ${shortId}...`);
      } else {
        this.titleService.setTitle('OmniLab Console');
      }
    });
  }

  readonly statusDisplay = computed(
    () =>
      TEST_STATUS_DISPLAY_MAP[
        this.testOverview()?.status ?? TestStatus.TEST_STATUS_UNSPECIFIED
      ],
  );

  readonly resultDisplay = computed(() => {
    const result = this.testOverview()?.result;
    return !result || result === TestResult.TEST_RESULT_UNSPECIFIED
      ? null
      : TEST_RESULT_DISPLAY_MAP[result];
  });

  setActiveTab(tab: 'overview' | 'timeline' | 'log') {
    this.activeTab.set(tab);
  }
}
