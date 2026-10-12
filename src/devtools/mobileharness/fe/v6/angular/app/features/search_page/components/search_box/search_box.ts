import {
  ChangeDetectionStrategy,
  Component,
  inject,
  viewChild,
} from '@angular/core';

import {HeaderSearchDockDirective} from '../../../../shared/directives/header_search_dock/header_search_dock';
import {SearchPageStore} from '../../services/search_page_store';
import {FilterValuePicker} from '../filter_value_picker/filter_value_picker';
import {FilterPresets} from './filter_presets/filter_presets';
import {SearchInput} from './search_input/search_input';

/** Compound component assembling search input, filter presets, and filter value picker. */
@Component({
  selector: 'app-search-box',
  standalone: true,
  templateUrl: './search_box.ng.html',
  styleUrl: './search_box.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    HeaderSearchDockDirective,
    FilterPresets,
    SearchInput,
    FilterValuePicker,
  ],
})
export class SearchBox {
  readonly store = inject(SearchPageStore);
  readonly searchInputComponent = viewChild(SearchInput);

  /** Delegates focus to the underlying SearchInput component. */
  focusInput() {
    this.searchInputComponent()?.focusInput();
  }
}
