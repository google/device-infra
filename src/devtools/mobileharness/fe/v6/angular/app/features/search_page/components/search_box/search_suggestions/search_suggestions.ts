import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  output,
} from '@angular/core';

import {SearchBoxSuggestion} from '../../../models';
import {SearchPageStore} from '../../../services/search_page_store';

/** Standalone popover component rendering typeahead search suggestions. */
@Component({
  selector: 'app-search-suggestions',
  standalone: true,
  templateUrl: './search_suggestions.ng.html',
  styleUrl: './search_suggestions.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
})
export class SearchSuggestions {
  readonly store = inject(SearchPageStore);

  /** Currently highlighted keyboard navigation index. */
  readonly activeIndex = input<number>(-1);

  /** Event emitted when a suggestion is selected. */
  readonly selectSuggestion = output<{item: SearchBoxSuggestion}>();

  onItemClick(item: SearchBoxSuggestion) {
    this.selectSuggestion.emit({item});
  }
}
