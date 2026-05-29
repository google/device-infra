import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';

/**
 * Component to display confirmation content when saving pass-through flags.
 */
@Component({
  selector: 'app-save-flags-confirm-content',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './save_flags_confirm_content.ng.html',
  styleUrls: ['./save_flags_confirm_content.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SaveFlagsConfirmContent {
  @Input() flags = '';
}
