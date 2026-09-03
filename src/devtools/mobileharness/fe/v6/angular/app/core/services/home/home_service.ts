import {InjectionToken} from '@angular/core';
import {
  GetGlobalSummaryRequest,
  GlobalSummary,
} from '@deviceinfra/app/core/models/home';
import {Observable} from 'rxjs';

/** Injection token for HomeService implementations. */
export const HOME_SERVICE = new InjectionToken<HomeService>('HomeService');

/**
 * Abstract class defining the contract for FE v6 HomeService operations.
 */
export abstract class HomeService {
  /**
   * Retrieves the Home page global fleet summary ("OmniLab Summary").
   */
  abstract getGlobalSummary(
    request?: GetGlobalSummaryRequest,
  ): Observable<GlobalSummary>;
}
