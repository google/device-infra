import {ComplexMatch} from '../../../core/models/search';
import {dateUtils} from '../../../shared/utils/date_utils';
import {
  AdvancedMatchMode,
  ApplyEventPayload,
  DateRangeState,
  DEFAULT_INT_COMPARISON_OP,
  FilterChip,
  FilterSortOptions,
  IntComparisonOp,
  PickerValueItem,
  ValuePickerApplyEvent,
} from '../models';
import {
  createComplexMatch,
  EMPTY_FILTER_VALUE,
  extractComplexMatchInfo,
  normalizeKey,
  parseStrictInt32,
} from './search_filter_utils';

/**
 * Pure function: Combines base candidate items with selected custom free-text inputs.
 *
 * @param baseValues List of candidate items returned by backend or configuration.
 * @param selectedValues Set of currently selected value strings.
 * @param isPlain Whether the candidate items are plain values without count columns.
 * @param isLoading Whether candidate values are currently loading.
 * @return Array of combined PickerValueItem elements.
 */
export function buildDisplayValues(
  baseValues: readonly PickerValueItem[],
  selectedValues?: ReadonlySet<string>,
  isPlain = false,
  isLoading = false,
): PickerValueItem[] {
  if (isLoading || !selectedValues?.size) {
    return [...baseValues];
  }

  const existingKeys = new Set(
    baseValues.flatMap((v) => [
      normalizeKey(v.value),
      normalizeKey(v.displayLabel),
    ]),
  );

  const missingItems: PickerValueItem[] = [];
  for (const raw of selectedValues) {
    const trimmed = raw.trim();
    const key = normalizeKey(trimmed);
    if (!trimmed || existingKeys.has(key)) continue;

    existingKeys.add(key);
    missingItems.push({
      value: trimmed,
      displayLabel: trimmed,
      filtered: isPlain ? undefined : 0,
      total: isPlain ? undefined : 0,
    });
  }

  return [...baseValues, ...missingItems];
}

const ZERO_CODE = 48;
const NINE_CODE = 57;
const UPPER_A_CODE = 65;
const UPPER_Z_CODE = 90;
const LOWER_A_CODE = 97;
const LOWER_Z_CODE = 122;

function isAsciiDigitCode(code: number): boolean {
  return code >= ZERO_CODE && code <= NINE_CODE;
}

function isAsciiAlphaCode(code: number): boolean {
  return (
    (code >= UPPER_A_CODE && code <= UPPER_Z_CODE) ||
    (code >= LOWER_A_CODE && code <= LOWER_Z_CODE)
  );
}

function toAsciiLowerCode(code: number): number {
  return code >= UPPER_A_CODE && code <= UPPER_Z_CODE ? code + 32 : code;
}

function startsNumberToken(s: string, pos: number): boolean {
  const code = s.charCodeAt(pos);
  if (isAsciiDigitCode(code)) {
    return true;
  }
  return (
    pos === 0 &&
    (code === 45 || code === 43) &&
    s.length > 1 &&
    isAsciiDigitCode(s.charCodeAt(1))
  );
}

function numberTokenEnd(s: string, pos: number, decimalMode: boolean): number {
  const len = s.length;
  const signed =
    pos === 0 && (s.charCodeAt(0) === 45 || s.charCodeAt(0) === 43);
  let i = signed ? 1 : pos;
  while (i < len && isAsciiDigitCode(s.charCodeAt(i))) {
    i++;
  }
  if (pos === 0 && i < len && s.charCodeAt(i) === 46) {
    let fracEnd = i + 1;
    while (fracEnd < len && isAsciiDigitCode(s.charCodeAt(fracEnd))) {
      fracEnd++;
    }
    if (fracEnd > i + 1 && (fracEnd === len || s.charCodeAt(fracEnd) !== 46)) {
      const hyphenDelimited = fracEnd < len && s.charCodeAt(fracEnd) === 45;
      if (signed || (decimalMode && !hyphenDelimited)) {
        return fracEnd;
      }
    }
  }
  return i;
}

