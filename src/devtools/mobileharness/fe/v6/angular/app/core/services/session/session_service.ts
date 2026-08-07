import {InjectionToken} from '@angular/core';
import {Observable} from 'rxjs';
import {
  GetSessionFileRequest,
  GetSessionFileResponse,
  GetSessionLogRequest,
  GetSessionLogResponse,
  GetSessionRequest,
  GetSessionResponse,
} from '../../models/session_overview';

/**
 * Abstract service defining operations for Mobile Harness Session Management.
 *
 * Provides methods for retrieving session metadata, fetching session execution logs,
 * and reading generated files associated with a session.
 */
export abstract class SessionService {
  /**
   * Retrieves detail information for a specific session by its ID or request payload.
   *
   * @param request Session ID string or GetSessionRequest object.
   * @return Observable emitting the session detail response.
   */
  abstract getSession(
    request: string | GetSessionRequest,
  ): Observable<GetSessionResponse>;

  /**
   * Retrieves log content for a specific session with offset-based pagination.
   *
   * @param request GetSessionLogRequest specifying session ID, offset, and optional content hash.
   * @return Observable emitting log chunk and pagination metadata.
   */
  abstract getSessionLog(
    request: GetSessionLogRequest,
  ): Observable<GetSessionLogResponse>;

  /**
   * Retrieves the contents of a generated file associated with a session.
   *
   * @param request GetSessionFileRequest specifying session ID and relative file path.
   * @return Observable emitting file content payload.
   */
  abstract getSessionFile(
    request: GetSessionFileRequest,
  ): Observable<GetSessionFileResponse>;
}

/** InjectionToken for accessing the SessionService implementation. */
export const SESSION_SERVICE = new InjectionToken<SessionService>(
  'SessionService',
);
