import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  effect,
  inject,
  viewChild,
} from '@angular/core';

import {HeaderSearchDockService} from '../../../../core/services/search/header_search_dock_service';
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
  imports: [FilterPresets, SearchInput, FilterValuePicker],
})
export class SearchBox {
  readonly store = inject(SearchPageStore);
  private readonly dockService = inject(HeaderSearchDockService);

  readonly searchInputComponent = viewChild(SearchInput);
  readonly searchInputElement = viewChild(SearchInput, {read: ElementRef});
  readonly bodySearchDock =
    viewChild<ElementRef<HTMLElement>>('bodySearchDock');

  constructor() {
    // Angular 20+ best practice: effect is strictly reserved for imperative external
    // DOM reparenting side-effects (no signal mutations/writes).
    effect(() => {
      this.updateDockPosition(this.store.isLandingState());
    });

    inject(DestroyRef).onDestroy(() => {
      this.cleanupHeaderDock();
    });
  }

  /** Delegates focus to the underlying SearchInput component. */
  focusInput() {
    this.searchInputComponent()?.focusInput();
  }

  private updateDockPosition(isLanding: boolean) {
    const inputEl = this.searchInputElement()?.nativeElement as
      | HTMLElement
      | undefined;
    const bodyDock = this.bodySearchDock()?.nativeElement;
    if (!inputEl || !bodyDock) return;

    if (!isLanding) {
      this.dockService.dock(inputEl);
    } else {
      this.dockService.undock(inputEl, bodyDock);
    }
  }

  private cleanupHeaderDock() {
    const inputEl = this.searchInputElement()?.nativeElement as
      | HTMLElement
      | undefined;
    if (inputEl) {
      this.dockService.cleanup(inputEl);
    }
  }
}
