import {inject} from '@angular/core';
import {ClipboardService} from '../services/clipboard_service';
import {SnackBarService} from '../services/snackbar_service';

/**
 * Composable function that returns a copy-to-clipboard function
 * that automatically shows success/error toast notifications using SnackBarService.
 */
export function useCopyToClipboard() {
  const clipboard = inject(ClipboardService);
  const snackBar = inject(SnackBarService);

  return (text: string, successMessage = 'Copied to clipboard!') => {
    const success = clipboard.copyToClipboard(text);
    if (success) {
      snackBar.showSuccess(successMessage);
    } else {
      snackBar.showError('Failed to copy text.');
    }
  };
}
