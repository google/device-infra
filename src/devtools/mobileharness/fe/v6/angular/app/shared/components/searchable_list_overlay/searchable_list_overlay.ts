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

/** Configuration data for the SearchableListOverlayComponent. */
export interface SearchableListOverlayData {
  title: string;
  subtitle?: string;
  items?: SearchableListOverlayItem[];
  simpleItems?: string[];
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
    if (!filter) {
      return this.data?.items || [];
    }
    return (this.data?.items || []).filter(
      (d) =>
        (d.name?.toLowerCase() || '').includes(filter) ||
        (d.value?.toLowerCase() || '').includes(filter),
    );
  });

  readonly filteredSimpleItems = computed(() => {
    const filter = this.searchFilter().toLowerCase().trim();
    if (!filter) {
      return this.data?.simpleItems || [];
    }
    return (this.data?.simpleItems || []).filter((v) =>
      v.toLowerCase().includes(filter),
    );
  });

  close() {
    this.closeOverlay.emit();
  }
}
