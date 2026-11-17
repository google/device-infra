/**
 * @fileoverview Pure BFF proto adapters and response mappers for Fleet (Devices & Hosts) search, suggestions, value lists, and Arsenal translation.
 */

import {
  ComplexMatch,
  Filter,
  Fleet,
  FleetChipResolverRequest,
  FleetChipResolverResponse,
  FleetGroupSort,
  FleetPromotedKeysResponse,
  FleetResolvedFilterChip,
  FleetResolvedGroupByChip,
  FleetSearchResults,
  FleetSuggestion,
  FleetValidFilterChip,
  FleetValueListResponse,
  SearchEntity,
  TranslateArsenalSearchResponse,
} from '../../../core/models/search';
import {
  BuiltFleetPickerFilter,
  createInitialValuePickerState,
  EMPTY_FILTER_VALUE,
  EntityType,
  FilterChip,
  FilterKeyMetadata,
  FilterPair,
  FleetScope,
  PickerValueItem,
  PromotedFilterKeyItem,
  PromotedGroupByKeyItem,
  ValuePickerApplyEvent,
  ValuePickerConfig,
  ValuePickerState,
} from '../models';
import {formatPaginationRange} from './search_cell_utils';
import {
  buildFleetFilterFromChip,
  buildSimpleFleetFilter,
  createGroupByChip,
  extractComplexMatchInfo,
  getChipKey,
  isChipNegated,
} from './search_filter_utils';
import {buildResolvedFilterChips} from './search_url_utils';
import {
  buildComplexMatchFromEvent,
  extractAdvancedStateFromChip,
} from './value_picker_utils';

const DUPLICATE_KEY_WARNING_PREFIX =
  'Only one filter per key is supported; ignored ';

/** Extracts raw string values (including `<empty>` for `noValue`) from a simple Fleet Filter proto. */
function extractRawValuesFromFleetFilter(
  filter?: Filter,
): string[] | undefined {
  const vals = filter?.simple?.values
    ?.map((v) => v.value || (v.noValue ? EMPTY_FILTER_VALUE : ''))
    .filter(Boolean);
  return vals?.length ? vals : undefined;
}

/** Maps string entity type to SearchEntity protobuf enum value. */
export function toSearchEntityProto(entity: EntityType): SearchEntity {
  return entity === 'hosts'
    ? SearchEntity.SEARCH_ENTITY_HOST
    : SearchEntity.SEARCH_ENTITY_DEVICE;
}

/** Maps string fleet to Fleet protobuf enum value. */
export function toFleetProto(fleet?: string): Fleet {
  return fleet === 'ats' ? Fleet.FLEET_ATS : Fleet.FLEET_SELF;
}

/** Builds a FleetGroupSort Protobuf payload from a frontend sort value string. */
export function buildFleetGroupSort(
  sortStr: string,
): FleetGroupSort | undefined {
  if (sortStr === 'count:desc' || sortStr === 'count:asc') {
    return {field: {itemCount: {}}, ascending: sortStr === 'count:asc'};
  }
  if (sortStr.startsWith('gb_asc:') || sortStr.startsWith('gb_desc:')) {
    const ascending = sortStr.startsWith('gb_asc:');
    const key = sortStr.slice(ascending ? 7 : 8);
    return key ? {field: {groupKey: key}, ascending} : undefined;
  }
  return undefined;
}

/** Constructs a FilterChip from a canonical backend Filter and FleetValidFilterChip. */
export function buildFilterChipFromResolved(
  filter: Filter | undefined,
  valid: Partial<FleetValidFilterChip>,
  fallbackPillKey = filter?.key || 'Filter',
): FilterChip {
  return {
    key: filter?.key,
    pillKey: valid.pillKey || fallbackPillKey,
    pillCondition: valid.pillCondition || '',
    isGroupBy: false,
    metadata: valid.metadata,
    rawValues: extractRawValuesFromFleetFilter(filter),
    negated: isChipNegated({
      complex: filter?.complex,
      negated: filter?.simple?.negated,
      pillCondition: valid.pillCondition,
    }),
    complex: filter?.complex,
    fleetFilter: filter,
  };
}

/** Extracts a FilterChip representation from a selected FleetSuggestion item. */
export function extractFilterChipFromFleetSuggestion(
  item: FleetSuggestion,
): FilterChip | null {
  return item.applyFilter
    ? buildFilterChipFromResolved(
        item.applyFilter.resultingFilter,
        item.applyFilter,
        'Filter',
      )
    : null;
}

