import {ComplexMatch} from '../../../core/models/search';
import {dateUtils} from '../../../shared/utils/date_utils';
import {
  AdvancedMatchMode,
  FilterChip,
  PickerValueItem,
  ValuePickerApplyEvent,
} from '../models';
import {
  createComplexMatch,
  extractComplexMatchInfo,
  normalizeKey,
} from './search_filter_utils';

/** Configuration options passed to computeFilteredAndSortedValues pure engine function. */
export interface FilterSortOptions {
  /** Candidate value items available for selection. */
  readonly items: readonly PickerValueItem[];
  /** User-entered text query for filtering candidate items. */
  readonly query: string;
  /** Primary column to sort items by ('value' | 'filtered' | 'total'). */
  readonly sortBy: 'value' | 'filtered' | 'total';
  /** Sort direction flag (true = ascending, false = descending). */
  readonly sortAsc: boolean;
  /** Staged custom user inputs added manually that are not in base items. */
  readonly stagedCustomInputs?: ReadonlySet<string>;
  /** Loading flag indicating whether backend values are currently fetching. */
  readonly isLoading?: boolean;
}

/**
 * Pure function: Combines base candidate items with staged custom free-text inputs.
 *
 * @param baseValues List of candidate items returned by backend or configuration.
 * @param staged Set of custom inputs entered by user.
 * @param isLoading Whether candidate values are currently loading.
 * @return Array of combined PickerValueItem elements.
 */
export function buildDisplayValues(
  baseValues: readonly PickerValueItem[],
  staged: ReadonlySet<string> | undefined,
  isLoading = false,
): PickerValueItem[] {
  if (isLoading || !staged || staged.size === 0) {
    return [...baseValues];
  }

  const baseLower = new Set<string>();
  for (const v of baseValues) {
    baseLower.add(normalizeKey(v.value));
    baseLower.add(normalizeKey(v.displayLabel));
  }

  const missingStaged: PickerValueItem[] = [];
  for (const customVal of staged) {
    if (!baseLower.has(normalizeKey(customVal))) {
      missingStaged.push({
        value: customVal,
        displayLabel: customVal,
        disabled: true,
      });
    }
  }

  return [...baseValues, ...missingStaged];
}

/**
 * Pure function: Filters and multi-column sorts candidate items for display in the picker UI.
 *
 * @param opts Options specifying items, search query, sort column, and sort direction.
 * @returns Filtered and sorted candidate items.
 */
