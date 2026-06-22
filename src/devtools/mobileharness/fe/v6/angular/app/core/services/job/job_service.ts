import {InjectionToken} from '@angular/core';
import {Observable} from 'rxjs';
import {GetJobLogResponse, JobOverviewData} from '../../models/job_overview';

/** Injection token for the JobService. */
export const JOB_SERVICE = new InjectionToken<JobService>('JobService');

/** Abstract class defining the contract for job data operations. */
export abstract class JobService {
  /** Retrieves the detailed overview data for a specific job by its ID. */
  abstract getJob(id: string): Observable<JobOverviewData>;

  /** Retrieves a chunk of logs for a specific job. */
  abstract getJobLog(
    id: string,
    offset: number,
    length: number,
  ): Observable<GetJobLogResponse>;
}
