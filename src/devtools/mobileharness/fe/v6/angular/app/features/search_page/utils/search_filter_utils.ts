import {ActivatedRoute, Params, Router} from '@angular/router';

import {
  ComplexMatch,
  Filter,
  FilterValue,
  Fleet,
  FleetGroupSort,
  FleetSuggestion,
  SearchEntity,
  TjsEntity,
  TjsFilter,
  TjsSuggestion,
} from '../../../core/models/search';

import {
  AdvancedMatchMode,
  ComplexMatchInfo,
  EntityType,
  FilterChip,
  FilterPair,
  ParsedUrlQueryChips,
  SearchBoxSuggestion,
} from '../models';

// ============================================================================
// ComplexMatch AST (Advanced Matching Parsing & Display)
// ============================================================================

/**
 * Factory to construct a Protobuf ComplexMatch AST strictly adhering to the search_fleet.proto contract:
 * - StartsWith (starts/prefix): scalar string, NOT negatable (O(log n) prefix bisect index).
 * - ContainsSubstring (contains/substring): scalar string, negatable (bool negated).
 * - MatchesRegex (regex): scalar string, negatable (bool negated).
 * - MatchesExactly (exactly/exact): repeated string, NOT negatable (device's value set == this set).
 * - MatchesAtLeast (atleast/at_least): repeated string, NOT negatable (device's value set ⊇ this set).
 */
export function createComplexMatch(
  mode: AdvancedMatchMode | string,
  values: string | string[],
  negated = false,
): ComplexMatch | undefined {
  const valArray = Array.isArray(values) ? values : [values];
  const primaryVal = valArray[0]?.trim() || '';

  switch (mode) {
    case 'starts':
    case 'prefix':
      return primaryVal ? {startsWith: {value: primaryVal}} : undefined;

    case 'contains':
    case 'substring':
      return primaryVal
        ? {containsSubstring: {value: primaryVal, negated: !!negated}}
        : undefined;

    case 'not_contains':
    case 'not_substring':
      return primaryVal
        ? {containsSubstring: {value: primaryVal, negated: true}}
        : undefined;

    case 'regex':
      return primaryVal
        ? {matchesRegex: {value: primaryVal, negated: !!negated}}
        : undefined;

    case 'not_regex':
      return primaryVal
        ? {matchesRegex: {value: primaryVal, negated: true}}
        : undefined;

    case 'exactly':
    case 'exact': {
      const cleanVals = valArray.map((s) => s.trim()).filter(Boolean);
      return cleanVals.length > 0
        ? {matchesExactly: {values: cleanVals}}
        : undefined;
    }

    case 'atleast':
    case 'at_least': {
      const cleanVals = valArray.map((s) => s.trim()).filter(Boolean);
      return cleanVals.length > 0
        ? {matchesAtLeast: {values: cleanVals}}
        : undefined;
    }

    default:
      return undefined;
  }
}

/**
 * Parses raw condition string into structured Protobuf ComplexMatch AST.
 * Supports Scheme B DSL: "starts~Pixel", "contains~Pixel", "regex~^lab.*", "exactly~A,B", "atleast~X,Y".
 */
export function parseComplexCondition(
  rawCondition?: string,
  negated?: boolean,
): ComplexMatch | undefined {
  if (!rawCondition) return undefined;

  const trimmed = rawCondition.trim();
  const sepIdx = trimmed.indexOf('~');
  if (sepIdx === -1) return undefined;

  const operator = trimmed.substring(0, sepIdx).trim().toLowerCase();
  const rawValue = trimmed.substring(sepIdx + 1).trim();
  if (!rawValue) return undefined;

  const values =
    operator === 'exact' ||
    operator === 'exactly' ||
    operator === 'atleast' ||
    operator === 'at_least'
      ? rawValue
          .split(',')
          .map((v) => decodeURIComponent(v.trim()))
          .filter(Boolean)
      : [
          operator === 'regex' || operator === 'not_regex'
            ? rawValue
            : decodeURIComponent(rawValue),
        ];

  return createComplexMatch(operator, values, negated);
}

/**
 * Converts a Protobuf ComplexMatch payload into a strongly-typed ComplexMatchInfo View-Model
 * for UI display and ValuePicker state.
 * Strictly adheres to search_fleet.proto negation capabilities.
 */
