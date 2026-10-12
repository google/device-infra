import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  linkedSignal,
} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';

import {PromotedFilterKeyItem, PromotedGroupByKeyItem} from '../../../models';
import {SearchPageStore} from '../../../services/search_page_store';

/**
 * Standalone component displaying promoted quick filter keys and group-by preset links below the primary search box.
 */
@Component({
  selector: 'app-filter-presets',
  standalone: true,
  templateUrl: './filter_presets.ng.html',
  styleUrl: './filter_presets.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule],
})
export class FilterPresets {
  /** Shared search page state store injected via Angular Dependency Injection. */
  readonly store = inject(SearchPageStore);

  /**
   * Whether the suggested filter presets panel is collapsed into a single pill.
   * Automatically expands when returning to the landing state.
   */
  readonly isCollapsed = linkedSignal({
    source: this.store.isLandingState,
    computation: (isLanding, previous) =>
      isLanding ? false : (previous?.value ?? false),
  });

  readonly hasPromotedFilters = computed(
    () => this.store.promotedFilterKeys().length > 0,
  );

  readonly hasPromotedGroups = computed(
    () => this.store.promotedGroupByKeys().length > 0,
  );

  readonly hasAnyPromoted = computed(
    () => this.hasPromotedFilters() || this.hasPromotedGroups(),
  );

  /** Toggles or sets the collapsed state of the preset filters bar. */
  toggleCollapsed(forceState?: boolean) {
    this.isCollapsed.set(forceState ?? !this.isCollapsed());
  }

  /**
   * Handles user click on a promoted quick filter key link.
   * Opens the suggestions popover, creates a temporary/pending chip if not yet applied,
   * focuses the chip in the popover, and opens the ValuePicker anchored to that chip.
   */
  onSelectPromotedFilterKey(k: PromotedFilterKeyItem, event?: MouseEvent) {
    event?.stopPropagation();
    this.store.openQuickFilter(k.key, k.displayName, k.metadata);
  }

  /**
   * Handles user click on a promoted group-by quick preset link.
   * Relies on backend BFF promoted-keys for disabled state and reason.
   */
  onSelectPromotedGroupBy(k: PromotedGroupByKeyItem, event?: MouseEvent) {
    event?.stopPropagation();
    if (k.disabled) return;
    this.store.openQuickGroupBy(k.key, k.displayName);
  }
}
