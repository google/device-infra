import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnInit,
  signal,
  ViewEncapsulation,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {take} from 'rxjs/operators';

import {TroubleshootScriptAction} from '../../../../../core/models/host_action';
import {HOST_SERVICE} from '../../../../../core/services/host/host_service';
import {Dialog} from '../../../../../shared/components/config_common/dialog/dialog';

/** Data structure injected into the Advanced Operations dialog. */
export interface AdvancedOpsDialogData {
  hostName: string;
  universe: string;
}

/**
 * Dialog component for executing advanced troubleshooting operations on a host.
 * Displays a list of available operations and a live console for log output.
 */
@Component({
  selector: 'app-advanced-ops-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
    Dialog,
  ],
  templateUrl: './advanced_ops_dialog.ng.html',
  styleUrl: './advanced_ops_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
})
export class AdvancedOpsDialog implements OnInit {
  readonly data = inject<AdvancedOpsDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<AdvancedOpsDialog>);
  private readonly hostService = inject(HOST_SERVICE);

  readonly isLoading = signal(true);
  readonly actions = signal<TroubleshootScriptAction[]>([]);
  readonly selectedAction = signal<TroubleshootScriptAction | null>(null);

  readonly isExecuting = signal(false);
  readonly logs = signal<string[]>([]);
  readonly errorMessage = signal('');
  readonly executionSuccess = signal(false);

  ngOnInit() {
    this.loadActions();
  }

  private loadActions() {
    this.isLoading.set(true);
    this.hostService
      .listTroubleshootScripts(this.data.hostName, this.data.universe)
      .pipe(take(1))
      .subscribe({
        next: (response) => {
          this.actions.set(response.actions || []);
          if (this.actions().length > 0) {
            this.selectedAction.set(this.actions()[0]);
          }
          this.isLoading.set(false);
        },
        error: (err: Error) => {
          this.errorMessage.set(
            err.message || 'Failed to load advanced operations.',
          );
          this.isLoading.set(false);
        },
      });
  }

  selectAction(action: TroubleshootScriptAction) {
    if (this.isExecuting()) {
      return;
    }
    this.selectedAction.set(action);
    this.errorMessage.set('');
    this.logs.set([]);
    this.executionSuccess.set(false);
  }

  executeScript(action: TroubleshootScriptAction) {
    this.isExecuting.set(true);
    this.executionSuccess.set(false);
    this.errorMessage.set('');
    this.logs.set([
      `[Lab Console] Starting Advanced Operation: ${action.displayName}`,
      `[Lab Console] Target Host: ${this.data.hostName}`,
      `[Lab Console] Requesting execution. Please wait...`,
    ]);

    this.hostService
      .runTroubleshootScript(
        this.data.hostName,
        action.script,
        {},
        this.data.universe,
      )
      .pipe(take(1))
      .subscribe({
        next: (response) => {
          this.isExecuting.set(false);
          const newLogs = [...this.logs()];
          newLogs.push(`[Lab Console] Execution completed.`);
          newLogs.push(`[Exit Code]: ${response.exitCode}`);
          if (response.stdout) {
            newLogs.push(`--- STDOUT ---`);
            newLogs.push(response.stdout);
          }
          if (response.stderr) {
            newLogs.push(`--- STDERR ---`);
            newLogs.push(response.stderr);
          }
          this.logs.set(newLogs);
          this.executionSuccess.set(response.exitCode === 0);
          if (response.exitCode !== 0) {
            this.errorMessage.set('Script returned a non-zero exit code.');
          }
        },
        error: (err: Error) => {
          this.isExecuting.set(false);
          this.errorMessage.set(
            err.message || 'Failed to execute troubleshooting script.',
          );
          const newLogs = [...this.logs()];
          newLogs.push(`[Lab Console] ERROR: RPC invocation failed.`);
          newLogs.push(err.message);
          this.logs.set(newLogs);
        },
      });
  }

  close() {
    this.dialogRef.close();
  }
}