export function extractComplexMatchInfo(
  complex?: ComplexMatch,
): ComplexMatchInfo | undefined {
  if (!complex) return undefined;

  if (complex.startsWith?.value) {
    return {
      mode: 'prefix',
      values: [complex.startsWith.value],
      isNegated: false,
    };
  }

  if (complex.containsSubstring?.value) {
    const isNeg = Boolean(complex.containsSubstring.negated);
    return {
      mode: isNeg ? 'not_substring' : 'substring',
      values: [complex.containsSubstring.value],
      isNegated: isNeg,
    };
  }

  if (complex.matchesRegex?.value) {
    const isNeg = Boolean(complex.matchesRegex.negated);
    return {
      mode: isNeg ? 'not_regex' : 'regex',
      values: [complex.matchesRegex.value],
      isNegated: isNeg,
    };
  }

  if (complex.matchesExactly?.values?.length) {
    return {
      mode: 'exactly',
      values: complex.matchesExactly.values,
      isNegated: false,
    };
  }

  if (complex.matchesAtLeast?.values?.length) {
    return {
      mode: 'at_least',
      values: complex.matchesAtLeast.values,
      isNegated: false,
    };
  }

  return undefined;
}

// ============================================================================
// Simple Fleet Filter Builders
// ============================================================================

/** Special UI placeholder representing an unassigned/empty value in Protobuf. */
export const EMPTY_FILTER_VALUE = '<empty>';

/** Constructs simple Fleet Filter Protobuf payload with single-pass optimization. */
export function buildSimpleFleetFilter(
  key: string,
  values: string[],
  negated?: boolean,
): Filter {
  const filterValues: FilterValue[] = [];
  let hasNoValue = false;

  for (const v of values) {
    if (v === EMPTY_FILTER_VALUE || v === '') {
      if (!hasNoValue) {
        filterValues.push({noValue: true});
        hasNoValue = true;
      }
    } else if (v) {
      filterValues.push({value: v});
    }
  }

  return {
    key,
    simple: {
      values: filterValues,
      negated: !!negated,
    },
  };
}

// ============================================================================
// URL Filter String Serializer & Parser (Scheme B Specification)
// ============================================================================

/**
 * Serializes a Protobuf ComplexMatch payload into official Scheme B DSL string:
 * (e.g. "starts~Pixel", "contains~Pixel", "regex~^lab.*", "exactly~A,B", "atleast~X,Y")
 */
export function serializeComplexCondition(complex?: ComplexMatch): string {
  if (!complex) return '';

  if (complex.startsWith?.value) {
    return `starts~${encodeURIComponent(complex.startsWith.value)}`;
  }
  if (complex.containsSubstring?.value) {
    return `contains~${encodeURIComponent(complex.containsSubstring.value)}`;
  }
  if (complex.matchesRegex?.value) {
    return `regex~${complex.matchesRegex.value}`;
  }
  if (complex.matchesExactly?.values?.length) {
    return `exactly~${complex.matchesExactly.values.map(encodeURIComponent).join(',')}`;
  }
  if (complex.matchesAtLeast?.values?.length) {
    return `atleast~${complex.matchesAtLeast.values.map(encodeURIComponent).join(',')}`;
  }

  return '';
}

/** Safe URI component decoder that gracefully falls back to raw string on malformed URI. */
function safeDecodeURIComponent(val: string): string {
  try {
    return decodeURIComponent(val);
  } catch {
    return val;
  }
}

/**
 * Serializes a FilterChip object into official Scheme B URL format:
 *   - Simple with values: [!]<key>~<val1>,<val2>
 *   - No-Value (Empty):   [!]<key>~
 *   - Complex match:      [!]<key>~<mode>~<pattern>
 */
export function serializeFilterChip(c: FilterChip): string {
  if (c.isGroupBy) {
    return '';
  }

  const prefix = isChipNegated(c) ? '!' : '';
  const key = getChipKey(c);

  // 1. Complex Match -> 3-Part Scheme B DSL (e.g. !model~contains~Pixel)
  if (c.complex) {
    const dsl = serializeComplexCondition(c.complex);
    if (dsl) {
      return `${prefix}${key}~${dsl}`;
    }
  }

  // 2. Simple Match with Values or Empty
  if (c.rawValues && c.rawValues.length > 0) {
    const hasEmpty = c.rawValues.some(
      (v) => v === EMPTY_FILTER_VALUE || v === '',
    );
    const validVals = c.rawValues
      .map((v) => v.trim())
      .filter((v) => v && v !== EMPTY_FILTER_VALUE);

    if (hasEmpty && validVals.length === 0) {
      return `${prefix}${key}~`; // (no value) -> key~
    }
    const serializedVals: string[] = [];
    if (hasEmpty) {
      serializedVals.push(EMPTY_FILTER_VALUE);
    }
    for (const val of validVals) {
      serializedVals.push(encodeURIComponent(val));
    }
    return `${prefix}${key}~${serializedVals.join(',')}`;
  }

  // 3. Fallback for pillCondition
  if (c.pillCondition) {
    const cond = c.pillCondition.trim();
    const cleanCond = cond.startsWith('!') ? cond.substring(1).trim() : cond;
    if (
      cleanCond === '(no value)' ||
      cleanCond === EMPTY_FILTER_VALUE ||
      cleanCond === key
    ) {
      return `${prefix}${key}~`;
    }
    return cleanCond ? `${prefix}${key}~${cleanCond}` : `${prefix}${key}~`;
  }

  return `${prefix}${key}~`;
}

