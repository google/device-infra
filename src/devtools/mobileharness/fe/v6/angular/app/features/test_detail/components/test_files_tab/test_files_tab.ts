import {ChangeDetectionStrategy, Component, inject, input} from '@angular/core';
import {FileExplorer} from '../../../../core/models/common_models';
import {TEST_SERVICE} from '../../../../core/services/test/test_service';
import {FilesTab} from '../../../../shared/components/files_tab/files_tab';

/** Component for rendering the test files tab content. */
@Component({
  selector: 'app-test-files-tab',
  standalone: true,
  imports: [FilesTab],
  templateUrl: './test_files_tab.ng.html',
  styleUrl: './test_files_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TestFilesTab {
  readonly testId = input.required<string>();
  readonly jobId = input<string>('');
  readonly fileExplorer = input.required<FileExplorer>();

  private readonly testService = inject(TEST_SERVICE);

  readonly getFileContent = (path: string) =>
    this.testService.getTestFile(this.testId(), this.jobId(), path);
}
