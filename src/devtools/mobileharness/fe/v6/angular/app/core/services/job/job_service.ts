import {InjectionToken} from '@angular/core';
import {Observable} from 'rxjs';
import {
  GetJobFileRequest,
  GetJobFileResponse,
  GetJobLogRequest,
  GetJobLogResponse,
  GetJobRequest,
  GetJobResponse,
  KillJobRequest,
  KillJobResponse,
} from '../../models/job_overview';

/** Injection token for the JobService. */
export const JOB_SERVICE = new InjectionToken<JobService>('JobService');

/**
 * Abstract class defining the contract for job data operations.
 */
export abstract class JobService {
  /** Retrieves the detailed overview data for a specific job. */
  abstract getJob(request: GetJobRequest): Observable<GetJobResponse>;

  /** Retrieves a chunk of logs for a specific job. */
  abstract getJobLog(request: GetJobLogRequest): Observable<GetJobLogResponse>;

  /** Retrieves the content of a specific file associated with a Job. */
  abstract getJobFile(
    request: GetJobFileRequest,
  ): Observable<GetJobFileResponse>;

  /** Terminates a running job immediately. */
  abstract killJob(
    request: string | KillJobRequest,
  ): Observable<KillJobResponse>;
}
