import {Injectable} from '@angular/core';
import {Observable, of, throwError} from 'rxjs';
import {delay} from 'rxjs/operators';
import {GetJobLogResponse, JobOverviewData} from '../../models/job_overview';
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
   * Retrieves the detailed overview data for a specific job by its ID from the mock dataset.
   */
  override getJob(id: string): Observable<JobOverviewData> {
    const scenario = MOCK_JOB_SCENARIOS.find((s) => s.id === id || s.overview.id === id);
    if (scenario) {
      return of(scenario.overview).pipe(delay(1000));
    } else {
      return throwError(
        () => new Error(`Job with ID '${id}' not found in mock data.`),
      ).pipe(delay(1000));
    }
  }

  /**
   * Retrieves a chunk of logs for a specific job.
   */
  override getJobLog(
    id: string,
    offset: number,
    length: number,
  ): Observable<GetJobLogResponse> {
    const scenario = MOCK_JOB_SCENARIOS.find((s) => s.id === id || s.overview.id === id);
    if (!scenario) {
      return throwError(
        () => new Error(`Job with ID '${id}' not found in mock data.`),
      );
    }
    const fullLog = scenario.log || '';
    const slice = fullLog.substring(offset, offset + length);
    const nextOffset = offset + slice.length;
    const hasMore = nextOffset < fullLog.length;

    return of({
      logContent: slice,
      nextOffset,
      hasMore,
      cloudLogLink: scenario.cloudLogLink || '#',
    }).pipe(delay(500));
  }
}