function isVersionKeyId(keyId: string): boolean {
  const sep = keyId.lastIndexOf('::');
  const bare = (sep >= 0 ? keyId.slice(sep + 2) : keyId).toLowerCase();
  if (bare === 'sdk_version' || bare === 'api_level') {
    return false;
  }
  return (
    bare === 'version' ||
    bare.endsWith('_version') ||
    bare === 'ver' ||
    bare.endsWith('_ver')
  );
}

function startsWithMultiSegmentOrHyphenVersion(s: string): boolean {
  const len = s.length;
  let i = 0;
  while (i < len && isAsciiDigitCode(s.charCodeAt(i))) {
    i++;
  }
  if (i === 0 || i + 1 >= len || s.charCodeAt(i) !== 46) {
    return false;
  }
  let fracEnd = i + 1;
  while (fracEnd < len && isAsciiDigitCode(s.charCodeAt(fracEnd))) {
    fracEnd++;
  }
  if (fracEnd === i + 1 || fracEnd + 1 >= len) {
    return false;
  }
  const next = s.charCodeAt(fracEnd);
  const afterNext = s.charCodeAt(fracEnd + 1);
  if (next === 46) {
    return isAsciiDigitCode(afterNext);
  }
  if (next === 45) {
    return isAsciiDigitCode(afterNext) || isAsciiAlphaCode(afterNext);
  }
  return false;
}

function skipZeroCodes(s: string, start: number, end: number): number {
  let i = start;
  while (i < end && s.charCodeAt(i) === ZERO_CODE) {
    i++;
  }
  return i;
}

function trimTrailingZeroCodes(s: string, start: number, end: number): number {
  let i = end;
  while (i > start && s.charCodeAt(i - 1) === ZERO_CODE) {
    i--;
  }
  return i;
}

function dotIndexInSlice(s: string, start: number, end: number): number {
  for (let i = start; i < end; i++) {
    if (s.charCodeAt(i) === 46) {
      return i;
    }
  }
  return end;
}

function compareDigitSlicesAsInt(
  a: string,
  startA: number,
  endA: number,
  b: string,
  startB: number,
  endB: number,
): number {
  const lenA = endA - startA;
  const lenB = endB - startB;
  if (lenA !== lenB) {
    return lenA - lenB;
  }
  for (let i = 0; i < lenA; i++) {
    const diff = a.charCodeAt(startA + i) - b.charCodeAt(startB + i);
    if (diff !== 0) {
      return diff;
    }
  }
  return 0;
}

function compareDigitSlicesLex(
  a: string,
  startA: number,
  endA: number,
  b: string,
  startB: number,
  endB: number,
): number {
  const lenA = endA - startA;
  const lenB = endB - startB;
  const minLen = Math.min(lenA, lenB);
  for (let i = 0; i < minLen; i++) {
    const diff = a.charCodeAt(startA + i) - b.charCodeAt(startB + i);
    if (diff !== 0) {
      return diff;
    }
  }
  return lenA - lenB;
}

function numberSubGroup(s: string, pos: number, end: number): number {
  const negative = pos === 0 && s.charCodeAt(0) === 45;
  const digitStart =
    pos === 0 && (s.charCodeAt(0) === 45 || s.charCodeAt(0) === 43) ? 1 : pos;
  const dot = pos === 0 ? dotIndexInSlice(s, digitStart, end) : end;
  const sigIntStart = skipZeroCodes(s, digitStart, dot);
  const fracEnd = dot < end ? trimTrailingZeroCodes(s, dot + 1, end) : end;
  if (sigIntStart === dot && (dot === end || fracEnd === dot + 1)) {
    return 0;
  }
  return negative ? -1 : 1;
}

