import {InjectionToken} from '@angular/core';

import {Observable} from 'rxjs';

import {
  GetTestLogRequest,
  GetTestLogResponse,
  GetTestRequest,
  GetTestResponse,
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
  abstract getTest(request: GetTestRequest): Observable<GetTestResponse>;

  /**
   * Retrieves a chunk of logs for a specific test.
   */
  abstract getTestLog(
    request: GetTestLogRequest,
  ): Observable<GetTestLogResponse>;

  /**
   * Retrieves the content of a specific file associated with a Test.
   */
  abstract getTestFile(
    testId: string,
    jobId: string,
    filePath: string,
  ): Observable<string>;
}
