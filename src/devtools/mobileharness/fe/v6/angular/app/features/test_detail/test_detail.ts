import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  signal,
} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {Title} from '@angular/platform-browser';
import {ActivatedRoute, RouterModule} from '@angular/router';
import {NavLink} from '@deviceinfra/app/shared/components/nav_link/nav_link';
import {LoadingService} from '@deviceinfra/app/shared/services/loading_service';
import {of} from 'rxjs';
import {catchError, map, switchMap, tap} from 'rxjs/operators';

import {TestOverviewData} from '../../core/models/test_overview';
import {TEST_SERVICE} from '../../core/services/test/test_service';
import {useCopyToClipboard} from '@deviceinfra/app/shared/composables/copy';
import {TestLogTab} from './components/test_log_tab/test_log_tab';
import {TestOverviewTab} from './components/test_overview_tab/test_overview_tab';
import {TestTimelineTab} from './components/test_timeline_tab/test_timeline_tab';

interface TestPageData {
  testOverviewData: TestOverviewData | null;
  error?: string;
}

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

  constructor() {
    effect(() => {
      const id = this.testId();
      if (id) {
        const shortId = id.substring(0, 8);
        this.titleService.setTitle(`OmniLab Console - Test ${shortId}...`);
      } else {
        this.titleService.setTitle('OmniLab Console');
      }
    });

    this.destroyRef.onDestroy(() => {
      this.titleService.setTitle('OmniLab Console');
    });
  }

  readonly activeTab = signal<'overview' | 'timeline' | 'log'>('overview');

  readonly testPageData = toSignal(
    this.route.paramMap.pipe(
      map((params) => params.get('id')),
      tap(() => {
        this.loadingService.show();
      }),
      switchMap((id: string | null) => {
        if (!id) {
          this.loadingService.hide();
          return of<TestPageData>({
            testOverviewData: null,
            error: 'No test ID provided in the route.',
          });
        }

        return this.testService.getTest(id).pipe(
          map(
            (testOverviewData) =>
              ({
                testOverviewData,
              }) as TestPageData,
          ),
          catchError((err) => {
            console.error(`Error fetching test ${id}:`, err);
            return of<TestPageData>({
              testOverviewData: null,
              error: `Failed to load test data for ID: ${id}. ${err.message || ''}`,
            });
          }),
          tap((data) => {
            this.loadingService.hide();
            if (data && data.testOverviewData) {
              const test = data.testOverviewData;
              if (this.getStatusPillLabel(test) === 'RUNNING') {
                this.activeTab.set('log');
              } else {
                this.activeTab.set('overview');
              }
            }
          }),
        );
      }),
    ),
  );

  setActiveTab(tab: 'overview' | 'timeline' | 'log') {
    this.activeTab.set(tab);
  }

  getStatusPillLabel(test: TestOverviewData): string {
    if (!test || !test.status) return 'UNKNOWN';
    return test.status;
  }

  getResultPillLabel(test: TestOverviewData): string | null {
    if (!test || !test.result) return null;
    return test.result === 'UNKNOWN' ? null : test.result;
  }
}
