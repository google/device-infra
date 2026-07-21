import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';

import {APP_DATA, AppData} from '../../models/app_data';
import {
  GetJobFileResponse,
  GetJobLogResponse,
  GetJobResponse,
} from '../../models/job_overview';
import {JobService} from './job_service';

/** An implementation of the JobService that uses HTTP to fetch data. */
@Injectable()
export class HttpJobService extends JobService {
  private readonly appData: AppData = inject(APP_DATA);
  private readonly apiUrl = `${this.appData.labConsoleServerUrl}/v6/jobs`;
  private readonly http = inject(HttpClient);

  constructor() {
    super();
  }

  override getJob(id: string): Observable<GetJobResponse> {
    return this.http.get<GetJobResponse>(`${this.apiUrl}/${id}`);
  }

  override getJobLog(
    id: string,
    offset: number,
    length: number,
  ): Observable<GetJobLogResponse> {
    return this.http.post<GetJobLogResponse>(`${this.apiUrl}/${id}:getJobLog`, {
      'job_id': id,
      'offset': offset,
      'length': length,
    });
  }

  override getJobFile(
    id: string,
    filePath: string,
  ): Observable<GetJobFileResponse> {
    return this.http.post<GetJobFileResponse>(
      `${this.apiUrl}/${id}:getJobFile`,
      {
        'job_id': id,
        'file_path': filePath,
      },
    );
  }
}
