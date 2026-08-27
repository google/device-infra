import {
  AdvancedMatchMode,
  PickerValueItem,
  ValuePickerApplyEvent,
} from '../models/value_picker_models';

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
 * @returns Array of combined PickerValueItem elements.
 */
export function buildDisplayValues(
  baseValues: readonly PickerValueItem[],
  staged: ReadonlySet<string> | undefined,
  isLoading = false,
): PickerValueItem[] {

  if (isLoading || !staged || staged.size === 0) {
    return [...baseValues];
  }

  const baseLower = new Set(
    baseValues.flatMap((v) => [
      v.value.toLowerCase(),
      v.displayLabel.toLowerCase(),
    ]),
  );

  const missingStaged: PickerValueItem[] = [];
  for (const customVal of staged) {
    if (!baseLower.has(customVal.toLowerCase())) {
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
 * @returns Filtered and sorted slice of candidate items capped at 100 elements.
 */
export function computeFilteredAndSortedValues(
  opts: FilterSortOptions,
): PickerValueItem[] {
  const displayItems = buildDisplayValues(
    opts.items,
    opts.stagedCustomInputs,
    opts.isLoading,
  );
  const q = opts.query.toLowerCase().trim();

  const list = !q
    ? displayItems
    : displayItems.filter(
        (v) =>
          v.value.toLowerCase().includes(q) ||
          v.displayLabel.toLowerCase().includes(q),
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

  return [...normalItems, ...disabledItems].slice(0, 100);
}

/**
 * Pure function: Computes pinned selected items when total candidate count exceeds threshold.
 *
 * @param items List of candidate items.
 * @param selectedSet Set of currently selected value strings.
 * @param query Active search query.
 * @param isLoading Whether values are currently loading.
 * @param threshold Maximum total item threshold before pinning triggers (default 20).
 * @returns Array of pinned items matching current selections.
 */
export function computePinnedValues(
  items: readonly PickerValueItem[],
  selectedSet: ReadonlySet<string>,
  query: string,
  isLoading: boolean,
  threshold = 20,
): PickerValueItem[] {

  const hasQuery = query.trim().length > 0;
  if (hasQuery || isLoading || items.length <= threshold || selectedSet.size === 0) {
    return [];
  }

  return items
    .filter((v) => selectedSet.has(v.value))
    .sort((a, b) => a.displayLabel.localeCompare(b.displayLabel));
}

/**
 * Pure function: Formats millisecond timestamp into datetime-local HTML input format YYYY-MM-DDTHH:MM.
 *
 * @param ms Epoch timestamp in milliseconds.
 * @returns Formatted datetime-local string.
 */
export function toDateTimeLocalString(ms: number): string {
  const d = new Date(ms);
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/**
 * Pure function: Extracts From and To date strings from selected values set or returns default 24h range.
 *
 * @param selectedValues Set of selected value strings.
 * @returns Object containing 'from' and 'to' datetime-local strings.
 */
export function parseDateRange(
  selectedValues: ReadonlySet<string>,
): {from: string; to: string} {
  const selected = Array.from(selectedValues);
  const t = selected[0] || '';
  const match = t.match(/From:\s*([^\s]+)\s*To:\s*([^\s]+)/);
  if (match) {
    return {from: match[1], to: match[2]};
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
 * @returns ValuePickerApplyEvent object ready to dispatch.
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
    advValues: isAdvanced && payload.advValues ? [...payload.advValues] : undefined,
  };
}
