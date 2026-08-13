/**
 * @fileoverview Inventory search data models extracted from search_fleet.proto
 */

import {Column, KeyDescriptor, Row, TextSegment} from './search_common';

/** Fleet search entity type enum. */
export enum SearchEntity {
  SEARCH_ENTITY_UNSPECIFIED = 'SEARCH_ENTITY_UNSPECIFIED',
  SEARCH_ENTITY_DEVICE = 'SEARCH_ENTITY_DEVICE',
  SEARCH_ENTITY_HOST = 'SEARCH_ENTITY_HOST',
}

/** Fleet scope choice enum. */
export enum Fleet {
  FLEET_UNSPECIFIED = 'FLEET_UNSPECIFIED',
  FLEET_SELF = 'FLEET_SELF',
  FLEET_ATS = 'FLEET_ATS',
}

/** A single filter chip query contract for Fleet search. */
export declare interface Filter {
  key: string;
  simple?: SimpleMatch;
  complex?: ComplexMatch;
}

/** Simple match condition. */
export declare interface SimpleMatch {
  values?: FilterValue[];
  negated?: boolean;
}

/** Single value entry in a SimpleMatch. */
export declare interface FilterValue {
  value?: string;
  noValue?: {};
}

/** Advanced complex match condition. */
export declare interface ComplexMatch {
  startsWith?: StartsWith;
  containsSubstring?: ContainsSubstring;
  matchesRegex?: MatchesRegex;
  matchesExactly?: MatchesExactly;
  matchesAtLeast?: MatchesAtLeast;
}

/** Prefix match. */
export declare interface StartsWith {
  value: string;
}

/** Substring match. */
export declare interface ContainsSubstring {
  value: string;
  negated?: boolean;
}

/** Regular expression match. */
export declare interface MatchesRegex {
  value: string;
  negated?: boolean;
}

/** Exact set match. */
export declare interface MatchesExactly {
  values?: string[];
}

/** Superset match. */
export declare interface MatchesAtLeast {
  values?: string[];
}

// =============================================================================
// used for request and response
// =============================================================================

// ================GetFleetSearchConfig - FleetSearchConfig===========================
/** Fleet column config. */
export declare interface FleetColumnConfig {
  recommended?: KeyDescriptor[];
  defaults?: FleetColumnDescriptor[];
}

/** Fleet column descriptor. */
export declare interface FleetColumnDescriptor {
  key: string;
  displayName: string;
  locked?: boolean;
}

/** Fleet landing config. */
export declare interface FleetLandingConfig {
  enabled?: boolean;
  browseAllCount?: number;
  tryCategories?: FleetTryCategory[];
}

/** Fleet try category. */
export declare interface FleetTryCategory {
  label: string;
  examples?: FleetTryExample[];
}

/** Fleet try example. */
export declare interface FleetTryExample {
  text: string;
}

// ===========================SearchFleet===========================
/** Flat view: paginated rows with columns and sort. */
export declare interface FleetFlatView {
  columns?: string[];
  sort?: FleetColumnSort;
  page?: FleetPageRequest;
}

/** Group-by header view: returns collapsed group cards. */
export declare interface FleetGroupHeaderView {
  groupBy?: string[];
  sort?: FleetGroupSort;
  page?: FleetPageRequest;
}

/** Expand one group: returns rows inside a specific group card. */
export declare interface FleetGroupExpandView {
  groupId: string;
  columns?: string[];
  sort?: FleetColumnSort;
  pageToken?: string;
}

/** Sort by a column value. */
export declare interface FleetColumnSort {
  key: string;
  ascending?: boolean;
}

/** Cursor-based pagination for flat and group-header views. */
export declare interface FleetPageRequest {
  pageSize?: number;
  pageToken?: string;
}

/** Chip metadata for Fleet search. */
export declare interface FleetFilterChipMetadata {
  keyDisplayName?: string;
  canUseAdvanced?: boolean;
  isPlural?: boolean;
}

/** Sort for group headers. */
export declare interface FleetGroupSort {
  field?: FleetGroupSortField;
  ascending?: boolean;
}

/** Sort field for group headers. */
export declare interface FleetGroupSortField {
  itemCount?: FleetItemCountSort;
  groupKey?: string;
}

/** Marker: sort groups by their item count. */
export declare interface FleetItemCountSort {}

