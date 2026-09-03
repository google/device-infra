import {HttpClient} from '@angular/common/http';
import {Injectable, inject} from '@angular/core';
import {APP_DATA} from '@deviceinfra/app/core/models/app_data';
import {
  GetGlobalSummaryRequest,
  GlobalSummary,
} from '@deviceinfra/app/core/models/home';
import {Observable} from 'rxjs';
import {HomeService} from './home_service';

/** An implementation of HomeService that uses HTTP to fetch data. */
@Injectable()
export class HttpHomeService extends HomeService {
  private readonly appData = inject(APP_DATA);
  private readonly summaryApiUrl = `${this.appData.labConsoleServerUrl}/v6/fleet-search/global-summary`;
  private readonly http = inject(HttpClient);

  override getGlobalSummary(
    request?: GetGlobalSummaryRequest,
  ): Observable<GlobalSummary> {
    return this.http.get<GlobalSummary>(this.summaryApiUrl);
  }
}
