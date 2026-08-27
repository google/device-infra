/**
 * @fileoverview Test/Job/Session models extracted from search_tjs.proto
 */

import {TextSegment} from './search_common';

/** TJS search entity type enum. */
export enum TjsEntity {
  TJS_ENTITY_UNSPECIFIED = 'TJS_ENTITY_UNSPECIFIED',
  TJS_ENTITY_TEST = 'TJS_ENTITY_TEST',
  TJS_ENTITY_JOB = 'TJS_ENTITY_JOB',
  TJS_ENTITY_SESSION = 'TJS_ENTITY_SESSION',
}

/** TJS Filter contract. */
export declare interface TjsFilter {
  key: string;
  stringValue?: {value: string};
  enumValues?: {values: string[]};
  timeRange?: TimeRange;
  namedValue?: NamedValue;
}

/** Time range filter for create_time. Both endpoints are RFC 3339 timestamp strings. */
export declare interface TimeRange {
  from?: string;
  to?: string;
}

/** Named key-value pair. */
export declare interface NamedValue {
  name: string;
  value: string;
}

// =============================================================================
// used for request and response
// =============================================================================

// ================GetTjsSearchConfig - TjsSearchConfig===========================
/** Default chip auto-applied at page load. */
export declare interface TjsDefaultChip {
  filter?: TjsFilter;
  pillKey: string;
  pillCondition: string;
  keyDisplayName: string;
}

/** Promoted key for TJS. */
export declare interface TjsPromotedKey {
  key: string;
  displayName: string;
  textInput?: TextInputPicker;
  enumPicker?: EnumPicker;
  timeRange?: TimeRangePicker;
  namedPair?: NamedPairPicker;
}

/** TextInputPicker */
export declare interface TextInputPicker {
  placeholder?: string;
}

/** Enum option in EnumPicker. */
export declare interface EnumOption {
  value: string;
  label: string;
}

/** EnumPicker */
export declare interface EnumPicker {
  options?: EnumOption[];
  multiSelect?: boolean;
}

/** TimeRangePicker */
export declare interface TimeRangePicker {}

/** NamedPairPicker */
export declare interface NamedPairPicker {
  namePlaceholder: string;
  valuePlaceholder: string;
}

// ================ GetTjsSuggestions - TjsSuggestionResponse

/** Search bar suggestion item for TJS search. */
export declare interface TjsSuggestion {
  label?: string;
  mainText?: TextSegment[];
  applyFilter?: ApplyTjsFilter;
  openPicker?: OpenTjsPicker;
}

/** TJS apply filter suggestion action. */
export declare interface ApplyTjsFilter {
  filter?: TjsFilter;
  pillKey?: string;
  pillCondition?: string;
  keyDisplayName?: string;
}

/** TJS open picker suggestion action. */
export declare interface OpenTjsPicker {
  key: string;
  keyDisplayName?: string;
  newChip?: TjsNewChip;
  viewExisting?: TjsViewExisting;
}

/** TJS suggestion picker type for a new chip. */
export declare interface TjsNewChip {}
/** TJS suggestion picker type for viewing an existing chip. */
export declare interface TjsViewExisting {}

// ================ ResolveTjsChips - TjsResolveChipsResponse
/** Resolved TJS chip. */
export declare interface ResolvedTjsChip {
  pillKey: string;
  pillCondition: string;
  keyDisplayName: string;
}