/**
 * Parses a Scheme B URL filter param string (e.g. "!model~contains~Pixel", "status~IDLE,BUSY", "driver~")
 * directly into a standard FilterChip object.
 */
export function parseQueryFilterParam(rawParam: string): FilterChip | null {
  if (!rawParam || typeof rawParam !== 'string') return null;

  let str = rawParam.trim();
  let negated = false;

  if (str.startsWith('-') || str.startsWith('!')) {
    negated = true;
    str = str.substring(1).trim();
  }

  const parts = str.split('~');
  if (parts.length < 2) return null;

  const key = parts[0].trim();
  if (!key) return null;

  // Case A: 3-Part Complex Match (key~mode~pattern)
  if (parts.length >= 3) {
    const operator = parts[1].trim().toLowerCase();
    const rawValue = parts.slice(2).join('~').trim();
    if (rawValue) {
      const values =
        operator === 'exact' ||
        operator === 'exactly' ||
        operator === 'atleast' ||
        operator === 'at_least'
          ? rawValue
              .split(',')
              .map((v) => safeDecodeURIComponent(v.trim()))
              .filter(Boolean)
          : [
              operator === 'regex' || operator === 'not_regex'
                ? rawValue
                : safeDecodeURIComponent(rawValue),
            ];

      const complex = createComplexMatch(operator, values, negated);
      if (complex) {
        const info = extractComplexMatchInfo(complex);
        const extractedValues = info?.values || [];
        const condition = `${operator}~${rawValue}`;
        const filter = {key, complex};
        return {
          key,
          pillKey: key,
          pillCondition: condition,
          rawValues: extractedValues.length > 0 ? extractedValues : undefined,
          negated,
          complex,
          fleetFilter: filter,
        };
      }
    }
  }

  // Case B: Non-Complex Match (Simple, Multi-part, or No-Value)
  const remainingParts = parts.slice(1);
  const joinedVal = remainingParts.join('~').trim();

  // (No Value): e.g. "driver~" -> remaining part is empty string
  if (!joinedVal) {
    const simpleFilter = buildSimpleFleetFilter(
      key,
      [EMPTY_FILTER_VALUE],
      negated,
    );
    return {
      key,
      pillKey: key,
      pillCondition: key,
      rawValues: [EMPTY_FILTER_VALUE],
      negated,
      fleetFilter: simpleFilter,
    };
  }

  // If there are multiple parts separated by ~ (e.g. named pair key~name~value or range key~from~to)
  // or a single part with comma-separated values:
  const rawValues =
    remainingParts.length > 1
      ? remainingParts
          .map((v) => safeDecodeURIComponent(v.trim()))
          .map((v) => (v === '(no value)' ? EMPTY_FILTER_VALUE : v))
          .filter(Boolean)
      : remainingParts[0]
          .split(',')
          .map((v) => safeDecodeURIComponent(v.trim()))
          .map((v) => (v === '(no value)' ? EMPTY_FILTER_VALUE : v))
          .filter(Boolean);

  const simpleFilter = buildSimpleFleetFilter(key, rawValues, negated);
  const condition = rawValues.join(', ') || key;

  return {
    key,
    pillKey: key,
    pillCondition: condition,
    rawValues: rawValues.length > 0 ? rawValues : undefined,
    negated,
    fleetFilter: simpleFilter,
  };
}

/** Converts a FilterChip to a Fleet Filter Protobuf payload. */
export function buildFleetFilterFromChip(c: FilterChip): Filter {
  if (c.fleetFilter) {
    return c.fleetFilter;
  }
  const key = getChipKey(c);
  const negated = isChipNegated(c);

  if (c.complex) {
    return {key, complex: c.complex};
  }

  const rawVals = c.rawValues?.length ? c.rawValues : [EMPTY_FILTER_VALUE];
  return buildSimpleFleetFilter(key, rawVals, negated);
}

