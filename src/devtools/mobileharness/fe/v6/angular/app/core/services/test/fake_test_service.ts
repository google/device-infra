import {Injectable} from '@angular/core';
import {Observable, of, throwError} from 'rxjs';
import {delay} from 'rxjs/operators';
import {
  GetTestLogRequest,
  GetTestLogResponse,
  GetTestRequest,
  TestOverviewData,
  TestStatus,
} from '../../models/test_overview';
import {MOCK_TEST_SCENARIOS} from '../mock_data';
import {TestService} from './test_service';

/**
 * A fake implementation of the TestService for development and testing.
 * It uses the mock data defined in the central mock_data registry.
 */
@Injectable({
  providedIn: 'root',
})
export class FakeTestService extends TestService {
  private readonly logLimits = new Map<string, number>();

  constructor() {
    super();
  }

  /**
   * Retrieves the detailed overview data for a specific test by its ID from the mock dataset.
   */
  override getTest(request: GetTestRequest): Observable<TestOverviewData> {
    const id = request.subTestId || request.testId;
    const scenario = MOCK_TEST_SCENARIOS.find(
      (s) => s.id === id || s.overview.id === id,
    );
    if (scenario) {
      const execDetails = scenario.overview.executionDetails;
      const overview: TestOverviewData = {
        ...scenario.overview,
        executionDetails: execDetails
          ? {
              ...execDetails,
              cloudLogLink: scenario.cloudLogLink,
            }
          : undefined,
      };
      return of(overview).pipe(delay(1000));
    } else {
      return throwError(
        () => new Error(`Test with ID '${id}' not found in mock data.`),
      ).pipe(delay(1000));
    }
  }

  /**
   * Mock implementation of getTestLog that streams log chunks from the test scenario.
   */
  override getTestLog(
    request: GetTestLogRequest,
  ): Observable<GetTestLogResponse> {
    const id = request.testId;
    const offset = request.offset;
    const length = request.length;
    const scenario = MOCK_TEST_SCENARIOS.find(
      (s) => s.id === id || s.overview.id === id,
    );
    if (scenario) {
      const fullLog = scenario.log || '';
      const status = scenario.overview.status;

      let visibleLog = '';
      if (status === TestStatus.TEST_STATUS_RUNNING) {
        // Stream line-by-line if test is active
        const lines = fullLog.split('\n');
        if (Number(offset) === 0) {
          this.logLimits.set(id, Math.min(2, lines.length));
        } else {
          const currentLineLimit = this.logLimits.get(id) || 0;
          const newLineLimit = Math.min(currentLineLimit + 2, lines.length);
          this.logLimits.set(id, newLineLimit);
        }
        const visibleLineLimit = this.logLimits.get(id) || lines.length;
        visibleLog = lines.slice(0, visibleLineLimit).join('\n');
      } else {
        // If test is DONE/terminated, return the entire log in one call (adaptive return)
        visibleLog = fullLog;
      }

      const chunk = visibleLog.substring(
        Number(offset),
        Number(offset) + length,
      );
      const nextOffset = Number(offset) + chunk.length;
      const hasMore = nextOffset < visibleLog.length;

      return of({
        logContent: chunk,
        nextOffset,
        hasMore,
      }).pipe(delay(500));
    } else {
      return throwError(
        () => new Error(`Test with ID '${id}' not found in mock data.`),
      ).pipe(delay(500));
    }
  }
}