function compareDecimalMagnitudes(
  a: string,
  posA: number,
  endA: number,
  b: string,
  posB: number,
  endB: number,
): number {
  const startA =
    posA === 0 && (a.charCodeAt(0) === 45 || a.charCodeAt(0) === 43) ? 1 : posA;
  const startB =
    posB === 0 && (b.charCodeAt(0) === 45 || b.charCodeAt(0) === 43) ? 1 : posB;
  const dotA = posA === 0 ? dotIndexInSlice(a, startA, endA) : endA;
  const dotB = posB === 0 ? dotIndexInSlice(b, startB, endB) : endB;
  const intCmp = compareDigitSlicesAsInt(
    a,
    skipZeroCodes(a, startA, dotA),
    dotA,
    b,
    skipZeroCodes(b, startB, dotB),
    dotB,
  );
  if (intCmp !== 0) {
    return intCmp;
  }
  const fracStartA = dotA < endA ? dotA + 1 : endA;
  const fracEndA =
    dotA < endA ? trimTrailingZeroCodes(a, fracStartA, endA) : endA;
  const fracStartB = dotB < endB ? dotB + 1 : endB;
  const fracEndB =
    dotB < endB ? trimTrailingZeroCodes(b, fracStartB, endB) : endB;
  return compareDigitSlicesLex(
    a,
    fracStartA,
    fracEndA,
    b,
    fracStartB,
    fracEndB,
  );
}

function compareNumberTokens(
  a: string,
  posA: number,
  endA: number,
  b: string,
  posB: number,
  endB: number,
): number {
  const subA = numberSubGroup(a, posA, endA);
  const subB = numberSubGroup(b, posB, endB);
  if (subA !== subB) {
    return subA - subB;
  }
  if (subA === 0) {
    return 0;
  }
  const cmp = compareDecimalMagnitudes(a, posA, endA, b, posB, endB);
  return subA === -1 ? -cmp : cmp;
}

/**
 * Pure function: Case-insensitive lexicographical comparator for plain identifier picker values,
 * matching backend FleetSortComparator.compareLexicographicalNonEmpty semantics.
 */
export function compareLexicographicalValues(a: string, b: string): number {
  if (a === b) return 0;
  if ((a.length === 0) !== (b.length === 0)) {
    return a.length === 0 ? 1 : -1;
  }
  const minLen = Math.min(a.length, b.length);
  for (let i = 0; i < minLen; i++) {
    const ca = toAsciiLowerCode(a.charCodeAt(i));
    const cb = toAsciiLowerCode(b.charCodeAt(i));
    if (ca !== cb) {
      return ca - cb;
    }
  }
  if (a.length !== b.length) {
    return a.length - b.length;
  }
  return a < b ? -1 : 1;
}

function compareTokenizedValues(
  a: string,
  b: string,
  decimalMode: boolean,
): number {
  if (a === b) return 0;
  if ((a.length === 0) !== (b.length === 0)) {
    return a.length === 0 ? 1 : -1;
  }
  const lenA = a.length;
  const lenB = b.length;
  let posA = 0;
  let posB = 0;
  while (posA < lenA && posB < lenB) {
    const numA = startsNumberToken(a, posA);
    const numB = startsNumberToken(b, posB);
    if (!numA && !numB) {
      const ca = toAsciiLowerCode(a.charCodeAt(posA));
      const cb = toAsciiLowerCode(b.charCodeAt(posB));
      if (ca !== cb) {
        return ca - cb;
      }
      posA++;
      posB++;
      continue;
    }
    if (numA !== numB) {
      if (numA) {
        return toAsciiLowerCode(b.charCodeAt(posB)) < ZERO_CODE ? 1 : -1;
      }
      return toAsciiLowerCode(a.charCodeAt(posA)) < ZERO_CODE ? -1 : 1;
    }
    const endA = numberTokenEnd(a, posA, decimalMode);
    const endB = numberTokenEnd(b, posB, decimalMode);
    const cmp = compareNumberTokens(a, posA, endA, b, posB, endB);
    if (cmp !== 0) {
      return cmp;
    }
    posA = endA;
    posB = endB;
  }
  if (posA < lenA || posB < lenB) {
    return posA < lenA ? 1 : -1;
  }
  return a < b ? -1 : 1;
}

/**
 * Pure function: Case-insensitive numeric and natural-order comparator for picker values,
 * matching backend FleetSortComparator.DECIMAL semantics.
 */
export function compareFilterValues(a: string, b: string): number {
  return compareTokenizedValues(a, b, true);
}

