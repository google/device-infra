import {HttpClient} from '@angular/common/http';
import {Injectable, inject} from '@angular/core';

import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';

import {APP_DATA, AppData} from '../../models/app_data';
import {
  GetTestLogRequest,
  GetTestLogResponse,
  GetTestRequest,
  GetTestResponse,
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

  override getTest(request: GetTestRequest): Observable<GetTestResponse> {
    const params: {[key: string]: string} = {
      'job_id': request.jobId,
    };
    if (request.subTestId) {
      params['sub_test_id'] = request.subTestId;
    }
    return this.http.get<GetTestResponse>(`${this.apiUrl}/${request.testId}`, {
      params,
    });
  }

  override getTestLog(
    request: GetTestLogRequest,
  ): Observable<GetTestLogResponse> {
    const params: {[key: string]: string | number} = {
      'job_id': request.jobId,
      'offset': request.offset,
    };
    if (request.contentHash) {
      params['content_hash'] = request.contentHash;
    }
    return this.http.get<GetTestLogResponse>(
      `${this.apiUrl}/${request.testId}/log`,
      {params},
    );
  }

  override getTestFile(
    testId: string,
    jobId: string,
    filePath: string,
  ): Observable<string> {
    const params: {[key: string]: string} = {
      'job_id': jobId,
      'file_path': filePath,
    };
    return this.http
      .get<{content: string}>(`${this.apiUrl}/${testId}/file`, {params})
      .pipe(map((resp) => resp.content || ''));
  }
}
