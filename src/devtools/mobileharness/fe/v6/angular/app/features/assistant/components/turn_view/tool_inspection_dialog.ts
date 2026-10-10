import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MAT_DIALOG_DATA, MatDialogModule} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';

import {ToolExecution} from '../../../../core/models/assistant';
import {ClipboardService} from '../../../../shared/services/clipboard_service';

/** Formats a JSON string with 2-space indentation, falling back to raw text on parse error. */
export function formatJsonString(raw?: string): string {
  if (!raw) return '{}';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

/**
 * Dialog component inspecting the input arguments and output payload
 * of an assistant ReAct tool execution.
 */
@Component({
  selector: 'app-tool-inspection-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatTooltipModule,
  ],
  templateUrl: './tool_inspection_dialog.ng.html',
  styleUrl: './tool_inspection_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToolInspectionDialogComponent {
  readonly data: ToolExecution = inject(MAT_DIALOG_DATA);
  private readonly clipboardService = inject(ClipboardService);

  readonly formattedArguments = computed(() =>
    formatJsonString(this.data.argumentsJson),
  );

  readonly formattedResult = computed(() => {
    if (this.data.resultJson) {
      return formatJsonString(this.data.resultJson);
    }
    return this.data.errorMessage || 'No output payload';
  });

  readonly copiedArgs = signal<boolean>(false);
  readonly copiedResult = signal<boolean>(false);

  copyArguments(): void {
    const success = this.clipboardService.copyToClipboard(
      this.formattedArguments(),
    );
    if (success) {
      this.copiedArgs.set(true);
      setTimeout(() => {
        this.copiedArgs.set(false);
      }, 2000);
    }
  }

  copyResult(): void {
    const success = this.clipboardService.copyToClipboard(
      this.formattedResult(),
    );
    if (success) {
      this.copiedResult.set(true);
      setTimeout(() => {
        this.copiedResult.set(false);
      }, 2000);
    }
  }
}