/**
 * Pure function: Case-insensitive version-segment and natural-order comparator for version picker
 * values, matching backend FleetSortComparator.VERSION semantics.
 */
export function compareVersionFilterValues(a: string, b: string): number {
  return compareTokenizedValues(a, b, false);
}

function selectPickerValueComparator(
  items: readonly PickerValueItem[],
  isPlain?: boolean,
  key?: string,
): (a: string, b: string) => number {
  if (isPlain) {
    return compareLexicographicalValues;
  }
  if (
    (key && isVersionKeyId(key)) ||
    items.some(
      (item) =>
        !item.isNoValue &&
        startsWithMultiSegmentOrHyphenVersion(item.displayLabel),
    )
  ) {
    return compareVersionFilterValues;
  }
  return compareFilterValues;
}

/**
 * Pure function: Filters and multi-column sorts candidate items for display in the picker UI.
 *
 * @param opts Options specifying items, search query, sort column, and sort direction.
 * @return Filtered and sorted candidate items.
 */
export function computeFilteredAndSortedValues(
  opts: FilterSortOptions,
): PickerValueItem[] {
  const displayItems = buildDisplayValues(
    opts.items,
    opts.selectedValues,
    opts.isPlain,
    opts.isLoading,
  );
  const q = normalizeKey(opts.query);

  const list = !q
    ? displayItems
    : displayItems.filter(
        (v) =>
          normalizeKey(v.value).includes(q) ||
          normalizeKey(v.displayLabel).includes(q),
      );

  const normalItems = list.filter((v) => !v.disabled && !v.isNoValue);
  const noValueItems = list.filter((v) => !v.disabled && v.isNoValue);
  const disabledItems = list.filter((v) => v.disabled);
  const compareLabels = selectPickerValueComparator(
    displayItems,
    opts.isPlain,
    opts.key,
  );

  normalItems.sort((a, b) => {
    if (opts.sortBy === 'value') {
      const res = compareLabels(a.displayLabel, b.displayLabel);
      return opts.sortAsc ? res : -res;
    }

    const valA = (opts.sortBy === 'filtered' ? a.filtered : a.total) ?? 0;
    const valB = (opts.sortBy === 'filtered' ? b.filtered : b.total) ?? 0;
    if (valA !== valB) {
      return opts.sortAsc ? valA - valB : valB - valA;
    }
    return compareLabels(a.displayLabel, b.displayLabel);
  });

  return [...normalItems, ...noValueItems, ...disabledItems];
}

/**
 * Pure function: Computes pinned selected items when total candidate count exceeds threshold.
 *
 * @param items List of candidate items.
 * @param selectedSet Set of currently selected value strings.
 * @param query Active search query.
 * @param isLoading Whether values are currently loading.
 * @param threshold Maximum total item threshold before pinning triggers (default 20).
 * @param isPlain Whether the candidate items belong to a plain identifier list.
 * @param key Optional filter key identifier used to select version vs decimal comparison.
 * @return Array of pinned items matching current selections.
 */
export function computePinnedValues(
  items: readonly PickerValueItem[],
  selectedSet: ReadonlySet<string>,
  query: string,
  isLoading: boolean,
  threshold = 20,
  isPlain = false,
  key?: string,
): PickerValueItem[] {
  if (
    query.trim().length > 0 ||
    isLoading ||
    items.length <= threshold ||
    selectedSet.size === 0
  ) {
    return [];
  }

  const selectedNorm = new Set(Array.from(selectedSet, normalizeKey));
  const compareLabels = selectPickerValueComparator(items, isPlain, key);

  return items
    .filter(
      (v) =>
        selectedSet.has(v.value) || selectedNorm.has(normalizeKey(v.value)),
    )
    .sort((a, b) => {
      if (Boolean(a.disabled) !== Boolean(b.disabled)) {
        return a.disabled ? 1 : -1;
      }
      if (Boolean(a.isNoValue) !== Boolean(b.isNoValue)) {
        return a.isNoValue ? 1 : -1;
      }
      return compareLabels(a.displayLabel, b.displayLabel);
    });
}

