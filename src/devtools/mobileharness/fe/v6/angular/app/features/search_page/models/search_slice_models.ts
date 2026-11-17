/**
 * @fileoverview Host and configuration option interfaces for composable store slices in `services/slices/`.
 */

import {DestroyRef, Signal, WritableSignal} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {Observable} from 'rxjs';

import {
  Filter,
  FleetColumnDescriptor,
  FleetSearchConfig,
  FleetSearchResults,
  Row,
} from '../../../core/models/search';
import {ValuePickerConfig, ValuePickerState} from './filter_picker_models';
import {FilterChip, FilterKeyMetadata} from './search_filter_models';
import {EntityType, FleetScope} from './search_page_ui';

/** Host interface required by the URL & Router synchronization slice (`useSearchUrlSync`). */
export interface SearchUrlSyncHost {
  readonly destroyRef: DestroyRef;
  readonly route: ActivatedRoute;
  readonly router: Router;
  readonly isCurrentRouteActive: Signal<boolean>;
  readonly supportsFleetScope: boolean;
  readonly isFleetSwitchable: boolean;
  readonly fleet: WritableSignal<FleetScope>;
  readonly activeChips: WritableSignal<FilterChip[]>;
  readonly searchQuery: WritableSignal<string>;
  readonly browseAll: WritableSignal<boolean>;
  readonly showSuggestions: WritableSignal<boolean>;
  readonly showValuePicker: Signal<boolean>;
  readonly focusInputTrigger: WritableSignal<boolean>;
  getDefaultChips(): FilterChip[];
  getLegacyQueryParamKeys(): readonly string[];
  handleLegacyQueryParams(params: Record<string, unknown>): boolean;
  cancelPendingLegacyTranslation(): void;
  resolveChipsFromBackend(
    parsedFilters: FilterChip[],
    groupByKeys: string[],
  ): Observable<FilterChip[]>;
  restoreDefaultState(updateUrl?: boolean): void;
}

/** Configuration options for `useValuePickerOverlay`. */
export interface ValuePickerOverlaySliceOptions {
  readonly activeChips: Signal<FilterChip[]>;
  readonly filterChips: Signal<FilterChip[]>;
  readonly groupByKeys: Signal<string[]>;
  readonly showSuggestions: WritableSignal<boolean>;
  readonly autoCollapseAfterApply: Signal<boolean>;
  toggleGroupBy(gbKey: string, displayName?: string): void;
  buildPickerConfig(
    key: string,
    title?: string,
    metadata?: FilterKeyMetadata,
  ): ValuePickerConfig;
  buildInitialPickerState(
    key: string,
    activeChip?: FilterChip,
    metadata?: FilterKeyMetadata,
    stagedValues?: string[],
  ): ValuePickerState;
}

/** Configuration options for `useVisibleColumns`. */
export interface VisibleColumnsSliceOptions {
  readonly entity: Signal<EntityType>;
  readonly fleet: Signal<string>;
  readonly searchConfig: Signal<FleetSearchConfig | null>;
  readonly searchResults: Signal<FleetSearchResults | null>;
  readonly keyMetadataMap: Signal<Map<string, FilterKeyMetadata>>;
  onVisibleColumnsChanged(): void;
}

/** Configuration options for `useGroupAccordion`. */
export interface GroupAccordionSliceOptions {
  readonly entity: Signal<EntityType>;
  readonly fleet: Signal<string>;
  readonly currentUrlParamKey: Signal<string>;
  readonly activeChips: Signal<FilterChip[]>;
  readonly groupByKeys: Signal<string[]>;
  readonly effectiveFilters: Signal<Filter[]>;
  readonly visibleColumns: Signal<Set<string>>;
  readonly sortColumn: Signal<string>;
  getKeyMetadata(key?: string): FilterKeyMetadata | undefined;
  deselectRows(rows: readonly Row[] | undefined): void;
  resetPagination(): void;
}

/** Configuration options for `useArsenalTranslator`. */
export interface ArsenalTranslatorSliceOptions {
  readonly entity: Signal<EntityType>;
  readonly fleet: WritableSignal<FleetScope>;
  readonly isCurrentRouteActive: Signal<boolean>;
  readonly visibleColumnDescriptors: WritableSignal<FleetColumnDescriptor[]>;
  readonly browseAll: WritableSignal<boolean>;
  readonly activeChips: WritableSignal<FilterChip[]>;
  cancelPendingChipResolution(): void;
  syncUrl(
    chips: FilterChip[],
    fleet: 'internal' | 'ats',
    replaceUrl: boolean,
  ): void;
  executeSearch(): void;
}
