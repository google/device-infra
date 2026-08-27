import {
  ComplexMatch,
  Filter,
  FleetGroupSort,
  FleetSuggestion,
  TjsFilter,
  TjsSuggestion,
} from '../../../core/models/search';

import {
  ADV_MODES_LIST,
  AdvancedMatchMode,
  ComplexMatchInfo,
  FilterChip,
  ParsedQueryFilter,
  SearchBoxSuggestion,
  ValuePickerApplyEvent,
} from '../models';
export * from '../models';

/** Pre-compiled regex for parsing complex search filter conditions. */
const COMPLEX_CONDITION_REGEX =
  /^(starts with|starts|prefix|startswith|starts_with|does not contain|not contain|doesnt contain|not_substring|not substring|contains substring|contains_substring|contains|substring|does not match regex|doesnt match regex|not_regex|not regex|matches regex|matches_regex|regex|matches exactly|matches_exactly|is exactly|exactly|matches at least|matches_at_least|is at least|at least|at_least|atleast|matches|does not match|doesnt match|has all of)\b[\s:\/]*"?([^"\/]*)"?\/?$/i;

// ============================================================================
// ValuePicker Dialog ↔ ComplexMatch AST (Advanced Matching)
// ============================================================================

/** Helper to normalize any raw condition phrase into a canonical mode key matching ADV_MODES_LIST. */
function normalizeRawMode(rawMode: string): string {
  const norm = rawMode.toLowerCase().trim();
  if (norm.includes('start') || norm.includes('prefix')) return 'prefix';
  if (norm.includes('contain') || norm.includes('substring')) {
    return norm.includes('not') || norm.includes('doesnt')
      ? 'not_substring'
      : 'substring';
  }
  if (
    norm.includes('regex') ||
    norm === 'matches' ||
    norm === 'does not match' ||
    norm === 'doesnt match'
  ) {
    return norm.includes('not') || norm.includes('doesnt')
      ? 'not_regex'
      : 'regex';
  }
  if (norm.includes('least') || norm === 'has all of') return 'at_least';
  return 'exactly';
}

/** Parses raw pillCondition text string into structured ComplexMatchInfo. */
export function parseComplexCondition(
  pillCondition?: string,
  rawValues?: string[],
  negated?: boolean,
): ComplexMatch | undefined {
  if (!pillCondition) return undefined;
  const match = pillCondition.match(COMPLEX_CONDITION_REGEX);
  if (!match) return undefined;

  const rawMode = match[1];
  const targetVal = match[2]?.trim() || '';
  const modeKey = normalizeRawMode(rawMode);

  const valuesArr =
    modeKey === 'exactly' || modeKey === 'at_least'
      ? targetVal
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean)
      : rawValues?.length
        ? rawValues
        : targetVal
          ? [targetVal]
          : [];

  switch (modeKey) {
    case 'prefix':
      return {startsWith: {value: targetVal}};
    case 'substring':
      return {containsSubstring: {value: targetVal, negated: false}};
    case 'not_substring':
      return {containsSubstring: {value: targetVal, negated: true}};
    case 'regex':
      return {matchesRegex: {value: targetVal, negated: false}};
    case 'not_regex':
      return {matchesRegex: {value: targetVal, negated: true}};
    case 'at_least':
      return {matchesAtLeast: {values: valuesArr}};
    case 'exactly':
    default:
      return {matchesExactly: {values: valuesArr}};
  }
}

/** Converts a ComplexMatch Protobuf payload back to human-readable ComplexMatchInfo for UI display. */
export function extractComplexMatchInfo(
  complex?: ComplexMatch,
  fallbackCondition?: string,
  negated?: boolean,
): ComplexMatchInfo | undefined {
  if (!complex) {
    const parsed = parseComplexCondition(fallbackCondition, undefined, negated);
    if (!parsed) return undefined;
    complex = parsed;
  }

  if (complex.startsWith?.value) {
    return {
      mode: 'prefix',
      values: [complex.startsWith.value],
      isNegated: !!negated,
    };
  }
  if (complex.containsSubstring?.value) {
    return {
      mode: complex.containsSubstring.negated ? 'not_substring' : 'substring',
      values: [complex.containsSubstring.value],
      isNegated: !!complex.containsSubstring.negated || !!negated,
    };
  }
  if (complex.matchesRegex?.value) {
    return {
      mode: complex.matchesRegex.negated ? 'not_regex' : 'regex',
      values: [complex.matchesRegex.value],
      isNegated: !!complex.matchesRegex.negated || !!negated,
    };
  }
  if (complex.matchesExactly?.values?.length) {
    return {
      mode: 'exactly',
      values: complex.matchesExactly.values,
      isNegated: !!negated,
    };
  }
  if (complex.matchesAtLeast?.values?.length) {
    return {
      mode: 'at_least',
      values: complex.matchesAtLeast.values,
      isNegated: !!negated,
    };
  }

  return undefined;
}

