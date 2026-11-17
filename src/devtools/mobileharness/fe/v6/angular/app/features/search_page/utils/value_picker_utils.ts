/**
 * @fileoverview Pure functions for FilterValuePicker candidate filtering, sorting, pinning, Pacific date-range parsing, and apply event construction.
 */

import {ComplexMatch} from '../../../core/models/search';
import {dateUtils} from '../../../shared/utils/date_utils';
import {
  AdvancedMatchMode,
  ApplyEventPayload,
  DateRangeState,
  DEFAULT_INT_COMPARISON_OP,
  EMPTY_FILTER_VALUE,
  FilterChip,
  FilterSortOptions,
  IntComparisonOp,
  PickerFooterStatusOptions,
  PickerValueItem,
  ValuePickerApplyEvent,
} from '../models';
import {
  createComplexMatch,
  extractComplexMatchInfo,
  normalizeKey,
  parseStrictInt32,
} from './search_filter_utils';

/** Combines base candidate items with selected custom free-text inputs. */
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

/** Filters and multi-column sorts candidate items for display in the picker UI. */
export function computeFilteredAndSortedValues(
  opts: FilterSortOptions,
): PickerValueItem[] {
  const q = normalizeKey(opts.query);

  const list = !q
    ? [...opts.items]
    : opts.items.filter(
        (v) =>
          normalizeKey(v.value).includes(q) ||
          normalizeKey(v.displayLabel).includes(q),
      );

  return list.sort((a, b) => {
    if (opts.sortBy === 'value') {
      const res = a.displayLabel
        .toLowerCase()
        .localeCompare(b.displayLabel.toLowerCase());
      return opts.sortAsc ? res : -res;
    }

    const valA = (opts.sortBy === 'filtered' ? a.filtered : a.total) ?? 0;
    const valB = (opts.sortBy === 'filtered' ? b.filtered : b.total) ?? 0;
    if (valA !== valB) {
      return opts.sortAsc ? valA - valB : valB - valA;
    }
    return a.displayLabel
      .toLowerCase()
      .localeCompare(b.displayLabel.toLowerCase());
  });
}

/** Computes pinned selected items when total candidate count exceeds threshold. */
export function computePinnedValues(
  items: readonly PickerValueItem[],
  selectedSet: ReadonlySet<string>,
  query: string,
  isLoading: boolean,
  threshold = 20,
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

  return items
    .filter(
      (v) =>
        selectedSet.has(v.value) || selectedNorm.has(normalizeKey(v.value)),
    )
    .sort((a, b) =>
      a.displayLabel.toLowerCase().localeCompare(b.displayLabel.toLowerCase()),
    );
}

/** Returns the active Pacific timezone abbreviation ('PDT' or 'PST') for a given timestamp. */
export const getPacificTimezoneName = dateUtils.getPacificTimezoneName;

/** Converts a Pacific Time date/time string into an ISO-8601 UTC timestamp string. */
export const pacificToUtc = dateUtils.pacificToUtc;

const ONE_DAY_MS = 24 * 60 * 60 * 1000;
const FROM_BOUND_REGEX = /(?:From:|≥|>=)\s*(.*?)(?=\s*(?:To:|≤|<=|&)|$)/i;
const TO_BOUND_REGEX = /(?:To:|≤|<=)\s*(.*)/i;
const RANGE_DELIMITER_REGEX = /[~,]|(?:\s+-\s+)/;
const TIMEZONE_SUFFIX_REGEX = /\s*\([A-Za-z]+\)$/;

/** Strips timezone suffixes and normalizes a raw date bound string into Pacific datetime-local format. */
function cleanRawDateBound(raw?: string): string {
  if (!raw) return '';
  const cleaned = raw.replace(TIMEZONE_SUFFIX_REGEX, '').trim();
  if (!cleaned || cleaned === EMPTY_FILTER_VALUE) {
    return '';
  }
  return dateUtils.utcToPacific(cleaned);
}

/** Extracts `[from, to]` bound strings from a single combined range string. */
function extractBoundsFromSingleString(str: string): [string, string] {
  const fromMatch = str.match(FROM_BOUND_REGEX);
  const toMatch = str.match(TO_BOUND_REGEX);
  if (fromMatch || toMatch) {
    return [fromMatch?.[1]?.trim() || '', toMatch?.[1]?.trim() || ''];
  }
  const parts = str.split(RANGE_DELIMITER_REGEX).map((s) => s.trim());
  return [parts[0] || '', parts[1] || ''];
}

/** Constructs default 24-hour range spanning the previous 24 hours to now in Pacific Time. */
export function getDefault24hRange(nowMs = Date.now()): DateRangeState {
  return {
    from: dateUtils.utcToPacific(nowMs - ONE_DAY_MS),
    to: dateUtils.utcToPacific(nowMs),
  };
}

/** Extracts From and To date strings from selected values or returns default 24h range in Pacific Time. */
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

/** Constructs canonical ValuePickerApplyEvent payload for store application. */
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

/** Checks whether an advanced match mode is multi-value (exactly, at_least). */
export function isMultiModeAdvanced(mode?: AdvancedMatchMode): boolean {
  return mode === 'exactly' || mode === 'at_least';
}

/** Extracts advanced matching state from a FilterChip for ValuePicker initialization. */
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

/** Checks if a ValuePickerApplyEvent payload contains empty selection values. */
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

/** Computes footer status summary text for the ValuePicker popover. */
export function computePickerFooterStatusText(
  opts: PickerFooterStatusOptions,
): string {
  if (opts.isAdvanced) {
    if (opts.advMode === 'compare_int') {
      const raw = opts.advText.trim();
      if (!raw) return 'No values added';
      return parseStrictInt32(raw) !== undefined
        ? '1 value added'
        : 'Invalid integer';
    }
    if (!isMultiModeAdvanced(opts.advMode)) {
      return opts.advText.trim() ? '1 value added' : 'No values added';
    }
    const count = opts.advValues.length;
    return count === 0
      ? 'No values added'
      : `${count} value${count > 1 ? 's' : ''} added`;
  }
  if (opts.type !== 'list' && opts.type !== undefined) {
    return '';
  }
  const verb = opts.isNegated ? 'excluded' : 'selected';
  return opts.selectedCount === 0
    ? `No options ${verb}`
    : `${opts.selectedCount} option${opts.selectedCount > 1 ? 's' : ''} ${verb}`;
}

/** Checks whether the "Add custom input" row should be displayed while picker values are loading. */
export function shouldShowAddCustomRow(
  loading: boolean | undefined,
  rawQuery: string,
  selectedSet: ReadonlySet<string>,
  values: readonly PickerValueItem[] | undefined,
): boolean {
  const q = rawQuery.trim().toLowerCase();
  if (!loading || !q) return false;
  if (Array.from(selectedSet).some((s) => s.toLowerCase() === q)) {
    return false;
  }
  return !(values || []).some(
    (v) => v.value.toLowerCase() === q || v.displayLabel.toLowerCase() === q,
  );
}
