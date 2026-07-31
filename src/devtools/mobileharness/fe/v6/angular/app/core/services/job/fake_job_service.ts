import {Injectable} from '@angular/core';
import {Observable, of, throwError} from 'rxjs';
import {delay} from 'rxjs/operators';
import {
  GetJobFileResponse,
  GetJobLogRequest,
  GetJobLogResponse,
  GetJobRequest,
  GetJobResponse,
  JobStatus,
} from '../../models/job_overview';
import {MOCK_JOB_SCENARIOS} from '../mock_data';
import {JobService} from './job_service';

/**
 * A fake implementation of the JobService for development and testing.
 * It uses the mock data defined in the central mock_data registry.
 */
@Injectable({
  providedIn: 'root',
})
export class FakeJobService extends JobService {
  constructor() {
    super();
  }

  /**
   * Retrieves the detailed overview data and actions for a specific job by its ID from the mock dataset.
   */
  override getJob(request: string | GetJobRequest): Observable<GetJobResponse> {
    const id = typeof request === 'string' ? request : request.jobId;
    const scenario = MOCK_JOB_SCENARIOS.find(
      (s) =>
        s.id === id ||
        s.overview.id === id ||
        (id === '1845eb94-459b-4028-a9b4-0f13558fdc61' &&
          s.overview.id === 'b65cadd7-6ad6-440e-a3b7-bfe1948557e6'),
    );
    if (scenario) {
      return of({
        job: scenario.overview,
        actions: scenario.actions,
      }).pipe(delay(1000));
    } else {
      return throwError(
        () => new Error(`Job with ID '${id}' not found in mock data.`),
      ).pipe(delay(1000));
    }
  }

  /**
   * Retrieves a chunk of logs for a specific job.
   */
  override getJobLog(request: GetJobLogRequest): Observable<GetJobLogResponse> {
    const id = request.jobId;
    const offset = request.offset;
    const scenario = MOCK_JOB_SCENARIOS.find(
      (s) =>
        s.id === id ||
        s.overview.id === id ||
        (id === '1845eb94-459b-4028-a9b4-0f13558fdc61' &&
          s.overview.id === 'b65cadd7-6ad6-440e-a3b7-bfe1948557e6'),
    );
    if (!scenario) {
      return throwError(
        () => new Error(`Job with ID '${id}' not found in mock data.`),
      );
    }
    const fullLog = scenario.log || '';
    const slice = fullLog.substring(offset);
    const nextOffset = offset + slice.length;

    return of({
      logContent: slice,
      nextOffset,
      jobStatus: scenario.overview.status || JobStatus.JOB_STATUS_DONE,
      logReset: false,
      contentHash: 'mock-hash',
    }).pipe(delay(500));
  }

  /**
   * Retrieves the content of a specific file associated with a Job.
   */
  override getJobFile(
    id: string,
    filePath: string,
  ): Observable<GetJobFileResponse> {
    const scenario = MOCK_JOB_SCENARIOS.find(
      (s) =>
        s.id === id ||
        s.overview.id === id ||
        (id === '1845eb94-459b-4028-a9b4-0f13558fdc61' &&
          s.overview.id === 'b65cadd7-6ad6-440e-a3b7-bfe1948557e6'),
    );
    if (!scenario) {
      return throwError(
        () => new Error(`Job with ID '${id}' not found in mock data.`),
      );
    }
    const jobOverview = scenario.overview;
    const file = jobOverview.fileExplorer?.files?.find(
      (f) => f.path === filePath,
    );
    if (!file) {
      return throwError(
        () => new Error(`File '${filePath}' not found in job mockup.`),
      );
    }
    if (filePath.includes('undetermined_size_heavy_manifest')) {
      const error = new Error(
        'File undetermined_size_heavy_manifest.txt is too large',
      );
      Object.assign(error, {status: 413});
      return throwError(() => error).pipe(delay(500));
    }
    return of({
      content: (file as {content?: string}).content || '',
    }).pipe(delay(500));
  }
}