/** Helper to extract advanced matching state from a FilterChip for ValuePicker initialization. */
export function extractAdvancedStateFromChip(chip: FilterChip): {
  isAdv: boolean;
  advMode: AdvancedMatchMode;
  advText: string;
  advValues: string[];
} {
  const info = extractComplexMatchInfo(
    chip.complex,
    chip.pillCondition,
    chip.negated,
  );
  if (info) {
    return {
      isAdv: true,
      advMode: info.mode,
      advText: info.values.join(', '),
      advValues: info.values,
    };
  }
  return {
    isAdv: false,
    advMode: 'substring',
    advText: '',
    advValues: [],
  };
}

/** Builds ComplexMatch Protobuf structure from Popover ValuePickerApplyEvent. */
export function buildComplexMatchFromEvent(
  event: ValuePickerApplyEvent,
): ComplexMatch | undefined {
  if (!event.isAdvanced || !event.advMode) return undefined;

  const mode = event.advMode;
  const txt = event.advText?.trim() || '';

  switch (mode) {
    case 'prefix':
      return {startsWith: {value: txt}};
    case 'substring':
      return {containsSubstring: {value: txt, negated: !!event.negate}};
    case 'not_substring':
      return {containsSubstring: {value: txt, negated: true}};
    case 'regex':
      return {matchesRegex: {value: txt, negated: !!event.negate}};
    case 'not_regex':
      return {matchesRegex: {value: txt, negated: true}};
    case 'exactly':
      return {matchesExactly: {values: event.advValues || (txt ? [txt] : [])}};
    case 'at_least':
      return {matchesAtLeast: {values: event.advValues || (txt ? [txt] : [])}};
    default:
      return undefined;
  }
}

/** Formats fallback human-readable pill condition string from Popover ValuePickerApplyEvent. */
export function formatPillConditionFromEvent(
  event: ValuePickerApplyEvent,
): string {
  if (event.isAdvanced) {
    const modeObj = ADV_MODES_LIST.find((m) => m.id === event.advMode);
    const modeLabel = modeObj?.label || event.advMode || '';
    const valStr = event.advValues?.length
      ? event.advValues.join(', ')
      : event.advText || '';
    return `${modeLabel} "${valStr}"`;
  }

  if (event.rangeFrom || event.rangeTo) {
    return `${event.rangeFrom || ''} ~ ${event.rangeTo || ''}`.trim();
  }

  if (event.selected && event.selected.length > 0) {
    return event.selected.join(', ');
  }

  if (event.propName || event.textVal) {
    return `${event.propName || ''}:${event.textVal || ''}`;
  }

  if (event.textVal?.trim()) {
    return event.textVal.trim();
  }

  return '';
}

/** Helper to check if a ValuePickerApplyEvent payload contains empty selection values. */
export function isValuePickerSelectionEmpty(
  event: ValuePickerApplyEvent,
): boolean {
  if (event.isAdvanced) {
    if (event.advMode === 'exactly' || event.advMode === 'at_least') {
      return !event.advValues?.length;
    }
    return !event.advText?.trim();
  }
  return (
    !event.selected?.length &&
    !event.rangeFrom?.trim() &&
    !event.rangeTo?.trim() &&
    !event.textVal?.trim() &&
    !event.propName?.trim()
  );
}

// ============================================================================
// Simple Fleet Filter Builders
// ============================================================================

/** Constructs simple Fleet Filter Protobuf payload. */
export function buildSimpleFleetFilter(
  key: string,
  values: string[],
  negated?: boolean,
): Filter {
  const isNoVal = values.includes('<empty>');
  const cleanVals = values.filter((v) => v !== '<empty>');

  return {
    key,
    simple: {
      values: [
        ...cleanVals.map((v) => ({value: v})),
        ...(isNoVal ? [{noValue: true}] : []),
      ],
      negated: !!negated,
    },
  };
}

// ============================================================================
// URL Filter String Serializer & Parser
// ============================================================================

/** Helper to extract unique values from a ComplexMatch object. */
function getValuesFromComplexMatch(complex: ComplexMatch): string[] {
  if (complex.startsWith?.value) {
    return [complex.startsWith.value];
  }
  if (complex.containsSubstring?.value) {
    return [complex.containsSubstring.value];
  }
  if (complex.matchesRegex?.value) {
    return [complex.matchesRegex.value];
  }
  if (complex.matchesExactly?.values) {
    return complex.matchesExactly.values;
  }
  if (complex.matchesAtLeast?.values) {
    return complex.matchesAtLeast.values;
  }
  return [];
}


