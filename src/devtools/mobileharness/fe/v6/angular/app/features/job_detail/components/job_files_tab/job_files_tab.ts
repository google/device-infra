import {ChangeDetectionStrategy, Component, inject, input} from '@angular/core';
import {map} from 'rxjs/operators';

import {FileExplorer} from '../../../../core/models/common_models';
import {JOB_SERVICE} from '../../../../core/services/job/job_service';
import {FilesTab} from '../../../../shared/components/files_tab/files_tab';

/** Component for rendering the job files tab content. */
@Component({
  selector: 'app-job-files-tab',
  standalone: true,
  imports: [FilesTab],
  templateUrl: './job_files_tab.ng.html',
  styleUrl: './job_files_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JobFilesTab {
  readonly jobId = input.required<string>();
  readonly fileExplorer = input.required<FileExplorer>();

  private readonly jobService = inject(JOB_SERVICE);

  readonly getFileContent = (path: string) =>
    this.jobService
      .getJobFile(this.jobId(), path)
      .pipe(map((resp) => resp.content || ''));
}
