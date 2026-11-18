/**
 * @fileoverview Pure BFF proto adapters and response mappers for Test / Job / Session (TJS) search, suggestions, default chips, and value pickers.
 */

import {ActivatedRoute} from '@angular/router';

import {
  TjsEntity,
  TjsFilter,
  TjsPromotedKey,
  TjsResolveChipsResponse,
  TjsSearchConfig,
  TjsSuggestion,
} from '../../../core/models/search';
import {dateUtils} from '../../../shared/utils/date_utils';
import {
  createInitialValuePickerState,
  EMPTY_FILTER_VALUE,
  EntityType,
  FilterChip,
  FilterKeyMetadata,
  FilterPair,
  PromotedFilterKeyItem,
  ValuePickerApplyEvent,
  ValuePickerConfig,
  ValuePickerState,
} from '../models';
import {getChipKey, isChipNegated} from './search_filter_utils';
import {
  buildResolvedFilterChips,
  resolveInitialChips,
} from './search_url_utils';

/** Maps frontend EntityType string enum to backend Protobuf TjsEntity enum. */
export function toTjsEntityProto(entity: EntityType): TjsEntity {
  switch (entity) {
    case 'tests':
      return TjsEntity.TJS_ENTITY_TEST;
    case 'jobs':
      return TjsEntity.TJS_ENTITY_JOB;
    case 'sessions':
      return TjsEntity.TJS_ENTITY_SESSION;
    default:
      return TjsEntity.TJS_ENTITY_TEST;
  }
}

/** Maps a FilterChip into a TjsFilter protobuf payload. */
export function buildTjsFilterFromChip(
  chip: Partial<FilterChip>,
  hasSearchConfig: boolean,
  getKeyMetadata: (key?: string) => FilterKeyMetadata | undefined,
): TjsFilter | null {
  if (chip.tjsFilter) return chip.tjsFilter;
  const rawKey = chip.key || chip.pillKey;
  if (!rawKey) return null;

  const meta = chip.metadata || getKeyMetadata(rawKey);
  if (hasSearchConfig && !meta) return null;

  if (meta?.timeRange) {
    const parseTime = (v?: string) =>
      v && v !== EMPTY_FILTER_VALUE
        ? dateUtils.pacificToUtc(v) || undefined
        : undefined;
    const from = parseTime(chip.rawValues?.[0]);
    const to = parseTime(chip.rawValues?.[1]);
    return from || to ? {key: rawKey, timeRange: {from, to}} : null;
  }
  if (meta?.namedPair) {
    const name = chip.rawValues?.[0]?.trim();
    const value = chip.rawValues?.[1]?.trim();
    return name && value ? {key: rawKey, namedValue: {name, value}} : null;
  }
  if (meta?.enumPicker) {
    const values = chip.rawValues?.filter(Boolean) ?? [];
    return values.length > 0 ? {key: rawKey, enumValues: {values}} : null;
  }

  const rawValStr =
    chip.rawValues?.filter(Boolean).join(',') || chip.pillCondition?.trim();
  return rawValStr ? {key: rawKey, stringValue: {value: rawValStr}} : null;
}

/** Helper to extract rawValues string array from a TjsFilter payload. */
export function extractRawValuesFromTjsFilter(
  filter?: TjsFilter | null,
): string[] | undefined {
  if (!filter) return undefined;
  if (filter.stringValue?.value) {
    return [filter.stringValue.value];
  }
  if (filter.enumValues?.values?.length) {
    return filter.enumValues.values;
  }
  if (filter.namedValue) {
    const {name = '', value = ''} = filter.namedValue;
    return name || value ? [name, value] : undefined;
  }
  if (filter.timeRange) {
    const fromVal = filter.timeRange.from || '';
    const toVal = filter.timeRange.to || '';
    if (!fromVal && !toVal) return undefined;
    return [fromVal || EMPTY_FILTER_VALUE, toVal || EMPTY_FILTER_VALUE];
  }
  return undefined;
}

