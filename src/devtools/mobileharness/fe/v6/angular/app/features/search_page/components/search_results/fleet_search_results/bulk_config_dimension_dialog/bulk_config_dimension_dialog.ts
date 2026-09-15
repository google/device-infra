import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTooltipModule} from '@angular/material/tooltip';
import {
  CandidateDimensionValue,
  ConfigurableDimension,
  DeviceCurrentDimension,
  DimensionScope,
} from '../../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../../core/services/config/config_service';
import {Dialog} from '../../../../../../shared/components/dialog/dialog';

/** Input data structure passed into BulkConfigDimensionDialog. */
export interface BulkConfigDimensionDialogData {
  deviceIds: string[];
  dimension: ConfigurableDimension;
}

/** Result returned when BulkConfigDimensionDialog closes. */
export interface BulkConfigDimensionDialogResult {
  updated: boolean;
}

/** Aggregated value set suggestion displayed in Step 1. */
export interface AggregatedDimensionSuggestion {
  key: string;
  values: string[];
  selectedCount: number;
  fleetCount?: number;
  isNotSet: boolean;
}

/** Per-device review comparison item displayed in Step 2. */
export interface DimensionReviewRow {
  deviceId: string;
  currentValues: string[];
  writable: boolean;
  unwritableReason: string;
  isChanged: boolean;
}

/**
 * Parses raw text into discrete tokens, respecting quotes ('...' or "...").
 * Commas, semicolons, and newlines act as separators outside quotes.
 */
export function parseDimensionTokens(raw: string): string[] {
  if (!raw) return [];
  const tokens: string[] = [];
  let cur = '';
  let inQuote: string | null = null;
  for (let i = 0; i < raw.length; i++) {
    const ch = raw[i];
    if (inQuote) {
      if (ch === inQuote) {
        inQuote = null;
      } else {
        cur += ch;
      }
    } else {
      if (ch === '"' || ch === "'") {
        inQuote = ch;
      } else if (ch === ',' || ch === ';' || ch === '\n' || ch === '\r') {
        const t = cur.trim();
        if (t) tokens.push(t);
        cur = '';
      } else {
        cur += ch;
      }
    }
  }
  const last = cur.trim();
  if (last) tokens.push(last);
  return tokens;
}

/** Canonical JSON representation for comparing two value sets order- and case-insensitively. */
export function canonicalValueSet(values: string[]): string {
  return JSON.stringify(
    (values || []).map((v) => (v || '').toLowerCase()).sort(),
  );
}

/**
 * Bulk Dimension Configuration Dialog (2-step wizard) for OmniLab Console FE v6.
 *
 * Step 1: Choose values to apply (M3 chip input field + aggregated suggestion rows).
 * Step 2: Review and apply (Summary banner + comparison table directly showing
 * backend-provided writability, skipped status, and update preview).
 */
@Component({
  selector: 'app-bulk-config-dimension-dialog',
  standalone: true,
  templateUrl: './bulk_config_dimension_dialog.ng.html',
  styleUrl: './bulk_config_dimension_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    Dialog,
    FormsModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
})
export class BulkConfigDimensionDialog implements OnInit {
  readonly dialogData: BulkConfigDimensionDialogData = inject(MAT_DIALOG_DATA);
  readonly dialogRef = inject(
    MatDialogRef<BulkConfigDimensionDialog, BulkConfigDimensionDialogResult>,
  );
  private readonly configService = inject(CONFIG_SERVICE);

  readonly chipInputField =
    viewChild<ElementRef<HTMLInputElement>>('chipInput');

  /** Current active wizard step (1: choose values, 2: review & apply). */
  readonly step = signal<1 | 2>(1);

  /** Whether the initial batch context is being fetched from the backend. */
  readonly isLoading = signal<boolean>(true);

  /** Error message if loading the batch context failed. */
  readonly loadError = signal<string | null>(null);

  /** Whether the batch update mutation is in-flight. */
  readonly isApplying = signal<boolean>(false);

  /** Terminal outcome of applying the batch update. */
  readonly applyResult = signal<'success' | 'errors' | null>(null);

