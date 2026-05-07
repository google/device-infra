import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, Input} from '@angular/core';

/**
 * Component to display "no valid release versions" message.
 */
@Component({
  selector: 'app-no-valid-versions-content',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './no_valid_versions_content.ng.html',
  styleUrls: ['./no_valid_versions_content.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NoValidVersionsContent {
  @Input({required: true}) hostName!: string;
}
