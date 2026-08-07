import {ChangeDetectionStrategy, Component, inject, input} from '@angular/core';
import {map} from 'rxjs/operators';
import {FileExplorer} from '../../../../core/models/common_models';
import {SESSION_SERVICE} from '../../../../core/services/session/session_service';
import {FilesTab} from '../../../../shared/components/files_tab/files_tab';

/** Component for rendering the session files tab content. */
@Component({
  selector: 'app-session-files-tab',
  standalone: true,
  imports: [FilesTab],
  templateUrl: './session_files_tab.ng.html',
  styleUrl: './session_files_tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionFilesTab {
  readonly sessionId = input.required<string>();
  readonly fileExplorer = input.required<FileExplorer>();

  private readonly sessionService = inject(SESSION_SERVICE);

  readonly getFileContent = (path: string) =>
    this.sessionService
      .getSessionFile({
        sessionId: this.sessionId(),
        filePath: path,
      })
      .pipe(map((resp) => resp.content || ''));
}
