/**
 * @fileoverview Dialog component for running advanced operations (troubleshooting scripts).
 */

import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTooltipModule} from '@angular/material/tooltip';
import {
  RunTroubleshootScriptResponse,
  TroubleshootScriptAction,
} from '@deviceinfra/app/core/models/host_action';
import {EnvUniverseService} from '@deviceinfra/app/core/services/env_universe_service';
import {HOST_SERVICE} from '@deviceinfra/app/core/services/host/host_service';
import {Dialog} from '@deviceinfra/app/shared/components/dialog/dialog';
import {SnackBarService} from '@deviceinfra/app/shared/services/snackbar_service';

/**
 * Data passed into the `HostAdvancedOpsDialog` component when opened via `MatDialog`.
 */
export interface HostAdvancedOpsDialogData {
  hostName: string;
}

/**
 * Component for displaying and executing advanced troubleshooting scripts on a specific host.
 */
@Component({
  selector: 'app-host-advanced-ops-dialog',
  standalone: true,
  templateUrl: './host_advanced_ops_dialog.ng.html',
  styleUrl: './host_advanced_ops_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    Dialog,
  ],
})
export class HostAdvancedOpsDialog implements OnInit {
  readonly data = inject<HostAdvancedOpsDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<HostAdvancedOpsDialog>);
  private readonly hostService = inject(HOST_SERVICE);
  private readonly envUniverseService = inject(EnvUniverseService);
  private readonly snackBar = inject(SnackBarService);

  readonly currentStep = signal(1);
  readonly isLoadingScripts = signal(false);
  readonly isExecuting = signal(false);
  readonly scripts = signal<TroubleshootScriptAction[]>([]);
  readonly selectedScript = signal<TroubleshootScriptAction | null>(null);
  readonly executionResult = signal<RunTroubleshootScriptResponse | null>(null);

  readonly dialogTitle = computed(() => {
    const step = this.currentStep();
    const script = this.selectedScript();
    if (step === 1) {
      return 'Advanced Operations';
    } else if (step === 2 && script) {
      return script.displayName;
    } else if (step === 3 && script) {
      return `Running: ${script.displayName}`;
    }
    return '';
  });
  ngOnInit() {
    this.fetchScripts();
  }

  fetchScripts() {
    this.isLoadingScripts.set(true);
    this.hostService
      .listTroubleshootScripts(
        this.data.hostName,
        this.envUniverseService.getUniverseString(),
      )
      .subscribe({
        next: (response) => {
          this.scripts.set(response.actions);
          this.isLoadingScripts.set(false);
        },
        error: (err: {message?: string}) => {
          this.snackBar.showError(
            `Failed to load scripts: ${err.message || 'Unknown error'}`,
          );
          this.isLoadingScripts.set(false);
        },
      });
  }

  selectScript(script: TroubleshootScriptAction) {
    if (script.enabled) {
      this.selectedScript.set(script);
      this.currentStep.set(2);
    }
  }

  goBack() {
    if (this.currentStep() === 2) {
      this.selectedScript.set(null);
      this.currentStep.set(1);
    } else if (this.currentStep() === 3) {
      this.executionResult.set(null);
      this.currentStep.set(1); // Go back to catalog
    }
  }

  runOperation() {
    const script = this.selectedScript();
    if (!script) return;

    this.isExecuting.set(true);
    this.currentStep.set(3);

    this.hostService
      .runTroubleshootScript(
        this.data.hostName,
        script.script,
        this.envUniverseService.getUniverseString(),
      )
      .subscribe({
        next: (response) => {
          this.executionResult.set(response);
          this.isExecuting.set(false);
        },
        error: (err: {message?: string}) => {
          const errorMessage = err.message || 'Unknown error';
          this.snackBar.showError(`Execution failed: ${errorMessage}`);
          this.isExecuting.set(false);
          // Set a fake error response to display in terminal
          this.executionResult.set({
            exitCode: -1,
            stdout: '',
            stderr: errorMessage,
          });
        },
      });
  }

  close() {
    if (!this.isExecuting()) {
      this.dialogRef.close();
    }
  }

  getScriptIcon(script: string): string {
    if (script === 'RESET_USB_HUB') {
      return 'usb';
    }
    return 'build';
  }
}
