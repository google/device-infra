import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';

/**
 * Component to display connection error info.
 */
@Component({
  selector: 'app-connection-error-content',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './connection_error_content.ng.html',
  styleUrls: ['./connection_error_content.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConnectionErrorContent {
  @Input() device: {id: string} | null = null;
  @Input() isTestbed = false;
  @Input() capabilitiesList: Array<{id: string; modes: string}> | null = null;
}
