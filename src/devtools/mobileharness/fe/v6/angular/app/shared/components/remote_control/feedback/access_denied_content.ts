import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';

/**
 * Component to display access denied info.
 */
@Component({
  selector: 'app-access-denied-content',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './access_denied_content.ng.html',
  styleUrls: ['./access_denied_content.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccessDeniedContent {
  @Input() devices: Array<{id: string}> = [];
}
