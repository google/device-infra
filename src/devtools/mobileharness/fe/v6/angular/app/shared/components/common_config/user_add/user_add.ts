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
  selector: 'app-user-add',
  standalone: true,
  templateUrl: './user_add.ng.html',
  styleUrl: './user_add.scss',
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
export class UserAdd implements OnInit {
  @Input() users: string[] = [];
  @Output() readonly usersChange = new EventEmitter<string[]>();

  user = '';

  ngOnInit() {}

  remove(index: number): void {
    if (index > -1) {
      this.users.splice(index, 1);
      this.usersChange.emit(this.users);
    }
  }

  add(): void {
    const user = this.user.trim();
    if (user && !this.users.includes(user)) {
      this.users.push(user);
      this.usersChange.emit(this.users);
    }

    this.user = '';
  }
}
