/** Supported advanced matching mode keys. */
export type AdvancedMatchMode =
  | 'prefix'
  | 'substring'
  | 'not_substring'
  | 'regex'
  | 'not_regex'
  | 'exactly'
  | 'at_least';

/** Represents a selectable item in the value picker. */
export interface PickerValueItem {
  value: string;
  displayLabel: string;
  filtered?: number;
  total?: number;
  isNoValue?: boolean;
  disabled?: boolean;
}

/** Payload emitted when applying value picker changes. */
export interface ValuePickerApplyEvent {
  selected: string[];
  negate: boolean;
  isAdvanced: boolean;
  advMode?: AdvancedMatchMode;
  advText?: string;
  advValues?: string[];
  rangeFrom?: string;
  rangeTo?: string;
  propName?: string;
  textVal?: string;
}

/** Configuration layout and domain properties for ValuePicker. */
export interface ValuePickerConfig {
  key: string;
  type: 'list' | 'range' | 'namedPair' | 'text';
  title?: string;
  canUseAdvanced?: boolean;
  isPlural?: boolean;
  needsName?: boolean;
  namePlaceholder?: string;
  valPlaceholder?: string;
}

/** Active reactive state values for ValuePicker. */
export interface ValuePickerState {
  loading: boolean;
  values: PickerValueItem[];
  selectedValues: Set<string>;
  negated: boolean;
  advanced: {
    active: boolean;
    mode: AdvancedMatchMode;
    text: string;
    values: string[];
  };
}

/** Creates a fresh default initial state for ValuePicker overlay. */
export function createInitialValuePickerState(): ValuePickerState {
  return {
    loading: false,
    values: [],
    selectedValues: new Set<string>(),
    negated: false,
    advanced: {
      active: false,
      mode: 'prefix',
      text: '',
      values: [],
    },
  };
}

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
  /** Set of currently selected value strings. */
  readonly selectedValues?: ReadonlySet<string>;
  /** Whether the candidate items are plain values without count columns. */
  readonly isPlain?: boolean;
  /** Loading flag indicating whether backend values are currently fetching. */
  readonly isLoading?: boolean;
}

/** Date range bounds representation for value picker range layout. */
export interface DateRangeState {
  readonly from: string;
  readonly to: string;
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

/** List of configurations for advanced matching options. */
export const ADV_MODES_LIST: Array<{
  id: AdvancedMatchMode;
  label: string;
  placeholder: string;
}> = [
  {id: 'prefix', label: 'Starts with', placeholder: 'Enter a prefix…'},
  {id: 'substring', label: 'Contains', placeholder: 'Enter text…'},
  {id: 'not_substring', label: 'Does not contain', placeholder: 'Enter text…'},
  {id: 'regex', label: 'Matches regex', placeholder: 'Enter a pattern…'},
  {
    id: 'not_regex',
    label: 'Does not match regex',
    placeholder: 'Enter a pattern…',
  },
  {id: 'exactly', label: 'Is exactly', placeholder: 'Add a value…'},
  {id: 'at_least', label: 'Is at least', placeholder: 'Add a value…'},
];
