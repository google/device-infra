import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
  ViewEncapsulation,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {take} from 'rxjs/operators';

import {SnackBarService} from '@deviceinfra/app/shared/services/snackbar_service';
import {WritePermissionResult} from '../../../../../core/models/action_common';
import {
  PopularFlag,
  UpdatePassThroughFlagsResponse,
} from '../../../../../core/models/host_action';
import {HOST_SERVICE} from '../../../../../core/services/host/host_service';
import {Dialog} from '../../../../../shared/components/config_common/dialog/dialog';

/**
 * Structure for passing data including host identity and current flags to FlagsDialog.
 */
export interface FlagsDialogData {
  hostName: string;
  currentFlags: string;
}
/**
 * Handles configuration and synchronization operations for Pass-through Flags in the Lab Console.
 */
@Component({
  selector: 'app-flags-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    Dialog,
  ],
  templateUrl: './flags_dialog.ng.html',
  styleUrl: './flags_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
})
export class FlagsDialog implements OnInit {
  readonly data = inject<FlagsDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<FlagsDialog>);
  private readonly hostService = inject(HOST_SERVICE);
  private readonly snackBarService = inject(SnackBarService);

  readonly isListMode = signal(true);
  readonly currentFlagsArray = signal<string[]>([]);
  readonly initialFlagsArray = signal<string[]>([]);
  readonly presets = signal<PopularFlag[]>([]);
  readonly isLoadingPresets = signal(false);
  readonly isSaving = signal(false);
  readonly addInput = signal('');
  readonly filterText = signal('');
  readonly rawTextFlags = signal('');
  readonly hasPermission = signal(true);

  readonly isDirty = computed(() => {
    const currentFlags = this.isListMode()
      ? this.currentFlagsArray().join(' ')
      : this.rawTextFlags();
    const normalizedCurrentStr = this.splitFlags(currentFlags).join(' ');
    const normalizedInitialStr = this.initialFlagsArray().join(' ');
    return normalizedCurrentStr !== normalizedInitialStr;
  });

  readonly filteredPresets = computed(() => {
    const filter = (this.addInput() || '').toLowerCase();
    if (!filter) return this.presets();
    return this.presets().filter(
      (p) =>
        (p.name || '').toLowerCase().includes(filter) ||
        (p.description || '').toLowerCase().includes(filter) ||
        (p.cmd || '').toLowerCase().includes(filter),
    );
  });

  private splitFlags(input: string): string[] {
    if (!input) return [];
    return input
      .trim()
      .split(/\s+(?=--)/)
      .filter(Boolean);
  }

  readonly flagDescriptions = computed(() => {
    const entries = this.presets().flatMap((p) => {
      const flags = this.splitFlags(p.cmd);
      const desc = flags.length === 1 ? p.description : `Part of: ${p.name}`;
      return flags.map((f) => [f, desc] as [string, string]);
    });
    return new Map<string, string>(entries);
  });

  readonly displayedFlags = computed(() => {
    const map = this.flagDescriptions();
    return this.currentFlagsArray().map((flag) => {
      let description = map.get(flag) || '';
      if (!description) {
        const baseFlag = flag.split('=')[0];
        description = map.get(baseFlag) || '';
      }
      return {name: flag, description};
    });
  });

  readonly displayedPresets = computed(() => {
    const activeFlags = this.isListMode()
      ? this.currentFlagsArray()
      : this.splitFlags(this.rawTextFlags());
    const currentSet = new Set(activeFlags);
    return this.filteredPresets().map((p) => {
      const presetFlags = this.splitFlags(p.cmd);
      const isAdded =
        presetFlags.length > 0 && presetFlags.every((f) => currentSet.has(f));
      return {...p, isAdded};
    });
  });

  ngOnInit() {
    this.parseInitialFlags();
    this.loadPresets();
  }

  parseInitialFlags() {
    const flags = this.data.currentFlags
      ? this.splitFlags(this.data.currentFlags)
      : [];
    this.rawTextFlags.set(this.data.currentFlags);
    this.currentFlagsArray.set([...flags]);
    this.initialFlagsArray.set([...flags]);
  }

  updateFlagsFromText(text: string) {
    this.rawTextFlags.set(text);
  }

  loadPresets() {
    this.isLoadingPresets.set(true);
    this.hostService
      .getPopularFlags(this.data.hostName)
      .pipe(take(1))
      .subscribe({
        next: (response) => {
          this.presets.set(response?.flags || []);
          this.isLoadingPresets.set(false);
        },
        error: () => {
          this.isLoadingPresets.set(false);
        },
      });
  }

  toggleMode(isList: boolean) {
    if (this.isListMode() && !isList) {
      // Switching from List to Text: Update rawTextFlags from current array
      this.rawTextFlags.set(this.currentFlagsArray().join(' '));
    } else if (!this.isListMode() && isList) {
      // Switching from Text to List: Parse rawTextFlags into currentFlagsArray
      const flags = this.rawTextFlags()
        ? this.splitFlags(this.rawTextFlags())
        : [];
      this.currentFlagsArray.set(flags);
    }
    this.isListMode.set(isList);
  }

  addFlag() {
    if (!this.hasPermission()) return;
    const val = this.addInput().trim();
    if (val) {
      const newFlags = this.splitFlags(val);
      this.currentFlagsArray.update((flags) => [...flags, ...newFlags]);
      this.addInput.set('');
    }
  }

  removeFlag(index: number) {
    if (!this.hasPermission()) return;
    this.currentFlagsArray.update((flags) =>
      flags.filter((_, i) => i !== index),
    );
  }

  appendPreset(preset: PopularFlag) {
    if (!this.hasPermission()) return;
    const newFlags = this.splitFlags(preset.cmd);
    if (this.isListMode()) {
      this.currentFlagsArray.update((flags) => {
        const currentSet = new Set(flags);
        const flagsToAppend = newFlags.filter((f) => !currentSet.has(f));
        return [...flags, ...flagsToAppend];
      });
    } else {
      this.rawTextFlags.update((text) => {
        const currentFlags = this.splitFlags(text);
        const currentSet = new Set(currentFlags);
        const flagsToAppend = newFlags.filter((f) => !currentSet.has(f));
        if (flagsToAppend.length === 0) return text;
        const trimmed = text.trim();
        return trimmed
          ? `${trimmed} ${flagsToAppend.join(' ')}`
          : flagsToAppend.join(' ');
      });
    }
  }

  clearAll() {
    if (!this.hasPermission()) return;
    this.currentFlagsArray.set([]);
  }

  discardChanges() {
    if (!this.hasPermission()) return;
    this.rawTextFlags.set(this.initialFlagsArray().join(' '));
    this.currentFlagsArray.set([...this.initialFlagsArray()]);
  }

  save() {
    if (!this.hasPermission()) return;
    const finalString = this.isListMode()
      ? this.currentFlagsArray().join(' ').trim()
      : this.rawTextFlags().trim();

    // If in text mode, re-parse to ensure currentFlagsArray is in sync
    if (!this.isListMode()) {
      const flags = finalString ? this.splitFlags(finalString) : [];
      this.currentFlagsArray.set(flags);
    }

    this.isSaving.set(true);

    this.hostService
      .updatePassThroughFlags(this.data.hostName, finalString)
      .pipe(take(1))
      .subscribe({
        next: (response: UpdatePassThroughFlagsResponse) => {
          if (response.success) {
            this.isSaving.set(false);
            this.dialogRef.close(finalString);
          } else {
            this.isSaving.set(false);
            const msg =
              response.error?.message ||
              response.error?.code ||
              'Failed to save flags';
            this.snackBarService.showError(msg);
          }
        },
        error: () => {
          this.isSaving.set(false);
          this.snackBarService.showError('Failed to save flags');
        },
      });
  }

  handlePermissionChange(result: WritePermissionResult) {
    this.hasPermission.set(result.hasPermission);
  }

  close() {
    this.dialogRef.close();
  }
}
