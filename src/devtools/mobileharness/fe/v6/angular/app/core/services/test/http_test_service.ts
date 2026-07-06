import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';

import {APP_DATA, AppData} from '../../models/app_data';
import {
  GetTestLogRequest,
  GetTestLogResponse,
  GetTestRequest,
  GetTestResponse,
  TestOverviewData,
} from '../../models/test_overview';
import {TestService} from './test_service';

/** An implementation of the TestService that uses HTTP to fetch data. */
@Injectable()
export class HttpTestService extends TestService {
  private readonly appData: AppData = inject(APP_DATA);
  private readonly apiUrl = `${this.appData.labConsoleServerUrl}/v6/tests`;
  private readonly http = inject(HttpClient);

  constructor() {
    super();
  }

  override getTest(request: GetTestRequest): Observable<TestOverviewData> {
    let url = `${this.apiUrl}/${request.testId}`;
    if (request.subTestId) {
      url += `?sub_test_id=${request.subTestId}`;
    }
    return this.http
      .get<GetTestResponse>(url)
      .pipe(map((response) => response.test));
  }

  override getTestLog(
    request: GetTestLogRequest,
  ): Observable<GetTestLogResponse> {
    return this.http.post<GetTestLogResponse>(
      `${this.apiUrl}/${request.testId}:getTestLog`,
      {
        'offset': request.offset,
        'length': request.length,
      },
    );
  }
}
