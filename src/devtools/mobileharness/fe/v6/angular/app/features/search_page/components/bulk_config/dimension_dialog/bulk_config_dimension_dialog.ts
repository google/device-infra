import {DecimalPipe} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';

import {
  CandidateDimensionKey,
  CandidateDimensionValue,
  DeviceDimensionConfigEntry,
  DimensionList,
  TargetDimensionValues,
} from '../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {Dialog} from '../../../../../shared/components/dialog/dialog';
import {
  BulkConfigDialogResult,
  BulkConfigDimensionDialogData,
  DimensionWizardPhase,
} from '../../../models';
import {
  formatCurrentSelectionBadge,
  formatDeviceCount,
  formatNothingToUpdateMessage,
  formatSuccessSupportText,
  parseDimensionTokens,
} from '../common/bulk_config_utils';
import {BulkReviewHeaderBanner} from '../common/bulk_review_header_banner';
import {BulkStatusPill} from '../common/bulk_status_pill';
import {useBulkWizardSession} from '../common/bulk_wizard_session';
import {BulkWizardStatusView} from '../common/bulk_wizard_status_view';

/**
 * Bulk Dimension Configuration Dialog (2-step or 3-step wizard) for OmniLab Console FE v6.
 *
 * Aligns with cl/1001276314 and the official prototype in bulk_dimension.js:
 *
 * Curated dimension flow (2-step wizard):
 *   Step 1: Choose values (Chip input + backend candidate suggestions + clear values).
 *   Step 2: Review and apply (Authoritative change status from backend against target values).
 *
 * Custom dimension flow (3-step wizard):
 *   Step 1: Choose dimension key (Textfield + regex validation + system dimension blocklist + existing keys).
 *   Step 2: Choose values (Chip input + backend candidate suggestions + clear values).
 *   Step 3: Review and apply.
 */
@Component({
  selector: 'app-bulk-config-dimension-dialog',
  standalone: true,
  templateUrl: './bulk_config_dimension_dialog.ng.html',
  styleUrl: './bulk_config_dimension_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    BulkReviewHeaderBanner,
    BulkStatusPill,
    BulkWizardStatusView,
    DecimalPipe,
    Dialog,
    FormsModule,
    MatIconModule,
    MatTooltipModule,
  ],
})
export class BulkConfigDimensionDialog {
  readonly dialogData: BulkConfigDimensionDialogData = inject(MAT_DIALOG_DATA);
  readonly dialogRef = inject(
    MatDialogRef<BulkConfigDimensionDialog, BulkConfigDialogResult>,
  );
  private readonly configService = inject(CONFIG_SERVICE);

  /** Whether this dialog is operating on a custom dimension. */
  readonly isCustom = !this.dialogData.dimension;

  readonly wizard = useBulkWizardSession<
    DeviceDimensionConfigEntry,
    DimensionWizardPhase
  >(this.dialogRef, this.isCustom ? 'key' : 'values');

  readonly chipInputField =
    viewChild<ElementRef<HTMLInputElement>>('chipInput');
  readonly customKeyInputField =
    viewChild<ElementRef<HTMLInputElement>>('customKeyInput');

  readonly formatDeviceCount = formatDeviceCount;
  readonly formatCurrentSelectionBadge = formatCurrentSelectionBadge;

  /** Which dimension list in composite_dimension to configure. */
  readonly dimensionList: DimensionList =
    this.dialogData.dimension?.dimensionList ??
    this.dialogData.dimensionList ??
    DimensionList.DIMENSION_LIST_SUPPORTED;

  /** Total number of selected devices. */
  readonly totalCount = this.dialogData.deviceIds.length;

  /** Pluralized device count text. */
  readonly deviceLabel = formatDeviceCount(this.totalCount);

  /** Whether this dimension is in the required scope. */
  readonly isRequired =
    this.dimensionList === DimensionList.DIMENSION_LIST_REQUIRED;

  /** Current active wizard phase ('key' -> 'values' -> 'review'). */
  readonly phase = this.wizard.phase;

  /** Whether the active step's async data request is loading. */
  readonly isStepLoading = this.wizard.isStepLoading;

