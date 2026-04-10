import {Clipboard} from '@angular/cdk/clipboard';
import {Injectable, inject} from '@angular/core';

/**
 * Service for clipboard operations.
 * Uses Angular CDK's Clipboard for best compatibility with iframes.
 */
@Injectable({
  providedIn: 'root',
})
export class ClipboardService {
  private readonly clipboard = inject(Clipboard);

  /**
   * Copies the given text to the clipboard.
   * Returns true if successful, false otherwise.
   */
  copyToClipboard(text: string): boolean {
    return this.clipboard.copy(text);
  }
}