/** Formats a ComplexMatch object into a canonical, parser-friendly string representation. */
function getCanonicalComplexCondition(complex: ComplexMatch): string {
  if (complex.startsWith?.value) {
    return `Starts with "${complex.startsWith.value}"`;
  }
  if (complex.containsSubstring?.value) {
    const term = complex.containsSubstring.value;
    return complex.containsSubstring.negated
      ? `Does not contain "${term}"`
      : `Contains "${term}"`;
  }
  if (complex.matchesRegex?.value) {
    const term = complex.matchesRegex.value;
    return complex.matchesRegex.negated
      ? `Does not match regex "${term}"`
      : `Matches regex "${term}"`;
  }
  if (complex.matchesExactly?.values?.length) {
    const vals = complex.matchesExactly.values.join(', ');
    return `Is exactly "${vals}"`;
  }
  if (complex.matchesAtLeast?.values?.length) {
    const vals = complex.matchesAtLeast.values.join(', ');
    return `Is at least "${vals}"`;
  }
  return '';
}

/** Serializes a FilterChip object into a compressed URL query string format. */
export function serializeFilterChip(c: FilterChip): string {
  if (c.isGroupBy) {
    return '';
  }

  const prefix = c.negated ? '!' : '';
  const key = c.key || c.pillKey;

  if (c.complex) {
    const cond = getCanonicalComplexCondition(c.complex);
    if (cond) {
      return `${prefix}${key}~${cond}`;
    }
  }

  if (c.rawValues && c.rawValues.length > 0) {
    return `${prefix}${key}~${c.rawValues.join(',')}`;
  }

  if (c.pillCondition) {
    return `${prefix}${key}~${c.pillCondition}`;
  }

  return `${prefix}${key}`;
}

/** Parses a URL filter param string (e.g. "-status~RUNNING") into a ParsedQueryFilter object. */
export function parseQueryFilterParam(
  rawParam: string,
): ParsedQueryFilter | null {
  if (!rawParam || typeof rawParam !== 'string') return null;

  let str = rawParam.trim();
  let negated = false;

  if (str.startsWith('-') || str.startsWith('!')) {
    negated = true;
    str = str.substring(1).trim();
  }

  // Support multiple filter separators: '~' (primary), '=' or ':' (compatibility)
  let separatorIdx = str.indexOf('~');
  if (separatorIdx === -1) {
    separatorIdx = str.indexOf('=');
  }
  if (separatorIdx === -1) {
    separatorIdx = str.indexOf(':');
  }

  let key = str;
  let rawCond = '';

  if (separatorIdx !== -1) {
    key = str.substring(0, separatorIdx).trim();
    rawCond = str.substring(separatorIdx + 1).trim();
  }

  if (!key) return null;

  const rawValues = rawCond
    ? rawCond
        .split(',')
        .map((v) => v.trim())
        .filter(Boolean)
    : [];

  const complexMatch = parseComplexCondition(rawCond, rawValues, negated);

  const finalRawValues = complexMatch
    ? getValuesFromComplexMatch(complexMatch)
    : rawValues;

  const fleetFilter: Filter = complexMatch
    ? {key, complex: complexMatch}
    : buildSimpleFleetFilter(key, finalRawValues, negated);

  const tjsFilter: TjsFilter = complexMatch
    ? {key, stringValue: {value: rawCond}}
    : finalRawValues.length > 0
      ? {key, stringValue: {value: finalRawValues.join(',')}}
      : {key, stringValue: {value: rawCond}};

  return {
    key,
    filter: fleetFilter,
    tjsFilter,
    fallbackPillCondition: rawCond || key,
    rawValues: finalRawValues.length > 0 ? finalRawValues : undefined,
    negated,
    complex: complexMatch,
  };
}

/** Converts a FilterChip to a Fleet Filter Protobuf payload. */
export function buildFleetFilterFromChip(c: FilterChip): Filter {
  const key = c.key || c.pillKey;
  if (c.complex) {
    return {key, complex: c.complex};
  }

  const parsedComplex = parseComplexCondition(
    c.pillCondition,
    c.rawValues,
    c.negated,
  );
  if (parsedComplex) {
    return {key, complex: parsedComplex};
  }

  const rawVals = c.rawValues?.length
    ? c.rawValues
    : c.pillCondition.split(',');

  return buildSimpleFleetFilter(key, rawVals, c.negated);
}

// ============================================================================
// Chip Dimension & Key Comparison (Deduplication)
// ============================================================================