  /** Per-device failures returned by the backend. */
  readonly applyErrors = signal<Array<{deviceId: string; reason: string}>>([]);

  /** Raw per-device dimension values and permissions returned by the backend. */
  readonly deviceItems = signal<DeviceCurrentDimension[]>([]);

  /** Candidate dimension values returned by backend ranked by fleet popularity. */
  readonly candidateValues = signal<CandidateDimensionValue[]>([]);

  /** Target value set to apply to selected devices. Empty array indicates removing the dimension. */
  readonly targetValues = signal<string[]>([]);

  /** Current uncommitted text inside the chip input box. */
  readonly inputValue = signal<string>('');

  /** Total number of selected devices. */
  readonly totalCount = computed<number>(
    () => this.dialogData.deviceIds.length,
  );

  /** Dimension display name. */
  readonly dimensionDisplayName = computed<string>(
    () =>
      this.dialogData.dimension.displayName || this.dialogData.dimension.key,
  );

  /** Dimension key identifier. */
  readonly dimensionKey = computed<string>(() => this.dialogData.dimension.key);

  /** Target storage scope for the dimension update. */
  readonly dimensionScope = computed<DimensionScope | string>(
    () => this.dialogData.dimension.scope || DimensionScope.SUPPORTED,
  );

  /** Pluralized device label. */
  readonly deviceLabel = computed<string>(() =>
    this.totalCount() === 1
      ? '1 selected device'
      : `${this.totalCount()} selected devices`,
  );

  /** Dialog headline, consistently maintained as 'Configure dimension'. */
  readonly headline = computed<string>(() => 'Configure dimension');

  /** Dialog supporting subtitle text. */
  readonly supportText = computed<string>(() => {
    if (this.isApplying() || this.applyResult()) return '';
    if (this.step() === 1) {
      return `Step 1 of 2 · Choose a value for ${this.deviceLabel()}`;
    }
    return 'Step 2 of 2 · Review and apply';
  });

  /** Dynamic placeholder text for chip input. */
  readonly chipPlaceholder = computed<string>(() => {
    if (this.targetValues().length === 0) {
      return `Enter a value for ${this.dimensionDisplayName()}, or pick from suggestions below`;
    }
    return 'Add another value';
  });

  /** Deduplicated aggregated suggestions list across selected devices + candidate values. */
  readonly aggregatedSuggestions = computed<AggregatedDimensionSuggestion[]>(
    () => {
      const selectionCounts = new Map<string, number>();
      for (const item of this.deviceItems()) {
        const key = canonicalValueSet(item.values || []);
        selectionCounts.set(key, (selectionCounts.get(key) || 0) + 1);
      }

      const suggestionMap = new Map<string, AggregatedDimensionSuggestion>();

      // Populate from backend candidate values
      for (const cv of this.candidateValues()) {
        const vals = cv.values || [];
        const key = canonicalValueSet(vals);
        suggestionMap.set(key, {
          key,
          values: vals,
          selectedCount: selectionCounts.get(key) || 0,
          fleetCount: cv.deviceCount || 0,
          isNotSet: vals.length === 0,
        });
      }

      // Populate any value sets present in current devices not in candidates
      for (const item of this.deviceItems()) {
        const vals = item.values || [];
        const key = canonicalValueSet(vals);
        if (!suggestionMap.has(key)) {
          suggestionMap.set(key, {
            key,
            values: vals,
            selectedCount: selectionCounts.get(key) || 0,
            isNotSet: vals.length === 0,
          });
        }
      }

      return Array.from(suggestionMap.values()).sort(
        (a, b) =>
          b.selectedCount - a.selectedCount ||
          (b.fleetCount ?? 0) - (a.fleetCount ?? 0),
      );
    },
  );

  /** Per-device review comparison rows for Step 2. */
  readonly reviewRows = computed<DimensionReviewRow[]>(() => {
    const targetCanon = canonicalValueSet(this.targetValues());

    return this.deviceItems().map((item) => {
      const cur = item.values || [];
      const writable = item.writable !== false;
      const unwritableReason = item.unwritableReason || '';
      const isChanged = writable && canonicalValueSet(cur) !== targetCanon;

      return {
        deviceId: item.deviceId,
        currentValues: cur,
        writable,
        unwritableReason,
        isChanged,
      };
    });
  });

