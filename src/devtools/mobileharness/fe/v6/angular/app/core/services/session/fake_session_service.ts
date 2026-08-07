import {Injectable} from '@angular/core';
import {Observable, of, throwError} from 'rxjs';
import {delay} from 'rxjs/operators';

import {SessionStatus} from '../../models/common_models';
import {
  GetSessionFileRequest,
  GetSessionFileResponse,
  GetSessionLogRequest,
  GetSessionLogResponse,
  GetSessionRequest,
  GetSessionResponse,
} from '../../models/session_overview';
import {MOCK_SESSION_SCENARIOS} from '../mock_data';
import {SessionService} from './session_service';

/**
 * A fake implementation of the SessionService for development and testing.
 */
@Injectable({
  providedIn: 'root',
})
export class FakeSessionService extends SessionService {
  private readonly runningLineOffsets = new Map<string, number>();
  private readonly completedRunningSessions = new Set<string>();

  constructor() {
    super();
  }

  override getSession(
    request: string | GetSessionRequest,
  ): Observable<GetSessionResponse> {
    const id = typeof request === 'string' ? request : request.sessionId;
    const scenario = MOCK_SESSION_SCENARIOS.find(
      (s) => s.id === id || s.overview.id === id,
    );
    if (scenario) {
      let status = scenario.overview.status;
      if (
        status === SessionStatus.SESSION_STATUS_RUNNING &&
        this.completedRunningSessions.has(scenario.id)
      ) {
        status = SessionStatus.SESSION_STATUS_DONE;
      }

      return of({
        session: {
          ...scenario.overview,
          status,
        },
      }).pipe(delay(1000));
    } else {
      return throwError(
        () => new Error(`Session with ID '${id}' not found in mock data.`),
      ).pipe(delay(1000));
    }
  }

  override getSessionLog(
    request: GetSessionLogRequest,
  ): Observable<GetSessionLogResponse> {
    const id = request.sessionId;
    const numOffset = Number(request.offset) || 0;
    const scenario = MOCK_SESSION_SCENARIOS.find(
      (s) => s.id === id || s.overview.id === id,
    );
    if (!scenario) {
      return throwError(
        () => new Error(`Session with ID '${id}' not found in mock data.`),
      ).pipe(delay(300));
    }

    const fullLog = scenario.log || '';
    const status = scenario.overview.status;

    if (numOffset === 0) {
      this.completedRunningSessions.delete(scenario.id);
      this.runningLineOffsets.delete(scenario.id);
    }

    if (status !== SessionStatus.SESSION_STATUS_RUNNING) {
      const chunk = fullLog.substring(numOffset);
      const nextOffset = numOffset + chunk.length;

      return of({
        logContent: chunk,
        nextOffset,
        sessionStatus: status,
        logReset: false,
        contentHash: 'mock-hash',
      }).pipe(delay(300));
    }

    const lines = fullLog.split('\n');
    const currentLineOffset = this.runningLineOffsets.get(scenario.id) || 0;
    const newLineOffset = Math.min(currentLineOffset + 3, lines.length);
    this.runningLineOffsets.set(scenario.id, newLineOffset);

    const visibleLog =
      lines.slice(0, newLineOffset).join('\n') +
      (newLineOffset < lines.length ? '\n' : '');
    const chunk = visibleLog.substring(numOffset);
    const nextOffset = numOffset + chunk.length;

    if (newLineOffset >= lines.length) {
      this.completedRunningSessions.add(scenario.id);
    }

    return of({
      logContent: chunk,
      nextOffset,
      sessionStatus: SessionStatus.SESSION_STATUS_RUNNING,
      logReset: false,
      contentHash: 'mock-hash',
    }).pipe(delay(300));
  }

  override getSessionFile(
    request: GetSessionFileRequest,
  ): Observable<GetSessionFileResponse> {
    const id = request.sessionId;
    const filePath = request.filePath;
    const scenario = MOCK_SESSION_SCENARIOS.find(
      (s) => s.id === id || s.overview.id === id,
    );
    if (!scenario) {
      return throwError(
        () => new Error(`Session with ID '${id}' not found in mock data.`),
      );
    }
    const sessionOverview = scenario.overview;
    const file = sessionOverview.fileExplorer?.files?.find(
      (f) => f.path === filePath,
    );
    if (!file) {
      return throwError(
        () => new Error(`File '${filePath}' not found in session mockup.`),
      );
    }
    return of({
      content: `mocked content for ${filePath}`,
    }).pipe(delay(500));
  }
}
