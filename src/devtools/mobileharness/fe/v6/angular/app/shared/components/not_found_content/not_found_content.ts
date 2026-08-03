import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
  inject,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {RouterModule} from '@angular/router';
import {DebugService} from '../../../core/services/debug_service';
import {UrlService} from '../../../core/services/url_service';

/**
 * Enum for available back actions.
 */
export enum BackActionType {
  HOST = 'host',
  DEVICE = 'device',
}

/**
 * Component to display a user-friendly "Not Found" page for entities like Hosts or Devices.
 */
@Component({
  selector: 'app-not-found-content',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule, RouterModule],
  templateUrl: './not_found_content.ng.html',
  styleUrls: ['./not_found_content.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundContent {
  @Input() entityType = 'Entity';
  @Input() entityId = '';
  @Input() backActionType: BackActionType = BackActionType.HOST;

  @Output() readonly refresh = new EventEmitter<void>();

  readonly debugService = inject(DebugService);
  private readonly urlService = inject(UrlService);

  /**
   * Returns the user-facing label for the back button depending on the entity type.
   *
   * @return The localized button label string (e.g. 'Back to Host List').
   */
  get backLabel(): string {
    if (this.backActionType === BackActionType.HOST) {
      return 'Back to Host List';
    }
    if (this.backActionType === BackActionType.DEVICE) {
      return 'Back to Device List';
    }
    return '';
  }

  /**
   * Handles the back button click by navigating to the corresponding parent
   * search/list view via UrlService.
   */
  onBack() {
    if (this.backActionType === BackActionType.HOST) {
      this.urlService.navigateToParent('host_search');
    } else if (this.backActionType === BackActionType.DEVICE) {
      this.urlService.navigateToParent('device_search');
    }
  }
}
