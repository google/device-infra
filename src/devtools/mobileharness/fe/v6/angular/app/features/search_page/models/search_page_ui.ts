import {
  FleetColumnConfig,
  FleetLandingConfig,
  TextSegment,
  TjsDefaultChip,
  TjsPromotedKey,
} from '../../../core/models/search';

/** Supported entity category names for search views. */
export type EntityType = 'devices' | 'hosts' | 'tests' | 'jobs' | 'sessions';

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

/** Unified configuration options for initializing the search page. */
export interface SearchPageConfig {
  columns?: FleetColumnConfig;
  landing?: FleetLandingConfig;
  entityLabel?: string;
  defaultChips?: TjsDefaultChip[];
  promotedKeys?: TjsPromotedKey[];
}
