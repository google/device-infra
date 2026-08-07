import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';

import {APP_DATA, AppData} from '../../models/app_data';
import {
  GetJobFileRequest,
  GetJobFileResponse,
  GetJobLogRequest,
  GetJobLogResponse,
  GetJobRequest,
  GetJobResponse,
  KillJobRequest,
  KillJobResponse,
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

  override getJob(request: GetJobRequest): Observable<GetJobResponse> {
    return this.http.get<GetJobResponse>(`${this.apiUrl}/${request.jobId}`);
  }

  override getJobLog(request: GetJobLogRequest): Observable<GetJobLogResponse> {
    const params: {[key: string]: string | number} = {
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
    request: GetJobFileRequest,
  ): Observable<GetJobFileResponse> {
    const params: {[key: string]: string} = {
      'file_path': request.filePath,
    };
    return this.http.get<GetJobFileResponse>(
      `${this.apiUrl}/${request.jobId}/file`,
      {params},
    );
  }

  override killJob(
    request: string | KillJobRequest,
  ): Observable<KillJobResponse> {
    const id = typeof request === 'string' ? request : request.jobId;
    return this.http.post<KillJobResponse>(`${this.apiUrl}/${id}:kill`, {
      'job_id': id,
    });
  }
}
