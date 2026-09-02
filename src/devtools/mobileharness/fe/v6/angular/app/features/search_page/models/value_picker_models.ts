/** Represents a selectable item in the value picker. */
export interface PickerValueItem {
  value: string;
  displayLabel: string;
  filtered?: number;
  total?: number;
  isNoValue?: boolean;
  disabled?: boolean;
}

/** Supported advanced matching mode keys. */
export type AdvancedMatchMode =
  | 'prefix'
  | 'substring'
  | 'not_substring'
  | 'regex'
  | 'not_regex'
  | 'exactly'
  | 'at_least';

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

/** Configuration layout and feature toggle properties for ValuePicker. */
export interface ValuePickerConfig {
  key: string;
  type: 'list' | 'range' | 'namedPair' | 'text';
  title?: string;
  canUseAdvanced?: boolean;
  isPlural?: boolean;
  valuesType?: 'counted' | 'plain';
  needsName?: boolean;
  namePlaceholder?: string;
  valPlaceholder?: string;
  showNegateToggle?: boolean;
  showAdvancedMenu?: boolean;
  showSearchInput?: boolean;
  showRowActions?: boolean;
}

/** Active reactive state values for ValuePicker. */
export interface ValuePickerState {
  loading: boolean;
  values: PickerValueItem[];
  valuesType?: 'counted' | 'plain';
  selectedValues: Set<string>;
  negated: boolean;
  advanced: {
    active: boolean;
    mode: AdvancedMatchMode;
    text: string;
    values: string[];
  };
}

/** Default initial state for ValuePicker overlay. */
export const INITIAL_VALUE_PICKER_STATE: ValuePickerState = Object.freeze({
  loading: false,
  values: [],
  selectedValues: new Set<string>(),
  negated: false,
  advanced: {
    active: false,
    mode: 'prefix' as AdvancedMatchMode,
    text: '',
    values: [],
  },
});

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