/** Flat results: paginated rows with columns. */
export declare interface FleetFlatResults {
  columns?: Column[];
  rows?: Row[];
  total?: number;
  rangeStart?: number;
  rangeEnd?: number;
  nextPageToken?: string;
  prevPageToken?: string;
}

/** Grouped results: group-by headers with counts. */
export declare interface FleetGroupedResults {
  groupByKeys?: Column[];
  groups?: FleetGroup[];
  totalGroups?: number;
  totalItems?: number;
  rangeStart?: number;
  rangeEnd?: number;
  nextPageToken?: string;
  prevPageToken?: string;
}

/** A single group in grouped results. */
export declare interface FleetGroup {
  groupId: string;
  values?: string[];
  itemCount?: number;
  utilization?: FleetUtilization;
}

/** Device status breakdown within a group. */
export declare interface FleetUtilization {
  idle?: number;
  busy?: number;
  other?: number;
  total?: number;
}

// ===========================GetFleetSuggestions================
/** Search bar suggestion item for Fleet search. */
export declare interface FleetSuggestion {
  label?: string;
  mainText?: TextSegment[];
  count?: number;
  countUnit?: string;
  overMax?: boolean;
  countPrefix?: string;
  applyFilter?: FleetApplyFilter;
  openPicker?: FleetOpenPicker;
  addGroupBy?: FleetAddGroupBy;
}

/** Fleet apply filter suggestion action. */
export declare interface FleetApplyFilter {
  resultingFilter?: Filter;
  pillKey?: string;
  pillCondition?: string;
  metadata?: FleetFilterChipMetadata;
}

/** Fleet open value picker suggestion action. */
export declare interface FleetOpenPicker {
  key: string;
  metadata?: FleetFilterChipMetadata;
  newChip?: FleetNewChip;
  viewExisting?: FleetViewExisting;
  stagedModify?: FleetStagedModification;
}

/** Fleet add group-by suggestion action. */
export declare interface FleetAddGroupBy {
  key: string;
  pillKey?: string;
}

/** Suggestion Action detail sub-types */
/** Detail shape for a new filter chip representation. */
export declare interface FleetNewChip {}
/** Detail shape for viewing an existing filter chip configuration. */
export declare interface FleetViewExisting {}
/** Detail shape for a staged modification. */
export declare interface FleetStagedModification {
  values?: string[];
}

// ===========================ResolveFleetChips response===========================
/** Resolved display text for a filter chip. */
export declare interface FleetResolvedFilterChip {
  pillKey: string;
  pillCondition: string;
  metadata?: FleetFilterChipMetadata;
}

/** Resolved display text for a group-by chip. */
export declare interface FleetResolvedGroupByChip {
  pillKey: string;
  displayName: string;
}

// ===============GetFleetValueList - FleetValueListResponse=================
/** Counted value list. */
export declare interface FleetCountedValueList {
  values?: FleetCountedValue[];
  noValueEntry?: FleetCountedNoValueEntry;
}

/** Plain value list. */
export declare interface FleetPlainValueList {
  values?: FleetPlainValue[];
  noValueEntry?: FleetPlainNoValueEntry;
}

/** Counted value. */
export declare interface FleetCountedValue {
  value: string;
  displayLabel: string;
  filtered?: number;
  total?: number;
}

/** Counted no value entry. */
export declare interface FleetCountedNoValueEntry {
  filtered?: number;
  total?: number;
}

/** Plain value. */
export declare interface FleetPlainValue {
  value: string;
  displayLabel: string;
}

/** Plain no-value entry. */
export declare interface FleetPlainNoValueEntry {}

// ============GetFleetPromotedKeys - FleetPromotedKeysResponse================
/** Promoted filter key. */
export declare interface FleetPromotedFilterKey {
  key: string;
  metadata?: FleetFilterChipMetadata;
}

/** Promoted group-by key. */
export declare interface FleetPromotedGroupByKey {
  key: string;
  displayName: string;
  groupCount?: number;
}

// ============GetFleetColumnCatalog - FleetColumnCatalogResponse===============
/** Catalog section. */
export declare interface FleetColumnCatalogSection {
  heading: string;
  entries?: FleetColumnCatalogEntry[];
  totalAvailable?: number;
}

/** Column catalog entry. */
export declare interface FleetColumnCatalogEntry {
  key: string;
  displayName: string;
  deviceCount?: number;
  reason?: string;
}
