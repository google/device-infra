import {
  ComplexMatch,
  EnumPicker,
  Filter,
  FleetFilterChipMetadata,
  NamedPairPicker,
  TextInputPicker,
  TimeRangePicker,
  TjsFilter,
} from '../../../core/models/search';
import {AdvancedMatchMode} from './filter_picker_models';

/** Unified metadata descriptor for search filter keys. */
export interface FilterKeyMetadata extends Partial<FleetFilterChipMetadata> {
  key?: string;
  timeRange?: TimeRangePicker;
  enumPicker?: EnumPicker;
  namedPair?: NamedPairPicker;
  textInput?: TextInputPicker;
}

/** Base chip metadata and matching properties. */
export interface ChipMetadata {
  metadata?: FilterKeyMetadata;
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
  /** Authoritative backend Protobuf TjsFilter contract when in TJS domain. */
  tjsFilter?: TjsFilter;
  /** Authoritative backend Protobuf Filter contract when in Fleet domain. */
  fleetFilter?: Filter;
}

/** Promoted preset filter key item. */
export interface PromotedFilterKeyItem {
  key: string;
  metadata?: FilterKeyMetadata;
}

/** Promoted preset group-by key item. */
export interface PromotedGroupByKeyItem {
  key: string;
  displayName: string;
  groupCount?: number;
}

/** Pair binding a FilterChip with its converted backend Protobuf filter contract. */
export interface FilterPair<TFilter> {
  chip: FilterChip;
  filter: TFilter;
}

/** Result of parsing URL search query parameters into filter and group-by chips. */
export interface ParsedUrlQueryChips {
  parsedFilters: FilterChip[];
  groupByKeys: string[];
  initialChips: FilterChip[];
}
