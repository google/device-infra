import {Injectable} from '@angular/core';
import {
  GetGlobalSummaryRequest,
  GlobalSummary,
} from '@deviceinfra/app/core/models/home';
import {MOCK_GLOBAL_SUMMARY} from '@deviceinfra/app/core/services/mock_data/home';
import {Observable, of} from 'rxjs';
import {HomeService} from './home_service';

/** Mock implementation of HomeService for frontend testing & offline fallback. */
@Injectable()
export class FakeHomeService extends HomeService {
  override getGlobalSummary(
    request?: GetGlobalSummaryRequest,
  ): Observable<GlobalSummary> {
    return of(MOCK_GLOBAL_SUMMARY);
  }
}