/** Extracts a FilterChip representation from a selected TjsSuggestion item. */
export function extractFilterChipFromTjsSuggestion(
  item: TjsSuggestion,
): FilterChip | null {
  if (!item.applyFilter) return null;
  const af = item.applyFilter;
  return {
    key: af.filter?.key,
    pillKey: af.pillKey || af.keyDisplayName || 'Filter',
    pillCondition: af.pillCondition || '',
    isGroupBy: false,
    rawValues: extractRawValuesFromTjsFilter(af.filter),
    negated: isChipNegated({pillCondition: af.pillCondition}),
    tjsFilter: af.filter,
  };
}

/** Maps TjsSearchConfig default chips into FilterChip items. */
export function mapTjsDefaultChips(
  cfg: TjsSearchConfig | null,
  getKeyMetadata: (key?: string) => FilterKeyMetadata | undefined,
): FilterChip[] {
  if (!cfg?.defaultChips?.length) return [];
  return cfg.defaultChips.map((c) => {
    const rawValues = extractRawValuesFromTjsFilter(c.filter);
    const key = c.filter?.key || c.pillKey;
    const meta = getKeyMetadata(key);
    return {
      key,
      pillKey: c.pillKey || c.keyDisplayName || meta?.keyDisplayName || key,
      pillCondition: c.pillCondition || c.filter?.stringValue?.value || '',
      rawValues,
      metadata: meta,
      tjsFilter: c.filter,
    };
  });
}

/** Maps TJS search configuration promoted keys into PromotedFilterKeyItem list. */
export function buildTjsPromotedKeys(
  cfg: TjsSearchConfig | null,
): PromotedFilterKeyItem[] {
  if (!cfg?.promotedKeys) return [];
  return cfg.promotedKeys.map((pk: TjsPromotedKey) => {
    const displayName = pk.displayName || pk.key;
    return {
      key: pk.key,
      displayName,
      metadata: {...pk, keyDisplayName: displayName},
    };
  });
}

/** Computes active TJS filter chips from TjsSearchConfig and URL query parameters. */
export function computeTjsActiveChips(
  config: TjsSearchConfig | null,
  previous: {source: TjsSearchConfig | null; value: FilterChip[]} | undefined,
  route: ActivatedRoute,
  getKeyMetadata: (key?: string) => FilterKeyMetadata | undefined,
): FilterChip[] {
  const qp = route.snapshot?.queryParams;
  const hasUrlFilters = Boolean(qp?.['f'] || qp?.['gb']);
  if (previous && !previous.source && config && !hasUrlFilters) {
    return mapTjsDefaultChips(config, getKeyMetadata);
  }
  if (previous) return previous.value;
  const urlChips = hasUrlFilters ? resolveInitialChips(route) : [];
  if (urlChips.length > 0) return urlChips;
  return config ? mapTjsDefaultChips(config, getKeyMetadata) : [];
}

/** Validates and pairs parsed TJS URL chips into TjsFilter payloads and unsupported-key warnings. */
export function prepareTjsChipResolution(
  parsedFilters: readonly FilterChip[],
  entity: EntityType,
  getKeyMetadata: (key?: string) => FilterKeyMetadata | undefined,
  toTjsFilter: (chip: Partial<FilterChip>) => TjsFilter | null,
): {filterPairs: Array<FilterPair<TjsFilter>>; warnings: string[]} {
  const filterPairs: Array<FilterPair<TjsFilter>> = [];
  const warnings: string[] = [];
  for (const chip of parsedFilters) {
    const key = getChipKey(chip);
    if (!key || !getKeyMetadata(key)) {
      const label = chip.pillKey || chip.key || key || 'Unknown';
      warnings.push(
        `Filter "${label}" is not supported for ${entity} and has been ignored.`,
      );
      continue;
    }
    const filter = chip.tjsFilter || toTjsFilter(chip);
    if (filter) filterPairs.push({chip, filter});
  }
  return {filterPairs, warnings};
}

