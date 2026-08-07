import {
  ExecutionDetails,
  FileExplorer,
  FileInfo,
  JobResult,
  JobStatus,
  SessionResult,
  SessionStatus,
  Troubleshooting,
} from './common_models';

/** Represents a lightweight child job within the session. */
export declare interface SessionJob {
  id: string;
  name: string;
  status: JobStatus;
  result?: JobResult;
  startTime?: string;
  endTime?: string;
}

/** Legacy type alias for Session file metadata, mapped to FileInfo. */
export type SessionFile = FileInfo;
export type {FileExplorer};

/** Detailed information of a session. */
export declare interface SessionDetail {
  id: string;
  name: string;
  status: SessionStatus;
  result?: SessionResult;
  spongeLink?: string;
  client?: string;
  version?: string;
  executor?: string;
  labels?: string[];
  troubleshooting?: Troubleshooting;
  executionDetails?: ExecutionDetails;
  properties?: {[key: string]: string};
  jobs?: SessionJob[];
  fileExplorer?: FileExplorer;
}

/** Request structure for active session detail retrieval. */
export declare interface GetSessionRequest {
  sessionId: string;
}

/** Response structure returning detail info for session. */
export declare interface GetSessionResponse {
  session: SessionDetail;
}

/** Request payload to obtain logs for a session. */
export declare interface GetSessionLogRequest {
  sessionId: string;
  offset: number;
  contentHash?: string;
}

/** Captured responses containing log files for session. */
export declare interface GetSessionLogResponse {
  logContent: string;
  nextOffset: number;
  sessionStatus: SessionStatus;
  logReset: boolean;
  contentHash: string;
}

/** Request payload to fetch a session generated file. */
export declare interface GetSessionFileRequest {
  sessionId: string;
  filePath: string;
}

/** Response payload for a session generated file content. */
export declare interface GetSessionFileResponse {
  content: string;
}
