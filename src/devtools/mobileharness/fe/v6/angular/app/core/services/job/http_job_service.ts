import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';

import {APP_DATA, AppData} from '../../models/app_data';
import {
  GetJobFileResponse,
  GetJobLogRequest,
  GetJobLogResponse,
  GetJobRequest,
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

  override getJob(request: string | GetJobRequest): Observable<GetJobResponse> {
    const id = typeof request === 'string' ? request : request.jobId;
    return this.http.get<GetJobResponse>(`${this.apiUrl}/${id}`);
  }

  override getJobLog(request: GetJobLogRequest): Observable<GetJobLogResponse> {
    const params: {[key: string]: string | number} = {
      'job_id': request.jobId,
      'offset': request.offset,
    };
    if (request.contentHash) {
      params['content_hash'] = request.contentHash;
    }
    return this.http.get<GetJobLogResponse>(
      `${this.apiUrl}/${request.jobId}/log`,
      {params},
    );
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
