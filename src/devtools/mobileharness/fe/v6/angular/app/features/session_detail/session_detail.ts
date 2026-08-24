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
import {Observable, of} from 'rxjs';
import {catchError, map} from 'rxjs/operators';

import {APP_DATA, getLegacyFeUrl} from '../../core/models/app_data';
import {SessionStatus} from '../../core/models/common_models';
import {SESSION_SERVICE} from '../../core/services/session/session_service';
import {LegacyConsoleBanner} from '../../shared/components/legacy_console_banner/legacy_console_banner';
import {useCopyToClipboard} from '../../shared/composables/copy';
import {usePageTitle} from '../../shared/composables/page_title';
import {useSilentResource} from '../../shared/composables/silent_resource';
import {TooltipIfTruncatedDirective} from '../../shared/directives/tooltip_if_truncated/tooltip_if_truncated';
import {SessionFilesTab} from './components/session_files_tab/session_files_tab';
import {SessionLogTab} from './components/session_log_tab/session_log_tab';
import {SessionOverviewTab} from './components/session_overview_tab/session_overview_tab';

import {SessionPageData} from './models/session_page_ui';

/** Component for displaying the detailed information of a single session. */
@Component({
  selector: 'app-session-detail',
  standalone: true,
  templateUrl: './session_detail.ng.html',
  styleUrl: './session_detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatIconModule,
    MatTooltipModule,
    RouterModule,
    SessionOverviewTab,
    SessionLogTab,
    SessionFilesTab,
    TooltipIfTruncatedDirective,
    LegacyConsoleBanner,
  ],
})
export class SessionDetailPage {
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly sessionService = inject(SESSION_SERVICE);
  private readonly appData = inject(APP_DATA);
  readonly legacyFeUrl = getLegacyFeUrl(this.appData.applicationId ?? '');
  readonly copyToClipboard = useCopyToClipboard();

  readonly activeTab = signal<'overview' | 'log' | 'files'>('overview');
  readonly copiedSessionId = signal<boolean>(false);

  private readonly sessionId = toSignal(
    this.activatedRoute.paramMap.pipe(map((params) => params.get('id'))),
  );

  private readonly silentResourceResult = useSilentResource<
    SessionPageData,
    string | null
  >({
    params: () => this.sessionId() || null,
    stream: (id) => {
      if (!id) {
        return of<SessionPageData>({
          sessionDetail: null,
          error: 'No session ID provided in the route.',
        });
      }

      return this.sessionService.getSession(id).pipe(
        map(
          (response) =>
            ({
              sessionDetail: response.session,
            }) as SessionPageData,
        ),
        catchError((err) => {
          console.error(`Error fetching session ${id}:`, err);
          return of<SessionPageData>({
            sessionDetail: null,
            error: `Failed to load session data for ID: ${id}. ${err.message || ''}`,
          });
        }),
      );
    },
    onInitialLoad: (data) => {
      if (data?.sessionDetail) {
        this.activeTab.set(
          data.sessionDetail.status === SessionStatus.SESSION_STATUS_RUNNING
            ? 'log'
            : 'overview',
        );
      }
    },
  });

  readonly sessionResource = this.silentResourceResult.resource;

  readonly sessionPageData = computed(
    () => this.sessionResource.value() || null,
  );

  readonly session = computed(
    () => this.sessionPageData()?.sessionDetail || null,
  );
  readonly errorMessage = computed(() => this.sessionPageData()?.error || null);

  readonly pageTitle = computed(() => {
    const id = this.session()?.id;
    return id ? `OmniLab Console - Session ${id.substring(0, 8)}...` : null;
  });

  constructor() {
    usePageTitle(this.pageTitle);
  }

  readonly statusPillLabel = computed(() => {
    const sessionVal = this.session();
    if (!sessionVal || !sessionVal.status) return 'UNKNOWN';
    return sessionVal.status.replace('SESSION_STATUS_', '');
  });

  readonly resultPillLabel = computed(() => {
    const sessionVal = this.session();
    if (!sessionVal || !sessionVal.result) return null;
    return sessionVal.result.replace('SESSION_RESULT_', '');
  });

  setActiveTab(tab: 'overview' | 'log' | 'files') {
    this.activeTab.set(tab);
  }

  onLogStreamCompleted() {
    const sessionVal = this.session();
    if (
      sessionVal &&
      sessionVal.status === SessionStatus.SESSION_STATUS_RUNNING
    ) {
      this.silentResourceResult.reloadSilent();
    }
  }

  copySessionId(id: string) {
    this.copyToClipboard(id, 'Session ID copied to clipboard!');
    this.copiedSessionId.set(true);
    setTimeout(() => {
      this.copiedSessionId.set(false);
    }, 2000);
  }

  readonly getSessionFileContent = (path: string): Observable<string> => {
    const sessionVal = this.session();
    if (!sessionVal) return of('');
    return this.sessionService
      .getSessionFile({
        sessionId: sessionVal.id,
        filePath: path,
      })
      .pipe(map((resp) => resp.content));
  };
}
