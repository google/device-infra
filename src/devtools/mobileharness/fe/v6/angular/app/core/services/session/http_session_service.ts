import {HttpClient} from '@angular/common/http';
import {Injectable, inject} from '@angular/core';
import {Observable} from 'rxjs';

import {APP_DATA, AppData} from '../../models/app_data';
import {
  GetSessionFileRequest,
  GetSessionFileResponse,
  GetSessionLogRequest,
  GetSessionLogResponse,
  GetSessionRequest,
  GetSessionResponse,
} from '../../models/session_overview';
import {SessionService} from './session_service';

/** An implementation of the SessionService that uses HTTP to fetch data. */
@Injectable()
export class HttpSessionService extends SessionService {
  private readonly appData: AppData = inject(APP_DATA);
  private readonly apiUrl = `${this.appData.labConsoleServerUrl}/v6/sessions`;
  private readonly http = inject(HttpClient);

  constructor() {
    super();
  }

  /**
   * Fetches session detail info from the backend REST endpoint GET /v6/sessions/{id}.
   */
  override getSession(
    request: string | GetSessionRequest,
  ): Observable<GetSessionResponse> {
    const id = typeof request === 'string' ? request : request.sessionId;
    return this.http.get<GetSessionResponse>(`${this.apiUrl}/${id}`);
  }

  /**
   * Fetches log content from the backend REST endpoint GET /v6/sessions/{sessionId}/log.
   */
  override getSessionLog(
    request: GetSessionLogRequest,
  ): Observable<GetSessionLogResponse> {
    return this.http.get<GetSessionLogResponse>(
      `${this.apiUrl}/${request.sessionId}/log`,
    );
  }

  /**
   * Fetches generated file content from backend REST endpoint GET /v6/sessions/{sessionId}/file.
   */
  override getSessionFile(
    request: GetSessionFileRequest,
  ): Observable<GetSessionFileResponse> {
    const params = {
      'file_path': request.filePath,
    };
    return this.http.get<GetSessionFileResponse>(
      `${this.apiUrl}/${request.sessionId}/file`,
      {params},
    );
  }
}