  /** Error message if the active step's async data request failed. */
  readonly stepError = this.wizard.stepError;

  /** Unified wizard view mode driving dialog header, body, and footer templates. */
  readonly viewMode = this.wizard.viewMode;

  // --- Step 1 (Key Step) State ---
  readonly customKey = signal<string>('');
  readonly candidateKeys = signal<CandidateDimensionKey[]>([]);
  readonly systemDimensionNames = signal<Set<string>>(new Set());

  /** Validation error for custom key, computed reactively from input and system blocklist. */
  readonly customKeyError = computed<string>(() => {
    const clean = this.customKey();
    if (!clean) return '';
    if (this.systemDimensionNames().has(clean)) {
      return `"${clean}" is automatically detected by OmniLab and cannot be configured manually.`;
    }
    if (!/^[a-z0-9_\-\.]+$/.test(clean)) {
      return 'Dimension key may only contain lowercase letters, numbers, underscores, dashes, and dots.';
    }
    return '';
  });

  // --- Dimension Target Metadata ---
  readonly dimensionKey = signal<string>(
    this.dialogData.dimension?.dimensionName ?? '',
  );

  /** Display label derived from curated dimension metadata or custom dimension key/scope. */
  readonly displayName = computed<string>(() => {
    if (!this.isCustom) {
      return (
        this.dialogData.dimension?.displayName ||
        this.dialogData.dimension?.dimensionName ||
        'dimension'
      );
    }
    const key = this.dimensionKey();
    if (!key) return 'dimension';
    return this.isRequired ? `Required Dimension: ${key}` : key;
  });

  // --- Values Step State ---
  readonly targetValues = signal<string[]>([]);
  readonly inputValue = signal<string>('');
  readonly candidateValues = signal<CandidateDimensionValue[]>([]);

  // --- Review & Apply State ---
  readonly reviewEntries = this.wizard.reviewEntries;
  readonly reviewSummary = this.wizard.reviewSummary;
  readonly expandedDeviceValues = signal<Set<string>>(new Set());
  readonly isApplying = this.wizard.isApplying;
  readonly applyResult = this.wizard.applyResult;
  readonly applyErrors = this.wizard.applyErrors;
  readonly applySuccessCount = this.wizard.applySuccessCount;

  /** Constructs the reactive TargetDimensionValues message according to targetValues. */
  readonly targetDimensionValues = computed<TargetDimensionValues>(() => {
    if (this.targetValues().length === 0) {
      return {clearValues: {}};
    }
    return {valueList: {values: [...this.targetValues()]}};
  });

  /** Dialog headline title according to current journey, phase, and terminal apply state. */
  readonly headline = computed<string>(() => {
    const name = this.displayName();
    switch (this.viewMode()) {
      case 'applying':
        return `Applying ${name}`;
      case 'success':
        return `${name} updated`;
      case 'errors':
        return `${formatDeviceCount(this.applyErrors().length)} could not be updated`;
      case 'key_loading':
      case 'key_error':
      case 'key':
        return this.isRequired
          ? 'Configure Required Dimension'
          : 'Configure Supported Dimension';
      default:
        return `Configure ${name}`;
    }
  });

  /** Dialog supporting subtitle text. */
  readonly supportText = computed<string>(() => {
    if (this.isApplying() || this.applyResult()) return '';
    const n = this.deviceLabel;
    const custom = this.isCustom;
    switch (this.phase()) {
      case 'key':
        return `Step 1 of 3 · Choose dimension key for ${n}`;
      case 'values':
        return `Step ${custom ? 2 : 1} of ${custom ? 3 : 2} · Choose a value for ${n}`;
      case 'review':
      default:
        return `Step ${custom ? 3 : 2} of ${custom ? 3 : 2} · Review and apply`;
    }
  });

  /** Placeholder for chip input box. */
  readonly chipPlaceholder = computed<string>(() => {
    if (this.targetValues().length === 0) {
      return `Enter a value for ${this.displayName()}, or pick from suggestions below`;
    }
    return 'Add another value';
  });

