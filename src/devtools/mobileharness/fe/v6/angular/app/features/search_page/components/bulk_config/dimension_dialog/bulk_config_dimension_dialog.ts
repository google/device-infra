import {DecimalPipe, NgTemplateOutlet} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  linkedSignal,
  signal,
  viewChild,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';

import {
  CandidateDimensionKey,
  CandidateDimensionValue,
  DeviceDimensionConfigEntry,
  DeviceTarget,
  DimensionList,
  TargetDimensionValues,
} from '../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {Dialog} from '../../../../../shared/components/dialog/dialog';
import {BulkConfigDialogData} from '../../../models';
import {
  formatCurrentSelectionBadge,
  formatDeviceCount,
  formatNothingToUpdateMessage,
  formatSuccessSupportText,
  getDeviceTargetKey,
} from '../../../utils';
import {BulkReviewHeaderBanner} from '../common/bulk_review_header_banner';
import {BulkReviewTable} from '../common/bulk_review_table';
import {useBulkWizardSession} from '../common/bulk_wizard_session';
import {BulkWizardStatusView} from '../common/bulk_wizard_status_view';

const DIMENSION_TOKEN_REGEX =
  /"([^"]*)"?|'([^']*)'?|([^,;\r\n"'\s][^,;\r\n"']*)/g;
const CLOSED_QUOTES_REGEX = /^(?:[^"']+|"[^"]*"|'[^']*')*$/;

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
    BulkReviewTable,
    BulkWizardStatusView,
    DecimalPipe,
    Dialog,
    FormsModule,
    MatIconModule,
    MatTooltipModule,
    NgTemplateOutlet,
  ],
})
export class BulkConfigDimensionDialog {
  readonly dialogData: BulkConfigDialogData = inject(MAT_DIALOG_DATA);
  private readonly configService = inject(CONFIG_SERVICE);

  /** Whether this dialog is operating on a custom dimension. */
  readonly isCustom = !this.dialogData.dimension;

  readonly wizard = useBulkWizardSession<
    DeviceDimensionConfigEntry,
    'key' | 'values' | 'review'
  >(this.isCustom ? 'key' : 'values');

  readonly chipInputField =
    viewChild<ElementRef<HTMLInputElement>>('chipInput');
  readonly customKeyInputField =
    viewChild<ElementRef<HTMLInputElement>>('customKeyInput');

  /** Which dimension list in composite_dimension to configure. */
  readonly dimensionList: DimensionList =
    this.dialogData.dimension?.dimensionList ??
    this.dialogData.dimensionList ??
    DimensionList.DIMENSION_LIST_SUPPORTED;

  /** Total number of selected devices. */
  readonly totalCount = this.dialogData.devices.length;

  /** Pluralized device count text. */
  readonly deviceLabel = formatDeviceCount(this.totalCount);

  /** Whether this dimension is in the required scope. */
  readonly isRequired =
    this.dimensionList === DimensionList.DIMENSION_LIST_REQUIRED;

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
  readonly expandedDeviceValues = linkedSignal<
    readonly DeviceDimensionConfigEntry[],
    Set<string>
  >({
    source: this.wizard.reviewEntries,
    computation: () => new Set(),
  });

  /** Pre-computed review table rows with memoized value truncation and expansion state. */
  readonly reviewRows = computed(() => {
    const expanded = this.expandedDeviceValues();
    return this.wizard.reviewEntries().map((entry) => {
      const values = entry.values ?? [];
      const isExpanded = expanded.has(getDeviceTargetKey(entry));
      const hiddenCount = Math.max(0, values.length - 2);
      return {
        ...entry,
        isExpanded,
        visibleValues:
          isExpanded || hiddenCount === 0 ? values : values.slice(0, 2),
        hiddenCount,
        hiddenTooltip:
          isExpanded || hiddenCount === 0 ? '' : values.slice(2).join(', '),
      };
    });
  });

  /** Constructs the reactive TargetDimensionValues message according to targetValues. */
  readonly targetDimensionValues = computed<TargetDimensionValues>(() => {
    const values = this.targetValues();
    return values.length === 0 ? {clearValues: {}} : {valueList: {values}};
  });

  /** Dialog headline title according to current journey, phase, and terminal apply state. */
  readonly headline = computed<string>(() => {
    const name = this.displayName();
    return this.wizard.resolveHeadline(
      name,
      this.wizard.phase() === 'key'
        ? this.isRequired
          ? 'Configure Required Dimension'
          : 'Configure Supported Dimension'
        : `Configure ${name}`,
    );
  });

  /** Dialog supporting subtitle text. */
  readonly supportText = computed<string>(() => {
    const n = this.deviceLabel;
    const custom = this.isCustom;
    const total = custom ? 3 : 2;
    switch (this.wizard.phase()) {
      case 'key':
        return this.wizard.resolveSupportText(
          `Step 1 of 3 · Choose dimension key for ${n}`,
        );
      case 'values':
        return this.wizard.resolveSupportText(
          `Step ${custom ? 2 : 1} of ${total} · Choose a value for ${n}`,
        );
      case 'review':
      default:
        return this.wizard.resolveSupportText(
          `Step ${custom ? 3 : 2} of ${total} · Review and apply`,
        );
    }
  });