/**
 * Checks whether a FilterChip represents a negated/excluded condition.
 */
export function isChipNegated(
  chip: Partial<FilterChip> | undefined | null,
): boolean {
  if (!chip) return false;

  // 1. Explicit boolean flag on the chip is authoritative.
  if (chip.negated !== undefined) {
    return chip.negated;
  }

  // 2. If negated is not explicitly set on the chip, check complex match AST.
  if (chip.complex?.containsSubstring?.negated !== undefined) {
    return chip.complex.containsSubstring.negated;
  }
  if (chip.complex?.matchesRegex?.negated !== undefined) {
    return chip.complex.matchesRegex.negated;
  }

  const cond = (chip.pillCondition || '').trim();
  return cond.startsWith('!');
}

// ============================================================================
// Chip Dimension & Key Comparison (Deduplication)
// ============================================================================

/** Normalizes search key by trimming and lowercasing. */
export function normalizeKey(key: string | null | undefined): string {
  if (!key) return '';
  return key.toLowerCase().trim();
}

/** Extracts the raw search key from a FilterChip. */
export function getChipKey(c: FilterChip): string {
  return c.key || c.pillKey || '';
}

/** Extracts a normalized lower-case search key for chip comparison and deduplication. */
export function getNormalizedChipKey(c: FilterChip): string {
  return normalizeKey(getChipKey(c));
}

/** Helper to compare whether two filter chips refer to the same logical search key and dimension. */
export function isSameFilterChip(
  a: Partial<FilterChip> | null | undefined,
  b: Partial<FilterChip> | null | undefined,
): boolean {
  if (a === b) return true;
  if (!a || !b) return false;
  if (Boolean(a.isGroupBy) !== Boolean(b.isGroupBy)) return false;

  const keyA = getNormalizedChipKey(a as FilterChip);
  const keyB = getNormalizedChipKey(b as FilterChip);
  return !!(keyA && keyB && keyA === keyB);
}