  /** Filtered candidate keys in Step 1 matching the user search input. */
  readonly filteredCandidateKeys = computed<CandidateDimensionKey[]>(() => {
    const q = this.customKey();
    const candidates = this.candidateKeys();
    if (!q) return candidates.slice(0, 50);
    return candidates
      .filter((c) => c.dimensionName.toLowerCase().includes(q))
      .slice(0, 50);
  });

  /** Whether the user typed a valid new key that is not yet in the fleet. */
  readonly isNewKey = computed<boolean>(() => {
    const q = this.customKey();
    if (!q || Boolean(this.customKeyError())) return false;
    return !this.candidateKeys().some(
      (c) => c.dimensionName.toLowerCase() === q,
    );
  });

  /** Whether the user can proceed from Key Step to Values Step. */
  readonly canProceedFromKeyStep = computed<boolean>(
    () => Boolean(this.customKey()) && !this.customKeyError(),
  );

  /** Informational message shown when willUpdateCount is 0 in the review step. */
  readonly nothingToUpdateMessage = computed<string>(() => {
    const dn = this.displayName();
    return formatNothingToUpdateMessage(
      this.reviewSummary(),
      this.totalCount,
      this.targetValues().length === 0
        ? `already have ${dn} unset`
        : `already have this ${dn} value`,
      `${dn} value`,
    );
  });

  /** Explanatory support text shown on the success screen. */
  readonly successSupportText = computed<string>(() => {
    const dn = this.displayName();
    return formatSuccessSupportText(
      this.reviewSummary(),
      this.targetValues().length === 0
        ? `Removed ${dn} from every updated device.`
        : `Every updated device now uses this ${dn} value.`,
    );
  });

  constructor() {
    effect(() => {
      const mode = this.viewMode();
      if (mode === 'key') {
        this.customKeyInputField()?.nativeElement.focus();
      } else if (mode === 'values') {
        this.chipInputField()?.nativeElement.focus();
      }
    });

    if (this.isCustom) {
      this.loadConfigurableDimensionKeys();
    } else {
      this.loadValuesStepData();
    }
  }

  // ================= STEP 1: KEY SELECTION (CUSTOM ONLY) =================

  /** Loads configurable dimension keys and system dimension blocklist from backend. */
  loadConfigurableDimensionKeys() {
    this.wizard.runStepTask(
      this.configService.batchGetConfigurableDimensionKeys({
        deviceIds: this.dialogData.deviceIds,
        dimensionList: this.dimensionList,
        universe: this.dialogData.universe,
        fleet: this.dialogData.fleet,
      }),
      (res) => {
        this.candidateKeys.set(res.candidateKeys ?? []);
        const sysNames = res.systemDimensionNames ?? [];
        this.systemDimensionNames.set(
          new Set(sysNames.map((s) => String(s).toLowerCase())),
        );
      },
      'Failed to load configurable dimension keys',
    );
  }

  /** Normalizes custom dimension key as user types. Validation updates reactively. */
  onCustomKeyInput(val: string) {
    this.customKey.set(
      (val || '')
        .trim()
        .toLowerCase()
        .replace(/^dim::/, ''),
    );
  }

  /** Selects a key from the existing candidate keys list. */
  pickKey(key: string) {
    this.onCustomKeyInput(key);
    this.customKeyInputField()?.nativeElement.focus();
  }

  /** Advances from Key Step to Values Step. */
  goFromKeyToValues() {
    if (!this.canProceedFromKeyStep()) return;

    this.dimensionKey.set(this.customKey());
    this.wizard.setPhase('values');
    this.loadValuesStepData();
  }

  /** Returns back from Values Step to Key Step. */
  goBackToKey() {
    this.commitInput();
    this.wizard.setPhase('key');
  }

  // ================= STEP: VALUES SELECTION =================

  /** Loads candidate value-set suggestions for the values step. */
  loadValuesStepData() {
    this.wizard.runStepTask(
      this.configService.getDimensionValueSuggestions({
        dimensionName: this.dimensionKey(),
        dimensionList: this.dimensionList,
        universe: this.dialogData.universe,
        fleet: this.dialogData.fleet,
      }),
      (suggestions) => {
        const rows = [...(suggestions.candidateValues ?? [])];
        rows.sort((a, b) => (b.deviceCount ?? 0) - (a.deviceCount ?? 0));
        this.candidateValues.set(rows);
      },
      'Failed to load dimension values',
    );
  }

