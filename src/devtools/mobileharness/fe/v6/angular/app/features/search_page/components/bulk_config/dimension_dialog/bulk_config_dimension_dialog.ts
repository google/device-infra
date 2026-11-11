import {NgTemplateOutlet} from '@angular/common';
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
  canonicalizeValueSet,
  extractDimensionTokens,
  formatCurrentSelectionBadge,
  formatDeviceCount,
  formatNothingToUpdateMessage,
  formatWizardHeadline,
  formatWizardSupportText,
  getDeviceTargetKey,
  hasClosedQuotes,
} from '../../../utils';
import {BulkClearBanner} from '../common/bulk_clear_banner';
import {BulkPanelStatus} from '../common/bulk_panel_status';
import {BulkReviewHeaderBanner} from '../common/bulk_review_header_banner';
import {BulkReviewTable} from '../common/bulk_review_table';
import {BulkWizardFooterActions} from '../common/bulk_wizard_footer_actions';
import {
  useBulkWizardSession,
  useInlineTask,
} from '../common/bulk_wizard_session';
import {BulkWizardStatusView} from '../common/bulk_wizard_status_view';

/**
 * Bulk Dimension Configuration Dialog (2-step or 3-step wizard) for OmniLab Console FE v6.
 *
 * Aligns with cl/1001276314 and the official prototype in bulk_dimension.js:
 *
 * Curated dimension flow (2-step wizard):
 *   Step 1: Choose values (Chip input + pinned "Not set" clear option + backend candidate suggestions).
 *   Step 2: Review and apply (Authoritative change status from backend against target values).
 *
 * Custom dimension flow (3-step wizard):
 *   Step 1: Choose dimension key (Textfield + regex validation + system dimension blocklist + existing keys).
 *   Step 2: Choose values (Chip input + pinned "Not set" clear option + backend candidate suggestions).
 *   Step 3: Review and apply.
 */
