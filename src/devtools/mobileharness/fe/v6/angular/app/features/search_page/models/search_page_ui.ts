import {
  FleetAddGroupBy,
  FleetFlatResults,
  FleetOpenPicker,
  FleetSearchConfig,
  FleetSuggestion,
  OpenTjsPicker,
  TextSegment,
  TjsSearchConfig,
  TjsSuggestion,
} from '../../../core/models/search';

/** Supported entity category names for search views. */
export type EntityType = 'devices' | 'hosts' | 'tests' | 'jobs' | 'sessions';

/** Supported fleet operational scope partitions. */
export type FleetScope = 'internal' | 'ats';

/** Density option type for search results table rows. */
export type TableDensity = 'compact' | 'default' | 'comfortable';

/** Suggestion item entry rendered in the search box suggestion popover. */
export interface SearchBoxSuggestion {
  label?: string;
  mainText?: TextSegment[];
  count?: number;
  countPrefix?: string;
  countUnit?: string;
  overMax?: boolean;
  openPicker?: FleetOpenPicker | OpenTjsPicker;
  addGroupBy?: FleetAddGroupBy;
  rawItem?: FleetSuggestion | TjsSuggestion;
}

/** Unified configuration options for initializing the search page. */
export type SearchPageConfig = Partial<FleetSearchConfig> &
  Partial<TjsSearchConfig>;

/** Data state structure for an expanded group card page. */
export interface GroupPageState {
  readonly loading?: boolean;
  readonly error?: string;
  readonly data?: FleetFlatResults;
}
