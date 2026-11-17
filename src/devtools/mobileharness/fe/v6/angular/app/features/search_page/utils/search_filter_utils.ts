/**
 * @fileoverview Pure functions for Scheme B URL filter serialization/parsing, ComplexMatch AST conversion, and FilterChip identity/deduplication.
 */

import {
  ComplexMatch,
  Filter,
  FilterValue,
  IntComparisonOperator,
} from '../../../core/models/search';
import {
  AdvancedMatchMode,
  ComplexMatchInfo,
  DEFAULT_INT_COMPARISON_OP,
  EMPTY_FILTER_VALUE,
  FilterChip,
  INT_COMPARISON_OPERATORS,
  IntComparisonOp,
} from '../models';

export {EMPTY_FILTER_VALUE};

/** Strict 32-bit signed integer pattern (no decimals, scientific notation, or trailing chars). */
const STRICT_INT_REGEX = /^[-+]?\d+$/;
const INT32_MIN = -2147483648;
const INT32_MAX = 2147483647;

/** Parses a string or number into a signed 32-bit integer, or undefined if invalid. */
export function parseStrictInt32(
  raw?: string | number | null,
): number | undefined {
  if (raw === undefined || raw === null) return undefined;
  const trimmed = String(raw).trim();
  if (!trimmed || !STRICT_INT_REGEX.test(trimmed)) return undefined;
  const parsed = Number(trimmed);
  if (
    !Number.isSafeInteger(parsed) ||
    parsed < INT32_MIN ||
    parsed > INT32_MAX
  ) {
    return undefined;
  }
  return parsed === 0 ? 0 : parsed;
}

/** Factory to construct a Protobuf ComplexMatch AST strictly adhering to search_fleet.proto. */
export function createComplexMatch(
  mode: AdvancedMatchMode | string,
  values: string | string[],
  negated = false,
  intOp: IntComparisonOp = DEFAULT_INT_COMPARISON_OP,
): ComplexMatch | undefined {
  const valArray = Array.isArray(values) ? values : [values];
  const primaryVal = valArray[0]?.trim() || '';

  switch (mode) {
    case 'compare_int':
    case 'gt':
    case 'gte':
    case 'lt':
    case 'lte': {
      const parsedInt = parseStrictInt32(primaryVal);
      if (parsedInt === undefined) return undefined;
      const resolvedOp: IntComparisonOp = mode === 'compare_int' ? intOp : mode;
      const opDef =
        INT_COMPARISON_OPERATORS.find((op) => op.id === resolvedOp) ||
        INT_COMPARISON_OPERATORS[0];
      return {intComparison: {operator: opDef.proto, value: parsedInt}};
    }
    case 'starts':
    case 'prefix':
      return primaryVal ? {startsWith: {value: primaryVal}} : undefined;
    case 'contains':
    case 'substring':
    case 'not_contains':
    case 'not_substring':
      return primaryVal
        ? {
            containsSubstring: {
              value: primaryVal,
              negated: Boolean(negated) || mode.startsWith('not_'),
            },
          }
        : undefined;
    case 'regex':
    case 'not_regex':
      return primaryVal
        ? {
            matchesRegex: {
              value: primaryVal,
              negated: Boolean(negated) || mode === 'not_regex',
            },
          }
        : undefined;
    case 'exactly':
    case 'exact':
    case 'atleast':
    case 'at_least': {
      const cleanVals = valArray.map((s) => s.trim()).filter(Boolean);
      if (cleanVals.length === 0) return undefined;
      return mode === 'exactly' || mode === 'exact'
        ? {matchesExactly: {values: cleanVals}}
        : {matchesAtLeast: {values: cleanVals}};
    }
    default:
      return undefined;
  }
}

/** Safe URI component decoder that gracefully falls back to raw string on malformed URI. */
export function safeDecodeURIComponent(val: string): string {
  try {
    return decodeURIComponent(val);
  } catch {
    return val;
  }
}

/** Parses raw condition string into structured Protobuf ComplexMatch AST. */
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
          .map((v) => safeDecodeURIComponent(v.trim()))
          .filter(Boolean)
      : [
          operator === 'regex' || operator === 'not_regex'
            ? rawValue
            : safeDecodeURIComponent(rawValue),
        ];

  return createComplexMatch(operator, values, negated);
}

