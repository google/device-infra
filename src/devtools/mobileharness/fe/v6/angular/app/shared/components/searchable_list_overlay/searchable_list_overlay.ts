import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  EventEmitter,
  Input,
  Output,
  signal,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';

/** Represents an item in the searchable list with a name and a value. */
export interface SearchableListOverlayItem {
  name: string;
  value: string;
}

/** Represents a chip item in the searchable list. */
export interface SearchableListOverlayChip {
  label: string;
  cssClass?: string;
}

/** The type of data to display in the overlay list. */
export type OverlayListType = 'key-value' | 'simple' | 'chip';

/** Configuration data for the SearchableListOverlayComponent. */
export interface SearchableListOverlayData {
  title: string;
  subtitle?: string;
  type: OverlayListType;
  items: Array<SearchableListOverlayItem | string | SearchableListOverlayChip>;
}

/**
 * A component that displays a list of items (either structured or simple strings)
 * in an overlay with a search filter.
 */
@Component({
  selector: 'app-searchable-list-overlay',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  templateUrl: './searchable_list_overlay.ng.html',
  styleUrl: './searchable_list_overlay.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchableListOverlayComponent {
  @Input({required: true}) data!: SearchableListOverlayData;
  @Output() readonly closeOverlay = new EventEmitter<void>();

  readonly searchFilter = signal('');

  readonly filteredItems = computed(() => {
    const filter = this.searchFilter().toLowerCase().trim();
    const items = this.data.items || [];
    if (!filter) {
      return items;
    }

    return items.filter((item) => {
      if (this.data.type === 'simple') {
        return (item as string).toLowerCase().includes(filter);
      }

      if (this.data.type === 'chip') {
        return (item as SearchableListOverlayChip).label
          .toLowerCase()
          .includes(filter);
      }

      const i = item as SearchableListOverlayItem;
      return (
        (i.name?.toLowerCase() || '').includes(filter) ||
        (i.value?.toLowerCase() || '').includes(filter)
      );
    });
  });

  close() {
    this.closeOverlay.emit();
  }
}
