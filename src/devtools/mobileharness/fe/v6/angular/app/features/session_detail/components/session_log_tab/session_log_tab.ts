import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import {map} from 'rxjs/operators';
import {SessionStatus} from '../../../../core/models/common_models';
import {SESSION_SERVICE} from '../../../../core/services/session/session_service';
import {
  LogFetchStrategy,
  StreamingLogViewerComponent,
} from '../../../../shared/components/streaming_log_viewer/streaming_log_viewer';

/** Component for rendering the session log tab content using generic log viewer. */
@Component({
  selector: 'app-session-log-tab',
  standalone: true,
  imports: [StreamingLogViewerComponent],
  templateUrl: './session_log_tab.ng.html',
  styleUrl: './session_log_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionLogTab {
  /** The unique session ID passed from the parent component. */
  readonly sessionId = input.required<string>();
  readonly streamCompleted = output<void>();

  private readonly sessionService = inject(SESSION_SERVICE);

  readonly logViewer = viewChild(StreamingLogViewerComponent);
  readonly logLines = computed(() => this.logViewer()?.logLines() || []);
  readonly logViewport = computed(() => this.logViewer()?.logViewport());

  readonly logStrategy: LogFetchStrategy = (offset: number, hash?: string) =>
    this.sessionService
      .getSessionLog({
        sessionId: this.sessionId(),
        offset,
        contentHash: hash,
      })
      .pipe(
        map((resp) => {
          const content =
            resp.logContent ||
            (resp as unknown as Record<string, string>)['log_content'] ||
            '';
          return {
            offset:
              resp.nextOffset !== undefined ? resp.nextOffset : content.length,
            content,
            isDone:
              resp.sessionStatus !== undefined
                ? resp.sessionStatus === SessionStatus.SESSION_STATUS_DONE
                : true,
            reset: !!resp.logReset,
            hash: resp.contentHash || '',
          };
        }),
      );
}
