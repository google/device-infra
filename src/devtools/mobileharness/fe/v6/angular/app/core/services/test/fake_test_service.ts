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
  // Tracks test IDs that have finished streaming all their simulated running logs.
  private readonly completedRunningTests = new Set<string>();
  // Tracks the number of generated lines for running tests.
  private readonly runningLineOffsets = new Map<string, number>();

  constructor() {
    super();
  }

  /**
   * Retrieves the detailed overview data for a specific test by its ID from the mock dataset.
   *
   * @param request The request containing the test ID or sub-test ID.
   * @return An Observable emitting the TestOverviewData.
   */
  override getTest(request: GetTestRequest): Observable<TestOverviewData> {
    const id = request.subTestId || request.testId;
    const scenario = MOCK_TEST_SCENARIOS.find(
      (s) => s.id === id || s.overview.id === id,
    );
    if (scenario) {
      let status = scenario.overview.status;
      // In production, running tests eventually transition to DONE.
      // Once all logs of a running test have been streamed, simulate test completion.
      if (
        status === TestStatus.TEST_STATUS_RUNNING &&
        this.completedRunningTests.has(scenario.id)
      ) {
        status = TestStatus.TEST_STATUS_DONE;
      }
      console.log(
        `[FakeTestService] getTest id=${scenario.id} status=${status} completedHas=${this.completedRunningTests.has(scenario.id)}`,
      );

      const execDetails = scenario.overview.executionDetails;
      const overview: TestOverviewData = {
        ...scenario.overview,
        status,
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
   * Mock implementation of getTestLog that streams log chunks progressively from the test scenario.
   */
  override getTestLog(
    request: GetTestLogRequest,
  ): Observable<GetTestLogResponse> {
    const id = request.testId;
    const numOffset = Number(request.offset) || 0;
    const scenario = MOCK_TEST_SCENARIOS.find(
      (s) => s.id === id || s.overview.id === id,
    );
    if (scenario) {
      const fullLog = scenario.log || '';
      const status = scenario.overview.status;

      // When starting fresh from offset 0, reset completion status to simulate a new run.
      if (numOffset === 0) {
        this.completedRunningTests.delete(scenario.id);
        this.runningLineOffsets.delete(scenario.id);
      }

      if (status === TestStatus.TEST_STATUS_RUNNING) {
        const lines = fullLog.split('\n');
        const currentLineOffset = this.runningLineOffsets.get(scenario.id) || 0;
        // Progressively add 3 lines on each poll to simulate production logs growing.
        const newLineOffset = Math.min(currentLineOffset + 3, lines.length);
        this.runningLineOffsets.set(scenario.id, newLineOffset);

        const visibleLog =
          lines.slice(0, newLineOffset).join('\n') +
          (newLineOffset < lines.length ? '\n' : '');
        const chunk = visibleLog.substring(numOffset);
        const nextOffset = numOffset + chunk.length;

        console.log(
          `[FakeTestService] getTestLog id=${scenario.id} offset=${numOffset} chunkLen=${chunk.length} nextOffset=${nextOffset} fullLogLen=${fullLog.length} linesEmitted=${newLineOffset}/${lines.length}`,
        );

        if (newLineOffset >= lines.length) {
          this.completedRunningTests.add(scenario.id);
        }

        const hasMore = false;

        return of({
          logContent: chunk,
          nextOffset,
          hasMore,
        }).pipe(delay(300));
      } else {
        // If test is DONE/terminated, return remaining log content.
        const chunk = fullLog.substring(numOffset);
        const nextOffset = numOffset + chunk.length;
        const hasMore = false;

        console.log(
          `[FakeTestService] getTestLog DONE id=${scenario.id} offset=${numOffset} chunkLen=${chunk.length} nextOffset=${nextOffset} fullLogLen=${fullLog.length}`,
        );

        return of({
          logContent: chunk,
          nextOffset,
          hasMore,
        }).pipe(delay(300));
      }
    } else {
      return throwError(
        () => new Error(`Test with ID '${id}' not found in mock data.`),
      ).pipe(delay(300));
    }
  }
}
