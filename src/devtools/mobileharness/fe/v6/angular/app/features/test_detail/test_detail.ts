import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {ActivatedRoute, RouterModule} from '@angular/router';
import {combineLatest, of} from 'rxjs';
import {catchError, map} from 'rxjs/operators';

import {NavLink} from '@deviceinfra/app/shared/components/nav_link/nav_link';
import {useCopyToClipboard} from '@deviceinfra/app/shared/composables/copy';
import {usePageTitle} from '@deviceinfra/app/shared/composables/page_title';
import {useSilentResource} from '@deviceinfra/app/shared/composables/silent_resource';
import {APP_DATA, getLegacyFeUrl} from '../../core/models/app_data';
import {
  GetTestRequest,
  TestResult,
  TestStatus,
} from '../../core/models/test_overview';
import {TEST_SERVICE} from '../../core/services/test/test_service';
import {LegacyConsoleBanner} from '../../shared/components/legacy_console_banner/legacy_console_banner';
import {TooltipIfTruncatedDirective} from '../../shared/directives/tooltip_if_truncated/tooltip_if_truncated';
import {TestFilesTab} from './components/test_files_tab/test_files_tab';
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
    TestFilesTab,
    NavLink,
    TooltipIfTruncatedDirective,
    LegacyConsoleBanner,
  ],
})
export class TestDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly testService = inject(TEST_SERVICE);
  private readonly appData = inject(APP_DATA);
  readonly legacyFeUrl = getLegacyFeUrl(this.appData.applicationId ?? '');
  readonly copyToClipboard = useCopyToClipboard();

  readonly testId = toSignal(
    this.route.paramMap.pipe(map((params) => params.get('id'))),
    {initialValue: null},
  );

  readonly activeTab = signal<'overview' | 'timeline' | 'log' | 'files'>(
    'overview',
  );

  private readonly routeParams = toSignal(
    combineLatest([this.route.paramMap, this.route.queryParamMap]).pipe(
      map(([params, queryParams]) => ({
        testId: params.get('id'),
        subTestId:
          queryParams.get('sub_test_id') ||
          queryParams.get('sub_test') ||
          undefined,
        jobId: params.get('jobId') || '',
      })),
    ),
  );

  private readonly silentResourceResult = useSilentResource<
    TestPageData,
    {testId?: string | null; subTestId?: string; jobId: string} | null
  >({
    params: () => {
      const rp = this.routeParams();
      return rp
        ? {
            testId: rp.testId || null,
            subTestId: rp.subTestId,
            jobId: rp.jobId || '',
          }
        : null;
    },
    stream: (params) => {
      if (!params || !params.testId) {
        return of<TestPageData>({
          testOverviewData: null,
          error: 'No test ID provided in the route.',
          jobId: params?.jobId || '',
        });
      }

      const {testId, subTestId, jobId} = params;

      const request: GetTestRequest = {testId, jobId};
      if (subTestId) {
        request.subTestId = subTestId;
      }

      const idForErrorLogging = subTestId || testId;

      return this.testService.getTest(request).pipe(
        map(
          (response) =>
            ({
              testOverviewData: response.test,
              jobId,
            }) as TestPageData,
        ),
        catchError((err) => {
          console.error(`Error fetching test ${idForErrorLogging}:`, err);
          return of<TestPageData>({
            testOverviewData: null,
            error: `Failed to load test data for ID: ${idForErrorLogging}. ${err.message || ''}`,
            jobId,
          });
        }),
      );
    },
    onInitialLoad: (data) => {
      if (data?.testOverviewData) {
        const test = data.testOverviewData;
        this.activeTab.set(
          test.status === TestStatus.TEST_STATUS_RUNNING ? 'log' : 'overview',
        );
      }
    },
  });

  readonly testResource = this.silentResourceResult.resource;

  readonly testPageData = computed(() => this.testResource.value() || null);

  readonly testOverview = computed(
    () => this.testPageData()?.testOverviewData ?? null,
  );
  readonly test = this.testOverview;
  readonly errorMessage = computed(() => this.testPageData()?.error || null);

  readonly jobId = computed(
    () => this.testOverview()?.job?.id || this.testPageData()?.jobId || '',
  );

  readonly pageTitle = computed(() => {
    const test = this.testOverview();
    const id = this.testId();
    if (test?.name) {
      return `OmniLab Console - ${test.name}`;
    }
    if (id) {
      return `OmniLab Console - Test ${id.substring(0, 8)}...`;
    }
    return null;
  });

  constructor() {
    usePageTitle(this.pageTitle);
  }

  readonly statusDisplay = computed(() => {
    const status =
      this.testOverview()?.status ?? TestStatus.TEST_STATUS_UNSPECIFIED;
    return TEST_STATUS_DISPLAY_MAP[
      status as keyof typeof TEST_STATUS_DISPLAY_MAP
    ];
  });

  readonly resultDisplay = computed(() => {
    const result = this.testOverview()?.result;
    return !result || result === TestResult.TEST_RESULT_UNSPECIFIED
      ? null
      : TEST_RESULT_DISPLAY_MAP[result as keyof typeof TEST_RESULT_DISPLAY_MAP];
  });

  readonly getTestFileContent = (path: string) => {
    const test = this.testOverview();
    const jobId = test?.job?.id || this.testPageData()?.jobId || '';
    const testId = this.testId() || '';
    return this.testService.getTestFile(testId, jobId, path);
  };

  setActiveTab(tab: 'overview' | 'timeline' | 'log' | 'files') {
    this.activeTab.set(tab);
  }

  onLogStreamCompleted() {
    const test = this.testOverview();
    if (test && test.status === TestStatus.TEST_STATUS_RUNNING) {
      this.silentResourceResult.reloadSilent();
    }
  }
}