  /** Writable devices in the selection. */
  readonly writableRows = computed<DimensionReviewRow[]>(() =>
    this.reviewRows().filter((r) => r.writable),
  );

  /** Devices that will have their dimension updated. */
  readonly changedCount = computed<number>(
    () => this.writableRows().filter((r) => r.isChanged).length,
  );

  /** Read-only devices that will be skipped. */
  readonly skippedCount = computed<number>(
    () => this.reviewRows().filter((r) => !r.writable).length,
  );

  /** Devices whose current dimension value already matches the target. */
  readonly sameCount = computed<number>(
    () => this.writableRows().filter((r) => !r.isChanged).length,
  );

  /** Whether at least one selected device is writable. */
  readonly hasWritableDevices = computed<boolean>(
    () => this.writableRows().length > 0,
  );

  ngOnInit() {
    this.loadBatchDimensionContext();
  }

  /** Loads current dimension contexts and candidate values for selected devices. */
  loadBatchDimensionContext() {
    this.isLoading.set(true);
    this.loadError.set(null);

    this.configService
      .getBatchDimensionContext({
        deviceIds: this.dialogData.deviceIds,
        key: this.dialogData.dimension.key,
      })
      .subscribe({
        next: (res) => {
          const rawCurrent = res.current || [];
          const normalizedCurrent: DeviceCurrentDimension[] = rawCurrent.map(
            (item) => ({
              deviceId: item.deviceId || '',
              values: item.values || [],
              writable: item.writable !== false,
              unwritableReason: item.unwritableReason || '',
            }),
          );

          const rawCandidates = res.candidateValues || [];
          const normalizedCandidates: CandidateDimensionValue[] =
            rawCandidates.map((cv) => ({
              values: cv.values || [],
              deviceCount: cv.deviceCount ?? 0,
            }));

          this.deviceItems.set(normalizedCurrent);
          this.candidateValues.set(normalizedCandidates);
          this.initDefaultTarget(normalizedCurrent);
          this.isLoading.set(false);
        },
        error: (err) => {
          this.loadError.set(
            err?.message || 'Failed to load dimension context',
          );
          this.isLoading.set(false);
        },
      });
  }

  /**
   * If all selected devices already share the exact same non-empty value set,
   * initialize targetValues to that set. Otherwise start empty.
   */
  private initDefaultTarget(devices: DeviceCurrentDimension[]) {
    if (devices.length === 0) return;
    const seen = new Set<string>();
    let sample: string[] = [];

    for (const d of devices) {
      const vals = d.values || [];
      seen.add(canonicalValueSet(vals));
      if (vals.length > 0) sample = vals;
    }

    if (seen.size === 1 && sample.length > 0) {
      this.targetValues.set([...sample]);
    } else {
      this.targetValues.set([]);
    }
  }

  /** Commits any uncommitted text currently in the input box into targetValues. */
  commitInput() {
    const raw = this.inputValue().trim();
    if (!raw) return;

    const tokens = parseDimensionTokens(raw);
    const existingLower = new Set(
      this.targetValues().map((v) => v.toLowerCase()),
    );
    const toAdd: string[] = [];

    for (const token of tokens) {
      if (!existingLower.has(token.toLowerCase())) {
        toAdd.push(token);
        existingLower.add(token.toLowerCase());
      }
    }

    if (toAdd.length > 0) {
      this.targetValues.update((current) => [...current, ...toAdd]);
    }
    this.inputValue.set('');
  }

  /** Focuses the chip input field when clicking anywhere inside the chips container. */
  focusChipInput(event: MouseEvent) {
    if ((event.target as HTMLElement).closest('.bd-chip-remove')) return;
    this.chipInputField()?.nativeElement.focus();
  }

