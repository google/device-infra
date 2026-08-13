import {
  CdkOverlayOrigin,
  ConnectionPositionPair,
  OverlayModule,
} from '@angular/cdk/overlay';
import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
  viewChildren,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatChipsModule} from '@angular/material/chips';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';

import {
  Filter,
  FleetChipResolverRequest,
  FleetFilterChipMetadata,
  FleetSuggestion,
  TjsPromotedKey,
  TjsResolveChipsRequest,
  TjsSuggestion,
} from '../../../../core/models/search';
import {SEARCH_SERVICE} from '../../../../core/services/search/search_service';
import {SearchPageStore} from '../../services/search_page_store';
import {
  extractAdvancedStateFromChip,
  FilterChip,
  getChipDisplayCondition,
  SearchBoxSuggestion,
} from '../../services/search_utils';
import {ValuePicker} from '../value_picker/value_picker';

/** Component representing the main search input box with filter chip layout. */
@Component({
  selector: 'app-search-box',
  standalone: true,
  templateUrl: './search_box.ng.html',
  styleUrl: './search_box.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatChipsModule,
    MatIconModule,
    MatTooltipModule,
    OverlayModule,
    ValuePicker,
  ],
})
export class SearchBox {
  readonly store = inject(SearchPageStore);
  private readonly searchService = inject(SEARCH_SERVICE);
  private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);
  private blurTimer: ReturnType<typeof setTimeout> | null = null;

  readonly searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  readonly pickerOverlayOrigin = signal<CdkOverlayOrigin | HTMLElement | null>(
    null,
  );
  readonly searchBoxOrigin = viewChild<CdkOverlayOrigin>('searchBoxOrigin');
  readonly chipOrigins = viewChildren<CdkOverlayOrigin>('chipOrigin');
  readonly presetOrigins = viewChildren<CdkOverlayOrigin>('presetOrigin');
  readonly pickerTjsType = signal<
    'simple' | 'enum' | 'range' | 'namedPair' | 'text'
  >('simple');
  readonly pickerNeedsName = signal<boolean>(false);
  readonly pickerNamePlaceholder = signal<string>('');
  readonly pickerValPlaceholder = signal<string>('');

  readonly pickerOverlayPositions: ConnectionPositionPair[] = [
    {
      originX: 'start',
      originY: 'bottom',
      overlayX: 'start',
      overlayY: 'top',
      offsetY: 6,
    },
    {
      originX: 'start',
      originY: 'top',
      overlayX: 'start',
      overlayY: 'bottom',
      offsetY: -6,
    },
  ];

  // Computed / Signal shortcuts
  readonly entity = this.store.entity;
  readonly fleet = this.store.fleet;
  readonly searchQuery = this.store.searchQuery;
  readonly activeChips = this.store.activeChips;
  readonly browseAll = this.store.browseAll;

  readonly showSuggestions = this.store.showSuggestions;
  readonly suggestions = this.store.suggestions;
  readonly showScopeSwitcher = this.store.showScopeSwitcher;
  readonly searchPlaceholder = this.store.searchPlaceholder;
  readonly promotedFilterKeys = this.store.promotedFilterKeys;
  readonly promotedGroupByKeys = this.store.promotedGroupByKeys;
  readonly groupByKeys = this.store.groupByKeys;

  readonly showValuePicker = this.store.showValuePicker;
  readonly pickerKey = this.store.pickerKey;
  readonly pickerTitle = this.store.pickerTitle;
  readonly pickerPos = this.store.pickerPos;
  readonly pickerLoading = this.store.pickerLoading;
  readonly pickerCanUseAdvanced = this.store.pickerCanUseAdvanced;
  readonly pickerIsPlural = signal<boolean>(false);
  readonly pickerValues = this.store.pickerValues;
  readonly selectedPickerValues = this.store.selectedPickerValues;
  readonly pickerNegated = this.store.pickerNegated;

  readonly showSearchClear = this.store.showSearchClear;

  constructor() {
    this.destroyRef.onDestroy(() => {
      if (this.blurTimer) {
        clearTimeout(this.blurTimer);
      }
    });
  }

  onSelectSuggestion(item: SearchBoxSuggestion, event?: MouseEvent) {
    const e = this.entity();
    if (e === 'devices' || e === 'hosts') {
      this.onSelectFleetSuggestion(item.rawItem as FleetSuggestion, event);
    } else {
      this.onSelectTjsSuggestion(item.rawItem as TjsSuggestion);
    }
  }

  setFleet(f: 'internal' | 'ats') {
    this.fleet.set(f);
    this.store.resetSearchState();
  }

  onSearchInput(val: string) {
    this.searchQuery.set(val);
  }

  onInputFocus() {
    if (this.blurTimer) {
      clearTimeout(this.blurTimer);
      this.blurTimer = null;
    }
    this.showSuggestions.set(true);
  }

  onInputBlur() {
    this.blurTimer = setTimeout(() => {
      this.showSuggestions.set(false);
      this.blurTimer = null;
    }, 200);
  }

  getChipDisplayCondition(chip: FilterChip): string {
    return getChipDisplayCondition(chip);
  }

  fetchSuggestions(val: string) {
    this.store.fetchSuggestions(val);
  }

  onSelectFleetSuggestion(item: FleetSuggestion, event?: MouseEvent) {
    this.showSuggestions.set(false);
    this.searchQuery.set('');

    if (item.openPicker) {
      const op = item.openPicker;
      const displayTitle = op.metadata?.keyDisplayName || op.key;
      if (event) {
        this.openValuePickerForKey(op.key, displayTitle, event, op.metadata);
      }
      return;
    }

    const filters: Filter[] = [];
    if (item.applyFilter?.resultingFilter) {
      filters.push(item.applyFilter.resultingFilter);
    }
    const groupByKeys: string[] = [];
    if (item.addGroupBy?.key) {
      groupByKeys.push(item.addGroupBy.key);
    }

    if (filters.length === 0 && groupByKeys.length === 0) {
      this.applyFleetSuggestionDirectly(item);
      return;
    }

    const req: FleetChipResolverRequest = {
      filters: filters.length > 0 ? filters : undefined,
      groupByKeys: groupByKeys.length > 0 ? groupByKeys : undefined,
    };

    this.searchService
      .resolveFleetChips(req)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          let hasApplied = false;
          if (res.filterChips && res.filterChips.length > 0) {
            const rawVals = item.applyFilter?.resultingFilter?.simple?.values
              ?.map((v) => v.value || (v.noValue ? '<empty>' : ''))
              .filter(Boolean);
            for (const fc of res.filterChips) {
              this.store.addFilterChip(
                fc.pillKey,
                fc.pillCondition,
                item.applyFilter?.resultingFilter?.key,
                false,
                fc.metadata,
                rawVals,
              );
              hasApplied = true;
            }
          }
          if (res.groupByChips && res.groupByChips.length > 0) {
            for (const gbc of res.groupByChips) {
              const gbKey = gbc.pillKey || gbc.displayName;
              this.store.addFilterChip(gbKey, gbKey, 'group_by_' + gbKey, true);
              hasApplied = true;
            }
          }
          if (!hasApplied) {
            this.applyFleetSuggestionDirectly(item);
          }
        },
        error: () => {
          this.applyFleetSuggestionDirectly(item);
        },
      });
  }

  private applyFleetSuggestionDirectly(item: FleetSuggestion) {
    if (item.applyFilter) {
      const af = item.applyFilter;
      const rawVals = af.resultingFilter?.simple?.values
        ?.map((v) => v.value || (v.noValue ? '<empty>' : ''))
        .filter(Boolean);
      this.store.addFilterChip(
        af.pillKey || 'Filter',
        af.pillCondition || '',
        af.resultingFilter?.key,
        false,
        af.metadata,
        rawVals,
      );
    }
    if (item.addGroupBy) {
      const gbKey = item.addGroupBy.pillKey || item.addGroupBy.key;
      this.store.addFilterChip(gbKey, gbKey, 'group_by_' + gbKey, true);
    }
  }

  onSelectTjsSuggestion(item: TjsSuggestion) {
    this.showSuggestions.set(false);
    this.searchQuery.set('');

    if (!item.applyFilter) return;

    const af = item.applyFilter;
    if (af.filter) {
      const req: TjsResolveChipsRequest = {
        filters: [af.filter],
      };
      this.searchService
        .resolveTjsChips(req)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (res) => {
            if (res.chips && res.chips.length > 0) {
              const rawVals = af.filter?.stringValue?.value
                ? [af.filter.stringValue.value]
                : af.filter?.enumValues?.values || [];
              for (const chip of res.chips) {
                this.store.addFilterChip(
                  chip.pillKey,
                  chip.pillCondition,
                  af.filter?.key,
                  false,
                  undefined,
                  rawVals,
                );
              }
            } else {
              this.applyTjsSuggestionDirectly(item);
            }
          },
          error: () => {
            this.applyTjsSuggestionDirectly(item);
          },
        });
    } else {
      this.applyTjsSuggestionDirectly(item);
    }
  }

  private applyTjsSuggestionDirectly(item: TjsSuggestion) {
    if (item.applyFilter) {
      const af = item.applyFilter;
      const rawVals = af.filter?.stringValue?.value
        ? [af.filter.stringValue.value]
        : af.filter?.enumValues?.values || [];
      this.store.addFilterChip(
        af.pillKey || 'Filter',
        af.pillCondition || '',
        af.filter?.key,
        false,
        undefined,
        rawVals.length > 0 ? rawVals : undefined,
      );
    }
  }

  clearSearch() {
    this.store.resetSearchState();
  }

  onSelectPromotedFilterKey(
    k: {key: string; metadata?: FleetFilterChipMetadata},
    event: MouseEvent,
  ) {
    const displayName = k.metadata?.keyDisplayName || k.key;
    this.openValuePickerForKey(k.key, displayName, event);
  }

  onSelectPromotedGroupBy(k: {
    key: string;
    displayName: string;
    groupCount?: number;
  }) {
    const gbKey = k.key;
    const current = this.groupByKeys();
    if (current.includes(gbKey)) {
      const existing = this.activeChips().find(
        (c) => c.isGroupBy && c.key === 'group_by_' + gbKey,
      );
      if (existing) {
        this.store.removeFilterChip(existing);
      }
    } else {
      this.store.addFilterChip(
        k.displayName || gbKey,
        k.displayName || gbKey,
        'group_by_' + gbKey,
        true,
      );
    }
  }

  isChipNegated(chip: FilterChip): boolean {
    if (chip.negated) return true;
    if (chip.complex?.containsSubstring?.negated) return true;
    if (chip.complex?.matchesRegex?.negated) return true;
    const cond = (chip.pillCondition || '').toLowerCase().trim();
    if (
      cond.startsWith('does not') ||
      cond.startsWith('is not') ||
      cond.startsWith('not ') ||
      cond.startsWith('!')
    ) {
      return true;
    }
    return false;
  }

  openValuePickerForChip(chip: FilterChip, event: MouseEvent) {
    if (chip.isGroupBy) return;
    const key = chip.key || chip.pillKey;
    const title = chip.metadata?.keyDisplayName || chip.pillKey;
    this.openValuePickerForKey(key, title, event, chip.metadata);
  }

  openValuePickerForKey(
    key: string,
    title: string,
    event?: MouseEvent,
    metadata?: FleetFilterChipMetadata,
  ) {
    if (event) {
      event.stopPropagation();
    }

    const cleanKey = key.toLowerCase().replace(/^(field::|dim::|config::)/, '');
    const currentKey = this.pickerKey()
      ? this.pickerKey()
          .toLowerCase()
          .replace(/^(field::|dim::|config::)/, '')
      : '';

    if (this.showValuePicker() && currentKey === cleanKey) {
      this.store.closeValuePicker();
      return;
    }

    if (this.showValuePicker()) {
      this.store.closeValuePicker();
    }
    const activeChip = this.activeChips().find(
      (c) =>
        !c.isGroupBy &&
        ((c.key && c.key.toLowerCase() === key.toLowerCase()) ||
          c.pillKey.toLowerCase() === cleanKey),
    );

    let foundOrigin: CdkOverlayOrigin | HTMLElement | null = null;
    if (activeChip) {
      const chipKey = (activeChip.key || activeChip.pillKey).toLowerCase();
      const matchingChip = this.chipOrigins().find((origin) => {
        const el = origin.elementRef.nativeElement;
        const dataKey = el.getAttribute('data-chip-key');
        return dataKey && dataKey.toLowerCase() === chipKey;
      });
      if (matchingChip) {
        foundOrigin = matchingChip;
      }
    }

    if (!foundOrigin && event?.currentTarget) {
      const targetEl = event.currentTarget as HTMLElement;
      if (!targetEl.closest('.search-suggestions-popover')) {
        const matchingPreset = this.presetOrigins().find(
          (origin: CdkOverlayOrigin) =>
            origin.elementRef.nativeElement.contains(targetEl),
        );
        foundOrigin = matchingPreset || targetEl;
      }
    }

    if (!foundOrigin) {
      foundOrigin = this.searchBoxOrigin() || null;
    }

    this.pickerOverlayOrigin.set(foundOrigin);
    this.pickerKey.set(key);

    const meta = metadata || this.store.keyMetadataMap.get(key);
    const displayTitle = meta?.keyDisplayName || title || key;
    this.pickerTitle.set(displayTitle);
    this.pickerCanUseAdvanced.set(!!meta?.canUseAdvanced);
    this.pickerIsPlural.set(!!meta?.isPlural);

    if (this.store.isTjs() && meta) {
      const tjsMeta = meta as unknown as TjsPromotedKey;
      if (tjsMeta.timeRange) {
        this.pickerTjsType.set('range');
        this.pickerNeedsName.set(false);
      } else if (tjsMeta.enumPicker) {
        this.pickerTjsType.set('enum');
        this.pickerNeedsName.set(false);
      } else if (tjsMeta.namedPair) {
        this.pickerTjsType.set('namedPair');
        this.pickerNeedsName.set(true);
        this.pickerNamePlaceholder.set(
          tjsMeta.namedPair.namePlaceholder || 'Name',
        );
        this.pickerValPlaceholder.set(
          tjsMeta.namedPair.valuePlaceholder || 'Value',
        );
      } else if (tjsMeta.textInput) {
        this.pickerTjsType.set('text');
        this.pickerNeedsName.set(false);
        this.pickerValPlaceholder.set(tjsMeta.textInput.placeholder || 'Value');
      } else {
        this.pickerTjsType.set('simple');
        this.pickerNeedsName.set(false);
      }
    } else {
      this.pickerTjsType.set('simple');
      this.pickerNeedsName.set(false);
    }

    let isAdv = false;
    let advMode = 'prefix';
    let advText = '';
    let advValues: string[] = [];

    if (activeChip) {
      const state = extractAdvancedStateFromChip(activeChip);
      isAdv = state.isAdv;
      advMode = state.advMode;
      advText = state.advText;
      advValues = state.advValues;

      if (activeChip.rawValues) {
        this.selectedPickerValues.set(new Set(activeChip.rawValues));
      } else {
        const existingVals = activeChip.pillCondition
          .split(',')
          .map((s: string) => s.trim())
          .map((s: string) => (s === '(no value)' ? '<empty>' : s))
          .filter(Boolean);
        this.selectedPickerValues.set(new Set(existingVals));
      }
    } else {
      this.selectedPickerValues.set(new Set());
    }

    this.store.pickerIsAdvanced.set(isAdv);
    this.store.pickerAdvMode.set(advMode);
    this.store.pickerAdvText.set(advText);
    this.store.pickerAdvValues.set(advValues);

    this.showValuePicker.set(true);
    this.store.fetchValueList(key);
  }

  executeFleetSearch() {
    this.store.executeFleetSearch();
  }
}