/** Resolves the active EntityType from path or URL string based on supported domain entities. */
export function resolveEntityFromPathOrUrl(
  pathOrUrl: string,
  supportedEntities: Set<EntityType>,
  defaultEntity: EntityType = 'devices',
): EntityType {
  if (!pathOrUrl) return defaultEntity;
  const clean = pathOrUrl
    .replace(/^\//, '')
    .split('?')[0]
    .split('#')[0]
    .replace(/\/$/, '');
  const segment = clean.split('/')[0] as EntityType;
  if (supportedEntities.has(segment)) {
    return segment;
  }
  return defaultEntity;
}

/**
 * Validates whether an active URL/path precisely matches a search store instance's bound entity.
 * Returns true if the path's single segment equals the bound entity.
 * Excludes sub-routes (e.g. detail pages like /devices/:id) and non-matching entity routes.
 */
export function isSearchRouteActive(
  pathOrUrl: string,
  boundEntity: EntityType,
): boolean {
  if (!pathOrUrl || pathOrUrl === '/' || pathOrUrl === '') return true;
  const clean = pathOrUrl
    .replace(/^\//, '')
    .split('?')[0]
    .split('#')[0]
    .replace(/\/$/, '');
  const segments = clean.split('/').filter(Boolean);
  return segments.length === 1 && segments[0] === boundEntity;
}

/** Normalizes Angular Router query param value (string or string[]) into a clean array of strings. */
export function getQueryParamAsArray(rawParam: unknown): string[] {
  if (Array.isArray(rawParam)) {
    return rawParam
      .map((item) => (item != null ? String(item).trim() : ''))
      .filter(Boolean);
  }
  if (typeof rawParam === 'string') {
    const trimmed = rawParam.trim();
    return trimmed ? [trimmed] : [];
  }
  return [];
}

/** Builds a normalized canonical key string representing URL query parameters for comparison and deduplication. */
export function buildUrlParamKey(
  filters: string[],
  groupBys: string[],
  fleet?: string | null,
): string {
  const normFilters = filters
    .map(normalizeKey)
    .filter(Boolean)
    .sort()
    .join('&');
  const normGb = groupBys.map(normalizeKey).filter(Boolean).sort().join(',');
  const normFleet = normalizeKey(fleet);
  const cleanFleet = normFleet && normFleet !== 'internal' ? normFleet : '';
  return `f=${normFilters}|gb=${normGb}|fleet=${cleanFleet}`;
}

/** Generates a normalized canonical key string for a given array of filter chips. */
export function getSerializedChipsKey(
  chips: FilterChip[],
  fleet?: string | null,
): string {
  const filters = chips
    .filter((c) => !c.isGroupBy)
    .map(serializeFilterChip)
    .filter(Boolean);
  const groupBys = chips
    .filter((c) => c.isGroupBy)
    .map(getChipKey)
    .filter(Boolean);
  return buildUrlParamKey(filters, groupBys, fleet);
}

/** Maps a raw suggestion object into a unified SearchBoxSuggestion structure. */
export function mapToSearchBoxSuggestion(
  item: FleetSuggestion | TjsSuggestion,
): SearchBoxSuggestion {
  const fleetItem = item as FleetSuggestion;
  return {
    label: item.label,
    mainText: item.mainText,
    count: fleetItem.count,
    countPrefix: fleetItem.countPrefix,
    countUnit: fleetItem.countUnit,
    overMax: fleetItem.overMax,
    rawItem: item,
  };
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

/**
 * Builds a FleetGroupSort Protobuf payload from a frontend sort value string.
 * Supports 'count:desc', 'count:asc', 'gb_asc:<key>', and 'gb_desc:<key>'.
 * Preserves exact group keys containing colons (e.g. 'host::lab_type').
 */
export function buildFleetGroupSort(
  sortStr: string,
): FleetGroupSort | undefined {
  if (!sortStr) return undefined;

  if (sortStr === 'count:desc') {
    return {field: {itemCount: {}}, ascending: false};
  }
  if (sortStr === 'count:asc') {
    return {field: {itemCount: {}}, ascending: true};
  }

  if (sortStr.startsWith('gb_asc:')) {
    const key = sortStr.slice('gb_asc:'.length);
    if (key) return {field: {groupKey: key}, ascending: true};
  }

  if (sortStr.startsWith('gb_desc:')) {
    const key = sortStr.slice('gb_desc:'.length);
    if (key) return {field: {groupKey: key}, ascending: false};
  }

  return undefined;
}

/** Extracts a FilterChip representation from a selected FleetSuggestion item. */
export function extractFilterChipFromFleetSuggestion(
  item: FleetSuggestion,
): FilterChip | null {
  if (!item.applyFilter) return null;
  const af = item.applyFilter;
  const rf = af.resultingFilter;
  const rawVals = rf?.simple?.values
    ?.map((v) => v.value || (v.noValue ? EMPTY_FILTER_VALUE : ''))
    .filter(Boolean);

  return {
    key: rf?.key,
    pillKey: af.pillKey || 'Filter',
    pillCondition: af.pillCondition || '',
    isGroupBy: false,
    metadata: af.metadata,
    rawValues: rawVals && rawVals.length > 0 ? rawVals : undefined,
    negated: isChipNegated({
      complex: rf?.complex,
      negated: rf?.simple?.negated,
      pillCondition: af.pillCondition,
    }),
    complex: rf?.complex,
    fleetFilter: rf,
  };
}

/** Helper to extract rawValues string array from a TjsFilter payload. */
export function extractRawValuesFromTjsFilter(
  filter?: TjsFilter | null,
): string[] | undefined {
  if (!filter) return undefined;
  if (filter.stringValue?.value) {
    return [filter.stringValue.value];
  }
  if (filter.enumValues?.values && filter.enumValues.values.length > 0) {
    return filter.enumValues.values;
  }
  if (filter.namedValue) {
    return [filter.namedValue.name || '', filter.namedValue.value || ''].filter(
      Boolean,
    );
  }
  if (filter.timeRange) {
    const fromVal = filter.timeRange.from || '';
    const toVal = filter.timeRange.to || '';
    const vals = [fromVal, toVal].filter(Boolean);
    return vals.length > 0 ? vals : undefined;
  }
  return undefined;
}

/** Extracts a FilterChip representation from a selected TjsSuggestion item. */
export function extractFilterChipFromTjsSuggestion(
  item: TjsSuggestion,
): FilterChip | null {
  if (!item.applyFilter) return null;
  const af = item.applyFilter;
  const rawVals = extractRawValuesFromTjsFilter(af.filter);
  return {
    key: af.filter?.key,
    pillKey: af.pillKey || af.keyDisplayName || 'Filter',
    pillCondition: af.pillCondition || '',
    isGroupBy: false,
    rawValues: rawVals,
    negated: isChipNegated({pillCondition: af.pillCondition}),
    tjsFilter: af.filter,
  };
}

// ============================================================================
// Group-By FilterChip Construction
// ============================================================================

/**
 * Constructs a specialized group-by FilterChip for search bar presentation.
 *
 * @param gbKey Key to group results by (e.g. 'host', 'model').
 * @param displayName Optional user-friendly display name (e.g. 'Host', 'Model').
 */
export function createGroupByChip(
  gbKey: string,
  displayName?: string,
): FilterChip {
  const display = displayName || gbKey;
  return {
    key: gbKey,
    pillKey: display,
    pillCondition: display,
    isGroupBy: true,
  };
}

/**
 * Deduplicates filter chips by key, ensuring that later conditions
 * overwrite earlier conditions for the same key (Scenario 4).
 */
export function deduplicateFilterChipsByKey(chips: FilterChip[]): FilterChip[] {
  const map = new Map<string, FilterChip>();
  for (const chip of chips) {
    const normKey = getNormalizedChipKey(chip);
    if (normKey) {
      const compositeKey = `${chip.isGroupBy ? 'gb:' : 'f:'}${normKey}`;
      map.set(compositeKey, chip);
    }
  }
  return Array.from(map.values());
}

/**
 * Builds resolved filter chips from backend response.
 * Filters not returned by backend or if response is empty are ignored/cleared (Scenarios 1, 2, 5).
 * If enrichFn returns null/undefined (e.g. invalid chip), the chip is excluded.
 */
export function buildResolvedFilterChips<TFilter, TResolved>(
  filterPairs: Array<FilterPair<TFilter>>,
  resolvedList: TResolved[] | undefined | null,
  enrichFn: (
    chip: FilterChip,
    resolved: TResolved,
    filter: TFilter,
  ) => FilterChip | null | undefined,
): FilterChip[] {
  if (!resolvedList || resolvedList.length === 0) {
    return [];
  }

  const results: FilterChip[] = [];
  resolvedList.forEach((resolved, idx) => {
    const pair = filterPairs[idx];
    if (pair && resolved) {
      const enriched = enrichFn(pair.chip, resolved, pair.filter);
      if (enriched) {
        results.push(enriched);
      }
    }
  });

  return results;
}

// ============================================================================
// URL Parameter & Route Initialization Utilities
// ============================================================================

/** Resolves initial URL from router state or browser location synchronously. */
export function getInitialRouterUrl(router: Router): string {
  const nav =
    typeof router.currentNavigation === 'function'
      ? router.currentNavigation()
      : null;
  if (nav?.finalUrl) {
    return nav.finalUrl.toString();
  }
  if (nav?.extractedUrl) {
    return nav.extractedUrl.toString();
  }
  if (router.url && router.url !== '/') {
    return router.url;
  }
  if (typeof window !== 'undefined' && window.location?.pathname) {
    return window.location.pathname;
  }
  return router.url || '';
}

/** Parses route query params ('f' and 'gb') into structured filter and group-by chips. */
export function parseUrlChips(
  params: Params | null | undefined,
): ParsedUrlQueryChips {
  if (!params) {
    return {parsedFilters: [], groupByKeys: [], initialChips: []};
  }

  const fParams = getQueryParamAsArray(params['f']);
  const gbParam: string = params['gb'] || '';
  const groupByKeys = Array.from(
    new Set(
      gbParam
        .split(',')
        .map((k) => k.trim())
        .filter(Boolean),
    ),
  );

  const rawFilters = fParams
    .map(parseQueryFilterParam)
    .filter(Boolean) as FilterChip[];
  // Scenario 4: Later conditions overwrite earlier conditions for the same key
  const parsedFilters = deduplicateFilterChipsByKey(rawFilters);
  const groupByChips = groupByKeys.map((gb: string) => createGroupByChip(gb));

  return {
    parsedFilters,
    groupByKeys,
    initialChips: [...parsedFilters, ...groupByChips],
  };
}

/** Resolves initial filter and group-by chips from ActivatedRoute snapshot query params. */
export function resolveInitialChips(route: ActivatedRoute): FilterChip[] {
  return parseUrlChips(route.snapshot?.queryParams).initialChips;
}

/** Resolves initial fleet operational scope from ActivatedRoute snapshot query params. */
export function resolveInitialFleet(route: ActivatedRoute): 'internal' | 'ats' {
  const fleetParam = route.snapshot?.queryParams?.['fleet'];
  return fleetParam === 'ats' ? 'ats' : 'internal';
}
