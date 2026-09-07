/**
 * @fileoverview Barrel file for all search models.
 */

import {Column, Row} from './search_common';
import {
  Filter,
  Fleet,
  FleetColumnCatalogSection,
  FleetColumnConfig,
  FleetCountedValueList,
  FleetFlatResults,
  FleetFlatView,
  FleetGroupExpandView,
  FleetGroupHeaderView,
  FleetGroupedResults,
  FleetLandingConfig,
  FleetPlainValueList,
  FleetPromotedFilterKey,
  FleetPromotedGroupByKey,
  FleetResolvedFilterChip,
  FleetResolvedGroupByChip,
  FleetSuggestion,
  SearchEntity,
} from './search_fleet';
import {
  ResolvedTjsChip,
  TjsDefaultChip,
  TjsEntity,
  TjsFilter,
  TjsPromotedKey,
  TjsSuggestion,
} from './search_tjs';

// Re-export all model files under this barrel to maintain compatibility with existing code imports
export * from './search_common';
export * from './search_fleet';
export * from './search_tjs';

// =============================================================================
// Requests and Responses
// =============================================================================

/** Fleet search config request. */
export declare interface FleetSearchConfigRequest {
  entity: SearchEntity;
  fleet?: Fleet;
}

/** Page-load configuration returned by GetFleetSearchConfig. */
export declare interface FleetSearchConfig {
  columns?: FleetColumnConfig;
  landing?: FleetLandingConfig;
}

/** Fleet search request. */
export declare interface FleetSearchRequest {
  entity: SearchEntity;
  filters?: Filter[];
  fleet?: Fleet;
  flat?: FleetFlatView;
  groupHeader?: FleetGroupHeaderView;
  groupExpand?: FleetGroupExpandView;
}

/** Fleet search results. */
export declare interface FleetSearchResults {
  flat?: FleetFlatResults;
  grouped?: FleetGroupedResults;
}

/** Fleet suggestion request. */
export declare interface FleetSuggestionRequest {
  entity: SearchEntity;
  input: string;
  filters?: Filter[];
  groupBy?: string[];
  fleet?: Fleet;
  limit?: number;
}

/** Fleet search suggestion response. */
export declare interface FleetSuggestionResponse {
  items?: FleetSuggestion[];
}

/** Fleet chip resolver request. */
export declare interface FleetChipResolverRequest {
  filters?: Filter[];
  groupByKeys?: string[];
  entity?: SearchEntity;
  fleet?: Fleet;
}

/** Fleet chip resolver response. */
export declare interface FleetChipResolverResponse {
  filterChips?: FleetResolvedFilterChip[];
  groupByChips?: FleetResolvedGroupByChip[];
}

/** Fleet value list request. */
export declare interface FleetValueListRequest {
  entity: SearchEntity;
  key: string;
  filters?: Filter[];
  fleet?: Fleet;
}

/** Fleet value list response. */
export declare interface FleetValueListResponse {
  counted?: FleetCountedValueList;
  plain?: FleetPlainValueList;
}

/** Fleet promoted keys request. */
export declare interface FleetPromotedKeysRequest {
  entity: SearchEntity;
  filters?: Filter[];
  groupBy?: string[];
  fleet?: Fleet;
}

/** Fleet promoted keys response. */
export declare interface FleetPromotedKeysResponse {
  filterKeys?: FleetPromotedFilterKey[];
  groupByKeys?: FleetPromotedGroupByKey[];
}

/** Fleet column catalog request. */
export declare interface FleetColumnCatalogRequest {
  entity: SearchEntity;
  fleet?: Fleet;
  query?: string;
  filters?: Filter[];
  recentKeys?: string[];
}

/** Fleet column catalog response. */
export declare interface FleetColumnCatalogResponse {
  sections?: FleetColumnCatalogSection[];
}

/** TJS search config request. */
export declare interface TjsSearchConfigRequest {
  entity: TjsEntity;
}

/** TJS search config response. */
export declare interface TjsSearchConfig {
  entityLabel?: string;
  defaultChips?: TjsDefaultChip[];
  promotedKeys?: TjsPromotedKey[];
}

/** TJS search request. */
export declare interface TjsSearchRequest {
  entity: TjsEntity;
  filters?: TjsFilter[];
  pageToken?: string;
}

/** TJS search response. */
export declare interface TjsSearchResponse {
  columns?: Column[];
  rows?: Row[];
  nextPageToken?: string;
}

/** TJS search suggestion request. */
export declare interface TjsSuggestionRequest {
  entity: TjsEntity;
  input: string;
  filters?: TjsFilter[];
  limit?: number;
}

/** TJS search suggestion response. */
export declare interface TjsSuggestionResponse {
  items?: TjsSuggestion[];
}

/** TJS resolve chips request. */
export declare interface TjsResolveChipsRequest {
  filters?: TjsFilter[];
}

/** TJS resolve chips response. */
export declare interface TjsResolveChipsResponse {
  chips?: ResolvedTjsChip[];
}