/** Resolves the operator definition and numeric value from an integer-comparison ComplexMatch. */
function resolveIntComparison(
  complex?: ComplexMatch,
):
  | {opDef: (typeof INT_COMPARISON_OPERATORS)[number]; value: number}
  | undefined {
  const operator = complex?.intComparison?.operator;
  if (
    !operator ||
    operator === IntComparisonOperator.INT_COMPARISON_OPERATOR_UNSPECIFIED
  ) {
    return undefined;
  }
  const opDef = INT_COMPARISON_OPERATORS.find((op) => op.proto === operator);
  if (!opDef) return undefined;
  return {opDef, value: complex.intComparison?.value ?? 0};
}

/** Converts a Protobuf ComplexMatch payload into a strongly-typed ComplexMatchInfo View-Model. */
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
    const isNegated = Boolean(complex.containsSubstring.negated);
    return {
      mode: isNegated ? 'not_substring' : 'substring',
      values: [complex.containsSubstring.value],
      isNegated,
    };
  }
  if (complex.matchesRegex?.value) {
    const isNegated = Boolean(complex.matchesRegex.negated);
    return {
      mode: isNegated ? 'not_regex' : 'regex',
      values: [complex.matchesRegex.value],
      isNegated,
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
  const intCmp = resolveIntComparison(complex);
  return intCmp
    ? {
        mode: 'compare_int',
        values: [String(intCmp.value)],
        isNegated: false,
        intOp: intCmp.opDef.id,
      }
    : undefined;
}

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
        filterValues.push({noValue: {}});
        hasNoValue = true;
      }
    } else if (v) {
      filterValues.push({value: v});
    }
  }
  return {
    key,
    simple: {values: filterValues, negated: Boolean(negated)},
  };
}

/** Serializes a Protobuf ComplexMatch payload into official Scheme B DSL string. */
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
  const intCmp = resolveIntComparison(complex);
  return intCmp ? `${intCmp.opDef.id}~${intCmp.value}` : '';
}

/** Serializes a FilterChip object into official Scheme B URL format. */
export function serializeFilterChip(c: FilterChip): string {
  if (c.isGroupBy) return '';
  const prefix = isChipNegated(c) ? '!' : '';
  const key = getChipKey(c);

  if (c.complex) {
    const dsl = serializeComplexCondition(c.complex);
    if (dsl) return `${prefix}${key}~${dsl}`;
  }

  if (c.rawValues?.length) {
    const serializedVals = c.rawValues.map((v) => {
      const trimmed = v.trim();
      return !trimmed || trimmed === EMPTY_FILTER_VALUE
        ? EMPTY_FILTER_VALUE
        : encodeURIComponent(trimmed);
    });
    return serializedVals.every((v) => v === EMPTY_FILTER_VALUE)
      ? `${prefix}${key}~`
      : `${prefix}${key}~${serializedVals.join(',')}`;
  }

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

/** Parses a Scheme B URL filter param string directly into a standard FilterChip object. */
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

  if (parts.length >= 3) {
    const rawCondition = parts.slice(1).join('~');
    const complex = parseComplexCondition(rawCondition, negated);
    if (complex) {
      const operator = parts[1].trim().toLowerCase();
      const rawValue = parts.slice(2).join('~').trim();
      const info = extractComplexMatchInfo(complex);
      const extractedValues = info?.values || [];
      return {
        key,
        pillKey: key,
        pillCondition: `${operator}~${rawValue}`,
        rawValues: extractedValues.length > 0 ? extractedValues : undefined,
        negated: Boolean(info?.isNegated),
        complex,
        fleetFilter: {key, complex},
      };
    }
  }

  const remainingParts = parts.slice(1);
  const joinedVal = remainingParts.join('~').trim();
  if (!joinedVal) {
    return {
      key,
      pillKey: key,
      pillCondition: key,
      rawValues: [EMPTY_FILTER_VALUE],
      negated,
      fleetFilter: buildSimpleFleetFilter(key, [EMPTY_FILTER_VALUE], negated),
    };
  }

  const rawValues = (
    remainingParts.length > 1 ? remainingParts : remainingParts[0].split(',')
  )
    .map((v) => safeDecodeURIComponent(v.trim()))
    .map((v) => (v === '(no value)' ? EMPTY_FILTER_VALUE : v))
    .filter(Boolean);

  return {
    key,
    pillKey: key,
    pillCondition: rawValues.join(', ') || key,
    rawValues: rawValues.length > 0 ? rawValues : undefined,
    negated,
    fleetFilter: buildSimpleFleetFilter(key, rawValues, negated),
  };
}

