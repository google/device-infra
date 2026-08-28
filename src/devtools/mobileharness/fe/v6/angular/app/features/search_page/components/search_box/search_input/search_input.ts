import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';

import {FilterChip, SearchBoxSuggestion} from '../../../models';
import {SearchPageStore} from '../../../services/search_page_store';
import {getChipKey} from '../../../utils';
import {SearchSuggestions} from '../search_suggestions/search_suggestions';

/**
 * Standalone component containing Fleet scope switcher, active filter chips,
 * and primary search input with typeahead suggestions popover.
 */
@Component({
  selector: 'app-search-input',
  standalone: true,
  templateUrl: './search_input.ng.html',
  styleUrl: './search_input.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatButtonModule, MatIconModule, SearchSuggestions],
})
export class SearchInput {
  /** Shared search page state store injected via Angular Dependency Injection. */
  readonly store = inject(SearchPageStore);

  /** Helper to safely extract chip keys. */
  readonly getChipKey = getChipKey;

  /** Signal reference targeting the native HTML `<input>` element inside the search bar. */
  readonly searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  /** Signal reference targeting the search box outer container DOM element for CDK overlay positioning. */
  readonly searchBoxOrigin = viewChild<ElementRef<HTMLElement>>('searchBox');

  /** Active highlighted index during keyboard navigation over auto-complete suggestions (-1 when unselected). */
  readonly activeSuggestionIndex = signal<number>(-1);

  /**
   * Handles user selection of an auto-complete suggestion item.
   * If the suggestion requests an open-picker action, opens the FilterValuePicker overlay anchored to the search box;
   * otherwise, applies the suggestion filter directly to the store.
   *
   * @param event Object containing the selected `SearchBoxSuggestion` item.
   */
  onSelectSuggestion(event: {item: SearchBoxSuggestion}) {
    const {item} = event;
    this.searchInput()?.nativeElement.blur();
    this.store.showSuggestions.set(false);
    this.activeSuggestionIndex.set(-1);

    this.store.selectSuggestion(
      item,
      this.searchBoxOrigin()?.nativeElement || null,
    );
  }

  /**
   * Handles user text input events in the search box.
   * Updates the search query signal, resets active suggestion highlight, and opens suggestions popover.
   *
   * @param val Current text value in the search input box.
   */
  onSearchInput(val: string) {
    this.store.searchQuery.set(val);
    this.store.showSuggestions.set(true);
    this.activeSuggestionIndex.set(-1);
  }

  /** Focuses the native search input element and displays the suggestions popover. */
  focusInput() {
    this.searchInput()?.nativeElement.focus();
    this.store.showSuggestions.set(true);
  }

  /** Handles focus event on the input element by opening the suggestions popover. */
  onInputFocus() {
    this.store.showSuggestions.set(true);
  }

  /**
   * Prevents click propagation from closing overlays and displays suggestions popover on input click.
   *
   * @param event Mouse event from input click.
   */
  onInputClick(event?: MouseEvent) {
    event?.stopPropagation();
    this.store.showSuggestions.set(true);
  }

  /**
   * Handles click events anywhere on the outer search box area.
   * Delegates focus to the text input unless a chip or clear button was explicitly clicked.
   *
   * @param event Mouse event from search box container click.
   */
  onSearchBoxClick(event?: MouseEvent) {
    const target = event?.target as HTMLElement | null;
    if (
      target === this.searchInput()?.nativeElement ||
      target?.closest('.search-chip') ||
      target?.closest('.search-clear-btn')
    ) {
      return;
    }
    this.focusInput();
  }

  /** Handles blur event on the input element by hiding suggestions popover. */
  onInputBlur() {
    this.store.showSuggestions.set(false);
  }

  /**
   * Dispatches keyboard navigation events for search input controls.
   * Handles ArrowUp/ArrowDown for suggestion highlighting, Enter for selection/execution, and Escape for closing overlays.
   *
   * @param event Keyboard event triggered from the search input.
   */
  onKeyDown(event: KeyboardEvent) {
    const key = event.key;
    if (!['ArrowDown', 'ArrowUp', 'Enter', 'Escape'].includes(key)) return;

    const suggestions = this.store.suggestions();
    const isShowing = this.store.showSuggestions() && suggestions.length > 0;

    switch (key) {
      case 'ArrowDown':
        if (!isShowing) return;
        event.preventDefault();
        this.activeSuggestionIndex.update((i) => (i + 1) % suggestions.length);
        break;

      case 'ArrowUp':
        if (!isShowing) return;
        event.preventDefault();
        this.activeSuggestionIndex.update((i) =>
          i <= 0 ? suggestions.length - 1 : i - 1,
        );
        break;

      case 'Enter':
        this.handleEnterKey(event, isShowing, suggestions);
        break;

      case 'Escape':
        this.handleEscapeKey();
        break;

      default:
        break;
    }
  }

  /**
   * Private helper executing selection or search query when the Enter key is pressed.
   *
   * @param event Keyboard event.
   * @param isShowing Whether suggestions popover is active.
   * @param suggestions Array of available search suggestions.
   */
  private handleEnterKey(
    event: KeyboardEvent,
    isShowing: boolean,
    suggestions: SearchBoxSuggestion[],
  ) {
    if (suggestions.length > 0) {
      event.preventDefault();
      const idx = this.activeSuggestionIndex();
      const targetIdx =
        isShowing && idx >= 0 && idx < suggestions.length ? idx : 0;
      this.onSelectSuggestion({item: suggestions[targetIdx]});
      return;
    }

    if (this.store.searchQuery().trim()) {
      this.store.showSuggestions.set(false);
      this.store.executeSearch();
    }
  }

  /** Private helper closing suggestions popover or value picker overlay when Escape key is pressed. */
  private handleEscapeKey() {
    if (this.store.showSuggestions()) {
      this.store.showSuggestions.set(false);
      this.activeSuggestionIndex.set(-1);
      return;
    }

    if (this.store.showValuePicker()) {
      this.store.closeValuePicker();
    }
  }

  /**
   * Opens the FilterValuePicker CDK Overlay popover to edit an active filter chip.
   *
   * @param chip The FilterChip to edit.
   * @param anchor DOM element anchor where the ValuePicker overlay positions itself.
   * @param event Optional MouseEvent to prevent event bubbling.
   */
  openPickerForChip(chip: FilterChip, anchor: HTMLElement, event?: MouseEvent) {
    event?.stopPropagation();
    if (chip.isGroupBy) return;
    const key = getChipKey(chip);
    const title = chip.metadata?.keyDisplayName || chip.pillKey;
    this.store.openValuePicker(key, anchor, title, chip.metadata);
  }
}