/**
 * Returns the active Pacific timezone abbreviation ('PDT' or 'PST') for a given timestamp.
 * Delegates to shared dateUtils.
 */
export const getPacificTimezoneName = dateUtils.getPacificTimezoneName;

/**
 * Converts a datetime-local string in Pacific Time (America/Los_Angeles) to UTC ISO-8601 string.
 */
export const pacificToUtc = dateUtils.pacificToUtc;

/**
 * Converts a UTC timestamp to Pacific Time HTML datetime-local format string `YYYY-MM-DDTHH:MM`.
 */
export const utcToPacific = dateUtils.utcToPacific;

/** One day duration in milliseconds for default range computation. */
const ONE_DAY_MS = 24 * 60 * 60 * 1000;

/** Regular expression matching lower-bound range markers ('From:', '>=', '≥'). */
const FROM_BOUND_REGEX = /(?:From:|≥|>=)\s*(.*?)(?=\s*(?:To:|≤|<=|&)|$)/i;

/** Regular expression matching upper-bound range markers ('To:', '<=', '≤'). */
const TO_BOUND_REGEX = /(?:To:|≤|<=)\s*(.*)/i;

/** Regular expression splitting range strings by standard separators ('~', ',', ' - '). */
const RANGE_DELIMITER_REGEX = /[~,]|(?:\s+-\s+)/;

/** Regular expression stripping optional trailing timezone labels, e.g. '(PDT)', '(PST)'. */
const TIMEZONE_SUFFIX_REGEX = /\s*\([A-Za-z]+\)$/;

/**
 * Cleans and converts a raw bound string to a Pacific Time datetime-local representation.
 */
function cleanRawDateBound(raw?: string): string {
  if (!raw) return '';
  const cleaned = raw.replace(TIMEZONE_SUFFIX_REGEX, '').trim();
  if (!cleaned || cleaned === EMPTY_FILTER_VALUE) {
    return '';
  }
  return utcToPacific(cleaned);
}

/**
 * Extracts from and to raw bounds from a single formatted range string.
 */
function extractBoundsFromSingleString(str: string): [string, string] {
  const fromMatch = str.match(FROM_BOUND_REGEX);
  const toMatch = str.match(TO_BOUND_REGEX);
  if (fromMatch || toMatch) {
    return [fromMatch?.[1]?.trim() || '', toMatch?.[1]?.trim() || ''];
  }
  const parts = str.split(RANGE_DELIMITER_REGEX).map((s) => s.trim());
  return [parts[0] || '', parts[1] || ''];
}

/**
 * Constructs default 24-hour range spanning the previous 24 hours to now in Pacific Time.
 */
export function getDefault24hRange(nowMs = Date.now()): DateRangeState {
  return {
    from: utcToPacific(nowMs - ONE_DAY_MS),
    to: utcToPacific(nowMs),
  };
}

/**
 * Pure function: Extracts From and To date strings from selected values or returns default 24h range in Pacific Time.
 * Follows Angular 20+ best practices: pure, strongly typed, defensive against null/undefined/arrays/sets.
 *
 * @param selectedValues Iterable of selected value strings (e.g. Set or Array).
 * @return DateRangeState containing 'from' and 'to' datetime-local strings in Pacific Time.
 */
export function parseDateRange(
  selectedValues?: Iterable<string> | null,
): DateRangeState {
  if (!selectedValues) {
    return getDefault24hRange();
  }

  const selected = Array.from(selectedValues, (s) => s.trim());
  let rawFrom = '';
  let rawTo = '';

  if (selected.length >= 2) {
    rawFrom = selected[0] || '';
    rawTo = selected[1] || '';
  } else if (selected.length === 1 && selected[0]) {
    [rawFrom, rawTo] = extractBoundsFromSingleString(selected[0]);
  }

  const from = cleanRawDateBound(rawFrom);
  const to = cleanRawDateBound(rawTo);

  if (from || to) {
    return {from, to};
  }

  return getDefault24hRange();
}

