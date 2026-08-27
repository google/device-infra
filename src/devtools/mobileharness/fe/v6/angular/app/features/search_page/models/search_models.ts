import {
  Cell,
  Column,
  ComplexMatch,
  EnumPicker,
  Filter,
  FleetColumnConfig,
  FleetFilterChipMetadata,
  FleetLandingConfig,
  NamedPairPicker,
  Row,
  TextInputPicker,
  TextSegment,
  TimeRangePicker,
  TjsDefaultChip,
  TjsFilter,
  TjsPromotedKey,
} from '../../../core/models/search';
import {AdvancedMatchMode} from './value_picker_models';

/** Supported entity category names for search views. */
export type EntityType = 'devices' | 'hosts' | 'tests' | 'jobs' | 'sessions';

/** Structured flat search results containing table columns, rows, and pagination information. */
export interface FlatSearchResults {
  columns?: Column[];
  rows?: Array<Row | SearchResultRow>;
  nextPageToken?: string;
  prevPageToken?: string;
  total?: number;
  rangeStart?: number;
  rangeEnd?: number;
}

/** Unified metadata descriptor for search filter keys. */
export interface FilterKeyMetadata extends Partial<FleetFilterChipMetadata> {
  key?: string;
  timeRange?: TimeRangePicker | boolean;
  enumPicker?: EnumPicker | boolean;
  namedPair?:
    | NamedPairPicker
    | {namePlaceholder?: string; valuePlaceholder?: string};
  textInput?: TextInputPicker | {placeholder?: string};
}

/** Base chip metadata and matching properties. */
export interface ChipMetadata {
  metadata?: FleetFilterChipMetadata;
  rawValues?: string[];
  negated?: boolean;
  complex?: ComplexMatch;
}

/** Normalized descriptor extracted from a ComplexMatch structure matching ADV_MODES_LIST. */
export interface ComplexMatchInfo {
  mode: AdvancedMatchMode;
  values: string[];
  isNegated: boolean;
}

/** Structure representing an active or staged filter chip. */
export interface FilterChip extends ChipMetadata {
  key?: string;
  pillKey: string;
  pillCondition: string;
  isGroupBy?: boolean;
}

/** Deserialized/parsed result of a URL query string filter param. */
export interface ParsedQueryFilter extends ChipMetadata {
  key: string;
  filter: Filter;
  tjsFilter: TjsFilter;
  fallbackPillCondition: string;
}

/** Suggestion item entry rendered in the search box suggestion popover. */
export interface SearchBoxSuggestion {
  label?: string;
  mainText?: TextSegment[];
  count?: number;
  countPrefix?: string;
  countUnit?: string;
  overMax?: boolean;
  rawItem?: unknown;
}

/** Promoted preset filter key item. */
export interface PromotedFilterKeyItem {
  key: string;
  metadata?: FleetFilterChipMetadata;
}

/** Promoted preset group-by key item. */
export interface PromotedGroupByKeyItem {
  key: string;
  displayName: string;
  groupCount?: number;
}

/** Union type for search result rows supporting both structured Row and key-value Record structures. */
export type SearchResultRow = Record<
  string,
  Cell | string | number | boolean | unknown
>;

/** Configuration options for the search page. */
export interface SearchPageConfig {
  columns?: FleetColumnConfig;
  landing?: FleetLandingConfig;
  entityLabel?: string;
  defaultChips?: TjsDefaultChip[];
  promotedKeys?: TjsPromotedKey[];
}