/** Extracts filter and group-by chips from a selected FleetSuggestion item. */
export function extractChipsFromFleetSuggestion(
  rawItem: FleetSuggestion,
): FilterChip[] {
  const chips: FilterChip[] = [];
  const filterChip = extractFilterChipFromFleetSuggestion(rawItem);
  if (filterChip) chips.push(filterChip);
  if (rawItem.addGroupBy) {
    const key = rawItem.addGroupBy.key;
    chips.push(createGroupByChip(key, rawItem.addGroupBy.pillKey || key));
  }
  return chips;
}

/** Resolves group-by keys against backend-validated chips, dropping invalid keys and recording warnings. */
export function resolveFleetGroupByChips(
  groupByKeys: readonly string[],
  groupByChips: readonly FleetResolvedGroupByChip[] | undefined,
  warnings: string[] = [],
): FilterChip[] {
  const resolved: FilterChip[] = [];
  for (let i = 0; i < groupByKeys.length; i++) {
    const gbKey = groupByKeys[i];
    const gbc = groupByChips?.[i];
    if (gbc?.invalid?.reason) {
      warnings.push(gbc.invalid.reason);
    }
    if (gbc?.invalid) continue;
    resolved.push(
      createGroupByChip(
        gbKey,
        gbc?.valid?.displayName || gbc?.valid?.pillKey || gbKey,
      ),
    );
  }
  return resolved;
}

/** Formats one or more dropped-chip reasons into a single concise snackbar line. */
export function formatFleetChipWarnings(warnings: readonly string[]): string {
  const duplicateKeyTargets: string[] = [];
  const otherWarnings: string[] = [];
  for (const raw of warnings) {
    const trimmed = raw.trim();
    if (trimmed.startsWith(DUPLICATE_KEY_WARNING_PREFIX)) {
      const target = trimmed
        .slice(DUPLICATE_KEY_WARNING_PREFIX.length)
        .replace(/\.$/, '')
        .trim();
      if (!duplicateKeyTargets.includes(target)) {
        duplicateKeyTargets.push(target);
      }
    } else if (!otherWarnings.includes(trimmed)) {
      otherWarnings.push(trimmed);
    }
  }
  const clauses: string[] = [];
  if (duplicateKeyTargets.length > 0) {
    const suffix = otherWarnings.length === 0 ? '.' : '';
    clauses.push(
      `${DUPLICATE_KEY_WARNING_PREFIX}${duplicateKeyTargets.join(', ')}${suffix}`,
    );
  }
  clauses.push(...otherWarnings);
  return clauses.join('; ');
}

/** Builds a dictionary map of filter and group-by key metadata from active chips, pending filter, and promoted keys. */
export function buildFleetKeyMetadataMap(
  activeChips: readonly FilterChip[],
  pendingFilter: {key: string; metadata?: FilterKeyMetadata} | null,
  promoted?: {
    filterKeys: readonly PromotedFilterKeyItem[];
    groupByKeys: readonly PromotedGroupByKeyItem[];
  },
): Map<string, FilterKeyMetadata> {
  const nextMap = new Map<string, FilterKeyMetadata>();
  for (const chip of activeChips) {
    const chipKey = getChipKey(chip);
    if (chipKey && chip.metadata) {
      nextMap.set(chipKey, chip.metadata);
    }
  }
  if (pendingFilter?.metadata) {
    nextMap.set(pendingFilter.key, pendingFilter.metadata);
  }
  for (const pk of promoted?.filterKeys ?? []) {
    if (pk.metadata) {
      nextMap.set(pk.key, pk.metadata);
    }
  }
  for (const gk of promoted?.groupByKeys ?? []) {
    if (!nextMap.has(gk.key)) {
      nextMap.set(gk.key, {keyDisplayName: gk.displayName});
    }
  }
  return nextMap;
}

/** Unpacks a FleetResolvedFilterChip, recording any invalid reason into warnings if provided. */
export function extractValidFleetFilterChip(
  fc?: FleetResolvedFilterChip,
  warnings?: string[],
): FleetValidFilterChip | null {
  if (fc?.invalid?.reason && warnings) {
    warnings.push(fc.invalid.reason);
  }
  return fc?.invalid ? null : (fc?.valid ?? null);
}

