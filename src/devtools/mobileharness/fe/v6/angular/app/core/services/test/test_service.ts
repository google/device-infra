import {InjectionToken} from '@angular/core';
import {Observable} from 'rxjs';
import {GetTestLogResponse, TestOverviewData} from '../../models/test_overview';

/**
 * Injection token for the TestService.
 */
export const TEST_SERVICE = new InjectionToken<TestService>('TestService');

/**
 * Abstract class defining the contract for test data operations.
 */
export abstract class TestService {
  /**
   * Retrieves the detailed overview data for a specific test by its ID.
   */
  abstract getTest(id: string): Observable<TestOverviewData>;

  /**
   * Retrieves a chunk of logs for a specific test.
   */
  abstract getTestLog(
    id: string,
    offset: number,
    length: number,
  ): Observable<GetTestLogResponse>;
}