/** Converts a FilterChip to a Fleet Filter Protobuf payload. */
export function buildFleetFilterFromChip(c: FilterChip): Filter {
  if (c.fleetFilter) return c.fleetFilter;
  const key = getChipKey(c);
  if (c.complex) return {key, complex: c.complex};
  const rawVals = c.rawValues?.length ? c.rawValues : [EMPTY_FILTER_VALUE];
  return buildSimpleFleetFilter(key, rawVals, isChipNegated(c));
}

/** Checks whether a FilterChip represents a negated/excluded condition. */
export function isChipNegated(
  chip: Partial<FilterChip> | undefined | null,
): boolean {
  if (!chip) return false;
  if (chip.negated !== undefined) return chip.negated;
  if (chip.complex?.containsSubstring?.negated !== undefined) {
    return chip.complex.containsSubstring.negated;
  }
  if (chip.complex?.matchesRegex?.negated !== undefined) {
    return chip.complex.matchesRegex.negated;
  }
  return (chip.pillCondition || '').trim().startsWith('!');
}

/** Normalizes search key by trimming and lowercasing. */
export function normalizeKey(key: string | null | undefined): string {
  return key ? key.toLowerCase().trim() : '';
}

/** Extracts the raw search key from a FilterChip. */
export function getChipKey(c: FilterChip): string {
  return c.key || c.pillKey || '';
}

/** Extracts a normalized lower-case search key for chip comparison and deduplication. */
export function getNormalizedChipKey(c: FilterChip): string {
  return normalizeKey(getChipKey(c));
}

/** Compares whether two filter chips refer to the same logical search key and dimension. */
export function isSameFilterChip(
  a: Partial<FilterChip> | null | undefined,
  b: Partial<FilterChip> | null | undefined,
): boolean {
  if (a === b) return true;
  if (!a || !b || Boolean(a.isGroupBy) !== Boolean(b.isGroupBy)) return false;
  const keyA = getNormalizedChipKey(a as FilterChip);
  const keyB = getNormalizedChipKey(b as FilterChip);
  return Boolean(keyA && keyB && keyA === keyB);
}

/** Tracking function for active filter and group-by chips in Angular templates. */
export function trackChip(chip: FilterChip): string {
  return `${getChipKey(chip)}:${chip.pillCondition}`;
}

/** Structural equality comparator for filter arrays in Angular computed signals. */
export function areFilterArraysEqual<T>(
  a: readonly T[],
  b: readonly T[],
): boolean {
  if (a === b) return true;
  return a.length === b.length && JSON.stringify(a) === JSON.stringify(b);
}

/** Constructs a specialized group-by FilterChip for search bar presentation. */
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

/** Deduplicates filter chips by key so later conditions overwrite earlier ones. */
export function deduplicateFilterChipsByKey(chips: FilterChip[]): FilterChip[] {
  const map = new Map<string, FilterChip>();
  for (const chip of chips) {
    const normKey = getNormalizedChipKey(chip);
    if (normKey) {
      map.set(`${chip.isGroupBy ? 'gb:' : 'f:'}${normKey}`, chip);
    }
  }
  return Array.from(map.values());
}

/** Inserts a new filter chip or replaces an existing chip with the same key and group-by flag. */
export function upsertFilterChipInList(
  current: readonly FilterChip[],
  chip: FilterChip,
): FilterChip[] {
  const newChip: FilterChip = {
    ...chip,
    pillCondition: chip.pillCondition || '',
    negated: isChipNegated(chip),
  };
  const idx = current.findIndex((c) => isSameFilterChip(c, chip));
  return idx !== -1
    ? current.map((c, i) => (i === idx ? newChip : c))
    : [...current, newChip];
}
