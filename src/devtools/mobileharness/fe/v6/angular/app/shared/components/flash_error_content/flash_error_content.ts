import {ChangeDetectionStrategy, Component} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';

/**
 * Content for the flash error dialog, which includes a link to report the issue.
 */
@Component({
  selector: 'app-flash-error-content',
  standalone: true,
  imports: [MatIconModule],
  templateUrl: './flash_error_content.ng.html',
  styleUrl: './flash_error_content.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlashErrorContent {}
