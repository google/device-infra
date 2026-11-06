import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  Input,
  input,
  OnInit,
  output,
  signal,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {MatIconModule} from '@angular/material/icon';
import {forkJoin, of} from 'rxjs';
import {catchError, map, switchMap} from 'rxjs/operators';

import {
  ConfigurableDimension,
  DeviceDimension,
  DimensionList,
  PartStatus,
} from '../../../../../../core/models/device_config_models';
import {CONFIG_SERVICE} from '../../../../../../core/services/config/config_service';

const DIMENSION_TOKEN_REGEX =
  /"([^"]*)"?|'([^']*)'?|([^,;\r\n"'\s][^,;\r\n"']*)/g;
const CLOSED_QUOTES_REGEX = /^(?:[^"']+|"[^"]*"|'[^']*')*$/;

const DEFAULT_ATS_DIMENSIONS: readonly ConfigurableDimension[] = [
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
];

const DIMENSION_DESCRIPTIONS: ReadonlyMap<string, string> = new Map([
  [
    'pool',
    'Pool assignment for test scheduling (for example shared or cts-presubmit).',
  ],
  [
    'persist_test_harness',
    'Keep Android test harness mode enabled across reboots (true or false).',
  ],
  [
    'recovery',
    'Automatic recovery behavior when a device enters fastboot or goes offline (true or false).',
  ],
  [
    'skip_suw_app',
    'Skip Setup Wizard provisioning during device preparation (disable or enable).',
  ],
]);

const QUICK_SUGGESTIONS: ReadonlyMap<string, readonly string[]> = new Map([
  ['recovery', ['true', 'false']],
  ['skip_suw_app', ['disable', 'enable']],
  ['persist_test_harness', ['true', 'false']],
]);

function dimensionFieldKey(dim: ConfigurableDimension): string {
  return `${dim.dimensionList}:${dim.dimensionName.toLowerCase()}`;
}

function orderAtsDimensions(
  dims: readonly ConfigurableDimension[],
): ConfigurableDimension[] {
  const required = dims.filter(
    (d) => d.dimensionList === DimensionList.DIMENSION_LIST_REQUIRED,
  );
  const supported = dims.filter(
    (d) => d.dimensionList !== DimensionList.DIMENSION_LIST_REQUIRED,
  );
  return [...required, ...supported];
}

/**
 * Curated ATS single-device dimension configuration editor.
 *
 * Exposes the 4 curated ATS scheduling dimensions (pool, persist_test_harness,
 * recovery, and skip_suw_app) with chip tokenization and fleet value
 * suggestions while preserving any non-curated custom dimensions on the device.
 */
@Component({
  selector: 'app-ats-dimensions',
  standalone: true,
  templateUrl: './ats_dimensions.ng.html',
  styleUrl: './ats_dimensions.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatIconModule],
})
export class AtsDimensions implements OnInit {
  private readonly configService = inject(CONFIG_SERVICE);
  private readonly destroyRef = inject(DestroyRef);

  readonly uiStatus = input<PartStatus>({
    visible: true,
    editability: {editable: true},
  });
  readonly universe = input<string>('');

  private rawDimensions: {
    supported: DeviceDimension[];
    required: DeviceDimension[];
  } = {supported: [], required: []};

  @Input({required: true})
  set dimensions(value: {
    supported?: DeviceDimension[];
    required?: DeviceDimension[];
  }) {
    this.rawDimensions = {
      supported: [...(value?.supported ?? [])],
      required: [...(value?.required ?? [])],
    };
    this.syncValuesFromDimensions();
  }

  readonly dimensionsChange = output<{
    supported: DeviceDimension[];
    required: DeviceDimension[];
  }>();

  readonly curatedDimensions = signal<ConfigurableDimension[]>([
    ...DEFAULT_ATS_DIMENSIONS,
  ]);
  readonly valuesByKey = signal<Record<string, string[]>>({});
  readonly draftInputs = signal<Record<string, string>>({});
  readonly fleetSuggestionsByKey = signal<Record<string, string[]>>({});

  readonly isEditable = computed<boolean>(
    () => this.uiStatus().editability?.editable ?? false,
  );

  readonly hasPendingDraft = computed<boolean>(() =>
    Object.values(this.draftInputs()).some((v) => v.trim().length > 0),
  );

  readonly hasAnyAssignedValues = computed<boolean>(() =>
    this.dimensionFields().some((f) => f.values.length > 0),
  );

  readonly dimensionFields = computed(() => {
    const vals = this.valuesByKey();
    const drafts = this.draftInputs();
    const fleetSuggestions = this.fleetSuggestionsByKey();

    return this.curatedDimensions().map((dim) => {
      const fieldKey = dimensionFieldKey(dim);
      const values = vals[fieldKey] ?? [];
      const lowerKey = dim.dimensionName.toLowerCase();
      const mergedSuggestions = this.mergeSuggestions(
        fleetSuggestions[fieldKey] ?? [],
        QUICK_SUGGESTIONS.get(lowerKey) ?? [],
        values,
      );
      return {
        dim,
        fieldKey,
        values,
        draft: drafts[fieldKey] ?? '',
        suggestions: mergedSuggestions,
        description: DIMENSION_DESCRIPTIONS.get(lowerKey) ?? '',
        isRequired: dim.dimensionList === DimensionList.DIMENSION_LIST_REQUIRED,
        scopeLabel:
          dim.dimensionList === DimensionList.DIMENSION_LIST_REQUIRED
            ? 'Required'
            : 'Supported',
      };
    });
  });

  ngOnInit(): void {
    this.loadCuratedDimensionsAndSuggestions();
  }

  onDraftInput(fieldKey: string, value: string): void {
    this.draftInputs.update((curr) => ({...curr, [fieldKey]: value}));
  }

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

  commitAllDrafts(): void {
    let changed = false;
    const nextValues: Record<string, string[]> = {...this.valuesByKey()};
    const drafts = this.draftInputs();

    for (const dim of this.curatedDimensions()) {
      const fieldKey = dimensionFieldKey(dim);
      const raw = (drafts[fieldKey] ?? '').trim();
      if (!raw) continue;
      const merged = this.mergeChipTokens(nextValues[fieldKey] ?? [], raw);
      if (merged.length !== (nextValues[fieldKey] ?? []).length) {
        nextValues[fieldKey] = merged;
        changed = true;
      }
    }

    this.draftInputs.set({});
    if (changed) {
      this.valuesByKey.set(nextValues);
      this.emitUpdatedDimensions(nextValues);
    }
  }

  discardDrafts(): void {
    this.draftInputs.set({});
  }

  onChipKeydown(fieldKey: string, event: KeyboardEvent): void {
    if (!this.isEditable()) return;

    if (event.key === 'Enter') {
      event.preventDefault();
      this.commitDraft(fieldKey);
      return;
    }
    if (event.key === ',' || event.key === ';') {
      const inputEl = event.target as HTMLInputElement | null;
      const value = inputEl?.value ?? this.draftInputs()[fieldKey] ?? '';
      const cursor = inputEl?.selectionStart ?? value.length;
      if (CLOSED_QUOTES_REGEX.test(value.slice(0, cursor))) {
        event.preventDefault();
        this.commitDraft(fieldKey);
      }
      return;
    }
    if (event.key === 'Backspace') {
      const inputEl = event.target as HTMLInputElement;
      if (
        inputEl.selectionStart === 0 &&
        inputEl.selectionEnd === 0 &&
        !inputEl.value
      ) {
        const currentValues = this.valuesByKey()[fieldKey] ?? [];
        const last = currentValues[currentValues.length - 1];
        if (last !== undefined) {
          event.preventDefault();
          const next = {
            ...this.valuesByKey(),
            [fieldKey]: currentValues.slice(0, -1),
          };
          this.valuesByKey.set(next);
          this.draftInputs.update((curr) => ({...curr, [fieldKey]: last}));
          this.emitUpdatedDimensions(next);
        }
      }
    }
  }

  onChipPaste(fieldKey: string, event: ClipboardEvent): void {
    if (!this.isEditable()) return;
    event.preventDefault();
    const pasted = event.clipboardData?.getData('text') || '';
    const merged = (this.draftInputs()[fieldKey] ?? '') + pasted;
    this.draftInputs.update((curr) => ({...curr, [fieldKey]: ''}));
    this.appendTokens(fieldKey, merged);
  }

  addSuggestion(fieldKey: string, suggestion: string): void {
    if (!this.isEditable()) return;
    this.appendTokens(fieldKey, suggestion);
  }

  removeChip(fieldKey: string, index: number): void {
    if (!this.isEditable()) return;
    const next = {
      ...this.valuesByKey(),
      [fieldKey]: (this.valuesByKey()[fieldKey] ?? []).filter(
        (_, i) => i !== index,
      ),
    };
    this.valuesByKey.set(next);
    this.emitUpdatedDimensions(next);
  }

  clearDimension(fieldKey: string): void {
    if (!this.isEditable()) return;
    const next = {...this.valuesByKey(), [fieldKey]: []};
    this.valuesByKey.set(next);
    this.draftInputs.update((curr) => ({...curr, [fieldKey]: ''}));
    this.emitUpdatedDimensions(next);
  }

  clearAllDimensions(): void {
    if (!this.isEditable()) return;
    const cleared: Record<string, string[]> = {};
    for (const dim of this.curatedDimensions()) {
      cleared[dimensionFieldKey(dim)] = [];
    }
    this.valuesByKey.set(cleared);
    this.draftInputs.set({});
    this.emitUpdatedDimensions(cleared);
  }

  private loadCuratedDimensionsAndSuggestions(): void {
    const universe = this.universe() || undefined;
    this.configService
      .getConfigurableDimensions({universe})
      .pipe(
        catchError(() =>
          of({
            dimensions: [...DEFAULT_ATS_DIMENSIONS],
            allowCustomDimensions: false,
          }),
        ),
        switchMap((res) => {
          const dims =
            res.dimensions && res.dimensions.length > 0
              ? orderAtsDimensions(res.dimensions)
              : [...DEFAULT_ATS_DIMENSIONS];
          this.curatedDimensions.set(dims);
          this.syncValuesFromDimensions();

          if (dims.length === 0) {
            return of({} as Record<string, string[]>);
          }

          const calls = dims.map((dim) => {
            const fieldKey = dimensionFieldKey(dim);
            return this.configService
              .getDimensionValueSuggestions({
                dimensionName: dim.dimensionName,
                dimensionList: dim.dimensionList,
                universe,
              })
              .pipe(
                catchError(() => of({candidateValues: []})),
                map((sugRes) => {
                  const seen = new Set<string>();
                  const distinct: string[] = [];
                  for (const candidate of sugRes.candidateValues ?? []) {
                    if ((candidate.deviceCount ?? 0) <= 0) continue;
                    for (const val of candidate.values ?? []) {
                      const trimmed = val.trim();
                      if (!trimmed) continue;
                      const lower = trimmed.toLowerCase();
                      if (!seen.has(lower)) {
                        seen.add(lower);
                        distinct.push(trimmed);
                      }
                    }
                  }
                  return [fieldKey, distinct] as const;
                }),
              );
          });

          return forkJoin(calls).pipe(
            map((entries) => Object.fromEntries(entries)),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((suggestionsMap) => {
        this.fleetSuggestionsByKey.set(suggestionsMap);
      });
  }

  private syncValuesFromDimensions(): void {
    const next: Record<string, string[]> = {};
    for (const dim of this.curatedDimensions()) {
      const source =
        dim.dimensionList === DimensionList.DIMENSION_LIST_REQUIRED
          ? this.rawDimensions.required
          : this.rawDimensions.supported;
      const targetName = dim.dimensionName.toLowerCase();
      next[dimensionFieldKey(dim)] = source
        .filter((d) => d.name.trim().toLowerCase() === targetName)
        .map((d) => d.value.trim())
        .filter((v) => v.length > 0);
    }
    this.valuesByKey.set(next);
  }

  private appendTokens(fieldKey: string, raw: string): void {
    const existing = this.valuesByKey()[fieldKey] ?? [];
    const merged = this.mergeChipTokens(existing, raw);
    if (merged.length === existing.length) return;
    const next = {...this.valuesByKey(), [fieldKey]: merged};
    this.valuesByKey.set(next);
    this.emitUpdatedDimensions(next);
  }

  private mergeChipTokens(existing: readonly string[], raw: string): string[] {
    const seen = new Set(existing.map((v) => v.toLowerCase()));
    const merged = [...existing];
    for (const match of raw.matchAll(DIMENSION_TOKEN_REGEX)) {
      const token = (match[1] ?? match[2] ?? match[3] ?? '').trim();
      if (!token) continue;
      const lower = token.toLowerCase();
      if (!seen.has(lower)) {
        seen.add(lower);
        merged.push(token);
      }
    }
    return merged;
  }

  private mergeSuggestions(
    fleet: readonly string[],
    quick: readonly string[],
    assigned: readonly string[],
  ): string[] {
    const assignedLower = new Set(assigned.map((v) => v.toLowerCase()));
    const seen = new Set<string>();
    const out: string[] = [];
    for (const candidate of [...fleet, ...quick]) {
      const trimmed = candidate.trim();
      if (!trimmed) continue;
      const lower = trimmed.toLowerCase();
      if (assignedLower.has(lower) || seen.has(lower)) continue;
      seen.add(lower);
      out.push(trimmed);
      if (out.length >= 6) break;
    }
    return out;
  }

  private emitUpdatedDimensions(vals: Record<string, string[]>): void {
    const curatedRequiredNames = new Set<string>();
    const curatedSupportedNames = new Set<string>();
    for (const dim of this.curatedDimensions()) {
      if (dim.dimensionList === DimensionList.DIMENSION_LIST_REQUIRED) {
        curatedRequiredNames.add(dim.dimensionName.toLowerCase());
      } else {
        curatedSupportedNames.add(dim.dimensionName.toLowerCase());
      }
    }

    const supported: DeviceDimension[] = this.rawDimensions.supported.filter(
      (d) => !curatedSupportedNames.has(d.name.trim().toLowerCase()),
    );
    const required: DeviceDimension[] = this.rawDimensions.required.filter(
      (d) => !curatedRequiredNames.has(d.name.trim().toLowerCase()),
    );

    for (const dim of this.curatedDimensions()) {
      const values = vals[dimensionFieldKey(dim)] ?? [];
      const bucket =
        dim.dimensionList === DimensionList.DIMENSION_LIST_REQUIRED
          ? required
          : supported;
      for (const value of values) {
        bucket.push({name: dim.dimensionName, value});
      }
    }

    this.rawDimensions = {supported, required};
    this.dimensionsChange.emit({supported, required});
  }
}
