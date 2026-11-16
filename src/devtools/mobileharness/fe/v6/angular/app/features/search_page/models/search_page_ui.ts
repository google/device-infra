import {
  ApplyTjsFilter,
  FleetAddGroupBy,
  FleetApplyFilter,
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

/**
 * Suggestion item entry rendered in the search box suggestion popover.
 * Directly corresponds to the BFF proto contracts (`FleetSuggestion` | `TjsSuggestion`).
 */
export interface SearchBoxSuggestion {
  label?: string;
  mainText?: TextSegment[];
  count?: number;
  countPrefix?: string;
  countUnit?: string;
  overMax?: boolean;
  applyFilter?: FleetApplyFilter | ApplyTjsFilter;
  openPicker?: FleetOpenPicker | OpenTjsPicker;
  addGroupBy?: FleetAddGroupBy;
  rawItem?: FleetSuggestion | TjsSuggestion;
}

/** Unified configuration options for initializing the search page. */
export type SearchPageConfig = Partial<FleetSearchConfig> &
  Partial<TjsSearchConfig>;

/** Default rows-per-page size inside an expanded group card. */
export const DEFAULT_GROUP_EXPAND_PAGE_SIZE = 100;

/**
 * Default page size of the search footers: rows per page of the flat results
 * table, and groups per page of the grouped header list.
 */
export const DEFAULT_PAGE_SIZE = 25;

/**
 * Rows-per-page options offered for fleet rows (flat table and expanded group
 * cards). The upper end matches the legacy MHFE page sizes lab operators rely
 * on and the BFF's 5000-row clamp; anything above 100 rows is rendered through
 * the windowed (virtual scroll) table so the page stays responsive.
 */
export const PAGE_SIZE_OPTIONS: readonly number[] = [
  10, 25, 50, 100, 500, 1000, 2000, 5000,
];

/**
 * Initial row-height estimate (px) of the flat results table per density.
 * Mirrors `.density-* .results-table tr { height }` in
 * `shared/styles/search_table_common.scss`; the windowed table measures the
 * real rendered height afterwards, so a drift here only costs one re-layout.
 */
export const ROW_HEIGHT_BY_DENSITY: Readonly<Record<TableDensity, number>> = {
  compact: 40,
  default: 52,
  comfortable: 64,
};

/**
 * Initial row-height estimate (px) of an expanded group card's inner table.
 * The SCSS declares 32px, but the checkbox touch target stretches the rendered
 * row to 37px; like the flat estimate it is corrected by measurement.
 */
export const GROUP_ROW_HEIGHT_ESTIMATE = 37;

/** Data state structure for an expanded group card page. */
export interface GroupPageState {
  readonly loading?: boolean;
  readonly error?: string;
  readonly data?: FleetFlatResults;
  readonly sortColumn?: string;
  readonly sortAsc?: boolean;
  readonly pageSize?: number;
}