/**
 * Pure function: Constructs canonical ValuePickerApplyEvent payload for store application.
 *
 * @param payload Event payload state parameters.
 * @return ValuePickerApplyEvent object ready to dispatch.
 */
export function buildValuePickerApplyEvent(
  payload: ApplyEventPayload,
): ValuePickerApplyEvent {
  const {type, isAdvanced, negated, selectedSet} = payload;

  switch (type) {
    case 'range': {
      const fromVal = (payload.rangeFrom || '').trim();
      const toVal = (payload.rangeTo || '').trim();
      return {
        selected: [`From: ${fromVal} To: ${toVal}`],
        negate: false,
        isAdvanced: false,
        rangeFrom: fromVal,
        rangeTo: toVal,
      };
    }
    case 'namedPair': {
      const name = (payload.propName || '').trim();
      const val = (payload.propVal || '').trim();
      return {
        selected: [val],
        negate: false,
        isAdvanced: false,
        propName: name,
        textVal: val,
      };
    }
    case 'text': {
      const val = (payload.textVal || '').trim();
      return {
        selected: [val],
        negate: false,
        isAdvanced: false,
        textVal: val,
      };
    }
    default:
      break;
  }

  const selectedList = Array.from(selectedSet);
  const isMulti = isMultiModeAdvanced(payload.advMode);

  return {
    selected: selectedList,
    negate: negated,
    isAdvanced,
    advMode: isAdvanced ? payload.advMode : undefined,
    advText:
      isAdvanced && !isMulti ? (payload.advText || '').trim() : undefined,
    advValues:
      isAdvanced && isMulti && payload.advValues
        ? [...payload.advValues]
        : undefined,
    intOp:
      isAdvanced && payload.advMode === 'compare_int'
        ? payload.intOp || DEFAULT_INT_COMPARISON_OP
        : undefined,
  };
}

/** Helper to check whether an advanced match mode is multi-value (exactly, at_least). */
export function isMultiModeAdvanced(mode?: AdvancedMatchMode): boolean {
  return mode === 'exactly' || mode === 'at_least';
}

/** Helper to extract advanced matching state from a FilterChip for ValuePicker initialization. */
export function extractAdvancedStateFromChip(chip: FilterChip): {
  isAdv: boolean;
  advMode: AdvancedMatchMode;
  advText: string;
  advValues: string[];
  intOp: IntComparisonOp;
} {
  const info = extractComplexMatchInfo(chip.complex);
  if (info) {
    const isMulti = isMultiModeAdvanced(info.mode);
    return {
      isAdv: true,
      advMode: info.mode,
      advText: isMulti ? '' : info.values.join(', '),
      advValues: isMulti ? info.values : [],
      intOp: info.intOp || DEFAULT_INT_COMPARISON_OP,
    };
  }
  return {
    isAdv: false,
    advMode: 'substring',
    advText: '',
    advValues: [],
    intOp: DEFAULT_INT_COMPARISON_OP,
  };
}

/** Builds ComplexMatch Protobuf structure from Popover ValuePickerApplyEvent. */
export function buildComplexMatchFromEvent(
  event: ValuePickerApplyEvent,
): ComplexMatch | undefined {
  if (!event.isAdvanced || !event.advMode) return undefined;

  const isMulti = isMultiModeAdvanced(event.advMode);
  const vals = isMulti
    ? event.advValues?.length
      ? event.advValues
      : []
    : event.advText?.trim()
      ? [event.advText.trim()]
      : [];

  const isNeg =
    event.advMode === 'not_substring' || event.advMode === 'not_regex';
  return createComplexMatch(
    event.advMode,
    vals,
    isNeg,
    event.intOp || DEFAULT_INT_COMPARISON_OP,
  );
}

/** Helper to check if a ValuePickerApplyEvent payload contains empty selection values. */
export function isValuePickerSelectionEmpty(
  event: ValuePickerApplyEvent,
): boolean {
  if (event.isAdvanced) {
    if (isMultiModeAdvanced(event.advMode)) {
      return !event.advValues?.length;
    }
    if (event.advMode === 'compare_int') {
      return parseStrictInt32(event.advText) === undefined;
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