  /** Handles keydown events in the chip input field. */
  onInputKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter') {
      event.preventDefault();
      this.commitInput();
      return;
    }

    if (event.key === ',' || event.key === ';') {
      const input = event.target as HTMLInputElement;
      const textBefore = input.value.slice(0, input.selectionStart || 0);
      const quoteCount = (textBefore.match(/["']/g) || []).length;
      if (quoteCount % 2 === 0) {
        event.preventDefault();
        this.commitInput();
        return;
      }
    }

    if (event.key === 'Backspace') {
      const input = event.target as HTMLInputElement;
      if (
        input.selectionStart === 0 &&
        input.selectionEnd === 0 &&
        !input.value &&
        this.targetValues().length > 0
      ) {
        event.preventDefault();
        const current = [...this.targetValues()];
        const popped = current.pop()!;
        this.targetValues.set(current);
        this.inputValue.set(popped);
      }
    }
  }

  /** Handles paste events to automatically tokenize pasted text. */
  onInputPaste(event: ClipboardEvent) {
    event.preventDefault();
    const pasted = event.clipboardData?.getData('text') || '';
    const currentVal = this.inputValue() + pasted;
    const tokens = parseDimensionTokens(currentVal);
    const existingLower = new Set(
      this.targetValues().map((v) => v.toLowerCase()),
    );
    const toAdd: string[] = [];

    for (const token of tokens) {
      if (!existingLower.has(token.toLowerCase())) {
        toAdd.push(token);
        existingLower.add(token.toLowerCase());
      }
    }

    if (toAdd.length > 0) {
      this.targetValues.update((current) => [...current, ...toAdd]);
    }
    this.inputValue.set('');
  }

  /** Commits input on blur. */
  onInputBlur() {
    this.commitInput();
  }

  /** Removes a chip at the specified index. */
  removeChip(index: number) {
    this.targetValues.update((current) =>
      current.filter((_, i) => i !== index),
    );
  }

  /** Clears all target values, representing removing the dimension. */
  clearTarget() {
    this.targetValues.set([]);
    this.inputValue.set('');
  }

  /** Copies a distinct value set suggestion into the target values. */
  useValueSet(values: string[]) {
    this.targetValues.set([...values]);
    this.inputValue.set('');
  }

  /** Advances from Step 1 to Step 2. */
  goToStep2() {
    this.commitInput();
    this.step.set(2);
  }

  /** Returns from Step 2 back to Step 1. */
  backToStep1() {
    this.step.set(1);
  }

  /** Applies the batch dimension update to all writable devices. */
  apply() {
    const writableIds = this.writableRows().map((r) => r.deviceId);
    if (writableIds.length === 0) return;

    this.isApplying.set(true);

    this.configService
      .batchUpdateDeviceConfig({
        deviceIds: writableIds,
        dimension: {
          key: this.dimensionKey(),
          values: this.targetValues(),
          scope: this.dimensionScope(),
        },
      })
      .subscribe({
        next: (res) => {
          this.isApplying.set(false);
          const errorsMap = res.errors || {};
          const errEntries = Object.entries(errorsMap);

          if (errEntries.length > 0) {
            const errList = errEntries.map(([did, err]) => {
              let reason = 'Update failed';
              if (typeof err === 'string') {
                reason = err;
              } else if (err && typeof err === 'object') {
                reason = err.message || err.code || 'Update failed';
              }
              return {deviceId: did, reason};
            });
            this.applyErrors.set(errList);
            this.applyResult.set('errors');
          } else {
            this.applyResult.set('success');
          }
        },
        error: (err) => {
          this.isApplying.set(false);
          this.applyErrors.set([
            {
              deviceId: 'All writable devices',
              reason:
                err?.message || 'Server error while applying dimension update',
            },
          ]);
          this.applyResult.set('errors');
        },
      });
  }

  /** Closes dialog upon completion, confirming updates were made. */
  done() {
    this.dialogRef.close({updated: true});
  }

  /** Closes dialog directly. */
  close() {
    this.dialogRef.close(
      this.applyResult() === 'success' ? {updated: true} : undefined,
    );
  }
}
