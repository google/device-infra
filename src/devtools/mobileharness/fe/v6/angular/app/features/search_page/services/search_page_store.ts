import {Injectable, Signal, WritableSignal} from '@angular/core';

import {
  Column,
  ComplexMatch,
  FleetColumnConfig,
  FleetFilterChipMetadata,
  FleetLandingConfig,
  TjsDefaultChip,
  TjsPromotedKey,
} from '../../../core/models/search';
import {ValuePickerApplyEvent} from '../components/value_picker/value_picker';
import {
  EntityType,
  FilterChip,
  PickerValueItem,
  PromotedFilterKeyItem,
  PromotedGroupByKeyItem,
  SearchBoxSuggestion,
} from './search_utils';

/** Configuration options for the search page. */
export interface SearchPageConfig {
  columns?: FleetColumnConfig;
  landing?: FleetLandingConfig;
  entityLabel?: string;
  defaultChips?: TjsDefaultChip[];
  promotedKeys?: TjsPromotedKey[];
}

/** Abstract base store defining shared search state and actions. */
@Injectable()
export abstract class SearchPageStore {
  abstract readonly entity: WritableSignal<EntityType>;
  abstract readonly fleet: WritableSignal<'internal' | 'ats'>;
  abstract readonly searchQuery: WritableSignal<string>;
  abstract readonly activeChips: WritableSignal<FilterChip[]>;
  abstract readonly showSuggestions: WritableSignal<boolean>;
  abstract readonly suggestions: Signal<SearchBoxSuggestion[]>;

  abstract readonly showScopeSwitcher: Signal<boolean>;
  abstract readonly searchPlaceholder: Signal<string>;
  abstract readonly promotedFilterKeys: WritableSignal<PromotedFilterKeyItem[]>;
  abstract readonly promotedGroupByKeys: WritableSignal<
    PromotedGroupByKeyItem[]
  >;
  abstract readonly groupByKeys: Signal<string[]>;
  abstract readonly browseAll: WritableSignal<boolean>;

  abstract readonly showValuePicker: WritableSignal<boolean>;
  abstract readonly pickerKey: WritableSignal<string>;
  abstract readonly pickerTitle: WritableSignal<string>;
  abstract readonly pickerPos: WritableSignal<{top: number; left: number}>;
  abstract readonly pickerLoading: Signal<boolean>;
  abstract readonly pickerCanUseAdvanced: WritableSignal<boolean>;
  abstract readonly pickerIsAdvanced: WritableSignal<boolean>;
  abstract readonly pickerAdvMode: WritableSignal<string>;
  abstract readonly pickerAdvText: WritableSignal<string>;
  abstract readonly pickerAdvValues: WritableSignal<string[]>;
  abstract readonly pickerValues: Signal<PickerValueItem[]>;
  abstract readonly selectedPickerValues: WritableSignal<Set<string>>;
  abstract readonly pickerNegated: WritableSignal<boolean>;

  abstract readonly showSearchClear: Signal<boolean>;
  abstract readonly keyMetadataMap: Map<string, FleetFilterChipMetadata>;

  abstract readonly isLandingState: Signal<boolean>;
  abstract readonly isTjs: Signal<boolean>;
  abstract readonly searchConfig: Signal<SearchPageConfig | null>;

  abstract readonly displayColumns: Signal<Column[]>;
  abstract readonly rows: Signal<unknown[]>;
  abstract readonly isLoading: Signal<boolean>;
  abstract readonly pageIndex: WritableSignal<number>;
  abstract readonly pageSize: WritableSignal<number>;
  abstract readonly density: WritableSignal<
    'compact' | 'default' | 'comfortable'
  >;
  abstract readonly searchResults: Signal<unknown>;

  abstract resetSearchState(): void;
  abstract loadSearchConfig(): void;
  abstract loadPromotedKeys(): void;
  abstract fetchSuggestions(val: string): void;
  abstract addFilterChip(
    pillKey: string,
    pillCondition: string,
    key?: string,
    isGroupBy?: boolean,
    metadata?: FleetFilterChipMetadata,
    rawValues?: string[],
    negated?: boolean,
    complex?: ComplexMatch,
  ): void;
  abstract removeFilterChip(chip: FilterChip): void;
  abstract fetchValueList(key: string): void;
  abstract executeFleetSearch(): void;
  abstract closeValuePicker(): void;
  abstract applyValuePicker(event: ValuePickerApplyEvent): void;
  abstract prevPage(): void;
  abstract nextPage(): void;
}
