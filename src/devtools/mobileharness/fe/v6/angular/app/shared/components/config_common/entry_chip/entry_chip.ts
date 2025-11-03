import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {MatAutocompleteModule} from '@angular/material/autocomplete';
import {MatButtonModule} from '@angular/material/button';
import {MatChipsModule} from '@angular/material/chips';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';

/**
 * The component for adding users and groups.
 *
 * It is used in the common config page to add users and groups.
 */
@Component({
  selector: 'app-entry-chip',
  standalone: true,
  templateUrl: './entry_chip.ng.html',
  styleUrl: './entry_chip.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatAutocompleteModule,
    MatChipsModule,
    MatIconModule,
    MatInputModule,
    MatButtonModule,
  ],
})
export class EntryChip implements OnInit {
  @Input() editable = true;
  @Input() emptyLabel = 'None added.';
  @Input() placeholder = 'Add user or group...';
  @Input() entries: string[] = [];
  @Output() readonly entriesChange = new EventEmitter<string[]>();

  entry = '';

  ngOnInit() {}

  remove(index: number): void {
    if (index > -1) {
      this.entries.splice(index, 1);
    }
  }

  add(): void {
    const entry = this.entry.trim();
    if (entry && !this.entries.includes(entry)) {
      this.entries.push(entry);
    }

    this.entry = '';
  }
}
