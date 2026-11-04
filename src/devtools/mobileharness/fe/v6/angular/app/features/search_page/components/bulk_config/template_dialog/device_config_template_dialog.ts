import {NgTemplateOutlet} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTableModule} from '@angular/material/table';
import {EMPTY, forkJoin, Observable, of} from 'rxjs';
import {expand, finalize, map, reduce, switchMap} from 'rxjs/operators';

import {
  ConfigurableDimension,
  DeviceConfigChangeStatus,
  DeviceConfigTemplate,
  DeviceDimension,
  DeviceDimensionConfigEntry,
  DeviceTarget,
  DimensionList,
  TargetDimensionValues,
} from '../../../../../core/models/device_config_models';
import {
  Fleet,
  FleetSearchResults,
  Row,
  SearchEntity,
} from '../../../../../core/models/search';
import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {SEARCH_SERVICE} from '../../../../../core/services/search/search_service';
import {Dialog} from '../../../../../shared/components/dialog/dialog';
import {
  BulkApplyError,
  BulkConfigDialogResult,
  BulkConfigReviewSummary,
  DeviceConfigTemplateDialogData,
} from '../../../models';
import {
  extractDeviceTarget,
  formatDeviceCount,
  formatNothingToUpdateMessage,
  formatSuccessSupportText,
  getDeviceTargetKey,
} from '../../../utils';
import {BulkReviewHeaderBanner} from '../common/bulk_review_header_banner';
import {BulkStatusPill} from '../common/bulk_status_pill';
import {BulkWizardStatusView} from '../common/bulk_wizard_status_view';

const DIMENSION_TOKEN_REGEX =
  /"([^"]*)"?|'([^']*)'?|([^,;\r\n"'\s][^,;\r\n"']*)/g;