  /** Commits any uncommitted text in the input box into targetValues. */
  commitInput() {
    const raw = this.inputValue().trim();
    this.inputValue.set('');
    if (!raw) return;

    const existingLower = new Set(
      this.targetValues().map((v) => v.toLowerCase()),
    );
    const toAdd = parseDimensionTokens(raw).filter((token) => {
      const lower = token.toLowerCase();
      if (existingLower.has(lower)) return false;
      existingLower.add(lower);
      return true;
    });

    if (toAdd.length > 0) {
      this.targetValues.update((current) => [...current, ...toAdd]);
    }
  }

  /** Focuses the chip input field when clicking anywhere inside the chips container. */
  focusChipInput(event: MouseEvent) {
    const target = event.target as HTMLElement;
    if (target === this.chipInputField()?.nativeElement) return;
    if (target.closest('.bd-chip-remove')) return;
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
      const input = event.target as HTMLInputElement | null;
      const value = input?.value ?? this.inputValue();
      const cursor = input?.selectionStart ?? value.length;
      const textBefore = value.slice(0, cursor);
      const quoteCount = (textBefore.match(/["']/g) || []).length;
      if (quoteCount % 2 === 0) {
        event.preventDefault();
        this.commitInput();
      }
      return;
    }

    if (event.key === 'Backspace') {
      const input = event.target as HTMLInputElement;
      if (
        input.selectionStart === 0 &&
        input.selectionEnd === 0 &&
        !input.value
      ) {
        const last = this.targetValues().at(-1);
        if (last !== undefined) {
          event.preventDefault();
          this.targetValues.update((current) => current.slice(0, -1));
          this.inputValue.set(last);
        }
      }
    }
  }

  /** Handles paste events to automatically tokenize pasted text. */
  onInputPaste(event: ClipboardEvent) {
    event.preventDefault();
    const pasted = event.clipboardData?.getData('text') || '';
    this.inputValue.update((cur) => cur + pasted);
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
    this.useValueSet([]);
  }

  /** Copies a distinct value set suggestion into the target values. */
  useValueSet(values: string[]) {
    this.targetValues.set([...values]);
    this.inputValue.set('');
  }

  // ================= STEP TRANSITION: ADVANCE TO REVIEW =================

  /**
   * Advances to review step and triggers backend evaluation against target values.
   */
  goToReview() {
    this.commitInput();
    this.expandedDeviceValues.set(new Set());
    this.loadReviewData();
  }

  /** Loads authoritative change status and read-only reasons from backend for the review step. */
  loadReviewData() {
    this.wizard.loadReview(
      this.configService.batchGetDeviceDimensionConfigs({
        deviceIds: this.dialogData.deviceIds,
        dimensionName: this.dimensionKey(),
        dimensionList: this.dimensionList,
        target: this.targetDimensionValues(),
        universe: this.dialogData.universe,
        fleet: this.dialogData.fleet,
      }),
      'Failed to check device dimension status',
    );
  }

  /** Returns from Review Step back to Values Step. */
  goBackToValues() {
    this.wizard.setPhase('values');
  }

  /** Toggles expanded display of all current values for a device in review table. */
  toggleExpandDeviceValues(deviceId: string) {
    this.expandedDeviceValues.update((current) => {
      const next = new Set(current);
      if (next.has(deviceId)) {
        next.delete(deviceId);
      } else {
        next.add(deviceId);
      }
      return next;
    });
  }

  // ================= APPLY & RESULT =================

  /** Applies the batch dimension update to all devices marked WILL_UPDATE. */
  apply() {
    this.wizard.apply(
      (deviceIds) =>
        this.configService.batchUpdateDeviceDimensionConfigs({
          deviceIds,
          dimensionName: this.dimensionKey(),
          dimensionList: this.dimensionList,
          target: this.targetDimensionValues(),
          universe: this.dialogData.universe,
          fleet: this.dialogData.fleet,
        }),
      'Server error while applying dimension update',
    );
  }

  /** Closes dialog upon completion, confirming updates were made. */
  close() {
    this.wizard.close();
  }
}