export function computeFilteredAndSortedValues(
  opts: FilterSortOptions,
): PickerValueItem[] {
  const displayItems = buildDisplayValues(
    opts.items,
    opts.stagedCustomInputs,
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

  const normalItems = list.filter((v) => !v.disabled);
  const disabledItems = list.filter((v) => v.disabled);

  normalItems.sort((a, b) => {
    if (opts.sortBy === 'value') {
      const res = a.displayLabel
        .toLowerCase()
        .localeCompare(b.displayLabel.toLowerCase());
      return opts.sortAsc ? res : -res;
    }

    const valA = (opts.sortBy === 'filtered' ? a.filtered : a.total) ?? 0;
    const valB = (opts.sortBy === 'filtered' ? b.filtered : b.total) ?? 0;
    return opts.sortAsc ? valA - valB : valB - valA;
  });

  return [...normalItems, ...disabledItems];
}

/**
 * Pure function: Computes pinned selected items when total candidate count exceeds threshold.
 *
 * @param items List of candidate items.
 * @param selectedSet Set of currently selected value strings.
 * @param query Active search query.
 * @param isLoading Whether values are currently loading.
 * @param threshold Maximum total item threshold before pinning triggers (default 20).
 * @return Array of pinned items matching current selections.
 */
export function computePinnedValues(
  items: readonly PickerValueItem[],
  selectedSet: ReadonlySet<string>,
  query: string,
  isLoading: boolean,
  threshold = 20,
): PickerValueItem[] {
  const hasQuery = query.trim().length > 0;
  if (
    hasQuery ||
    isLoading ||
    items.length <= threshold ||
    selectedSet.size === 0
  ) {
    return [];
  }

  return items
    .filter((v) => selectedSet.has(v.value))
    .sort((a, b) => a.displayLabel.localeCompare(b.displayLabel));
}

/**
 * Returns the active Pacific timezone abbreviation ('PDT' or 'PST') for a given timestamp.
 * Delegates to shared dateUtils.
 */
export const getPacificTimezoneName = dateUtils.getPacificTimezoneName;

/**
 * Pure function: Formats millisecond timestamp into datetime-local HTML input format YYYY-MM-DDTHH:MM.
 *
 * @param ms Epoch timestamp in milliseconds.
 * @return Formatted datetime-local string.
 */
export const toDateTimeLocalString = dateUtils.toDateTimeLocalString;

/**
 * Converts a datetime-local string in Pacific Time (America/Los_Angeles) to UTC ISO-8601 string.
 * Delegates to shared dateUtils.
 */
export const pdtDateTimeToUtcIso = dateUtils.pdtDateTimeToUtcIso;

/**
 * Pure function: Extracts From and To date strings from selected values set or returns default 24h range in Pacific Time.
 *
 * @param selectedValues Set of selected value strings.
 * @return Object containing 'from' and 'to' datetime-local strings.
 */
export function parseDateRange(selectedValues: ReadonlySet<string>): {
  from: string;
  to: string;
} {
  const selected = Array.from(selectedValues);
  if (selected.length >= 2) {
    return {
      from: (selected[0] || '').trim(),
      to: (selected[1] || '').trim(),
    };
  }

  const t = selected[0] || '';
  const match = t.match(/From:\s*([^\s]+)\s*To:\s*([^\s]+)/i);
  if (match) {
    return {from: match[1], to: match[2]};
  }

  const matchTilde = t.match(/([^\s~]+)\s*~\s*([^\s~]+)/);
  if (matchTilde) {
    return {from: matchTilde[1], to: matchTilde[2]};
  }

  const now = Date.now();
  return {
    from: toDateTimeLocalString(now - 86400000),
    to: toDateTimeLocalString(now),
  };
}

/** Input payload interface for buildValuePickerApplyEvent. */
export interface ApplyEventPayload {
  /** Specific picker mode type ('range', 'namedPair', 'text', etc.). */
  readonly type?: string;
  /** Whether advanced regex/match mode is active. */
  readonly isAdvanced: boolean;
  /** Whether negate/polarity toggle is active. */
  readonly negated: boolean;
  /** Set of selected value strings. */
  readonly selectedSet: ReadonlySet<string>;
  /** Pending search query in input box. */
  readonly searchQuery: string;
  /** Advanced match mode string ('prefix', 'suffix', 'contains', 'regex', etc.). */
  readonly advMode?: AdvancedMatchMode;
  /** Advanced mode text query. */
  readonly advText?: string;
  /** Advanced mode values array. */
  readonly advValues?: readonly string[];
  /** From date string for range mode. */
  readonly rangeFrom?: string;
  /** To date string for range mode. */
  readonly rangeTo?: string;
  /** Property name for namedPair mode. */
  readonly propName?: string;
  /** Property value for namedPair mode. */
  readonly propVal?: string;
  /** Text value for plain text mode. */
  readonly textVal?: string;
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
  const {type, isAdvanced, negated, selectedSet, searchQuery} = payload;

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
  const pendingQuery = searchQuery.trim();
  if (pendingQuery && !selectedList.includes(pendingQuery) && !isAdvanced) {
    selectedList.push(pendingQuery);
  }

  return {
    selected: selectedList,
    negate: negated,
    isAdvanced,
    advMode: isAdvanced ? payload.advMode : undefined,
    advText: isAdvanced ? (payload.advText || '').trim() : undefined,
    advValues:
      isAdvanced && payload.advValues ? [...payload.advValues] : undefined,
  };
}

/** Helper to extract advanced matching state from a FilterChip for ValuePicker initialization. */
export function extractAdvancedStateFromChip(chip: FilterChip): {
  isAdv: boolean;
  advMode: AdvancedMatchMode;
  advText: string;
  advValues: string[];
} {
  const info = extractComplexMatchInfo(chip.complex);
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

  const vals = event.advValues?.length
    ? event.advValues
    : event.advText?.trim()
      ? [event.advText.trim()]
      : [];

  const isNeg =
    event.advMode === 'not_substring' || event.advMode === 'not_regex';
  return createComplexMatch(event.advMode, vals, isNeg);
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