/** Converts a TranslateArsenalSearchResponse into resolved chips, fleet scope, browseAll flag, and warnings. */
export function unpackArsenalTranslation(res: TranslateArsenalSearchResponse): {
  targetFleet: FleetScope;
  allChips: FilterChip[];
  shouldBrowseAll: boolean;
  warnings: string[];
} {
  const targetFleet: FleetScope =
    res.fleet === Fleet.FLEET_ATS ? 'ats' : 'internal';
  const filters = res.filters || [];
  const filterChips = res.filterChips || [];
  const warnings: string[] = [];
  const resolvedFilterChips = filters.reduce<FilterChip[]>((acc, f, i) => {
    const valid = extractValidFleetFilterChip(filterChips[i], warnings);
    if (valid) {
      acc.push(buildFilterChipFromResolved(f, valid));
    }
    return acc;
  }, []);
  const resolvedGroupByChips = resolveFleetGroupByChips(
    res.groupByKeys || [],
    res.groupByChips,
    warnings,
  );
  const allChips = [...resolvedFilterChips, ...resolvedGroupByChips];
  const hadTranslatedCriteria =
    filters.length > 0 || (res.groupByKeys?.length ?? 0) > 0;
  return {
    targetFleet,
    allChips,
    shouldBrowseAll: allChips.length === 0 && !hadTranslatedCriteria,
    warnings,
  };
}

/** Maps a FleetChipResolverResponse into resolved FilterChips and collects any drop warnings. */
export function mapFleetResolvedChipsResponse(
  filterPairs: ReadonlyArray<FilterPair<Filter>>,
  groupByKeys: readonly string[],
  res: FleetChipResolverResponse,
): {chips: FilterChip[]; warningText: string | null} {
  const warnings: string[] = [];
  const updatedFilterChips = buildResolvedFilterChips(
    [...filterPairs],
    res.filterChips,
    (chip, resolved) => {
      const valid = extractValidFleetFilterChip(resolved, warnings);
      return valid
        ? {
            ...chip,
            pillKey: valid.pillKey || chip.pillKey,
            pillCondition: valid.pillCondition || chip.pillCondition,
            metadata: valid.metadata || chip.metadata,
          }
        : null;
    },
  );
  const updatedGroupByChips = resolveFleetGroupByChips(
    groupByKeys,
    res.groupByChips,
    warnings,
  );
  return {
    chips: [...updatedFilterChips, ...updatedGroupByChips],
    warningText: warnings.length > 0 ? formatFleetChipWarnings(warnings) : null,
  };
}

/** Maps a FleetPromotedKeysResponse into promoted filter and group-by key items. */
export function mapFleetPromotedKeysResponse(res: FleetPromotedKeysResponse): {
  filterKeys: PromotedFilterKeyItem[];
  groupByKeys: PromotedGroupByKeyItem[];
} {
  return {
    filterKeys: (res.filterKeys || []).map((pk) => ({
      ...pk,
      displayName: pk.metadata?.keyDisplayName || pk.key,
    })),
    groupByKeys: res.groupByKeys || [],
  };
}

/** Builds filter pairs and a FleetChipResolverRequest from parsed URL filter chips and group-by keys. */
export function buildFleetChipResolverInput(
  parsedFilters: readonly FilterChip[],
  groupByKeys: readonly string[],
  entity: EntityType,
  fleet: string,
): {
  filterPairs: Array<FilterPair<Filter>>;
  req: FleetChipResolverRequest;
} {
  const filterPairs: Array<FilterPair<Filter>> = [];
  for (const chip of parsedFilters) {
    const filter = buildFleetFilterFromChip(chip);
    if (filter) filterPairs.push({chip, filter});
  }
  return {
    filterPairs,
    req: {
      filters:
        filterPairs.length > 0 ? filterPairs.map((p) => p.filter) : undefined,
      groupByKeys: groupByKeys.length > 0 ? [...groupByKeys] : undefined,
      entity: toSearchEntityProto(entity),
      fleet: toFleetProto(fleet),
    },
  };
}

/** Formats pagination range text for flat or grouped Fleet search results. */
export function computeFleetRangeText(
  isGrouped: boolean,
  res: FleetSearchResults | null,
): string {
  if (isGrouped) {
    const grp = res?.grouped;
    return formatPaginationRange(
      grp?.rangeStart,
      grp?.rangeEnd,
      grp?.totalGroups,
      ' groups',
    );
  }
  const flat = res?.flat;
  return formatPaginationRange(flat?.rangeStart, flat?.rangeEnd, flat?.total);
}

