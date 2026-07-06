import {InjectionToken} from '@angular/core';
import {Observable} from 'rxjs';
import {
  GetTestLogRequest,
  GetTestLogResponse,
  GetTestRequest,
  TestOverviewData,
} from '../../models/test_overview';

/**
 * Injection token for the TestService.
 */
export const TEST_SERVICE = new InjectionToken<TestService>('TestService');

/**
 * Abstract class defining the contract for test data operations.
 */
export abstract class TestService {
  /**
   * Retrieves the detailed overview data for a specific test.
   */
  abstract getTest(request: GetTestRequest): Observable<TestOverviewData>;

  /**
   * Retrieves a chunk of logs for a specific test.
   */
  abstract getTestLog(
    request: GetTestLogRequest,
  ): Observable<GetTestLogResponse>;
}