/** Strips known filter key prefixes ('field::', 'dim::', 'config::') and lowercases the key. */
export function normalizeKey(key: string | null | undefined): string {
  if (!key) return '';
  return key.toLowerCase().replace(/^(field::|dim::|config::)/, '');
}

/** Extracts a normalized lower-case search key for chip comparison. */
export function getNormalizedChipKey(c: FilterChip): string {
  const rawKey = c.isGroupBy ? c.pillKey : c.key || c.pillKey;
  return normalizeKey(rawKey);
}

/** Helper to compare whether two filter chips refer to the same logical search key. */
export function isSameFilterChip(a: FilterChip, b: FilterChip): boolean {
  if (a === b) return true;
  if (!!a.isGroupBy !== !!b.isGroupBy) return false;
  return getNormalizedChipKey(a) === getNormalizedChipKey(b);
}

/** Normalizes Angular Router query param value (string or string[]) into a clean array of strings. */
export function getQueryParamAsArray(rawParam: unknown): string[] {
  if (Array.isArray(rawParam)) {
    return rawParam.map(String).filter(Boolean);
  }
  if (typeof rawParam === 'string' && rawParam) {
    return [rawParam];
  }
  return [];
}

/** Builds a normalized canonical key string representing URL query parameters for comparison and deduplication. */
export function buildUrlParamKey(
  filters: string[],
  groupBys: string[],
  fleet?: string | null,
): string {
  const normFilters = Array.from(filters, (s) => s.toLowerCase().trim())
    .filter(Boolean)
    .sort()
    .join('&');
  const normGb = Array.from(groupBys, (s) => s.toLowerCase().trim())
    .filter(Boolean)
    .sort()
    .join(',');
  const normFleet =
    fleet && fleet.toLowerCase().trim() !== 'internal'
      ? fleet.toLowerCase().trim()
      : '';
  return `f=${normFilters}|gb=${normGb}|fleet=${normFleet}`;
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


/**
 * Builds a FleetGroupSort Protobuf payload from a frontend sort value string and active group-by keys.
 * Supports 'count:desc', 'count:asc', 'gb_asc:<key>', 'gb_desc:<key>', 'name:asc', and 'name:desc'.
 * Preserves exact group keys containing colons (e.g. 'host::lab_type').
 */
export function buildFleetGroupSort(
  sortStr: string,
  groupByKeys: string[],
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

  if (sortStr === 'name:asc' && groupByKeys.length > 0) {
    return {field: {groupKey: groupByKeys[0]}, ascending: true};
  }

  if (sortStr === 'name:desc' && groupByKeys.length > 0) {
    return {field: {groupKey: groupByKeys[0]}, ascending: false};
  }

  // Fallback for direct field key (e.g. 'host::lab_type:asc')
  const colonIdx = sortStr.lastIndexOf(':');
  if (colonIdx > 0) {
    const key = sortStr.substring(0, colonIdx);
    const dir = sortStr.substring(colonIdx + 1);
    if (groupByKeys.includes(key)) {
      return {field: {groupKey: key}, ascending: dir === 'asc'};
    }
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
    ?.map((v) => v.value || (v.noValue ? '<empty>' : ''))
    .filter(Boolean);
  const isNegated = rf?.simple?.negated || false;
  return {
    key: rf?.key,
    pillKey: af.pillKey || 'Filter',
    pillCondition: af.pillCondition || '',
    isGroupBy: false,
    metadata: af.metadata,
    rawValues: rawVals && rawVals.length > 0 ? rawVals : undefined,
    negated: isNegated,
    complex: rf?.complex,
  };
}

/** Extracts a FilterChip representation from a selected TjsSuggestion item. */
export function extractFilterChipFromTjsSuggestion(
  item: TjsSuggestion,
): FilterChip | null {
  if (!item.applyFilter) return null;
  const af = item.applyFilter;
  let rawVals: string[] | undefined;
  if (af.filter?.stringValue?.value) {
    rawVals = [af.filter.stringValue.value];
  } else if (
    af.filter?.enumValues?.values &&
    af.filter.enumValues.values.length > 0
  ) {
    rawVals = af.filter.enumValues.values;
  } else if (af.filter?.namedValue) {
    rawVals = [
      af.filter.namedValue.name || '',
      af.filter.namedValue.value || '',
    ].filter(Boolean);
  }
  return {
    key: af.filter?.key,
    pillKey: af.pillKey || af.keyDisplayName || 'Filter',
    pillCondition: af.pillCondition || '',
    isGroupBy: false,
    rawValues: rawVals && rawVals.length > 0 ? rawVals : undefined,
  };
}

