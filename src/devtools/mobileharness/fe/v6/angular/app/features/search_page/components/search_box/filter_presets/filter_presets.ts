import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, inject} from '@angular/core';

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
  imports: [CommonModule],
})
export class FilterPresets {
  /** Shared search page state store injected via Angular Dependency Injection. */
  readonly store = inject(SearchPageStore);

  /**
   * Handles user click on a promoted quick filter key link.
   * Stops event propagation and opens the FilterValuePicker overlay anchored to the preset button element.
   *
   * @param k The selected `PromotedFilterKeyItem` configuration.
   * @param anchor DOM button element acting as the anchor for the ValuePicker overlay.
   * @param event Optional MouseEvent to prevent event bubbling.
   */
  onSelectPromotedFilterKey(
    k: PromotedFilterKeyItem,
    anchor: HTMLElement,
    event?: MouseEvent,
  ) {
    event?.stopPropagation();
    const displayName = k.metadata?.keyDisplayName || k.key;
    this.store.openValuePicker(k.key, anchor, displayName, k.metadata);
  }

  /**
   * Handles user click on a promoted group-by quick preset link.
   * Toggles the specified group-by key (adds it if not present, or removes it if already active).
   *
   * @param k The selected `PromotedGroupByKeyItem` configuration.
   */
  onSelectPromotedGroupBy(k: PromotedGroupByKeyItem) {
    this.store.toggleGroupBy(k.key, k.displayName);
  }
}
