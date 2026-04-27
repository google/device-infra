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

import {PopularFlag} from '../../../../../core/models/host_action';
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

  readonly isListMode = signal(true);
  readonly currentFlagsArray = signal<string[]>([]);
  readonly initialFlagsArray = signal<string[]>([]);
  readonly presets = signal<PopularFlag[]>([]);
  readonly isLoadingPresets = signal(false);
  readonly isSaving = signal(false);
  readonly errorMessage = signal('');
  readonly addInput = signal('');
  readonly filterText = signal('');

  readonly isDirty = computed(() => {
    const currentStr = this.isListMode()
      ? this.currentFlagsArray().join(' ')
      : this.currentFlagsArray().join(' '); // Text mode shares same array in this implementation
    const initialStr = this.initialFlagsArray().join(' ');
    return currentStr.trim() !== initialStr.trim();
  });

  readonly filteredPresets = computed(() => {
    const filter = this.addInput().toLowerCase();
    if (!filter) return this.presets();
    return this.presets().filter(
      (p) =>
        p.name.toLowerCase().includes(filter) ||
        p.description.toLowerCase().includes(filter) ||
        p.cmd.toLowerCase().includes(filter),
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
    const map = new Map<string, string>();
    for (const p of this.presets()) {
      const flags = this.splitFlags(p.cmd);
      if (flags.length === 1) {
        map.set(flags[0], p.description);
      } else {
        flags.forEach((f) => map.set(f, `Part of: ${p.name}`));
      }
    }
    return map;
  });

  getFlagDescription(flag: string): string {
    const map = this.flagDescriptions();
    if (map.has(flag)) {
      return map.get(flag) || '';
    }
    const baseFlag = flag.split('=')[0];
    if (map.has(baseFlag)) {
      return map.get(baseFlag) || '';
    }
    return '';
  }

  ngOnInit() {
    this.parseInitialFlags();
    this.loadPresets();
  }

  parseInitialFlags() {
    const flags = this.data.currentFlags
      ? this.splitFlags(this.data.currentFlags)
      : [];
    this.currentFlagsArray.set([...flags]);
    this.initialFlagsArray.set([...flags]);
  }

  updateFlagsFromText(text: string) {
    const flags = text ? this.splitFlags(text) : [];
    this.currentFlagsArray.set(flags);
  }

  loadPresets() {
    this.isLoadingPresets.set(true);
    this.hostService
      .getPopularFlags(this.data.hostName)
      .pipe(take(1))
      .subscribe({
        next: (presets) => {
          this.presets.set(presets);
          this.isLoadingPresets.set(false);
        },
        error: () => {
          this.isLoadingPresets.set(false);
        },
      });
  }

  toggleMode(isList: boolean) {
    this.isListMode.set(isList);
  }

  addFlag() {
    const val = this.addInput().trim();
    if (val) {
      const newFlags = this.splitFlags(val);
      this.currentFlagsArray.update((flags) => [...flags, ...newFlags]);
      this.addInput.set('');
    }
  }

  removeFlag(index: number) {
    this.currentFlagsArray.update((flags) =>
      flags.filter((_, i) => i !== index),
    );
  }

  appendPreset(preset: PopularFlag) {
    const newFlags = this.splitFlags(preset.cmd);
    this.currentFlagsArray.update((flags) => [...flags, ...newFlags]);
  }

  clearAll() {
    this.currentFlagsArray.set([]);
  }

  discardChanges() {
    this.currentFlagsArray.set([...this.initialFlagsArray()]);
  }

  save() {
    const finalString = this.currentFlagsArray().join(' ').trim();
    this.isSaving.set(true);
    this.errorMessage.set('');

    this.hostService
      .updatePassThroughFlags(this.data.hostName, finalString)
      .pipe(take(1))
      .subscribe({
        next: () => {
          this.isSaving.set(false);
          this.dialogRef.close(finalString);
        },
        error: (err: Error) => {
          this.isSaving.set(false);
          this.errorMessage.set(err.message || 'Failed to save flags');
        },
      });
  }

  close() {
    this.dialogRef.close();
  }

  isPresetAdded(preset: PopularFlag): boolean {
    const currentSet = new Set(this.currentFlagsArray());
    const presetFlags = this.splitFlags(preset.cmd);
    return (
      presetFlags.length > 0 && presetFlags.every((f) => currentSet.has(f))
    );
  }
}