const CLOSED_QUOTES_REGEX = /^(?:[^"']+|"[^"]*"|'[^']*')*$/;

const DEFAULT_ATS_DIMENSIONS: readonly ConfigurableDimension[] = [
  {
    dimensionName: 'recovery',
    displayName: 'recovery',
    dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
  },
  {
    dimensionName: 'skip_suw_app',
    displayName: 'skip_suw_app',
    dimensionList: DimensionList.DIMENSION_LIST_SUPPORTED,
  },
  {
    dimensionName: 'pool',
    displayName: 'pool',
    dimensionList: DimensionList.DIMENSION_LIST_REQUIRED,
  },
  {
    dimensionName: 'persist_test_harness',
    displayName: 'persist_test_harness',
    dimensionList: DimensionList.DIMENSION_LIST_REQUIRED,
  },
];

const QUICK_SUGGESTIONS: ReadonlyMap<string, readonly string[]> = new Map([
  ['recovery', ['true', 'false']],
  ['skip_suw_app', ['disable', 'enable']],
  ['persist_test_harness', ['true', 'false']],
]);

const CONTROLLER_FLEET_PAGE_SIZE = 500;

/** Per-dimension diff item rendered inside a device review row. */
export interface DimensionDiffSummary {
  dimensionName: string;
  displayName: string;
  dimensionList: DimensionList;
  currentValues: string[];
  targetValues: string[];
}

/** Consolidated 3-state review row for an existing controller device. */
export interface TemplateDeviceReviewRow {
  key: string;
  deviceId: string;
  hostName?: string;
  status: DeviceConfigChangeStatus;
  readOnlyReason?: string;
  changedDimensions: DimensionDiffSummary[];
}

type TemplateViewMode =
  | 'loading'
  | 'step_error'
  | 'template'
  | 'saving_template'
  | 'template_saved'
  | 'review'
  | 'applying'
  | 'success'
  | 'errors';

function extractErrorMessage(err: unknown, fallback: string): string {
  return (err as {message?: string})?.message || fallback;
}

function dimensionFieldKey(dim: ConfigurableDimension): string {
  return `${dim.dimensionList}:${dim.dimensionName.toLowerCase()}`;
}

/**
 * Controller-level Device Configuration Template Dialog for ATS Lab Console.
 *
 * Allows ATS operators to configure default dimensions copied onto newly
 * connected unconfigured devices, clearly warns that saving a template does not
 * retroactively alter existing devices, and offers a one-click Save and apply
 * to existing devices workflow that loads all controller devices into a 3-state
 * batch dimension review with Will update devices selected by default.
 */
@Component({
  selector: 'app-device-config-template-dialog',
  standalone: true,
  templateUrl: './device_config_template_dialog.ng.html',
  styleUrl: './device_config_template_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    BulkReviewHeaderBanner,
    BulkStatusPill,
    BulkWizardStatusView,
    Dialog,
    FormsModule,
    MatCheckboxModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    NgTemplateOutlet,
  ],
})
export class DeviceConfigTemplateDialog {
  readonly dialogData: DeviceConfigTemplateDialogData =
    inject(MAT_DIALOG_DATA, {optional: true}) ?? {};
  private readonly configService = inject(CONFIG_SERVICE);
  private readonly searchService = inject(SEARCH_SERVICE);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialogRef =
    inject<
      MatDialogRef<
        DeviceConfigTemplateDialog,
        BulkConfigDialogResult | undefined
      >
    >(MatDialogRef);

  readonly reviewColumns = ['select', 'deviceId', 'changes', 'status'] as const;

  readonly mode = signal<TemplateViewMode>('loading');
  readonly statusMessage = signal<string>(
    'Loading device configuration template…',
  );
  readonly saveError = signal<string | null>(null);
  readonly templateSaved = signal<boolean>(false);

  /** Curated dimensions exposed in the template form. */
  readonly dimensions = signal<ConfigurableDimension[]>([]);

  /** Assigned chip values keyed by `dimensionFieldKey(dim)`. */
  readonly valuesByKey = signal<Record<string, string[]>>({});

  /** Uncommitted input text keyed by `dimensionFieldKey(dim)`. */
  readonly draftInputs = signal<Record<string, string>>({});

  /** Consolidated 3-state review rows across all existing controller devices. */
  readonly reviewRows = signal<TemplateDeviceReviewRow[]>([]);

  /** Composite device keys selected by the operator to update in Step 2. */
  readonly selectedDeviceKeys = signal<Set<string>>(new Set());

  /** Number of devices targeted by the active batch apply call. */
  readonly appliedDeviceCount = signal<number>(0);

  /** Per-device update errors collected across dimensions. */
  readonly applyErrors = signal<BulkApplyError[]>([]);

  private retryAction: (() => void) | null = null;

  /** View rows for the Step 1 curated dimension editor. */
  readonly dimensionFields = computed(() => {
    const vals = this.valuesByKey();
    const drafts = this.draftInputs();
    return this.dimensions().map((dim) => {
      const fieldKey = dimensionFieldKey(dim);
      const values = vals[fieldKey] ?? [];
      const suggestions = (
        QUICK_SUGGESTIONS.get(dim.dimensionName.toLowerCase()) ?? []
      ).filter((s) => !values.some((v) => v.toLowerCase() === s.toLowerCase()));
      return {
        dim,
        fieldKey,
        values,
        draft: drafts[fieldKey] ?? '',
        suggestions,
        scopeLabel:
          dim.dimensionList === DimensionList.DIMENSION_LIST_REQUIRED
            ? 'Required dimension'
            : 'Supported dimension',
      };
    });
  });

  /** Summary entries of configured template dimensions for confirmation cards. */
  readonly configuredTemplateDimensions = computed(() =>
    this.dimensionFields().filter((f) => f.values.length > 0),
  );

  /** Whether any curated dimension currently holds one or more values. */
  readonly hasAnyTemplateValues = computed<boolean>(
    () => this.configuredTemplateDimensions().length > 0,
  );

  /** Review summary counts matching BulkReviewHeaderBanner's contract. */
  readonly reviewSummary = computed<
    BulkConfigReviewSummary & {willUpdateRows: TemplateDeviceReviewRow[]}
  >(() => {
    let alreadyMatchCount = 0;
    let readOnlyCount = 0;
    const willUpdateRows: TemplateDeviceReviewRow[] = [];
    for (const row of this.reviewRows()) {
      switch (row.status) {
        case DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE:
          willUpdateRows.push(row);
          break;
        case DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES:
          alreadyMatchCount++;
          break;
        case DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY:
          readOnlyCount++;
          break;
        default:
          break;
      }
    }
    const willUpdateCount = willUpdateRows.length;
    return {
      willUpdateRows,
      willUpdateCount,
      willUpdateLabel: formatDeviceCount(willUpdateCount),
      alreadyMatchCount,
      alreadyMatchLabel: formatDeviceCount(alreadyMatchCount),
      readOnlyCount,
      readOnlyLabel: formatDeviceCount(readOnlyCount),
    };
  });

  /** Checked `WILL_UPDATE` rows to apply when clicking Apply. */
  readonly checkedWillUpdateRows = computed<TemplateDeviceReviewRow[]>(() => {
    const selected = this.selectedDeviceKeys();
    return this.reviewSummary().willUpdateRows.filter((row) =>
      selected.has(row.key),
    );
  });

  /** Formatted count of checked `WILL_UPDATE` devices for the Apply button. */
  readonly checkedWillUpdateLabel = computed<string>(() =>
    formatDeviceCount(this.checkedWillUpdateRows().length),
  );

  /** Tri-state header checkbox state across `WILL_UPDATE` rows. */
  readonly reviewSelectionState = computed<{
    all: boolean;
    some: boolean;
    disabled: boolean;
  }>(() => {
    const totalUpdatable = this.reviewSummary().willUpdateRows.length;
    if (totalUpdatable === 0) {
      return {all: false, some: false, disabled: true};
    }
    const checkedCount = this.checkedWillUpdateRows().length;
    return {
      all: checkedCount === totalUpdatable,
      some: checkedCount > 0 && checkedCount < totalUpdatable,
      disabled: false,
    };
  });

  /** Informational message when 0 existing devices need updating. */
  readonly nothingToUpdateMessage = computed<string>(() => {
    const total = this.reviewRows().length;
    if (total === 0) {
      return 'Template saved for future devices. No existing devices are currently connected to this controller.';
    }
    return formatNothingToUpdateMessage(
      this.reviewSummary(),
      total,
      'already match this device configuration template',
      'device configuration template',
    );
  });

  /** Supporting text shown on the terminal batch apply success screen. */
  readonly successSupportText = computed<string>(() =>
    formatSuccessSupportText(
      this.reviewSummary(),
      'Every selected device now matches the controller template.',
    ),
  );

  /** Dialog title derived from the active phase. */
  readonly headline = computed<string>(() => {
    switch (this.mode()) {
      case 'applying':
        return 'Applying template to existing devices';
      case 'success':
        return 'Template applied to existing devices';
      case 'errors':
        return `${formatDeviceCount(this.applyErrors().length)} could not be updated`;
      case 'template_saved':
        return 'Device config template saved';
      case 'review':
        return 'Review and apply to existing devices';
      default:
        return 'Device Config Template';
    }
  });

  /** Dialog subtitle derived from the active phase. */
  readonly supportText = computed<string>(() => {
    switch (this.mode()) {
      case 'template':
      case 'saving_template':
        return 'Controller default dimensions for newly connected unconfigured devices';
      case 'review':
        return 'Template saved · Confirm existing controller devices to update';
      default:
        return '';
    }
  });

  constructor() {
    this.loadTemplateData();
  }

  /** Loads curated ATS dimensions and the current controller template. */
  loadTemplateData(): void {
    this.retryAction = () => {
      this.loadTemplateData();
    };
    this.mode.set('loading');
    this.statusMessage.set('Loading device configuration template…');
    this.saveError.set(null);

    forkJoin({
      configurable: this.configService.getConfigurableDimensions({
        universe: this.dialogData.universe,
        fleet: this.dialogData.fleet,
      }),
      templateRes: this.configService.getDeviceConfigTemplate({
        universe: this.dialogData.universe,
      }),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({configurable, templateRes}) => {
          const dims =
            configurable.dimensions && configurable.dimensions.length > 0
              ? configurable.dimensions
              : [...DEFAULT_ATS_DIMENSIONS];
          this.dimensions.set(dims);
          this.populateValuesFromTemplate(
            dims,
            templateRes.deviceConfigTemplate,
          );
          this.mode.set('template');
        },
        error: (err: unknown) => {
          this.statusMessage.set(
            extractErrorMessage(
              err,
              'Failed to load device configuration template',
            ),
          );
          this.mode.set('step_error');
        },
      });
  }

  /** Retries the most recent failed async step. */
  retryStep(): void {
    this.retryAction?.();
  }

  /** Updates the uncommitted chip input text for a dimension field. */
  onDraftInput(fieldKey: string, value: string): void {
    this.draftInputs.update((curr) => ({...curr, [fieldKey]: value}));
  }

  /** Tokenizes and appends any uncommitted chip text for a dimension field. */
  commitDraft(fieldKey: string): void {
    const raw = (this.draftInputs()[fieldKey] ?? '').trim();
    if (!raw) {
      if (this.draftInputs()[fieldKey]) {
        this.draftInputs.update((curr) => ({...curr, [fieldKey]: ''}));
      }
      return;
    }
    this.draftInputs.update((curr) => ({...curr, [fieldKey]: ''}));
    this.appendTokens(fieldKey, raw);
  }

  /** Commits pending chip text across all dimension fields. */
  commitAllDrafts(): void {
    for (const field of this.dimensionFields()) {
      this.commitDraft(field.fieldKey);
    }
  }

  /** Handles Enter, comma/semicolon, and Backspace in a dimension chip input. */
  onChipKeydown(fieldKey: string, event: KeyboardEvent): void {
    if (event.key === 'Enter') {
      event.preventDefault();
      this.commitDraft(fieldKey);
      return;
    }
    if (event.key === ',' || event.key === ';') {
      const input = event.target as HTMLInputElement | null;
      const value = input?.value ?? this.draftInputs()[fieldKey] ?? '';
      const cursor = input?.selectionStart ?? value.length;
      if (CLOSED_QUOTES_REGEX.test(value.slice(0, cursor))) {
        event.preventDefault();
        this.commitDraft(fieldKey);
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
        const currentValues = this.valuesByKey()[fieldKey] ?? [];
        const last = currentValues.at(-1);
        if (last !== undefined) {
          event.preventDefault();
          this.valuesByKey.update((curr) => ({
            ...curr,
            [fieldKey]: currentValues.slice(0, -1),
          }));
          this.draftInputs.update((curr) => ({...curr, [fieldKey]: last}));
        }
      }
    }
  }

  /** Tokenizes pasted text into chips for a dimension field. */
  onChipPaste(fieldKey: string, event: ClipboardEvent): void {
    event.preventDefault();
    const pasted = event.clipboardData?.getData('text') || '';
    const merged = (this.draftInputs()[fieldKey] ?? '') + pasted;
    this.draftInputs.update((curr) => ({...curr, [fieldKey]: ''}));
    this.appendTokens(fieldKey, merged);
  }

  /** Adds a suggested value chip to a dimension field. */
  addSuggestion(fieldKey: string, suggestion: string): void {
    this.appendTokens(fieldKey, suggestion);
  }

  /** Removes a chip at `index` from a dimension field. */
  removeChip(fieldKey: string, index: number): void {
    this.valuesByKey.update((curr) => ({
      ...curr,
      [fieldKey]: (curr[fieldKey] ?? []).filter((_, i) => i !== index),
    }));
  }

  /** Clears all values for a single dimension field. */
  clearDimension(fieldKey: string): void {
    this.valuesByKey.update((curr) => ({...curr, [fieldKey]: []}));
    this.draftInputs.update((curr) => ({...curr, [fieldKey]: ''}));
  }

  /** Clears all values across every curated dimension field in the template. */
  clearAllDimensions(): void {
    const cleared: Record<string, string[]> = {};
    for (const dim of this.dimensions()) {
      cleared[dimensionFieldKey(dim)] = [];
    }
    this.valuesByKey.set(cleared);
    this.draftInputs.set({});
  }

  /** Builds the DeviceConfigTemplate proto payload from current UI state. */
  buildTemplatePayload(): DeviceConfigTemplate {
    const supported: DeviceDimension[] = [];
    const required: DeviceDimension[] = [];
    const vals = this.valuesByKey();

    for (const dim of this.dimensions()) {
      const values = vals[dimensionFieldKey(dim)] ?? [];
      const bucket =
        dim.dimensionList === DimensionList.DIMENSION_LIST_REQUIRED
          ? required
          : supported;
      for (const value of values) {
        bucket.push({name: dim.dimensionName, value});
      }
    }

    return {
      dimensions: {supported, required},
    };
  }

  /** Saves the template for future newly connected devices without modifying existing devices. */
  saveTemplateOnly(): void {
    if (this.mode() === 'saving_template') return;
    this.commitAllDrafts();
    this.saveError.set(null);
    this.mode.set('saving_template');
    this.dialogRef.disableClose = true;

    this.configService
      .updateDeviceConfigTemplate({
        deviceConfigTemplate: this.buildTemplatePayload(),
        universe: this.dialogData.universe,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.dialogRef.disableClose = false;
        }),
      )
      .subscribe({
        next: (res) => {
          if (!res.success) {
            this.saveError.set(
              res.error?.message ||
                'Failed to save device configuration template.',
            );
            this.mode.set('template');
            return;
          }
          this.templateSaved.set(true);
          this.mode.set('template_saved');
        },
        error: (err: unknown) => {
          this.saveError.set(
            extractErrorMessage(
              err,
              'Failed to save device configuration template.',
            ),
          );
          this.mode.set('template');
        },
      });
  }

  /**
   * Saves the template and loads all existing controller devices into the
   * 3-state batch dimension review with Will update rows checked by default.
   */
  saveAndReviewExistingDevices(): void {
    this.commitAllDrafts();
    this.saveError.set(null);
    this.retryAction = () => {
      this.saveAndReviewExistingDevices();
    };
    this.mode.set('loading');
    this.statusMessage.set(
      'Saving template and checking existing controller devices…',
    );
    this.dialogRef.disableClose = true;

    this.configService
      .updateDeviceConfigTemplate({
        deviceConfigTemplate: this.buildTemplatePayload(),
        universe: this.dialogData.universe,
      })
      .pipe(
        switchMap((saveRes) => {
          if (!saveRes.success) {
            throw new Error(
              saveRes.error?.message ||
                'Failed to save device configuration template.',
            );
          }
          this.templateSaved.set(true);
          return this.loadAllControllerDevicesAndEvaluate();
        }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.dialogRef.disableClose = false;
        }),
      )
      .subscribe({
        next: (rows) => {
          this.reviewRows.set(rows);
          this.selectedDeviceKeys.set(
            new Set(
              rows
                .filter(
                  (r) =>
                    r.status ===
                    DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
                )
                .map((r) => r.key),
            ),
          );
          this.mode.set('review');
        },
        error: (err: unknown) => {
          this.statusMessage.set(
            extractErrorMessage(
              err,
              'Failed to evaluate existing controller devices.',
            ),
          );
          this.mode.set('step_error');
        },
      });
  }

  /** Transitions from the post-save confirmation screen into the existing-device review step. */
  reviewExistingDevicesAfterSave(): void {
    this.retryAction = () => {
      this.reviewExistingDevicesAfterSave();
    };
    this.mode.set('loading');
    this.statusMessage.set('Checking existing controller devices…');

    this.loadAllControllerDevicesAndEvaluate()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rows) => {
          this.reviewRows.set(rows);
          this.selectedDeviceKeys.set(
            new Set(
              rows
                .filter(
                  (r) =>
                    r.status ===
                    DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE,
                )
                .map((r) => r.key),
            ),
          );
          this.mode.set('review');
        },
        error: (err: unknown) => {
          this.statusMessage.set(
            extractErrorMessage(
              err,
              'Failed to evaluate existing controller devices.',
            ),
          );
          this.mode.set('step_error');
        },
      });
  }

  /** Returns from Step 2 review back to Step 1 template editing. */
  backToTemplate(): void {
    this.mode.set('template');
  }

  /** Returns true if a review row is currently selected for batch update. */
  isRowSelected(row: TemplateDeviceReviewRow): boolean {
    return this.selectedDeviceKeys().has(row.key);
  }

  /** Toggles selection for a single `WILL_UPDATE` review row. */
  toggleRowSelection(row: TemplateDeviceReviewRow): void {
    if (
      row.status !==
      DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE
    ) {
      return;
    }
    this.selectedDeviceKeys.update((curr) => {
      const next = new Set(curr);
      if (next.has(row.key)) {
        next.delete(row.key);
      } else {
        next.add(row.key);
      }
      return next;
    });
  }

  /** Toggles selection across all `WILL_UPDATE` review rows. */
  toggleSelectAllUpdatable(): void {
    const updatable = this.reviewSummary().willUpdateRows;
    if (updatable.length === 0) return;
    if (this.reviewSelectionState().all) {
      this.selectedDeviceKeys.set(new Set());
    } else {
      this.selectedDeviceKeys.set(new Set(updatable.map((r) => r.key)));
    }
  }

  /** Applies the template dimensions across all checked `WILL_UPDATE` devices. */
  applyToSelectedDevices(): void {
    const selectedRows = this.checkedWillUpdateRows();
    if (selectedRows.length === 0 || this.mode() === 'applying') return;

    const updateCalls: Array<
      Observable<{errors?: Record<string, {code: string; message?: string}>}>
    > = [];

    for (const dim of this.dimensions()) {
      const targetDevices: DeviceTarget[] = selectedRows
        .filter((row) =>
          row.changedDimensions.some(
            (d) =>
              d.dimensionName === dim.dimensionName &&
              d.dimensionList === dim.dimensionList,
          ),
        )
        .map((row) => ({
          deviceId: row.deviceId,
          ...(row.hostName ? {hostName: row.hostName} : {}),
        }));

      if (targetDevices.length === 0) continue;

      const values = this.valuesByKey()[dimensionFieldKey(dim)] ?? [];
      const target: TargetDimensionValues =
        values.length === 0 ? {clearValues: {}} : {valueList: {values}};

      updateCalls.push(
        this.configService.batchUpdateDeviceDimensionConfigs({
          devices: targetDevices,
          dimensionName: dim.dimensionName,
          dimensionList: dim.dimensionList,
          target,
          universe: this.dialogData.universe,
          fleet: this.dialogData.fleet,
        }),
      );
    }

    if (updateCalls.length === 0) {
      this.appliedDeviceCount.set(selectedRows.length);
      this.mode.set('success');
      return;
    }

    this.appliedDeviceCount.set(selectedRows.length);
    this.applyErrors.set([]);
    this.mode.set('applying');
    this.dialogRef.disableClose = true;

    forkJoin(updateCalls)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.dialogRef.disableClose = false;
        }),
      )
      .subscribe({
        next: (responses) => {
          const errorByDevice = new Map<string, string>();
          for (const res of responses) {
            for (const [deviceId, err] of Object.entries(res.errors ?? {})) {
              if (!errorByDevice.has(deviceId)) {
                errorByDevice.set(deviceId, err.message || err.code);
              }
            }
          }
          if (errorByDevice.size > 0) {
            this.applyErrors.set(
              Array.from(errorByDevice.entries(), ([deviceId, reason]) => ({
                deviceId,
                reason,
              })),
            );
            this.mode.set('errors');
          } else {
            this.mode.set('success');
          }
        },
        error: (err: unknown) => {
          const reason = extractErrorMessage(
            err,
            'Server error while applying template dimensions to devices.',
          );
          this.applyErrors.set(
            selectedRows.map((row) => ({deviceId: row.deviceId, reason})),
          );
          this.mode.set('errors');
        },
      });
  }

  /** Closes the dialog and reports whether existing devices were modified. */
  close(): void {
    const mode = this.mode();
    const updatedDevices =
      mode === 'success' ||
      (mode === 'errors' &&
        this.appliedDeviceCount() > this.applyErrors().length);
    this.dialogRef.close(updatedDevices ? {updated: true} : undefined);
  }

  private populateValuesFromTemplate(
    dims: readonly ConfigurableDimension[],
    template?: DeviceConfigTemplate,
  ): void {
    const nextValues: Record<string, string[]> = {};
    const supported = template?.dimensions?.supported ?? [];
    const required = template?.dimensions?.required ?? [];

    for (const dim of dims) {
      const source =
        dim.dimensionList === DimensionList.DIMENSION_LIST_REQUIRED
          ? required
          : supported;
      const targetName = dim.dimensionName.toLowerCase();
      nextValues[dimensionFieldKey(dim)] = source
        .filter((d) => (d.name || '').toLowerCase() === targetName && d.value)
        .map((d) => d.value);
    }
    this.valuesByKey.set(nextValues);
    this.draftInputs.set({});
  }

  private appendTokens(fieldKey: string, raw: string): void {
    const current = this.valuesByKey()[fieldKey] ?? [];
    const seen = new Set(current.map((v) => v.toLowerCase()));
    const additions: string[] = [];

    for (const [, dq, sq, unquoted] of raw.matchAll(DIMENSION_TOKEN_REGEX)) {
      const token = (dq ?? sq ?? unquoted).trim();
      const lower = token.toLowerCase();
      if (token && !seen.has(lower)) {
        seen.add(lower);
        additions.push(token);
      }
    }

    if (additions.length > 0) {
      this.valuesByKey.update((curr) => ({
        ...curr,
        [fieldKey]: [...current, ...additions],
      }));
    }
  }

  private loadAllControllerDevices(): Observable<DeviceTarget[]> {
    const fetchPage = (pageToken?: string): Observable<FleetSearchResults> =>
      this.searchService.searchFleet({
        entity: SearchEntity.SEARCH_ENTITY_DEVICE,
        fleet: Fleet.FLEET_SELF,
        flat: {
          page: {
            pageSize: CONTROLLER_FLEET_PAGE_SIZE,
            ...(pageToken ? {pageToken} : {}),
          },
        },
      });

    const seenTokens = new Set<string>();
    return fetchPage().pipe(
      expand((res) => {
        const nextToken = res.flat?.nextPageToken;
        if (!nextToken || seenTokens.has(nextToken)) {
          return EMPTY;
        }
        seenTokens.add(nextToken);
        return fetchPage(nextToken);
      }),
      reduce<FleetSearchResults, Row[]>(
        (acc, res) => acc.concat(res.flat?.rows ?? []),
        [],
      ),
      map((rows) => {
        const byKey = new Map<string, DeviceTarget>();
        for (const row of rows) {
          if (!row.id) continue;
          const target = extractDeviceTarget(row);
          const key = getDeviceTargetKey(target);
          if (!byKey.has(key)) {
            byKey.set(key, target);
          }
        }
        return Array.from(byKey.values());
      }),
    );
  }

  private loadAllControllerDevicesAndEvaluate(): Observable<
    TemplateDeviceReviewRow[]
  > {
    const dims = this.dimensions();
    return this.loadAllControllerDevices().pipe(
      switchMap((devices) => {
        if (devices.length === 0 || dims.length === 0) {
          return of<TemplateDeviceReviewRow[]>([]);
        }
        const vals = this.valuesByKey();
        const checks = dims.map((dim) => {
          const targetValues = vals[dimensionFieldKey(dim)] ?? [];
          const target: TargetDimensionValues =
            targetValues.length === 0
              ? {clearValues: {}}
              : {valueList: {values: targetValues}};
          return this.configService
            .batchGetDeviceDimensionConfigs({
              devices,
              dimensionName: dim.dimensionName,
              dimensionList: dim.dimensionList,
              target,
              universe: this.dialogData.universe,
              fleet: this.dialogData.fleet,
            })
            .pipe(
              map((res) => ({
                dim,
                targetValues,
                entries: res.entries ?? [],
              })),
            );
        });

        return forkJoin(checks).pipe(
          map((results) => this.mergeDimensionEvaluations(devices, results)),
        );
      }),
    );
  }

  private mergeDimensionEvaluations(
    devices: readonly DeviceTarget[],
    results: ReadonlyArray<{
      dim: ConfigurableDimension;
      targetValues: string[];
      entries: DeviceDimensionConfigEntry[];
    }>,
  ): TemplateDeviceReviewRow[] {
    const entryMaps = results.map(({dim, targetValues, entries}) => {
      const byKey = new Map<string, DeviceDimensionConfigEntry>();
      const byDeviceId = new Map<string, DeviceDimensionConfigEntry>();
      for (const entry of entries) {
        byKey.set(getDeviceTargetKey(entry), entry);
        if (!byDeviceId.has(entry.deviceId)) {
          byDeviceId.set(entry.deviceId, entry);
        }
      }
      return {dim, targetValues, byKey, byDeviceId};
    });

    return devices.map((device) => {
      const key = getDeviceTargetKey(device);
      let readOnlyReason: string | undefined;
      let isReadOnly = false;
      const changedDimensions: DimensionDiffSummary[] = [];

      for (const {dim, targetValues, byKey, byDeviceId} of entryMaps) {
        const entry = byKey.get(key) ?? byDeviceId.get(device.deviceId);
        if (!entry) continue;
        if (
          entry.status ===
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY
        ) {
          isReadOnly = true;
          readOnlyReason ??= entry.readOnlyReason;
        } else if (
          entry.status ===
          DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE
        ) {
          changedDimensions.push({
            dimensionName: dim.dimensionName,
            displayName: dim.displayName || dim.dimensionName,
            dimensionList: dim.dimensionList,
            currentValues: entry.values ?? [],
            targetValues,
          });
        }
      }

      const status = isReadOnly
        ? DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_READ_ONLY
        : changedDimensions.length > 0
          ? DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_WILL_UPDATE
          : DeviceConfigChangeStatus.DEVICE_CONFIG_CHANGE_STATUS_ALREADY_MATCHES;

      return {
        key,
        deviceId: device.deviceId,
        ...(device.hostName ? {hostName: device.hostName} : {}),
        status,
        ...(readOnlyReason ? {readOnlyReason} : {}),
        changedDimensions,
      };
    });
  }
}