  /** Whether the active step has a previous step to navigate back to. */
  readonly canGoBack = computed<boolean>(
    () =>
      this.wizard.phase() === 'review' ||
      (this.isCustom && this.wizard.phase() === 'values'),
  );

  /** Placeholder for chip input box. */
  readonly chipPlaceholder = computed<string>(() => {
    if (this.targetValues().length === 0) {
      return `Enter a value for ${this.displayName()}, or pick from suggestions below`;
    }
    return 'Add another value';
  });

  /** Filtered candidate keys in Step 1 matching the user search input, with pre-computed selection badges. */
  readonly filteredCandidateKeys = computed(() => {
    const q = this.customKey();
    const candidates = this.candidateKeys();
    const matched = q
      ? candidates.filter((c) => c.dimensionName.toLowerCase().includes(q))
      : candidates;
    return matched.slice(0, 50).map((c) => ({
      ...c,
      badge: formatCurrentSelectionBadge(
        c.selectedDeviceCount,
        this.totalCount,
        'Configured on',
      ),
    }));
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

  /** Whether the user can advance from the current input step to the next step. */
  readonly canGoNext = computed<boolean>(
    () => this.wizard.phase() !== 'key' || this.canProceedFromKeyStep(),
  );

  /** Informational message shown when willUpdateCount is 0 in the review step. */
  readonly nothingToUpdateMessage = computed<string>(() => {
    const dn = this.displayName();
    return formatNothingToUpdateMessage(
      this.wizard.reviewSummary(),
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
      this.wizard.reviewSummary(),
      this.targetValues().length === 0
        ? `Removed ${dn} from every updated device.`
        : `Every updated device now uses this ${dn} value.`,
    );
  });

  constructor() {
    effect(() => {
      const mode = this.wizard.viewMode();
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
      () =>
        this.configService.batchGetConfigurableDimensionKeys({
          devices: this.dialogData.devices,
          dimensionList: this.dimensionList,
          universe: this.dialogData.universe,
          fleet: this.dialogData.fleet,
        }),
      (res) => {
        this.candidateKeys.set(res.candidateKeys ?? []);
        this.systemDimensionNames.set(
          new Set(res.systemDimensionNames?.map((s) => s.toLowerCase())),
        );
      },
      'Failed to load configurable dimension keys',
      'key',
      'Loading configurable dimensions…',
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
    this.loadValuesStepData();
  }

  /** Returns back from Values Step to Key Step. */
  goBackToKey() {
    this.commitInput();
    this.wizard.setPhase('key');
  }

  /** Navigates back one step in the wizard. */
  goBack() {
    if (this.wizard.phase() === 'review') {
      this.wizard.setPhase('values');
    } else {
      this.goBackToKey();
    }
  }

  /** Advances forward one step from the current input phase. */
  goNext() {
    if (this.wizard.phase() === 'key') {
      this.goFromKeyToValues();
    } else {
      this.goToReview();
    }
  }

  // ================= STEP: VALUES SELECTION =================

  /** Loads candidate value-set suggestions for the values step. */
  loadValuesStepData() {
    this.wizard.runStepTask(
      () =>
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
      'values',
      'Loading dimension values…',
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
    const toAdd: string[] = [];
    for (const [, dq, sq, unquoted] of raw.matchAll(DIMENSION_TOKEN_REGEX)) {
      const token = (dq ?? sq ?? unquoted).trim();
      const lower = token.toLowerCase();
      if (token && !existingLower.has(lower)) {
        existingLower.add(lower);
        toAdd.push(token);
      }
    }

    if (toAdd.length > 0) {
      this.targetValues.update((current) => [...current, ...toAdd]);
    }
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
      if (CLOSED_QUOTES_REGEX.test(textBefore)) {
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

  /** Copies a distinct value set suggestion (or empty array to clear) into the target values. */
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
    this.wizard.loadReview(
      () =>
        this.configService.batchGetDeviceDimensionConfigs({
          devices: this.dialogData.devices,
          dimensionName: this.dimensionKey(),
          dimensionList: this.dimensionList,
          target: this.targetDimensionValues(),
          universe: this.dialogData.universe,
          fleet: this.dialogData.fleet,
        }),
      'Failed to check device dimension status',
      'Checking device dimension configurations…',
    );
  }

  /** Toggles expanded display of all current values for a device in review table. */
  toggleExpandDeviceValues(row: DeviceTarget) {
    const key = getDeviceTargetKey(row);
    this.expandedDeviceValues.update((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  // ================= APPLY & RESULT =================

  /** Applies the batch dimension update to all devices marked WILL_UPDATE. */
  apply() {
    this.wizard.apply(
      (devices) =>
        this.configService.batchUpdateDeviceDimensionConfigs({
          devices,
          dimensionName: this.dimensionKey(),
          dimensionList: this.dimensionList,
          target: this.targetDimensionValues(),
          universe: this.dialogData.universe,
          fleet: this.dialogData.fleet,
        }),
      'Server error while applying dimension update',
    );
  }
}