/** Maps a TjsResolveChipsResponse into enriched FilterChip items. */
export function mapTjsResolvedChipsResponse(
  filterPairs: ReadonlyArray<FilterPair<TjsFilter>>,
  res: TjsResolveChipsResponse,
  getKeyMetadata: (key?: string) => FilterKeyMetadata | undefined,
): FilterChip[] {
  return buildResolvedFilterChips(
    [...filterPairs],
    res.chips,
    (chip, resolved, filter) => {
      const meta = chip.key
        ? getKeyMetadata(chip.key) || getKeyMetadata(resolved.pillKey)
        : undefined;
      return {
        ...chip,
        pillKey: resolved.pillKey || chip.pillKey,
        pillCondition: resolved.pillCondition || chip.pillCondition,
        metadata: meta || chip.metadata,
        tjsFilter: filter,
      };
    },
  );
}

/** Constructs TJS-specific ValuePicker configuration based on key metadata. */
export function buildTjsPickerConfig(
  key: string,
  title?: string,
  meta?: FilterKeyMetadata,
): ValuePickerConfig {
  const displayTitle = meta?.keyDisplayName || title || key;
  const namedPair =
    typeof meta?.namedPair === 'object' ? meta.namedPair : undefined;
  const textInput =
    typeof meta?.textInput === 'object' ? meta.textInput : undefined;

  let type: 'list' | 'range' | 'namedPair' | 'text' = 'text';
  if (meta?.timeRange) {
    type = 'range';
  } else if (meta?.enumPicker) {
    type = 'list';
  } else if (namedPair) {
    type = 'namedPair';
  }

  return {
    key,
    type,
    title: displayTitle,
    needsName: Boolean(namedPair),
    canUseAdvanced: false,
    isPlural: Boolean(meta?.isPlural),
    namePlaceholder: namedPair?.namePlaceholder || 'Name',
    valPlaceholder:
      namedPair?.valuePlaceholder || textInput?.placeholder || 'Value',
  };
}

/** Constructs initial ValuePicker interaction state for TJS filter keys. */
export function buildTjsInitialPickerState(
  meta?: FilterKeyMetadata,
  activeChip?: FilterChip,
  stagedValues: readonly string[] = [],
): ValuePickerState {
  const enumPicker =
    typeof meta?.enumPicker === 'object' ? meta.enumPicker : undefined;
  const values =
    enumPicker?.options?.map((o) => ({
      value: o.value,
      displayLabel: o.label || o.value,
    })) ?? [];

  let chipValues: string[] = [];
  if (activeChip?.rawValues?.length) {
    chipValues = activeChip.rawValues;
  } else if (activeChip?.tjsFilter) {
    chipValues = extractRawValuesFromTjsFilter(activeChip.tjsFilter) ?? [];
  } else if (activeChip?.pillCondition) {
    chipValues = activeChip.pillCondition
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
  }

  const rawValues = [...chipValues, ...stagedValues];
  return {
    ...createInitialValuePickerState(),
    values,
    selectedValues: new Set(rawValues),
    rawValues,
  };
}

/** Extracts raw values string array from a TJS ValuePickerApplyEvent. */
export function extractTjsRawValues(
  event: ValuePickerApplyEvent,
  meta?: FilterKeyMetadata,
): string[] | undefined {
  if (
    meta?.timeRange ||
    event.rangeFrom !== undefined ||
    event.rangeTo !== undefined
  ) {
    return event.rangeFrom || event.rangeTo
      ? [
          event.rangeFrom || EMPTY_FILTER_VALUE,
          event.rangeTo || EMPTY_FILTER_VALUE,
        ]
      : undefined;
  }
  if (meta?.namedPair || event.propName) {
    return [event.propName || '', event.textVal || ''];
  }
  if (event.textVal?.trim()) {
    return [event.textVal.trim()];
  }
  if (event.selected?.length) {
    return event.selected;
  }
  return undefined;
}
