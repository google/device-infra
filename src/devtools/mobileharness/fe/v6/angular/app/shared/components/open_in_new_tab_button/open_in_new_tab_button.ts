import {CommonModule, DOCUMENT} from '@angular/common';
import {Component, inject} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {DebugService} from '../../../core/services/debug_service';
import {UrlService} from '../../../core/services/url_service';
import {openInNewTab} from '../../utils/safe_dom';

/**
 * A button that opens the current page in a new tab.
 * Only visible in embedded mode and debug mode.
 */
@Component({
  selector: 'app-open-in-new-tab-button',
  standalone: true,
  templateUrl: './open_in_new_tab_button.ng.html',
  styleUrl: './open_in_new_tab_button.scss',
  imports: [CommonModule, MatIconModule],
})
export class OpenInNewTabButton {
  private readonly document = inject(DOCUMENT);
  readonly debugService = inject(DebugService);
  readonly urlService = inject(UrlService);

  openInNewTab() {
    const win =
      this.document?.defaultView ??
      (typeof window !== 'undefined' ? window : null);
    if (win) {
      openInNewTab(win.location.href);
    }
  }
}