@Component({
  selector: 'app-bulk-config-dimension-dialog',
  standalone: true,
  templateUrl: './bulk_config_dimension_dialog.ng.html',
  styleUrl: './bulk_config_dimension_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    BulkClearBanner,
    BulkPanelStatus,
    BulkReviewHeaderBanner,
    BulkReviewTable,
    BulkWizardFooterActions,
    BulkWizardStatusView,
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

  /** Pluralized device count text. */
  readonly deviceLabel = formatDeviceCount(this.dialogData.devices.length);

  /** Whether this dimension is in the required scope. */
  readonly isRequired =
    this.dimensionList === DimensionList.DIMENSION_LIST_REQUIRED;

  // --- Step 1 (Key Step) State ---
  readonly customKey = signal<string>('');
  readonly candidateKeys = signal<CandidateDimensionKey[]>([]);
  readonly keysTask = useInlineTask(
    'Failed to load configurable dimension keys',
  );
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
  readonly displayName = computed<string>(
    () =>
      this.dialogData.dimension?.displayName ??
      `${this.isRequired ? 'Required' : 'Supported'} Dimension: ${this.dimensionKey()}`,
  );

  // --- Values Step State ---
  /** Whether the target operation is to clear/remove the dimension. */
  readonly isClearDimension = signal<boolean>(false);
  readonly targetValues = signal<string[]>([]);
  readonly inputValue = signal<string>('');
  readonly candidateValues = signal<CandidateDimensionValue[]>([]);
  readonly valuesTask = useInlineTask('Failed to load dimension values');

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
        visibleValues: isExpanded ? values : values.slice(0, 2),
        hiddenCount,
        hiddenTooltip: isExpanded ? '' : values.slice(2).join(', '),
      };
    });
  });

  /** Constructs the reactive TargetDimensionValues message according to current UI state. */
  readonly targetDimensionValues = computed<TargetDimensionValues>(() =>
    this.isClearDimension()
      ? {clearValues: {}}
      : {valueList: {values: this.targetValues()}},
  );

  /** Dialog headline title according to current journey, phase, and terminal apply state. */
  readonly headline = computed<string>(() => {
    const name = this.displayName();
    const stepHeadline =
      this.wizard.phase() === 'key'
        ? `Configure ${this.isRequired ? 'Required' : 'Supported'} Dimension`
        : `Configure ${name}`;
    return formatWizardHeadline(this.wizard.viewMode(), name, stepHeadline);
  });

  /** Dialog supporting subtitle text. */
  readonly supportText = computed<string>(() => {
    const n = this.deviceLabel;
    const custom = this.isCustom;
    const total = custom ? 3 : 2;
    let stepSupportText: string;
    switch (this.wizard.phase()) {
      case 'key':
        stepSupportText = `Step 1 of 3 · Choose dimension key for ${n}`;
        break;
      case 'values':
        stepSupportText = `Step ${custom ? 2 : 1} of ${total} · Choose a value for ${n}`;
        break;
      default:
        stepSupportText = `Step ${custom ? 3 : 2} of ${total} · Review and apply`;
        break;
    }
    return formatWizardSupportText(this.wizard.viewMode(), stepSupportText);
  });

  /** Placeholder for chip input box. */
  readonly chipPlaceholder = computed<string>(() => {
    if (this.targetValues().length === 0) {
      return `Enter a value for ${this.displayName()}, or pick from suggestions below`;
    }
    return 'Add another value';
  });

  /** O(1) lookup set of existing dimension names in the fleet. */
  private readonly existingKeyNames = computed(
    () => new Set(this.candidateKeys().map((c) => c.dimensionName)),
  );

  /** Filtered candidate keys in Step 1 matching the user search input, with pre-computed selection badges. */
  readonly filteredCandidateKeys = computed(() => {
    const q = this.customKey();
    const totalCount = this.dialogData.devices.length;
    const candidates = this.candidateKeys();
    const matched = q
      ? candidates.filter((c) => c.dimensionName.includes(q))
      : candidates;
    return matched.slice(0, 50).map((c) => ({
      ...c,
      fleetSupportText: `${formatDeviceCount(c.fleetDeviceCount ?? 0)} in fleet`,
      badge: formatCurrentSelectionBadge(c.selectedDeviceCount, totalCount),
      isActive: c.dimensionName === q,
    }));
  });

  /** Candidate value suggestions enriched with canonical keys, fleet count text, and active selection state. */
  readonly suggestionRows = computed(() => {
    const targetCanon = this.isClearDimension()
      ? null
      : canonicalizeValueSet(this.targetValues());
    return this.candidateValues().map((sug) => {
      const canonKey = canonicalizeValueSet(sug.values);
      return {
        ...sug,
        fleetSupportText: `${formatDeviceCount(sug.deviceCount ?? 0)} in fleet`,
        canonKey,
        isActive: canonKey === targetCanon,
      };
    });
  });

  /** Whether the user can proceed from Key Step to Values Step. */
  readonly canProceedFromKeyStep = computed<boolean>(
    () => Boolean(this.customKey()) && !this.customKeyError(),
  );

  /** Whether the user typed a valid new key that is not yet in the fleet. */
  readonly isNewKey = computed<boolean>(
    () =>
      this.canProceedFromKeyStep() &&
      this.keysTask.hasLoaded() &&
      !this.existingKeyNames().has(this.customKey()),
  );

  /** Whether the user can proceed from Values Step to Review Step. */
  readonly canProceedFromValuesStep = computed<boolean>(
    () =>
      this.isClearDimension() ||
      this.targetValues().length > 0 ||
      extractDimensionTokens(this.inputValue()).length > 0,
  );

  /** Informational message shown when willUpdateCount is 0 in the review step. */
  readonly nothingToUpdateMessage = computed<string>(() => {
    const dn = this.displayName();
    return formatNothingToUpdateMessage(
      this.wizard.reviewSummary(),
      this.isClearDimension()
        ? `already have ${dn} unset`
        : `already have this ${dn} value`,
      `${dn} value`,
    );
  });

  constructor() {
    effect(() => {
      (
        this.customKeyInputField() ?? this.chipInputField()
      )?.nativeElement.focus();
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
    this.keysTask.run(
      () =>
        this.configService.batchGetConfigurableDimensionKeys({
          devices: this.dialogData.devices,
          dimensionList: this.dimensionList,
        }),
      (res) => {
        this.candidateKeys.set(res.candidateKeys ?? []);
        this.systemDimensionNames.set(new Set(res.systemDimensionNames));
      },
    );
  }

  /** Normalizes custom dimension key as user types. Validation updates reactively. */
  onCustomKeyInput(val: string) {
    this.customKey.set((val || '').trim().toLowerCase());
  }

  /** Selects a key from the existing candidate keys list. */
  pickKey(key: string) {
    this.onCustomKeyInput(key);
    this.customKeyInputField()?.nativeElement.focus();
  }

  /** Advances from Key Step to Values Step. */
  goFromKeyToValues() {
    if (!this.canProceedFromKeyStep()) return;

    const nextKey = this.customKey();
    this.wizard.setPhase('values');
    if (this.dimensionKey() !== nextKey) {
      this.dimensionKey.set(nextKey);
      this.useValueSet([]);
      this.loadValuesStepData();
    }
  }

  /** Returns back from Values Step to Key Step. */
  goBackToKey() {
    this.commitInput();
    this.wizard.setPhase('key');
  }

  // ================= STEP: VALUES SELECTION =================

  /** Loads candidate value-set suggestions for the values step. */
  loadValuesStepData() {
    this.candidateValues.set([]);
    this.valuesTask.run(
      () =>
        this.configService.getDimensionValueSuggestions({
          dimensionName: this.dimensionKey(),
          dimensionList: this.dimensionList,
        }),
      (suggestions) => {
        this.candidateValues.set(suggestions.candidateValues ?? []);
      },
    );
  }

  /** Commits any uncommitted text in the input box into targetValues. */
  commitInput() {
    const tokens = extractDimensionTokens(this.inputValue());
    this.inputValue.set('');
    if (tokens.length === 0) return;

    this.isClearDimension.set(false);
    this.targetValues.update((current) =>
      Array.from(new Set([...current, ...tokens])),
    );
  }

  /** Handles keydown events in the chip input field. */
  onInputKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter') {
      event.preventDefault();
      this.commitInput();
      return;
    }

    const input = event.target as HTMLInputElement | null;
    if (event.key === ',' || event.key === ';') {
      const value = input?.value ?? this.inputValue();
      const cursor = input?.selectionStart ?? value.length;
      if (hasClosedQuotes(value.slice(0, cursor))) {
        event.preventDefault();
        this.commitInput();
      }
      return;
    }

    const last = this.targetValues().at(-1);
    if (
      event.key === 'Backspace' &&
      last !== undefined &&
      input?.selectionStart === 0 &&
      input.selectionEnd === 0 &&
      !input.value
    ) {
      event.preventDefault();
      this.targetValues.update((current) => current.slice(0, -1));
      this.inputValue.set(last);
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

  /** Copies a distinct value set suggestion into the target values (and exits clear mode). */
  useValueSet(values: readonly string[]) {
    this.isClearDimension.set(false);
    this.targetValues.set([...values]);
    this.inputValue.set('');
    this.chipInputField()?.nativeElement.focus();
  }

  /** Sets target to clear/remove the dimension from selected devices. */
  clearTargetDimension() {
    this.isClearDimension.set(true);
    this.targetValues.set([]);
    this.inputValue.set('');
  }

  // ================= STEP TRANSITION: ADVANCE TO REVIEW =================

  /**
   * Advances to review step and triggers backend evaluation against target values.
   */
  goToReview() {
    this.commitInput();
    if (!this.canProceedFromValuesStep()) return;
    this.wizard.loadReview(
      () =>
        this.configService.batchGetDeviceDimensionConfigs({
          devices: this.dialogData.devices,
          dimensionName: this.dimensionKey(),
          dimensionList: this.dimensionList,
          target: this.targetDimensionValues(),
        }),
      'Failed to check device dimension status',
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
        }),
      'Server error while applying dimension update',
    );
  }
}