/** Maps a FleetValueListResponse proto into PickerValueItem options. */
export function mapFleetValueListResponse(
  res: FleetValueListResponse,
): PickerValueItem[] {
  const group = res.counted ?? res.plain;
  if (!group) return [];
  const withCounts = (entry: object) => {
    if (!res.counted) return {};
    const {filtered = 0, total = 0} = entry as {
      filtered?: number;
      total?: number;
    };
    return {filtered, total};
  };
  const list: PickerValueItem[] = (group.values || []).map((v) => ({
    value: v.value,
    displayLabel: v.displayLabel || v.value,
    ...withCounts(v),
  }));
  if (group.noValueEntry) {
    list.push({
      value: EMPTY_FILTER_VALUE,
      displayLabel: '(no value)',
      ...withCounts(group.noValueEntry),
      isNoValue: true,
    });
  }
  return list;
}

/** Constructs initial ValuePicker interaction state from an active Fleet filter chip. */
export function buildFleetInitialPickerState(
  activeChip?: FilterChip,
  stagedValues: readonly string[] = [],
): ValuePickerState {
  const base = createInitialValuePickerState();
  if (!activeChip) {
    return {...base, selectedValues: new Set(stagedValues)};
  }

  const adv = extractAdvancedStateFromChip(activeChip);
  if (adv.isAdv) {
    return {
      ...base,
      selectedValues: new Set(stagedValues),
      advanced: {
        active: true,
        mode: adv.advMode,
        text: adv.advText,
        values: adv.advValues,
        intOp: adv.intOp,
      },
    };
  }

  const chipValues = activeChip.fleetFilter?.simple?.values
    ? (extractRawValuesFromFleetFilter(activeChip.fleetFilter) ?? [])
    : (activeChip.rawValues ?? []);

  return {
    ...base,
    selectedValues: new Set([...chipValues, ...stagedValues]),
    negated: isChipNegated(activeChip),
  };
}

/** Builds a Fleet Filter proto and extracted raw values from a ValuePickerApplyEvent. */
export function buildFleetFilterFromPickerEvent(
  key: string,
  event: ValuePickerApplyEvent,
): BuiltFleetPickerFilter {
  let rawValues: string[] | undefined = event.selected;
  let complex: ComplexMatch | undefined;
  let isNegated = Boolean(event.negate);

  if (event.isAdvanced) {
    complex = buildComplexMatchFromEvent(event);
    const info = extractComplexMatchInfo(complex);
    rawValues = info?.values;
    isNegated = Boolean(info?.isNegated);
  }

  const filter: Filter = complex
    ? {key, complex}
    : buildSimpleFleetFilter(key, rawValues || [], isNegated);

  return {filter, rawValues, isNegated, complex};
}

/** Constructs Fleet-specific ValuePicker configuration from key metadata and column descriptions. */
export function buildFleetPickerConfig(
  key: string,
  title: string | undefined,
  meta: FilterKeyMetadata | undefined,
  columnDescriptions: ReadonlyMap<string, string>,
): ValuePickerConfig {
  return {
    key,
    type: 'list',
    title: meta?.keyDisplayName || title || key,
    description: meta?.description || columnDescriptions.get(key) || undefined,
    canUseAdvanced: Boolean(meta?.canUseAdvanced),
    canCompareInt: Boolean(meta?.canCompareInt),
    isPlural: Boolean(meta?.isPlural),
  };
}

/** Extracts validated FilterChips and invalid reasons from a FleetChipResolverResponse after picker apply. */
export function extractAppliedFleetPickerChips(
  key: string,
  displayTitle: string,
  meta: FilterKeyMetadata | undefined,
  built: BuiltFleetPickerFilter,
  res: FleetChipResolverResponse,
): {chips: FilterChip[]; warnings: string[]} {
  const chips: FilterChip[] = [];
  const warnings: string[] = [];
  for (const fc of res.filterChips ?? []) {
    const valid = extractValidFleetFilterChip(fc, warnings);
    if (!valid) continue;
    chips.push({
      key,
      pillKey: valid.pillKey || displayTitle,
      pillCondition: valid.pillCondition,
      isGroupBy: false,
      metadata: valid.metadata || meta,
      rawValues: built.rawValues,
      negated: built.isNegated,
      complex: built.complex,
      fleetFilter: built.filter,
    });
  }
  return {chips, warnings};
}
